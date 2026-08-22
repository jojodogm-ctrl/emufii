package eu.emufii.app.library

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.FileInputStream

/**
 * Telling a GameCube or Wii disc from everything else that ends in `.iso`.
 *
 * The PSP got `.iso` first, and it is not giving it back: a UMD rip and a
 * GameCube image share the extension and nothing else. Sorting them by name
 * would be a coin toss, so this reads the first bytes instead, and reads them
 * only to promote a file. Anything this cannot positively identify stays
 * whatever [Console.forExtension] already said it was, which is why adding
 * Dolphin cannot take a single game away from PPSSPP.
 *
 * Every offset below was measured on real files sitting on this machine, not
 * copied out of a wiki:
 *
 * - a PSP rip has plain zeroes at 0x18 and 0x1C, where the two console magics
 *   live, so there is no false positive to worry about (checked on three);
 * - an RVZ carries its disc type at 0x48 and a verbatim copy of the disc header
 *   at 0x58, which is why the Wii magic turns up at 0x70 (checked on four).
 */
object DiscImage {

    /** How many bytes [identify] needs. Cheap: one read, no decompression. */
    const val HEADER_BYTES = 0x80

    /** `WII` in the disc header, big-endian, at [WII_MAGIC_OFFSET]. */
    private const val WII_MAGIC = 0x5D1C9EA3
    private const val WII_MAGIC_OFFSET = 0x18

    /** The GameCube's, four bytes further in. */
    private const val GC_MAGIC = 0xC2339F3D.toInt()
    private const val GC_MAGIC_OFFSET = 0x1C

    /**
     * Where a compressed image keeps its uncompressed copy of the disc header.
     *
     * RVZ and WIA share a container: a file header, then a `WIADisc` whose
     * `disc_type` says which console it came off, then the first 0x80 bytes of
     * the original disc verbatim. Both are readable without touching the
     * compressed payload.
     */
    private const val WIA_DISC_TYPE_OFFSET = 0x48
    private const val WIA_DISC_HEADER_OFFSET = 0x58
    private const val WIA_TYPE_GAMECUBE = 1
    private const val WIA_TYPE_WII = 2

    /**
     * Which console this image belongs to, or null when the bytes don't say.
     *
     * Null is the ordinary answer for a PSP rip, and it is also the answer for a
     * truncated read or a format not listed here, all three mean the same
     * thing to the caller: leave the file where the extension put it.
     */
    /**
     * The ISO9660 volume descriptor, and the system identifier in it that names
     * the console.
     *
     * A PS2 disc and a UMD rip are both `.iso`, and on the Thor they look alike
     * right down to the filename. But the disc says so itself at byte `0x8008`,
     * and this is measured on the real files on this machine:
     *
     * ```
     * TimeSplitters 2 (PS2): system id 'PLAYSTATION'  volume id 'SLES_50877'
     * WipEout Pulse  (PSP) : system id 'PSP GAME'     volume id 'SCEE'
     * ```
     *
     * A CD/DVD's first data sector starts at `0x8000` (sector 16 of 2048 bytes),
     * the type and the `CD001` signature first, then the system identifier over
     * 32 bytes.
     */
    private const val PVD_OFFSET = 0x8000
    private const val PVD_MAGIC_OFFSET = PVD_OFFSET + 1
    private const val PVD_SYSTEM_ID_OFFSET = PVD_OFFSET + 8
    private const val PVD_VOLUME_ID_OFFSET = PVD_OFFSET + 40
    private const val PVD_ID_LENGTH = 32

    /** What a PlayStation disc writes into its system identifier. */
    private const val PS_SYSTEM_ID = "PLAYSTATION"

    /** How much has to be read before an `.iso` can be asked the question. */
    const val PVD_BYTES = PVD_VOLUME_ID_OFFSET + PVD_ID_LENGTH

