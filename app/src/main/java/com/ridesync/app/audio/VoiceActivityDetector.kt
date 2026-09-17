package com.ridesync.app.audio

import kotlin.math.sqrt

/**
 * Lightweight energy-based voice activity detector for Open Intercom mode.
 *
 * Tracks a slow noise floor and opens the gate when short-term RMS rises a set
 * margin above it, with hangover frames so speech isn't chopped between words.
 * Cheap enough to run on every 20 ms frame. Pure logic, unit-testable.
 */
class VoiceActivityDetector(
    /** Linear energy margin over the noise floor to trigger (0..1 scale). */
    var threshold: Float = 0.05f,
    private val hangoverFrames: Int = 12, // ~240 ms
) {
    private var noiseFloor = 0.01f
    private var hangover = 0
    private var active = false

    val isActive: Boolean get() = active

    /** @return true if this frame should be transmitted. */
    fun process(pcm: ShortArray, frameSamples: Int): Boolean {
        val rms = rms(pcm, frameSamples)
        // Adapt the noise floor only while quiet, and only downward-ish.
        if (!active) {
            noiseFloor = if (rms < noiseFloor) {
                lerp(noiseFloor, rms, 0.3f)
            } else {
                lerp(noiseFloor, rms, 0.02f)
            }
        }
        val speech = rms > noiseFloor + threshold
        if (speech) {
            hangover = hangoverFrames
            active = true
        } else if (hangover > 0) {
            hangover--
            active = true
        } else {
            active = false
        }
        return active
    }

    fun reset() {
        noiseFloor = 0.01f
        hangover = 0
        active = false
    }

    private fun rms(pcm: ShortArray, n: Int): Float {
        if (n == 0) return 0f
        var sum = 0.0
        for (i in 0 until n) {
            val v = pcm[i] / 32768.0
            sum += v * v
        }
        return sqrt(sum / n).toFloat()
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
