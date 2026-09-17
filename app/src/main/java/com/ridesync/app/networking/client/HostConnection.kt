package com.ridesync.app.networking.client

import com.ridesync.app.core.RLog
import com.ridesync.app.domain.model.ConnectionQuality
import com.ridesync.app.networking.ClockSync
import com.ridesync.app.networking.protocol.Bye
import com.ridesync.app.networking.protocol.ControlMessage
import com.ridesync.app.networking.protocol.Envelope
import com.ridesync.app.networking.protocol.Heartbeat
import com.ridesync.app.networking.protocol.HostHeartbeat
import com.ridesync.app.networking.protocol.JoinAccepted
import com.ridesync.app.networking.protocol.JoinRejected
import com.ridesync.app.networking.protocol.JoinRequest
import com.ridesync.app.networking.protocol.VoicePackets
import com.ridesync.app.networking.protocol.Wire
import com.ridesync.app.networking.transport.TcpJsonConnection
import com.ridesync.app.networking.transport.UdpChannel
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where + how to reach a host. */
data class HostEndpoint(
    val rideId: String,
    val host: String,
    val controlPort: Int,
    val voicePort: Int,
    val pin: String,
)

/**
 * The client side of the star topology: one resilient link to the host.
 *
 * Responsibilities:
 *  - JOIN handshake (with retry/backoff)
 *  - inbound control message stream -> [Listener]
 *  - periodic heartbeat + battery report
 *  - UDP voice send/receive + clock ping loop
 *  - automatic reconnection with capped exponential backoff + jitter
 *
 * Connection quality is derived from measured RTT and current link state so
 * the UI can show Excellent/Good/Weak without extra plumbing.
 */
