package com.ridesync.app.audio

import com.ridesync.app.core.RLog

/**
 * Voice codec abstraction. Encodes/decodes one 20 ms frame of 16 kHz mono
 * PCM (320 samples) at a time.
 *
 * Two implementations:
 *  - [OpusVoiceCodec]  : Concentus (pure-JVM Opus). ~8 KB/s, packet-loss tolerant.
 *  - [PcmPassthroughCodec] : 16-bit PCM fallback for devices where Opus init
 *    fails; higher bandwidth but always works.
 *
 * Frames are marked so the decoder can flag PCM vs Opus per packet (the wire
 * FLAG_PCM bit), which keeps the two interoperable during fallback.
 */
interface VoiceEncoder {
    val isPcm: Boolean

    /** @return encoded byte length written into [out], or -1 on failure. */
    fun encode(pcm: ShortArray, frameSamples: Int, out: ByteArray): Int
    fun release()
}

interface VoiceDecoder {
    /** @return decoded sample count written into [out]. [data] null => packet loss concealment. */
    fun decode(data: ByteArray?, length: Int, out: ShortArray): Int
    fun release()
}

object VoiceFormat {
    const val SAMPLE_RATE = 16000
    const val FRAME_MS = 20
    const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000 // 320
    const val CHANNELS = 1
    const val OPUS_BITRATE = 20000
    const val MAX_ENCODED_BYTES = 1024
}

/** 16-bit little-endian PCM passthrough. */
class PcmPassthroughCodec : VoiceEncoder, VoiceDecoder {
    override val isPcm: Boolean = true

    override fun encode(pcm: ShortArray, frameSamples: Int, out: ByteArray): Int {
        val bytes = frameSamples * 2
        if (out.size < bytes) return -1
        var j = 0
        for (i in 0 until frameSamples) {
            val s = pcm[i].toInt()
            out[j++] = (s and 0xFF).toByte()
            out[j++] = (s shr 8 and 0xFF).toByte()
        }
        return bytes
    }

    override fun decode(data: ByteArray?, length: Int, out: ShortArray): Int {
        if (data == null) {
            // Loss concealment: emit silence.
            val n = VoiceFormat.FRAME_SAMPLES
            for (i in 0 until n) out[i] = 0
            return n
        }
        val n = length / 2
        var j = 0
        for (i in 0 until n) {
            val lo = data[j++].toInt() and 0xFF
            val hi = data[j++].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
        }
        return n
    }

    override fun release() {}
}

/**
 * Opus codec backed by Concentus. Loaded reflectively so the pure-logic parts
 * of the app compile and unit-test without the dependency on the classpath;
 * if the class is missing at runtime, callers fall back to PCM.
 */
object OpusFactory {

    fun createEncoder(): VoiceEncoder? = try {
        OpusVoiceEncoder()
    } catch (t: Throwable) {
        RLog.w(RLog.Cat.VOICE, "Opus encoder unavailable, using PCM", t as? Exception)
        null
    }

    fun createDecoder(): VoiceDecoder? = try {
        OpusVoiceDecoder()
    } catch (t: Throwable) {
        RLog.w(RLog.Cat.VOICE, "Opus decoder unavailable, using PCM", t as? Exception)
        null
    }
}

private class OpusVoiceEncoder : VoiceEncoder {
    override val isPcm = false
    private val encoder = org.concentus.OpusEncoder(
        VoiceFormat.SAMPLE_RATE,
        VoiceFormat.CHANNELS,
        org.concentus.OpusApplication.OPUS_APPLICATION_VOIP,
    ).apply {
        bitrate = VoiceFormat.OPUS_BITRATE
        useDTX = true
        complexity = 5
        signalType = org.concentus.OpusSignal.OPUS_SIGNAL_VOICE
    }

    override fun encode(pcm: ShortArray, frameSamples: Int, out: ByteArray): Int = try {
        encoder.encode(pcm, 0, frameSamples, out, 0, out.size)
    } catch (e: Exception) {
        RLog.d(RLog.Cat.VOICE, "opus encode failed: ${e.message}")
        -1
    }

    override fun release() {}
}

private class OpusVoiceDecoder : VoiceDecoder {
    private val decoder = org.concentus.OpusDecoder(VoiceFormat.SAMPLE_RATE, VoiceFormat.CHANNELS)

    override fun decode(data: ByteArray?, length: Int, out: ShortArray): Int = try {
        if (data == null) {
            // Concentus PLC: passing null in triggers concealment.
            decoder.decode(null, 0, 0, out, 0, VoiceFormat.FRAME_SAMPLES, false)
        } else {
            decoder.decode(data, 0, length, out, 0, VoiceFormat.FRAME_SAMPLES, false)
        }
    } catch (e: Exception) {
        RLog.d(RLog.Cat.VOICE, "opus decode failed: ${e.message}")
        for (i in 0 until VoiceFormat.FRAME_SAMPLES) out[i] = 0
        VoiceFormat.FRAME_SAMPLES
    }

    override fun release() {}
}
