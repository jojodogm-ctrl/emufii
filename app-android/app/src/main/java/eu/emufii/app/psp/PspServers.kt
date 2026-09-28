package eu.emufii.app.psp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** One entry of PPSSPP's own public ad hoc server list (`assets/adhoc-servers.json`). */
data class PspServer(
    val name: String,
    val host: String,
    val location: String,
    val flag: String,
    val description: String,
    val statusJson: String? = null,
    val statusXml: String? = null,
)

/** [players] is null when the server publishes no status, not when nobody is there. */
data class PspServerPick(val server: PspServer, val players: Int?)

/**
 * Picks where to play from who is already there: most servers publish, live, how many
 * players each game has, by disc ID. Servers are general-purpose; what differs is the
 * crowd, and a crowd is what a public lobby needs.
 */
object PspServers {

    /** PPSSPP's list, fetched fresh: servers come and go faster than our releases. */
    private const val LIST_URL =
        "https://raw.githubusercontent.com/hrydgard/ppsspp/master/assets/adhoc-servers.json"

    /** PPSSPP's `DefaultProAdhocServer()`, and the first of its list. */
    const val DEFAULT_HOST = "socom.cc"

    /** Offline or list unreachable: the busiest general servers of PPSSPP's list, 2026-09-28. */
    private val FALLBACK = listOf(
        PspServer("Socom Adhoc Server", "socom.cc", "France", "🇫🇷", "For players looking to play any games",
            statusXml = "https://www.socom.cc/status.xml"),
        PspServer("Madness Gaming Network", "psp.mgn.pub", "Alaska USA", "🇺🇸", "For players looking to play any games"),
        PspServer("EA Nation Hub", "eahub.eu", "France", "🇫🇷", "Mostly for Medal of Honor Heroes 2 & Need For Speed Most Wanted players",
            statusJson = "https://adhoc.eahub.eu/data.json"),
        PspServer("Psi-Hate", "psi-hate.com", "Minnesota USA", "🇺🇸", "For players looking to play any games",
            statusJson = "http://psi-hate.com:27315/data.json"),
    )

    /** The player's pick for the next online launch; null lets [rank]'s first win. */
    @Volatile
    var chosenHost: String? = null

    suspend fun rank(discId: String?, title: String): List<PspServerPick> = coroutineScope {
        val servers = fetchList() ?: FALLBACK
        val picks = servers.map { server ->
            async(Dispatchers.IO) { PspServerPick(server, playersOn(server, discId, title)) }
        }.awaitAll()
        // Most players first; unknown counts after known ones; PPSSPP's own order breaks ties.
        picks.withIndex().sortedWith(
            compareByDescending<IndexedValue<PspServerPick>> { it.value.players ?: -1 }
                .thenBy { it.index }
        ).map { it.value }
    }

    private suspend fun fetchList(): List<PspServer>? = withContext(Dispatchers.IO) {
        runCatching {
            val servers = JSONObject(get(LIST_URL)).getJSONArray("servers")
            (0 until servers.length()).mapNotNull { i ->
                val o = servers.optJSONObject(i) ?: return@mapNotNull null
                val host = o.optString("host").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PspServer(
                    name = o.optString("name").ifBlank { host },
                    host = host,
                    location = o.optString("location"),
                    flag = o.optString("location-emoji"),
                    description = o.optString("description"),
                    statusJson = o.optString("status_data_json").takeIf { it.startsWith("http") },
                    statusXml = o.optString("status_xml").takeIf { it.startsWith("http") },
                )
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun playersOn(server: PspServer, discId: String?, title: String): Int? = runCatching {
        server.statusJson?.let { url -> return@runCatching countJson(get(url), discId, title) }
        server.statusXml?.let { url -> return@runCatching countXml(get(url), title) }
        null
    }.getOrNull()

    internal fun countJson(json: String, discId: String?, title: String): Int {
        val games = JSONObject(json).optJSONArray("games") ?: return 0
        var total = 0
        for (i in 0 until games.length()) {
            val g = games.optJSONObject(i) ?: continue
            val ids = g.optJSONArray("game_ids")
            val byId = discId != null && ids != null &&
                (0 until ids.length()).any { ids.optString(it).equals(discId, ignoreCase = true) }
            if (byId || sameTitle(g.optString("name"), title)) {
                total += g.optString("usercount").toIntOrNull() ?: g.optInt("usercount")
            }
        }
        return total
    }

    /** The XML status carries names only, no disc ID. */
    internal fun countXml(xml: String, title: String): Int =
        Regex("<game name=\"([^\"]*)\" usercount=\"(\\d+)\"").findAll(xml)
            .filter { sameTitle(decode(it.groupValues[1]), title) }
            .sumOf { it.groupValues[2].toInt() }

    /** Loose on purpose: servers name games their own way ("PES 2013: Pro Evolution Soccer"). */
    internal fun sameTitle(a: String, b: String): Boolean {
        val x = fold(a)
        val y = fold(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x == y || x.contains(y) || y.contains(x))
    }

    private fun fold(s: String) = s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun decode(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 3000
        }
        return try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
