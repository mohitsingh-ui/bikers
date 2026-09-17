package com.ridesync.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import com.ridesync.app.core.RLog
import com.ridesync.app.domain.model.QuickAlertKind
import java.util.ArrayDeque
import java.util.Locale

/**
 * Speaks short ride messages aloud with Android's text-to-speech engine, so
 * quick alerts and emergencies are heard, not just shown — e.g. a spoken
 * "Slow down, from Ravi" on every rider's phone.
 *
 * Speech is played on the navigation-guidance audio stream, so it ducks the
 * shared music (and mixes with voice) instead of talking over it.
 *
 * Everything here is wrapped defensively: TTS can be missing, still loading, or
 * fail on a given device, and none of that should ever crash the app or a ride.
 * Messages spoken before the engine is ready are queued briefly and flushed on
 * init.
 */
class SpeechAnnouncer(context: Context) {

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    // Whether the user wants spoken alerts. Emergencies always speak.
    @Volatile var enabled: Boolean = true

    private val pending = ArrayDeque<Utterance>()

    private data class Utterance(val text: String, val urgent: Boolean)

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                onInit(status)
            }
        }.onFailure { RLog.w(RLog.Cat.AUDIO, "TTS unavailable", it) }
    }

    private fun onInit(status: Int) {
        runCatching {
            val engine = tts ?: return
            if (status != TextToSpeech.SUCCESS) {
                RLog.w(RLog.Cat.AUDIO, "TTS init failed (status=$status)")
                return
            }
            // Prefer the device language, fall back to English, then default.
            val wanted = Locale.getDefault()
            val res = engine.setLanguage(wanted)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.ENGLISH)
            }
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            ready = true
            RLog.i(RLog.Cat.AUDIO, "TTS ready")
            // Flush anything queued while loading.
            val flush: List<Utterance>
            synchronized(pending) { flush = pending.toList(); pending.clear() }
            flush.forEach { speakNow(it) }
        }.onFailure { RLog.w(RLog.Cat.AUDIO, "TTS onInit error", it) }
    }

    // --------------------------------------------------------------- public

    /** Speak a quick alert someone in the ride sent. */
    fun announceAlert(kind: QuickAlertKind, riderName: String) {
        if (!enabled) return
        speak(alertPhrase(kind, riderName), urgent = false)
    }

    /** Speak an alert I just sent myself (no name — I know it was me). */
    fun announceOwnAlert(kind: QuickAlertKind) {
        if (!enabled) return
        speak(actionPhrase(kind), urgent = false)
    }

    /**
     * Emergency speaks loudly and jumps the queue. It still respects [enabled]
     * so that turning off "Spoken voice alerts" is a complete kill switch for
     * text-to-speech (the emergency banner, tone and vibration remain).
     */
    fun announceEmergency(riderName: String) {
        if (!enabled) return
        val who = riderName.ifBlank { "A rider" }
        speak("Emergency! $who needs help.", urgent = true)
    }

    /** Optional freeform announcement (e.g. ride started). */
    fun announce(text: String) {
        if (!enabled) return
        speak(text, urgent = false)
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }

    // -------------------------------------------------------------- internal

    private fun speak(text: String, urgent: Boolean) {
        if (text.isBlank()) return
        val u = Utterance(text, urgent)
        if (!ready || tts == null) {
            synchronized(pending) { if (pending.size < 8) pending.addLast(u) }
            return
        }
        speakNow(u)
    }

    private fun speakNow(u: Utterance) {
        runCatching {
            val mode = if (u.urgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(u.text, mode, null, "rs-${System.nanoTime()}")
        }.onFailure { RLog.w(RLog.Cat.AUDIO, "TTS speak failed", it) }
    }

    private fun alertPhrase(kind: QuickAlertKind, riderName: String): String {
        val who = riderName.ifBlank { "A rider" }
        return "${actionPhrase(kind)}, from $who."
    }

    private fun actionPhrase(kind: QuickAlertKind): String = when (kind) {
        QuickAlertKind.STOPPING -> "Stopping"
        QuickAlertKind.FUEL -> "Fuel stop"
        QuickAlertKind.BREAK -> "Taking a break"
        QuickAlertKind.SLOW_DOWN -> "Slow down"
        QuickAlertKind.TURNING -> "Turning"
        QuickAlertKind.HAZARD -> "Hazard ahead"
    }
}
