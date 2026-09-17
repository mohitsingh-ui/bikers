package com.ridesync.app.audio

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ridesync.app.core.RLog

/**
 * Distinct haptic patterns for the key ride interactions. All patterns are
 * short and coarse — riders feel them through gloves, and battery matters.
 * Every call is a no-op when haptics are disabled or unavailable.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        RLog.w(RLog.Cat.AUDIO, "vibrator unavailable", e)
        null
    }

    @Volatile
    var enabled: Boolean = true

    /** PTT pressed — crisp single tick. */
    fun talkStart() = oneShot(28, strong = true)

    /** PTT released — softer, shorter tick (distinct from start). */
    fun talkEnd() = oneShot(16, strong = false)

    /** A rider joined/left — gentle double tap. */
    fun riderChange() = pattern(longArrayOf(0, 20, 80, 20), intensities = intArrayOf(0, 120, 0, 120))

    /** Quick alert received — medium buzz. */
    fun alert() = oneShot(60, strong = true)

    /** Emergency — strong, unmistakable triple pulse. */
    fun emergency() = pattern(
        longArrayOf(0, 120, 90, 120, 90, 200),
        intensities = intArrayOf(0, 255, 0, 255, 0, 255),
    )

    private fun oneShot(ms: Long, strong: Boolean) {
        if (!enabled) return
        val vib = vibrator ?: return
        try {
            val amplitude = if (strong) VibrationEffect.DEFAULT_AMPLITUDE else 90
            vib.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        } catch (e: Exception) {
            RLog.d(RLog.Cat.AUDIO, "haptic failed: ${e.message}")
        }
    }

    private fun pattern(timings: LongArray, intensities: IntArray) {
        if (!enabled) return
        val vib = vibrator ?: return
        try {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect.createWaveform(timings, intensities, -1)
            } else {
                @Suppress("DEPRECATION")
                return vib.vibrate(timings, -1)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vib.vibrate(effect)
            } else {
                vib.vibrate(effect)
            }
        } catch (e: Exception) {
            RLog.d(RLog.Cat.AUDIO, "haptic pattern failed: ${e.message}")
        }
    }
}
