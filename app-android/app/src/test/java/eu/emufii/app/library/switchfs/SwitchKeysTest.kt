package eu.emufii.app.library.switchfs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading `prod.keys`, which is the player's file and holds their console's
 * secrets. Two rules follow from that and are tested here: take only the three
 * key families the icon reader actually needs, and never fall over on a file
 * that is malformed, truncated or simply something else entirely.
 */
class SwitchKeysTest {

    private val hex32 = "0".repeat(64)
    private val hex16 = "0".repeat(32)

    @Test
    fun `the three families we need are read`() {
        val keys = SwitchKeys.parse(
            """
            header_key = $hex32
            key_area_key_application_10 = $hex16
            titlekek_11 = $hex16
            """.trimIndent()
        )
        assertTrue(keys.isUsable)
        assertEquals(32, keys.headerKey!!.size)
        assertNotNull(keys.keyAreaKeyApplication(0x10))
        assertNotNull(keys.titleKek(0x11))
        assertNull(keys.keyAreaKeyApplication(0x09))
    }

    @Test
    fun `everything else in the file is left alone`() {
        // These are real key names, and none of them is ours to hold.
        val keys = SwitchKeys.parse(
            """
            header_key = $hex32
            master_key_00 = $hex16
            aes_kek_generation_source = $hex16
            eticket_rsa_kek = $hex16
            """.trimIndent()
        )
        assertTrue(keys.isUsable)
        // Nothing beyond the wanted families is retained, asking for one of
        // them is the only way in, and there is no way to ask for the rest.
        assertNull(keys.titleKek(0x00))
    }

    @Test
    fun `comments, blank lines and odd spacing are survivable`() {
        val keys = SwitchKeys.parse(
            """
            # dumped 2026-07-22

               header_key   =   $hex32   # the one that matters
            HEADER_KEY_2 = nonsense
            """.trimIndent()
        )
        assertTrue(keys.isUsable)
    }

    @Test
    fun `a file that isn't prod keys yields nothing usable`() {
        for (text in listOf(
            "",
            "just some words",
            "header_key = not-hex-at-all",
            "header_key = abc",                       // odd length
            "header_key = ${"0".repeat(30)}",         // right shape, wrong size
            "= $hex32"
        )) {
            assertFalse(text, SwitchKeys.parse(text).isUsable)
        }
    }

    @Test
    fun `an absurd line is skipped rather than held in memory`() {
        val keys = SwitchKeys.parse("header_key = " + "0".repeat(100_000))
        assertFalse(keys.isUsable)
    }
}
