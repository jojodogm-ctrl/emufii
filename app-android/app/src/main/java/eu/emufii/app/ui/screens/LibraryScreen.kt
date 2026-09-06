package eu.emufii.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.emufii.app.R
import eu.emufii.app.artwork.rememberTileArt
import eu.emufii.app.compat.LocalCompatDb
import eu.emufii.app.library.Backend
import eu.emufii.app.library.Console
import eu.emufii.app.library.LibraryLayout
import eu.emufii.app.library.LibrarySort
import eu.emufii.app.library.Rom
import eu.emufii.app.library.compatKeys
import eu.emufii.app.profile.Profile
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.secondscreen.PanelMark
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.SecondScreenModel
import eu.emufii.app.secondscreen.rememberPresentationDisplay
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.LocalRingTone
import eu.emufii.app.ui.RingTone
import eu.emufii.app.ui.components.ChevronLeft
import eu.emufii.app.ui.components.CompatBadge
import eu.emufii.app.ui.components.FolderMark
import eu.emufii.app.ui.components.FriendsChip
import eu.emufii.app.ui.components.GameLaunchDialog
import eu.emufii.app.ui.components.HideRomDialog
import eu.emufii.app.ui.components.IconPickerDialog
import eu.emufii.app.ui.components.LayoutChip
import eu.emufii.app.ui.components.ProfileChip
import eu.emufii.app.ui.components.RenameRomDialog
import eu.emufii.app.ui.components.SearchChip
import eu.emufii.app.ui.components.SearchField
import eu.emufii.app.ui.components.SessionsChip
import eu.emufii.app.ui.components.SortChip
import eu.emufii.app.ui.components.TileMenu
import eu.emufii.app.ui.components.VpsLamp
import eu.emufii.app.ui.components.WallpaperVeil
import eu.emufii.app.ui.components.consoleArtwork
import eu.emufii.app.ui.components.tilePlate
import eu.emufii.app.ui.focusRing
import eu.emufii.app.ui.gamepadClick
import eu.emufii.app.ui.screens.library.ConsoleBadge
import eu.emufii.app.ui.screens.library.ENTRANCE_WINDOW_MS
import eu.emufii.app.ui.screens.library.EXTRA_ROWS_AFTER
import eu.emufii.app.ui.screens.library.Entry
import eu.emufii.app.ui.screens.library.FolderTile
import eu.emufii.app.ui.screens.library.GRID_COLS_PORTRAIT
import eu.emufii.app.ui.screens.library.HEADER_GAP
import eu.emufii.app.ui.screens.library.HeaderSide
import eu.emufii.app.ui.screens.library.LibraryScreenState
import eu.emufii.app.ui.screens.library.LibraryUiState
import eu.emufii.app.ui.screens.library.LocalTileEntrance
import eu.emufii.app.ui.screens.library.MIN_ROWS
import eu.emufii.app.ui.screens.library.PlaceholderArtwork
import eu.emufii.app.ui.screens.library.PublishHovered
import eu.emufii.app.ui.screens.library.RomTile
import eu.emufii.app.ui.screens.library.RomsCarousel
import eu.emufii.app.ui.screens.library.SHELF_INSET
import eu.emufii.app.ui.screens.library.TILE_MIN_WIDTH_DP
import eu.emufii.app.ui.screens.library.TILE_TITLE_ROOM
import eu.emufii.app.ui.screens.library.TileAction
import eu.emufii.app.ui.screens.library.entryKeys
import eu.emufii.app.ui.screens.library.paletteFor
import eu.emufii.app.ui.screens.library.rememberConfirmHold
import eu.emufii.app.ui.screens.library.rememberLibraryScreenState
import eu.emufii.app.ui.sounded
import eu.emufii.app.ui.tap
import eu.emufii.app.ui.tapOrHold
import eu.emufii.app.ui.theme.ArtworkShape
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.TileShape
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.wallpaper.TrayBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    profile: Profile,
    onOpenProfile: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenFinder: () -> Unit,
    onCreate: (Rom, private: Boolean) -> Unit,
    onJoinWith: (Rom) -> Unit,
    /**
     * Open a game straight into its console's public multiplayer, no session,
     * no tunnel. PSP only; see `PHASE1_SCOUT_PPSSPP_ONLINE.md`.
     */
    onPlayPublic: (Rom) -> Unit,
    onFolderPicked: (Uri) -> Unit,
    libraryRevision: Int
) {
    val dark = LocalEmufiiDarkTheme.current
    val state = rememberLibraryScreenState()
    val ui by state.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings = remember(context) { SettingsStore.get(context) }

    val topBarLeftFocus = remember { FocusRequester() }
    val topBarFocus = remember { FocusRequester() }
    fun headerFocus(side: HeaderSide) =
        if (side == HeaderSide.LEFT) topBarLeftFocus else topBarFocus

    val gridFocus = remember { FocusRequester() }

    LibraryEffects(
        ui = ui,
        state = state,
        gridFocus = gridFocus,
        libraryRevision = libraryRevision,
        topBarLeftFocus = topBarLeftFocus,
    )

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) onFolderPicked(uri) }

    // Without it, entering a console was a one-way trip for anyone without a controller.
    BackHandler(enabled = ui.openConsole != null) { state.closeFolder() }
    // After the folder's, so back closes the search first: one layer at a time.
    BackHandler(enabled = ui.searchOpen) { state.closeSearch() }

    val onEntry: (Entry) -> Unit = { entry -> state.onEntry(entry) }

    // `IgnoringVisibility`: the loading screen hides the bars, and their return
    // must not re-measure the grid.
    val topInset = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues()
        .calculateTopPadding()
    val bottomInset = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
        .calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        val hazeState = rememberHazeState()
        // The blur source is wired only while something blurs, or the whole grid goes
        // through a full-screen render target for nobody. On `searchOpen`, a frame
        // ahead of the panel.
        // pourquoi : docs/decisions/performance-rendu.md § The blur source is only wired up when something blurs
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (ui.searchOpen) Modifier.hazeSource(hazeState) else Modifier)
        ) {
            TrayBackdrop(modifier = Modifier.fillMaxSize(), dark = dark)

            HandleState(
                ui = ui,
                state = state,
                onEntry = onEntry,
                onPickFolder = { folderPicker.launch(null) },
                onExitTop = { side -> runCatching { headerFocus(side).requestFocus() } },
                gridFocus = gridFocus,
                topInset = topInset,
                bottomInset = bottomInset,
            )

            // Inside the Haze source and after the grid: they trim it
            // rather than take room from it.
            // pourquoi : docs/decisions/bibliotheque.md § The veils, and why the launch card is where it is
            WallpaperVeil(band = topInset + 60.dp, dark = dark)
            // Just enough that the last row does not touch the screen edge while scrolling.
            WallpaperVeil(band = bottomInset + 14.dp, dark = dark, fromTop = false)
        }

        FloatingTopBar(
            profile = profile,
            layout = ui.layout,
            onPickLayout = settings::setLibraryLayout,
            sort = ui.sort,
            onPickSort = settings::setLibrarySort,
            openConsole = ui.openConsole,
            openConsoleCount = ui.entries.size,
            onLeaveFolder = { state.closeFolder() },
            searchOpen = ui.searchOpen,
            query = ui.query,
            onSearchOpen = { state.openSearch() },
            onQueryChange = { state.onQuery(it) },
            onSearchClose = { state.closeSearch() },
            onOpenProfile = onOpenProfile,
            onOpenFriends = onOpenFriends,
            onOpenFinder = onOpenFinder,
            topBarLeftFocus = topBarLeftFocus,
            topBarFocus = topBarFocus,
            // Down from the header leads to the grid: the keypad is the system's and is
            // not a cursor stop.
            onLeaveDown = { runCatching { gridFocus.requestFocus() } },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        )

        // OVERLAY : the launch card. Sibling of the Haze source (so it can
        // blur the grid) and last (so a modal covers the chrome).
        // pourquoi : docs/decisions/bibliotheque.md § The veils, and why the launch card is where it is
        ui.renameFor?.let { rom ->
            RenameRomDialog(
                rom = rom,
                // The name is applied when the repository builds the list: without a rebuild
                // the rename looks ignored until the next scan.
                onRenamed = {
                    state.rename(null)
                    state.refresh()
                },
                onDismiss = { state.rename(null) }
            )
        }

        ui.hideFor?.let { rom ->
            HideRomDialog(
                rom = rom,
                onHidden = {
                    state.hide(null)
                    state.refresh()
                },
                onDismiss = { state.hide(null) }
            )
        }

        ui.pickIconFor?.let { rom ->
            IconPickerDialog(
                rom = rom,
                apiKey = ui.artworkKey,
                onDismiss = { state.pickIcon(null) }
            )
        }

        ui.selected?.let { rom ->
            GameLaunchDialog(
                rom = rom,
                onDismiss = { state.clearSelection() },
                // Deliberately left up: nothing else publishes a screen until
                // the tunnel leg, so this spinner is what covers the wait.
                // pourquoi : docs/decisions/bibliotheque.md § The veils, and why the launch card is where it is
                onPrimary = { private -> onCreate(rom, private) },
                // DS online play has no session to create or join: each console dials the
                // revival server itself.
                onJoinWithCode =
                    if (rom.console.backend == Backend.MELONDS_WFC) null
                    else ({ state.clearSelection(); onJoinWith(rom) }),
                // The PSP's public ad hoc: a second kind of multiplayer, hence
                // its own button. (PS2's was set aside, see docs.)
                // pourquoi : docs/decisions/bibliotheque.md § The veils, and why the launch card is where it is
                onPlayOnline =
                    if (rom.console.backend == Backend.PPSSPP) ({ onPlayPublic(rom) })
                    else null
            )
        }
    }
}

