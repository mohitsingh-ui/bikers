package com.ridesync.app.audio

/**
 * Per-speaker adaptive jitter buffer.
 *
 * Voice frames arrive over UDP out of order, late, or not at all. This buffer
 * reorders by sequence number, waits for a small target depth before releasing
 * (absorbing jitter), conceals single-frame gaps, and skips ahead when it
 * falls too far behind (so a stalled speaker never adds permanent latency).
 *
 * Pure logic — no Android, no threads. The audio thread calls [offer] as
 * packets arrive and [poll] once per frame period. Fully unit-tested.
 */
class JitterBuffer(
    private val targetFrames: Int = DEFAULT_TARGET_FRAMES,
    private val maxFrames: Int = DEFAULT_MAX_FRAMES,
) {
    /** One received frame: its encoded bytes + whether it was Opus or PCM. */
    class Frame(val seq: Int, val data: ByteArray, val isPcm: Boolean)

    sealed class Output {
        /** A real frame to decode normally. */
        class Packet(val frame: Frame) : Output()

        /** Gap: decode with packet-loss concealment. */
        data object Conceal : Output()

        /** Nothing buffered yet (pre-roll or speaker idle). */
        data object Silence : Output()
    }

    private val frames = HashMap<Int, Frame>()
    private var nextSeq: Int = -1
    private var started = false
    private var highestSeq: Int = -1

    val size: Int get() = frames.size

    fun reset() {
        frames.clear()
        nextSeq = -1
        started = false
        highestSeq = -1
    }

    /** Add a received frame. Duplicates and hopelessly-late frames are dropped. */
    fun offer(seq: Int, data: ByteArray, isPcm: Boolean) {
        if (started && seq < nextSeq) return // too late, already played past it
        if (frames.containsKey(seq)) return
        if (frames.size >= maxFrames * 2) {
            // Runaway growth guard (sender key collision, etc.)
            val oldest = frames.keys.minOrNull() ?: return
            frames.remove(oldest)
        }
        frames[seq] = Frame(seq, data, isPcm)
        if (seq > highestSeq) highestSeq = seq
        if (!started && frames.size >= targetFrames) {
            started = true
            nextSeq = frames.keys.minOrNull() ?: seq
        }
    }

    /**
     * Release the next frame for playback. Call exactly once per frame tick
     * for this speaker.
     */
    fun poll(): Output {
        if (!started) return Output.Silence

        // If we've drifted far behind the newest packet, jump forward to bound latency.
        if (highestSeq - nextSeq > maxFrames) {
            val jumpTo = highestSeq - targetFrames
            frames.keys.filter { it < jumpTo }.forEach { frames.remove(it) }
            nextSeq = jumpTo
        }

        val frame = frames.remove(nextSeq)
        if (frame != null) {
            nextSeq++
            return Output.Packet(frame)
        }

        // Missing frame. If future frames exist, conceal and move on.
        return if (frames.isNotEmpty() && highestSeq >= nextSeq) {
            nextSeq++
            Output.Conceal
        } else {
            // Buffer drained; go idle until it refills.
            started = false
            frames.clear()
            Output.Silence
        }
    }

    companion object {
        const val DEFAULT_TARGET_FRAMES = 3 // ~60 ms pre-roll
        const val DEFAULT_MAX_FRAMES = 12 // ~240 ms hard cap
    }
}
