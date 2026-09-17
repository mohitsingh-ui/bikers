package com.ridesync.app.networking.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary UDP datagrams for the low-latency plane.
 *
 * Layout (big endian):
 * ```
 * [0] magic 0x52 ('R')
 * [1] version
 * [2] type
 * ... type-specific body
 * ```
 *
 * VOICE body:  senderKey:int32, seq:int32, flags:int8, len:int16, payload[len]
 * CLOCK_PING:  nonce:int64, t0:int64
 * CLOCK_PONG:  nonce:int64, t0:int64, hostTime:int64
 * HELLO body:  riderKey:int32   (registers the sender's UDP endpoint with the host)
 *
 * Kept dependency-free and allocation-light; this runs 50 times a second per
 * active speaker.
 */
object VoicePackets {

    const val MAGIC: Byte = 0x52
    const val VERSION: Byte = 1

    const val TYPE_VOICE: Byte = 1
    const val TYPE_CLOCK_PING: Byte = 2
    const val TYPE_CLOCK_PONG: Byte = 3
    const val TYPE_HELLO: Byte = 4

    const val FLAG_START: Int = 0x01
    const val FLAG_END: Int = 0x02
    const val FLAG_PCM: Int = 0x04

    const val HEADER_SIZE = 3
    const val VOICE_HEADER_SIZE = HEADER_SIZE + 4 + 4 + 1 + 2
    const val MAX_PAYLOAD = 1200
    const val MAX_DATAGRAM = VOICE_HEADER_SIZE + MAX_PAYLOAD

    sealed class Datagram {
        data class Voice(
            val senderKey: Int,
            val seq: Int,
            val flags: Int,
            val payload: ByteArray,
        ) : Datagram() {
            val isStart: Boolean get() = flags and FLAG_START != 0
            val isEnd: Boolean get() = flags and FLAG_END != 0
            val isPcm: Boolean get() = flags and FLAG_PCM != 0

            override fun equals(other: Any?): Boolean =
                other is Voice && other.senderKey == senderKey && other.seq == seq &&
                    other.flags == flags && other.payload.contentEquals(payload)

            override fun hashCode(): Int = 31 * (31 * senderKey + seq) + flags
        }

        data class ClockPing(val nonce: Long, val t0: Long) : Datagram()
        data class ClockPong(val nonce: Long, val t0: Long, val hostTime: Long) : Datagram()
        data class Hello(val riderKey: Int) : Datagram()
    }

    fun encodeVoice(senderKey: Int, seq: Int, flags: Int, payload: ByteArray, length: Int): ByteArray {
        require(length in 0..MAX_PAYLOAD) { "voice payload too large: $length" }
        val buf = ByteBuffer.allocate(VOICE_HEADER_SIZE + length).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC).put(VERSION).put(TYPE_VOICE)
        buf.putInt(senderKey).putInt(seq).put(flags.toByte()).putShort(length.toShort())
        buf.put(payload, 0, length)
        return buf.array()
    }

    fun encodeClockPing(nonce: Long, t0: Long): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + 16).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC).put(VERSION).put(TYPE_CLOCK_PING)
        buf.putLong(nonce).putLong(t0)
        return buf.array()
    }

    fun encodeClockPong(nonce: Long, t0: Long, hostTime: Long): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + 24).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC).put(VERSION).put(TYPE_CLOCK_PONG)
        buf.putLong(nonce).putLong(t0).putLong(hostTime)
        return buf.array()
    }

    fun encodeHello(riderKey: Int): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + 4).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC).put(VERSION).put(TYPE_HELLO)
        buf.putInt(riderKey)
        return buf.array()
    }

    /** Returns null for anything malformed — never throws on network input. */
    fun decode(data: ByteArray, length: Int): Datagram? {
        if (length < HEADER_SIZE || length > MAX_DATAGRAM) return null
        if (data[0] != MAGIC || data[1] != VERSION) return null
        val buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN)
        buf.position(2)
        return try {
            when (buf.get()) {
                TYPE_VOICE -> {
                    if (length < VOICE_HEADER_SIZE) return null
                    val senderKey = buf.int
                    val seq = buf.int
                    val flags = buf.get().toInt() and 0xFF
                    val payloadLen = buf.short.toInt() and 0xFFFF
                    if (payloadLen > MAX_PAYLOAD || buf.remaining() < payloadLen) return null
                    val payload = ByteArray(payloadLen)
                    buf.get(payload)
                    Datagram.Voice(senderKey, seq, flags, payload)
                }

                TYPE_CLOCK_PING -> {
                    if (buf.remaining() < 16) return null
                    Datagram.ClockPing(buf.long, buf.long)
                }

                TYPE_CLOCK_PONG -> {
                    if (buf.remaining() < 24) return null
                    Datagram.ClockPong(buf.long, buf.long, buf.long)
                }

                TYPE_HELLO -> {
                    if (buf.remaining() < 4) return null
                    Datagram.Hello(buf.int)
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