/**
 * The main body of the library: the empty-state, the "scanning" splash, and the three
 * layouts. Kept out of [LibraryScreen] so the top-bar wiring and dialog stack it sits
 * inside stay readable at a glance.
 */
@Composable
private fun HandleState(
    ui: LibraryUiState,
    state: LibraryScreenState,
    onEntry: (Entry) -> Unit,
    onPickFolder: () -> Unit,
    onExitTop: (HeaderSide) -> Unit,
    gridFocus: FocusRequester,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    when {
        ui.folderUri == null -> EmptyState(
            title = stringResource(R.string.lib_no_folder_title),
            subtitle = stringResource(R.string.lib_no_folder_body),
            cta = stringResource(R.string.lib_choose_folder),
            onCta = onPickFolder,
            topPadding = topInset + 72.dp,
            bottomPadding = bottomInset + 24.dp
        )

        ui.loading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.lib_scanning),
                style = MaterialTheme.typography.titleMedium,
                // Straight on the wallpaper, so nothing supplies a
                // content colour and it would fall back to black.
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        else -> {
            val onMenuAction: (Rom, TileAction) -> Unit = { rom, action ->
                state.openMenu(null)
                when (action) {
                    TileAction.ICON -> state.pickIcon(rom)
                    TileAction.RENAME -> state.rename(rom)
                    TileAction.HIDE -> state.hide(rom)
                }
            }
            val contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                // Pushes the grid down rather than covering its first
                // row. The air after the header is [HEADER_GAP].
                // pourquoi : docs/decisions/bibliotheque.md § The veils, and why the launch card is where it is
                top = topInset + 72.dp,
                // Travel, not empty space: the last row must rise
                // fully into the usable area.
                // pourquoi : docs/decisions/bibliotheque.md § Whole rows, or nothing
                bottom = bottomInset + 88.dp
            )

            // No need to suppress it at startup: the grid composes behind the
            // loading screen and has settled by the time it clears.
            // pourquoi : docs/decisions/bibliotheque.md § The tiles' arrival is armed, then disarmed
            var arriving by remember(ui.openConsole, ui.revision) { mutableStateOf(true) }
            LaunchedEffect(ui.openConsole, ui.revision) {
                arriving = true
                delay(ENTRANCE_WINDOW_MS.milliseconds)
                arriving = false
            }

            CompositionLocalProvider(LocalTileEntrance provides arriving) {
                when (ui.layout) {
                    LibraryLayout.GRID -> RomsGrid(
                        entries = ui.entries,
                        onSelect = onEntry,
                        onLongPress = { state.openMenu(it) },
                        menuFor = ui.menuFor,
                        onMenuAction = onMenuAction,
                        onDismissMenu = { state.openMenu(null) },
                        onExitTop = onExitTop,
                        onBack = { state.closeFolder() },
                        canGoBack = ui.openConsole != null,
                        gridFocus = gridFocus,
                        contentPadding = contentPadding
                    )

                    LibraryLayout.CAROUSEL -> RomsCarousel(
                        entries = ui.entries,
                        onSelect = onEntry,
                        onLongPress = { state.openMenu(it) },
                        menuFor = ui.menuFor,
                        onMenuAction = onMenuAction,
                        onDismissMenu = { state.openMenu(null) },
                        onExitTop = onExitTop,
                        onBack = { state.closeFolder() },
                        canGoBack = ui.openConsole != null,
                        gridFocus = gridFocus,
                        contentPadding = contentPadding
                    )

                    LibraryLayout.LIST -> RomsList(
                        entries = ui.entries,
                        onSelect = onEntry,
                        onLongPress = { state.openMenu(it) },
                        menuFor = ui.menuFor,
                        onMenuAction = onMenuAction,
                        onDismissMenu = { state.openMenu(null) },
                        onExitTop = onExitTop,
                        onBack = { state.closeFolder() },
                        canGoBack = ui.openConsole != null,
                        gridFocus = gridFocus,
                        contentPadding = contentPadding
                    )
                }
            }
        }
    }
}

