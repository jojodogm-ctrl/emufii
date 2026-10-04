package eu.emufii.app.ps2

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Ps2CardPatchTest {

    private val epoch = 1755729273L
    private val benchId = hex("3027d42057 0694 80".replace(" ", ""))


    @Test
    fun `a blank card is formatted and carries the save`() {
        val blank = ByteArray(16384 * 528) { 0xFF.toByte() }
        val patched = Ps2CardPatch.inject(blank, epochSecond = epoch)
        val saves = readAllSaves(patched)
        assertEquals(listOf("BWNETCNF"), saves.keys.toList())
        assertEquals(
            6,
            saves.getValue("BWNETCNF").size,
        )
        assertArrayEquals(Ps2NetcnfConfig.ifcDat(), saves.getValue("BWNETCNF").getValue("ifc000.dat"))
    }

    @Test
    fun `a blank sixteen megabyte card gets sixteen megabyte geometry`() {
        val blank = ByteArray(32768 * 528) { 0xFF.toByte() }
        val patched = Ps2CardPatch.inject(blank, epochSecond = epoch)
        val clusters = u32(page(patched, 0), 0x30)
        assertEquals(16384, clusters)
        val saves = readAllSaves(patched)
        assertTrue("BWNETCNF" in saves)
    }

    @Test
    fun `a ps1 card is refused, not mangled`() {
        try {
            Ps2CardPatch.inject(ByteArray(0x20000) { 0xFF.toByte() })
            fail("expected CardFormatException")
        } catch (expected: Ps2CardPatch.CardFormatException) {
        }
    }

    @Test
    fun `a foreign file is refused`() {
        try {
            Ps2CardPatch.inject(ByteArray(65536) { 0x5A.toByte() })
            fail("expected CardFormatException")
        } catch (expected: Ps2CardPatch.CardFormatException) {
        }
    }


    @Test
    fun `every save on a used card survives injection`() {
        var card = Ps2MemoryCard.generate("Emufii", epochSecond = epoch)
        card = Ps2CardPatch.addSave(
            card, "BESLES-52942MC3",
            listOf("icon.sys" to ByteArray(964) { (it * 3).toByte() },
                "gamedata.bin" to ByteArray(2500) { (it * 7).toByte() }), // spans three clusters
            epochSecond = epoch,
        )
        card = Ps2CardPatch.addSave(
            card, "BASLUS-20911TIME",
            listOf("save.dat" to ByteArray(100) { 0x11 }),
            epochSecond = epoch,
        )

        val patched = Ps2CardPatch.inject(card, epochSecond = epoch)
        val saves = readAllSaves(patched)
        assertEquals(setOf("BWNETCNF", "BESLES-52942MC3", "BASLUS-20911TIME"), saves.keys)
        assertArrayEquals(
            ByteArray(2500) { (it * 7).toByte() },
            saves.getValue("BESLES-52942MC3").getValue("gamedata.bin"),
        )
        assertArrayEquals(
            ByteArray(964) { (it * 3).toByte() },
            saves.getValue("BESLES-52942MC3").getValue("icon.sys"),
        )
        assertArrayEquals(
            ByteArray(100) { 0x11 },
            saves.getValue("BASLUS-20911TIME").getValue("save.dat"),
        )
        verifyStructure(patched)
    }

    @Test
    fun `injection returns a clone and never mutates the source card`() {
        val source = Ps2MemoryCard.generate("Emufii", consoleId = benchId, epochSecond = epoch)
        val before = source.copyOf()

        val patched = Ps2CardPatch.inject(source, epochSecond = epoch)

        assertArrayEquals(before, source)
        assertTrue(!patched.contentEquals(source))
        assertArrayEquals(
            Ps2NetcnfConfig.ifcDat(),
            readAllSaves(patched).getValue("BWNETCNF").getValue("ifc000.dat"),
        )
    }

    @Test
    fun `an existing configuration is replaced, not duplicated`() {
        var card = Ps2MemoryCard.generate("Emufii", consoleId = benchId, epochSecond = epoch)
        val patched = Ps2CardPatch.inject(card, epochSecond = epoch)
        val saves = readAllSaves(patched)
        assertEquals(listOf("BWNETCNF"), saves.keys.toList())
        assertArrayEquals(
            Ps2NetcnfConfig.INDEX,
            saves.getValue("BWNETCNF").getValue("BWNETCNF"),
        )
        val dev = saves.getValue("BWNETCNF").getValue("dev000.dat")
        assertTrue(
            String(Ps2NetcnfConfig.decode(dev, Ps2NetcnfConfig.ARMSX2_CONSOLE_ID), Charsets.US_ASCII)
                .contains("Ethernet (Network Adaptor)"),
        )
        verifyStructure(patched)
    }

    @Test
    fun `compaction moves the later saves up and fixes their back-references`() {
        var card = Ps2MemoryCard.generate("Emufii", epochSecond = epoch)
        card = Ps2CardPatch.addSave(card, "AAA-SAVE", listOf("a.bin" to ByteArray(10) { 1 }), epochSecond = epoch)
        card = Ps2CardPatch.addSave(card, "BBB-SAVE", listOf("b.bin" to ByteArray(10) { 2 }), epochSecond = epoch)

        val patched = Ps2CardPatch.inject(card, epochSecond = epoch)
        val saves = readAllSaves(patched)
        assertEquals(
            listOf("AAA-SAVE", "BBB-SAVE", "BWNETCNF"),
            saves.keys.toList(),
        )
        val root = chainOf(patched, 0)
        for (slot in 2 until 5) {
            val saveDir = chainOf(patched, u32(root, slot * PAGE_DATA + 0x10))
            assertEquals("slot $slot", slot, u32(saveDir, 0x14))
        }
        verifyStructure(patched)
    }

    @Test
    fun `fragmented free space is allocated around, not through`() {
        var card = Ps2MemoryCard.generate("Emufii", epochSecond = epoch)
        var patched = Ps2CardPatch.inject(card, epochSecond = epoch)
        patched = Ps2CardPatch.addSave(
            patched, "BIG-SAVE",
            listOf("big.bin" to ByteArray(7000) { (it * 11).toByte() }),
            epochSecond = epoch,
        )
        patched = Ps2CardPatch.inject(patched, epochSecond = epoch)
        val saves = readAllSaves(patched)
        assertEquals(setOf("BIG-SAVE", "BWNETCNF"), saves.keys)
        assertArrayEquals(ByteArray(7000) { (it * 11).toByte() }, saves.getValue("BIG-SAVE").getValue("big.bin"))
        verifyStructure(patched)
    }


    @Test
    fun `the console a card was encrypted for is recovered from the card`() {
        val card = Ps2MemoryCard.generate("Emufii", consoleId = benchId, epochSecond = epoch)
        assertArrayEquals(benchId, Ps2CardPatch.recoverConsoleId(card))
    }

    @Test
    fun `the default console id is recovered from a card this app wrote`() {
        val card = Ps2MemoryCard.generate("Emufii", epochSecond = epoch)
        assertArrayEquals(Ps2NetcnfConfig.ARMSX2_CONSOLE_ID, Ps2CardPatch.recoverConsoleId(card))
    }

    @Test
    fun `a game save added after preparation does not lose the profile`() {
        val prepared = Ps2MemoryCard.generate("Emufii", consoleId = benchId, epochSecond = epoch)
        // addSave writes in place, unlike inject.
        val played = Ps2CardPatch.addSave(
            prepared.copyOf(),
            "BASLUS-21355MC3",
            listOf("icon.sys" to ByteArray(964) { 7 }, "mc3.dat" to ByteArray(140_000) { 3 }),
            epochSecond = epoch
        )
        assertFalse(prepared.contentEquals(played))
        assertArrayEquals(benchId, Ps2CardPatch.recoverConsoleId(played))
    }

    @Test
    fun `a card without the save yields no identity`() {
        val card = Ps2MemoryCard.generate("Emufii", epochSecond = epoch)
        val stripped = Ps2CardPatch.addSave(card, "OTHER", listOf("x" to ByteArray(10)), epochSecond = epoch)
        val without = removeNetworkSave(stripped)
        assertNull(Ps2CardPatch.recoverConsoleId(without))
    }

    @Test
    fun `recovery then injection re-keys for the recovered console`() {
        val card = Ps2MemoryCard.generate("Emufii", consoleId = benchId, epochSecond = epoch)
        val id = Ps2CardPatch.recoverConsoleId(card)
        assertNotNull(id)
        val patched = Ps2CardPatch.inject(card, consoleId = id!!, epochSecond = epoch)
        val files = readAllSaves(patched).getValue("BWNETCNF")
        assertTrue(
            String(Ps2NetcnfConfig.decode(files.getValue("ifc000.dat"), benchId), Charsets.US_ASCII)
                .endsWith("type nic\ndhcp\n"),
        )
    }


    private val PAGE_DATA = 512

    private fun readAllSaves(card: ByteArray): Map<String, Map<String, ByteArray>> {
        val root = chainOf(card, 0)
        val out = linkedMapOf<String, Map<String, ByteArray>>()
        for (slot in 0 until root.size / PAGE_DATA) {
            val at = slot * PAGE_DATA
            val mode = u32(root, at)
            if (mode == -1 || mode == 0) break
            val name = name(root, at) ?: break
            if (name == "." || name == "..") continue
            val dir = chainOf(card, u32(root, at + 0x10))
            val files = mutableMapOf<String, ByteArray>()
            for (fileSlot in 0 until dir.size / PAGE_DATA) {
                val fileAt = fileSlot * PAGE_DATA
                val fileMode = u32(dir, fileAt)
                if (fileMode == -1 || fileMode == 0) break
                val fileName = name(dir, fileAt) ?: break
                if (fileName == "." || fileName == "..") continue
                if (fileMode and 0x0010 == 0) continue
                val first = u32(dir, fileAt + 0x10)
                files[fileName] =
                    if (first == -1) ByteArray(0) else chainOf(card, first).copyOf(u32(dir, fileAt + 4))
            }
            out[name] = files
        }
        return out
    }

    private fun verifyStructure(card: ByteArray) {
        val pages = card.size / 528
        for (p in 0 until pages) {
            val spare = card.copyOfRange(p * 528 + 512, p * 528 + 528)
            if (spare.contentEquals(ByteArray(16) { 0xFF.toByte() })) continue
            assertArrayEquals("page $p", Ps2MemoryCard.spare(card.copyOfRange(p * 528, p * 528 + 512)), spare)
        }
        val clustersSeen = mutableSetOf<Int>()
        fun walk(rel: Int) {
            var current = rel
            val local = mutableSetOf<Int>()
            while (current != -1 && current in 0 until allocEnd(card)) {
                assertTrue("cluster $current used twice", local.add(current))
                assertTrue("cluster $current shared between chains", clustersSeen.add(allocOffset(card) + current))
                current = fatEntry(card, current)
                if (current == -1) break
                current = current and 0x7FFFFFFF
            }
        }

        val root = chainOf(card, 0)
        for (slot in 0 until root.size / PAGE_DATA) {
            val at = slot * PAGE_DATA
            val mode = u32(root, at)
            if (mode == -1 || mode == 0) break
            val name = name(root, at) ?: break
            if (name == "." || name == "..") continue
            val dirFirst = u32(root, at + 0x10)
            walk(dirFirst)
            val dir = chainOf(card, dirFirst)
            for (fileSlot in 0 until dir.size / PAGE_DATA) {
                val fileAt = fileSlot * PAGE_DATA
                val fileMode = u32(dir, fileAt)
                if (fileMode == -1 || fileMode == 0) break
                val fileName = name(dir, fileAt) ?: break
                if (fileName == "." || fileName == "..") continue
                if (fileMode and 0x0010 != 0) walk(u32(dir, fileAt + 0x10))
            }
        }
    }

    private fun removeNetworkSave(card: ByteArray): ByteArray {
        val out = card.copyOf()
        val root = chainOf(card, 0)
        for (slot in 2 until root.size / PAGE_DATA) {
            val at = slot * PAGE_DATA
            val mode = u32(root, at)
            if (mode == -1 || mode == 0) break
            if (name(root, at) == "BWNETCNF") {
                val cluster = allocOffset(card) + at / 1024
                val within = at % 1024
                for (half in 0 until 2) {
                    val page = 2 * cluster + half
                    for (i in 0 until 512) out[page * 528 + within % 1024 + half * 512 + i] = 0xFF.toByte()
                    for (i in 0 until 16) out[page * 528 + 512 + i] = 0xFF.toByte()
                }
                break
            }
        }
        return out
    }

    private fun allocOffset(card: ByteArray): Int = u32(page(card, 0), 0x34)

    private fun allocEnd(card: ByteArray): Int = u32(page(card, 0), 0x38)

    private fun page(card: ByteArray, p: Int) = card.copyOfRange(p * 528, p * 528 + 512)

    private fun clusterOf(card: ByteArray, c: Int): ByteArray {
        val out = ByteArray(1024)
        for (half in 0 until 2) System.arraycopy(card, (2 * c + half) * 528, out, half * 512, 512)
        return out
    }

    private fun chainOf(card: ByteArray, first: Int): ByteArray {
        val allocOffset = allocOffset(card)
        val allocEnd = allocEnd(card)
        val chunks = mutableListOf<ByteArray>()
        var rel = first
        val seen = mutableSetOf<Int>()
        while (rel != -1 && rel in 0 until allocEnd) {
            assertTrue(seen.add(rel))
            chunks.add(clusterOf(card, allocOffset + rel))
            val entry = fatEntry(card, rel)
            rel = if (entry == -1) -1 else entry and 0x7FFFFFFF
        }
        return chunks.reduce { acc, bytes -> acc + bytes }
    }

    private fun fatEntry(card: ByteArray, rel: Int): Int {
        val ifd = clusterOf(card, 8)
        val fatCluster = u32(ifd, (rel / 256) * 4)
        return u32(clusterOf(card, fatCluster), (rel % 256) * 4)
    }

    private fun u32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

    private fun name(entry: ByteArray, at: Int): String? {
        var end = at + 0x40
        while (end < at + 0x60 && entry[end].toInt() != 0) end++
        val bytes = entry.copyOfRange(at + 0x40, end)
        if (bytes.isEmpty()) return null
        return String(bytes, Charsets.US_ASCII)
    }

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) out[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return out
    }
}
