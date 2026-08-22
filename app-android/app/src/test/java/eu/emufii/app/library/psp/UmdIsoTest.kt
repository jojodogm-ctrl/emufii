package eu.emufii.app.library.psp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parsing a UMD, exercised on discs built here byte by byte.
 *
 * Three of the standard's traps deserve to be pinned down rather than assumed:
 * the `;1` ISO9660 sticks after filenames, the end-of-sector padding that cuts a
 * large directory in two, and a path that crosses a directory. Each shows up as
 * the same symptom, "file not found" on a disc that contains it, so telling them
 * apart requires separate cases.
 */
class UmdIsoTest {

    private val sector = UmdIso.SECTOR

    /**
     * A minimal disc: a volume descriptor, a root containing the [dirName]
     * directory, which in turn contains [files].
     *
     * With [padded], the directory occupies two sectors and its files are pushed
     * into the second, behind the zero padding, the shape any real directory
     * takes as soon as it exceeds 2048 bytes.
     */
    private class Disc(
        dirName: String,
        files: Map<String, ByteArray>,
        padded: Boolean = false
    ) {
        val bytes: ByteArray

        /** The sector each file's content lands in. */
        val lbaOf: Map<String, Int>

        init {
            val s = UmdIso.SECTOR
            val dirSectors = if (padded) 2 else 1
            val firstFile = 18 + dirSectors
            lbaOf = files.keys.mapIndexed { i, name -> name to firstFile + i }.toMap()
            bytes = ByteArray((firstFile + files.size.coerceAtLeast(1)) * s)

            // Sector 16: the volume descriptor, its signature, and the root
            // record at the offset the standard specifies.
            val pvd = 16 * s
            "CD001".toByteArray(Charsets.US_ASCII).copyInto(bytes, pvd + 1)
            record(pvd + 156, name = "\u0000", lba = 17, size = s, dir = true)

            // Sector 17: the root, which contains only the game's directory.
            var at = 17 * s
            at += record(at, "\u0000", 17, s, dir = true)
            record(at, dirName, 18, dirSectors * s, dir = true)

            // Sector 18: the game's directory. In [padded] mode its entries
            // start at the following sector and the first stays empty behind its
            // "." entry.
            at = 18 * s
            at += record(at, "\u0000", 18, dirSectors * s, dir = true)
            if (padded) at = 19 * s
            for ((name, content) in files) {
                at += record(at, "$name;1", lbaOf.getValue(name), content.size, dir = false)
                content.copyInto(bytes, lbaOf.getValue(name) * s)
            }
        }

        /** One ISO9660 directory record. Returns its length. */
        private fun record(at: Int, name: String, lba: Int, size: Int, dir: Boolean): Int {
            val raw = name.toByteArray(Charsets.US_ASCII)
            // The standard wants an even length: a padding byte otherwise.
            val len = 33 + raw.size + (if (raw.size % 2 == 0) 1 else 0)
            bytes[at] = len.toByte()
            le32(at + 2, lba)
            le32(at + 10, size)
            bytes[at + 25] = if (dir) 0x02 else 0x00
            bytes[at + 32] = raw.size.toByte()
            raw.copyInto(bytes, at + 33)
            return len
        }

        private fun le32(at: Int, value: Int) {
            for (i in 0 until 4) bytes[at + i] = ((value shr (8 * i)) and 0xFF).toByte()
        }
    }

    private fun sourceOf(bytes: ByteArray) = UmdIso.Source { offset, length ->
        if (offset < 0 || length < 0 || offset + length > bytes.size) null
        else bytes.copyOfRange(offset.toInt(), offset.toInt() + length)
    }

    @Test
    fun `trouve un fichier dans un sous-dossier malgre le suffixe de version`() {
        val disc = Disc("PSP_GAME", mapOf("ICON0.PNG" to ByteArray(120) { 0x42 }))

        val found = UmdIso.find(sourceOf(disc.bytes), listOf("PSP_GAME", "ICON0.PNG"))

        assertEquals(disc.lbaOf.getValue("ICON0.PNG").toLong() * sector, found?.offset)
        assertEquals(120, found?.size)
    }

    @Test
    fun `la casse du chemin demande n'a pas d'importance`() {
        val disc = Disc("PSP_GAME", mapOf("PARAM.SFO" to ByteArray(8)))

        assertEquals(
            UmdIso.find(sourceOf(disc.bytes), listOf("PSP_GAME", "PARAM.SFO")),
            UmdIso.find(sourceOf(disc.bytes), listOf("psp_game", "param.sfo"))
        )
    }

    @Test
    fun `deux fichiers du meme dossier ne se confondent pas`() {
        val disc = Disc(
            "PSP_GAME",
            mapOf("PARAM.SFO" to ByteArray(64), "ICON0.PNG" to ByteArray(120))
        )
        val source = sourceOf(disc.bytes)

        assertEquals(64, UmdIso.find(source, listOf("PSP_GAME", "PARAM.SFO"))?.size)
        assertEquals(120, UmdIso.find(source, listOf("PSP_GAME", "ICON0.PNG"))?.size)
    }

