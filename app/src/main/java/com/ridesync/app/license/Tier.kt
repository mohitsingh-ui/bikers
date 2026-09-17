package com.ridesync.app.license

/**
 * The RideSync subscription tiers.
 *
 * These MUST match the website's `server/tiers.js` exactly — the website is the
 * source of truth for what a paying customer gets, and the app mirrors it so it
 * can show plan details and enforce the rider limit even while offline.
 *
 * `maxUsers` is the total riders allowed in one ride (the host counts as one).
 */
enum class Tier(
    val id: String,
    val displayName: String,
    val maxUsers: Int,
    val priceInr: Int,
) {
    FREE("free", "Free", 4, 0),
    PLUS("plus", "Plus", 6, 99),
    PRO("pro", "Pro", 8, 199),
    MAX("max", "Max", 12, 299),
    FLEET("fleet", "Fleet", 50, 499);

    val isPaid: Boolean get() = priceInr > 0

    companion object {
        /** Cheapest → most riders, for rendering the plan list. */
        val ordered: List<Tier> = listOf(FREE, PLUS, PRO, MAX, FLEET)

        fun fromId(id: String?): Tier =
            ordered.firstOrNull { it.id == (id ?: "").trim().lowercase() } ?: FREE
    }
}