/**
 * The three cross-cutting `LaunchedEffect`s that outlive any single UI section:
 * propagating the parent's rescan lever, snapping focus back to the grid when the launch
 * card closes, and jumping to the search field when it opens.
 */
@Composable
private fun LibraryEffects(
    state: LibraryScreenState,
    ui: LibraryUiState,
    libraryRevision: Int,
    gridFocus: FocusRequester,
    topBarLeftFocus: FocusRequester,
) {
    // The parent bumps `libraryRevision` when the settings screen rescans; the state
    // holder already scanned on init, so we skip the very first fire.
    val firstRevision = remember { mutableStateOf(true) }
    LaunchedEffect(libraryRevision) {
        if (firstRevision.value) firstRevision.value = false
        else state.refresh()
    }

    // Without it the cursor went up into the top bar, and you came back down by hand.
    LaunchedEffect(ui.selected) {
        if (ui.selected == null) runCatching { gridFocus.requestFocus() }
    }

    LaunchedEffect(ui.searchOpen) {
        if (ui.searchOpen) runCatching { topBarLeftFocus.requestFocus() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RomsGrid(
    entries: List<Entry>,
    onSelect: (Entry) -> Unit,
    onLongPress: (Rom) -> Unit,
    menuFor: Rom?,
    onMenuAction: (Rom, TileAction) -> Unit,
    onDismissMenu: () -> Unit,
    onExitTop: (HeaderSide) -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean,
    gridFocus: FocusRequester,
    contentPadding: PaddingValues
) {
    val localWindowInfo = LocalWindowInfo.current
    val landscape = localWindowInfo.containerSize.width > localWindowInfo.containerSize.height
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Whole rows, or none: leftover height goes to the *top* padding, never
        // the bottom, which is travel and only exists once scrolled.
        // pourquoi : docs/decisions/bibliotheque.md § Whole rows, or nothing
        val gutter = 18.dp
        val rowGap = 24.dp
        val topPad = contentPadding.calculateTopPadding()
        // Insets read ignoring visibility: the splash hides the bars then restores
        // them, and the grid went from six columns to seven under the player's eyes.
        // pourquoi : docs/decisions/bibliotheque.md § The insets are read "ignoring visibility"
        // pourquoi : docs/decisions/bibliotheque.md § Whole rows, or nothing
        val bottomLimit = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
            .calculateBottomPadding() + 14.dp
        val available = maxHeight - topPad - bottomLimit

        // Tile size comes from the height too, not width alone. Never more than
        // three extra columns: past that the covers stop being recognisable.
        // pourquoi : docs/decisions/bibliotheque.md § Whole rows, or nothing
        fun cellFor(c: Int) = (maxWidth - 40.dp - gutter * (c - 1)) / c
        fun rowFor(c: Int) = cellFor(c) + 8.dp + TILE_TITLE_ROOM
        val wantRows = if (landscape) 2 else 3
        val widthCols = if (landscape) {
            max(
                GRID_COLS_PORTRAIT,
                (localWindowInfo.containerSize.width - 40) / (TILE_MIN_WIDTH_DP + 18)
            )
        } else {
            GRID_COLS_PORTRAIT
        }
        var cols = widthCols
        while (
            cols < widthCols + 3 &&
            rowFor(cols) * wantRows + rowGap * (wantRows - 1) > available
        ) cols++

        val rowHeight = rowFor(cols)
        val wholeRows = ((available + rowGap) / (rowHeight + rowGap)).toInt().coerceAtLeast(1)
        // A floor of [HEADER_GAP], never an addition to it: adding both pushed the
        // tray down 14 dp for nothing.
        // pourquoi : docs/decisions/bibliotheque.md § The air under the header is named, no longer left to chance
        val slack = (available - (rowHeight * wholeRows + rowGap * (wholeRows - 1)))
            .coerceIn(0.dp, 20.dp)
            .coerceAtLeast(HEADER_GAP)


        // The grid's ONE column count: the cursor moves by ±columns and reads this
        // and nothing else. Two counts is the whole bug family this screen ended.
        // pourquoi : docs/decisions/bibliotheque.md § Whole rows, or nothing
        val columns = cols

        val rowsFromEntries = if (entries.isEmpty()) 0 else (entries.size + columns - 1) / columns
        val totalRows = max(if (landscape) 2 else MIN_ROWS, rowsFromEntries + EXTRA_ROWS_AFTER)
        val totalSlots = totalRows * columns

        val gridState = rememberLazyGridState()
        val scope = rememberCoroutineScope()
        val marginPx = marginPx()

        /**
         * An index we compute ourselves, so it cannot get lost with a component.
         * pourquoi : docs/decisions/bibliotheque.md § The cursor is a computed index, never a guessed focus
         */
        // The state, not its value: reading `cursor` in a composable body subscribes it.
        // pourquoi : docs/decisions/bibliotheque.md § What the tile reads must change only for it
        val cursorState = rememberSaveable { mutableIntStateOf(0) }
        var cursor by cursorState
        val padFocusedState = remember { mutableStateOf(false) }
        var padFocused by padFocusedState
        PublishHovered(entries, cursorState)

        // A rescan, or entering a folder, can shorten the list under the cursor.
        LaunchedEffect(entries.size) {
            if (cursor > entries.lastIndex) cursor = entries.lastIndex.coerceAtLeast(0)
        }

        LaunchedEffect(Unit) { runCatching { gridFocus.requestFocus() } }

        // The tile menu's window takes focus; without this nobody holds it on closing and
        // directions do nothing until you touch the screen.
        LaunchedEffect(menuFor) {
            if (menuFor == null) runCatching { gridFocus.requestFocus() }
        }

        /**
         * Compose stops at the first visible pixel, which is not the same thing.
         * pourquoi : docs/decisions/bibliotheque.md § Bring the target, not merely make it "visible"
         */
        fun reveal(index: Int) {
            scope.launch {
                val info = gridState.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                if (item == null) {
                    // The offset lifts the row under the band rather than pinning it to the edge.
                    gridState.animateScrollToItem(index, -info.beforeContentPadding)
                    return@launch
                }
                val top = item.offset.y
                val bottom = top + item.size.height
                // Both edges are read in `item.offset.y`'s frame, which counts from the
                // start of the content, so the top edge is zero.
                // pourquoi : docs/decisions/bibliotheque.md § Bringing the target: both edges are read in the same frame
                val safeTop = info.viewportStartOffset + info.beforeContentPadding
                val safeBottom = info.viewportEndOffset - info.afterContentPadding
                // The targeted tile is scaled up 7 % and carries a glow: it spills past its
                // own layout bounds, and the exact pixel clips it.

                val delta = when {
                    top < safeTop + marginPx -> top - safeTop - marginPx
                    bottom > safeBottom - marginPx -> bottom - safeBottom + marginPx
                    else -> 0
                }
                if (delta != 0) gridState.animateScrollBy(delta.toFloat())
            }
        }

        fun moveTo(index: Int): Boolean {
            if (index !in entries.indices) return false
            cursor = index
            reveal(index)
            return true
        }

        val hold = rememberConfirmHold()
        val onKey = entryKeys(
            entries = entries,
            cursorIndex = { cursor },
            onSelect = onSelect,
            onLongPress = onLongPress,
            onBack = onBack,
            canGoBack = canGoBack,
            hold = hold
        ) { key ->
            when (key) {
                Key.DirectionLeft -> moveTo(cursor - 1)
                Key.DirectionRight -> moveTo(cursor + 1)
                Key.DirectionDown -> moveTo(cursor + columns)
                Key.DirectionUp ->
                    if (cursor < columns) {
                        // Named destination, and named per column: sibling layers
                        // in one Box have no automatic path between them.
                        // pourquoi : docs/decisions/bibliotheque.md § Leaving through the top is named, and depends on the column
                        onExitTop(
                            if (cursor % columns < columns / 2) HeaderSide.LEFT
                            else HeaderSide.RIGHT
                        )
                        true
                    } else {
                        moveTo(cursor - columns)
                    }

                else -> null
            }
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                top = topPad + slack,
                bottom = contentPadding.calculateBottomPadding()
            ),
            horizontalArrangement = Arrangement.spacedBy(gutter),
            verticalArrangement = Arrangement.spacedBy(rowGap),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(gridFocus)
                .onFocusChanged { padFocused = it.hasFocus }
                .focusable()
                .onPreviewKeyEvent(onKey)
        ) {
            items(count = totalSlots, key = { it }) { i ->
                val entry = entries.getOrNull(i)
                // A derived state: reading `cursor` here would subscribe all fourteen tiles
                // on screen, and one step would recompose them all.
                // pourquoi : docs/decisions/bibliotheque.md § What the tile reads must change only for it
                val selected = remember(i) {
                    derivedStateOf { padFocusedState.value && i == cursorState.intValue }
                }
                val held = remember(i) { derivedStateOf { hold.down && i == cursorState.intValue } }
                when (entry) {
                    null -> EmptySlot()
                    is Entry.Folder -> FolderTile(
                        folder = entry,
                        onClick = { onSelect(entry) },
                        selected = selected.value,
                        padHeld = held.value
                    )

                    is Entry.Game -> RomTile(
                        rom = entry.rom,
                        onClick = { onSelect(entry) },
                        onLongClick = { onLongPress(entry.rom) },
                        // Told rather than asking: a tile destroyed on leaving the screen
                        // cannot take the selection with it.
                        selected = selected.value,
                        padHeld = held.value,
                        menuOpen = menuFor?.uri == entry.rom.uri,
                        onChangeIcon = { onMenuAction(entry.rom, TileAction.ICON) },
                        onRename = { onMenuAction(entry.rom, TileAction.RENAME) },
                        onHide = { onMenuAction(entry.rom, TileAction.HIDE) },
                        onDismissMenu = onDismissMenu
                    )
                }
            }
        }
    }
}

/**
 * pourquoi : docs/decisions/bibliotheque.md § The list exists to tell two dumps of one game apart
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RomsList(
    entries: List<Entry>,
    onSelect: (Entry) -> Unit,
    onLongPress: (Rom) -> Unit,
    menuFor: Rom?,
    onMenuAction: (Rom, TileAction) -> Unit,
    onDismissMenu: () -> Unit,
    onExitTop: (HeaderSide) -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean,
    gridFocus: FocusRequester,
    contentPadding: PaddingValues
) {
    val marginPx = marginPx()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // The strip the bottom veil paints back over the list; layout does not know it.
    val veilPx = with(density) {
        (WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
            .calculateBottomPadding() + 14.dp).roundToPx()
    }
    val cursorState = rememberSaveable { mutableIntStateOf(0) }
    var cursor by cursorState
    val padFocusedState = remember { mutableStateOf(false) }
    var padFocused by padFocusedState
    PublishHovered(entries, cursorState)

    LaunchedEffect(entries.size) {
        if (cursor > entries.lastIndex) cursor = entries.lastIndex.coerceAtLeast(0)
    }
    LaunchedEffect(Unit) { runCatching { gridFocus.requestFocus() } }
    LaunchedEffect(menuFor) {
        if (menuFor == null) runCatching { gridFocus.requestFocus() }
    }

    /**
     * A margin for the glow, plus the band the bottom veil repaints.
     * pourquoi : docs/decisions/bibliotheque.md § Bring the target, not merely make it "visible"
     */
    fun reveal(index: Int) {
        scope.launch {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
            if (item == null) {
                listState.animateScrollToItem(index, -info.beforeContentPadding)
                return@launch
            }
            val top = item.offset
            val bottom = top + item.size
            val safeTop = info.beforeContentPadding + marginPx
            val safeBottom = info.viewportEndOffset - info.viewportStartOffset -
                    info.afterContentPadding - marginPx - veilPx

            // Aim at the centre of the band, not merely inside it: one row per
            // press, with as much list ahead as behind. Both ends clamp.
            // pourquoi : docs/decisions/bibliotheque.md § Bring the target, not merely make it "visible"
            val centre = (safeTop + safeBottom) / 2
            val delta = (top + bottom) / 2 - centre
            if (delta != 0) listState.animateScrollBy(delta.toFloat())
        }
    }

    fun moveTo(index: Int): Boolean {
        if (index !in entries.indices) return false
        cursor = index
        reveal(index)
        return true
    }

    val hold = rememberConfirmHold()
    val onKey = entryKeys(
        entries = entries,
        cursorIndex = { cursor },
        onSelect = onSelect,
        onLongPress = onLongPress,
        onBack = onBack,
        canGoBack = canGoBack,
        hold = hold
    ) { key ->
        when (key) {
            Key.DirectionDown -> moveTo(cursor + 1)
            Key.DirectionUp ->
                if (cursor == 0) {
                    onExitTop(HeaderSide.RIGHT)
                    true
                } else {
                    moveTo(cursor - 1)
                }
            // A list has no columns: captured, so Compose does not go looking for a
            // focusable off screen.
            Key.DirectionLeft, Key.DirectionRight -> true
            else -> null
        }
    }

    LazyColumn(
        state = listState,
        // Added outright: a list has no slack to pour, and nothing else holds its first
        // plate off the header's pills.
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            top = contentPadding.calculateTopPadding() + HEADER_GAP,
            bottom = contentPadding.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(gridFocus)
            .onFocusChanged { padFocused = it.hasFocus }
            .focusable()
            .onPreviewKeyEvent(onKey)
    ) {
        items(count = entries.size, key = { entries[it].key }) { i ->
            val selected by remember(i) {
                derivedStateOf { padFocusedState.value && i == cursorState.intValue }
            }
            EntryRow(
                entry = entries[i],
                selected = selected,
                onClick = { onSelect(entries[i]) },
                onLongClick = { (entries[i] as? Entry.Game)?.let { onLongPress(it.rom) } },
                menuFor = menuFor,
                onMenuAction = onMenuAction,
                onDismissMenu = onDismissMenu
            )
        }
    }
}

