package eu.emufii.app.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import eu.emufii.app.library.psp.PspUmdReader
import eu.emufii.app.library.switchfs.SwitchKeys
import eu.emufii.app.library.switchfs.SwitchReader

private const val TAG = "RomsRepository"
private const val PREFS = "emufii_library"
private const val KEY_FOLDER_URI = "roms_folder_uri"

/**
 * How deep to walk. People file ROMs as `Roms/3DS/Jeux/…`, occasionally a level
 * or two more; past this we're almost certainly somewhere we shouldn't be, and
 * every extra level costs a query per directory.
 */
private const val MAX_DEPTH = 6

/** Guards against a folder pick that lands on something enormous. */
private const val MAX_FILES = 5000

/** What every Switch emulator calls the key file, and where players already keep it. */
private const val PROD_KEYS = "prod.keys"

/**
 * The containers the PSP shares with other consoles. A file carrying one of
 * these extensions only enters the library once recognised as a PSP game; see
 * `toPspRom`.
 */
private val AMBIGUOUS_PSP = setOf("iso", "chd")

class RomsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val headerReader = RomHeaderReader(context)
    private val discImages = DiscImageReader(context)
    private val smdhReader = SmdhReader(context)
    private val romNames = RomNames(context)
    private val hiddenRoms = HiddenRoms(context)
    private val ndsReader = NdsBannerReader(context)
    private val switchReader = SwitchReader(context)
    private val pspReader = PspUmdReader(context)

    /**
     * The player's own console keys, found during the scan, kept only in memory.
     *
     * A Switch dump says nothing about itself without them, no icon, no title.
     * Emufii never ships keys and never fetches any: it picks up a `prod.keys`
     * that the player has already put in their own ROMs folder, which is where
     * every Switch emulator asks them to put it anyway. Absent, Switch tiles
     * keep their initials, exactly like an unrecognised file.
     */
    private var switchKeys: SwitchKeys? = null

    /** Keys the player pointed Emufii at from the settings, when the folder has none. */
    private val keysStore = ConsoleKeysStore(context)
    private val iconCache = IconCache(context)

    fun savedFolderUri(): Uri? = prefs.getString(KEY_FOLDER_URI, null)?.let(Uri::parse)

    /**
     * Something the user can recognise, not the raw tree URI. SAF hands us a
     * document id shaped like `primary:Roms/3DS`; the volume prefix means nothing
     * to anyone outside the framework, so only what follows it is shown. Falls
     * back to the last URI segment for providers with an unfamiliar id format.
     */
    fun savedFolderLabel(): String? {
        val uri = savedFolderUri() ?: return null
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        return docId?.substringAfter(':')?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
    }

    fun setFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        prefs.edit().putString(KEY_FOLDER_URI, uri.toString()).apply()
        cachedRoms = null
    }

    fun clear() {
        savedFolderUri()?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        prefs.edit().remove(KEY_FOLDER_URI).apply()
    }

    /**
     * Last scan's result, so callers that only need to look something up don't
     * walk the whole tree again. Joining from the session finder did exactly
     * that, a second full walk just to match one title id.
     *
     * Deliberately shared across instances rather than held per repository. A
     * repository is remembered per composition, so rotating the device made a
     * new one and rescanned from scratch, with a 2 GB 3DS ROM in the folder
     * that took long enough to ANR, and several rotations queued several scans
     * at once. The cache belongs to the process, not to the screen.
     */
    private companion object {
        @Volatile
        var cachedRoms: List<Rom>? = null
        val scanLock = Any()
    }

    fun cachedOrScan(): List<Rom> = cachedRoms?.let(::named) ?: scan()

    /**
     * [force] is for the explicit Rescan action, which has to look at the disc
     * again even when a perfectly good cache exists.
     */
    fun scan(force: Boolean = false): List<Rom> = synchronized(scanLock) {
        // A library scanned in French is stale the moment the app is switched to
        // English: every title in it is the wrong string. Changing the language
        // recreates the activity but not this process-level cache, so the check
        // belongs here rather than at the call sites.
        TitleLanguage.apply(context)
        val staleLanguage = scannedLanguage != null && scannedLanguage != TitleLanguage.tag
        // Another thread may have finished while we waited on the lock.
        if (!force && !staleLanguage) cachedRoms?.let { return named(it) }
        return doScan()
    }

    /** The language the cached list was read in. Null until the first scan. */
    private var scannedLanguage: String? = null

    private fun doScan(): List<Rom> {
        // Titles come out of the cartridges in whatever language is asked for,
        // so the app's own language has to be settled before a single one is
        // read, and re-read each scan, because changing it is what triggers one.
        TitleLanguage.apply(context)
        scannedLanguage = TitleLanguage.tag
        val folderUri = savedFolderUri() ?: return emptyList()
        val found = runCatching { walk(folderUri) }
            .onFailure { Log.w(TAG, "scan failed", it) }
            .getOrDefault(emptyList())

        Log.i(TAG, "walked ${found.size} candidate file(s), titles in ${TitleLanguage.tag}")

        return found
            .mapNotNull { it.toRom() }
            .also { cachedRoms = it }
            .let(::named)
    }

    /**
     * The player's chosen names, laid over the scanned list on the way out.
     *
     * Deliberately *not* baked into the cache. Renaming a game does not rescan
     * anything — it changes one preference — so a cache holding the renamed
     * titles was a cache nothing invalidated: the rename only appeared at the
     * next cold start, which reads as "renaming does nothing". Keeping the
     * cache at the titles read off the files, and applying the chosen ones on
     * every read, also makes clearing a name give the original title straight
     * back, where before it left the custom one in place until a rescan.
     *
     * The sort belongs here for the same reason: a renamed game has to move to
     * its new place in the alphabet, not stay where its old title put it.
     */
    private fun named(roms: List<Rom>): List<Rom> =
        roms.filterNot(hiddenRoms::isHidden)
            .map(romNames::apply)
            .sortedWith(compareBy({ it.console.ordinal }, { it.displayName.lowercase() }))

    private data class Candidate(
        val uri: Uri,
        val name: String,
        val console: Console,
        val addedAt: Long
    )

    /**
     * Walks the picked tree, subfolders included, people keep their ROMs
     * sorted into `3DS/`, `GameCube/`, `Jeux/`, and a flat scan found nothing.
     *
     * Queries [DocumentsContract] directly rather than using `DocumentFile`:
     * the latter issues a query per entry to answer `isFile`/`name`, which on a
     * library of a few thousand files means a few thousand round trips. Here
     * one query per directory returns everything needed.
     *
     * Breadth-first, so the shallow well-named folders are visited before the
     * deep ones, it matters when [MAX_FILES] cuts the walk short.
     */
    private fun walk(treeUri: Uri): List<Candidate> {
        val resolver = context.contentResolver
        val out = mutableListOf<Candidate>()
        var keysUri: Uri? = null
        val queue = ArrayDeque<Pair<String, Int>>()
        val seen = mutableSetOf<String>()

        queue += DocumentsContract.getTreeDocumentId(treeUri) to 0

        while (queue.isNotEmpty() && out.size < MAX_FILES) {
            val (parentId, depth) = queue.removeFirst()
            if (!seen.add(parentId)) continue

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                // The "recently added" sort has no other source: a document
                // provider exposes no creation date. Asked for in the same query
                // as the rest, it costs nothing, where fetching it afterwards
                // would be one round trip per file.
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            // A folder we can't read shouldn't abort the whole scan, some
            // providers throw on entries they've since lost access to.
            val cursor = runCatching { resolver.query(childrenUri, projection, null, null, null) }
                .onFailure { Log.w(TAG, "cannot list $parentId", it) }
                .getOrNull() ?: continue

            cursor.use {
                while (it.moveToNext() && out.size < MAX_FILES) {
                    val docId = it.getString(0) ?: continue
                    val name = it.getString(1) ?: continue
                    val mime = it.getString(2)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        // Skip dot-folders: emulator caches, save states and
                        // `.git` checkouts hold nothing playable and can be big.
                        if (depth + 1 <= MAX_DEPTH && !name.startsWith(".")) {
                            queue += docId to depth + 1
                        }
                        continue
                    }

                    if (name.equals(PROD_KEYS, ignoreCase = true)) {
                        keysUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        continue
                    }

                    val ext = name.substringAfterLast('.', "")
                    val byName = Console.forExtension(ext) ?: continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    // `.iso` belongs to the PSP by extension and to the
                    // GameCube by content, and only the bytes can tell which.
                    // The read is one header and it can only ever *move* a file
                    // onto Dolphin: when it says nothing, a PSP rip, an
                    // unreadable provider, a format we chose not to guess at,
                    // the extension's answer stands, so no library that worked
                    // yesterday changes today. See [DiscImage].
                    val console = if (ext.lowercase() in DiscImage.SNIFFED_EXTENSIONS) {
                        discImages.identify(uri) ?: byName
                    } else {
                        byName
                    }
                    out += Candidate(
                        uri = uri,
                        name = name,
                        console = console,
                        // Null on some providers, hence the guarded read:
                        // `getLong` on a null column returns 0, but only if it
                        // does not throw first.
                        addedAt = if (it.isNull(3)) 0L else it.getLong(3)
                    )
                }
            }
        }

        // The folder wins when it has one, that copy is the one the emulators
        // are using, so it is the one the player maintains, and the file
        // imported from the settings covers everyone whose keys live elsewhere.
        switchKeys = keysUri?.let { uri ->
            runCatching {
                resolver.openInputStream(uri)?.use { SwitchKeys.parse(it.reader().readText()) }
            }.onFailure { Log.w(TAG, "prod.keys unreadable", it) }.getOrNull()
        }?.takeIf { it.isUsable } ?: keysStore.load()
        if (switchKeys?.isUsable == true) Log.i(TAG, "console keys available — Switch icons enabled")

        if (out.size >= MAX_FILES) {
            Log.w(TAG, "stopped at $MAX_FILES files — library larger than expected")
        }
        return out
    }

    /**
     * 3DS and DS files get opened, because both carry their real title and icon
     * inside, an SMDH for the 3DS, a banner for the DS, and both are cheap to
     * reach. Disc images (GameCube, Wii) still take their *title* from the
     * filename: `.rvz` is compressed, so no banner sits at a fixed offset. Their
     * *identity* is another matter, an uncompressed image opens with its six
     * character game id, and that is what the session guard compares.
     */
    private fun Candidate.toRom(): Rom? = readRom()?.copy(addedAt = addedAt)

    /**
     * The title read out of the file, which is what gets cached.
     *
     * The name the player chose is laid over this later, in [named], and never
     * here: every console has its own reading path, and applying it per path
     * would have given a library where renaming works for the 3DS and not for
     * the DS. One place, on the way out, for all of them.
     */
    private fun Candidate.readRom(): Rom? {
        if (console == Console.DS) return toDsRom()
        if (console == Console.SWITCH) return toSwitchRom()

        if (console == Console.PSP) return toPspRom()

        // The PS2 takes the same path as the Nintendo discs, and for the same
        // reason: the title comes from the filename, but the number is read out
        // of the disc. `SLES-50877` on TimeSplitters 2, exactly what ARMSX2
        // displays, so a guest can recognise the host's game as their own
        // emulator names it to them.
        if (console == Console.GAMECUBE || console == Console.WII || console == Console.PS2) {
            return toDiscRom()
        }

        // What is left is what no path can serve: we do not list it. A grid
        // whose only function is to open sessions has no business showing games
        // it cannot put into a game.
        if (console != Console.THREE_DS) return null

        val header = headerReader.read(uri)
        val smdh = header?.let { readSmdhWithCache(uri, it) }
        val iconFile = header?.let { h -> iconCache.fileFor(h.titleIdHex).takeIf { it.exists() } }

        return Rom(
            uri = uri,
            filename = name,
            displayName = smdh?.title ?: displayNameFromFilename(name),
            console = console,
            titleIdHex = header?.titleIdHex,
            productCode = header?.productCode,
            iconFile = iconFile,
            accentArgb = header?.let { iconCache.readAccent(it.titleIdHex) }
        )
    }

    /**
     * The PSP, with the same cache discipline as the DS and the Switch.
     *
     * A UMD carries its icon and its title inside its filesystem, under
     * `PSP_GAME`: a few kilobytes to read on a disc weighing a million of them.
     * The title comes from the `PARAM.SFO` and is markedly better than the
     * filename, which usually drags its region and its revision along in
     * brackets.
     *
     * The disc id serves as the cache key, but not as the session identity: it
     * goes into `productCode`, as for the DS, and not into `titleIdHex`, which
     * decides whether two players really have the same game. Two regional dumps
     * of the same title carry two ids, and nothing yet says they refuse to play
     * together; that would be forbidding a game on a supposition.
     */
    private fun Candidate.toPspRom(): Rom? {
        val fallback = Rom(
            uri = uri,
            filename = name,
            displayName = displayNameFromFilename(name),
            console = console
        )

        val cachedKey = ndsKeyCache[uri.toString()]
        if (cachedKey != null) {
            val icon = iconCache.fileFor(cachedKey).takeIf { it.exists() }
            val title = iconCache.readTitle(cachedKey)
            if (icon != null && title != null) {
                return fallback.copy(
                    displayName = title,
                    iconFile = icon,
                    productCode = cachedKey,
                    accentArgb = iconCache.readAccent(cachedKey)
                )
            }
        }

        val data = pspReader.read(uri)
        // `.iso` and `.chd` say nothing about the console that burned them: the
        // PS2, the Xbox and a pile of arcade systems carry them too, and those
        // games ended up in the grid under their filename when Emufii can do
        // nothing with them. For these two containers the file has to prove it is
        // a PSP game, a `PSP_GAME` in its table of contents, failing which it is
        // not listed. `.pbp` and `.cso` stay admitted on their extension alone:
        // they belong to the PSP and nothing else.
        val ambiguous = name.substringAfterLast('.', "").lowercase() in AMBIGUOUS_PSP
        if (ambiguous && !data.recognised) return null
        // A homebrew can have no disc id at all while still having an icon; with
        // no key it would have nowhere to be filed, so the filename stands in,
        // stable from one scan to the next, which is all a cache key is asked
        // for.
        val key = data.cacheKey ?: "PSP-F%08x".format(name.lowercase().hashCode())
        if (data.icon == null && data.title == null) return fallback
        ndsKeyCache[uri.toString()] = key

        data.icon?.let { bitmap ->
            iconCache.writeIcon(key, bitmap)
            IconAccent.fromBitmap(bitmap)?.let { iconCache.writeAccent(key, it) }
        }
        data.title?.let { iconCache.writeTitle(key, it) }

        return fallback.copy(
            displayName = data.title ?: fallback.displayName,
            iconFile = iconCache.fileFor(key).takeIf { it.exists() },
            productCode = key,
            accentArgb = iconCache.readAccent(key)
        )
    }

    /**
     * The DS path, cached the same way as the 3DS one: the icon lands in the
     * cache directory under the cartridge's game code, so a rescan doesn't
     * decode every banner again.
     */
    private fun Candidate.toDsRom(): Rom {
        val fallback = Rom(
            uri = uri,
            filename = name,
            displayName = displayNameFromFilename(name),
            console = console
        )

        val key = ndsKeyCache[uri.toString()]
        if (key != null) {
            val icon = iconCache.fileFor(key).takeIf { it.exists() }
            val title = iconCache.readTitle(key)
            if (icon != null && title != null) {
                return fallback.copy(
                    displayName = title,
                    iconFile = icon,
                    productCode = key,
                    accentArgb = iconCache.readAccent(key)
                )
            }
        }

        val data = ndsReader.read(uri)
        val cacheKey = data.cacheKey ?: return fallback
        ndsKeyCache[uri.toString()] = cacheKey

        data.icon?.let { bitmap ->
            iconCache.writeIcon(cacheKey, bitmap)
            IconAccent.fromBitmap(bitmap)?.let { iconCache.writeAccent(cacheKey, it) }
        }
        data.title?.let { iconCache.writeTitle(cacheKey, it) }

        return fallback.copy(
            displayName = data.title ?: fallback.displayName,
            iconFile = iconCache.fileFor(cacheKey).takeIf { it.exists() },
            productCode = cacheKey,
            accentArgb = iconCache.readAccent(cacheKey)
        )
    }

    /**
     * The Switch path. Same cache discipline as the DS one, and the same
     * graceful nothing when the file won't talk, but here "won't talk" is the
     * common case, because it means the player has no keys.
     */
    /**
     * A GameCube or Wii disc image.
     *
     * The title comes from the filename, and that is accepted: an `.rvz` is
     * compressed, so no banner can be read at a fixed offset, and a whole game
     * would have to be decompressed to go and fetch its `opening.bnr`. What the
     * file gives for almost nothing is its disc id, six characters at the front
     * of the header, and that is what matters here: without it the session
     * publishes nothing, and the guest is told they do not have the game they
     * have right in front of them. That is exactly the flaw fixed for the PSP in
     * versionCode 12.
     *
     * Filed under `productCode` and not `titleIdHex`, like the PSP and the DS:
     * `sessionId` can find a game by either, but the wrong-game safeguard only
     * refuses on a *title* id, and two regional dumps of the same game carry two
     * disc ids while playing together perfectly well.
     */
    private fun Candidate.toDiscRom(): Rom {
        val fallback = Rom(
            uri = uri,
            filename = name,
            displayName = displayNameFromFilename(name),
            console = console
        )
        val (detected, gameId) = discImages.read(uri) ?: return fallback
        // The console read back wins: it is the same read that served the scan,
        // and it can tell a GameCube RVZ from a Wii RVZ where the extension
        // cannot.
        return fallback.copy(console = detected, productCode = gameId)
    }

    private fun Candidate.toSwitchRom(): Rom {
        val fallback = Rom(
            uri = uri,
            filename = name,
            displayName = displayNameFromFilename(name),
            console = console
        )

        val cachedId = ndsKeyCache[uri.toString()]
        if (cachedId != null) {
            val icon = iconCache.fileFor(cachedId).takeIf { it.exists() }
            val title = iconCache.readTitle(cachedId)
            if (icon != null && title != null) {
                return fallback.copy(
                    displayName = title,
                    iconFile = icon,
                    titleIdHex = cachedId,
                    accentArgb = iconCache.readAccent(cachedId)
                )
            }
        }

        val data = switchReader.read(uri, switchKeys)
        val key = data.cacheKey ?: return fallback
        ndsKeyCache[uri.toString()] = key

        data.icon?.let { bitmap ->
            iconCache.writeIcon(key, bitmap)
            IconAccent.fromBitmap(bitmap)?.let { iconCache.writeAccent(key, it) }
        }
        data.title?.let { iconCache.writeTitle(key, it) }

        return fallback.copy(
            displayName = data.title ?: fallback.displayName,
            iconFile = iconCache.fileFor(key).takeIf { it.exists() },
            titleIdHex = key,
            accentArgb = iconCache.readAccent(key)
        )
    }

    /**
     * Which cache key a given file resolved to last time. Avoids re-reading a
     * header just to learn where its icon was filed.
     */
    private val ndsKeyCache = HashMap<String, String>()

    private fun readSmdhWithCache(uri: Uri, header: RomHeader): SmdhData {
        val cachedIcon = iconCache.fileFor(header.titleIdHex)
        val cachedTitle = iconCache.readTitle(header.titleIdHex)
        if (cachedIcon.exists() && cachedTitle != null) {
            return SmdhData(icon = null, title = cachedTitle)
        }
        val fresh = smdhReader.read(uri, header)
        if (fresh.icon != null) {
            iconCache.writeIcon(header.titleIdHex, fresh.icon)
            IconAccent.fromBitmap(fresh.icon)?.let { iconCache.writeAccent(header.titleIdHex, it) }
        }
        if (fresh.title != null) iconCache.writeTitle(header.titleIdHex, fresh.title)
        return fresh
    }
}
