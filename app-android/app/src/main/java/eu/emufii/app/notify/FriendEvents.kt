package eu.emufii.app.notify

import eu.emufii.app.profile.FriendStatus

/** Only the start of something joinable interrupts: going offline is not an event. */
sealed interface FriendEvent {
    val code: String
    val name: String?

    data class CameOnline(override val code: String, override val name: String?) : FriendEvent

    data class StartedPlaying(
        override val code: String,
        override val name: String?,
        val game: String?
    ) : FriendEvent
}

data class SeenFriend(val online: Boolean, val game: String?)

fun friendEvents(
    previous: Map<String, SeenFriend>,
    current: Map<String, FriendStatus>,
    names: Map<String, String?> = emptyMap()
): List<FriendEvent> {
    val events = mutableListOf<FriendEvent>()

    for ((code, status) in current) {
        val before = previous[code] ?: continue
        if (!status.online) continue

        val name = names[code]
        val game = status.romTitle

        when {
            // Arriving straight into a game: one line, the one that says the most.
            !before.online && game != null -> events += FriendEvent.StartedPlaying(code, name, game)
            !before.online -> events += FriendEvent.CameOnline(code, name)
            game != null && game != before.game -> events += FriendEvent.StartedPlaying(code, name, game)
        }
    }

    return events
}

fun seenFrom(current: Map<String, FriendStatus>): Map<String, SeenFriend> =
    current.mapValues { (_, s) -> SeenFriend(online = s.online, game = s.romTitle) }
