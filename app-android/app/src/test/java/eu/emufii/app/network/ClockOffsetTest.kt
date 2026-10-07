package eu.emufii.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockOffsetTest {

    // One test, in order: the offset is process-wide state.
    @Test
    fun learnsTheServerClockAndIgnoresNoise() {
        val now = 1_800_000_000_000L
        // A phone twelve hours behind: the server's Date says so, and signing follows it.
        assertTrue(ClientAuth.learnServerClock(now + 12 * 3600_000L, now - 100, now + 100))
        assertEquals(12 * 3600_000L, ClientAuth.clockOffsetMs)
        // Within Date's one-second resolution and some latency: kept as it is.
        assertFalse(ClientAuth.learnServerClock(now + 12 * 3600_000L + 1_500, now, now))
        assertEquals(12 * 3600_000L, ClientAuth.clockOffsetMs)
        // A reply without a Date header teaches nothing.
        assertFalse(ClientAuth.learnServerClock(0L, now, now))
        // The phone's clock fixed: back to no offset.
        assertTrue(ClientAuth.learnServerClock(now, now, now))
        assertEquals(0L, ClientAuth.clockOffsetMs)
    }
}
