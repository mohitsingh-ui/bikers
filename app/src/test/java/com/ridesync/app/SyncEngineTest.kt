package com.ridesync.app

import com.ridesync.app.music.SyncEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {

    private fun engine() = SyncEngine(softThresholdMs = 120, hardThresholdMs = 300)

    private fun state(pos: Long, sampleTime: Long, seq: Long, playing: Boolean = true) =
        SyncEngine.HostState("t1", playing, pos, sampleTime, seq)

    @Test
    fun `ignores out-of-order sync packets`() {
        val e = engine()
        assertTrue(e.onHostSync(state(1000, 0, seq = 5)))
        assertTrue(!e.onHostSync(state(2000, 0, seq = 4)))
        assertEquals(1000, e.current?.positionMs)
    }

    @Test
    fun `expected position advances with host clock while playing`() {
        val e = engine()
        e.onHostSync(state(pos = 1000, sampleTime = 10_000, seq = 1))
        // 500ms later on the host clock
        assertEquals(1500, e.expectedPositionMs(10_500))
    }

    @Test
    fun `no correction within soft threshold`() {
        val e = engine()
        e.onHostSync(state(pos = 1000, sampleTime = 0, seq = 1))
        // expected 1000, actual 1050 -> drift 50 < 120
        assertTrue(e.computeCorrection(actualPositionMs = 1050, hostNowMs = 0) is SyncEngine.Correction.None)
    }

    @Test
    fun `speed nudge in the middle band`() {
        val e = engine()
        e.onHostSync(state(pos = 1000, sampleTime = 0, seq = 1))
        // expected 1000, actual 800 -> behind by 200 (120<=200<300) -> speed up
        val c = e.computeCorrection(actualPositionMs = 800, hostNowMs = 0)
        assertTrue(c is SyncEngine.Correction.Speed)
        assertTrue("should speed up", (c as SyncEngine.Correction.Speed).multiplier > 1f)
    }

    @Test
    fun `hard seek beyond hard threshold`() {
        val e = engine()
        e.onHostSync(state(pos = 5000, sampleTime = 0, seq = 1))
        val c = e.computeCorrection(actualPositionMs = 1000, hostNowMs = 0)
        assertTrue(c is SyncEngine.Correction.Seek)
        assertEquals(5000, (c as SyncEngine.Correction.Seek).toPositionMs)
    }

    @Test
    fun `paused state expects fixed position`() {
        val e = engine()
        e.onHostSync(state(pos = 3000, sampleTime = 0, seq = 1, playing = false))
        assertEquals(3000, e.expectedPositionMs(999_999))
    }

    @Test
    fun `speed nudge is bounded`() {
        val e = engine()
        e.onHostSync(state(pos = 1_000_000, sampleTime = 0, seq = 1))
        val c = e.computeCorrection(actualPositionMs = 999_750, hostNowMs = 0) as SyncEngine.Correction.Speed
        assertTrue(c.multiplier <= 1f + SyncEngine.MAX_SPEED_NUDGE + 0.0001f)
    }
}
