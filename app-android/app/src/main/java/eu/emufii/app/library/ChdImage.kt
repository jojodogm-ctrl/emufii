package eu.emufii.app.library

import io.airlift.compress.zstd.ZstdDecompressor
import java.io.ByteArrayInputStream
import java.util.zip.Inflater
import org.tukaani.xz.LZMAInputStream

object ChdImage {

    private const val MAGIC = "MComprHD"
    private const val VERSION_5 = 5
    private const val HEADER_V5_BYTES = 124

    private const val OFF_VERSION = 12
    private const val OFF_COMPRESSORS = 16
    private const val OFF_LOGICAL_BYTES = 32
    private const val OFF_MAP_OFFSET = 40
    private const val OFF_META_OFFSET = 48
    private const val OFF_HUNK_BYTES = 56
    private const val OFF_UNIT_BYTES = 60

    /** 2352 bytes of sector, then 96 of subcode. */
    private const val CD_FRAME_BYTES = 2448
    private const val CD_SECTOR_BYTES = 2352

    const val PVD_SECTOR = 16

    /** A Dreamcast GD-ROM is also `unitbytes 2448`; only this tag tells it from a PS2 CD. */
    private const val TAG_GDROM_TRACK = "CHGD"
    private const val TAG_GDROM_OLD = "CHGT"

    private const val TYPE_BASE_0 = 0
    private const val TYPE_NONE = 4
    private const val TYPE_SELF = 5
    private const val TYPE_PARENT = 6
    private const val TYPE_RLE_SMALL = 7
    private const val TYPE_RLE_LARGE = 8
    private const val TYPE_SELF_0 = 9
    private const val TYPE_SELF_1 = 10

    interface Source {
        fun read(offset: Long, into: ByteArray, count: Int): Int
    }

    fun readSector(source: Source, sectorIndex: Int = PVD_SECTOR): ByteArray? =
        open(source)?.readDiscSector(sectorIndex)

    /** Parses the map once; per-seek parsing reads gigabytes. */
    fun open(source: Source): Reader? {
        val header = ByteArray(HEADER_V5_BYTES)
        if (source.read(0, header, header.size) < header.size) return null
        if (String(header, 0, 8, Charsets.ISO_8859_1) != MAGIC) return null
        if (beInt(header, OFF_VERSION) != VERSION_5) return null

        val hunkBytes = beInt(header, OFF_HUNK_BYTES)
        val unitBytes = beInt(header, OFF_UNIT_BYTES)
        if (hunkBytes <= 0 || unitBytes <= 0) return null
        val logicalBytes = beLong(header, OFF_LOGICAL_BYTES)
        val mapOffset = beLong(header, OFF_MAP_OFFSET)
        val metaOffset = beLong(header, OFF_META_OFFSET)

        if (isGdRom(source, metaOffset)) return null

        val compressors = (0 until 4).map {
            String(header, OFF_COMPRESSORS + 4 * it, 4, Charsets.ISO_8859_1)
        }
        val rawCd = unitBytes == CD_FRAME_BYTES
        val totalHunks = ((logicalBytes + hunkBytes - 1) / hunkBytes).toInt()
        if (totalHunks <= 0 || totalHunks > MAX_HUNKS) return null
        val entries = mapEntries(source, mapOffset, totalHunks, hunkBytes) ?: return null
        return Reader(source, compressors, entries, logicalBytes, hunkBytes, unitBytes, rawCd)
    }

    private const val MAX_HUNKS = 4 * 1024 * 1024

    private fun isGdRom(source: Source, metaOffset: Long): Boolean {
        var offset = metaOffset
        var seen = 0
        val entry = ByteArray(16)
        while (offset > 0 && seen < MAX_METADATA_ENTRIES) {
            if (source.read(offset, entry, entry.size) < entry.size) return false
            val tag = String(entry, 0, 4, Charsets.ISO_8859_1)
            if (tag == TAG_GDROM_TRACK || tag == TAG_GDROM_OLD) return true
            offset = beLong(entry, 8)
            seen++
        }
        return false
    }

    private const val MAX_METADATA_ENTRIES = 64