    fun identify(head: ByteArray): Console? {
        if (head.size >= 4) {
            // Three characters, not four: the fourth byte of an RVZ or WIA
            // magic is a format version, 0x01 on every file measured here, so
            // comparing four would stop matching the day upstream bumps it.
            val tag = String(head, 0, 3, Charsets.ISO_8859_1)
            if (tag == "RVZ" || tag == "WIA") return compressed(head)
            // WBFS only ever held Wii discs: the format was written for them.
            if (String(head, 0, 4, Charsets.ISO_8859_1) == "WBFS") return Console.WII
        }
        // The two GameCube/Wii magics first: they are in the very first bytes,
        // and a Nintendo disc is not ISO9660, so the two tests cannot fight over
        // a file.
        return raw(head, 0) ?: playstation(head)
    }

    /**
     * Promotion to the PS2, and only when the disc says so.
     *
     * An accepted limitation, not one to discover as a fault: a PS1 disc carries
     * the same `PLAYSTATION` at this offset. Telling them apart would mean
     * opening `SYSTEM.CNF` in the ISO9660 tree (`BOOT2` against `BOOT`), that is,
     * walking a directory for a console Emufii does not serve. In the meantime a
     * PS1 `.iso` will display as "PS2", which is no worse than the current state,
     * where it displays as "PSP", and it gets fixed the day the PS1 arrives.
     */
    private fun playstation(head: ByteArray): Console? = playstationAt(head, PVD_OFFSET)

    /**
     * The same rule, applied wherever the descriptor happens to start.
     *
     * On an `.iso` it starts at `0x8000`; on a sector lifted out of a CHD it
     * starts at 0, 16 or 24 depending on how the disc was pressed. One rule, one
     * place, so a PS2 recognised through a compressed container is recognised on
     * exactly the same evidence as one read off a plain file.
     */
    private fun playstationAt(bytes: ByteArray, base: Int): Console? {
        if (base + PVD_ID_LENGTH * 2 + 8 > bytes.size) return null
        if (String(bytes, base + 1, 5, Charsets.ISO_8859_1) != "CD001") return null
        val system = ascii(bytes, base + 8)
        return if (system.equals(PS_SYSTEM_ID, ignoreCase = true)) Console.PS2 else null
    }

    /**
     * Where the volume descriptor sits inside one raw disc sector.
     *
     * A 2048-byte sector is user data already. A raw CD sector carries 16 bytes
     * of sync and header before it (MODE1) or 24 (MODE2 FORM1), and the PS2
     * pressing measured here is MODE2, so both have to be tried. Measured on the
     * real file: `CD001` lands at 24, and the descriptor then reads
     * `system id 'PLAYSTATION'`.
     */
    private val SECTOR_USER_DATA_OFFSETS = intArrayOf(0, 16, 24)

    /**
     * Console and game id from a single disc sector, or null when it says
     * nothing.
     *
     * This is the entry point for containers that have to be decompressed
     * before anything can be read, [ChdImage] today. It deliberately answers
     * only for a PlayStation disc: the Nintendo magics live in the first bytes
     * of a file, not in a sector, and a GameCube image is not ISO9660 at all.
     */
    fun fromSector(sector: ByteArray): Pair<Console, String?>? {
        for (base in SECTOR_USER_DATA_OFFSETS) {
            val console = playstationAt(sector, base) ?: continue
            return console to volumeId(sector, base + 40)
        }
        return null
    }

