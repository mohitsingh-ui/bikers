package com.ridesync.app.audio

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
 * Codec selection point.
 *
 * The current build ships the PCM codec: on a local Wi-Fi network one speaker
 * is 16 kHz * 16-bit mono = ~256 kbps, which is trivial for a LAN, and PCM adds
 * *zero* codec latency — a good fit for a low-latency intercom. The
 * [VoiceEncoder]/[VoiceDecoder] abstraction and the wire's per-frame codec flag
 * keep the door open for a compressed codec (e.g. Opus) later: implement the two
 * interfaces and return them from here. It is wired so that returning null falls
 * back to PCM automatically, so a future Opus backend can be probed safely at
 * runtime without touching call sites.
 */
object OpusFactory {

    fun createEncoder(): VoiceEncoder? = null

    fun createDecoder(): VoiceDecoder? = null
}