    internal data class Entry(val type: Int, val offset: Long, val length: Int)
    internal class Entries(
        private val types: ByteArray,
        private val offsets: LongArray,
        private val lengths: IntArray,
    ) {
        val size: Int get() = types.size
        fun getOrNull(index: Int): Entry? = if (index in types.indices) {
            Entry(types[index].toInt() and 0xFF, offsets[index], lengths[index])
        } else null
    }

    /** Must not stop early: all hunk types are decoded before the first length. */
    private fun mapEntries(
        source: Source,
        mapOffset: Long,
        totalHunks: Int,
        hunkBytes: Int
    ): Entries? {
        val head = ByteArray(16)
        if (source.read(mapOffset, head, head.size) < head.size) return null
        val mapBytes = beInt(head, 0)
        if (mapBytes <= 0 || mapBytes > MAX_MAP_BYTES) return null
        val firstOffset = be48(head, 4)
        val lengthBits = head[12].toInt() and 0xFF
        val hunkBits = head[13].toInt() and 0xFF
        val parentBits = head[14].toInt() and 0xFF

        val compressed = ByteArray(mapBytes)
        if (source.read(mapOffset + 16, compressed, mapBytes) < mapBytes) return null
        val bits = BitReader(compressed)
        val huff = Huffman(numCodes = 16, maxBits = 8)
        if (!huff.importTreeRle(bits)) return null

        val types = ByteArray(totalHunks)
        var last = 0
        var repeat = 0
        for (i in 0 until totalHunks) {
            if (repeat > 0) {
                types[i] = last.toByte()
                repeat--
                continue
            }
            when (val value = huff.decodeOne(bits) ?: return null) {
                TYPE_RLE_SMALL -> {
                    types[i] = last.toByte()
                    repeat = 2 + (huff.decodeOne(bits) ?: return null)
                }
                TYPE_RLE_LARGE -> {
                    types[i] = last.toByte()
                    val high = huff.decodeOne(bits) ?: return null
                    val low = huff.decodeOne(bits) ?: return null
                    repeat = 2 + 16 + (high shl 4) + low
                }
                else -> {
                    last = value
                    types[i] = value.toByte()
                }
            }
        }

        val offsets = LongArray(totalHunks)
        val lengths = IntArray(totalHunks)
        var current = firstOffset
        var lastSelf = 0L
        for (i in 0 until totalHunks) {
            val type = types[i].toInt()
            var offset = current
            var length = 0
            when (type) {
                TYPE_BASE_0, 1, 2, 3 -> {
                    length = bits.read(lengthBits) ?: return null
                    current += length
                    bits.read(16) ?: return null
                }
                TYPE_NONE -> {
                    length = hunkBytes
                    current += hunkBytes
                    bits.read(16) ?: return null
                }
                TYPE_SELF -> {
                    offset = (bits.read(hunkBits) ?: return null).toLong()
                    lastSelf = offset
                }
                TYPE_PARENT -> {
                    bits.read(parentBits) ?: return null
                }
                TYPE_SELF_0 -> offset = lastSelf
                TYPE_SELF_1 -> {
                    lastSelf++
                    offset = lastSelf
                }
                else -> Unit
            }
            offsets[i] = offset
            lengths[i] = length
        }
        return Entries(types, offsets, lengths)
    }

    private const val MAX_MAP_BYTES = 64 * 1024 * 1024