    /**
     * The PS2 disc's real serial, read from `SYSTEM.CNF`.
     *
     * The volume identifier was used for this and it was the wrong field.
     * Measured on the eight PS2 discs of the bench, **two carried a serial**:
     * `SLES_50877` and `SCED_53990`. The rest said `MC3REMIX`, `FINAL_FANTASY_X`,
     * `1_01`, or nothing at all — publishers write what they like there, and
     * nothing obliges them to write the disc's number.
     *
     * The serial that every PS2 tool actually uses is the boot file named in
     * `SYSTEM.CNF` at the root of the disc:
     *
     *     BOOT2 = cdrom0:\SLES_537.17;1
     *
     * That is what PCSX2 keys its own database on, so it is also the only thing
     * that can match ours. Reaching it means walking ISO9660 — read the primary
     * descriptor, follow its root directory record, find the file — which is a
     * few hundred bytes of reading and the reason this takes a random-access
     * [reader] rather than the prefix everything else here works from.
     *
     * Answers null on anything it cannot follow, and the caller then keeps the
     * volume identifier: a disc that used to be identified badly must not become
     * a disc that is not identified at all.
     */
    fun ps2Serial(reader: (Long, ByteArray) -> Int): String? = runCatching {
        val pvd = ByteArray(SECTOR)
        if (reader(PVD_OFFSET.toLong(), pvd) < SECTOR) return null
        if (String(pvd, 1, 5, Charsets.ISO_8859_1) != "CD001") return null

        // The root directory's own record is embedded in the descriptor, at a
        // fixed offset and a fixed 34 bytes long.
        val rootLba = leInt(pvd, ROOT_RECORD_OFFSET + 2)
        val rootSize = leInt(pvd, ROOT_RECORD_OFFSET + 10)
        if (rootLba <= 0 || rootSize <= 0 || rootSize > MAX_ROOT_BYTES) return null

        val dir = ByteArray(rootSize)
        if (reader(rootLba.toLong() * SECTOR, dir) < rootSize) return null

        var at = 0
        while (at < dir.size) {
            val length = dir[at].toInt() and 0xFF
            if (length == 0) {
                // A directory record never straddles a sector: the remainder of
                // this one is padding, and the next record starts at the top of
                // the following sector.
                at = (at / SECTOR + 1) * SECTOR
                continue
            }
            if (at + length > dir.size) break
            val nameLength = dir[at + 32].toInt() and 0xFF
            val name = String(dir, at + 33, nameLength, Charsets.ISO_8859_1)
            // `;1` is the ISO9660 version suffix, always present on a file.
            if (name.substringBefore(';').equals("SYSTEM.CNF", ignoreCase = true)) {
                val lba = leInt(dir, at + 2)
                val size = leInt(dir, at + 10).coerceAtMost(MAX_CNF_BYTES)
                if (lba <= 0 || size <= 0) return null
                val cnf = ByteArray(size)
                if (reader(lba.toLong() * SECTOR, cnf) < size) return null
                return bootSerial(String(cnf, Charsets.ISO_8859_1))
            }
            at += length
        }
        null
    }.getOrNull()

    /**
     * `cdrom0:\SLES_537.17;1` reduced to `SLES-53717`.
     *
     * The dot inside the number is a filename convention, not part of the
     * serial, and the underscore is how a serial is spelled on a filesystem that
     * has no hyphen. Both are undone here so the result is the serial as it is
     * written everywhere else — on the box, in PCSX2's index, in our database.
     */
    fun bootSerial(cnf: String): String? {
        val line = cnf.lineSequence().firstOrNull { it.trimStart().startsWith("BOOT2", true) }
            ?: return null
        val path = line.substringAfter('=', "").trim()
        val file = path.substringAfterLast('\\').substringAfterLast('/')
            .substringAfterLast(':').substringBefore(';')
        val serial = file.replace(".", "").replace('_', '-').uppercase().trim()
        // Shaped like a serial or nothing: a homebrew boots from an ELF with any
        // name at all, and returning that would file it under a key that means
        // nothing.
        return serial.takeIf { it.matches(SERIAL_SHAPE) }
    }

    private fun leInt(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)

    private const val SECTOR = 2048
    /**
     * Where the root directory's record sits *inside* the descriptor.
     *
     * Relative, not absolute: the descriptor has already been read into a buffer
     * of its own by then. It was `PVD_OFFSET + 156` at first, which indexed 32 kB
     * into a 2 kB array — caught by the walk test, which would otherwise have
     * been the badge silently never appearing on a PS2 game.
     */
    private const val ROOT_RECORD_OFFSET = 156
    /** A root directory bigger than this is not a disc we are reading right. */
    private const val MAX_ROOT_BYTES = 1 shl 20
    private const val MAX_CNF_BYTES = 4096
    private val SERIAL_SHAPE = Regex("^[A-Z]{4}-\\d{5}$")

