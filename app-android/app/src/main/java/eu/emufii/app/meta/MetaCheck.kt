package eu.emufii.app.meta

import android.content.Context
import eu.emufii.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object MetaCheck {

    private const val FILE = "meta.json"

    fun cached(context: Context): GameMetaDb = runCatching {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) GameMetaDb.EMPTY else GameMetaDb.parse(file.readText())
    }.getOrDefault(GameMetaDb.EMPTY)

    suspend fun refresh(
        context: Context,
        baseUrl: String = BuildConfig.COORDINATOR_BASE_URL
    ): GameMetaDb = withContext(Dispatchers.IO) {
        val fetched = runCatching {
            val conn = (URL("$baseUrl/meta").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 6000
            }
            try {
                // 204 means nothing published yet: keep the cache.
                if (conn.responseCode != 200) return@runCatching null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull() ?: return@withContext cached(context)

        val parsed = GameMetaDb.parse(fetched)
        if (parsed.size > 0) {
            runCatching { File(context.filesDir, FILE).writeText(fetched) }
        }
        parsed.takeIf { it.size > 0 } ?: cached(context)
    }
}
