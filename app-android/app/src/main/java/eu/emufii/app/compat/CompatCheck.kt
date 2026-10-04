package eu.emufii.app.compat

import android.content.Context
import eu.emufii.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object CompatCheck {

    private const val FILE = "compat.json"

    fun cached(context: Context): CompatDb = runCatching {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) CompatDb.EMPTY else CompatDb.parse(file.readText())
    }.getOrDefault(CompatDb.EMPTY)

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
                // 204 means nothing published yet: keep the cache.
                if (conn.responseCode != 200) return@runCatching null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull() ?: return@withContext cached(context)

        val parsed = CompatDb.parse(fetched)
        // An empty parse must not wipe a working cache.
        if (parsed.size > 0) {
            runCatching { File(context.filesDir, FILE).writeText(fetched) }
        }
        parsed.takeIf { it.size > 0 } ?: cached(context)
    }
}