@Composable
private fun marginPx(): Int {
    val density = LocalDensity.current
    return with(density) { 14.dp.roundToPx() }
}

@Composable
private fun EntryRow(
    entry: Entry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menuFor: Rom?,
    onMenuAction: (Rom, TileAction) -> Unit,
    onDismissMenu: () -> Unit
) {
    val context = LocalContext.current
    val dark = LocalEmufiiDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            // The ring FIRST, before anything that clips and before an opaque
            // fill: a glow is a shadow, and it draws through a see-through row.
            // pourquoi : docs/decisions/bibliotheque.md § The list exists to tell two dumps of one game apart
            .focusRing(selected, shape)
            .plate(
                shape = shape,
                dark = dark,
                oled = LocalEmufiiOledTheme.current,
                lift = if (selected) 7.dp else 3.dp
            )
            // Scaling a full-width row pushes its neighbours and makes the list jump.
            // Over the opaque face, so it tints the plate rather than showing through it.
            .then(
                if (selected) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape
                ) else Modifier
            )
            .focusProperties { canFocus = false }
            .tapOrHold(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .gamepadClick(interaction, onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(ArtworkShape)
                .background(tilePlate())
        ) {
            when (entry) {
                is Entry.Folder -> {
                    // The tiles' artwork at thumbnail size: names in text under a grid of
                    // logos read as two libraries.
                    val plate = consoleArtwork(entry.console, LocalEmufiiDarkTheme.current)
                    if (plate != null) {
                        Image(
                            painter = painterResource(plate),
                            contentDescription = entry.console.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(consolePlate(entry.console)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                entry.console.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                is Entry.Game -> {
                    val art by rememberTileArt(entry.rom)
                    if (art.model != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(art.model).build(),
                            contentDescription = entry.rom.displayName,
                            contentScale = if (art.fitsWhole) ContentScale.Fit else ContentScale.Crop,
                            filterQuality =
                                if (art.isPixelArt) FilterQuality.None else FilterQuality.High,
                            modifier = Modifier.fillMaxSize(),
                            placeholder = ColorPainter(Color.Transparent),
                            error = ColorPainter(Color.Transparent)
                        )
                    } else {
                        PlaceholderArtwork(entry.rom.displayName)
                    }

                    // A Popup takes the bounds of whatever contains it.
                    TileMenu(
                        expanded = menuFor?.uri == entry.rom.uri,
                        title = entry.rom.displayName,
                        changeIconLabel = stringResource(R.string.tile_menu_icon),
                        renameLabel = stringResource(R.string.tile_menu_rename),
                        hideLabel = stringResource(R.string.tile_menu_hide),
                        accent = entry.rom.accentArgb?.let { Color(it) },
                        onChangeIcon = { onMenuAction(entry.rom, TileAction.ICON) },
                        onRename = { onMenuAction(entry.rom, TileAction.RENAME) },
                        onHide = { onMenuAction(entry.rom, TileAction.HIDE) },
                        onDismiss = onDismissMenu
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                when (entry) {
                    is Entry.Folder -> entry.console.label
                    is Entry.Game -> entry.rom.displayName
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (entry is Entry.Folder) {
                Text(
                    gameCount(entry.roms.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (entry is Entry.Game) {
            LocalCompatDb.current.ratingFor(entry.rom.compatKeys())?.let { known ->
                CompatBadge(rating = known.rating, modifier = Modifier.padding(end = 8.dp))
            }
            ConsoleBadge(console = entry.rom.console, modifier = Modifier.padding(end = 4.dp))
        }
    }
}

/**
 * Indexed by name, so its colour never moves between launches.
 * pourquoi : docs/decisions/bibliotheque.md § The console folders
 */
@Composable
internal fun consolePlate(console: Console): Brush {
    val (c1, c2) = paletteFor(console.name)
    return Brush.linearGradient(colors = listOf(c1, c2), start = Offset.Zero, end = Offset.Infinite)
}

@Composable
internal fun gameCount(n: Int): String =
    if (n == 1) stringResource(R.string.lib_folder_count_one)
    else stringResource(R.string.lib_folder_count, n)

/**
 * Back and B do the same; this exists for the hand touching the screen.
 * pourquoi : docs/decisions/bibliotheque.md § The console folders
 */

@Composable
private fun FolderHeader(
    console: Console,
    count: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = LocalEmufiiDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = CircleShape

    Box(
        modifier = modifier
            .focusRing(focused, shape)
            .plate(shape = shape, dark = dark, oled = LocalEmufiiOledTheme.current, lift = 5.dp)
            .tap(interactionSource = interaction, indication = null, onClick = onBack)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            ChevronLeft(size = 18.dp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                console.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                gameCount(count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * What I am looking at on the left, who I am on the right.
 * pourquoi : docs/decisions/bibliotheque.md § The top bar: two shelves, never a bar
 */
@Composable
private fun FloatingTopBar(
    profile: Profile,
    layout: LibraryLayout,
    onPickLayout: (LibraryLayout) -> Unit,
    sort: LibrarySort,
    onPickSort: (LibrarySort) -> Unit,
    openConsole: Console?,
    openConsoleCount: Int,
    onLeaveFolder: () -> Unit,
    searchOpen: Boolean,
    query: String,
    onSearchOpen: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenFinder: () -> Unit,
    topBarLeftFocus: FocusRequester,
    topBarFocus: FocusRequester,
    onLeaveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The setting is not enough: the device may have only one screen.
    // pourquoi : docs/decisions/session.md § What the rear panel carries, the front screen does not repeat
    val context = LocalContext.current
    val panelDisplay by rememberPresentationDisplay()
    val panelWanted by remember(context) { SettingsStore.get(context).secondScreen }
        .collectAsStateWithLifecycle()
    val panelLive = panelWanted && panelDisplay != null

    var headerAside by remember { mutableStateOf<Any?>(null) }
    DisposableEffect(Unit) {
        onDispose { headerAside?.let { SecondScreen.takeBack(it) } }
    }

    /**
     * What the panel shows while the header holds the cursor. A top-bar pill is a 21 dp
     * drawing with no label and no tooltip; the panel shows it large, and nothing leaves
     * the front screen.
     * pourquoi : CLAUDE.md § Two screens: the single-screen layout stays the main one
     * pourquoi : docs/decisions/reglages-ecran.md § The hub is a grid, and the panel shows the selected cell
     */
    var headerFace by remember { mutableStateOf<SecondScreenModel?>(null) }

    /**
     * Not withdrawn at once: Compose takes focus off one pill before giving it to the
     * next, so the row, reading only `hasFocus`, saw a departure at every step and the
     * panel flicked back through its resting face. [HEADER_RELEASE_MS] is two orders of
     * magnitude above a focus handover.
     * pourquoi : docs/decisions/bibliotheque.md § The panel stops talking about the game when you leave the grid
     */
    var barFocused by remember { mutableStateOf(false) }
    LaunchedEffect(barFocused) {
        if (barFocused) {
            if (headerAside == null) {
                headerAside = SecondScreen.putAside(headerFace ?: SecondScreenModel.Idle)
            }
        } else {
            delay(HEADER_RELEASE_MS.milliseconds)
            headerAside?.let { SecondScreen.takeBack(it) }
            headerAside = null
            headerFace = null
        }
    }

    LaunchedEffect(headerFace, headerAside) {
        headerAside?.let { SecondScreen.updateAside(it, headerFace ?: SecondScreenModel.Idle) }
    }

    val root = stringResource(R.string.bar_root)
    fun chipFace(title: String, summary: String, mark: PanelMark, social: Boolean = false) =
        SecondScreenModel.SettingsEntry(
            title = title,
            summary = summary,
            root = root,
            mark = mark,
            social = social
        )

    /** Two pills cross: an unconditional null would erase the one that just arrived. */
    fun follow(face: SecondScreenModel) = { focused: Boolean ->
        if (focused) headerFace = face
    }

    val searchFace = chipFace(
        stringResource(R.string.lib_search),
        stringResource(R.string.bar_search_summary),
        PanelMark.SEARCH
    )
    val layoutFace = chipFace(
        stringResource(R.string.lib_layout),
        stringResource(R.string.bar_layout_summary),
        PanelMark.LAYOUT
    )
    val sortFace = chipFace(
        stringResource(R.string.lib_sort),
        stringResource(R.string.bar_sort_summary),
        PanelMark.SORT
    )
    // The three on the right are the social domain: the panel takes the same coral tint
    // as those screens' cursor.
    // pourquoi : docs/decisions/theme-duotone-shelves.md § Two semantic axes
    val sessionsFace = chipFace(
        stringResource(R.string.finder_title),
        stringResource(R.string.bar_sessions_summary),
        PanelMark.SESSIONS,
        social = true
    )
    val friendsFace = chipFace(
        stringResource(R.string.friends_title),
        stringResource(R.string.bar_friends_summary),
        PanelMark.FRIENDS,
        social = true
    )
    val profileFace = chipFace(
        playerDisplayName(profile.name),
        stringResource(R.string.bar_profile_summary),
        PanelMark.PROFILE,
        social = true
    )

    Row(
        // Named, like going up, and on the whole row: the left corner carries
        // buttons now, so one must be able to come down from there too.
        // pourquoi : docs/decisions/bibliotheque.md § Leaving through the top is named, and depends on the column
        modifier = modifier
            // The panel stops naming the game on leaving the grid, where its legend
            // began to lie. The resting face is laid over rather than published.
            // pourquoi : docs/decisions/bibliotheque.md § The panel stops talking about the game when you leave the grid
            .onFocusChanged { state -> barFocused = state.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onLeaveDown()
                    true
                } else {
                    false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // pourquoi : docs/decisions/bibliotheque.md § The top bar: two shelves, never a bar
        val shelfDark = LocalEmufiiDarkTheme.current
        // Beside the shelf, never on it: one more pill on the socket would read as a
        // fourth button. The group yields to the social shelf, the lamp first.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                // Yields rather than pushing the right off screen: the breadcrumb carries a
                // console name, not guaranteed to stay short.
                modifier = Modifier
                    .weight(1f, fill = false)
                    .socket(PillShape, shelfDark)
                    .animateContentSize()
                    .padding(SHELF_INSET)
            ) {
                // pourquoi : docs/decisions/bibliotheque.md § Search takes the shelf, and the two states do not cross
                androidx.compose.animation.AnimatedContent(
                    targetState = searchOpen,
                    transitionSpec = {
                        androidx.compose.animation.fadeIn(
                            tween(
                                durationMillis = 120,
                                delayMillis = 100,
                                easing = androidx.compose.animation.core.LinearOutSlowInEasing
                            )
                        ).togetherWith(
                            androidx.compose.animation.fadeOut(
                                tween(
                                    durationMillis = 100,
                                    easing = androidx.compose.animation.core.FastOutLinearInEasing
                                )
                            )
                        ).using(
                            androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                                androidx.compose.animation.core.snap()
                            }
                        )
                    },
                    label = "shelf-search-swap"
                ) { open ->
                    if (open) {
                        SearchField(
                            value = query,
                            onValueChange = onQueryChange,
                            onClose = onSearchClose,
                            modifier = Modifier.focusRequester(topBarLeftFocus)
                        )
                    } else {
                        // The 10.dp the right-hand shelf uses: with no arrangement, three
                        // pills sat touching while their opposite numbers breathed.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SearchChip(onClick = onSearchOpen, onFocused = follow(searchFace))
                            LayoutChip(
                                current = layout,
                                onPick = onPickLayout,
                                modifier = Modifier.focusRequester(topBarLeftFocus),
                                onFocused = follow(layoutFace)
                            )
                            SortChip(
                                current = sort,
                                onPick = onPickSort,
                                onFocused = follow(sortFace)
                            )
                        }
                    }
                }
                // Not a line of its own: a full-width band for three words pushed all three
                // layouts down by as much.
                // pourquoi : docs/decisions/bibliotheque.md § The console folders
                if (!searchOpen) openConsole?.let { console ->
                    FolderHeader(
                        console = console,
                        count = openConsoleCount,
                        onBack = onLeaveFolder
                    )
                }
            }
            // Hidden while the rear panel is lit: the only thing the two screens would
            // say word for word, a foot apart.
            // pourquoi : docs/decisions/bibliotheque.md § The service lamp goes out when the panel is lit
            if (!searchOpen && !panelLive) VpsLamp(dotSize = 10.dp)
        }
        // The cursor says the zone: every ring inside turns coral, the library's own
        // controls staying on the teal axis.
        // pourquoi : docs/decisions/theme-duotone-shelves.md § GAMEPAD FOCUS
        CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .socket(PillShape, shelfDark)
                    .padding(SHELF_INSET)
            ) {
                SessionsChip(
                    onClick = onOpenFinder,
                    modifier = Modifier.focusRequester(topBarFocus),
                    onFocused = follow(sessionsFace)
                )
                FriendsChip(onClick = onOpenFriends, onFocused = follow(friendsFace))
                ProfileChip(
                    profile = profile,
                    onClick = onOpenProfile,
                    onFocused = follow(profileFace)
                )
            }
        }
    }
}

/**
 * One destination, one pill.
 * pourquoi : docs/decisions/bibliotheque.md § The top bar: two shelves, never a bar
 */
@Composable
private fun EmptySlot() {
    val dark = LocalEmufiiDarkTheme.current
    // pourquoi : docs/decisions/bibliotheque.md § The top bar: two shelves, never a bar
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .socket(TileShape, dark)
        )
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun EmptyState(
    title: String,
    subtitle: String,
    cta: String,
    onCta: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding, start = 32.dp, end = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val dark = LocalEmufiiDarkTheme.current
        Box(
            modifier = Modifier
                .size(96.dp)
                .plate(
                    shape = CircleShape,
                    dark = dark,
                    oled = LocalEmufiiOledTheme.current,
                    lift = 6.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            FolderMark(size = 46.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = sounded(onCta), shape = RoundedCornerShape(50)) {
            Text(cta, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}


/** Long enough to cover a focus handover between pills, which takes a frame. */
private const val HEADER_RELEASE_MS = 120L