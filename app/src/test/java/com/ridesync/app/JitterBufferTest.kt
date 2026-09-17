package com.ridesync.app

import com.ridesync.app.audio.JitterBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JitterBufferTest {

    private fun frame(seq: Int) = ByteArray(4) { seq.toByte() }

    @Test
    fun `pre-roll returns silence until target depth reached`() {
        val jb = JitterBuffer(targetFrames = 3, maxFrames = 12)
        jb.offer(0, frame(0), false)
        jb.offer(1, frame(1), false)
        // Only 2 buffered, target is 3 -> not started yet.
        assertTrue(jb.poll() is JitterBuffer.Output.Silence)
        jb.offer(2, frame(2), false)
        // Now started; releases in order.
        val out = jb.poll()
        assertTrue(out is JitterBuffer.Output.Packet)
        assertEquals(0, (out as JitterBuffer.Output.Packet).frame.seq)
    }

    @Test
    fun `reorders out-of-order frames`() {
        val jb = JitterBuffer(targetFrames = 2, maxFrames = 12)
        jb.offer(1, frame(1), false)
        jb.offer(0, frame(0), false)
        val first = jb.poll() as JitterBuffer.Output.Packet
        val second = jb.poll() as JitterBuffer.Output.Packet
        assertEquals(0, first.frame.seq)
        assertEquals(1, second.frame.seq)
    }

    @Test
    fun `conceals a single-frame gap when later frames exist`() {
        val jb = JitterBuffer(targetFrames = 2, maxFrames = 12)
        jb.offer(0, frame(0), false)
        jb.offer(2, frame(2), false) // seq 1 missing
        assertTrue(jb.poll() is JitterBuffer.Output.Packet) // 0
        assertTrue(jb.poll() is JitterBuffer.Output.Conceal) // gap for 1
        val out = jb.poll() as JitterBuffer.Output.Packet
        assertEquals(2, out.frame.seq)
    }

    @Test
    fun `drops frames that arrive too late`() {
        val jb = JitterBuffer(targetFrames = 1, maxFrames = 12)
        jb.offer(5, frame(5), false)
        jb.poll() // plays 5, nextSeq = 6
        jb.offer(4, frame(4), false) // late
        // 4 must be ignored, not resurrect the buffer.
        assertEquals(0, jb.size)
    }

    @Test
    fun `jumps forward when far behind newest packet to bound latency`() {
        val jb = JitterBuffer(targetFrames = 2, maxFrames = 4)
        jb.offer(0, frame(0), false)
        jb.offer(1, frame(1), false)
        jb.poll() // start, play 0
        // A big burst arrives; newest is far ahead.
        for (s in 2..30) jb.offer(s, frame(s), false)
        val out = jb.poll()
        // Should have jumped near the newest rather than playing seq 1.
        assertTrue(out is JitterBuffer.Output.Packet)
        val seq = (out as JitterBuffer.Output.Packet).frame.seq
        assertTrue("expected a jumped-forward seq, got $seq", seq > 20)
    }

    @Test
    fun `ignores duplicate sequence numbers`() {
        val jb = JitterBuffer(targetFrames = 1, maxFrames = 12)
        jb.offer(0, frame(0), false)
        jb.offer(0, frame(0), false)
        assertEquals(1, jb.size)
    }
}
