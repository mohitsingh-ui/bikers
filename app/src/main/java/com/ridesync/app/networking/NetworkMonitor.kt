package com.ridesync.app.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import com.ridesync.app.core.RLog
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Local-network awareness: are we on Wi-Fi (or providing the hotspot), what is
 * our LAN address, and what is the gateway (= the host phone, when we are a
 * client on its hotspot).
 */
class NetworkMonitor(private val context: Context) {

    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifi =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /** Emits true while a Wi-Fi network is available. Note: a phone RUNNING the
     *  hotspot has no Wi-Fi *client* network, so hosts should not gate on this. */
    val wifiAvailable: Flow<Boolean> = callbackFlow {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(hasWifiNow())
            }
        }
        trySend(hasWifiNow())
        connectivity.registerNetworkCallback(request, callback)
        awaitClose { runCatching { connectivity.unregisterNetworkCallback(callback) } }
    }

    fun hasWifiNow(): Boolean {
        val network = connectivity.activeNetwork ?: return hasAnyLanAddress()
        val caps = connectivity.getNetworkCapabilities(network) ?: return hasAnyLanAddress()
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || hasAnyLanAddress()
    }

    /**
     * Best local IPv4 for this device on the LAN. Works both for hotspot hosts
     * (ap/swlan interfaces) and Wi-Fi clients (wlan0), preferring 192.168.x.x.
     */
    fun localIpAddress(): String? {
        val candidates = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (address in nif.inetAddresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val ip = address.hostAddress ?: continue
                        if (address.isSiteLocalAddress) candidates += ip
                    }
                }
            }
        } catch (e: Exception) {
            RLog.w(RLog.Cat.NETWORK, "interface scan failed", e)
        }
        return candidates.firstOrNull { it.startsWith("192.168.") } ?: candidates.firstOrNull()
    }

    private fun hasAnyLanAddress(): Boolean = localIpAddress() != null

    /**
     * The default gateway of the current Wi-Fi connection. On a phone hotspot
     * this IS the host phone, which is what makes PIN-only joining work.
     */
    @Suppress("DEPRECATION")
    fun gatewayAddress(): String? {
        return try {
            val gw = wifi.dhcpInfo?.gateway ?: return null
            if (gw == 0) null else intToIpv4(gw)
        } catch (e: Exception) {
            RLog.w(RLog.Cat.NETWORK, "gateway lookup failed", e)
            null
        }
    }

    /** Acquire a multicast lock (needed for NSD + broadcast RX on many devices). */
    fun acquireMulticastLock(tag: String): WifiManager.MulticastLock? = try {
        wifi.createMulticastLock(tag).apply {
            setReferenceCounted(false)
            acquire()
        }
    } catch (e: Exception) {
        RLog.w(RLog.Cat.NETWORK, "multicast lock failed", e)
        null
    }

    companion object {
        /** DhcpInfo packs IPv4 little-endian. */
        fun intToIpv4(value: Int): String =
            "${value and 0xFF}.${value shr 8 and 0xFF}.${value shr 16 and 0xFF}.${value shr 24 and 0xFF}"
    }
}
