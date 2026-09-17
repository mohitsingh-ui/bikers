package com.ridesync.app.audio

/**
 * Automatic music ducking.
 *
 * When any rider is speaking, music gain drops to [duckLevel]. When everyone
 * goes quiet, it waits [holdMs] then fades back to full over [fadeMs]. Ducking
 * *down* is fast (music gets out of the way immediately); ducking *up* is the
 * gentle fade the spec asks for.
 *
 * Pure, time-driven, and frame-rate independent: [gainAt] is a function of
 * elapsed time, so it can be unit-tested with a virtual clock and called from
 * either the audio thread or a UI animation.
 */
class DuckingController(
    var duckLevel: Float = 0.25f,
    var fadeMs: Int = 1500,
    var holdMs: Int = 1200,
    var enabled: Boolean = true,
) {
    private var speaking = false
    private var stateSince: Long = 0L
    private var gainAtStateChange: Float = 1f
    private var lastGain: Float = 1f

    /** Update whether anyone is talking. Idempotent. */
    fun setSpeaking(isSpeaking: Boolean, nowMs: Long) {
        if (isSpeaking == speaking) return
        gainAtStateChange = lastGain
        speaking = isSpeaking
        stateSince = nowMs
    }

    /** Music gain multiplier in [duckLevel, 1] for the given time. */
    fun gainAt(nowMs: Long): Float {
        if (!enabled) {
            lastGain = 1f
            return 1f
        }
        val elapsed = (nowMs - stateSince).coerceAtLeast(0)
        lastGain = if (speaking) {
            // Fast attack (~80 ms) from wherever we were down to duckLevel.
            val t = (elapsed.toFloat() / ATTACK_MS).coerceIn(0f, 1f)
            lerp(gainAtStateChange, duckLevel, t)
        } else {
            when {
                elapsed < holdMs -> gainAtStateChange // hold at ducked level
                else -> {
                    val t = ((elapsed - holdMs).toFloat() / fadeMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                    lerp(gainAtStateChange, 1f, smoothstep(t))
                }
            }
        }
        return lastGain
    }

    /** True while the gain is still animating toward its target. */
    fun isSettling(nowMs: Long): Boolean {
        if (!enabled) return false
        val g = gainAt(nowMs)
        val target = if (speaking) duckLevel else 1f
        return kotlin.math.abs(g - target) > 0.01f
    }

    fun reset() {
        speaking = false
        stateSince = 0
        gainAtStateChange = 1f
        lastGain = 1f
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun smoothstep(t: Float) = t * t * (3 - 2 * t)

    companion object {
        const val ATTACK_MS = 80f
    }
}
