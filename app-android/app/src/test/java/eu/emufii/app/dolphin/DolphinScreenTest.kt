package eu.emufii.app.dolphin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading Dolphin's netplay form when it hands us nothing to hold on to.
 *
 * The trees below are transcribed from `uiautomator dump` on the Thor, Dolphin
 * dev build 2606-302, 2026-08-15, in landscape at 1920×1080, the same screens
 * a player sees. Every bound is the real one. That matters here more than
 * usual: this backend has no resource ids at all, so geometry and text are
 * the entire contract, and a plausible-looking synthetic tree would prove
 * nothing about the screen we actually have to drive.
 */
class DolphinScreenTest {

    private fun field(text: String, bounds: Bounds) =
        Node(text = text, className = Node.EDIT_TEXT, bounds = bounds)

    private fun label(text: String, bounds: Bounds) =
        Node(text = text, className = Node.TEXT_VIEW, bounds = bounds)

    /**
     * A button box, as a sibling of its own caption.
     *
     * That is how the real tree comes out, and getting it wrong is what stopped
     * the driver one tap short of opening a room: the first version of this test
     * nested the caption inside the button because that is what Compose looked
     * like it should do. Read off the Thor instead, the `Button` and its
     * `TextView` sit at the same depth, and only the bounds relate them.
     */
    private fun buttonBox(bounds: Bounds) =
        Node(text = "", className = "android.widget.Button", bounds = bounds, clickable = true)

    /**
     * The Connect tab, the guest's form, transcribed from the real tree.
     *
     * Every bound below was read off the Thor, including the two that separate
     * the "Connect" tab from the "Connect" button. This is the side no second
     * device has exercised yet, so the tree is the only evidence there is that
     * the rules read it correctly.
     */
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

    /** The Host tab, direct connection: no address field, and a UPnP row. */
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
        // Compose puts the caption inside the field's own border, so the pair is
        // nested rather than sequential, that containment is the anchor.
        assertEquals("127.0.0.1", DolphinScreen.fieldFor(connectTab, listOf("IP address"))?.text)
        assertEquals("2626", DolphinScreen.fieldFor(connectTab, listOf("Port"))?.text)
        assertEquals("Closssv", DolphinScreen.fieldFor(connectTab, listOf("Nickname"))?.text)
    }

    @Test
    fun `the host tab has no address field, which is what identifies it`() {
        // The driver reads exactly this to know which tab it is looking at,
        // rather than trusting a tab label that also names a button.
        assertNull(DolphinScreen.fieldFor(hostTab, listOf("IP address")))
        assertNotNull(DolphinScreen.fieldFor(hostTab, listOf("Port")))
    }

    @Test
    fun `the tab and the button share a label and must not be confused`() {
        // Dolphin labels both from `netplay_connection_role_host`. Pressing the
        // button while the wrong tab is showing starts the wrong side of the
        // session, so this is the assertion that keeps a guest from hosting.
        val tab = DolphinScreen.tab(hostTab, listOf("Host"))
        val button = DolphinScreen.actionButton(hostTab, listOf("Host"))
        assertNotNull(tab)
        assertNotNull(button)
        assertEquals(Bounds(1405, 235, 1475, 282), tab!!.bounds)
        assertEquals(Bounds(1756, 900, 1826, 947), button!!.bounds)
    }

    @Test
    fun `the closed dropdown is not mistaken for an open one`() {
        // The form shows the selected option as the field's text, so matching on
        // the option labels alone would fire on every pass and re-open the menu
        // forever. A popup carries no EditText; that is the difference.
        assertFalse(DolphinScreen.isDropdownOpen(connectTab, direct, traversal))
        assertFalse(DolphinScreen.isDropdownOpen(hostTab, direct, traversal))
    }

    @Test
    fun `the open dropdown is recognised and its direct entry picked`() {
        // Real bounds of the popup window, captured with the menu open.
        val popup = listOf(
            label("Direct connection", Bounds(74, 734, 332, 781)),
            label("Traversal server", Bounds(74, 845, 306, 892))
        )
        assertTrue(DolphinScreen.isDropdownOpen(popup, direct, traversal))
        assertEquals(Bounds(74, 734, 332, 781), DolphinScreen.option(popup, direct)?.bounds)
    }

    @Test
    fun `labels are matched in whatever language Dolphin runs in`() {
        // The driver passes every translation of a string, because there is no
        // API to ask a third-party app which locale it is using. Matching has to
        // be case and whitespace insensitive for the same reason.
        val french = listOf("Adresse IP", "IP address")
        assertEquals("127.0.0.1", DolphinScreen.fieldFor(connectTab, french)?.text)
        assertEquals("2626", DolphinScreen.fieldFor(connectTab, listOf(" port "))?.text)
    }

    @Test
    fun `the guest's own tab and button are told apart too`() {
        // The guest side has not been played by anyone yet: these bounds are the
        // only evidence that its rules read the right screen. The same trap as on
        // the host side, "Connect" names the tab *and* the button.
        val tab = DolphinScreen.tab(connectTab, listOf("Connect"))
        val button = DolphinScreen.actionButton(connectTab, listOf("Connect"))
        assertEquals(Bounds(419, 235, 542, 282), tab?.bounds)
        assertEquals(Bounds(1714, 900, 1837, 947), button?.bounds)
    }

    /**
     * The lobby, once the connection is made.
     *
     * Transcribed from the Thor's `uiautomator dump` on 2026-08-16, with the
     * lobby open as host. The game selector reads like the form's fields: a
     * clickable `EditText` whose "Game" label fits inside its bounds.
     */
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
        // The lobby's real trap, measured for real: Emufii cuts the filename at
        // the first bracket and gets "Super Smash Bros. Brawl"; Dolphin reads the
        // disc header and displays "Smash Bros. Brawl". Strict equality would
        // reopen the list forever.
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
        // Two entries that mean the target ROM equally well: picking one at
        // random would start the wrong game, which is worse than doing nothing.
        // The driver hands back and leaves the lobby usable.
        val twins = listOf(
            label("Mario Kart Wii", Bounds(0, 0, 500, 50)),
            label("Mario Kart Wii", Bounds(0, 50, 500, 100))
        )
        assertNull(DolphinScreen.looseOption(twins, "Mario Kart Wii"))
    }

    @Test
    fun `an unknown label finds nothing rather than the wrong field`() {
        // The failure that must stay a failure: a renamed string upstream should
        // stop the automation, not write an address into whatever came first.
        assertNull(DolphinScreen.fieldFor(connectTab, listOf("Serveur")))
        assertNull(DolphinScreen.fieldFor(connectTab, emptyList()))
        assertNull(DolphinScreen.actionButton(connectTab, listOf("Rejoindre")))
    }
}
