package eu.emufii.app.artwork

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import eu.emufii.app.R
import eu.emufii.app.library.Console
import eu.emufii.app.library.Rom
import java.util.concurrent.ConcurrentHashMap

enum class ArtworkFrontend(@StringRes val labelRes: Int) {
    COCOON(R.string.artwork_frontend_cocoon) {
        // Cocoon asks the player for a data folder on first run, so open at the storage root.
        override val defaultFolderId = "primary:"
        override val mediaFolder = "downloaded_media"

        // GameCube unmapped: Cocoon files it apart, and `wii` would match Wii games by filename.
        override fun folderFor(console: Console): String? = when (console) {
            Console.THREE_DS -> "n3ds"
            Console.DS -> "nds"
            Console.PSP -> "psp"
            Console.PS2 -> "ps2"
            Console.SWITCH -> "switch"
            Console.WII -> "wii"
            Console.GAMECUBE -> null
        }

        override fun foldersFor(kind: FrontendMedia.Kind): List<String> = when (kind) {
            FrontendMedia.Kind.ICON -> listOf("icon")
            FrontendMedia.Kind.HERO -> listOf("hero")
            FrontendMedia.Kind.LOGO -> listOf("logo")
            FrontendMedia.Kind.SCREENSHOT_GAMEPLAY -> listOf("screenshot_gameplay")
            FrontendMedia.Kind.SCREENSHOT_TITLE -> listOf("screenshot_title")
        }
    },

    ESDE(R.string.artwork_frontend_esde) {
        override val defaultFolderId = "primary:ES-DE"
        override val mediaFolder = "downloaded_media"

        override fun folderFor(console: Console): String? = when (console) {
            Console.THREE_DS -> "n3ds"
            Console.DS -> "nds"
            Console.PSP -> "psp"
            Console.PS2 -> "ps2"
            Console.SWITCH -> "switch"
            Console.WII -> "wii"
            Console.GAMECUBE -> "gc"
        }

        override fun foldersFor(kind: FrontendMedia.Kind): List<String> = when (kind) {
            FrontendMedia.Kind.ICON -> listOf("covers", "miximages")
            FrontendMedia.Kind.HERO -> listOf("fanart", "screenshots")
            FrontendMedia.Kind.LOGO -> listOf("marquees")
            FrontendMedia.Kind.SCREENSHOT_GAMEPLAY -> listOf("screenshots")
            FrontendMedia.Kind.SCREENSHOT_TITLE -> listOf("titlescreens")
        }
    },

    IISU(R.string.artwork_frontend_iisu) {
        override val defaultFolderId = "primary:Android/media/com.iisulauncher"
        override val mediaFolder = "iiSULauncher/assets/media/roms/consoles"
        override val perGame = true

        override fun folderFor(console: Console): String? = ESDE.folderFor(console)

        override fun foldersFor(kind: FrontendMedia.Kind): List<String> = when (kind) {
            FrontendMedia.Kind.ICON -> listOf("icon")
            FrontendMedia.Kind.HERO -> listOf("hero_1")
            FrontendMedia.Kind.LOGO -> listOf("title")
            FrontendMedia.Kind.SCREENSHOT_GAMEPLAY, FrontendMedia.Kind.SCREENSHOT_TITLE -> emptyList()
        }
    };

    abstract val defaultFolderId: String

    abstract val mediaFolder: String

    open val perGame: Boolean = false

    abstract fun folderFor(console: Console): String?

    abstract fun foldersFor(kind: FrontendMedia.Kind): List<String>

    companion object {
        fun fromName(name: String?): ArtworkFrontend =
            entries.firstOrNull { it.name == name } ?: COCOON
    }
}

object FrontendMedia {

    enum class Kind {
        ICON,

        HERO,

        LOGO,

        SCREENSHOT_GAMEPLAY,

        SCREENSHOT_TITLE,
    }

    // Cached: listing a folder through the storage provider is a real query, and grids draw hundreds of tiles.
    private val indexes = ConcurrentHashMap<String, Map<String, Uri>>()

    fun forget() = indexes.clear()

    fun uriFor(
        context: Context,
        frontend: ArtworkFrontend,
        root: Uri?,
        rom: Rom,
        kind: Kind
    ): Uri? {
        if (root == null) return null
        val console = frontend.folderFor(rom.console) ?: return null
        val base = baseOf(rom.filename)
        if (frontend.perGame) return perGameUri(context, frontend, root, console, base, kind)
        for (folder in frontend.foldersFor(kind)) {
            val index = indexes.getOrPut("${frontend.name}|$root|$console|$folder") {
                buildIndex(context, frontend, root, console, folder)
            }
            index[base]?.let { return it }
        }
        return null
    }

