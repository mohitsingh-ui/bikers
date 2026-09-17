package com.ridesync.app.networking.host

/**
 * Deterministic host succession.
 *
 * Every roster update carries the succession list (riders by join order, host
 * first). When the host vanishes and the network itself is still alive, each
 * surviving client computes its own rank; rank 0 promotes itself immediately,
 * rank 1 waits one grace period, and so on — so in the common case exactly one
 * new host appears, and in a race the deterministic comparison settles it.
 *
 * Pure logic, no Android imports, fully unit-tested.
 */
object HostElection {

    /** Base stagger between candidate ranks when self-promoting. */
    const val PROMOTION_STAGGER_MS = 1500L

    /** How long a client tries to reach the old host before electing. */
    const val HOST_LOSS_CONFIRM_MS = 8000L

    /**
     * Candidates, in promotion order, once [deadHostId] is gone. Riders not
     * in [aliveIds] are skipped (they were already disconnected before the
     * host died).
     */
    fun candidates(
        succession: List<String>,
        deadHostId: String,
        aliveIds: Set<String>,
    ): List<String> =
        succession.filter { it != deadHostId && it in aliveIds }.distinct()

    /**
     * My promotion rank: 0 = promote first. Self is always treated as alive
     * (a device running this election is, by definition, present — even if the
     * dead host's last roster happened to mark it reconnecting). Returns null
     * only when I am not in the succession list at all (e.g. I joined so
     * recently the host never broadcast an updated succession).
     */
    fun myRank(
        succession: List<String>,
        deadHostId: String,
        selfId: String,
        aliveIds: Set<String>,
    ): Int? {
        if (selfId !in succession || selfId == deadHostId) return null
        val order = candidates(succession, deadHostId, aliveIds + selfId)
        val index = order.indexOf(selfId)
        return if (index >= 0) index else null
    }

    fun promotionDelayMs(rank: Int): Long = rank * PROMOTION_STAGGER_MS

    /**
     * Split-brain resolution: while (or after) self-promoting, if another new
     * host for the same ride shows up, the better-ranked one wins; equal ranks
     * fall back to smallest id so both sides agree without talking.
     *
     * @return true if *we* should step down and join the other host.
     */
    fun shouldStepDown(
        myRank: Int,
        otherRank: Int?,
        myId: String,
        otherId: String,
    ): Boolean {
        if (otherId == myId) return false
        return when {
            otherRank == null -> false
            otherRank < myRank -> true
            otherRank > myRank -> false
            else -> otherId < myId
        }
    }
}
