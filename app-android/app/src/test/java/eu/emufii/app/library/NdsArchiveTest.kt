package eu.emufii.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NdsArchiveTest {

    private fun zip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(out.toByteArray())
    }

    private fun romIn(archive: ByteArrayInputStream): String? =
        archive.use { NdsArchive.openRom(it)?.readBytes()?.decodeToString() }

    @Test
    fun `the cartridge comes out of the archive`() {
        assertEquals("cartridge", romIn(zip("Game.nds" to "cartridge")))
        assertEquals("cartridge", romIn(zip("Game.dsi" to "cartridge")))
        assertEquals("cartridge", romIn(zip("Game.ids" to "cartridge")))
        assertEquals("cartridge", romIn(zip("GAME.NDS" to "cartridge")))
    }

    @Test
    fun `everything around the cartridge is passed over`() {
        val archive = zip(
            "readme.txt" to "notes",
            "cover.png" to "art",
            "Game.nds" to "cartridge",
        )
        assertEquals("cartridge", romIn(archive))
    }

    @Test
    fun `an archive holding no cartridge holds nothing`() {
        assertNull(romIn(zip("readme.txt" to "notes", "save.dsv" to "save")))
        assertNull(romIn(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun `the first cartridge is the one, as melonDS would pick it`() {
        val archive = zip("Disc 1.nds" to "first", "Disc 2.nds" to "second")
        assertEquals("first", romIn(archive))
    }

    @Test
    fun `an archive is recognised by its name`() {
        assertTrue(NdsArchive.isArchive("Game.zip"))
        assertTrue(NdsArchive.isArchive("Game.ZIP"))
        assertFalse(NdsArchive.isArchive("Game.nds"))
        assertFalse(NdsArchive.isArchive("Game.7z"))
        assertFalse(NdsArchive.isArchive("zip"))
    }

    @Test
    fun `no console claims the archive extension`() {
        // The table is a map, and a zip holds whatever someone put in it: only the entry
        // inside settles the console.
        assertNull(Console.forExtension(NdsArchive.EXTENSION))
    }
}
