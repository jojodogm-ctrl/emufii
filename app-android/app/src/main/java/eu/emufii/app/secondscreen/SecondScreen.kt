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

/**
 * What the second screen is showing, at process scope: a model held in a
 * composition dies with the composition.
 * pourquoi : docs/decisions/second-ecran.md § The panel's state lives process-wide, not in the composition
 */
object SecondScreen {
    private val _base = MutableStateFlow<SecondScreenModel>(SecondScreenModel.Idle)

    /**
     * Stacked so the layers need not know each other: each puts and removes its
     * own, and the one below comes back by itself.
     * pourquoi : docs/decisions/second-ecran.md § A stack rather than one more publication
     */
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

    /** Held here because the button that turns it is on the front screen. */
    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()

    fun publish(model: SecondScreenModel) {
        if (!sameGame(_base.value, model)) _page.value = 0
        _base.value = model
        refresh()
    }

    fun flipPage() {
        if (_base.value is SecondScreenModel.Browsing) _page.value = 1 - _page.value
    }

    /**
     * They travel already resolved: the panel's window has its own display context.
     * pourquoi : docs/decisions/second-ecran.md § What travels to the panel travels already resolved
     */
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

    /**
     * A locked step stays displayed and stops being a stop.
     * pourquoi : docs/decisions/second-ecran.md § The cursor only stops on a pressable step
     */
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

    /**
     * Does not empty the aside stack: the caller is a background publisher going
     * away, and the layers over it are not its to remove.
     * pourquoi : docs/decisions/second-ecran.md § A stack rather than one more publication
     */
    @Synchronized
    fun clear() {
        _base.value = SecondScreenModel.Idle
        refresh()
        _page.value = 0
        _steps.value = emptyList()
        _stepCursor.value = null
    }

    /** Same game, not equal: late facts must not snap an open second page shut. */
    private fun sameGame(before: SecondScreenModel, after: SecondScreenModel): Boolean =
        before is SecondScreenModel.Browsing && after is SecondScreenModel.Browsing &&
            before.rom.uri == after.rom.uri
}

/**
 * Already resolved.
 * pourquoi : docs/decisions/second-ecran.md § What travels to the panel travels already resolved
 */
data class PanelFriend(
    val name: String,
    val line: String,
    val online: Boolean,
    val inSession: Boolean,
    /** The panel confirms on its own side, where the finger just pressed. */
    val onRemove: () -> Unit = {},
)

/**
 * A name, not a composable, which would retain the tree that created it.
 * pourquoi : docs/decisions/second-ecran.md § What travels to the panel travels already resolved
 */
enum class PanelMark {
    PROFILE, LIBRARY, CONSOLES, EMULATORS, APPEARANCE, GENERAL, ABOUT, CRASH_LOGS,

    // The top bar's pills borrow marks already drawn rather than adding more.
    SEARCH, LAYOUT, SORT, SESSIONS, FRIENDS,
}

/**
 * They go to the back because it is touch, and the front screen keeps their height.
 * pourquoi : docs/decisions/second-ecran.md § The panel takes the steps, because it is touch
 */
data class PanelStep(
    /** Already translated: the panel window has its own display context. */
    val label: String,
    val done: Boolean,
    val enabled: Boolean,
    val onPress: () -> Unit,
)

/**
 * Deliberately few: a second screen that tries to be a second app is a second app
 * to maintain.
 * pourquoi : docs/decisions/second-ecran.md § What travels to the panel
 */
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

    /**
     * The panel shows large what the tile says small, and delegates nothing.
     * pourquoi : docs/decisions/reglages-ecran.md § The hub is a grid, and the panel shows the selected cell
     */
    data class SettingsEntry(
        val title: String,
        val summary: String,
        val root: String,
        val mark: PanelMark,
        val social: Boolean = false,
    ) : SecondScreenModel

    /**
     * Carries the question asked rather than a summary: this face exists to stop
     * showing something false, not to show something more.
     * pourquoi : docs/decisions/second-ecran.md § A panel that asserts something false is a fault
     */
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

    /**
     * The panel carries the whole list; the front screen keeps the two cards that
     * ask for something.
     * pourquoi : docs/decisions/second-ecran.md § The friends list goes to the back, both cards stay in front
     */
    data class Friends(
        val entries: List<PanelFriend>,
    ) : SecondScreenModel

    /**
     * Stays up while they play, where the front screen is covered by the emulator.
     * pourquoi : docs/decisions/second-ecran.md § The session code carries no label
     */
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
