package com.ridesync.app

import com.ridesync.app.audio.VoiceActivityDetector
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class VoiceActivityDetectorTest {

    private fun silence(n: Int = 320) = ShortArray(n) { 0 }

    private fun tone(amplitude: Int, n: Int = 320) = ShortArray(n) { i ->
        (amplitude * sin(2 * Math.PI * 300 * i / 16000.0)).toInt().toShort()
    }

    @Test
    fun `stays closed on silence`() {
        val vad = VoiceActivityDetector(threshold = 0.05f)
        repeat(20) { assertTrue(!vad.process(silence(), 320)) }
    }

    @Test
    fun `opens on loud speech`() {
        val vad = VoiceActivityDetector(threshold = 0.05f)
        // Let noise floor settle on silence first.
        repeat(10) { vad.process(silence(), 320) }
        val opened = vad.process(tone(12000), 320)
        assertTrue("VAD should open on loud tone", opened)
    }

    @Test
    fun `hangover keeps gate open briefly after speech stops`() {
        val vad = VoiceActivityDetector(threshold = 0.05f, hangoverFrames = 5)
        repeat(10) { vad.process(silence(), 320) }
        vad.process(tone(12000), 320) // open
        // Immediately quiet — should stay active for hangover frames.
        assertTrue(vad.process(silence(), 320))
        assertTrue(vad.isActive)
    }
}
