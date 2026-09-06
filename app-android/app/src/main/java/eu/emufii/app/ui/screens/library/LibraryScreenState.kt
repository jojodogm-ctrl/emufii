package eu.emufii.app.ui.screens.library

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import eu.emufii.app.library.Console
import eu.emufii.app.library.GameTitles
import eu.emufii.app.library.LibraryLayout
import eu.emufii.app.library.LibrarySort
import eu.emufii.app.library.Rom
import eu.emufii.app.library.RomsRepository
import eu.emufii.app.library.byConsole
import eu.emufii.app.library.sortedFor
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.util.combineAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What a library cell holds: a game or a folder. Shared by the composable and the state
 * holder that computes the list.
 * pourquoi : docs/decisions/bibliotheque.md § Three layouts, one cursor contract
 */
internal sealed interface Entry {
    val key: String

    data class Game(val rom: Rom) : Entry {
        override val key get() = rom.uri.toString()
    }

    data class Folder(val console: Console, val roms: List<Rom>) : Entry {
        override val key get() = "console:${console.name}"
    }
}

/**
 * The library screen's flat, single-source view: the composable reads this and nothing
 * else. `entries` and `shown` are derived; every other field is owned by a
 * [MutableStateFlow] inside [LibraryScreenState] (or by [SettingsStore]).
 */
internal data class LibraryUiState(
    val folderUri: Uri? = null,
    val loading: Boolean = false,
    val entries: List<Entry> = emptyList(),
    val selected: Rom? = null,
    val openConsole: Console? = null,
    val searchOpen: Boolean = false,
    val query: String = "",
    val menuFor: Rom? = null,
    val pickIconFor: Rom? = null,
    val renameFor: Rom? = null,
    val hideFor: Rom? = null,
    val sort: LibrarySort = LibrarySort.CONSOLE,
    val layout: LibraryLayout = LibraryLayout.GRID,
    val hiddenConsoles: Set<Console> = emptySet(),
    val artworkKey: String = "",
    /**
     * Bumps on every manual refresh, so UI-only effects keyed on "the grid just
     * changed" (e.g. the tile entrance animation) can retrigger themselves.
     */
    val revision: Int = 0,
)

/**
 * Plain state-holder for [eu.emufii.app.ui.screens.LibraryScreen]. No ViewModel and no
 * DI — just [MutableStateFlow]s, an init block that runs the three orchestration
 * effects, and action methods the composable calls.
 *
 * pourquoi : docs/decisions/bibliotheque.md § Hidden consoles are hidden here, not in the scan
 */
