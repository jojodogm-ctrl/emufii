package eu.emufii.app.library

import java.io.ByteArrayInputStream
import java.util.zip.Inflater
import org.tukaani.xz.LZMAInputStream

/**
 * Reading just enough of a CHD to know which console pressed the disc.
 *
 * `.chd` is the one container where the extension settles nothing: the PSP, the
 * PS2 and the Dreamcast all ship in it, and on this machine two of the three are
 * sitting in neighbouring folders. Unlike an `.iso`, the bytes that would answer
 * the question are compressed, so nothing can be read straight off the front of
 * the file. This decodes the container far enough to hand back one disc sector,
 * and no further: no full extraction, no temporary file, a few hundred kilobytes
 * read per candidate.
 *
 * Everything below was measured on two real files, never taken from a wiki:
 * a Dreamcast `Phantasy Star Online Ver. 2` and a PS2 `Unreal Tournament`.
 * Both are v5, both `cdlz/cdzl/cdfl`, both `hunkbytes 19584` over
 * `unitbytes 2448`. The PS2 one yields, at sector 16 offset 24, exactly:
 *
 * ```
 * CD001   system id 'PLAYSTATION'   volume id 'UT'
 * ```
 *
 * which is the same descriptor [DiscImage] already reads on a plain `.iso`, and
 * the reason this file stops at the sector rather than deciding anything: the
 * console is settled in one place for every disc format, not two.
 */
object ChdImage {

    private const val MAGIC = "MComprHD"
    private const val VERSION_5 = 5
    private const val HEADER_V5_BYTES = 124

    // Header fields, at the offsets the format fixes.
    private const val OFF_VERSION = 12
    private const val OFF_COMPRESSORS = 16
    private const val OFF_LOGICAL_BYTES = 32
    private const val OFF_MAP_OFFSET = 40
    private const val OFF_META_OFFSET = 48
    private const val OFF_HUNK_BYTES = 56
    private const val OFF_UNIT_BYTES = 60

    /** A raw CD frame: 2352 bytes of sector, then 96 of subcode. */
    private const val CD_FRAME_BYTES = 2448
    private const val CD_SECTOR_BYTES = 2352

    /** The ISO9660 volume descriptor lives in sector 16, on every disc. */
    const val PVD_SECTOR = 16

    /**
     * Metadata tags, read from the chain that starts right after the header.
     *
     * `CHGD`/`CHGT` are the GD-ROM ones, and they are the whole reason this
     * check exists: a Dreamcast disc is the false positive to avoid, it is
     * `unitbytes 2448` exactly like a PS2 CD, and only the tag tells them apart.
     * Measured: the Dreamcast file carries `CHGD "TRACK:1 TYPE:MODE1_RAW …"`
     * where the PS2 one carries `CHT2 "TRACK:1 TYPE:MODE2_RAW …"`.
     */
    private const val TAG_GDROM_TRACK = "CHGD"
    private const val TAG_GDROM_OLD = "CHGT"

    /** Hunk compression types, as the v5 map encodes them. */
    private const val TYPE_BASE_0 = 0
    private const val TYPE_NONE = 4
    private const val TYPE_SELF = 5
    private const val TYPE_PARENT = 6
    private const val TYPE_RLE_SMALL = 7
    private const val TYPE_RLE_LARGE = 8
    private const val TYPE_SELF_0 = 9
    private const val TYPE_SELF_1 = 10

    /** Where the bytes come from, so tests need no Android and no provider. */
    interface Source {
        /** Fills [into] from [offset]; returns how much was actually read. */
        fun read(offset: Long, into: ByteArray, count: Int): Int
    }

    /**
     * One disc sector, or null when this file cannot answer.
     *
     * Null is the ordinary answer for a GD-ROM, for a codec we do not decode,
     * for a CHD older than v5 and for anything truncated. Every one of them
     * means the same thing to the caller, and it is never "this is not a PS2":
     * it is "the bytes did not say", which leaves the file exactly where its
     * extension put it.
     */
    fun readSector(source: Source, sectorIndex: Int = PVD_SECTOR): ByteArray? {
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

        // A raw CD hunk interleaves sector and subcode; anything else is a flat
        // run of bytes. That is the only thing that changes where a sector sits.
        val rawCd = unitBytes == CD_FRAME_BYTES
        val sectorBytes = if (rawCd) CD_SECTOR_BYTES else unitBytes
        val logicalOffset = sectorIndex.toLong() * unitBytes
        if (logicalOffset + unitBytes > logicalBytes) return null
        val hunkIndex = (logicalOffset / hunkBytes).toInt()
        val withinHunk = (logicalOffset % hunkBytes).toInt()

        val totalHunks = ((logicalBytes + hunkBytes - 1) / hunkBytes).toInt()
        val entry = mapEntry(source, mapOffset, totalHunks, hunkIndex, hunkBytes) ?: return null
        val hunk = hunkPayload(source, entry, compressors, hunkBytes, rawCd) ?: return null

        // In a CD hunk the decoded payload is the sectors alone, subcode stripped
        // out, so the frame stride is 2352 rather than 2448.
        val at = if (rawCd) withinHunk / CD_FRAME_BYTES * CD_SECTOR_BYTES else withinHunk
        if (at + sectorBytes > hunk.size) return null
        return hunk.copyOfRange(at, at + sectorBytes)
    }