class HostConnection(
    private val scope: CoroutineScope,
    private val riderId: String,
    private val riderName: String,
    private val clock: ClockSync,
    private val autoReconnect: () -> Boolean,
    private val batteryProvider: () -> Int?,
    private val listener: Listener,
) {
    interface Listener {
        fun onJoined(accepted: JoinAccepted)
        fun onRejected(reason: String)
        fun onControlMessage(message: ControlMessage)
        fun onVoice(voice: VoicePackets.Datagram.Voice)
        fun onConnectionStateChanged(quality: ConnectionQuality)
        fun onPermanentlyDisconnected()
    }

    @Volatile
    var endpoint: HostEndpoint? = null
        private set

    @Volatile
    var myKey: Int = -1
        private set

    private val _quality = MutableStateFlow(ConnectionQuality.RECONNECTING)
    val quality: StateFlow<ConnectionQuality> = _quality

    private val seq = AtomicLong(0)

    @Volatile private var conn: TcpJsonConnection? = null
    @Volatile private var voice: UdpChannel? = null
    @Volatile private var supervisorJob: Job? = null
    @Volatile private var readerJob: Job? = null
    @Volatile private var heartbeatJob: Job? = null
    @Volatile private var clockJob: Job? = null

    @Volatile private var running = false
    @Volatile private var joined = false
    @Volatile private var lastRttMs: Int = -1

    private val voiceSeq = AtomicLong(0)
    private val clockNonce = AtomicLong(0)

    fun start(target: HostEndpoint) {
        if (running) stop(notify = false)
        endpoint = target
        running = true
        joined = false
        setQuality(ConnectionQuality.RECONNECTING)
        supervisorJob = scope.launch(Dispatchers.IO) { supervise() }
    }

    /** Redirect to a new host (host migration) without a full teardown. */
    fun redirect(target: HostEndpoint) {
        RLog.i(RLog.Cat.CONN, "redirecting to new host ${target.host}:${target.controlPort}")
        endpoint = target
        joined = false
        conn?.close()
    }

    fun stop(notify: Boolean = true) {
        running = false
        if (notify) conn?.send(env(Bye("CLIENT_LEAVING")))
        supervisorJob?.cancel()
        readerJob?.cancel()
        heartbeatJob?.cancel()
        clockJob?.cancel()
        conn?.close()
        voice?.close()
        conn = null
        voice = null
    }

    fun sendControl(message: ControlMessage) {
        conn?.send(env(message))
    }

    /** Encode + send one voice frame to the host for relay. */
    fun sendVoice(payload: ByteArray, length: Int, flags: Int) {
        val channel = voice ?: return
        val target = endpoint ?: return
        if (myKey < 0) return
        val packet = VoicePackets.encodeVoice(
            senderKey = myKey,
            seq = voiceSeq.getAndIncrement().toInt(),
            flags = flags,
            payload = payload,
            length = length,
        )
        channel.send(packet, InetSocketAddress(target.host, target.voicePort))
    }

    val isConnected: Boolean get() = joined && conn?.closed == false

    // ------------------------------------------------------------ supervisor

    private suspend fun supervise() {
        var attempt = 0
        while (running) {
            val target = endpoint ?: break
            val ok = connectAndJoin(target)
            if (ok) {
                attempt = 0
                runSession()
                if (!running) break
                // Session ended -> link dropped.
                if (!autoReconnect()) {
                    RLog.i(RLog.Cat.CONN, "auto-reconnect disabled; giving up")
                    setQuality(ConnectionQuality.DISCONNECTED)
                    listener.onPermanentlyDisconnected()
                    break
                }
                setQuality(ConnectionQuality.RECONNECTING)
            } else {
                attempt++
                if (attempt >= MAX_ATTEMPTS) {
                    RLog.w(RLog.Cat.CONN, "exhausted reconnect attempts")
                    setQuality(ConnectionQuality.DISCONNECTED)
                    listener.onPermanentlyDisconnected()
                    break
                }
            }
            val backoff = backoffMs(attempt)
            RLog.d(RLog.Cat.CONN, "reconnect in ${backoff}ms (attempt $attempt)")
            delay(backoff)
        }
    }

    private suspend fun connectAndJoin(target: HostEndpoint): Boolean = withContext(Dispatchers.IO) {
        val connection = TcpJsonConnection.connect(target.host, target.controlPort) ?: return@withContext false
        if (!connection.send(env(JoinRequest(riderName, target.pin)))) {
            connection.close()
            return@withContext false
        }
        val reply = connection.readNext()
        when (val msg = reply?.msg) {
            is JoinAccepted -> {
                myKey = msg.yourKey
                clock.addSample(offset = msg.hostTimeMs - System.currentTimeMillis(), rtt = ClockSync.COARSE_RTT_GUESS_MS)
                conn = connection
                joined = true
                ensureVoiceChannel()
                sendHello()
                setQuality(ConnectionQuality.GOOD)
                RLog.i(RLog.Cat.CONN, "joined ride as key ${msg.yourKey}")
                listener.onJoined(msg)
                true
            }

            is JoinRejected -> {
                RLog.w(RLog.Cat.CONN, "join rejected: ${msg.reason}")
                connection.close()
                listener.onRejected(msg.reason)
                running = false
                false
            }

            else -> {
                connection.close()
                false
            }
        }
    }

    private suspend fun runSession() {
        val connection = conn ?: return
        startHeartbeat()
        startClockLoop()
        // Reader runs inline so this function returns exactly when the link dies.
        // (No CoroutineScope receiver here, so cancellation is read from the
        // current context's Job rather than the CoroutineScope.isActive helper.)
        while (coroutineContext[Job]?.isActive != false && running && !connection.closed) {
            val envelope = connection.readNext() ?: break
            handleInbound(envelope)
        }
        heartbeatJob?.cancel()
        clockJob?.cancel()
        joined = false
        RLog.w(RLog.Cat.CONN, "session link closed")
    }

    private fun handleInbound(envelope: Envelope) {
        when (val msg = envelope.msg) {
            is HostHeartbeat -> clock.onHostHeartbeat(msg.hostTimeMs)
            is Bye -> {
                RLog.i(RLog.Cat.CONN, "host said bye: ${msg.reason}")
                conn?.close()
            }

            else -> listener.onControlMessage(msg)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive && running && conn?.closed == false) {
                sendControl(Heartbeat(batteryPercent = batteryProvider(), rttMs = lastRttMs.takeIf { it >= 0 }))
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    // ------------------------------------------------------------- voice/UDP

    private fun ensureVoiceChannel() {
        if (voice != null) return
        val channel = try {
            UdpChannel(bindPort = 0)
        } catch (e: Exception) {
            RLog.e(RLog.Cat.VOICE, "voice channel bind failed", e)
            return
        }
        voice = channel
        channel.startReceiving("client-voice") { data, length, _ ->
            when (val datagram = VoicePackets.decode(data, length)) {
                is VoicePackets.Datagram.Voice -> listener.onVoice(datagram)
                is VoicePackets.Datagram.ClockPong -> {
                    clock.onPong(datagram.t0, datagram.hostTime)
                    updateRtt()
                }

                else -> Unit
            }
        }
    }

    private fun sendHello() {
        val channel = voice ?: return
        val target = endpoint ?: return
        if (myKey < 0) return
        // A few HELLOs so the host reliably learns our UDP endpoint through NAT/AP isolation quirks.
        scope.launch(Dispatchers.IO) {
            repeat(3) {
                channel.send(VoicePackets.encodeHello(myKey), InetSocketAddress(target.host, target.voicePort))
                delay(120)
            }
        }
    }

    private fun startClockLoop() {
        clockJob?.cancel()
        clockJob = scope.launch(Dispatchers.IO) {
            // Fast burst at first for a quick lock, then steady maintenance.
            repeat(5) {
                pingClock()
                delay(400)
            }
            while (isActive && running && conn?.closed == false) {
                pingClock()
                delay(CLOCK_PING_INTERVAL_MS)
            }
        }
    }

    private fun pingClock() {
        val channel = voice ?: return
        val target = endpoint ?: return
        channel.send(
            VoicePackets.encodeClockPing(clockNonce.getAndIncrement(), System.currentTimeMillis()),
            InetSocketAddress(target.host, target.voicePort),
        )
    }

    private fun updateRtt() {
        val rtt = clock.lastRttMs.toInt()
        if (rtt < 0) return
        lastRttMs = rtt
        if (!joined) return
        setQuality(
            when {
                rtt <= EXCELLENT_RTT_MS -> ConnectionQuality.EXCELLENT
                rtt <= GOOD_RTT_MS -> ConnectionQuality.GOOD
                else -> ConnectionQuality.WEAK
            },
        )
    }

    private fun setQuality(q: ConnectionQuality) {
        if (_quality.value != q) {
            _quality.value = q
            listener.onConnectionStateChanged(q)
        }
    }

    private fun env(message: ControlMessage) = Envelope(
        senderId = riderId,
        rideId = endpoint?.rideId ?: "",
        seq = seq.incrementAndGet(),
        sentAt = System.currentTimeMillis(),
        msg = message,
    )

    private fun backoffMs(attempt: Int): Long {
        val base = (BASE_BACKOFF_MS * (1 shl (attempt - 1).coerceIn(0, 5))).coerceAtMost(MAX_BACKOFF_MS)
        val jitter = Random.nextLong(0, base / 2 + 1)
        return base / 2 + jitter
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MS = 2000L
        const val CLOCK_PING_INTERVAL_MS = 3000L
        const val BASE_BACKOFF_MS = 500L
        const val MAX_BACKOFF_MS = 6000L
        const val MAX_ATTEMPTS = 40
        const val EXCELLENT_RTT_MS = 60
        const val GOOD_RTT_MS = 150
    }
}
