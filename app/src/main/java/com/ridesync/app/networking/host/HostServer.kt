package com.ridesync.app.networking.host

import com.ridesync.app.core.RLog
import com.ridesync.app.domain.model.Rider
import com.ridesync.app.domain.model.RiderState
import com.ridesync.app.networking.ClockSync
import com.ridesync.app.networking.protocol.Bye
import com.ridesync.app.networking.protocol.ControlMessage
import com.ridesync.app.networking.protocol.DiscoveryReply
import com.ridesync.app.networking.protocol.Emergency
import com.ridesync.app.networking.protocol.Envelope
import com.ridesync.app.networking.protocol.Heartbeat
import com.ridesync.app.networking.protocol.HostHeartbeat
import com.ridesync.app.networking.protocol.JoinAccepted
import com.ridesync.app.networking.protocol.JoinRejected
import com.ridesync.app.networking.protocol.JoinRequest
import com.ridesync.app.networking.protocol.Ports
import com.ridesync.app.networking.protocol.QuickAlertMsg
import com.ridesync.app.networking.protocol.RideInfoDto
import com.ridesync.app.networking.protocol.RosterUpdate
import com.ridesync.app.networking.protocol.VoicePackets
import com.ridesync.app.networking.protocol.VoiceStart
import com.ridesync.app.networking.protocol.VoiceStop
import com.ridesync.app.networking.protocol.Wire
import com.ridesync.app.networking.protocol.toDto
import com.ridesync.app.networking.transport.TcpJsonConnection
import com.ridesync.app.networking.transport.UdpChannel
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Static configuration of a hosted ride. */
data class RideConfig(
    val rideId: String,
    val name: String,
    val hostName: String,
    val pin: String,
    val maxRiders: Int,
    val hostOnlyMusic: Boolean,
    val controlPort: Int = Ports.CONTROL_TCP,
    val voicePort: Int = Ports.VOICE_UDP,
)

/**
 * The host side of the star topology.
 *
 * Owns the TCP accept loop, per-client reader/writer pairs, the roster, the
 * heartbeat watchdog, and the UDP voice relay. Semantic decisions (music,
 * events, ride phase) live in the session layer via [Listener].
 *
 * Voice relay happens synchronously inside the UDP receive callback — one
 * datagram in, N datagrams out — which keeps relay latency at a few hundred
 * microseconds.
 */