    private fun hunkPayload(
        source: Source,
        entries: Entries,
        index: Int,
        entry: Entry,
        compressors: List<String>,
        hunkBytes: Int,
        rawCd: Boolean,
        depth: Int = 0,
    ): ByteArray? {
        if (depth > MAX_SELF_DEPTH) return null
        if (entry.type == TYPE_SELF || entry.type == TYPE_SELF_0 || entry.type == TYPE_SELF_1) {
            val referenced = entry.offset.toInt()
            if (referenced !in 0 until entries.size || referenced == index) return null
            return hunkPayload(
                source,
                entries,
                referenced,
                entries.getOrNull(referenced) ?: return null,
                compressors,
                hunkBytes,
                rawCd,
                depth + 1,
            )
        }
        if (entry.type == TYPE_PARENT) return null

        val storedLength = if (entry.type == TYPE_NONE) hunkBytes else entry.length
        if (storedLength <= 0 || storedLength > MAX_HUNK_BYTES) return null
        val raw = ByteArray(storedLength)
        if (source.read(entry.offset, raw, storedLength) < storedLength) return null

        val frames = hunkBytes / CD_FRAME_BYTES
        val decoded = if (entry.type == TYPE_NONE) raw else when (val codec = compressors.getOrNull(entry.type)) {
            "cdlz", "cdzl" -> {
                if (!rawCd || frames <= 0) return null
                // CD codec header: one ECC bit per frame rounded to bytes, then the compressed sector length.
                val eccBytes = (frames + 7) / 8
                val lengthBytes = if (hunkBytes < 65536) 2 else 3
                val headerBytes = eccBytes + lengthBytes
                if (raw.size <= headerBytes) return null
                var complen = ((raw[eccBytes].toInt() and 0xFF) shl 8) or
                    (raw[eccBytes + 1].toInt() and 0xFF)
                if (lengthBytes > 2) {
                    complen = (complen shl 8) or (raw[eccBytes + 2].toInt() and 0xFF)
                }
                if (complen <= 0 || headerBytes + complen > raw.size) return null
                val out = frames * CD_SECTOR_BYTES
                if (codec == "cdzl") inflate(raw, headerBytes, complen, out)
                else lzma(raw, headerBytes, complen, out)
            }
            "zlib" -> inflate(raw, 0, raw.size, hunkBytes)
            "lzma" -> lzma(raw, 0, raw.size, hunkBytes)
            "zstd" -> zstd(raw, hunkBytes)
            "flac" -> flacSilence(raw, hunkBytes)
            else -> null
        }
        decoded ?: return null
        return if (rawCd && decoded.size == hunkBytes) stripSubcode(decoded, frames) else decoded
    }

    private const val MAX_SELF_DEPTH = 64

    private fun stripSubcode(framesBytes: ByteArray, frames: Int): ByteArray? {
        if (frames <= 0 || frames * CD_FRAME_BYTES > framesBytes.size) return null
        return ByteArray(frames * CD_SECTOR_BYTES).also { out ->
            for (frame in 0 until frames) {
                framesBytes.copyInto(
                    out,
                    frame * CD_SECTOR_BYTES,
                    frame * CD_FRAME_BYTES,
                    frame * CD_FRAME_BYTES + CD_SECTOR_BYTES,
                )
            }
        }
    }

    class Reader internal constructor(
        private val source: Source,
        private val compressors: List<String>,
        private val entries: Entries,
        private val logicalBytes: Long,
        private val hunkBytes: Int,
        private val unitBytes: Int,
        private val rawCd: Boolean,
    ) : Ps2DiscIdentityReader.Reader {
        private var cachedIndex = -1
        private var cachedHunk: ByteArray? = null
        private var isoUserOffset: Int? = null

        fun readDiscSector(sectorIndex: Int): ByteArray? {
            if (sectorIndex < 0) return null
            if (!rawCd) {
                if (unitBytes <= 0) return null
                val bytes = ByteArray(unitBytes)
                return bytes.takeIf { readFlat(sectorIndex.toLong() * unitBytes, it, it.size) == it.size }
            }
            val logicalOffset = sectorIndex.toLong() * unitBytes
            if (logicalOffset + unitBytes > logicalBytes) return null
            val hunkIndex = (logicalOffset / hunkBytes).toInt()
            val within = (logicalOffset % hunkBytes).toInt()
            val frame = within / CD_FRAME_BYTES
            val hunk = hunk(hunkIndex) ?: return null
            val at = frame * CD_SECTOR_BYTES
            if (at + CD_SECTOR_BYTES > hunk.size) return null
            return hunk.copyOfRange(at, at + CD_SECTOR_BYTES)
        }

        override fun read(offset: Long, into: ByteArray, count: Int): Int {
            if (offset < 0 || count < 0 || count > into.size) return 0
            if (!rawCd) return readFlat(offset, into, count)
            val userOffset = isoUserOffset ?: detectIsoUserOffset()?.also { isoUserOffset = it }
                ?: return 0
            var done = 0
            while (done < count) {
                val isoAt = offset + done
                val sectorIndex = isoAt / ISO_SECTOR_BYTES
                val within = (isoAt % ISO_SECTOR_BYTES).toInt()
                val sector = readDiscSector(sectorIndex.toInt()) ?: break
                val copied = minOf(count - done, ISO_SECTOR_BYTES - within)
                if (userOffset + within + copied > sector.size) break
                sector.copyInto(into, done, userOffset + within, userOffset + within + copied)
                done += copied
            }
            return done
        }

        private fun detectIsoUserOffset(): Int? {
            val pvd = readDiscSector(PVD_SECTOR) ?: return null
            return intArrayOf(0, 16, 24).firstOrNull { at ->
                at + 6 <= pvd.size && String(pvd, at + 1, 5, Charsets.ISO_8859_1) == "CD001"
            }
        }

        private fun readFlat(offset: Long, into: ByteArray, count: Int): Int {
            if (offset < 0 || offset >= logicalBytes) return 0
            var done = 0
            val allowed = minOf(count.toLong(), logicalBytes - offset).toInt()
            while (done < allowed) {
                val at = offset + done
                val index = (at / hunkBytes).toInt()
                val within = (at % hunkBytes).toInt()
                val payload = hunk(index) ?: break
                val copied = minOf(allowed - done, payload.size - within)
                if (copied <= 0) break
                payload.copyInto(into, done, within, within + copied)
                done += copied
            }
            return done
        }

        private fun hunk(index: Int): ByteArray? {
            if (index == cachedIndex) return cachedHunk
            val entry = entries.getOrNull(index) ?: return null
            val decoded = hunkPayload(
                source,
                entries,
                index,
                entry,
                compressors,
                hunkBytes,
                rawCd,
            )
            cachedIndex = index
            cachedHunk = decoded
            return decoded
        }

    }

