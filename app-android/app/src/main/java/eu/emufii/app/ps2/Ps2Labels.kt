package eu.emufii.app.ps2

import android.content.Context
import android.util.Log
import org.json.JSONObject

class Ps2Labels(private val context: Context) {

    private val cache = HashMap<String, List<String>>()

    private val catalogs: List<JSONObject> by lazy { loadCatalogs() }

    /** English always included: Local Link labels are hardcoded in ARMSX2. */
    fun of(key: String?, english: String): List<String> = cache.getOrPut(key ?: english) {
        val out = LinkedHashSet<String>()
        out += english
        if (key != null) {
            for (catalog in catalogs) {
                catalog.optString(key).takeIf { it.isNotBlank() }?.let { out += it }
            }
        }
        out.toList()
    }

    /** Needs ARMSX2 in <queries>, or createPackageContext throws. */
    private fun loadCatalogs(): List<JSONObject> = runCatching {
        val pkg = Ps2Target.packages.first { installed(it) }
        val assets = context.createPackageContext(pkg, 0).assets
        val files = assets.list(Ps2Target.I18n.DIRECTORY).orEmpty()
        files.filter { it.endsWith(".json") }.mapNotNull { name ->
            runCatching {
                val text = assets.open("${Ps2Target.I18n.DIRECTORY}/$name")
                    .bufferedReader()
                    .use { it.readText() }
                JSONObject(text)
            }.getOrNull()
        }.also { Log.d(TAG, "ARMSX2 labels: ${it.size} languages read") }
    }.getOrElse {
        Log.w(TAG, "ARMSX2 i18n assets unreadable, falling back to English only", it)
        emptyList()
    }

    private fun installed(pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    private companion object {
        const val TAG = "Ps2Labels"
    }
}
