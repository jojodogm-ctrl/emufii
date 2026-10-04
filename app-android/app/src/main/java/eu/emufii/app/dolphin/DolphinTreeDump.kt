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

/** Written to public Downloads (`getExternalFilesDir` is not user-visible since Android 11). Debug only: it names the user's games. */
object DolphinTreeDump {

    private var written = false

    fun reset() {
        written = false
    }

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

        val message =
            if (ok) "Emufii: diagnostic written to Downloads/$name"
            else "Emufii: the diagnostic could not be written"
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        Log.i(TAG, "$message (${nodes.size} nodes, reason=$reason)")
    }

    private fun render(
        context: Context,
        pkg: String,
        nodes: List<Node>,
        reason: String
    ): String = buildString {
        appendLine("Emufii: Dolphin accessibility tree")
        appendLine("reason       : $reason")
        appendLine("date         : ${Date()}")
        appendLine("emufii       : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("target pkg   : $pkg ${versionOf(context, pkg)}")
        appendLine("device       : ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("locales      : ${context.resources.configuration.locales.toLanguageTags()}")
        appendLine("nodes        : ${nodes.size}")
        appendLine()

        appendLine("--- labels resolved in the resources of $pkg ---")
        for (name in LABELS) {
            val values = NetplayLabels.of(context, pkg, name)
            appendLine("$name (${values.size}) : ${values.joinToString(" | ")}")
        }
        appendLine()

        appendLine("--- nodes ---")
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

    private fun String.quote(): String = if (isEmpty()) "-" else "\"$this\""

    private fun versionOf(context: Context, pkg: String): String = runCatching {
        val info = context.packageManager.getPackageInfo(pkg, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("version inconnue")

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
