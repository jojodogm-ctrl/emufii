package eu.emufii.app.ps2

import eu.emufii.app.dolphin.Bounds
import eu.emufii.app.dolphin.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pinned against ARMSX2 2.6.6.7's real tree, taken with `uiautomator` on the
 * Thor on 2026-08-17. The bounds below are copied from the dump, not invented.
 */
class Ps2ScreenTest {

    private fun text(t: String, b: Bounds, clickable: Boolean = false) =
        Node(text = t, className = Node.TEXT_VIEW, bounds = b, clickable = clickable)

    /** The "Local Link port" row, as it is drawn in host mode. */
    private val portRow = listOf(
        text("Local Link port", Bounds(69, 809, 306, 867)),
        text("19072", Bounds(1761, 809, 1851, 867)),
        // The next line, which must never be taken for the value.
        text("Room code", Bounds(69, 997, 250, 1055)),
        text("KHYZF3W6", Bounds(1495, 997, 1658, 1055))
    )

    @Test
    fun `la valeur se lit sur la ligne du libelle, pas sur la suivante`() {
        assertEquals("19072", Ps2Screen.valueFor(portRow, "Local Link port")?.text)
        assertEquals("KHYZF3W6", Ps2Screen.valueFor(portRow, "Room code")?.text)
    }

    @Test
    fun `deux lignes voisines ne sont pas la meme ligne`() {
        assertFalse(Ps2Screen.sameRow(Bounds(69, 809, 306, 867), Bounds(69, 997, 250, 1055)))
        assertTrue(Ps2Screen.sameRow(Bounds(69, 809, 306, 867), Bounds(1761, 809, 1851, 867)))
    }

    @Test
    fun `un libelle absent ne rend rien plutot qu'une valeur voisine`() {
        assertNull(Ps2Screen.valueFor(portRow, "Host IPv4 address"))
        assertNull(Ps2Screen.row(portRow, "Host IPv4 address"))
    }

    @Test
    fun `la rangee cliquable est la plus petite qui contienne le libelle`() {
        val nodes = portRow + listOf(
            // The whole page, clickable as well: the trap to avoid.
            text("", Bounds(0, 0, 1920, 1080), clickable = true),
            text("", Bounds(60, 800, 1900, 880), clickable = true)
        )
        val row = Ps2Screen.row(nodes, "Local Link port")
        assertEquals(Bounds(60, 800, 1900, 880), row?.bounds)
    }

    @Test
    fun `l'interrupteur d'une bascule se prend a droite de son libelle`() {
        val nodes = listOf(
            text("Enable DEV9 Ethernet", Bounds(92, 667, 1599, 721)),
            text("", Bounds(1708, 687, 1828, 797), clickable = true)
        )
        assertEquals(Bounds(1708, 687, 1828, 797), Ps2Screen.toggleFor(nodes, "Enable DEV9 Ethernet")?.bounds)
    }

    @Test
    fun `les trois modes sont des boutons visibles en meme temps`() {
        val nodes = listOf(
            text("Online (Sockets)", Bounds(122, 295, 359, 353), clickable = true),
            text("Host local game", Bounds(437, 532, 669, 590), clickable = true),
            text("Join local game", Bounds(747, 376, 973, 434), clickable = true)
        )
        assertEquals(Bounds(437, 532, 669, 590), Ps2Screen.modeButton(nodes, "Host local game")?.bounds)
    }

    /** ARMSX2's keyboard, as it is drawn: 42 keys, not one field. */
    private val keyboard = listOf(
        text("10671", Bounds(69, 300, 400, 358)),
        text("1", Bounds(51, 507, 222, 627), clickable = true),
        text("0", Bounds(1698, 507, 1869, 627), clickable = true),
        text("q", Bounds(51, 641, 243, 761), clickable = true),
        text("⇧", Bounds(202, 806, 223, 864), clickable = true),
        text("Space", Bounds(51, 909, 826, 1029), clickable = true),
        text("⌫", Bounds(838, 909, 1148, 1029), clickable = true),
        text("Clear", Bounds(1160, 909, 1470, 1029), clickable = true),
        text("Done", Bounds(1482, 909, 1869, 1029), clickable = true)
    )

    @Test
    fun `le bouton Generate n'est pas pris pour le code de salon`() {
        // The infinite loop of 2026-08-17: "Generate" is further right than the
        // code, and neither is clickable in the tree.
        val row = listOf(
            text("Room code", Bounds(69, 183, 250, 241)),
            text("DNW757", Bounds(1532, 183, 1658, 241)),
            text("Generate", Bounds(1686, 188, 1823, 237))
        )
        assertEquals("DNW757", Ps2Screen.valueFor(row, "Room code")?.text)
    }

    @Test
    fun `le clavier d'ARMSX2 se reconnait a ses deux touches de commande`() {
        assertTrue(Ps2Screen.keyboardIsOpen(keyboard))
        assertFalse(Ps2Screen.keyboardIsOpen(portRow))
    }

    @Test
    fun `une touche se trouve quelle que soit la casse affichee`() {
        // Shift only toggles case: the same key is "q" or "Q".
        assertEquals("q", Ps2Screen.key(keyboard, 'Q')?.text)
        assertEquals("1", Ps2Screen.key(keyboard, '1')?.text)
    }

    @Test
    fun `une adresse IPv4 n'est pas saisissable, et il faut le savoir avant d'essayer`() {
        // The wall measured on 2026-08-17: no dot key, no automatic insertion.
        // Typing "10671" displays "10671".
        assertFalse(Ps2Screen.canType("10.67.1.2"))
        assertTrue(Ps2Screen.canType("KHYZF3W6"))
        assertTrue(Ps2Screen.canType("19072"))
    }

    @Test
    fun `le code de salon suit les bornes annoncees par l'emulateur`() {
        assertTrue(Ps2Target.isValidRoomCode("KHYZF3W6"))
        assertTrue(Ps2Target.isValidRoomCode("ABCD"))
        assertFalse(Ps2Target.isValidRoomCode("ABC"))
        assertFalse(Ps2Target.isValidRoomCode("ABCDEFGHIJKLM"))
        assertFalse(Ps2Target.isValidRoomCode("ABC-DEF"))
    }

    @Test
    fun `le port par defaut est celui d'ARMSX2 et il est dans les bornes`() {
        assertEquals(19072, Ps2Target.DEFAULT_PORT)
        assertTrue(Ps2Target.isValidPort(Ps2Target.DEFAULT_PORT))
        assertFalse(Ps2Target.isValidPort(80))
    }

    @Test
    fun `l'AetherSX2 d'origine n'est pas ARMSX2`() {
        assertTrue(Ps2Target.owns("com.armsx2"))
        assertFalse(Ps2Target.owns("xyz.aethersx2.android"))
    }
}
