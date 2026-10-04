package eu.emufii.app.ui.screens.library

import eu.emufii.app.compat.CompatDb
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

internal sealed interface Entry {
    val key: String

    data class Game(val rom: Rom) : Entry {
        override val key get() = rom.uri.toString()
    }

    data class Folder(val console: Console, val roms: List<Rom>) : Entry {
        override val key get() = "console:${console.name}"
    }
}

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
    val revision: Int = 0,
)

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
    private val _compat = MutableStateFlow(CompatDb.EMPTY)

    fun setCompat(db: CompatDb) {
        _compat.value = db
    }

    private val _filter = kotlinx.coroutines.flow.combine(
        settings.hiddenConsoles,
        settings.hideIncompatible,
        _compat,
    ) { consoles, hideBroken, db -> Triple(consoles, hideBroken, db) }

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
        _filter,
        settings.steamGridDbKey,
    ) { folderUri, roms, loading, selected, openConsole,
        searchOpen, query, menuFor, pickIconFor, renameFor,
        hideFor, sort, layout, filter, artworkKey ->
        val (hiddenConsoles, hideBroken, compat) = filter
        val shown = roms.filter { rom ->
            rom.console !in hiddenConsoles && !(hideBroken && compat.isBroken(rom))
        }
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
