package eu.emufii.app.ps2

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * ARMSX2's labels, in all of its languages, read from its own files.
 *
 * The same principle as `eu.emufii.app.netplay.NetplayLabels`, a different
 * container: there we resolve the emulator's Android resources in each locale,
 * here we open its assets, where ARMSX2 keeps 19 JSON files under `i18n`.
 *
 * Why read *every* language rather than the device's: there is no reliable way
 * to know which language the app opposite is running in. It may follow the
 * system locale, it may follow an internal setting. Searching all 19 costs one
 * read at the first call and removes the question.
 *
 * English always stays in the list, and not only out of caution: the Local Link
 * labels are in *no* translation file, they are hardcoded in ARMSX2's source, so
 * for them English is the only possible answer today. The day upstream
 * translates them, they will appear in the JSON and be found without our
 * touching anything.
 */
class Ps2Labels(private val context: Context) {

    /** Key to every known translation, English included. Read once. */
    private val cache = HashMap<String, List<String>>()

    /** All of ARMSX2's language files, loaded once. */
    private val catalogs: List<JSONObject> by lazy { loadCatalogs() }

    /**
     * Every way ARMSX2 might write this label.
     *
     * [english] is what we fall back on, and it is always returned: a key missing
     * from the translations is not an error, it is the normal case for Local
     * Link.
     */
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

    /**
     * Opens ARMSX2's assets from inside Emufii.
     *
     * Possible because the package is declared in `<queries>`: without that,
     * `createPackageContext` throws `NameNotFoundException` and we would only
     * find out at runtime. The failure is not fatal, we fall back to English, and
     * the automation will only work on an English ARMSX2, which is a legible
     * degraded mode rather than a silent breakdown.
     */
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
        }.also { Log.d(TAG, "libellés ARMSX2 : ${it.size} langues lues") }
    }.getOrElse {
        // Said once, loudly: this is the difference between "the automation does
        // not bite" and "the automation does not bite in French".
        Log.w(TAG, "assets i18n d'ARMSX2 illisibles, repli sur l'anglais seul", it)
        emptyList()
    }

    private fun installed(pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    private companion object {
        const val TAG = "Ps2Labels"
    }
}
