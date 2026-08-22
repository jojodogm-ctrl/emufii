package eu.emufii.app.compat

import org.json.JSONObject

/**
 * How well a game runs through Emufii.
 *
 * Three levels and no more. A finer scale would demand a judgement nobody can
 * make consistently across seven consoles, and the player only ever needs to
 * answer one question before a session: is it worth starting.
 *
 * Four, because "nobody knows" and "known to work" are different facts and a
 * player deciding whether to invite someone needs to tell them apart.
 */
enum class CompatRating {
    /** Plays through, multiplayer included. No badge. */
    PERFECT,

    /** Runs, with something to know first: slowdowns, a mode that fails. */
    PARTIAL,

    /** Does not usefully run. */
    BROKEN,

    /**
     * The game has multiplayer Emufii could carry, and nobody has tried it yet.
     *
     * Distinct from being absent from the database, and the distinction is the
     * point: an unlisted game says nothing at all, where this says "worth a go,
     * on your own head". It is what lets a library be swept once — marking
     * everything that *could* work — without anyone pretending to have played
     * it.
     */
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

/**
 * What is known about one game, in every region it was released in.
 *
 * [name] is for the tool and for a human reading the file; nothing in the app
 * matches on it. Matching is on [keys] alone — see `compatKeys` — because a
 * title is exactly the thing that changes with the language.
 */
data class CompatEntry(
    val name: String,
    val rating: CompatRating,
    /** One short line, shown where there is room for it. Optional. */
    val note: String? = null,
    val keys: List<String>
)

/**
 * The compatibility database, as the app holds it: a flat map from key to
 * verdict.
 *
 * Flattened on parse rather than searched on every tile. The library draws
 * hundreds of tiles per scroll and each one asks this question; walking a list
 * of entries per tile would be a lookup inside a draw loop for no reason.
 */
class CompatDb private constructor(
    private val byKey: Map<String, CompatEntry>
) {
    val size: Int get() = byKey.size

    /**
     * The verdict for a ROM, given the keys it answers to, or null when nothing
     * is known about it.
     *
     * The keys arrive most specific first — exact identifier before family — and
     * the first hit wins, so a rating aimed at one region beats the family's.
     * That is what makes it possible to say "this game is fine except the
     * Japanese dump" without splitting the entry in two.
     */
    fun ratingFor(keys: List<String>): CompatEntry? = keys.firstNotNullOfOrNull { byKey[it] }

    companion object {
        val EMPTY = CompatDb(emptyMap())

        /**
         * Parses the served document, skipping anything it cannot read.
         *
         * Entry by entry and never all-or-nothing: this file is edited by hand
         * as well as by the tool, and one malformed line must cost one game, not
         * the whole database. An unknown rating is skipped rather than defaulted
         * — defaulting would invent a verdict, which is the one thing a
         * compatibility list must never do.
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
                val entry = CompatEntry(
                    name = obj.optString("name").ifBlank { keys.first() },
                    rating = rating,
                    note = obj.optString("note").takeIf { it.isNotBlank() && it != "null" },
                    keys = keys
                )
                // First writer wins, so a duplicated key is a no-op rather than a
                // silent change of verdict depending on file order.
                for (key in keys) map.putIfAbsent(key, entry)
            }
            CompatDb(map)
        }.getOrDefault(EMPTY)
    }
}

/**
 * The database in force, for the tiles.
 *
 * A `CompositionLocal` rather than a parameter threaded through the screen: the
 * grid, the list and the carousel all draw the same tile, at three different
 * depths, and every one of them would have had to carry a value it does not use
 * itself. Empty by default, which is also the honest state before the first
 * fetch — no badge means "nothing known", never "it works".
 */
val LocalCompatDb = androidx.compose.runtime.staticCompositionLocalOf { CompatDb.EMPTY }
