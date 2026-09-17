package com.ridesync.app

import com.ridesync.app.networking.protocol.RideCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RideCodesTest {

    @Test
    fun `generated pins are four digits`() {
        repeat(200) {
            val pin = RideCodes.generatePin(Random(it.toLong()))
            assertTrue(RideCodes.isValidPin(pin))
            assertEquals(4, pin.length)
        }
    }

    @Test
    fun `join uri round-trips`() {
        val uri = RideCodes.buildJoinUri(
            rideId = "ride-abc",
            rideName = "Delhi Weekend Ride",
            hostName = "Mohit",
            hostAddress = "192.168.43.1",
            controlPort = 52780,
            pin = "4821",
        )
        val target = RideCodes.parseJoinUri(uri)!!
        assertEquals("ride-abc", target.rideId)
        assertEquals("Delhi Weekend Ride", target.rideName)
        assertEquals("Mohit", target.hostName)
        assertEquals("192.168.43.1", target.host)
        assertEquals(52780, target.port)
        assertEquals("4821", target.pin)
    }

    @Test
    fun `rejects non-ridesync uris`() {
        assertNull(RideCodes.parseJoinUri("https://example.com"))
        assertNull(RideCodes.parseJoinUri("ridesync://join?"))
        assertNull(RideCodes.parseJoinUri(""))
    }

    @Test
    fun `handles special characters in ride name`() {
        val uri = RideCodes.buildJoinUri("r1", "Sunday Ride & Chai ☕", "Aman", "192.168.1.5", 52780, "1234")
        val target = RideCodes.parseJoinUri(uri)!!
        assertEquals("Sunday Ride & Chai ☕", target.rideName)
    }

    @Test
    fun `validates ipv4`() {
        assertTrue(RideCodes.isPlausibleIpv4("192.168.1.1"))
        assertTrue(RideCodes.isPlausibleIpv4("10.0.0.1"))
        assertTrue(!RideCodes.isPlausibleIpv4("999.1.1.1"))
        assertTrue(!RideCodes.isPlausibleIpv4("1.2.3"))
        assertTrue(!RideCodes.isPlausibleIpv4("hello"))
    }

    @Test
    fun `rejects invalid pins`() {
        assertTrue(!RideCodes.isValidPin("123"))
        assertTrue(!RideCodes.isValidPin("12a4"))
        assertTrue(!RideCodes.isValidPin("12345"))
    }
}