    /** The disc's own number, as the volume identifier spells it. */
    private fun volumeId(bytes: ByteArray, at: Int): String? {
        if (at + PVD_ID_LENGTH > bytes.size) return null
        return ascii(bytes, at)
            .replace('_', '-')
            .takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '-' } }
    }

    private fun ascii(head: ByteArray, at: Int): String =
        String(head, at, PVD_ID_LENGTH, Charsets.ISO_8859_1).trim { it <= ' ' }

    /** The two magics, read at [base] so the same code serves an embedded copy. */
    private fun raw(head: ByteArray, base: Int): Console? = when {
        beInt(head, base + WII_MAGIC_OFFSET) == WII_MAGIC -> Console.WII
        beInt(head, base + GC_MAGIC_OFFSET) == GC_MAGIC -> Console.GAMECUBE
        else -> null
    }

    /**
     * RVZ and WIA, which state their console outright.
     *
     * The embedded header is checked as well, and it is not redundant: it is
     * what proves the file is what its `disc_type` claims, and it is the answer
     * on a container whose type field is one this build has never seen.
     */
    private fun compressed(head: ByteArray): Console? =
        when (beInt(head, WIA_DISC_TYPE_OFFSET)) {
            WIA_TYPE_GAMECUBE -> Console.GAMECUBE
            WIA_TYPE_WII -> Console.WII
            else -> raw(head, WIA_DISC_HEADER_OFFSET)
        }

    /**
     * The six-character game id stamped at the very start of a disc header.
     *
     * `RMGP01`, `GALE01`, the identity Dolphin itself sorts games by, and the
     * only thing here that lets a guest recognise the host's game as one they
     * own. A disc image has no SMDH and no banner at a fixed offset (an RVZ is
     * compressed), so the *title* still comes from the filename; this is the
     * part that does not depend on how someone named their file.
     *
     * Read at the same base the console was: 0 on a raw image, 0x58 inside a
     * compressed container.
     */
    fun gameId(head: ByteArray): String? {
        // The PS2 puts its number where the disc files it, not at the start of
        // the file: the volume identifier. Measured `SLES_50877` on
        // TimeSplitters 2, where ARMSX2 displays `SLES-50877`; it is the same
        // number bar the separator, so the guest will recognise the host's game as
        // their own emulator names it to them.
        if (playstation(head) == Console.PS2) return volumeId(head, PVD_VOLUME_ID_OFFSET)
        val base = if (head.size >= 3 &&
            String(head, 0, 3, Charsets.ISO_8859_1).let { it == "RVZ" || it == "WIA" }
        ) WIA_DISC_HEADER_OFFSET else 0
        if (base + 6 > head.size) return null
        val id = String(head, base, 6, Charsets.ISO_8859_1)
        // Letters and digits only: anything else means we are looking at
        // compressed bytes rather than a disc header, and a garbage id would be
        // published to the session as if it meant something.
        return id.takeIf { it.all { c -> c.isLetterOrDigit() } }
    }

    private fun beInt(bytes: ByteArray, at: Int): Int? {
        if (at < 0 || at + 4 > bytes.size) return null
        return (bytes[at].toInt() and 0xFF shl 24) or
            (bytes[at + 1].toInt() and 0xFF shl 16) or
            (bytes[at + 2].toInt() and 0xFF shl 8) or
            (bytes[at + 3].toInt() and 0xFF)
    }

    /**
     * Extensions worth opening to ask the question.
     *
     * `.iso` because the PSP owns it and only the bytes can settle it. The
     * Dolphin-only ones are here too, because they still have to say *which* of
     * the two consoles they are.
     *
     * `.chd` is here since 2026-08-20, and it is the one that needed real work:
     * the PSP, the PS2 and the Dreamcast all ship in it, and the bytes that
     * answer the question are compressed. [ChdImage] decodes just enough of the
     * container to hand back one sector, which then goes through the same
     * descriptor rule as everything else. A Dreamcast disc is refused before any
     * of that, on its GD-ROM metadata tag.
     *
     * Deliberately absent: `.gcz`. It says GameCube or Wii in a sub-type field
     * this project has no sample to check, and guessing would risk moving
     * somebody's game to an emulator that cannot open it, which is worse than
     * not listing a format.
     */
    val SNIFFED_EXTENSIONS = setOf("iso", "gcm", "rvz", "wia", "wbfs", "chd")
}

/**
 * Opens a candidate far enough to ask [DiscImage.identify] the question.
 *
 * One read of [DiscImage.HEADER_BYTES] bytes per ambiguous file, during a scan
 * that already opens every 3DS and DS file for its icon. A provider that
 * refuses the read answers null, which the scan reads as "keep the extension's
 * guess", the same as a PSP rip, and the reason a failure here is invisible
 * rather than destructive.
 */
