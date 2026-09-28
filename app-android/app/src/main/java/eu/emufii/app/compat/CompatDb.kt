package eu.emufii.app.compat

import org.json.JSONObject

/** Four levels: a finer scale demands a judgement nobody makes consistently across seven consoles. */
enum class CompatRating {
    PERFECT,

    PARTIAL,

    BROKEN,

    /** Distinct from being absent from the database, which says nothing at all. */
    UNTESTED;

    companion object {
        fun fromName(name: String?): CompatRating? = when (name?.lowercase()) {
            "perfect" -> PERFECT
            "partial" -> PARTIAL
            "broken" -> BROKEN
            "untested" -> UNTESTED
            else -> null
        }
    }
}

/** [name] is for the tool and for a reader; matching is on [keys] alone, a title changing with the language. */
data class CompatEntry(
    val name: String,
    val rating: CompatRating,
    val note: String? = null,
    val keys: List<String>,
    /** DS only: the verdict over Wi-Fi Connection, apart from local wireless play. */
    val online: CompatRating? = null,
    /** DS only: the file's own `rating`, local wireless play. [rating] is then the better of the two, for the tile. */
    val wireless: CompatRating? = null
)

/** Best first: a game that works in one mode is not a broken game. */
private val RANK = listOf(CompatRating.PERFECT, CompatRating.PARTIAL, CompatRating.UNTESTED, CompatRating.BROKEN)

private fun better(a: CompatRating, b: CompatRating?): CompatRating =
    if (b == null || RANK.indexOf(a) <= RANK.indexOf(b)) a else b

/** Flattened on parse: the library draws hundreds of tiles per scroll, each asking this. */
class CompatDb private constructor(
    private val byKey: Map<String, CompatEntry>
) {
    val size: Int get() = byKey.size

    /** Keys arrive most specific first and the first hit wins: a region beats its family. */
    fun ratingFor(keys: List<String>): CompatEntry? = keys.firstNotNullOfOrNull { byKey[it] }

    companion object {
        val EMPTY = CompatDb(emptyMap())

        /**
         * Entry by entry: the file is hand-edited, and one malformed line must cost one
         * game. An unknown rating is skipped, not defaulted, defaulting inventing a verdict.
         */
        fun parse(json: String): CompatDb = runCatching {
            val games = JSONObject(json).optJSONArray("games") ?: return@runCatching EMPTY
            val map = LinkedHashMap<String, CompatEntry>()
            for (i in 0 until games.length()) {
                val obj = games.optJSONObject(i) ?: continue
                val rating = CompatRating.fromName(obj.optString("rating")) ?: continue
                val keysArray = obj.optJSONArray("keys") ?: continue
                val keys = (0 until keysArray.length())
                    .mapNotNull { keysArray.optString(it).trim().takeIf(String::isNotEmpty) }
                if (keys.isEmpty()) continue
                val online = CompatRating.fromName(obj.optString("online"))
                val entry = CompatEntry(
                    name = obj.optString("name").ifBlank { keys.first() },
                    rating = better(rating, online),
                    note = obj.optString("note").takeIf { it.isNotBlank() && it != "null" },
                    keys = keys,
                    online = online,
                    wireless = rating.takeIf { online != null }
                )
                // First writer wins: a duplicated key is a no-op, not a verdict that
                // depends on file order.
                for (key in keys) map.putIfAbsent(key, entry)
            }
            CompatDb(map)
        }.getOrDefault(EMPTY)
    }
}

/** Empty by default: no badge means nothing is known, never that it works. */
val LocalCompatDb = androidx.compose.runtime.staticCompositionLocalOf { CompatDb.EMPTY }