class HostServer(
    private val scope: CoroutineScope,
    private val hostRiderId: String,
    val config: RideConfig,
    private val clock: ClockSync,
    private val listener: Listener,
) {
    interface Listener {
        fun onRosterChanged(roster: List<Rider>, succession: List<String>)
        fun onClientMessage(fromRiderId: String, message: ControlMessage)
        fun onClientJoined(rider: Rider, rejoin: Boolean)
        fun onClientLost(rider: Rider)
        fun onClientLeft(rider: Rider)
        fun onVoiceFromClient(voice: VoicePackets.Datagram.Voice)
    }

    private inner class ClientHandler(
        val riderId: String,
        val key: Int,
        var name: String,
        @Volatile var conn: TcpJsonConnection?,
    ) {
        val outbox = Channel<Envelope>(capacity = 256)
        @Volatile var readerJob: Job? = null
        @Volatile var writerJob: Job? = null
        @Volatile var lastSeenMs: Long = System.currentTimeMillis()
        @Volatile var voiceEndpoint: InetSocketAddress? = null
        @Volatile var battery: Int? = null
        @Volatile var talking: Boolean = false
        @Volatile var state: RiderState = RiderState.CONNECTED
        @Volatile var joinedAtMs: Long = System.currentTimeMillis()
    }

    private val handlers = ConcurrentHashMap<String, ClientHandler>()
    private val nextKey = AtomicInteger(1)
    private val seq = AtomicLong(0)

    @Volatile var hostBattery: Int? = null
    @Volatile var hostTalking: Boolean = false

    @Volatile
    var rideStarted: Boolean = false

    private var serverSocket: ServerSocket? = null
    private var voiceChannel: UdpChannel? = null

    @Volatile
    private var running = false

    private val hostJoinedAt = System.currentTimeMillis()

    // ------------------------------------------------------------------ API

    fun start(): Boolean {
        if (running) return true
        running = true
        try {
            serverSocket = ServerSocket(config.controlPort)
        } catch (e: IOException) {
            RLog.e(RLog.Cat.NETWORK, "control port bind failed", e)
            running = false
            return false
        }
        try {
            voiceChannel = UdpChannel(bindPort = config.voicePort)
        } catch (e: Exception) {
            RLog.e(RLog.Cat.NETWORK, "voice port bind failed", e)
            runCatching { serverSocket?.close() }
            running = false
            return false
        }
        startAcceptLoop()
        startVoiceLoop()
        startWatchdog()
        RLog.i(RLog.Cat.NETWORK, "host server up on tcp:${config.controlPort} udp:${config.voicePort}")
        notifyRoster()
        return true
    }

    fun stop(broadcastEnd: Boolean = false, finalMessage: ControlMessage? = null) {
        if (!running) return
        running = false
        if (finalMessage != null) {
            for (handler in handlers.values) {
                handler.conn?.send(env(finalMessage))
            }
        } else if (broadcastEnd) {
            for (handler in handlers.values) {
                handler.conn?.send(env(Bye("HOST_STOPPING")))
            }
        }
        for (handler in handlers.values) closeHandler(handler)
        handlers.clear()
        runCatching { serverSocket?.close() }
        voiceChannel?.close()
        RLog.i(RLog.Cat.NETWORK, "host server stopped")
    }

    /** Reliable fan-out to every connected client. */
    fun broadcast(message: ControlMessage, exceptRiderId: String? = null) {
        val envelope = env(message)
        for (handler in handlers.values) {
            if (handler.riderId == exceptRiderId) continue
            if (handler.state == RiderState.CONNECTED) {
                handler.outbox.trySend(envelope)
            }
        }
    }

    fun sendTo(riderId: String, message: ControlMessage) {
        handlers[riderId]?.outbox?.trySend(env(message))
    }

    /** Voice captured on the host device itself → all clients. */
    fun relayVoiceFromHost(data: ByteArray, length: Int) {
        val channel = voiceChannel ?: return
        for (handler in handlers.values) {
            val endpoint = handler.voiceEndpoint ?: continue
            channel.send(data, length, endpoint)
        }
    }

    fun updateHostTalking(talking: Boolean) {
        hostTalking = talking
        notifyRoster()
    }

    fun updateHostBattery(percent: Int?) {
        if (percent != hostBattery) {
            hostBattery = percent
            notifyRoster()
        }
    }

    fun markRideStarted() {
        rideStarted = true
    }

    fun currentRoster(): List<Rider> = buildRoster()

    fun connectedClientCount(): Int =
        handlers.values.count { it.state == RiderState.CONNECTED }

    fun discoveryInfo(): DiscoveryReply = DiscoveryReply(
        rideId = config.rideId,
        name = config.name,
        hostName = config.hostName,
        riderCount = 1 + connectedClientCount(),
        maxRiders = config.maxRiders,
        controlPort = config.controlPort,
        hostId = hostRiderId,
        pin = config.pin,
    )

    // ---------------------------------------------------------- accept/join

    private fun startAcceptLoop() {
        scope.launch(Dispatchers.IO) {
            val server = serverSocket ?: return@launch
            while (running) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    return@launch
                }
                launch(Dispatchers.IO) {
                    handleNewConnection(TcpJsonConnection(socket))
                }
            }
        }
    }

    private fun handleNewConnection(conn: TcpJsonConnection) {
        val first = conn.readNext()
        if (first == null) {
            conn.close()
            return
        }
        val join = first.msg as? JoinRequest
        if (join == null) {
            conn.send(env(JoinRejected(JoinRejected.REASON_UNKNOWN_RIDE)))
            conn.close()
            return
        }
        if (first.rideId.isNotBlank() && first.rideId != config.rideId) {
            conn.send(env(JoinRejected(JoinRejected.REASON_UNKNOWN_RIDE)))
            conn.close()
            return
        }
        if (join.protocol > Wire.PROTOCOL_VERSION) {
            conn.send(env(JoinRejected(JoinRejected.REASON_VERSION)))
            conn.close()
            return
        }
        if (join.pin != config.pin) {
            conn.send(env(JoinRejected(JoinRejected.REASON_WRONG_PIN)))
            conn.close()
            RLog.i(RLog.Cat.NETWORK, "join rejected: wrong pin from ${conn.remoteAddress}")
            return
        }

        val riderId = first.senderId
        val existing = handlers[riderId]
        val rejoin = existing != null
        val handler: ClientHandler

        if (existing != null) {
            // Same device returning — keep its slot and key.
            existing.conn?.close()
            existing.readerJob?.cancel()
            existing.writerJob?.cancel()
            existing.conn = conn
            existing.name = join.riderName.ifBlank { existing.name }
            existing.state = RiderState.CONNECTED
            existing.lastSeenMs = System.currentTimeMillis()
            handler = existing
        } else {
            // maxRiders includes the host; disconnected riders keep their slot
            // so they can come back.
            if (1 + handlers.size >= config.maxRiders) {
                conn.send(env(JoinRejected(JoinRejected.REASON_FULL)))
                conn.close()
                return
            }
            handler = ClientHandler(
                riderId = riderId,
                key = nextKey.getAndIncrement(),
                name = join.riderName.ifBlank { "Rider" },
                conn = conn,
            )
            handlers[riderId] = handler
        }

        val accepted = JoinAccepted(
            yourKey = handler.key,
            ride = rideInfoDto(),
            roster = buildRoster().map { it.toDto() },
            succession = buildSuccession(),
            hostTimeMs = clock.hostNow(),
            rideStarted = rideStarted,
        )
        if (!conn.send(env(accepted))) {
            conn.close()
            return
        }

        startHandlerLoops(handler)
        RLog.i(RLog.Cat.NETWORK, "rider ${if (rejoin) "rejoined" else "joined"}: ${handler.name} (${conn.remoteAddress})")
        notifyRoster()
        listener.onClientJoined(riderToDomain(handler), rejoin)
        broadcastRoster()
    }

    private fun startHandlerLoops(handler: ClientHandler) {
        handler.writerJob = scope.launch(Dispatchers.IO) {
            for (envelope in handler.outbox) {
                val conn = handler.conn ?: continue
                if (!conn.send(envelope)) {
                    // Writer noticed the dead link first.
                    markLost(handler)
                }
            }
        }
        handler.readerJob = scope.launch(Dispatchers.IO) {
            val conn = handler.conn ?: return@launch
            while (isActive && running && !conn.closed) {
                val envelope = conn.readNext()
                if (envelope == null) {
                    if (running && handler.conn === conn) markLost(handler)
                    return@launch
                }
                if (envelope.senderId != handler.riderId) continue
                handler.lastSeenMs = System.currentTimeMillis()
                routeClientMessage(handler, envelope.msg)
            }
        }
    }

    private fun routeClientMessage(handler: ClientHandler, message: ControlMessage) {
        when (message) {
            is Heartbeat -> {
                val changed = handler.battery != message.batteryPercent
                handler.battery = message.batteryPercent
                if (handler.state != RiderState.CONNECTED) {
                    handler.state = RiderState.CONNECTED
                    notifyRoster()
                    broadcastRoster()
                } else if (changed) {
                    notifyRoster()
                    broadcastRoster()
                }
            }

            is VoiceStart -> {
                handler.talking = true
                broadcast(message, exceptRiderId = handler.riderId)
                listener.onClientMessage(handler.riderId, message)
            }

            is VoiceStop -> {
                handler.talking = false
                broadcast(message, exceptRiderId = handler.riderId)
                listener.onClientMessage(handler.riderId, message)
            }

            is QuickAlertMsg -> {
                broadcast(message, exceptRiderId = handler.riderId)
                listener.onClientMessage(handler.riderId, message)
            }

            is Emergency -> {
                broadcast(message, exceptRiderId = handler.riderId)
                listener.onClientMessage(handler.riderId, message)
            }

            is Bye -> {
                RLog.i(RLog.Cat.NETWORK, "rider left: ${handler.name}")
                val rider = riderToDomain(handler)
                removeHandler(handler)
                listener.onClientLeft(rider)
                notifyRoster()
                broadcastRoster()
            }

            else -> listener.onClientMessage(handler.riderId, message)
        }
    }

    // ------------------------------------------------------------ voice UDP

    private fun startVoiceLoop() {
        val channel = voiceChannel ?: return
        channel.startReceiving("host-voice") { data, length, from ->
            when (val datagram = VoicePackets.decode(data, length)) {
                is VoicePackets.Datagram.Hello -> {
                    handlers.values.firstOrNull { it.key == datagram.riderKey }
                        ?.let { it.voiceEndpoint = from }
                }

                is VoicePackets.Datagram.ClockPing -> {
                    channel.send(
                        VoicePackets.encodeClockPong(datagram.nonce, datagram.t0, clock.hostNow()),
                        from,
                    )
                }

                is VoicePackets.Datagram.Voice -> {
                    // Fast path: relay to everyone except the speaker, then local.
                    for (handler in handlers.values) {
                        if (handler.key == datagram.senderKey) continue
                        val endpoint = handler.voiceEndpoint ?: continue
                        channel.send(data, length, endpoint)
                    }
                    listener.onVoiceFromClient(datagram)
                }

                else -> Unit
            }
        }
    }

    // ------------------------------------------------------------- watchdog

    private fun startWatchdog() {
        scope.launch {
            var lastHeartbeat = 0L
            while (isActive && running) {
                val now = System.currentTimeMillis()
                if (now - lastHeartbeat >= HOST_HEARTBEAT_INTERVAL_MS) {
                    lastHeartbeat = now
                    broadcast(HostHeartbeat(clock.hostNow()))
                }
                var changed = false
                for (handler in handlers.values) {
                    val silentFor = now - handler.lastSeenMs
                    when (handler.state) {
                        RiderState.CONNECTED -> if (silentFor > RECONNECTING_AFTER_MS) {
                            handler.state = RiderState.RECONNECTING
                            changed = true
                            RLog.w(RLog.Cat.CONN, "rider silent, reconnecting: ${handler.name}")
                            listener.onClientLost(riderToDomain(handler))
                        }

                        RiderState.RECONNECTING -> if (silentFor > DISCONNECTED_AFTER_MS) {
                            handler.state = RiderState.DISCONNECTED
                            handler.conn?.close()
                            changed = true
                            RLog.w(RLog.Cat.CONN, "rider disconnected: ${handler.name}")
                        }

                        RiderState.DISCONNECTED -> Unit
                    }
                }
                if (changed) {
                    notifyRoster()
                    broadcastRoster()
                }
                delay(WATCHDOG_TICK_MS)
            }
        }
    }

    private fun markLost(handler: ClientHandler) {
        if (handler.state == RiderState.CONNECTED) {
            handler.state = RiderState.RECONNECTING
            // Backdate so the watchdog owns the DISCONNECTED escalation.
            handler.lastSeenMs =
                minOf(handler.lastSeenMs, System.currentTimeMillis() - RECONNECTING_AFTER_MS)
            RLog.w(RLog.Cat.CONN, "link lost: ${handler.name}")
            listener.onClientLost(riderToDomain(handler))
            notifyRoster()
            broadcastRoster()
        }
    }

    private fun removeHandler(handler: ClientHandler) {
        handlers.remove(handler.riderId)
        closeHandler(handler)
    }

    private fun closeHandler(handler: ClientHandler) {
        handler.readerJob?.cancel()
        handler.writerJob?.cancel()
        handler.outbox.close()
        handler.conn?.close()
    }

    // --------------------------------------------------------------- roster

    private fun riderToDomain(handler: ClientHandler): Rider = Rider(
        id = handler.riderId,
        key = handler.key,
        name = handler.name,
        isHost = false,
        state = handler.state,
        batteryPercent = handler.battery,
        isTalking = handler.talking,
        joinedAtMs = handler.joinedAtMs,
    )

    private fun buildRoster(): List<Rider> {
        val host = Rider(
            id = hostRiderId,
            key = 0,
            name = config.hostName,
            isHost = true,
            state = RiderState.CONNECTED,
            batteryPercent = hostBattery,
            isTalking = hostTalking,
            joinedAtMs = hostJoinedAt,
        )
        val clients = handlers.values
            .map { riderToDomain(it) }
            .sortedBy { it.joinedAtMs }
        return listOf(host) + clients
    }

    private fun buildSuccession(): List<String> = buildRoster().map { it.id }

    private fun rideInfoDto() = RideInfoDto(
        rideId = config.rideId,
        name = config.name,
        hostName = config.hostName,
        maxRiders = config.maxRiders,
        riderCount = 1 + connectedClientCount(),
        controlPort = config.controlPort,
        hostOnlyMusic = config.hostOnlyMusic,
    )

    private fun notifyRoster() {
        listener.onRosterChanged(buildRoster(), buildSuccession())
    }

    private fun broadcastRoster() {
        broadcast(
            RosterUpdate(
                ride = rideInfoDto(),
                roster = buildRoster().map { it.toDto() },
                succession = buildSuccession(),
            ),
        )
    }

    private fun env(message: ControlMessage) = Envelope(
        senderId = hostRiderId,
        rideId = config.rideId,
        seq = seq.incrementAndGet(),
        sentAt = clock.hostNow(),
        msg = message,
    )

    companion object {
        const val HOST_HEARTBEAT_INTERVAL_MS = 2000L
        const val WATCHDOG_TICK_MS = 1000L
        const val RECONNECTING_AFTER_MS = 5000L
        const val DISCONNECTED_AFTER_MS = 30000L
    }
}