    private const val ISO_SECTOR_BYTES = 2048

    private const val MAX_HUNK_BYTES = 4 * 1024 * 1024

    private fun inflate(src: ByteArray, at: Int, count: Int, outSize: Int): ByteArray? =
        runCatching {
            val inflater = Inflater(true)
            try {
                inflater.setInput(src, at, count)
                val out = ByteArray(outSize)
                var done = 0
                while (done < outSize) {
                    val n = inflater.inflate(out, done, outSize - done)
                    if (n == 0) break
                    done += n
                }
                if (done == outSize) out else null
            } finally {
                inflater.end()
            }
        }.getOrNull()

    private fun zstd(src: ByteArray, outSize: Int): ByteArray? = runCatching {
        val out = ByteArray(outSize)
        val written = ZstdDecompressor().decompress(src, 0, src.size, out, 0, out.size)
        if (written == outSize) out else null
    }.getOrNull()

    internal fun flacSilence(src: ByteArray, outSize: Int): ByteArray? {
        // First byte is CHD's endianness marker, L or B.
        if (src.size < 10 || (src[0].toInt() != 'L'.code && src[0].toInt() != 'B'.code)) return null
        if (src[1].toInt() and 0xFF != 0xFF || src[2].toInt() and 0xFE != 0xF8) return null
        val blockCode = (src[3].toInt() ushr 4) and 0x0F
        val rateCode = src[3].toInt() and 0x0F
        val assignment = (src[4].toInt() ushr 4) and 0x0F
        val channels = if (assignment <= 7) assignment + 1 else if (assignment <= 10) 2 else return null

        var at = 5
        val firstNumber = src.getOrNull(at)?.toInt()?.and(0xFF) ?: return null
        val numberBytes = when {
            firstNumber and 0x80 == 0 -> 1
            firstNumber and 0xE0 == 0xC0 -> 2
            firstNumber and 0xF0 == 0xE0 -> 3
            firstNumber and 0xF8 == 0xF0 -> 4
            firstNumber and 0xFC == 0xF8 -> 5
            firstNumber and 0xFE == 0xFC -> 6
            firstNumber == 0xFE -> 7
            else -> return null
        }
        at += numberBytes
        at += when (blockCode) { 6 -> 1; 7 -> 2; else -> 0 }
        at += when (rateCode) { 12 -> 1; 13, 14 -> 2; else -> 0 }
        at++ // header CRC-8
        if (at >= src.size - 2) return null

        val bits = BitReader(src.copyOfRange(at, src.size - 2)) // frame CRC-16 is last
        repeat(channels) {
            if (bits.read(1) != 0 || bits.read(6) != 0) return null // constant subframe
            val wasted = bits.read(1) ?: return null
            var sampleBits = 16
            if (wasted != 0) {
                var wastedBits = 1
                while (bits.read(1) == 0) wastedBits++
                sampleBits -= wastedBits
            }
            if (sampleBits <= 0 || bits.read(sampleBits) != 0) return null
        }
        return ByteArray(outSize)
    }

