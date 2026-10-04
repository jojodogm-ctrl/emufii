package eu.emufii.app.notify

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

class WatchState(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun seen(): Map<String, SeenFriend> = runCatching {
        val raw = prefs.getString(KEY_SEEN, null) ?: return emptyMap()
        val o = JSONObject(raw)
        o.keys().asSequence().mapNotNull { code ->
            val entry = o.optJSONObject(code) ?: return@mapNotNull null
            code to SeenFriend(
                online = entry.optBoolean(FIELD_ONLINE, false),
                game = entry.optString(FIELD_GAME).takeIf { it.isNotBlank() }
            )
        }.toMap()
    }.getOrDefault(emptyMap())

    fun setSeen(seen: Map<String, SeenFriend>) {
        val o = JSONObject()
        seen.forEach { (code, s) ->
            o.put(
                code,
                JSONObject().apply {
                    put(FIELD_ONLINE, s.online)
                    s.game?.let { put(FIELD_GAME, it) }
                }
            )
        }
        prefs.edit { putString(KEY_SEEN, o.toString()) }
    }

    private companion object {
        const val PREFS = "emufii_watch"
        const val KEY_SEEN = "seen_friends"
        const val FIELD_ONLINE = "online"
        const val FIELD_GAME = "game"
    }
}
