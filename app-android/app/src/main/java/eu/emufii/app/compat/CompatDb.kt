package eu.emufii.app.compat

import eu.emufii.app.library.Rom
import eu.emufii.app.library.compatKeys
import org.json.JSONObject

enum class CompatRating {
    PERFECT,

    PARTIAL,

    BROKEN,

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

data class CompatEntry(
    val name: String,
    val rating: CompatRating,
    val note: String? = null,
    val keys: List<String>,
    val online: CompatRating? = null,
    val wireless: CompatRating? = null,
    /** PS2 only: ids of the revival resolvers serving this game (`Ps2Revival`). */
    val servers: List<String> = emptyList(),
    /** PS2 only: PSRewired application ids whose live players count for this game. */
    val psrewiredApps: List<Int> = emptyList(),
    /** PS2 only: rows of ps2online.com's activity table that count for this game. */
    val ps2onlineRows: List<String> = emptyList()
)

private val RANK = listOf(CompatRating.PERFECT, CompatRating.PARTIAL, CompatRating.UNTESTED, CompatRating.BROKEN)

private fun better(a: CompatRating, b: CompatRating?): CompatRating =
    if (b == null || RANK.indexOf(a) <= RANK.indexOf(b)) a else b

class CompatDb private constructor(
    private val byKey: Map<String, CompatEntry>
) {
    val size: Int get() = byKey.size

    fun ratingFor(keys: List<String>): CompatEntry? = keys.firstNotNullOfOrNull { byKey[it] }

    fun isBroken(rom: Rom): Boolean =
        ratingFor(rom.compatKeys())?.rating == CompatRating.BROKEN

    companion object {
        val EMPTY = CompatDb(emptyMap())

        /** Entry by entry: a malformed line costs one game; unknown ratings are skipped, not defaulted. */
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
                    wireless = rating.takeIf { online != null },
                    servers = obj.optJSONArray("servers")?.let { a ->
                        (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
                    }.orEmpty(),
                    psrewiredApps = obj.optJSONObject("live")?.optJSONArray("psrewired")?.let { a ->
                        (0 until a.length()).mapNotNull { a.optInt(it, -1).takeIf { id -> id >= 0 } }
                    }.orEmpty(),
                    ps2onlineRows = obj.optJSONObject("live")?.optJSONArray("ps2online")?.let { a ->
                        (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
                    }.orEmpty()
                )
                for (key in keys) map.putIfAbsent(key, entry)
            }
            CompatDb(map)
        }.getOrDefault(EMPTY)
    }
}

val LocalCompatDb = androidx.compose.runtime.staticCompositionLocalOf { CompatDb.EMPTY }