    /**
     * The Dreamcast, excluded before a single byte is decompressed.
     *
     * The metadata chain sits at `0x7c` on both files measured, that is, right
     * behind the header, so this costs one short read and settles the console
     * this project must never claim.
     */
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

    /** Enough to walk a disc's tracks; a bound, so a cyclic chain cannot hang. */
    private const val MAX_METADATA_ENTRIES = 64

    private class Entry(val type: Int, val offset: Long, val length: Int)

    /**
     * The map entry for one hunk, decoded from a Huffman-compressed stream.
     *
     * Two full passes, and the first one cannot be cut short: the types of
     * *every* hunk are decoded before the first length is written, so stopping
     * at the hunk we want reads lengths out of the middle of the type stream and
     * yields offsets that look plausible and decompress to nothing. That mistake
     * cost an afternoon; the loop below runs to [totalHunks] deliberately.
     */
    private fun mapEntry(
        source: Source,
        mapOffset: Long,
        totalHunks: Int,
        want: Int,
        hunkBytes: Int
    ): Entry? {
        if (want < 0 || want >= totalHunks) return null
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

        var current = firstOffset
        var lastSelf = 0L
        for (i in 0..want) {
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
                // A parent CHD is a delta against another file, which Emufii
                // never has to hand: there is nothing to read, and saying so is
                // better than reading the wrong bytes.
                else -> return null
            }
            if (i == want) return Entry(type, offset, length)
        }
        return null
    }

    /** A bound on the map: 64 MB covers a dual-layer disc many times over. */
    private const val MAX_MAP_BYTES = 64 * 1024 * 1024

    /**
     * The bytes of one hunk, decompressed.
     *
     * For a raw CD the payload returned is the sectors only: the codec keeps
     * sector data and subcode in two separately compressed blocks, and the
     * subcode carries nothing that names a console.
     */
    private fun hunkPayload(
        source: Source,
        entry: Entry,
        compressors: List<String>,
        hunkBytes: Int,
        rawCd: Boolean
    ): ByteArray? {
        // Self-referenced and uncompressed hunks are not what a disc's sector 16
        // is stored as on any file measured, and guessing at them would mean
        // reading bytes whose meaning has not been checked.
        if (entry.type !in TYPE_BASE_0..3) return null
        val codec = compressors.getOrNull(entry.type) ?: return null
        if (entry.length <= 0 || entry.length > MAX_HUNK_BYTES) return null
        val raw = ByteArray(entry.length)
        if (source.read(entry.offset, raw, entry.length) < entry.length) return null

        val frames = hunkBytes / CD_FRAME_BYTES
        return when (codec) {
            "cdlz", "cdzl" -> {
                if (!rawCd || frames <= 0) return null
                // The CD codecs put a header first: one ECC bit per frame,
                // rounded up to bytes, then the compressed length of the sector
                // block. The subcode block follows it and is of no use here.
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
            // `cdfl` is FLAC, which only ever holds CD audio, and `huff` is
            // CHD's own Huffman codec. Neither has been seen on a data sector,
            // and neither is worth decoding blind.
            else -> null
        }
    }

    /** A hunk is bounded by the format itself; this guards a corrupt length. */
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

    /**
     * Raw LZMA, with the properties CHD leaves implicit.
     *
     * The stream carries no header: MAME compresses with `lc=3, lp=0, pb=2`,
     * which packs into the single properties byte 0x5D, and a dictionary
     * normalised up to the next power of two. Verified against the real PS2
     * file, where the sector block decodes to exactly 18816 bytes.
     */
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

    /** MSB-first bit reader; the format's streams are all big-endian. */
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

    /**
     * MAME's canonical Huffman, with its run-length coded tree.
     *
     * Two details here are not guessable and were taken from `huffman.cpp`
     * rather than reconstructed: the repeat count comes from a *third* read of
     * the stream, and what gets repeated is the length just read, not zero.
     * Getting either wrong still produces a tree, just not one whose code
     * lengths sum to 1 — which is why [importTreeRle] checks exactly that
     * before returning, and refuses the file otherwise.
     */
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
