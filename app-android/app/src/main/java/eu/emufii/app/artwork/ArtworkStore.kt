package eu.emufii.app.artwork

import android.content.Context
import androidx.core.content.edit
import eu.emufii.app.library.Rom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ArtworkStore(context: Context) {

    private val prefs = context.getSharedPreferences("artwork", Context.MODE_PRIVATE)

    private val lock = Mutex()

    suspend fun iconUrl(rom: Rom, apiKey: String): String? {
        val key = key(rom)

        // Before the API key: an icon already chosen stays if the key is removed.
        chosen(key)?.let { return it }

        if (apiKey.isBlank()) return null

        cached(key)?.let { return it.takeIf { url -> url.isNotBlank() } }

        return lock.withLock {
            cached(key)?.let { return@withLock it.takeIf { url -> url.isNotBlank() } }
            val found = SteamGridDb.iconUrl(rom.displayName, apiKey)
            remember(key, found)
            found
        }
    }

    fun chosen(key: String): String? =
        prefs.getString(PICK_PREFIX + key, null)?.takeIf { it.isNotBlank() }

    fun choose(rom: Rom, url: String) {
        prefs.edit { putString(PICK_PREFIX + key(rom), url) }
        bumpRevision()
    }

    fun clearChoice(rom: Rom) {
        prefs.edit { remove(PICK_PREFIX + key(rom)) }
        bumpRevision()
    }

    fun chosenFor(rom: Rom): String? = chosen(key(rom))

    /** URL, "" for searched-and-missed, null for never searched. */
    private fun cached(key: String): String? {
        val url = prefs.getString(URL_PREFIX + key, null) ?: return null
        if (url.isNotBlank()) return url
        val at = prefs.getLong(MISS_PREFIX + key, 0L)
        if (System.currentTimeMillis() - at > MISS_TTL_MS) return null
        return ""
    }

    private fun remember(key: String, url: String?) {
        prefs.edit {
            putString(URL_PREFIX + key, url ?: "")
            if (url == null) putLong(MISS_PREFIX + key, System.currentTimeMillis())
        }
    }

    fun key(rom: Rom): String = rom.sessionId ?: rom.displayName

    companion object {
        private val _revision = MutableStateFlow(0)
        val revision: StateFlow<Int> = _revision.asStateFlow()

        private fun bumpRevision() {
            _revision.value = _revision.value + 1
        }

        const val URL_PREFIX = "url:"
        const val MISS_PREFIX = "miss:"
        const val PICK_PREFIX = "pick:"
        const val MISS_TTL_MS = 7L * 24 * 60 * 60 * 1000 // one week
    }
}
