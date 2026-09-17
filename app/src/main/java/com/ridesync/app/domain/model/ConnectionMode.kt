package com.ridesync.app.domain.model

/**
 * How riders' phones link together for the intercom + music.
 *
 * Only WIFI is a working transport today (local Wi-Fi / hotspot, no internet).
 * BLUETOOTH and RADIO are shown as honest options because they need extra
 * hardware or setup — a phone's own radio can't reach kilometres, so long-range
 * riding needs an external mesh/LoRa device. The picker explains this rather
 * than pretending it works.
 */
enum class ConnectionMode(
    val emoji: String,
    val label: String,
    val tagline: String,
    val available: Boolean,
    val note: String,
) {
    WIFI(
        emoji = "📶",
        label = "Wi-Fi (Local)",
        tagline = "Works now · no internet",
        available = true,
        note = "Everyone joins the host's Wi-Fi hotspot. Best quality, ~30–60 m range, fully offline.",
    ),
    BLUETOOTH(
        emoji = "🔵",
        label = "Bluetooth",
        tagline = "Nearby · needs setup",
        available = false,
        note = "For very close riders. Shorter range than Wi-Fi and lower audio quality, so RideSync uses Wi-Fi by default. Bluetooth linking is planned.",
    ),
    RADIO(
        emoji = "📡",
        label = "Radio (Long range)",
        tagline = "Km range · needs a radio device",
        available = false,
        note = "For long distances with no internet or Wi-Fi. A phone alone can't do this — it needs a compatible LoRa/mesh radio (e.g. Meshtastic) paired to each phone. Tell me which device you want and I'll wire it in.",
    ),
}
