package eu.emufii.app.ps2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vectors measured from a real card written by Midnight Club 3, i.Link ID 30 27 D4 20 57 06 94 80. */
class Ps2NetcnfConfigTest {

    private val benchId = hex("3027d42057 0694 80".replace(" ", ""))

    @Test
    fun `decodes the shipped ifc000 dat to its plaintext`() {
        val cipher = hex("737f759821231b7ef24228f945c5a366bf75462e351b173db48ca6a418b9bf6d46" +
            "728e0ebebee1a21f35f23b2d39eb375e72feb1")
        val plain = "# <Sony Computer Entertainment Inc.>\n\ntype nic\ndhcp\n"
        assertArrayEquals(plain.toByteArray(Charsets.US_ASCII), Ps2NetcnfConfig.decode(cipher, benchId))
    }

    @Test
    fun `decodes the shipped dev000 dat to its plaintext`() {
        val cipher = hex(
            "737f759821231b7ef24228f945c5a366bf75462e351b173db48ca6a418b9bf6d46" +
                "728e0ebebee1a21f35f23b2d39eb136a4672131bbf76b3f2eadf5dc6c726" +
                "e415392f7ebb75172fd46c66a4bdf8af636a2e4484b291efb7373df171211" +
                "badbbd63ff0d241394246665a8df9454fe422eb",
        )
        val plain = "# <Sony Computer Entertainment Inc.>\n\ntype nic\n" +
            "vendor \"SCE\"\nproduct \"Ethernet (Network Adaptor)\"\nphy_config auto\n"
        assertArrayEquals(plain.toByteArray(Charsets.US_ASCII), Ps2NetcnfConfig.decode(cipher, benchId))
    }

    @Test
    fun `encoding the bench plaintext reproduces the shipped bytes`() {
        val ifcPlain = "# <Sony Computer Entertainment Inc.>\n\ntype nic\ndhcp\n"
        val devPlain = "# <Sony Computer Entertainment Inc.>\n\ntype nic\n" +
            "vendor \"SCE\"\nproduct \"Ethernet (Network Adaptor)\"\nphy_config auto\n"
        assertArrayEquals(
            hex(
                "737f759821231b7ef24228f945c5a366bf75462e351b173db48ca6a418b9bf6d46" +
                    "728e0ebebee1a21f35f23b2d39eb375e72feb1",
            ),
            Ps2NetcnfConfig.encode(ifcPlain.toByteArray(Charsets.US_ASCII), benchId),
        )
        // 113 bytes: the odd trailing byte takes the 8-bit rotation.
        val devCipher = Ps2NetcnfConfig.encode(devPlain.toByteArray(Charsets.US_ASCII), benchId)
        assertEquals(113, devCipher.size)
        assertArrayEquals(
            hex(
                "737f759821231b7ef24228f945c5a366bf75462e351b173db48ca6a418b9bf6d46" +
                    "728e0ebebee1a21f35f23b2d39eb136a4672131bbf76b3f2eadf5dc6c726" +
                    "e415392f7ebb75172fd46c66a4bdf8af636a2e4484b291efb7373df171211" +
                    "badbbd63ff0d241394246665a8df9454fe422eb",
            ),
            devCipher,
        )
    }

    @Test
    fun `encode and decode invert each other across ids and lengths`() {
        for (id in listOf(benchId, Ps2NetcnfConfig.ARMSX2_CONSOLE_ID, hex("0000000000000000"))) {
            for (length in intArrayOf(0, 1, 2, 47, 48, 49, 51, 52, 113)) {
                val plain = ByteArray(length) { ((it * 7 + 13) and 0xFF).toByte() }
                assertArrayEquals(plain, Ps2NetcnfConfig.decode(Ps2NetcnfConfig.encode(plain, id), id))
            }
        }
    }

