package com.ridesync.app

import com.ridesync.app.networking.ClockSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockSyncTest {

    @Test
    fun `estimates a constant offset from symmetric round trips`() {
        var now = 1_000_000L
        val clock = ClockSync { now }
        val trueOffset = 5000L // host is 5s ahead of us

        // Simulate 6 pings with symmetric 40ms RTT.
        repeat(6) {
            val t0 = now
            now += 20 // outbound
            val hostTime = now + trueOffset
            now += 20 // inbound
            clock.onPong(t0, hostTime)
        }
        assertTrue("should be synced", clock.isSynced)
        assertTrue("offset ~5000, got ${clock.offsetMs}", kotlin.math.abs(clock.offsetMs - trueOffset) <= 5)
    }

    @Test
    fun `rejects implausible round trips`() {
        var now = 0L
        val clock = ClockSync { now }
        val t0 = now
        now += ClockSync.MAX_PLAUSIBLE_RTT_MS + 500
        clock.onPong(t0, now + 1000)
        assertTrue("outlier should be ignored", !clock.isSynced)
    }

    @Test
    fun `median rejects a single asymmetric outlier`() {
        var now = 0L
        val clock = ClockSync { now }
        val trueOffset = 200L
        // Good samples
        repeat(5) {
            val t0 = now
            now += 10
            val hostTime = now + trueOffset
            now += 10
            clock.onPong(t0, hostTime)
        }
        // One badly asymmetric sample (long inbound leg) — high RTT, so best-half rejects it.
        val t0 = now
        now += 5
        val hostTime = now + trueOffset
        now += 300
        clock.onPong(t0, hostTime)

        assertTrue("offset should stay near true, got ${clock.offsetMs}", kotlin.math.abs(clock.offsetMs - trueOffset) <= 20)
    }

    @Test
    fun `hostNow applies offset`() {
        var now = 500L
        val clock = ClockSync { now }
        clock.addSample(offset = 1234L, rtt = 20L)
        assertEquals(500L + 1234L, clock.hostNow())
        assertEquals(500L, clock.toLocalTime(clock.hostNow()))
    }
}
