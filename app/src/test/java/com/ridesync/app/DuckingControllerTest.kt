package com.ridesync.app

import com.ridesync.app.audio.DuckingController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckingControllerTest {

    @Test
    fun `full volume when idle`() {
        val d = DuckingController(duckLevel = 0.25f, fadeMs = 1000, holdMs = 500)
        assertEquals(1f, d.gainAt(0), 0.001f)
    }

    @Test
    fun `ducks quickly when speaking starts`() {
        val d = DuckingController(duckLevel = 0.25f, fadeMs = 1000, holdMs = 500)
        d.setSpeaking(true, 0)
        // After the attack window it should be at duck level.
        assertEquals(0.25f, d.gainAt(DuckingController.ATTACK_MS.toLong() + 5), 0.02f)
    }

    @Test
    fun `holds ducked level then fades back after speaking stops`() {
        val d = DuckingController(duckLevel = 0.2f, fadeMs = 1000, holdMs = 500)
        d.setSpeaking(true, 0)
        val ducked = d.gainAt(200)
        d.setSpeaking(false, 200)
        // During hold, still ducked.
        assertEquals(ducked, d.gainAt(300), 0.05f)
        // Well after hold + fade, back to full.
        assertEquals(1f, d.gainAt(200 + 500 + 1000 + 50), 0.02f)
        // Midway through the fade it should be between duck and full.
        val mid = d.gainAt(200 + 500 + 500)
        assertTrue("mid gain $mid should be between duck and full", mid > 0.2f && mid < 1f)
    }

    @Test
    fun `disabled controller is always full volume`() {
        val d = DuckingController(enabled = false)
        d.setSpeaking(true, 0)
        assertEquals(1f, d.gainAt(1000), 0.001f)
    }

    @Test
    fun `gain never drops below duck level`() {
        val d = DuckingController(duckLevel = 0.3f, fadeMs = 500, holdMs = 200)
        d.setSpeaking(true, 0)
        for (t in 0..2000 step 50) {
            assertTrue(d.gainAt(t.toLong()) >= 0.3f - 0.001f)
        }
    }
}
