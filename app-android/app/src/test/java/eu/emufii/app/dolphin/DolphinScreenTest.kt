package eu.emufii.app.dolphin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trees transcribed from `uiautomator dump` of Dolphin 2606-302 at 1920x1080; it exposes no resource ids. */
class DolphinScreenTest {

    private fun field(text: String, bounds: Bounds) =
        Node(text = text, className = Node.EDIT_TEXT, bounds = bounds)

    private fun label(text: String, bounds: Bounds) =
        Node(text = text, className = Node.TEXT_VIEW, bounds = bounds)

    /** Button and caption are siblings in the real tree, related only by bounds. */
    private fun buttonBox(bounds: Bounds) =
        Node(text = "", className = "android.widget.Button", bounds = bounds, clickable = true)

    private val connectTab = listOf(
        label("Connect", Bounds(419, 235, 542, 282)),
        label("Host", Bounds(1405, 235, 1475, 282)),
        field("Closssv", Bounds(37, 351, 1883, 499)),
        label("Nickname", Bounds(74, 351, 205, 388)),
        field("Direct connection", Bounds(37, 536, 1883, 684)),
        label("Connection type", Bounds(74, 536, 284, 573)),
        field("127.0.0.1", Bounds(37, 721, 1883, 869)),
        label("IP address", Bounds(74, 721, 211, 758)),
        field("2626", Bounds(37, 906, 1883, 1054)),
        label("Port", Bounds(74, 906, 130, 943)),
        label("Connect", Bounds(1714, 900, 1837, 947)),
        buttonBox(Bounds(1668, 859, 1883, 988))
    )

    private val hostTab = listOf(
        label("Connect", Bounds(419, 235, 542, 282)),
        label("Host", Bounds(1405, 235, 1475, 282)),
        field("Player", Bounds(37, 351, 1883, 499)),
        label("Nickname", Bounds(74, 351, 205, 388)),
        field("Direct connection", Bounds(37, 536, 1883, 684)),
        label("Connection type", Bounds(74, 536, 284, 573)),
        field("2626", Bounds(37, 721, 1374, 869)),
        label("Port", Bounds(74, 721, 130, 758)),
        label("Forward port (UPnP)", Bounds(1447, 781, 1745, 828)),
        label("Host", Bounds(1756, 900, 1826, 947)),
        buttonBox(Bounds(1698, 859, 1883, 988))
    )

    private val direct = listOf("Direct connection")
    private val traversal = listOf("Traversal server")

    @Test
    fun `a field is found by the label drawn inside it`() {
        assertEquals("127.0.0.1", DolphinScreen.fieldFor(connectTab, listOf("IP address"))?.text)
        assertEquals("2626", DolphinScreen.fieldFor(connectTab, listOf("Port"))?.text)
        assertEquals("Closssv", DolphinScreen.fieldFor(connectTab, listOf("Nickname"))?.text)
    }

    @Test
    fun `the host tab has no address field, which is what identifies it`() {
        assertNull(DolphinScreen.fieldFor(hostTab, listOf("IP address")))
        assertNotNull(DolphinScreen.fieldFor(hostTab, listOf("Port")))
    }

    @Test
    fun `the tab and the button share a label and must not be confused`() {
        // Both are labelled from `netplay_connection_role_host`.
        val tab = DolphinScreen.tab(hostTab, listOf("Host"))
        val button = DolphinScreen.actionButton(hostTab, listOf("Host"))
        assertNotNull(tab)
        assertNotNull(button)
        assertEquals(Bounds(1405, 235, 1475, 282), tab!!.bounds)
        assertEquals(Bounds(1756, 900, 1826, 947), button!!.bounds)
    }

    @Test
    fun `the closed dropdown is not mistaken for an open one`() {
        assertFalse(DolphinScreen.isDropdownOpen(connectTab, direct, traversal))
        assertFalse(DolphinScreen.isDropdownOpen(hostTab, direct, traversal))
    }

    @Test
    fun `the open dropdown is recognised and its direct entry picked`() {
        val popup = listOf(
            label("Direct connection", Bounds(74, 734, 332, 781)),
            label("Traversal server", Bounds(74, 845, 306, 892))
        )
        assertTrue(DolphinScreen.isDropdownOpen(popup, direct, traversal))
        assertEquals(Bounds(74, 734, 332, 781), DolphinScreen.option(popup, direct)?.bounds)
    }

    @Test
    fun `labels are matched in whatever language Dolphin runs in`() {
        val french = listOf("Adresse IP", "IP address")
        assertEquals("127.0.0.1", DolphinScreen.fieldFor(connectTab, french)?.text)
        assertEquals("2626", DolphinScreen.fieldFor(connectTab, listOf(" port "))?.text)
    }

    @Test
    fun `the guest's own tab and button are told apart too`() {
        val tab = DolphinScreen.tab(connectTab, listOf("Connect"))
        val button = DolphinScreen.actionButton(connectTab, listOf("Connect"))
        assertEquals(Bounds(419, 235, 542, 282), tab?.bounds)
        assertEquals(Bounds(1714, 900, 1837, 947), button?.bounds)
    }

    private val lobby = listOf(
        field("Smash Bros. Brawl", Bounds(978, 203, 1883, 221)),
        label("Game", Bounds(1015, 203, 1091, 221)),
        field("External", Bounds(978, 388, 1324, 536)),
        label("Join info", Bounds(1015, 388, 1130, 425)),
        field("82.67.7.240:2626", Bounds(1342, 388, 1883, 406)),
        label("Address", Bounds(1379, 388, 1486, 406)),
        label("Players", Bounds(1015, 573, 1111, 610)),
        label("Closssv", Bounds(1015, 705, 1134, 752)),
        field("Fair Input Delay", Bounds(978, 1009, 1883, 1080)),
        label("Input mode", Bounds(1015, 1009, 1161, 1046)),
        label("Start", Bounds(1755, 900, 1827, 947))
    )

    @Test
    fun `the lobby's game selector reads like any other field`() {
        assertEquals("Smash Bros. Brawl", DolphinScreen.fieldFor(lobby, listOf("Game"))?.text)
    }

    @Test
    fun `the disc's title and ours name the same game`() {
        assertTrue(DolphinScreen.looselyMatches("Smash Bros. Brawl", "Super Smash Bros. Brawl"))
        assertFalse(DolphinScreen.looselyMatches("Resident Evil 4", "Super Smash Bros. Brawl"))
    }

    @Test
    fun `the game list picks the entry that names our rom`() {
        val list = listOf(
            label("Resident Evil 4", Bounds(0, 0, 500, 50)),
            label("Smash Bros. Brawl", Bounds(0, 50, 500, 100)),
            label("Mario Kart Wii", Bounds(0, 100, 500, 150))
        )
        assertEquals(
            Bounds(0, 50, 500, 100),
            DolphinScreen.looseOption(list, "Super Smash Bros. Brawl")?.bounds
        )
    }

    @Test
    fun `an ambiguous game list is left to the player`() {
        val twins = listOf(
            label("Mario Kart Wii", Bounds(0, 0, 500, 50)),
            label("Mario Kart Wii", Bounds(0, 50, 500, 100))
        )
        assertNull(DolphinScreen.looseOption(twins, "Mario Kart Wii"))
    }

    @Test
    fun `an unknown label finds nothing rather than the wrong field`() {
        assertNull(DolphinScreen.fieldFor(connectTab, listOf("Serveur")))
        assertNull(DolphinScreen.fieldFor(connectTab, emptyList()))
        assertNull(DolphinScreen.actionButton(connectTab, listOf("Rejoindre")))
    }
}
