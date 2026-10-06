package eu.emufii.app.ps2

import eu.emufii.app.compat.CompatEntry
import eu.emufii.app.psp.PspServerPick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Neither source is an API meant for us: any failure leaves the count null, shown as "No live count". */
object Ps2LiveCount {

    private val PSREWIRED = listOf(
        "https://api.psrewired.com/us/api/universes/players",
        "https://api.psrewired.com/eu/api/universes/players",
    )
    private const val PS2ONLINE = "https://ps2online.com"

    /** The serving resolvers, busiest first, in the base's order on a tie. */
    suspend fun rank(entry: CompatEntry?): List<PspServerPick> = coroutineScope {
        val picks = Ps2Revival.picks(entry?.servers.orEmpty())
        val psrewired = async(Dispatchers.IO) {
            entry?.psrewiredApps?.takeIf { it.isNotEmpty() }?.let { psrewiredPlayers(it) }
        }
        val ps2online = async(Dispatchers.IO) {
            entry?.ps2onlineRows?.takeIf { it.isNotEmpty() }?.let { ps2onlinePlayers(it) }
        }
        val counts = mapOf(
            Ps2Revival.servers.getValue("psrewired").host to psrewired.await(),
            Ps2Revival.servers.getValue("ps2online").host to ps2online.await(),
        )
        picks.map { PspServerPick(it.server, counts[it.server.host]) }
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<PspServerPick>> { it.value.players ?: -1 }.thenBy { it.index })
            .map { it.value }
    }

    /** Both regions answer separately; a region that fails still lets the other count. */
    private fun psrewiredPlayers(apps: List<Int>): Int? {
        val wanted = apps.toSet()
        val perRegion = PSREWIRED.map { url ->
            runCatching {
                val players = JSONArray(get(url))
                (0 until players.length()).count { players.optJSONObject(it)?.optInt("applicationId") in wanted }
            }.getOrNull()
        }
        return perRegion.filterNotNull().takeIf { it.isNotEmpty() }?.sum()
    }

    private fun ps2onlinePlayers(rows: List<String>): Int? = runCatching {
        val table = parseActivity(get(PS2ONLINE, browser = true))
        rows.mapNotNull { table[it] }.takeIf { it.isNotEmpty() }?.sum()
    }.getOrNull()

    /**
     * Game name to its "Online" column; "?" rows are left out. Keyed without the trailing
     * parenthetical, which the site renames freely ("(Status/Lobbies)" became "(PES.es)").
     */
    internal fun parseActivity(html: String): Map<String, Int> {
        val start = html.indexOf("Number of entries").takeIf { it >= 0 } ?: return emptyMap()
        return ROW.findAll(html.substring(start)).mapNotNull { m ->
            val name = clean(m.groupValues[1]).replace(SUFFIX, "").trim()
            val online = clean(m.groupValues[2]).toIntOrNull() ?: return@mapNotNull null
            name to online
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
    }

    private fun clean(cell: String) = cell.replace(TAG, "").replace("&amp;", "&")
        .replace("&#039;", "'").replace("&#39;", "'").replace("&apos;", "'").trim()

    private fun get(url: String, browser: Boolean = false): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 3000
            // ps2online.com answers 403 to clients that do not look like a browser.
            if (browser) setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Emufii")
        }
        return try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private val ROW = Regex("<tr[^>]*>\\s*<td[^>]*>(.*?)</td>\\s*<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("<[^>]+>")
    private val SUFFIX = Regex("\\s*\\([^)]*\\)\\s*$")
}
