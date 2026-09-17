package com.ridesync.app

import com.ridesync.app.networking.host.HostElection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostElectionTest {

    private val succession = listOf("host", "rahul", "aman", "rohit")
    private val allAlive = setOf("rahul", "aman", "rohit")

    @Test
    fun `candidates exclude dead host and keep join order`() {
        val c = HostElection.candidates(succession, deadHostId = "host", aliveIds = allAlive)
        assertEquals(listOf("rahul", "aman", "rohit"), c)
    }

    @Test
    fun `first successor has rank zero`() {
        assertEquals(0, HostElection.myRank(succession, "host", "rahul", allAlive))
        assertEquals(1, HostElection.myRank(succession, "host", "aman", allAlive))
        assertEquals(2, HostElection.myRank(succession, "host", "rohit", allAlive))
    }

    @Test
    fun `disconnected candidate is skipped so ranks shift up`() {
        // rahul is not in the alive set (someone else's view). aman and rohit
        // rank among the survivors; aman jumps to rank 0.
        val alive = setOf("aman", "rohit")
        assertEquals(0, HostElection.myRank(succession, "host", "aman", alive))
        assertEquals(1, HostElection.myRank(succession, "host", "rohit", alive))
    }

    @Test
    fun `self is always a candidate even if last roster marked it away`() {
        // A device running the election is present by definition, so it counts
        // itself in — the stale roster's opinion doesn't remove it.
        val staleAlive = setOf("aman", "rohit")
        assertEquals(0, HostElection.myRank(succession, "host", "rahul", staleAlive))
    }

    @Test
    fun `null when self not in succession at all`() {
        assertNull(HostElection.myRank(succession, "host", "newcomer", allAlive))
    }

    @Test
    fun `dead host cannot elect itself`() {
        assertNull(HostElection.myRank(succession, "host", "host", allAlive))
    }

    @Test
    fun `promotion delay increases with rank`() {
        assertEquals(0L, HostElection.promotionDelayMs(0))
        assertTrue(HostElection.promotionDelayMs(2) > HostElection.promotionDelayMs(1))
    }

    @Test
    fun `better ranked new host wins split brain`() {
        // I am rank 1, other is rank 0 -> I step down.
        assertTrue(HostElection.shouldStepDown(myRank = 1, otherRank = 0, myId = "aman", otherId = "rahul"))
        // I am rank 0, other is rank 1 -> I stay.
        assertTrue(!HostElection.shouldStepDown(myRank = 0, otherRank = 1, myId = "rahul", otherId = "aman"))
    }

    @Test
    fun `equal rank tie broken by smallest id`() {
        assertTrue(HostElection.shouldStepDown(myRank = 0, otherRank = 0, myId = "zzz", otherId = "aaa"))
        assertTrue(!HostElection.shouldStepDown(myRank = 0, otherRank = 0, myId = "aaa", otherId = "zzz"))
    }

    @Test
    fun `never steps down for self`() {
        assertTrue(!HostElection.shouldStepDown(0, 0, "me", "me"))
    }
}