    /** CHD's implicit LZMA props: lc=3, lp=0, pb=2 (0x5D). */
    private fun lzma(src: ByteArray, at: Int, count: Int, outSize: Int): ByteArray? =
        runCatching {
            val stream = LZMAInputStream(
                ByteArrayInputStream(src, at, count),
                outSize.toLong(),
                LZMA_PROPS_BYTE,
                dictionarySizeFor(outSize)
            )
            stream.use {
                val out = ByteArray(outSize)
                var done = 0
                while (done < outSize) {
                    val n = it.read(out, done, outSize - done)
                    if (n <= 0) break
                    done += n
                }
                if (done == outSize) out else null
            }
        }.getOrNull()

    private const val LZMA_PROPS_BYTE: Byte = 0x5D

    private fun dictionarySizeFor(size: Int): Int {
        var dict = 4096
        while (dict < size && dict < (1 shl 26)) dict = dict shl 1
        return dict
    }

    private class BitReader(private val data: ByteArray) {
        private var position = 0L

        fun read(count: Int): Int? {
            if (count == 0) return 0
            if (count < 0 || count > 32) return null
            var value = 0
            repeat(count) {
                val index = (position ushr 3).toInt()
                if (index >= data.size) return null
                val bit = (data[index].toInt() ushr (7 - (position and 7).toInt())) and 1
                value = (value shl 1) or bit
                position++
            }
            return value
        }
    }

    /** Per huffman.cpp: the repeat count is a third read, and it repeats the last length, not zero. */
    private class Huffman(private val numCodes: Int, private val maxBits: Int) {
        private val lengths = IntArray(numCodes)
        private val codes = HashMap<Int, Int>()

        fun importTreeRle(bits: BitReader): Boolean {
            val numBits = if (maxBits >= 16) 5 else if (maxBits >= 8) 4 else 3
            var current = 0
            while (current < numCodes) {
                val nodeBits = bits.read(numBits) ?: return false
                if (nodeBits != 1) {
                    lengths[current++] = nodeBits
                } else {
                    val escaped = bits.read(numBits) ?: return false
                    if (escaped == 1) {
                        lengths[current++] = 1
                    } else {
                        var repeat = (bits.read(numBits) ?: return false) + 3
                        while (repeat > 0 && current < numCodes) {
                            lengths[current++] = escaped
                            repeat--
                        }
                    }
                }
            }
            return assignCanonicalCodes()
        }

        private fun assignCanonicalCodes(): Boolean {
            val histogram = IntArray(33)
            for (length in lengths) {
                if (length > maxBits) return false
                histogram[length]++
            }
            var start = 0
            for (length in 32 downTo 1) {
                val next = (start + histogram[length]) shr 1
                if (length != 1 && next * 2 != start + histogram[length]) return false
                histogram[length] = start
                start = next
            }
            codes.clear()
            for (symbol in 0 until numCodes) {
                val length = lengths[symbol]
                if (length > 0) {
                    codes[key(length, histogram[length])] = symbol
                    histogram[length]++
                }
            }
            return true
        }

        fun decodeOne(bits: BitReader): Int? {
            var code = 0
            for (length in 1..maxBits) {
                code = (code shl 1) or (bits.read(1) ?: return null)
                codes[key(length, code)]?.let { return it }
            }
            return null
        }

        private fun key(length: Int, code: Int) = (length shl 24) or code
    }

    private fun beInt(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF shl 24) or
            (bytes[at + 1].toInt() and 0xFF shl 16) or
            (bytes[at + 2].toInt() and 0xFF shl 8) or
            (bytes[at + 3].toInt() and 0xFF)

    private fun beLong(bytes: ByteArray, at: Int): Long {
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (bytes[at + i].toLong() and 0xFF)
        return value
    }

    private fun be48(bytes: ByteArray, at: Int): Long {
        var value = 0L
        for (i in 0 until 6) value = (value shl 8) or (bytes[at + i].toLong() and 0xFF)
        return value
    }
}