    @Test
    fun `un fichier absent rend null plutot que le premier venu`() {
        val disc = Disc("PSP_GAME", mapOf("PARAM.SFO" to ByteArray(8)))
        val source = sourceOf(disc.bytes)

        assertNull(UmdIso.find(source, listOf("PSP_GAME", "ICON0.PNG")))
        assertNull(UmdIso.find(source, listOf("UMD_DATA", "ICON0.PNG")))
    }

    @Test
    fun `un dossier demande comme fichier ne passe pas`() {
        val disc = Disc("PSP_GAME", mapOf("ICON0.PNG" to ByteArray(8)))

        // "PSP_GAME" exists, but it is a directory: reading it as an icon would
        // give table-of-contents bytes dressed up as an image.
        assertNull(UmdIso.find(sourceOf(disc.bytes), listOf("PSP_GAME")))
    }

    @Test
    fun `ce qui n'est pas un ISO9660 est refuse tout de suite`() {
        val notADisc = ByteArray(64 * sector) { 0x7F }

        assertNull(UmdIso.find(sourceOf(notADisc), listOf("PSP_GAME", "ICON0.PNG")))
    }

    @Test
    fun `un disque PS2 n'a pas de PSP_GAME et se reconnait a ca`() {
        // A PS2 burns a perfectly valid ISO9660, with `SYSTEM.CNF` at the root
        // and not a trace of a `PSP_GAME`. This null is what stops the library
        // listing its games: the `.iso` extension is the same as a UMD's and
        // distinguishes nothing.
        val ps2 = Disc("SYSTEM", mapOf("SYSTEM.CNF" to ByteArray(64)))
        val source = sourceOf(ps2.bytes)

        assertNull(UmdIso.find(source, listOf("PSP_GAME", "PARAM.SFO")))
        assertNull(UmdIso.find(source, listOf("PSP_GAME", "ICON0.PNG")))
    }

    @Test
    fun `une entree placee apres le remplissage de fin de secteur est vue quand meme`() {
        val disc = Disc("PSP_GAME", mapOf("ICON0.PNG" to ByteArray(120)), padded = true)

        val found = UmdIso.find(sourceOf(disc.bytes), listOf("PSP_GAME", "ICON0.PNG"))

        assertEquals(120, found?.size)
    }
}

/**
 * The `PARAM.SFO`, which gives the game its name and its disc id.
 */
class ParamSfoTest {

    /** Builds a record with the given keys, all of them strings. */
    private fun sfo(fields: Map<String, String>): ByteArray {
        val keyBytes = fields.keys.map { it.toByteArray(Charsets.UTF_8) + 0 }
        val valueBytes = fields.values.map { it.toByteArray(Charsets.UTF_8) + 0 }
        val keyTable = 20 + fields.size * 16
        val dataTable = keyTable + keyBytes.sumOf { it.size }
        val out = ByteArray(dataTable + valueBytes.sumOf { it.size })

        fun le32(at: Int, v: Int) { for (i in 0 until 4) out[at + i] = ((v shr (8 * i)) and 0xFF).toByte() }
        fun le16(at: Int, v: Int) { for (i in 0 until 2) out[at + i] = ((v shr (8 * i)) and 0xFF).toByte() }

        le32(0, 0x46535000)              // "\0PSF"
        le32(4, 0x01010000)
        le32(8, keyTable)
        le32(12, dataTable)
        le32(16, fields.size)

        var keyAt = 0
        var dataAt = 0
        for (i in fields.keys.indices) {
            val at = 20 + i * 16
            le16(at, keyAt)
            le16(at + 2, 0x0204)         // chaîne terminée par un zéro
            le32(at + 4, valueBytes[i].size)
            le32(at + 8, valueBytes[i].size)
            le32(at + 12, dataAt)
            keyBytes[i].copyInto(out, keyTable + keyAt)
            valueBytes[i].copyInto(out, dataTable + dataAt)
            keyAt += keyBytes[i].size
            dataAt += valueBytes[i].size
        }
        return out
    }

    @Test
    fun `lit le titre et l'identifiant de disque`() {
        val fields = ParamSfo.read(sfo(mapOf("DISC_ID" to "ULES01267", "TITLE" to "WipEout Pulse")))

        assertEquals("WipEout Pulse", fields["TITLE"])
        assertEquals("ULES01267", fields["DISC_ID"])
    }

    @Test
    fun `un titre accentue survit a l'aller-retour`() {
        val fields = ParamSfo.read(sfo(mapOf("TITLE" to "Astérix & Obélix")))

        assertEquals("Astérix & Obélix", fields["TITLE"])
    }

    @Test
    fun `ce qui n'est pas une fiche rend une table vide`() {
        assertEquals(emptyMap<String, String>(), ParamSfo.read(ByteArray(256)))
        assertEquals(emptyMap<String, String>(), ParamSfo.read(ByteArray(4)))
    }
}