class DiscImageReader(private val context: Context) {

    /** Console and game id in one read, for the library's enrichment pass. */
    fun read(uri: Uri): Pair<Console, String?>? {
        val head = head(uri) ?: return null
        if (isChd(head)) return chdSector(uri)?.let { DiscImage.fromSector(it) }
        val console = DiscImage.identify(head) ?: return null
        // The PS2 asks the disc a second question, and the answer is worth the
        // extra read: the volume identifier it used to be given is not a serial
        // on most discs — six of the bench's eight said `MC3REMIX`,
        // `FINAL_FANTASY_X`, `1_01` or nothing. `SYSTEM.CNF` is where the serial
        // really lives. It falls back to the volume identifier rather than to
        // nothing, so a disc that used to be identified badly does not become a
        // disc that is not identified at all.
        if (console == Console.PS2) {
            ps2Serial(uri)?.let { return console to it }
        }
        return console to DiscImage.gameId(head)
    }

    /**
     * Walks the ISO for its boot file, over a channel.
     *
     * A channel and not the forward stream the rest of this class uses: the root
     * directory sits wherever the disc was mastered, several hundred megabytes
     * in on a dual-layer game, and reading up to it would mean reading the game.
     *
     * Only for a plain image. A CHD would have to decompress a hunk per seek,
     * and the sector it already reads for identification carries the volume
     * identifier, which stays the answer there.
     */
    private fun ps2Serial(uri: Uri): String? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                val channel = stream.channel
                DiscImage.ps2Serial { offset, into ->
                    channel.position(offset)
                    var done = 0
                    while (done < into.size) {
                        val n = stream.read(into, done, into.size - done)
                        if (n <= 0) break
                        done += n
                    }
                    done
                }
            }
        }
    }.onFailure { Log.w("DiscImage", "SYSTEM.CNF illisible $uri", it) }.getOrNull()

    fun identify(uri: Uri): Console? = read(uri)?.first

    /**
     * A CHD announces itself in its first eight bytes, so nothing here depends
     * on the file being *named* `.chd`.
     */
    private fun isChd(head: ByteArray): Boolean =
        head.size >= 8 && String(head, 0, 8, Charsets.ISO_8859_1) == "MComprHD"

    /**
     * One sector out of a CHD, which needs seeking rather than a prefix.
     *
     * The hunk map sits near the end of the file, so this is the one format that
     * cannot be answered by reading forwards; hence a file descriptor and a
     * channel rather than the plain stream used everywhere else. A provider that
     * will not give one answers null, and the file keeps the console its
     * extension gave it.
     */
    private fun chdSector(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                val channel = stream.channel
                ChdImage.readSector(object : ChdImage.Source {
                    override fun read(offset: Long, into: ByteArray, count: Int): Int {
                        channel.position(offset)
                        var done = 0
                        while (done < count) {
                            val n = stream.read(into, done, count - done)
                            if (n <= 0) break
                            done += n
                        }
                        return done
                    }
                })
            }
        }
    }.onFailure { Log.w("DiscImage", "CHD illisible $uri", it) }.getOrNull()

    /**
     * We read as far as the volume descriptor, not just the header.
     *
     * The GameCube and Wii magics fit inside the first 0x80 bytes; the PS2 can
     * only be recognised at `0x8000`, where ISO9660 begins. Hence a 32 KB read per
     * sniffed file, sequential, once, during the library's enrichment pass.
     *
     * The array returned is truncated to what was actually read. That is the
     * delicate point: a 32 KB array whose tail was unread zeroes would have
     * `identify()` examining bytes that do not come from the file. A file shorter
     * than [DiscImage.HEADER_BYTES] is not a disc image at all and returns
     * nothing.
     */
    private fun head(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(DiscImage.PVD_BYTES)
            var read = 0
            while (read < buffer.size) {
                val n = stream.read(buffer, read, buffer.size - read)
                if (n <= 0) break
                read += n
            }
            when {
                read < DiscImage.HEADER_BYTES -> null
                read < buffer.size -> buffer.copyOf(read)
                else -> buffer
            }
        }
    }.onFailure { Log.w("DiscImage", "cannot read $uri", it) }.getOrNull()
}
