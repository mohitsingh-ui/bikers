package com.ridesync.app.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.ridesync.app.core.RLog
import com.ridesync.app.data.preferences.Settings
import com.ridesync.app.data.preferences.VoiceCodecChoice
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The real-time voice path.
 *
 * Capture thread:   AudioRecord -> (AEC/NS/AGC) -> gain -> [VAD] -> encode ->
 *                   [onFrameEncoded] -> network.
 * Playback thread:  network frames land in per-sender [JitterBuffer]s -> decode
 *                   -> mix -> AudioTrack, at a fixed 20 ms cadence.
 *
 * The mixer sums all active speakers (RideSync is a conference, not a
 * one-at-a-time radio), soft-clipping to avoid distortion when several riders
 * talk at once. Music ducking lives outside this class — the [MusicController]
 * reads the shared [anySpeaking] signal.
 *
 * Both threads are plain high-priority threads, not coroutines: audio glitches
 * come from scheduling jitter, and a dedicated thread with a tight loop is the
 * most predictable option.
 */
class VoiceEngine(
    private val onFrameEncoded: (payload: ByteArray, length: Int, isPcm: Boolean, flags: Int) -> Unit,
) {
    /** True whenever any local or remote speaker is active — drives ducking. */
    private val _anySpeaking = MutableStateFlow(false)
    val anySpeaking: StateFlow<Boolean> = _anySpeaking

    /** Keys of remote riders currently producing audio (for talk indicators). */
    private val _activeRemoteKeys = MutableStateFlow<Set<Int>>(emptySet())
    val activeRemoteKeys: StateFlow<Set<Int>> = _activeRemoteKeys

    @Volatile private var settings: Settings = Settings()
    @Volatile private var running = false
    @Volatile private var transmitting = false
    @Volatile private var openIntercom = false

    // Per-sender playback state
    private class RemoteVoice {
        val jitter = JitterBuffer()
        var decoder: VoiceDecoder = OpusFactory.createDecoder() ?: PcmPassthroughCodec()
        var pcmDecoder: VoiceDecoder = PcmPassthroughCodec()
        @Volatile var lastPacketMs: Long = 0
        @Volatile var localMuted: Boolean = false
        @Volatile var localVolume: Float = 1f
    }

    private val remotes = ConcurrentHashMap<Int, RemoteVoice>()

    private var captureThread: Thread? = null
    private var playbackThread: Thread? = null

    // Capture chain
    private var record: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var encoder: VoiceEncoder = PcmPassthroughCodec()
    private val vad = VoiceActivityDetector()

    // Playback
    private var track: AudioTrack? = null

    fun updateSettings(newSettings: Settings) {
        settings = newSettings
        vad.threshold = newSettings.vadThreshold
        openIntercom = newSettings.commMode == com.ridesync.app.domain.model.CommMode.OPEN_INTERCOM
    }

    fun start(initial: Settings) {
        if (running) return
        settings = initial
        openIntercom = initial.commMode == com.ridesync.app.domain.model.CommMode.OPEN_INTERCOM
        running = true
        startPlayback()
        startCapture()
        RLog.i(RLog.Cat.VOICE, "voice engine started (codec=${initial.voiceCodec})")
    }

    fun stop() {
        running = false
        transmitting = false
        captureThread?.join(500)
        playbackThread?.join(500)
        captureThread = null
        playbackThread = null
        releaseCapture()
        releasePlayback()
        remotes.clear()
        _anySpeaking.value = false
        _activeRemoteKeys.value = emptySet()
        RLog.i(RLog.Cat.VOICE, "voice engine stopped")
    }

    /** Push-to-talk gate. In open intercom this is ignored (VAD decides). */
    fun setTransmitting(on: Boolean) {
        if (transmitting == on) return
        transmitting = on
        updateSpeakingState()
    }

    fun setLocalMute(key: Int, muted: Boolean) {
        remotes.getOrPut(key) { RemoteVoice() }.localMuted = muted
    }

    fun setLocalVolume(key: Int, volume: Float) {
        remotes.getOrPut(key) { RemoteVoice() }.localVolume = volume.coerceIn(0f, 1f)
    }

    /** Feed a decoded remote voice datagram in from the network layer. */
    fun onRemoteVoice(senderKey: Int, seq: Int, isPcm: Boolean, payload: ByteArray) {
        if (!running) return
        val remote = remotes.getOrPut(senderKey) { RemoteVoice() }
        remote.jitter.offer(seq, payload, isPcm)
        remote.lastPacketMs = System.currentTimeMillis()
    }

    fun forgetRemote(key: Int) {
        remotes.remove(key)?.let {
            it.decoder.release()
            it.pcmDecoder.release()
        }
        updateActiveRemotes()
    }

    // -------------------------------------------------------------- capture

    @SuppressLint("MissingPermission")
    private fun startCapture() {
        captureThread = thread(name = "RideSync-VoiceCapture", priority = Thread.MAX_PRIORITY) {
            val minBuf = AudioRecord.getMinBufferSize(
                VoiceFormat.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(VoiceFormat.FRAME_SAMPLES * 8)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    VoiceFormat.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf,
                )
            } catch (e: Exception) {
                RLog.e(RLog.Cat.VOICE, "AudioRecord init failed", e)
                return@thread
            }
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                RLog.e(RLog.Cat.VOICE, "AudioRecord not initialized")
                recorder.release()
                return@thread
            }
            record = recorder
            attachEffects(recorder.audioSessionId)
            encoder = buildEncoder()

            val frame = ShortArray(VoiceFormat.FRAME_SAMPLES)
            val encoded = ByteArray(VoiceFormat.MAX_ENCODED_BYTES)
            var wasSending = false

            try {
                recorder.startRecording()
            } catch (e: Exception) {
                RLog.e(RLog.Cat.VOICE, "startRecording failed", e)
                return@thread
            }

            while (running) {
                val read = recorder.read(frame, 0, VoiceFormat.FRAME_SAMPLES)
                if (read <= 0) continue

                applyGain(frame, read, settings.micSensitivity)

                val gateOpen = if (openIntercom) {
                    vad.process(frame, read)
                } else {
                    transmitting
                }

                if (gateOpen) {
                    val len = encoder.encode(frame, read, encoded)
                    if (len > 0) {
                        var flags = 0
                        if (!wasSending) flags = flags or com.ridesync.app.networking.protocol.VoicePackets.FLAG_START
                        if (encoder.isPcm) flags = flags or com.ridesync.app.networking.protocol.VoicePackets.FLAG_PCM
                        onFrameEncoded(encoded, len, encoder.isPcm, flags)
                    }
                    wasSending = true
                    if (openIntercom) updateSpeakingState()
                } else if (wasSending) {
                    // Send an end marker frame so peers stop cleanly.
                    onFrameEncoded(
                        ByteArray(0), 0, encoder.isPcm,
                        com.ridesync.app.networking.protocol.VoicePackets.FLAG_END,
                    )
                    wasSending = false
                    if (openIntercom) updateSpeakingState()
                }
            }
            runCatching { recorder.stop() }
        }
    }

    private fun buildEncoder(): VoiceEncoder {
        return if (settings.voiceCodec == VoiceCodecChoice.OPUS) {
            OpusFactory.createEncoder() ?: PcmPassthroughCodec()
        } else {
            PcmPassthroughCodec()
        }
    }

    private fun attachEffects(sessionId: Int) {
        try {
            if (settings.echoCancellation && AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
            if (settings.noiseSuppression && NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
            if (settings.autoGainControl && AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }
            RLog.d(RLog.Cat.VOICE, "effects aec=${aec != null} ns=${ns != null} agc=${agc != null}")
        } catch (e: Exception) {
            RLog.w(RLog.Cat.VOICE, "audio effects attach failed", e)
        }
    }

    private fun applyGain(frame: ShortArray, n: Int, gain: Float) {
        if (gain == 1f) return
        for (i in 0 until n) {
            val v = (frame[i] * gain).toInt().coerceIn(-32768, 32767)
            frame[i] = v.toShort()
        }
    }

    // ------------------------------------------------------------- playback

    private fun startPlayback() {
        playbackThread = thread(name = "RideSync-VoicePlayback", priority = Thread.MAX_PRIORITY) {
            val minBuf = AudioTrack.getMinBufferSize(
                VoiceFormat.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(VoiceFormat.FRAME_SAMPLES * 8)

            val audioTrack = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(VoiceFormat.SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e: Exception) {
                RLog.e(RLog.Cat.VOICE, "AudioTrack init failed", e)
                return@thread
            }
            track = audioTrack
            audioTrack.play()

            val mixBuffer = IntArray(VoiceFormat.FRAME_SAMPLES)
            val outBuffer = ShortArray(VoiceFormat.FRAME_SAMPLES)
            val decodeBuffer = ShortArray(VoiceFormat.FRAME_SAMPLES)
            val frameIntervalNs = VoiceFormat.FRAME_MS * 1_000_000L
            var nextTick = System.nanoTime()

            while (running) {
                java.util.Arrays.fill(mixBuffer, 0)
                var activeCount = 0
                val nowMs = System.currentTimeMillis()

                for ((key, remote) in remotes) {
                    if (remote.localMuted) {
                        remote.jitter.poll() // keep draining so it doesn't back up
                        continue
                    }
                    val decoded = when (val out = remote.jitter.poll()) {
                        is JitterBuffer.Output.Packet -> {
                            val dec = if (out.frame.isPcm) remote.pcmDecoder else remote.decoder
                            val n = dec.decode(out.frame.data, out.frame.data.size, decodeBuffer)
                            n
                        }

                        JitterBuffer.Output.Conceal -> remote.decoder.decode(null, 0, decodeBuffer)
                        JitterBuffer.Output.Silence -> 0
                    }
                    if (decoded > 0) {
                        activeCount++
                        val vol = remote.localVolume
                        val limit = minOf(decoded, VoiceFormat.FRAME_SAMPLES)
                        for (i in 0 until limit) {
                            mixBuffer[i] += (decodeBuffer[i] * vol).toInt()
                        }
                    }
                }

                // Soft-clip the sum and apply master voice volume.
                val voiceVol = settings.voiceVolume
                for (i in mixBuffer.indices) {
                    val mixed = (mixBuffer[i] * voiceVol)
                    outBuffer[i] = softClip(mixed)
                }

                audioTrack.write(outBuffer, 0, VoiceFormat.FRAME_SAMPLES)
                updateActiveRemotes()

                // Pace to the frame clock without busy-spinning.
                nextTick += frameIntervalNs
                val sleep = nextTick - System.nanoTime()
                if (sleep > 0) {
                    java.util.concurrent.locks.LockSupport.parkNanos(sleep)
                } else {
                    nextTick = System.nanoTime()
                }
            }
            runCatching { audioTrack.stop() }
        }
    }

    private fun softClip(sample: Float): Short {
        // Simple tanh-like soft clip to keep multi-speaker sums pleasant.
        val x = sample / 32768f
        val clipped = when {
            x > 1f -> 1f - (1f / (x + 1f))
            x < -1f -> -1f + (1f / (-x + 1f))
            else -> x
        }
        return (clipped * 32767f).toInt().coerceIn(-32768, 32767).toShort()
    }

    private fun updateActiveRemotes() {
        val now = System.currentTimeMillis()
        val active = remotes.entries
            .filter { now - it.value.lastPacketMs < REMOTE_ACTIVE_WINDOW_MS && !it.value.localMuted }
            .map { it.key }
            .toSet()
        if (active != _activeRemoteKeys.value) {
            _activeRemoteKeys.value = active
            updateSpeakingState()
        }
    }

    private fun updateSpeakingState() {
        val localSpeaking = if (openIntercom) vad.isActive else transmitting
        val speaking = localSpeaking || _activeRemoteKeys.value.isNotEmpty()
        if (speaking != _anySpeaking.value) {
            _anySpeaking.value = speaking
        }
    }

    private fun releaseCapture() {
        runCatching { record?.release() }
        record = null
        runCatching { aec?.release() }
        runCatching { ns?.release() }
        runCatching { agc?.release() }
        aec = null; ns = null; agc = null
        encoder.release()
    }

    private fun releasePlayback() {
        runCatching { track?.release() }
        track = null
    }

    companion object {
        const val REMOTE_ACTIVE_WINDOW_MS = 400L
    }
}
