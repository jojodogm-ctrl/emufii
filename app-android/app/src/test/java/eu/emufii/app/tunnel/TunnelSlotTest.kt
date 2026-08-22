package eu.emufii.app.tunnel

import eu.emufii.app.wfc.WfcState
import eu.emufii.app.wg.WgState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelSlotTest {

    private val noSession = WgState.Idle
    private val noWfc = WfcState.Idle

    @Test
    fun `nobody holds the slot when both tunnels are idle`() {
        assertEquals(TunnelHolder.NONE, tunnelHolder(noSession, noWfc))
    }

    @Test
    fun `a live session holds the slot`() {
        assertEquals(
            TunnelHolder.SESSION,
            tunnelHolder(WgState.Online("UUF-758", "10.67.1.2"), noWfc)
        )
    }

    @Test
    fun `a session still starting already holds the slot`() {
        // establish() may have happened; this is the window where two tunnels
        // would otherwise collide.
        assertEquals(TunnelHolder.SESSION, tunnelHolder(WgState.Starting("UUF-758"), noWfc))
    }

    @Test
    fun `a session with no peers still holds the slot`() {
        assertEquals(TunnelHolder.SESSION, tunnelHolder(WgState.Offline("UUF-758"), noWfc))
    }

    @Test
    fun `the wfc relay holds the slot`() {
        assertEquals(
            TunnelHolder.WFC,
            tunnelHolder(noSession, WfcState.Active("me.magnum.melonds"))
        )
    }

    @Test
    fun `a tunnel shutting down does not hold the slot`() {
        assertEquals(TunnelHolder.NONE, tunnelHolder(WgState.Stopping, WfcState.Stopping))
    }

    @Test
    fun `a failed tunnel does not hold the slot`() {
        assertEquals(
            TunnelHolder.NONE,
            tunnelHolder(WgState.Error("boom"), WfcState.Error("boom"))
        )
    }

    @Test
    fun `the session wins when both look up`() {
        // Should not happen, but if it does one of them is a leftover, and the
        // session is the one whose loss costs the player a game in progress.
        assertEquals(
            TunnelHolder.SESSION,
            tunnelHolder(WgState.Online("UUF-758", "10.67.1.2"), WfcState.Active("me.magnum.melonds"))
        )
    }

    @Test
    fun `an idle slot is free for either tunnel`() {
        assertTrue(slotIsFree(noSession, noWfc, TunnelHolder.SESSION))
        assertTrue(slotIsFree(noSession, noWfc, TunnelHolder.WFC))
    }

    @Test
    fun `taking the slot you already hold is not a conflict`() {
        // Pointing the tunnel at another session is an in-service rebuild, not
        // a second VpnService.
        assertTrue(slotIsFree(WgState.Online("UUF-758", "10.67.1.2"), noWfc, TunnelHolder.SESSION))
        assertTrue(slotIsFree(noSession, WfcState.Active("me.magnum.melonds"), TunnelHolder.WFC))
    }

    @Test
    fun `the other tunnel blocks the slot`() {
        assertFalse(slotIsFree(noSession, WfcState.Active("me.magnum.melonds"), TunnelHolder.SESSION))
        assertFalse(slotIsFree(WgState.Online("UUF-758", "10.67.1.2"), noWfc, TunnelHolder.WFC))
    }
}
