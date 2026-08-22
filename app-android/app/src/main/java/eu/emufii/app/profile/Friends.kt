package eu.emufii.app.profile

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Someone you added by their code.
 *
 * [name] is the last pseudo the coordinator reported for them, kept so the list
 * still reads as names once everyone is offline. It starts null, you can add a
 * friend who has not opened the app in days, and there is nowhere to look their
 * name up until they do.
 */
data class Friend(
    val code: String,
    val name: String?,
    val addedAt: Long
) {
    /** `E7K2-9QM4-XR8T`, the code as it is shown and shared. */
    val displayCode: String get() = FriendCode.format(code)
}

/** What a friend is up to right now, as far as the coordinator knows. */
data class FriendStatus(
    val online: Boolean,
    val sessionCode: String? = null,
    val romTitle: String? = null,
    val romTitleId: String? = null,
    val players: Int = 0,
    val ready: Boolean = false
) {
    val inSession: Boolean get() = sessionCode != null

    companion object {
        val Offline = FriendStatus(online = false)
    }
}

sealed interface AddFriendResult {
    data class Added(val friend: Friend) : AddFriendResult

    /** Not a well-formed code, wrong length, stray characters, or a typo the checksum caught. */
    data object Invalid : AddFriendResult

    data object AlreadyAdded : AddFriendResult

    /** Their own code. Harmless, but it would sit in the list forever showing them their own game. */
    data object Self : AddFriendResult
}

/**
 * The friends list, on this device and nowhere else.
 *
 * There is no server-side social graph: the coordinator is only ever asked
 * "which of these codes is online", and it answers from a table it forgets
 * every couple of minutes. Nobody can enumerate who is friends with whom,
 * because nobody stores it, and a feature with nothing to store is a feature
 * with nothing to pay for.
 *
 * The consequence to be honest about: this list does not follow the user to a
 * new phone.
 */
class FriendStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _friends = MutableStateFlow(load())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private fun load(): List<Friend> = runCatching {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val code = o.optString(FIELD_CODE).takeIf { FriendCode.isValid(it) } ?: return@mapNotNull null
            Friend(
                code = code,
                name = o.optString(FIELD_NAME).takeIf { it.isNotBlank() },
                addedAt = o.optLong(FIELD_ADDED_AT, 0L)
            )
        }
    }.getOrDefault(emptyList())

    private fun persist(list: List<Friend>) {
        val array = JSONArray()
        for (f in list) {
            array.put(
                JSONObject().apply {
                    put(FIELD_CODE, f.code)
                    if (f.name != null) put(FIELD_NAME, f.name)
                    put(FIELD_ADDED_AT, f.addedAt)
                }
            )
        }
        prefs.edit { putString(KEY_LIST, array.toString()) }
        _friends.value = list
    }

    fun add(input: String, selfCode: String, now: Long = System.currentTimeMillis()): AddFriendResult {
        val code = FriendCode.normalize(input) ?: return AddFriendResult.Invalid
        if (code == selfCode) return AddFriendResult.Self
        if (_friends.value.any { it.code == code }) return AddFriendResult.AlreadyAdded
        val friend = Friend(code = code, name = null, addedAt = now)
        persist(_friends.value + friend)
        return AddFriendResult.Added(friend)
    }

    fun remove(code: String) {
        persist(_friends.value.filterNot { it.code == code })
    }

    /**
     * Record the pseudo the coordinator just reported. Names change, and the
     * stored one is only ever a cache of the last time we saw them.
     */
    fun noteNames(names: Map<String, String>) {
        if (names.isEmpty()) return
        val updated = _friends.value.map { f ->
            val fresh = names[f.code]
            if (fresh != null && fresh != f.name) f.copy(name = fresh) else f
        }
        if (updated != _friends.value) persist(updated)
    }

    fun clear() = persist(emptyList())

    private companion object {
        const val PREFS = "emufii_friends"
        const val KEY_LIST = "list"
        const val FIELD_CODE = "code"
        const val FIELD_NAME = "name"
        const val FIELD_ADDED_AT = "added_at"
    }
}
