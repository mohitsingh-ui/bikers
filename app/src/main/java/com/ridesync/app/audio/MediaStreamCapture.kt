package com.ridesync.app.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import com.ridesync.app.core.RLog
import kotlin.concurrent.thread

/**
 * Captures the audio being played by OTHER apps on this device (Android 10+),
 * via the AudioPlaybackCapture API, and hands each 20 ms frame of 16 kHz mono
 * PCM to [onFrame]. Used by the host to stream "whatever's playing on my phone"
 * to the other riders.
 *
 * HONEST LIMITATION: apps that play protected content — YouTube Music, Spotify,
 * Netflix and most commercial streaming apps — deliberately opt OUT of capture,
 * so their audio arrives here as SILENCE. Apps that allow capture (many local
 * music players, browsers, games, system sounds) are captured normally. This is
 * an OS/DRM restriction, not something the app can bypass.
 *
 * Requires: a MediaProjection consent granted by the user, and a foreground
 * service of type mediaProjection running (see RideSessionService).
 */
@RequiresApi(Build.VERSION_CODES.Q)
class MediaStreamCapture(
    private val projection: MediaProjection,
    private val onFrame: (pcm: ShortArray, samples: Int) -> Unit,
) {
    private var record: AudioRecord? = null
    private var captureThread: Thread? = null

    @Volatile
    private var running = false

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        return try {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(VoiceFormat.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()

            val minBuf = AudioRecord.getMinBufferSize(
                VoiceFormat.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(VoiceFormat.FRAME_SAMPLES * 8)

            val recorder = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBuf)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                RLog.e(RLog.Cat.MUSIC, "media capture AudioRecord not initialized")
                return false
            }

            record = recorder
            running = true
            recorder.startRecording()
            captureThread = thread(name = "RideSync-MediaCapture", priority = Thread.NORM_PRIORITY + 2) {
                val frame = ShortArray(VoiceFormat.FRAME_SAMPLES)
                while (running) {
                    val n = try {
                        recorder.read(frame, 0, VoiceFormat.FRAME_SAMPLES)
                    } catch (e: Exception) {
                        RLog.w(RLog.Cat.MUSIC, "media read failed", e)
                        -1
                    }
                    when {
                        n > 0 -> onFrame(frame, n)
                        n < 0 -> break
                    }
                }
                runCatching { recorder.stop() }
            }
            RLog.i(RLog.Cat.MUSIC, "media capture started (phone audio sharing)")
            true
        } catch (e: Exception) {
            RLog.e(RLog.Cat.MUSIC, "media capture failed to start", e)
            running = false
            false
        }
    }

    fun stop() {
        running = false
        captureThread?.join(300)
        captureThread = null
        runCatching { record?.release() }
        record = null
        runCatching { projection.stop() }
        RLog.i(RLog.Cat.MUSIC, "media capture stopped")
    }
}
