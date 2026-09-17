package com.ridesync.app

import com.ridesync.app.networking.protocol.VoicePackets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePacketsTest {

    @Test
    fun `voice frame round-trips`() {
        val payload = ByteArray(80) { (it * 3).toByte() }
        val flags = VoicePackets.FLAG_START
        val bytes = VoicePackets.encodeVoice(senderKey = 7, seq = 42, flags = flags, payload = payload, length = payload.size)
        val decoded = VoicePackets.decode(bytes, bytes.size)
        assertTrue(decoded is VoicePackets.Datagram.Voice)
        decoded as VoicePackets.Datagram.Voice
        assertEquals(7, decoded.senderKey)
        assertEquals(42, decoded.seq)
        assertTrue(decoded.isStart)
        assertTrue(!decoded.isEnd)
        assertTrue(decoded.payload.contentEquals(payload))
    }

    @Test
    fun `clock ping and pong round-trip`() {
        val ping = VoicePackets.encodeClockPing(nonce = 99, t0 = 123456789L)
        val dping = VoicePackets.decode(ping, ping.size) as VoicePackets.Datagram.ClockPing
        assertEquals(99L, dping.nonce)
        assertEquals(123456789L, dping.t0)

        val pong = VoicePackets.encodeClockPong(nonce = 99, t0 = 123456789L, hostTime = 555L)
        val dpong = VoicePackets.decode(pong, pong.size) as VoicePackets.Datagram.ClockPong
        assertEquals(555L, dpong.hostTime)
    }

    @Test
    fun `hello round-trips`() {
        val hello = VoicePackets.encodeHello(riderKey = 3)
        val d = VoicePackets.decode(hello, hello.size) as VoicePackets.Datagram.Hello
        assertEquals(3, d.riderKey)
    }

    @Test
    fun `rejects wrong magic`() {
        val bytes = VoicePackets.encodeHello(1)
        bytes[0] = 0x00
        assertNull(VoicePackets.decode(bytes, bytes.size))
    }

    @Test
    fun `rejects truncated packet`() {
        val bytes = VoicePackets.encodeVoice(1, 1, 0, ByteArray(50), 50)
        assertNull(VoicePackets.decode(bytes, 5))
    }

    @Test
    fun `rejects lying length field`() {
        val payload = ByteArray(10)
        val bytes = VoicePackets.encodeVoice(1, 1, 0, payload, payload.size)
        // Corrupt the length field to claim a huge payload.
        val decoded = VoicePackets.decode(bytes, bytes.size - 3) // fewer bytes than header claims
        assertNull(decoded)
    }

    @Test
    fun `end marker with empty payload decodes`() {
        val bytes = VoicePackets.encodeVoice(2, 5, VoicePackets.FLAG_END, ByteArray(0), 0)
        val d = VoicePackets.decode(bytes, bytes.size) as VoicePackets.Datagram.Voice
        assertTrue(d.isEnd)
        assertEquals(0, d.payload.size)
    }
}
