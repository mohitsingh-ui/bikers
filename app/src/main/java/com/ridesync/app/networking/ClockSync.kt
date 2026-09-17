package com.ridesync.app.networking

/**
 * NTP-style offset estimation against the host's clock.
 *
 * The host is the authoritative clock for the ride. Clients ping the host over
 * UDP; each pong yields one (offset, rtt) sample:
 *
 * ```
 * t0 = client send time
 * th = host time when it answered
 * t2 = client receive time
 * rtt = t2 - t0
 * offset ≈ th + rtt/2 - t2      (host clock minus client clock)
 * ```
 *
 * We keep the recent samples and use the median offset of the lowest-RTT half,
 * which rejects the asymmetric-delay outliers Wi-Fi loves to produce.
 *
 * Pure logic + injectable clock so it can be unit-tested deterministically.
 */
class ClockSync(private val now: () -> Long = System::currentTimeMillis) {

    private data class Sample(val offset: Long, val rtt: Long)

    private val samples = ArrayDeque<Sample>()
    private val lock = Any()

    @Volatile
    var offsetMs: Long = 0
        private set

    @Volatile
    var lastRttMs: Long = -1
        private set

    val isSynced: Boolean get() = synchronized(lock) { samples.isNotEmpty() }

    /** Feed a pong that answered our ping sent at [t0] with host time [hostTime]. */
    fun onPong(t0: Long, hostTime: Long) {
        val t2 = now()
        val rtt = t2 - t0
        if (rtt < 0 || rtt > MAX_PLAUSIBLE_RTT_MS) return
        addSample(offset = hostTime + rtt / 2 - t2, rtt = rtt)
    }

    /** Directly add a sample (used by tests and by TCP-carried host times). */
    fun addSample(offset: Long, rtt: Long) {
        synchronized(lock) {
            samples.addLast(Sample(offset, rtt))
            while (samples.size > WINDOW) samples.removeFirst()
            recompute()
        }
        lastRttMs = rtt
    }

    /** A rough sample from the host heartbeat when UDP pongs are not flowing. */
    fun onHostHeartbeat(hostTime: Long) {
        if (!isSynced) addSample(offset = hostTime - now(), rtt = COARSE_RTT_GUESS_MS)
    }

    fun reset() {
        synchronized(lock) {
            samples.clear()
            offsetMs = 0
        }
        lastRttMs = -1
    }

    /** Best estimate of the host's current clock. */
    fun hostNow(): Long = now() + offsetMs

    /** Convert a host timestamp to this device's local clock. */
    fun toLocalTime(hostTime: Long): Long = hostTime - offsetMs

    private fun recompute() {
        if (samples.isEmpty()) return
        val sorted = samples.sortedBy { it.rtt }
        val bestHalf = sorted.take(((sorted.size + 1) / 2).coerceAtLeast(1))
        val offsets = bestHalf.map { it.offset }.sorted()
        offsetMs = offsets[offsets.size / 2]
    }

    companion object {
        const val WINDOW = 10
        const val MAX_PLAUSIBLE_RTT_MS = 2000L
        const val COARSE_RTT_GUESS_MS = 60L
    }
}
