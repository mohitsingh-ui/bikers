package com.ridesync.app.music

import com.ridesync.app.core.RLog
import com.ridesync.app.domain.model.TrackInfo
import com.ridesync.app.domain.model.TrackSource
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Generates a small set of royalty-free demo tracks *on device*, deterministically.
 *
 * Why: synchronized playback needs every phone to hold the *same bytes* for a
 * track. We can't legally rebroadcast copyrighted audio between phones, and we
 * can't assume every rider has the same song file. So RideSync ships a tiny
 * synthesizer: each phone renders identical WAV files from the same seed, and
 * the sync engine then aligns playback position across them. This makes the
 * core "listen together" feature work out of the box with zero rights issues.
 *
 * These are simple ambient loops (chord pads + arpeggios), not an attempt at
 * real music — they exist to demonstrate frame-accurate group sync. Real
 * services plug in via [MediaIntegration].
 */
object DemoTrackGenerator {

    private const val SAMPLE_RATE = 44100
    private const val CHANNELS = 1

    data class Spec(
        val id: String,
        val title: String,
        val artist: String,
        val seconds: Int,
        val rootHz: Double,
        val scale: IntArray,
        val bpm: Int,
    )

    private val specs = listOf(
        Spec("demo_open_road", "Open Road", "RideSync Audio", 96, 220.0, intArrayOf(0, 2, 4, 7, 9), 96),
        Spec("demo_night_ride", "Night Ride", "RideSync Audio", 108, 164.81, intArrayOf(0, 3, 5, 7, 10), 84),
        Spec("demo_tail_wind", "Tail Wind", "RideSync Audio", 84, 261.63, intArrayOf(0, 2, 4, 5, 7, 9), 112),
        Spec("demo_switchbacks", "Switchbacks", "RideSync Audio", 120, 196.0, intArrayOf(0, 2, 3, 7, 8), 128),
    )

    /** Metadata for the demo playlist (no files needed to show the list). */
    fun playlistMetadata(): List<TrackInfo> = specs.map {
        TrackInfo(
            id = it.id,
            title = it.title,
            artist = it.artist,
            durationMs = it.seconds * 1000L,
            source = TrackSource.DEMO,
        )
    }

    /**
     * Ensures every demo track exists as a WAV in [dir], rendering any that are
     * missing. Deterministic: the same spec always produces identical bytes, so
     * all riders' files match. Returns tracks with populated file URIs.
     */
    fun ensureTracks(dir: File): List<TrackInfo> {
        if (!dir.exists()) dir.mkdirs()
        return specs.map { spec ->
            val file = File(dir, "${spec.id}.wav")
            if (!file.exists() || file.length() < 44) {
                runCatching { render(spec, file) }
                    .onFailure { RLog.w(RLog.Cat.MUSIC, "render ${spec.id} failed", it as? Exception) }
            }
            TrackInfo(
                id = spec.id,
                title = spec.title,
                artist = spec.artist,
                durationMs = spec.seconds * 1000L,
                source = TrackSource.DEMO,
                uri = file.toURI().toString(),
            )
        }
    }

    private fun render(spec: Spec, file: File) {
        val totalSamples = spec.seconds * SAMPLE_RATE
        val dataBytes = totalSamples * 2
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            writeWavHeader(raf, dataBytes)

            val secondsPerBeat = 60.0 / spec.bpm
            val samplesPerBeat = (secondsPerBeat * SAMPLE_RATE).toInt()
            val chunk = ShortArray(4096)
            var written = 0
            var idx = 0
            while (written < totalSamples) {
                val n = min(chunk.size, totalSamples - written)
                for (i in 0 until n) {
                    val t = idx.toDouble() / SAMPLE_RATE
                    val beat = idx / samplesPerBeat
                    chunk[i] = sampleAt(spec, t, beat, idx, samplesPerBeat)
                    idx++
                }
                writeShorts(raf, chunk, n)
                written += n
            }
        }
    }

    private fun sampleAt(spec: Spec, t: Double, beat: Int, idx: Int, samplesPerBeat: Int): Short {
        // Pad chord: root + third + fifth from the scale, slow tremolo.
        val chordDegrees = intArrayOf(
            spec.scale[0],
            spec.scale[2 % spec.scale.size],
            spec.scale[4 % spec.scale.size],
        )
        var value = 0.0
        for ((k, deg) in chordDegrees.withIndex()) {
            val freq = spec.rootHz * semitone(deg)
            value += sin(2 * PI * freq * t) * (0.16 - k * 0.03)
        }
        // Arpeggio voice, one scale step per beat, with a short pluck envelope.
        val arpDeg = spec.scale[beat % spec.scale.size]
        val arpFreq = spec.rootHz * 2 * semitone(arpDeg)
        val posInBeat = (idx % samplesPerBeat).toDouble() / samplesPerBeat
        val pluckEnv = Math.exp(-4.0 * posInBeat)
        value += sin(2 * PI * arpFreq * t) * 0.22 * pluckEnv

        // Gentle master tremolo + soft limit.
        value *= 0.85 + 0.15 * sin(2 * PI * 0.15 * t)
        val clipped = when {
            value > 1 -> 1.0
            value < -1 -> -1.0
            else -> value
        }
        return (clipped * 26000).toInt().toShort()
    }

    private fun semitone(steps: Int): Double = Math.pow(2.0, steps / 12.0)

    private fun writeWavHeader(raf: RandomAccessFile, dataBytes: Int) {
        val byteRate = SAMPLE_RATE * CHANNELS * 2
        raf.writeBytes("RIFF")
        writeIntLE(raf, 36 + dataBytes)
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        writeIntLE(raf, 16)
        writeShortLE(raf, 1) // PCM
        writeShortLE(raf, CHANNELS)
        writeIntLE(raf, SAMPLE_RATE)
        writeIntLE(raf, byteRate)
        writeShortLE(raf, CHANNELS * 2)
        writeShortLE(raf, 16)
        raf.writeBytes("data")
        writeIntLE(raf, dataBytes)
    }

    private fun writeShorts(raf: RandomAccessFile, data: ShortArray, n: Int) {
        val bytes = ByteArray(n * 2)
        var j = 0
        for (i in 0 until n) {
            val s = data[i].toInt()
            bytes[j++] = (s and 0xFF).toByte()
            bytes[j++] = (s shr 8 and 0xFF).toByte()
        }
        raf.write(bytes)
    }

    private fun writeIntLE(raf: RandomAccessFile, v: Int) {
        raf.write(byteArrayOf(
            (v and 0xFF).toByte(),
            (v shr 8 and 0xFF).toByte(),
            (v shr 16 and 0xFF).toByte(),
            (v shr 24 and 0xFF).toByte(),
        ))
    }

    private fun writeShortLE(raf: RandomAccessFile, v: Int) {
        raf.write(byteArrayOf((v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte()))
    }
}
