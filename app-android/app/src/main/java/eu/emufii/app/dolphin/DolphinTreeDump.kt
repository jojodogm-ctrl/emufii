package eu.emufii.app.dolphin

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import eu.emufii.app.BuildConfig
import eu.emufii.app.netplay.NetplayLabels
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the tree the driver has just read into a file the player can send.
 *
 * It exists for a precise reason: the project's only test bench is the Thor, and
 * when the Dolphin automation fails for a remote player with no PC, their logcat
 * is out of reach. Two hypotheses have already been spent guessing on their
 * behalf, the build (wrong), then the node ceiling (wrong), and each cost a round
 * trip. A dump settles it in one send.
 *
 * The file goes into the public Downloads, not into the app's private storage:
 * the target is someone who has to find it in their file browser and drag it into
 * Discord. `getExternalFilesDir` has been invisible to them since Android 11.
 *
 * Off unless [BuildConfig.TREE_DUMP]: the dump names the games in the grid, which
 * has no business in a production build.
 */
object DolphinTreeDump {

    /**
     * One dump per plan.
     *
     * The driver passes over a stuck screen several times a second, that being
     * the whole point of the spaced re-reads, and without this safeguard the
     * failure we want to photograph would fill Downloads with copies of the same
     * tree. Reset by [reset] when a new plan is armed.
     */
    private var written = false

    fun reset() {
        written = false
    }

    /**
     * Photographs [nodes] and tells the player, once.
     *
     * [reason] says at which fork the driver gave up: it is the first thing to
     * read in the file, before the nodes themselves.
     */
    fun capture(context: Context, pkg: String, nodes: List<Node>, reason: String) {
        if (!BuildConfig.TREE_DUMP || written) return
        written = true

        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
        val name = "emufii-arbre-dolphin-$stamp.txt"
        val body = render(context, pkg, nodes, reason)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val ok = runCatching {
            val uri = context.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(body.toByteArray())
            } ?: return@runCatching false
            true
        }.getOrElse {
            Log.w(TAG, "dump impossible", it)
            false
        }

        // The player does not read logs, that being this file's premise. If they
        // are not told on screen they will never know they have something to
        // send, and the dump will have been for nothing.
        val message =
            if (ok) "Emufii : diagnostic écrit dans Téléchargements/$name"
            else "Emufii : le diagnostic n'a pas pu être écrit"
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        Log.i(TAG, "$message (${nodes.size} nœuds, motif=$reason)")
    }

    private fun render(
        context: Context,
        pkg: String,
        nodes: List<Node>,
        reason: String
    ): String = buildString {
        appendLine("Emufii — arbre d'accessibilité Dolphin")
        appendLine("motif        : $reason")
        appendLine("date         : ${Date()}")
        appendLine("emufii       : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("paquet visé  : $pkg ${versionOf(context, pkg)}")
        appendLine("appareil     : ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("langues      : ${context.resources.configuration.locales.toLanguageTags()}")
        appendLine("nœuds        : ${nodes.size}")
        appendLine()

        // The labels first: one unresolved label explains a silent driver all by
        // itself, and that is half the cases. Zero translations for a name means
        // the string does not exist in this Dolphin build; a full list means the
        // word is known and it is the tree that does not contain it.
        appendLine("--- libellés résolus dans les ressources de $pkg ---")
        for (name in LABELS) {
            val values = NetplayLabels.of(context, pkg, name)
            appendLine("$name (${values.size}) : ${values.joinToString(" | ")}")
        }
        appendLine()

        appendLine("--- nœuds ---")
        nodes.forEachIndexed { i, n ->
            appendLine(
                "[$i] ${n.className}" +
                    " texte=${n.text.quote()}" +
                    " desc=${n.description.quote()}" +
                    " id=${n.viewId.quote()}" +
                    " clic=${n.clickable}" +
                    " bornes=[${n.bounds.left},${n.bounds.top}][${n.bounds.right},${n.bounds.bottom}]"
            )
        }
    }

    private fun String.quote(): String = if (isEmpty()) "-" else "«$this»"

    private fun versionOf(context: Context, pkg: String): String = runCatching {
        val info = context.packageManager.getPackageInfo(pkg, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("version inconnue")

    /** Everything the driver tries to read, in the order it uses them. */
    private val LABELS = listOf(
        DolphinTarget.LABEL_MENU_NETPLAY,
        DolphinTarget.LABEL_NICKNAME,
        DolphinTarget.LABEL_IP_ADDRESS,
        DolphinTarget.LABEL_PORT,
        DolphinTarget.LABEL_CONNECTION_TYPE,
        DolphinTarget.LABEL_DIRECT_CONNECTION,
        DolphinTarget.LABEL_TRAVERSAL_SERVER,
        DolphinTarget.LABEL_ROLE_CONNECT,
        DolphinTarget.LABEL_ROLE_HOST
    )

    private const val TAG = "DolphinNetplay"
}
