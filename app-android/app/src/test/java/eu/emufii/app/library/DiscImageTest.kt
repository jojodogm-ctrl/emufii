package eu.emufii.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which console an `.iso` belongs to, when the extension refuses to say.
 *
 * The offsets pinned here were measured on the disc images on this machine, not
 * read off a wiki, and the whole library was run through the rule before it was
 * written: six PSP rips stayed on the PSP, eleven RVZ files came out as Wii, and
 * the one Dreamcast `.chd` was left exactly where it was. These tests keep that
 * result from drifting.
 *
 * The direction of the rule is the thing to protect. A wrong "yes" moves a
 * player's game onto an emulator that cannot open it; a wrong "no" merely leaves
 * it where it already was. So the assertions below care far more about what must
 * *not* be identified than about what must.
 */
class DiscImageTest {

    private fun header(build: ByteArray.() -> Unit) =
        ByteArray(DiscImage.HEADER_BYTES).apply(build)

    private fun ByteArray.putBE(at: Int, value: Int) {
        this[at] = (value ushr 24).toByte()
        this[at + 1] = (value ushr 16).toByte()
        this[at + 2] = (value ushr 8).toByte()
        this[at + 3] = value.toByte()
    }

    private fun ByteArray.putAscii(at: Int, text: String) {
        for ((i, c) in text.withIndex()) this[at + i] = c.code.toByte()
    }

    @Test
    fun `a raw image is read from the magic at its own offset`() {
        // 0x18 for the Wii, 0x1C for the GameCube, four bytes apart, which is
        // why they are checked in that order and never by a range scan.
        assertEquals(Console.WII, DiscImage.identify(header { putBE(0x18, 0x5D1C9EA3) }))
        assertEquals(
            Console.GAMECUBE,
            DiscImage.identify(header { putBE(0x1C, 0xC2339F3D.toInt()) })
        )
    }

    @Test
    fun `a PSP rip is not identified, so it stays with PPSSPP`() {
        // Measured: a UMD rip has plain zeroes at both offsets. This is the
        // assertion that keeps Dolphin from stealing the PSP's extension.
        assertNull(DiscImage.identify(ByteArray(DiscImage.HEADER_BYTES)))
    }

    @Test
    fun `RVZ states its console in the disc type field`() {
        // 1 GameCube, 2 Wii, at 0x48, the value every real RVZ here carries.
        assertEquals(
            Console.WII,
            DiscImage.identify(header { putAscii(0, "RVZ"); putBE(0x48, 2) })
        )
        assertEquals(
            Console.GAMECUBE,
            DiscImage.identify(header { putAscii(0, "WIA"); putBE(0x48, 1) })
        )
    }

    @Test
    fun `an unknown disc type falls back to the copy of the real header`() {
        // The container keeps the first 0x80 bytes of the disc verbatim at 0x58,
        // which is why the Wii magic shows up at 0x70 in a hex dump. It is the
        // answer for a disc_type this build has never seen.
        assertEquals(
            Console.WII,
            DiscImage.identify(
                header {
                    putAscii(0, "RVZ")
                    putBE(0x48, 99)
                    putBE(0x58 + 0x18, 0x5D1C9EA3)
                }
            )
        )
    }

    @Test
    fun `WBFS only ever held Wii discs`() {
        assertEquals(Console.WII, DiscImage.identify(header { putAscii(0, "WBFS") }))
    }

    @Test
    fun `a truncated or empty read identifies nothing`() {
        assertNull(DiscImage.identify(ByteArray(0)))
        assertNull(DiscImage.identify(ByteArray(3)))
    }

    @Test
    fun `the PSP keeps every extension it had`() {
        // The other half of the guarantee, and the one a future edit is most
        // likely to break: no matter what Dolphin declares, `.iso` and `.chd`
        // must still resolve to the PSP by name alone.
        assertEquals(Console.PSP, Console.forExtension("iso"))
        assertEquals(Console.PSP, Console.forExtension("chd"))
        assertEquals(Console.PSP, Console.forExtension("cso"))
        assertEquals(Console.PSP, Console.forExtension("pbp"))
    }

    /**
     * A complete disc, as far as the ISO9660 volume descriptor.
     *
     * The values are the ones taken from the Thor's real files on 2026-08-17, not
     * plausible-looking ones:
     *
     * ```
     * TimeSplitters 2 (PS2): 'PLAYSTATION' / 'SLES_50877'
     * WipEout Pulse  (PSP) : 'PSP GAME'    / 'SCEE'
     * ```
     */
    private fun disc(systemId: String, volumeId: String = "") =
        ByteArray(DiscImage.PVD_BYTES).apply {
            this[0x8000] = 1
            putAscii(0x8001, "CD001")
            putAscii(0x8008, systemId.padEnd(32))
            putAscii(0x8028, volumeId.padEnd(32))
        }

    @Test
    fun `un disque PS2 se reconnait a son identifiant systeme`() {
        assertEquals(Console.PS2, DiscImage.identify(disc("PLAYSTATION", "SLES_50877")))
    }

    @Test
    fun `un rip UMD reste a la PSP, meme lu jusqu'au descripteur`() {
        // The regression that matters: on the Thor, the six PS2 games and the six
        // PSP games are all `.iso`.
        assertNull(DiscImage.identify(disc("PSP GAME", "SCEE")))
    }

    @Test
    fun `le numero PS2 est celui qu'ARMSX2 affiche`() {
        // The emulator writes `SLES-50877`, the disc `SLES_50877`.
        assertEquals("SLES-50877", DiscImage.gameId(disc("PLAYSTATION", "SLES_50877")))
    }

    @Test
    fun `un en-tete court ne promeut rien vers la PS2`() {
        // What was read stops before `0x8000`: say nothing, rather than read
        // zeroes that do not come from the file.
        assertNull(DiscImage.identify(ByteArray(DiscImage.HEADER_BYTES)))
    }

    @Test
    fun `la PS2 ne reclame aucune extension`() {
        // It only ever arrives through the bytes: giving it `.iso` would take
        // that from the PSP, the table being a map.
        assertTrue(Console.PS2.extensions.isEmpty())
        assertEquals(Console.PSP, Console.forExtension("iso"))
    }

    @Test
    fun `the sniffed extensions are ones the scan actually recognises`() {
        // An extension listed for sniffing but absent from every console's set
        // would never reach the reader: the scan skips the file before asking.
        for (ext in DiscImage.SNIFFED_EXTENSIONS) {
            assert(Console.forExtension(ext) != null) {
                ".$ext is sniffed but no console claims it, so the scan drops it first"
            }
        }
    }
}
