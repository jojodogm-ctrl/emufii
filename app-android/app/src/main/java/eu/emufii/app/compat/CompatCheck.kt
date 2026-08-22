package eu.emufii.app.compat

import android.content.Context
import eu.emufii.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Where the compatibility database comes from, and what happens when it does
 * not.
 *
 * Served, not shipped. A rating given today has to reach a player running a
 * build from three months ago, and baking the list into the APK would mean a
 * release per verdict. It rides the same road as `/latest`: an unauthenticated
 * GET of a static document on the coordinator, because it is the same public
 * fact for everybody and it carries no payload — a list of serials and three
 * words.
 *
 * **It is cached on disk, and the cache is what the library actually reads.**
 * The network answer only ever replaces the cache. Two reasons, and the second
 * matters more: the badges have to be there on the first frame of a cold start
 * rather than appearing a second later, and Emufii is used on handhelds that
 * are frequently offline — a compatibility list that vanishes without Wi-Fi
 * would be worse than none, because its absence reads as "this game is fine".
 */
object CompatCheck {

    private const val FILE = "compat.json"

    /** The cached copy, or an empty database. Never touches the network. */
    fun cached(context: Context): CompatDb = runCatching {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) CompatDb.EMPTY else CompatDb.parse(file.readText())
    }.getOrDefault(CompatDb.EMPTY)

    /**
     * Fetches the database and replaces the cache, returning what should now be
     * displayed.
     *
     * On any failure it returns the cache untouched. A server that is down, a
     * captive portal answering HTML, a truncated read: none of them are reasons
     * to forget what we already knew.
     */
    suspend fun refresh(
        context: Context,
        baseUrl: String = BuildConfig.COORDINATOR_BASE_URL
    ): CompatDb = withContext(Dispatchers.IO) {
        val fetched = runCatching {
            val conn = (URL("$baseUrl/compat").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
            }
            try {
                // 204 = nothing published yet, which is not an error and not a
                // reason to drop a cache from a server that used to publish.
                if (conn.responseCode != 200) return@runCatching null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull() ?: return@withContext cached(context)

        val parsed = CompatDb.parse(fetched)
        // Only a document we could actually read gets written. Overwriting a
        // working cache with something that parsed to nothing would turn a
        // server-side typo into every badge in the app disappearing.
        if (parsed.size > 0) {
            runCatching { File(context.filesDir, FILE).writeText(fetched) }
        }
        parsed.takeIf { it.size > 0 } ?: cached(context)
    }
}
