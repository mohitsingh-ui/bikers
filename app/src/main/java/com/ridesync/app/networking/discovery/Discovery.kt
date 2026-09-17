package com.ridesync.app.networking.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.ridesync.app.core.RLog
import com.ridesync.app.networking.protocol.DiscoveryReply
import com.ridesync.app.networking.protocol.DiscoveryWire
import com.ridesync.app.networking.protocol.Ports
import com.ridesync.app.networking.transport.UdpChannel
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A ride visible on the local network. */
data class DiscoveredRide(
    val rideId: String,
    val name: String,
    val hostName: String,
    val hostId: String,
    val riderCount: Int,
    val maxRiders: Int,
    val address: String,
    val controlPort: Int,
    val lastSeenMs: Long,
    val pin: String = "",
)

/**
 * Host side of discovery: advertises the ride over NSD (mDNS) and answers
 * plain UDP broadcast probes. Two mechanisms because NSD is flaky on a
 * meaningful share of devices/hotspots; the broadcast path almost always works.
 */
class HostAnnouncer(
    context: Context,
    private val infoProvider: () -> DiscoveryReply?,
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var responder: UdpChannel? = null

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        registerNsd()
        startProbeResponder()
        RLog.i(RLog.Cat.DISCOVERY, "announcer started")
    }

    /** NSD TXT records are set at registration; re-register to refresh them. */
    fun refresh() {
        if (!running) return
        unregisterNsd()
        registerNsd()
    }

    fun stop() {
        running = false
        unregisterNsd()
        responder?.close()
        responder = null
        RLog.i(RLog.Cat.DISCOVERY, "announcer stopped")
    }

    private fun registerNsd() {
        val info = infoProvider() ?: return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "RideSync-${info.name.take(24)}-${info.rideId.take(4)}"
            serviceType = Ports.NSD_SERVICE_TYPE
            port = info.controlPort
            setAttribute("rid", info.rideId.take(36))
            setAttribute("name", info.name.take(36))
            setAttribute("hn", info.hostName.take(24))
            setAttribute("hid", info.hostId.take(36))
            setAttribute("cnt", info.riderCount.toString())
            setAttribute("max", info.maxRiders.toString())
            setAttribute("pin", info.pin.take(4))
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                RLog.d(RLog.Cat.DISCOVERY, "nsd registered as ${nsdServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                RLog.w(RLog.Cat.DISCOVERY, "nsd registration failed: $errorCode (broadcast fallback active)")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        try {
            nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            RLog.w(RLog.Cat.DISCOVERY, "nsd register threw", e)
        }
    }

    private fun unregisterNsd() {
        registrationListener?.let {
            runCatching { nsd.unregisterService(it) }
        }
        registrationListener = null
    }

    private fun startProbeResponder() {
        val channel = try {
            UdpChannel(bindPort = Ports.DISCOVERY_UDP, enableBroadcast = true)
        } catch (e: Exception) {
            RLog.w(RLog.Cat.DISCOVERY, "discovery port bind failed", e)
            return
        }
        responder = channel
        channel.startReceiving("probe-responder") { data, length, from ->
            val text = String(data, 0, length, Charsets.UTF_8)
            if (text == DiscoveryWire.PROBE) {
                val info = infoProvider() ?: return@startReceiving
                val reply = DiscoveryWire.encodeReply(info).toByteArray(Charsets.UTF_8)
                channel.send(reply, from)
            }
        }
    }
}

/**
 * Client side of discovery: listens for NSD services and fires UDP broadcast
 * probes, merging both into one de-duplicated, TTL-expired ride list.
 */
