package eu.emufii.app.session

import eu.emufii.app.azahar.NetplayPlan
import eu.emufii.app.network.RoomRef
import eu.emufii.app.ui.screens.netplayPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Who hosts, depending on whether a room is running on the VPS.
 *
 * This is the heart of the work and a switch easy to break without noticing:
 * with a room, nobody hosts, both players join it, and the host's phone stops
 * being a link in the network. With no room, the old path has to stay intact
 * down to the gesture, because it is what serves the 3DS and every session
 * already under way.
 *
 * The ROM is left null: building one would need a `Uri`, which does not exist
 * outside Android, and none of the decisions checked here depend on it.
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
    fun `avec un salon, l'hôte le rejoint au lieu d'en porter un`() {
        val plan = session(Session.Role.HOST, room = room).netplayPlan("Jo")

        // The point of the whole exercise: the host is a guest like the other.
        assertEquals(NetplayPlan.Role.Guest, plan?.role)
        assertEquals("85.215.52.3", plan?.ip)
        assertEquals(24900, plan?.port)
        // The room listens on a public port: with no password, a stranger walks
        // into the game. It is the session code.
        assertEquals("ABC-123", plan?.password)
    }

    @Test
    fun `l'invité vise le salon, pas l'hôte`() {
        val plan = session(Session.Role.GUEST, room = room).netplayPlan("Clement")

        assertEquals("85.215.52.3", plan?.ip)
        assertEquals(24900, plan?.port)
    }

    @Test
    fun `un salon dispense d'attendre l'adresse de l'hôte`() {
        // A room on the VPS can be dialled before the tunnel is even up: it does
        // not go through it. Requiring `hostIp` here would hold the game back for
        // an address nobody needs any more.
        val plan = session(Session.Role.HOST, hostIp = "", room = room).netplayPlan("Jo")
        assertEquals("85.215.52.3", plan?.ip)
    }

    @Test
    fun `sans salon, l'ancien chemin ne bouge pas`() {
        val plan = session(Session.Role.HOST).netplayPlan("Jo")

        assertEquals(NetplayPlan.Role.Host, plan?.role)
        assertEquals("10.67.1.2", plan?.ip)
        assertEquals(24872, plan?.port)
        // And above all no password: a player's own room has none, and writing
        // one would shut the door on the other player.
        assertNull(plan?.password)
    }

    @Test
    fun `sans salon ni adresse d'hôte, il n'y a rien à composer`() {
        assertNull(session(Session.Role.GUEST, hostIp = "").netplayPlan("Jo"))
    }
}
