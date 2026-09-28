package eu.emufii.app.psp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PpssppIniTest {

    @Test
    fun `public config points a private game back at PPSSPP's default server`() {
        val privateFile = PpssppIni.privateConfig("[Network]\nPortOffset = 10000\n")
        val after = PpssppIni.publicConfig(privateFile)

        assertTrue(after.contains("EnableWlan = True"))
        assertTrue(after.contains("EnableAdhocServer = True"))
        assertTrue(after.contains("proAdhocServer = socom.cc"))
        assertTrue(after.contains("AdhocServerRelayMode = 0"))
        assertFalse(after.contains("10.66.1.1"))
        assertTrue(after.contains("PortOffset = 10000"))
    }

    @Test
    fun `private config changes only the four network keys`() {
        val before = """
            ; Castlevania settings
            [Graphics]
            RenderingResolution = 4

            [Network]
            EnableWlan = False
            EnableAdhocServer = False
            proAdhocServer = socom.cc
            AdhocServerRelayMode = 0
            PortOffset = 10000
            NicknameLikeUnknownKey = untouched

            [SystemParam]
            NickName = Jojo
        """.trimIndent() + "\n"

        val after = PpssppIni.privateConfig(before)

        assertTrue(after.contains("EnableWlan = True"))
        assertTrue(after.contains("EnableAdhocServer = True"))
        assertTrue(after.contains("proAdhocServer = 10.66.1.1"))
        assertTrue(after.contains("AdhocServerRelayMode = 2"))
        assertTrue(after.contains("PortOffset = 10000"))
        assertTrue(after.contains("NicknameLikeUnknownKey = untouched"))
        assertTrue(after.contains("RenderingResolution = 4"))
        assertTrue(after.contains("NickName = Jojo"))
    }

    @Test
    fun `adds a network section without changing CRLF style`() {
        val after = PpssppIni.privateConfig("[Graphics]\r\nBackend = 3\r\n")
        assertFalse(after.replace("\r\n", "").contains('\n'))
        assertTrue(after.contains("\r\n[Network]\r\n"))
        assertTrue(after.endsWith("\r\n"))
    }

    @Test
    fun `restore keeps settings PPSSPP added during private play`() {
        val original = """
            [Network]
            EnableWlan = False
            proAdhocServer = custom.example
            PortOffset = 12000
        """.trimIndent()
        val snapshot = PpssppIni.snapshot(original, fileExisted = true)
        val whilePrivate = PpssppIni.privateConfig(original) + """

            [Graphics]
            RenderingResolution = 5
        """.trimIndent()

        val restored = PpssppIni.restore(whilePrivate, snapshot)

        assertTrue(restored.contains("EnableWlan = False"))
        assertTrue(restored.contains("proAdhocServer = custom.example"))
        assertFalse(restored.contains("EnableAdhocServer ="))
        assertFalse(restored.contains("AdhocServerRelayMode ="))
        assertTrue(restored.contains("PortOffset = 12000"))
        assertTrue(restored.contains("RenderingResolution = 5"))
    }

    @Test
    fun `duplicates become one effective value`() {
        val after = PpssppIni.privateConfig(
            "[Network]\nEnableWlan = False\nEnableWlan=True\n",
        )
        assertEquals(1, after.lineSequence().count { it.startsWith("EnableWlan =") })
        assertTrue(after.contains("EnableWlan = True"))
    }

    @Test
    fun `later duplicate network section cannot override private values`() {
        val after = PpssppIni.privateConfig(
            "[Network]\nEnableWlan = False\nPortOffset = 10000\n" +
                "[Graphics]\nBackend = 3\n" +
                "[Network]\nEnableWlan = False\nEnableAdhocServer = False\n",
        )

        assertEquals(1, after.lineSequence().count { it.startsWith("EnableWlan =") })
        assertEquals(1, after.lineSequence().count { it.startsWith("EnableAdhocServer =") })
        assertTrue(after.contains("EnableWlan = True"))
        assertTrue(after.contains("EnableAdhocServer = True"))
        assertTrue(after.contains("PortOffset = 10000"))
        assertTrue(after.contains("Backend = 3"))
    }

    @Test
    fun `resolves metadata and conventional filename ids`() {
        assertEquals("ULUS10277", PpssppIni.resolveDiscId("PSP-ULUS10277", null, null))
        assertEquals(
            "ULES01372",
            PpssppIni.resolveDiscId(null, "METAL GEAR SOLID [ules01372].chd", null),
        )
        assertEquals(
            "NPHG00027",
            PpssppIni.resolveDiscId(null, null, "God of War NPHG00027"),
        )
        assertNull(PpssppIni.resolveDiscId("PSP-F12345678", "Kingdom Hearts.chd", null))
    }

    @Test
    fun `empty Emufii-created config can be deleted after restore`() {
        val snapshot = PpssppIni.snapshot("", fileExisted = false)
        val restored = PpssppIni.restore(PpssppIni.privateConfig(""), snapshot)
        assertTrue(PpssppIni.hasNoAssignments(restored))
    }
}