    private fun baseOf(filename: String): String =
        filename.substringBeforeLast('.', filename)

    fun stillsFor(context: Context, frontend: ArtworkFrontend, root: Uri?, rom: Rom): List<Uri> =
        listOfNotNull(
            uriFor(context, frontend, root, rom, Kind.SCREENSHOT_GAMEPLAY),
            uriFor(context, frontend, root, rom, Kind.SCREENSHOT_TITLE),
        )

    private fun buildIndex(
        context: Context,
        frontend: ArtworkFrontend,
        root: Uri,
        console: String,
        folderName: String
    ): Map<String, Uri> {
        val folder = runCatching {
            DocumentFile.fromTreeUri(context, root)?.let { tree ->
                mediaRoot(tree, frontend).findFile(console)?.findFile(folderName)
            }
        }.getOrNull() ?: return emptyMap()

        val names = listChildren(context, root, folder.uri).filter { isImage(it.first) }
        if (names.isEmpty()) return emptyMap()

        val plain = names.map { baseOf(it.first) }.toHashSet()
        val best = HashMap<String, Pair<Int, Uri>>()
        for ((name, uri) in names) {
            val (base, rank) = when (frontend) {
                ArtworkFrontend.COCOON -> classifyCocoon(baseOf(name), plain)
                ArtworkFrontend.ESDE, ArtworkFrontend.IISU -> baseOf(name) to 1
            }
            val score = rank * 2 + if (name.endsWith(".png", ignoreCase = true)) 0 else 1
            val current = best[base]
            if (current == null || score < current.first) best[base] = score to uri
        }
        return best.mapValues { it.value.second }
    }

    private fun perGameUri(
        context: Context,
        frontend: ArtworkFrontend,
        root: Uri,
        console: String,
        base: String,
        kind: Kind
    ): Uri? {
        val games = indexes.getOrPut("${frontend.name}|$root|$console") {
            val folder = runCatching {
                DocumentFile.fromTreeUri(context, root)?.let { mediaRoot(it, frontend).findFile(console) }
            }.getOrNull() ?: return@getOrPut emptyMap()
            listChildren(context, root, folder.uri).toMap()
        }
        val game = games[base] ?: return null
        val files = indexes.getOrPut("${frontend.name}|$game") {
            val best = HashMap<String, Pair<Int, Uri>>()
            for ((name, uri) in listChildren(context, root, game)) {
                if (!isImage(name)) continue
                val score = if (name.endsWith(".png", ignoreCase = true)) 0 else 1
                val current = best[baseOf(name)]
                if (current == null || score < current.first) best[baseOf(name)] = score to uri
            }
            best.mapValues { it.value.second }
        }
        return frontend.foldersFor(kind).firstNotNullOfOrNull { files[it] }
    }

    private fun mediaRoot(tree: DocumentFile, frontend: ArtworkFrontend): DocumentFile {
        var here = tree
        for (segment in frontend.mediaFolder.split('/')) {
            here = here.findFile(segment) ?: continue
        }
        return here
    }

    /** Queries two columns directly; `DocumentFile.listFiles()` allocates an object per entry. */
    private fun listChildren(context: Context, root: Uri, folder: Uri): List<Pair<String, Uri>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            folder,
            DocumentsContract.getDocumentId(folder)
        )
        val names = mutableListOf<Pair<String, Uri>>()
        runCatching {
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    names += name to DocumentsContract.buildDocumentUriUsingTree(root, id)
                }
            }
        }
        return names
    }

    /** ES-DE folders can hold videos and `.svg`, which Coil does not draw. */
    private fun isImage(name: String): Boolean =
        IMAGE_EXTENSIONS.any { name.endsWith(".$it", ignoreCase = true) }

    /** `Name__cocoon_edit_108_<hash>` (user edit) wins, then `Name`, then `Name (1)` only if `Name` exists. */
    private fun classifyCocoon(stem: String, plain: Set<String>): Pair<String, Int> {
        val edit = stem.indexOf(EDIT_MARK)
        if (edit > 0) return stem.substring(0, edit) to 0

        val duplicate = DUPLICATE.find(stem)
        if (duplicate != null) {
            val without = stem.removeRange(duplicate.range)
            if (without in plain) return without to 2
        }
        return stem to 1
    }

    private const val EDIT_MARK = "__cocoon_edit_"
    private val DUPLICATE = Regex(" \\(\\d+\\)$")
    private val IMAGE_EXTENSIONS = listOf("png", "jpg", "jpeg", "webp")
}
