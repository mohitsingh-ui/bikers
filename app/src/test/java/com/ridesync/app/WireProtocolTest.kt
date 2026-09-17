package com.ridesync.app

import com.ridesync.app.networking.protocol.ControlMessage
import com.ridesync.app.networking.protocol.Emergency
import com.ridesync.app.networking.protocol.Envelope
import com.ridesync.app.networking.protocol.JoinRequest
import com.ridesync.app.networking.protocol.PlaybackSync
import com.ridesync.app.networking.protocol.TrackDto
import com.ridesync.app.networking.protocol.Wire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireProtocolTest {

    private fun env(msg: ControlMessage) = Envelope(
        senderId = "rider-1", rideId = "ride-1", seq = 1, sentAt = 1000, msg = msg,
    )

    @Test
    fun `join request round-trips through json`() {
        val line = Wire.encode(env(JoinRequest("Rahul", "4821")))
        val decoded = Wire.decode(line)!!
        assertEquals("rider-1", decoded.senderId)
        val join = decoded.msg as JoinRequest
        assertEquals("Rahul", join.riderName)
        assertEquals("4821", join.pin)
    }

    @Test
    fun `playback sync round-trips`() {
        val msg = PlaybackSync(
            track = TrackDto("t1", "Open Road", "RideSync", 96000, "DEMO"),
            isPlaying = true,
            positionMs = 84320,
            hostTimeMs = 1726538492,
            syncSeq = 1024,
        )
        val decoded = Wire.decode(Wire.encode(env(msg)))!!
        val sync = decoded.msg as PlaybackSync
        assertEquals(84320, sync.positionMs)
        assertEquals(1024, sync.syncSeq)
        assertEquals("Open Road", sync.track?.title)
    }

    @Test
    fun `malformed json returns null instead of throwing`() {
        assertNull(Wire.decode("{ not valid json"))
        assertNull(Wire.decode(""))
        assertNull(Wire.decode("   "))
    }

    @Test
    fun `unknown message type is rejected safely`() {
        val forged = """{"v":1,"senderId":"x","rideId":"r","seq":1,"sentAt":0,"msg":{"type":"TOTALLY_MADE_UP"}}"""
        // Unknown polymorphic type -> serialization fails -> null, no crash.
        assertNull(Wire.decode(forged))
    }

    @Test
    fun `future protocol version is rejected`() {
        val future = """{"v":99,"senderId":"x","rideId":"r","seq":1,"sentAt":0,"msg":{"type":"HEARTBEAT"}}"""
        assertNull(Wire.decode(future))
    }

    @Test
    fun `oversized line is rejected`() {
        val huge = "x".repeat(Wire.MAX_CONTROL_LINE + 1)
        assertNull(Wire.decode(huge))
    }

    @Test
    fun `emergency with optional location round-trips`() {
        val msg = Emergency("Mohit", 1726538492, latitude = 28.6, longitude = 77.2)
        val decoded = Wire.decode(Wire.encode(env(msg)))!!
        val em = decoded.msg as Emergency
        assertEquals(28.6, em.latitude!!, 0.0001)
        assertEquals("Mohit", em.riderName)
    }

    @Test
    fun `emergency without location round-trips`() {
        val decoded = Wire.decode(Wire.encode(env(Emergency("Aman", 123))))!!
        val em = decoded.msg as Emergency
        assertNull(em.latitude)
    }
}
