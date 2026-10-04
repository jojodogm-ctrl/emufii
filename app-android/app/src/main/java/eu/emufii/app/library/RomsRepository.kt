package eu.emufii.app.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import eu.emufii.app.library.psp.PspUmdReader
import eu.emufii.app.library.switchfs.SwitchReader
import androidx.core.content.edit

private const val TAG = "RomsRepository"
private const val PREFS = "emufii_library"
private const val KEY_FOLDER_URI = "roms_folder_uri"

/** Separate key: migrating [KEY_FOLDER_URI] would empty older builds' libraries. */
private const val KEY_FOLDER_URI_2 = "roms_folder_uri_2"

private const val MAX_DEPTH = 6

private const val MAX_FILES = 5000

class RomsRepository private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val headerReader = RomHeaderReader(context)
    private val discImages = DiscImageReader(context)
    private val smdhReader = SmdhReader(context)
    private val romNames = RomNames(context)
    private val hiddenRoms = HiddenRoms(context)
    private val ndsReader = NdsBannerReader(context)
    private val switchReader = SwitchReader(context)
    private val pspReader = PspUmdReader(context)

    private val iconCache = IconCache(context)

    fun savedFolderUri(): Uri? = prefs.getString(KEY_FOLDER_URI, null)?.let(Uri::parse)

    fun secondFolderUri(): Uri? = prefs.getString(KEY_FOLDER_URI_2, null)?.let(Uri::parse)

    private fun folderUris(): List<Uri> = listOfNotNull(savedFolderUri(), secondFolderUri())

    fun savedFolderLabel(): String? = label(savedFolderUri())

    fun secondFolderLabel(): String? = label(secondFolderUri())

    private fun label(uri: Uri?): String? {
        if (uri == null) return null
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        return docId?.substringAfter(':')?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
    }

    fun setFolder(uri: Uri) = setFolder(KEY_FOLDER_URI, uri)

    fun setSecondFolder(uri: Uri): Boolean {
        if (uri == savedFolderUri()) return false
        setFolder(KEY_FOLDER_URI_2, uri)
        return true
    }

    private fun setFolder(key: String, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val previous = prefs.getString(key, null)?.let(Uri::parse)
        prefs.edit { putString(key, uri.toString()) }
        if (previous != null && previous != uri) release(previous)
        cachedRoms = null
    }

    fun clear() {
        val kept = secondFolderUri()
        savedFolderUri()?.takeIf { it != kept }?.let(::release)
        prefs.edit { remove(KEY_FOLDER_URI) }
        cachedRoms = null
    }

    fun clearSecondFolder() {
        val kept = savedFolderUri()
        secondFolderUri()?.takeIf { it != kept }?.let(::release)
        prefs.edit { remove(KEY_FOLDER_URI_2) }
        cachedRoms = null
    }

    private fun release(uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    companion object {
        @Volatile
        private var cachedRoms: List<Rom>? = null
        private val scanLock = Any()

        @Volatile
        private var instance: RomsRepository? = null

        fun get(context: Context): RomsRepository =
            instance ?: synchronized(this) {
                instance ?: RomsRepository(context.applicationContext).also { instance = it }
            }
    }

    fun cachedOrScan(): List<Rom> = cachedRoms?.let(::named) ?: scan()

    fun scan(force: Boolean = false): List<Rom> = synchronized(scanLock) {
        // The language change recreates the activity but not this cache.
        TitleLanguage.apply(context)
        val staleLanguage = scannedLanguage != null && scannedLanguage != TitleLanguage.tag
        if (!force && !staleLanguage) cachedRoms?.let { return named(it) }
        return doScan()
    }

    private var scannedLanguage: String? = null

    private fun doScan(): List<Rom> {
        TitleLanguage.apply(context)
        scannedLanguage = TitleLanguage.tag
        val folders = folderUris()
        if (folders.isEmpty()) return emptyList()
        val found = folders.flatMap { uri ->
            runCatching { walk(uri) }
                .onFailure { Log.w(TAG, "scan failed for $uri", it) }
                .getOrDefault(emptyList())
        }
            .distinctBy { it.uri.toString() }

        Log.i(TAG, "walked ${found.size} candidate file(s) in ${folders.size} folder(s), titles in ${TitleLanguage.tag}")

        return found
            .mapNotNull { it.toRom() }
            .also { cachedRoms = it }
            .let(::named)
    }

    private fun named(roms: List<Rom>): List<Rom> {
        val titles = GameTitles.cached(context)
        return roms.filterNot(hiddenRoms::isHidden)
            .map { GameTitles.apply(titles, it) }
            .map(romNames::apply)
            .sortedWith(compareBy({ it.console.ordinal }, { it.displayName.lowercase() }))
    }

    private data class Candidate(
        val uri: Uri,
        val name: String,
        val console: Console,
        val addedAt: Long,
        val size: Long,
    )

    private fun walk(treeUri: Uri): List<Candidate> {
        val resolver = context.contentResolver
        val out = mutableListOf<Candidate>()
        val queue = ArrayDeque<Triple<String, Int, String>>()
        val seen = mutableSetOf<String>()

        queue += Triple(DocumentsContract.getTreeDocumentId(treeUri), 0, "")

        while (queue.isNotEmpty() && out.size < MAX_FILES) {
            val (parentId, depth, folderName) = queue.removeFirst()
            if (!seen.add(parentId)) continue

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                // Only source for "recently added"; providers expose no creation date.
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            )

            // Some providers throw on entries they have since lost access to.
            val cursor = runCatching { resolver.query(childrenUri, projection, null, null, null) }
                .onFailure { Log.w(TAG, "cannot list $parentId", it) }
                .getOrNull() ?: continue

            cursor.use {
                while (it.moveToNext() && out.size < MAX_FILES) {
                    val docId = it.getString(0) ?: continue
                    val name = it.getString(1) ?: continue
                    val mime = it.getString(2)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth + 1 <= MAX_DEPTH && !name.startsWith(".")) {
                            queue += Triple(docId, depth + 1, name)
                        }
                        continue
                    }

                    val extLower = name.substringAfterLast('.', "").lowercase()
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val console = if (extLower == NdsArchive.EXTENSION) {
                        if (holdsDsRom(uri)) Console.DS else continue
                    } else {
                        val byName = Console.forExtension(extLower) ?: continue
                        val folderConsole = Console.forFolder(folderName)
                        when {
                            folderConsole != null -> folderConsole
                            extLower in DiscImage.AMBIGUOUS_EXTENSIONS ->
                                discImages.identify(uri) ?: continue
                            extLower in DiscImage.SNIFFED_EXTENSIONS ->
                                discImages.identify(uri) ?: byName
                            else -> byName
                        }
                    }
                    out += Candidate(
                        uri = uri,
                        name = name,
                        console = console,
                        // getLong on a null column returns 0 on some providers and throws on others.
                        addedAt = if (it.isNull(3)) 0L else it.getLong(3),
                        size = if (it.isNull(4)) 0L else it.getLong(4),
                    )
                }
            }
        }

        if (out.size >= MAX_FILES) {
            Log.w(TAG, "stopped at $MAX_FILES files, library larger than expected")
        }
        return out
    }

    private fun holdsDsRom(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { NdsArchive.openRom(it) != null }
    }.onFailure { Log.w(TAG, "cannot read archive $uri", it) }.getOrNull() ?: false

    private fun Candidate.toRom(): Rom? = readRom()?.copy(addedAt = addedAt)

    private fun Candidate.readRom(): Rom? {
        if (console == Console.DS) return toDsRom()
        if (console == Console.SWITCH) return toSwitchRom()

        if (console == Console.PSP) return toPspRom()

        if (console == Console.GAMECUBE || console == Console.WII || console == Console.PS2) {
            return toDiscRom()
        }

        if (console != Console.THREE_DS) return null

        // CIA has no magic: only the extension says what it is.
        val header = headerReader.read(uri, cia = name.substringAfterLast('.', "").equals("cia", true))
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
        val ambiguous = name.substringAfterLast('.', "").lowercase() in DiscImage.AMBIGUOUS_EXTENSIONS
        if (ambiguous && !data.recognised) return null
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

        val data = if (NdsArchive.isArchive(name)) ndsReader.readArchived(uri) else ndsReader.read(uri)
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

    private fun Candidate.toDiscRom(): Rom {
        val fallback = Rom(
            uri = uri,
            filename = name,
            displayName = displayNameFromFilename(name),
            console = console
        )
        val info = discImages.read(uri, addedAt, size) ?: return fallback
        return fallback.copy(
            console = info.console,
            productCode = info.gameId,
            ps2ElfCrc = info.ps2Identity?.elfCrc,
        )
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

        val key = switchReader.titleId(uri) ?: return fallback
        ndsKeyCache[uri.toString()] = key

        return fallback.copy(
            titleIdHex = key,
            iconFile = iconCache.fileFor(key).takeIf { it.exists() },
            accentArgb = iconCache.readAccent(key)
        )
    }

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
