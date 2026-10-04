package eu.emufii.app.secondscreen

import eu.emufii.app.compat.CompatRating
import eu.emufii.app.library.Console
import eu.emufii.app.library.Rom
import eu.emufii.app.library.RomTags
import eu.emufii.app.meta.GameMeta
import eu.emufii.app.session.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SecondScreen {
    private val _base = MutableStateFlow<SecondScreenModel>(SecondScreenModel.Idle)

    private val asides = mutableListOf<Pair<Any, SecondScreenModel>>()

    private val _aside = MutableStateFlow<SecondScreenModel?>(null)

    val aside: StateFlow<SecondScreenModel?> = _aside.asStateFlow()

    /** Recomputed on every write: `combine` would want a scope on `Dispatchers.Main`. */
    private val _model = MutableStateFlow<SecondScreenModel>(SecondScreenModel.Idle)
    val model: StateFlow<SecondScreenModel> = _model.asStateFlow()

    private fun refresh() {
        _aside.value = asides.lastOrNull()?.second
        _model.value = _aside.value ?: _base.value
    }

    @Synchronized
    fun putAside(model: SecondScreenModel): Any {
        val token = Any()
        asides += token to model
        refresh()
        return token
    }

    @Synchronized
    fun takeBack(token: Any) {
        if (asides.removeAll { it.first === token }) refresh()
    }

    /** In place: changing content must not jump a layer ahead of later ones. */
    @Synchronized
    fun updateAside(token: Any, model: SecondScreenModel) {
        val at = asides.indexOfFirst { it.first === token }
        if (at >= 0) {
            asides[at] = token to model
            refresh()
        }
    }

    private val _page = MutableStateFlow(PanelPage(null, 0))
    val page: StateFlow<PanelPage> = _page.asStateFlow()

    private val _gallery = MutableStateFlow<Int?>(null)
    val gallery: StateFlow<Int?> = _gallery.asStateFlow()

    @Volatile
    var galleryCount: Int = 0

    fun openGallery(): Boolean {
        if (_page.value.index != 1 || galleryCount == 0 || _gallery.value != null) return false
        _gallery.value = 0
        return true
    }

    fun closeGallery() {
        _gallery.value = null
    }

    fun moveGallery(step: Int) {
        val at = _gallery.value ?: return
        _gallery.value = (at + step).coerceIn(0, (galleryCount - 1).coerceAtLeast(0))
    }

    fun publish(model: SecondScreenModel) {
        if (!sameGame(_base.value, model)) {
            _page.value = PanelPage((model as? SecondScreenModel.Browsing)?.rom?.uri?.toString(), 0)
            _gallery.value = null
        }
        _base.value = model
        refresh()
    }

    fun flipPage() {
        _gallery.value = null
        if (_base.value is SecondScreenModel.Browsing) _page.value = _page.value.let { it.copy(index = 1 - it.index) }
    }

    val friendsFocus = MutableStateFlow<FriendsFocus>(FriendsFocus.None)

    private val _steps = MutableStateFlow<List<PanelStep>>(emptyList())
    val steps: StateFlow<List<PanelStep>> = _steps.asStateFlow()

    /** The publisher must clear these on the way out, or the panel keeps a dead session under the finger. */
    fun publishSteps(steps: List<PanelStep>) {
        _steps.value = steps
        _stepCursor.value = _stepCursor.value?.coerceIn(0, (steps.lastIndex).coerceAtLeast(0))
    }

    /** Focus does not cross windows: a virtual cursor each screen reads for itself. */
    private val _stepCursor = MutableStateFlow<Int?>(null)
    val stepCursor: StateFlow<Int?> = _stepCursor.asStateFlow()

    fun selectStep(index: Int) {
        val steps = _steps.value
        if (steps.isEmpty()) return
        val from = _stepCursor.value
        val wanted = index.coerceIn(0, steps.lastIndex)
        if (steps[wanted].enabled) {
            _stepCursor.value = wanted
            return
        }
        val step = if (from != null && wanted < from) -1 else 1
        var i = wanted + step
        while (i in steps.indices) {
            if (steps[i].enabled) {
                _stepCursor.value = i
                return
            }
            i += step
        }
        if (from == null) {
            steps.indexOfFirst { it.enabled }.takeIf { it >= 0 }?.let { _stepCursor.value = it }
        }
    }

    fun moveStep(delta: Int) {
        val index = _stepCursor.value ?: return
        selectStep(index + delta)
    }

    fun clearStepCursor() {
        _stepCursor.value = null
    }

    @Synchronized
    fun clear() {
        _base.value = SecondScreenModel.Idle
        refresh()
        _page.value = PanelPage(null, 0)
        _steps.value = emptyList()
        _stepCursor.value = null
    }

    /** Clears only its own face: the old grid is disposed after the new one has already published. */
    @Synchronized
    fun clearIfShowing(model: SecondScreenModel?) {
        if (model != null && _base.value === model) clear()
    }

    /** Same game, not equal: late facts must not snap an open second page shut. */
    private fun sameGame(before: SecondScreenModel, after: SecondScreenModel): Boolean =
        before is SecondScreenModel.Browsing && after is SecondScreenModel.Browsing &&
            before.rom.uri == after.rom.uri
}