internal class LibraryScreenState(
    private val context: Context,
    private val repo: RomsRepository,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    initialSearchOpen: Boolean = false,
    initialQuery: String = "",
) {

    private val _folderUri = MutableStateFlow(repo.savedFolderUri())
    private val _roms = MutableStateFlow<List<Rom>>(emptyList())
    private val _loading = MutableStateFlow(false)
    private val _selected = MutableStateFlow<Rom?>(null)
    private val _openConsole = MutableStateFlow<Console?>(null)
    private val _searchOpen = MutableStateFlow(initialSearchOpen)
    private val _query = MutableStateFlow(initialQuery)
    private val _menuFor = MutableStateFlow<Rom?>(null)
    private val _pickIconFor = MutableStateFlow<Rom?>(null)
    private val _renameFor = MutableStateFlow<Rom?>(null)
    private val _hideFor = MutableStateFlow<Rom?>(null)
    private val _revision = MutableStateFlow(0)

    val uiState: StateFlow<LibraryUiState> = combineAll(
        _folderUri,
        _roms,
        _loading,
        _selected,
        _openConsole,
        _searchOpen,
        _query,
        _menuFor,
        _pickIconFor,
        _renameFor,
        _hideFor,
        settings.librarySort,
        settings.libraryLayout,
        settings.hiddenConsoles,
        settings.steamGridDbKey,
    ) { folderUri, roms, loading, selected, openConsole,
        searchOpen, query, menuFor, pickIconFor, renameFor,
        hideFor, sort, layout, hiddenConsoles, artworkKey ->
        val shown = if (hiddenConsoles.isEmpty()) roms
        else roms.filter { it.console !in hiddenConsoles }
        val needle = query.trim()
        val entries: List<Entry> = when {
            needle.isNotEmpty() ->
                shown.filter { it.displayName.contains(needle, ignoreCase = true) }
                    .sortedFor(LibrarySort.NAME)
                    .map(Entry::Game)
            sort != LibrarySort.CONSOLE -> shown.sortedFor(sort).map(Entry::Game)
            openConsole != null ->
                shown.filter { it.console == openConsole }
                    .sortedFor(LibrarySort.NAME)
                    .map(Entry::Game)
            else -> shown.byConsole().map { (console, list) -> Entry.Folder(console, list) }
        }
        LibraryUiState(
            folderUri = folderUri,
            loading = loading,
            entries = entries,
            selected = selected,
            openConsole = openConsole,
            searchOpen = searchOpen,
            query = query,
            menuFor = menuFor,
            pickIconFor = pickIconFor,
            renameFor = renameFor,
            hideFor = hideFor,
            sort = sort,
            layout = layout,
            hiddenConsoles = hiddenConsoles,
            artworkKey = artworkKey,
            revision = _revision.value,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = LibraryUiState(
            folderUri = _folderUri.value,
            searchOpen = _searchOpen.value,
            query = _query.value,
        ),
    )

    init {
        // Rescan on folder or manual-refresh change. Never forced: the explicit rescan
        // already refreshed the cache. Also names encrypted dumps kept to themselves,
        // asked for by the ids they did give up.
        scope.launch {
            combine(_folderUri, _revision) { uri, _ -> uri }.collect { uri ->
                if (uri != null) {
                    _loading.value = true
                    val scanned = withContext(Dispatchers.IO) { repo.scan() }
                    _roms.value = scanned
                    _loading.value = false
                    if (GameTitles.refresh(context, scanned)) {
                        _roms.value = withContext(Dispatchers.IO) { repo.cachedOrScan() }
                    }
                } else {
                    _roms.value = emptyList()
                }
            }
        }

        // A sort that leaves CONSOLE has no folders to open into.
        scope.launch {
            settings.librarySort.collect { s ->
                if (s != LibrarySort.CONSOLE) _openConsole.value = null
            }
        }

        // A folder emptied by a rescan would be a blank screen with no way out.
        scope.launch {
            uiState.collect { s ->
                if (s.openConsole != null && s.entries.isEmpty()) _openConsole.value = null
            }
        }
    }

    fun onEntry(entry: Entry) {
        when (entry) {
            is Entry.Game -> _selected.value = entry.rom
            is Entry.Folder -> _openConsole.value = entry.console
        }
    }

    fun closeFolder() {
        _openConsole.value = null
    }

    fun clearSelection() {
        _selected.value = null
    }

    fun openSearch() {
        _searchOpen.value = true
    }

    fun closeSearch() {
        _searchOpen.value = false
        _query.value = ""
    }

    fun onQuery(query: String) {
        _query.value = query
    }

    fun openMenu(rom: Rom?) {
        _menuFor.value = rom
    }

    fun pickIcon(rom: Rom?) {
        _pickIconFor.value = rom
    }

    fun rename(rom: Rom?) {
        _renameFor.value = rom
    }

    fun hide(rom: Rom?) {
        _hideFor.value = rom
    }

    /**
     * Reloads the saved folder and forces a rescan. Called on init through the
     * `_revision` collector; the composable calls it whenever the parent's
     * `libraryRevision` changes so a settings-driven rescan still lands here.
     */
    fun refresh() {
        _folderUri.value = repo.savedFolderUri()
        _revision.value += 1
    }

    @Suppress("unused")
    fun onFolderChosen() {
        _folderUri.value = repo.savedFolderUri()
    }

    companion object {
        private const val KEY_SEARCH_OPEN = "libSearchOpen"
        private const val KEY_QUERY = "libQuery"

        fun saver(
            context: Context,
            repo: RomsRepository,
            settings: SettingsStore,
            scope: CoroutineScope,
        ): Saver<LibraryScreenState, Bundle> = Saver(
            save = { state ->
                Bundle().apply {
                    putBoolean(KEY_SEARCH_OPEN, state.uiState.value.searchOpen)
                    putString(KEY_QUERY, state.uiState.value.query)
                }
            },
            restore = { bundle ->
                LibraryScreenState(
                    context = context,
                    repo = repo,
                    settings = settings,
                    scope = scope,
                    initialSearchOpen = bundle.getBoolean(KEY_SEARCH_OPEN, false),
                    initialQuery = bundle.getString(KEY_QUERY, "") ?: "",
                )
            },
        )
    }
}

@Composable
internal fun rememberLibraryScreenState(): LibraryScreenState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(context) { RomsRepository.get(context) }
    val settings = remember(context) { SettingsStore.get(context) }
    return rememberSaveable(
        saver = LibraryScreenState.saver(context, repo, settings, scope),
    ) {
        LibraryScreenState(
            context = context,
            repo = repo,
            settings = settings,
            scope = scope,
        )
    }
}
