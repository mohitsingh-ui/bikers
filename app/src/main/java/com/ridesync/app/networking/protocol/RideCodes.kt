package com.ridesync.app.networking.protocol

import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Ride PIN + QR deep-link payload.
 *
 * QR codes encode `ridesync://join?...` so the in-app scanner AND any generic
 * QR app can open RideSync directly. Parsing is hand-rolled (no android.net.Uri)
 * so it stays JVM-testable and never throws on hostile input.
 */
object RideCodes {

    const val SCHEME_PREFIX = "ridesync://join?"

    fun generatePin(random: Random = Random.Default): String =
        (1000 + random.nextInt(9000)).toString()

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it.isDigit() }

    data class JoinTarget(
        val rideId: String?,
        val rideName: String?,
        val hostName: String?,
        val host: String?,
        val port: Int,
        val pin: String?,
    )

    fun buildJoinUri(
        rideId: String,
        rideName: String,
        hostName: String,
        hostAddress: String,
        controlPort: Int,
        pin: String,
    ): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        return SCHEME_PREFIX +
            "v=1" +
            "&rid=" + enc(rideId) +
            "&name=" + enc(rideName) +
            "&hn=" + enc(hostName) +
            "&host=" + enc(hostAddress) +
            "&port=" + controlPort +
            "&pin=" + enc(pin)
    }

    fun parseJoinUri(text: String): JoinTarget? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(SCHEME_PREFIX)) return null
        val query = trimmed.removePrefix(SCHEME_PREFIX)
        if (query.length > 2048) return null

        val params = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            val key = pair.substring(0, idx)
            val value = runCatching {
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            }.getOrNull() ?: continue
            params[key] = value
        }

        val port = params["port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: Ports.CONTROL_TCP
        val host = params["host"]?.takeIf { isPlausibleIpv4(it) }
        val pin = params["pin"]?.takeIf { isValidPin(it) }
        val rideId = params["rid"]?.takeIf { it.isNotBlank() && it.length <= 64 }
        if (host == null && rideId == null) return null

        return JoinTarget(
            rideId = rideId,
            rideName = params["name"]?.take(48),
            hostName = params["hn"]?.take(32),
            host = host,
            port = port,
            pin = pin,
        )
    }

    fun isPlausibleIpv4(s: String): Boolean {
        val parts = s.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            val n = part.toIntOrNull() ?: return false
            n in 0..255 && part.length <= 3
        }
    }
}