    @Test
    fun `two files encrypted for one console share their prefix when their text does`() {
        val ifc = Ps2NetcnfConfig.ifcDat(benchId)
        val dev = Ps2NetcnfConfig.devDat(benchId)
        var shared = 0
        while (shared < ifc.size && ifc[shared] == dev[shared]) shared++
        assertEquals(47, shared)
    }

    @Test
    fun `a card encrypted for one console does not read back on another`() {
        val plain = "# <Sony Computer Entertainment Inc.>\n\ntype nic\ndhcp\n".toByteArray(Charsets.US_ASCII)
        val a = Ps2NetcnfConfig.encode(plain, benchId)
        val other = benchId.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val b = Ps2NetcnfConfig.encode(plain, other)
        var differing = 0
        for (i in a.indices) if (a[i] != b[i]) differing++
        assertTrue("changing one ID byte must change the stream, saw $differing", differing > 0)
        assertTrue(!Ps2NetcnfConfig.decode(a, other).contentEquals(plain))
    }

    @Test
    fun `shifts come three per id byte, each between one and eight`() {
        val shifts = Ps2NetcnfConfig.shifts(benchId)
        assertEquals(24, shifts.size)
        shifts.forEach { assertEquals("shift in 1..8", true, it in 1..8) }
        assertEquals(2, shifts[0]) // 0x30 shr 5, plus one
        assertEquals(5, shifts[1]) // (0x30 shr 2) and 7, plus one
        assertEquals(1, shifts[2]) // 0x30 and 3, plus one
    }

    @Test
    fun `the BIOS version selects exactly one nvm layout`() {
        val nvm = ByteArray(1024)
        val oldId = hex("1020304050607080")
        System.arraycopy(oldId, 0, nvm, 0x1C0, 8)
        System.arraycopy(benchId, 0, nvm, 0x1E0, 8)

        assertArrayEquals(
            oldId,
            Ps2NetcnfConfig.ilinkIdFromNvm(nvm, Ps2NetcnfConfig.BiosVersion(1, 60)),
        )
        assertArrayEquals(
            benchId,
            Ps2NetcnfConfig.ilinkIdFromNvm(nvm, Ps2NetcnfConfig.BiosVersion(1, 70)),
        )
        assertArrayEquals(
            benchId,
            Ps2NetcnfConfig.ilinkIdFromNvm(nvm, Ps2NetcnfConfig.NvmLayout.FROM_1_70),
        )
    }

    @Test
    fun `an unprogrammed id area yields null so the caller keeps the default`() {
        assertNull(
            Ps2NetcnfConfig.ilinkIdFromNvm(
                ByteArray(1024),
                Ps2NetcnfConfig.BiosVersion(2, 20),
            ),
        )
        assertNull(
            Ps2NetcnfConfig.ilinkIdFromNvm(
                ByteArray(128),
                Ps2NetcnfConfig.NvmLayout.BEFORE_1_70,
            ),
        )
    }

    @Test
    fun `effective id follows armsx2 nvm sanity fallback`() {
        val version = Ps2NetcnfConfig.BiosVersion(2, 20)
        val nvm = ByteArray(1024)
        System.arraycopy(benchId, 0, nvm, 0x1E0, 8)
        // Blank language/region data makes ARMSX2 replace the whole NVM.
        assertArrayEquals(
            Ps2NetcnfConfig.ARMSX2_CONSOLE_ID,
            Ps2NetcnfConfig.effectiveIlinkIdFromNvm(nvm, version),
        )
        nvm[0x2B0 + 0x10] = 1
        nvm[0x180] = 1
        assertArrayEquals(benchId, Ps2NetcnfConfig.effectiveIlinkIdFromNvm(nvm, version))
        assertArrayEquals(
            Ps2NetcnfConfig.ARMSX2_CONSOLE_ID,
            Ps2NetcnfConfig.effectiveIlinkIdFromNvm(ByteArray(100), version),
        )
    }

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "").replace("\n", "")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) out[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return out
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}
