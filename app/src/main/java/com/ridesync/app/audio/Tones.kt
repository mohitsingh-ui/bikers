package com.ridesync.app.audio

import android.media.AudioManager
import android.media.ToneGenerator
import com.ridesync.app.core.RLog

/**
 * Short confirmation tones at the start/end of a transmission and for alerts,
 * so riders get audible feedback through the headset without looking at the
 * screen. Uses the platform [ToneGenerator] on the voice-call stream so it sits
 * with the intercom audio.
 */
class Tones {

    private val generator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
    } catch (e: Exception) {
        RLog.w(RLog.Cat.AUDIO, "tone generator unavailable", e)
        null
    }

    fun talkStart() = play(ToneGenerator.TONE_PROP_BEEP, 90)
    fun talkEnd() = play(ToneGenerator.TONE_PROP_ACK, 70)
    fun alert() = play(ToneGenerator.TONE_PROP_BEEP2, 150)
    fun emergency() = play(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)

    private fun play(tone: Int, durationMs: Int) {
        val gen = generator ?: return
        try {
            gen.startTone(tone, durationMs)
        } catch (e: Exception) {
            RLog.d(RLog.Cat.AUDIO, "tone failed: ${e.message}")
        }
    }

    fun release() {
        runCatching { generator?.release() }
    }
}