data class PanelFriend(
    val code: String,
    val name: String,
    val line: String,
    val online: Boolean,
    val inSession: Boolean,
    /** The panel confirms on its own side, where the finger just pressed. */
    val onRemove: () -> Unit = {},
    val onJoin: (() -> Unit)? = null,
    val avatar: java.io.File? = null,
    val game: eu.emufii.app.network.LastGame? = null,
    /** Resolved on the front: the panel's window has no game database. */
    val gameShot: String? = null,
)

sealed interface FriendsFocus {
    data object None : FriendsFocus
    data class Mine(val code: String) : FriendsFocus
    data class Friend(val code: String) : FriendsFocus
}

/** A name, not a composable, which would retain the tree that created it. */
enum class PanelMark {
    PROFILE, LIBRARY, CONSOLES, EMULATORS, APPEARANCE, GENERAL, ABOUT, CRASH_LOGS,

    SEARCH, LAYOUT, SORT, SESSIONS, FRIENDS,
}

data class PanelStep(
    /** Already translated: the panel window has its own display context. */
    val label: String,
    val done: Boolean,
    val enabled: Boolean,
    val onPress: () -> Unit,
    val busy: Boolean = false,
    val waiting: Boolean = false,
)

sealed interface SecondScreenModel {

    data object Idle : SecondScreenModel

    /** The whole [Rom] travels, so both screens resolve artwork from one cache. */
    data class Browsing(
        val rom: Rom,
        val rating: CompatRating? = null,
        /** Passed rather than computed: a cursor moves ten times a second. */
        val tags: RomTags = RomTags(),
        val meta: GameMeta? = null,
    ) : SecondScreenModel

    data class ConsoleFolder(val console: Console) : SecondScreenModel

    data class SettingsEntry(
        val title: String,
        val summary: String,
        val root: String,
        val mark: PanelMark,
        val social: Boolean = false,
    ) : SecondScreenModel

    data class Asking(
        val title: String,
        val detail: String,
        val social: Boolean = false,
    ) : SecondScreenModel

    /** So the panel never claims a key that is inert. */
    val legend: PadLegend
        get() = when (this) {
            // Nothing under the cursor: the legend used to offer Open over an empty screen.
            is Idle -> PadLegend()
            is Browsing -> PadLegend.BROWSING
            is ConsoleFolder, is SettingsEntry, is Asking -> PadLegend.FOLDER
            is Friends -> PadLegend()
            is InSession -> PadLegend.IN_SESSION
        }

    data class Friends(
        val entries: List<PanelFriend>,
        val focus: FriendsFocus = FriendsFocus.None,
    ) : SecondScreenModel

    data class InSession(
        val code: String,
        val role: Session.Role,
        val console: Console?,
        val gameTitle: String?,
        /** The clipboard carries one at a time and the emulator's dialog wants both. */
        val hostAddress: String? = null,
        val port: String? = null,
    ) : SecondScreenModel
}

data class PanelPage(val rom: String?, val index: Int)
