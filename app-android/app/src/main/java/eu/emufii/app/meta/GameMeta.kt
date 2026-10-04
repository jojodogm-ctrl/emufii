package eu.emufii.app.meta

import org.json.JSONObject
import java.util.Locale

data class GameMeta(
    val keys: List<String>,
    val genreFr: String? = null,
    val genreEn: String? = null,
    val released: String? = null,
    val summaryFr: String? = null,
    val summaryEn: String? = null,
    val screenshots: List<String> = emptyList(),
    /** The text is Wikipedia's under CC BY-SA: attribution is the condition. */
    val source: String? = null,
) {
    fun summaryFor(locale: Locale): String? =
        if (locale.language.equals("fr", ignoreCase = true)) summaryFr ?: summaryEn
        else summaryEn ?: summaryFr

    fun genreFor(locale: Locale): String? {
        val both = if (locale.language.equals("fr", ignoreCase = true)) genreFr ?: genreEn
        else genreEn ?: genreFr
        return both?.substringBefore(" · ")
    }

    fun isEmpty(locale: Locale): Boolean =
        genreFor(locale) == null && released == null && screenshots.isEmpty() &&
            summaryFor(locale) == null
}

class GameMetaDb private constructor(private val byKey: Map<String, GameMeta>) {
    val size: Int get() = byKey.size

    fun metaFor(keys: List<String>): GameMeta? = keys.firstNotNullOfOrNull { byKey[it] }

    companion object {
        val EMPTY = GameMetaDb(emptyMap())

        fun parse(json: String): GameMetaDb = runCatching {
            val games = JSONObject(json).optJSONArray("games") ?: return@runCatching EMPTY
            val map = LinkedHashMap<String, GameMeta>()
            for (i in 0 until games.length()) {
                val obj = games.optJSONObject(i) ?: continue
                val keysArray = obj.optJSONArray("keys") ?: continue
                val keys = (0 until keysArray.length())
                    .mapNotNull { keysArray.optString(it).trim().takeIf(String::isNotEmpty) }
                if (keys.isEmpty()) continue
                val shots = obj.optJSONArray("screenshots")
                val entry = GameMeta(
                    keys = keys,
                    genreFr = obj.text("genre_fr"),
                    genreEn = obj.text("genre_en"),
                    released = obj.text("released"),
                    summaryFr = obj.text("summary_fr"),
                    summaryEn = obj.text("summary_en"),
                    source = obj.text("source"),
                    // https only: a picture fetched in clear is one anybody can replace.
                    screenshots = (0 until (shots?.length() ?: 0))
                        .mapNotNull { shots?.optString(it)?.trim() }
                        .filter { it.startsWith("https://") }
                        .take(4),
                )
                for (key in keys) map.putIfAbsent(key, entry)
            }
            GameMetaDb(map)
        }.getOrDefault(EMPTY)

        private fun JSONObject.text(key: String): String? =
            optString(key).takeIf { it.isNotBlank() && it != "null" }
    }
}

val LocalGameMetaDb = androidx.compose.runtime.staticCompositionLocalOf { GameMetaDb.EMPTY }
