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

/**
 * A frontend that has already scraped artwork onto the device. Each one files its images
 * under the ROM's own filename, so no catalogue search and no key are needed: only the
 * folder layout and the console names differ.
 */
enum class ArtworkFrontend(@StringRes val labelRes: Int) {
    COCOON(R.string.artwork_frontend_cocoon) {
        override val defaultFolderId = "primary:Cocoonv2"
        override val mediaFolder = "downloaded_media"

        /**
         * Cocoon's own names, not ours. GameCube is absent rather than mapped to `wii`:
         * Cocoon files them apart, and the Wii folder would hand it the artwork of
         * whatever Wii game shares its filename.
         */
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

        /** ES-DE's system names. GameCube has its own folder there. */
        override fun folderFor(console: Console): String? = when (console) {
            Console.THREE_DS -> "n3ds"
            Console.DS -> "nds"
            Console.PSP -> "psp"
            Console.PS2 -> "ps2"
            Console.SWITCH -> "switch"
            Console.WII -> "wii"
            Console.GAMECUBE -> "gc"
        }

        /**
         * ES-DE has no square key art: the box front is the closest, and a miximage the
         * next best when only that was scraped. Each list is walked in order, the first
         * folder holding the file wins.
         */
        override fun foldersFor(kind: FrontendMedia.Kind): List<String> = when (kind) {
            FrontendMedia.Kind.ICON -> listOf("covers", "miximages")
            FrontendMedia.Kind.HERO -> listOf("fanart", "screenshots")
            FrontendMedia.Kind.LOGO -> listOf("marquees")
            FrontendMedia.Kind.SCREENSHOT_GAMEPLAY -> listOf("screenshots")
            FrontendMedia.Kind.SCREENSHOT_TITLE -> listOf("titlescreens")
        }
    };

    /** Where the picker opens, as an external-storage document id. */
    abstract val defaultFolderId: String

    /** The subfolder of the granted root that holds `<system>/<kind>/`. */
    abstract val mediaFolder: String

    abstract fun folderFor(console: Console): String?

    abstract fun foldersFor(kind: FrontendMedia.Kind): List<String>

    companion object {
        fun fromName(name: String?): ArtworkFrontend =
            entries.firstOrNull { it.name == name } ?: COCOON
    }
}

/**
 * Reads the artwork a frontend already scraped, under the ROM's own filename: no
 * catalogue to search, no key to type, and the cover a player re-cropped is the one they
 * mean. Read-only, always; Emufii never writes into a frontend's folders.
 */
object FrontendMedia {

    enum class Kind {
        /** Square key art (Cocoon), or the box front (ES-DE). */
        ICON,

        /** Wide banner. */
        HERO,

        /** The title, drawn, on transparency. */
        LOGO,

        SCREENSHOT_GAMEPLAY,

        SCREENSHOT_TITLE,
    }

    /**
     * One index per frontend, console and folder: a grid draws hundreds of tiles per
     * scroll, and listing a folder through the storage provider costs a real query.
     */
    private val indexes = ConcurrentHashMap<String, Map<String, Uri>>()

    /** Dropped when the player picks another folder or frontend, so the next tile rebuilds. */
    fun forget() = indexes.clear()

    /** [root] is the frontend folder the player granted us: `Cocoonv2` or `ES-DE` in practice. */
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
        for (folder in frontend.foldersFor(kind)) {
            val index = indexes.getOrPut("${frontend.name}|$root|$console|$folder") {
                buildIndex(context, frontend, root, console, folder)
            }
            index[base]?.let { return it }
        }
        return null
    }

    /** The name a frontend files artwork under. */
    private fun baseOf(filename: String): String =
        filename.substringBeforeLast('.', filename)

    /**
     * Read off the device and nothing else: the served catalogue carries screenshot links,
     * but this panel is looked at on a handheld, often offline. Gameplay before the title
     * screen, which is a logo the player has already seen on the cover next to it.
     */
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
                // The player may have picked the media folder itself rather than its
                // parent: `ES-DE/downloaded_media` is a natural place to stop.
                val media = tree.findFile(frontend.mediaFolder) ?: tree
                media.findFile(console)?.findFile(folderName)
            }
        }.getOrNull() ?: return emptyMap()

        // `DocumentFile.listFiles()` allocates a document object per entry to read two
        // columns off it.
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            folder.uri,
            DocumentsContract.getDocumentId(folder.uri)
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
                    if (!isImage(name)) continue
                    names += name to DocumentsContract.buildDocumentUriUsingTree(root, id)
                }
            }
        }
        if (names.isEmpty()) return emptyMap()

        val plain = names.map { baseOf(it.first) }.toHashSet()
        val best = HashMap<String, Pair<Int, Uri>>()
        for ((name, uri) in names) {
            val (base, rank) = when (frontend) {
                ArtworkFrontend.COCOON -> classifyCocoon(baseOf(name), plain)
                ArtworkFrontend.ESDE -> baseOf(name) to 1
            }
            val score = rank * 2 + if (name.endsWith(".png", ignoreCase = true)) 0 else 1
            val current = best[base]
            if (current == null || score < current.first) best[base] = score to uri
        }
        return best.mapValues { it.value.second }
    }

    /** ES-DE folders can hold videos and `.svg`; Coil draws neither on a tile. */
    private fun isImage(name: String): Boolean =
        IMAGE_EXTENSIONS.any { name.endsWith(".$it", ignoreCase = true) }

    /**
     * Three shapes come out of Cocoon, and the order between them is the point:
     *
     *  - `Game__cocoon_edit_108_<hash>.png`, the player's own edit, wins outright.
     *  - `Game.png` is what the scraper downloaded.
     *  - `Game (1).png` is a second download, taken only when `Game` is really there next
     *    to it: otherwise a title genuinely ending in "(1)" is filed under a name no ROM has.
     */
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