class RideScanner(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val found = ConcurrentHashMap<String, DiscoveredRide>()
    private val _rides = MutableStateFlow<List<DiscoveredRide>>(emptyList())
    val rides: StateFlow<List<DiscoveredRide>> = _rides

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var probeChannel: UdpChannel? = null
    private var probeJob: Job? = null

    // NSD allows only one in-flight resolve; queue the rest.
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()

    @Volatile
    private var resolving = false

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        found.clear()
        publish()
        startNsdDiscovery()
        startProbing()
        RLog.i(RLog.Cat.DISCOVERY, "scanner started")
    }

    fun stop() {
        running = false
        discoveryListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discoveryListener = null
        synchronized(resolveQueue) { resolveQueue.clear() }
        resolving = false
        probeJob?.cancel()
        probeJob = null
        probeChannel?.close()
        probeChannel = null
        RLog.i(RLog.Cat.DISCOVERY, "scanner stopped")
    }

    /** Clears results and probes again immediately. */
    fun refresh() {
        found.clear()
        publish()
        probeNow()
    }

    private fun startNsdDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.startsWith("_ridesync.")) return
                enqueueResolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                // TTL expiry handles removal; nothing to do eagerly.
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                RLog.w(RLog.Cat.DISCOVERY, "nsd discovery failed: $errorCode (broadcast fallback active)")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        try {
            nsd.discoverServices(Ports.NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            RLog.w(RLog.Cat.DISCOVERY, "nsd discover threw", e)
        }
    }

    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(resolveQueue) {
            resolveQueue.addLast(info)
            if (!resolving) resolveNext()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next = synchronized(resolveQueue) {
            if (resolveQueue.isEmpty()) {
                resolving = false
                return
            }
            resolving = true
            resolveQueue.removeFirst()
        }
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                synchronized(resolveQueue) { resolveNext() }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                handleResolved(serviceInfo)
                synchronized(resolveQueue) { resolveNext() }
            }
        }
        try {
            nsd.resolveService(next, listener)
        } catch (e: Exception) {
            RLog.w(RLog.Cat.DISCOVERY, "nsd resolve threw", e)
            synchronized(resolveQueue) { resolveNext() }
        }
    }

    private fun handleResolved(info: NsdServiceInfo) {
        val address = info.host?.hostAddress ?: return
        fun txt(key: String): String? = info.attributes[key]?.toString(Charsets.UTF_8)
        val rideId = txt("rid") ?: return
        val ride = DiscoveredRide(
            rideId = rideId,
            name = txt("name") ?: info.serviceName,
            hostName = txt("hn") ?: "",
            hostId = txt("hid") ?: "",
            riderCount = txt("cnt")?.toIntOrNull() ?: 1,
            maxRiders = txt("max")?.toIntOrNull() ?: 4,
            address = address,
            controlPort = if (info.port > 0) info.port else Ports.CONTROL_TCP,
            lastSeenMs = System.currentTimeMillis(),
            pin = txt("pin") ?: "",
        )
        found[rideId] = ride
        publish()
        RLog.d(RLog.Cat.DISCOVERY, "nsd resolved ride '${ride.name}' at $address")
    }

    private fun startProbing() {
        val channel = try {
            UdpChannel(bindPort = 0, enableBroadcast = true)
        } catch (e: Exception) {
            RLog.w(RLog.Cat.DISCOVERY, "probe socket failed", e)
            return
        }
        probeChannel = channel
        channel.startReceiving("probe-listener") { data, length, from ->
            val text = String(data, 0, length, Charsets.UTF_8)
            val reply = DiscoveryWire.decodeReply(text) ?: return@startReceiving
            val ride = DiscoveredRide(
                rideId = reply.rideId,
                name = reply.name,
                hostName = reply.hostName,
                hostId = reply.hostId,
                riderCount = reply.riderCount,
                maxRiders = reply.maxRiders,
                address = from.address.hostAddress ?: return@startReceiving,
                controlPort = reply.controlPort,
                lastSeenMs = System.currentTimeMillis(),
                pin = reply.pin,
            )
            found[reply.rideId] = ride
            publish()
        }
        probeJob = scope.launch {
            while (isActive && running) {
                probeNow()
                expireStale()
                delay(PROBE_INTERVAL_MS)
            }
        }
    }

    private fun probeNow() {
        probeChannel?.sendBroadcast(
            DiscoveryWire.PROBE.toByteArray(Charsets.UTF_8),
            Ports.DISCOVERY_UDP,
        )
    }

    private fun expireStale() {
        val cutoff = System.currentTimeMillis() - RIDE_TTL_MS
        var changed = false
        for ((key, ride) in found) {
            if (ride.lastSeenMs < cutoff) {
                found.remove(key)
                changed = true
            }
        }
        if (changed) publish()
    }

    private fun publish() {
        _rides.value = found.values.sortedByDescending { it.lastSeenMs }
    }

    companion object {
        const val PROBE_INTERVAL_MS = 2000L
        const val RIDE_TTL_MS = 6000L
    }
}
