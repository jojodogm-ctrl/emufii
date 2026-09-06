package eu.emufii.app.session

import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.network.RoomRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * With a room on the VPS nobody hosts, both players join it; with no room the old path
 * has to stay intact, it is what serves the 3DS and every session already under way.
 *
 * The ROM is left null: building one would need a `Uri`, absent outside Android, and no
 * decision checked here depends on it.
 */
class NetplayPlanForRoomTest {

    private fun session(
        role: Session.Role,
        hostIp: String = "10.67.1.2",
        room: RoomRef? = null
    ) = Session(
        code = "ABC-123",
        hostIp = hostIp,
        port = "24872",
        role = role,
        room = room
    )

    private val room = RoomRef(host = "85.215.52.3", port = 24900, password = "ABC-123")

    @Test
    fun `with a room, the host joins it instead of carrying one`() {
        val plan = session(Session.Role.HOST, room = room).netplayPlan("Jo")

        assertEquals(NetplayPlan.Role.Guest, plan?.role)
        assertEquals("85.215.52.3", plan?.ip)
        assertEquals(24900, plan?.port)
        // The room listens on a public port: the password is the session code.
        assertEquals("ABC-123", plan?.password)
    }

    @Test
    fun `the guest targets the room, not the host`() {
        val plan = session(Session.Role.GUEST, room = room).netplayPlan("Clement")

        assertEquals("85.215.52.3", plan?.ip)
        assertEquals(24900, plan?.port)
    }

    @Test
    fun `a room removes the wait for the host address`() {
        // A room is dialled before the tunnel is up: it does not go through it.
        val plan = session(Session.Role.HOST, hostIp = "", room = room).netplayPlan("Jo")
        assertEquals("85.215.52.3", plan?.ip)
    }

    @Test
    fun `with no room, the old path does not move`() {
        val plan = session(Session.Role.HOST).netplayPlan("Jo")

        assertEquals(NetplayPlan.Role.Host, plan?.role)
        assertEquals("10.67.1.2", plan?.ip)
        assertEquals(24872, plan?.port)
        // A player's own room has no password: writing one shuts the door on the other.
        assertNull(plan?.password)
    }

    @Test
    fun `with neither room nor host address, there is nothing to dial`() {
        assertNull(session(Session.Role.GUEST, hostIp = "").netplayPlan("Jo"))
    }
}
