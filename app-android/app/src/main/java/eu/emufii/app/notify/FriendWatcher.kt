package eu.emufii.app.notify

import android.content.Context
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.profile.FriendStatus
import eu.emufii.app.profile.FriendStore
import eu.emufii.app.settings.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class FriendWatcher(context: Context, private val client: CoordinatorClient) {

    private val appContext = context.applicationContext
    private val store = FriendStore.get(appContext)
    private val state = WatchState(appContext)
    private val settings = SettingsStore.get(appContext)

    private val _statuses = MutableStateFlow<Map<String, FriendStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, FriendStatus>> = _statuses.asStateFlow()

    private val _lastGames = MutableStateFlow<Map<String, eu.emufii.app.network.LastGame>>(emptyMap())
    val lastGames: StateFlow<Map<String, eu.emufii.app.network.LastGame>> = _lastGames.asStateFlow()

    private val avatars = eu.emufii.app.profile.AvatarSync.get(appContext)

    // Replay one so an alert raised just before a screen change is not lost.
    private val _alerts = MutableSharedFlow<FriendEvent>(replay = 1, extraBufferCapacity = 8)
    val alerts: SharedFlow<FriendEvent> = _alerts.asSharedFlow()

    fun debugEmit(event: FriendEvent) {
        if (eu.emufii.app.BuildConfig.DEBUG) _alerts.tryEmit(event)
    }

    suspend fun run(codes: List<String>) {
        while (true) {
            poll(codes)
            delay(REFRESH_MS)
        }
    }

    private suspend fun poll(codes: List<String>) {
        if (codes.isEmpty()) {
            _statuses.value = emptyMap()
            return
        }

        val reply = client.friendStatuses(codes).getOrNull() ?: return
        val fresh = reply.present
        val current = codes.associateWith { code ->
            fresh[code]?.let {
                FriendStatus(
                    online = true,
                    sessionCode = it.sessionCode,
                    romTitle = it.romTitle,
                    romTitleId = it.romTitleId,
                    players = it.players,
                    ready = it.ready
                )
            } ?: FriendStatus.Offline
        }
        _statuses.value = current
        _lastGames.value = reply.lastGames
        runCatching { avatars.syncFriends(client, codes, reply.avatars) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }

        val known = store.friends.value.associate { it.code to it.name }
        val names = codes.associateWith { fresh[it]?.name ?: known[it] }
        store.noteNames(reply.names + fresh.mapNotNull { (c, p) -> p.name?.let { c to it } }.toMap())

        // Advance the memory even when alerts are off, or they come back as a burst.
        val events = friendEvents(state.seen(), current, names)
        state.setSeen(seenFrom(current))

        if (!settings.notifyFriends.value) return
        val onScreen = AppForeground.visible.value
        events.forEach { event ->
            if (onScreen) _alerts.tryEmit(event) else Notifications.friendEvent(appContext, event)
        }
    }

    private companion object {
        const val REFRESH_MS = 5_000L
    }
}
