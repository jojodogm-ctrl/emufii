package eu.emufii.app.secondscreen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PanelFeed {

    /** [id] lets a coroutine retire its own note without retiring the one that replaced it. */
    data class Note(
        val text: String,
        val kind: Kind,
        /** The friend event behind a [Kind.FRIEND] note, so the strip can draw its badge. */
        val event: eu.emufii.app.notify.FriendEvent? = null,
        val id: Long = nextId(),
    )

    enum class Kind { FRIEND, INFO }

    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note.asStateFlow()

    fun post(text: String, kind: Kind = Kind.INFO, event: eu.emufii.app.notify.FriendEvent? = null) {
        if (text.isBlank()) return
        _note.value = Note(text = text, kind = kind, event = event)
    }

    fun dismiss(id: Long) {
        if (_note.value?.id == id) _note.value = null
    }

    fun clear() {
        _note.value = null
    }

    private var counter = 0L
    @Synchronized private fun nextId(): Long = ++counter
}
