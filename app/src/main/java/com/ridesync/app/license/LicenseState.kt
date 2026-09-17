package com.ridesync.app.license

/**
 * The device's current entitlement, cached from the last successful validation
 * against the website. Held in a StateFlow by [LicenseManager] and read by the
 * UI (the Subscription screen) and the session layer (to cap ride size).
 */
data class LicenseState(
    val key: String = "",
    val planTier: Tier = Tier.FREE, // what they pay for (may be lapsed)
    val status: Status = Status.FREE,
    val expiresAtMs: Long? = null,
    val lastValidatedMs: Long = 0L,
    val activating: Boolean = false,
    val lastError: String? = null,
    val lastValidationFailed: Boolean = false,
) {
    enum class Status { FREE, ACTIVE, EXPIRED, UNKNOWN }

    val hasKey: Boolean get() = key.isNotBlank()

    /** True when a paid plan's expiry has passed (so entitlement drops to Free). */
    val lapsed: Boolean
        get() = planTier.isPaid && expiresAtMs != null && expiresAtMs < System.currentTimeMillis()

    /** The tier actually in force right now (Free if lapsed). */
    val effectiveTier: Tier get() = if (lapsed) Tier.FREE else planTier

    /** The rider limit to enforce for a hosted ride right now. */
    val maxRiders: Int get() = effectiveTier.maxUsers

    val effectiveStatus: Status
        get() = when {
            lapsed -> Status.EXPIRED
            planTier.isPaid -> Status.ACTIVE
            else -> Status.FREE
        }
}
