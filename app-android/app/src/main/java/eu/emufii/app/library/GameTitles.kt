package eu.emufii.app.library

import android.content.Context
import eu.emufii.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GameTitles {

    private fun fileFor(lang: String) = "game_titles-$lang.json"

    @Volatile
    private var caches: Map<String, Map<String, String>> = emptyMap()

    fun cached(context: Context, lang: String = TitleLanguage.tag): Map<String, String> {
        caches[lang]?.let { return it }
        val read = runCatching {
            val file = File(context.filesDir, fileFor(lang))
            if (file.exists()) parse(file.readText()) else emptyMap()
        }.getOrDefault(emptyMap())
        caches = caches + (lang to read)
        return read
    }

    fun apply(titles: Map<String, String>, rom: Rom): Rom {
        if (titles.isEmpty()) return rom
        val name = resolve(titles, rom.displayName, rom.filename, rom.compatKeys())
            ?: return rom
        return rom.copy(displayName = name)
    }

    fun resolve(
        titles: Map<String, String>,
        displayName: String,
        filename: String,
        keys: List<String>
    ): String? {
        if (displayName != displayNameFromFilename(filename)) return null
        return keys.firstNotNullOfOrNull { titles[it] }
    }

    suspend fun refresh(
        context: Context,
        roms: List<Rom>,
        baseUrl: String = BuildConfig.COORDINATOR_BASE_URL
    ): Boolean = withContext(Dispatchers.IO) {
        val lang = TitleLanguage.tag
        val keys = roms
            .filter { it.displayName == displayNameFromFilename(it.filename) }
            .flatMap { it.compatKeys() }
            .distinct()
        if (keys.isEmpty()) return@withContext false

        val known = cached(context, lang)
        val answer = HashMap<String, String>()
        for (batch in batches(keys)) {
            answer += fetch(baseUrl, lang, batch) ?: continue
        }
        if (answer.isEmpty()) return@withContext false

        val merged = known + answer
        runCatching {
            val out = JSONObject()
            for ((k, v) in merged) out.put(k, v)
            File(context.filesDir, fileFor(lang)).writeText(out.toString())
        }
        caches = caches + (lang to merged)

        roms.any { apply(merged, it).displayName != it.displayName }
    }

    /** The coordinator answers at most 500 keys and Node drops request lines past 16 KB. */
    private const val KEYS_PER_REQUEST = 400

    private fun fetch(baseUrl: String, lang: String, keys: List<String>): Map<String, String>? =
        runCatching {
            val query = URLEncoder.encode(keys.joinToString(","), "UTF-8")
            val conn = (URL("$baseUrl/titles?lang=$lang&keys=$query").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 6000
            }
            try {
                if (conn.responseCode != 200) return@runCatching null
                parse(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }.getOrNull()

    fun batches(keys: List<String>): List<List<String>> = keys.chunked(KEYS_PER_REQUEST)

    private fun parse(raw: String): Map<String, String> = runCatching {
        val obj = JSONObject(raw)
        val titlesField = obj.optJSONObject("titles") ?: obj
        val out = HashMap<String, String>()
        for (key in titlesField.keys()) {
            val name = titlesField.optString(key)
            if (name.isNotBlank()) out[key] = name
        }
        out
    }.getOrDefault(emptyMap())
}
