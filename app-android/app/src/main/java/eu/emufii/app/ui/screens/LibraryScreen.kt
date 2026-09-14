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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.chrisbanes.haze.hazeSource
import eu.emufii.app.ui.LocalGlassPane
import eu.emufii.app.ui.glass
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.Backdrop
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
import eu.emufii.app.ui.components.ToolBank
import eu.emufii.app.ui.components.BankSeam
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
import androidx.compose.animation.SharedTransitionLayout
import eu.emufii.app.ui.LocalCardRom
import eu.emufii.app.ui.Motion
import eu.emufii.app.ui.LocalSharedMotion
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
import eu.emufii.app.ui.screens.library.TILE_MIN_WIDTH_DP
import eu.emufii.app.ui.screens.library.TILE_TITLE_ROOM
import eu.emufii.app.ui.screens.library.TILE_TITLE_DROP
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
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.Teal
import eu.emufii.app.ui.theme.TileShape
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.shelf
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
    /** How many friends are online; presence is polled once for the whole app. */
    onlineFriends: Int = 0,
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
    val folderFocus = remember { FocusRequester() }
    /**
     * In a folder, climbing out of the grid lands on the way back out, whichever column
     * you left from: it is the one thing you came up here for. Searching, the marker is
     * not composed and the shelf's own two ends take over again.
     * pourquoi : docs/decisions/bibliotheque.md § Leaving through the top is named, and depends on the column
     */
    fun headerFocus(side: HeaderSide) = when {
        ui.openConsole != null && !ui.searchOpen -> folderFocus
        side == HeaderSide.LEFT -> topBarLeftFocus
        else -> topBarFocus
    }

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

    // The grid and the launch card under one roof, so a cover can travel from the tile
    // to the card instead of one fading out while the other fades in. It wraps and
    // provides; nothing below it is obliged to use it.
    // pourquoi : docs/decisions/bibliotheque.md § The veils, and why the launch card is where it is
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
    CompositionLocalProvider(
        LocalSharedMotion provides this,
        LocalCardRom provides ui.selected?.uri,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        val hazeState = rememberHazeState()
        // The blur source is wired only while something blurs, or the whole grid goes
        // through a full-screen render target for nobody. On `searchOpen`, a frame
        // ahead of the panel.
        // pourquoi : docs/decisions/performance-rendu.md § The blur source is only wired up when something blurs
        // Measured rather than guessed, and read by the veil below.
        var shelfBottom by remember { mutableStateOf(0.dp) }
        // Raised by the grid once it has left the top: the shelf then gets out of the
        // way of the covers.
        val gridScrolled = remember { mutableFloatStateOf(0f) }
        // Hoisted, because the veil has to know it too: a band that kept the shelf's
        // height after the shelf had gone left a dead strip the size of a row.
        val barCursor = remember { mutableStateOf(false) }
        /**
         * How far the shelf is out of the way, 0 to 1. A lambda, not a value: read in
         * the composition it would recompose the whole chrome on every frame of every
         * scroll; read inside a draw lambda it only redraws.
         * pourquoi : docs/decisions/performance-rendu.md § One clock for everything that moves continuously
         */
        val shelfAway = { if (barCursor.value) 0f else gridScrolled.floatValue }
        // A list that is rebuilt, emptied or swapped for another layout starts at the
        // top again, and nothing else would ever lower the flag.
        LaunchedEffect(ui.openConsole, ui.revision, ui.layout) { gridScrolled.floatValue = 0f }
        val density = LocalDensity.current

        // What the header refracts: the tray and the grid, captured as a layer. The header
        // is their sibling, so it cannot read them without one.
        // pourquoi : docs/decisions/bibliotheque.md § The header is a pebble, and the game colours it
        val glass = rememberLayerBackdrop()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (ui.searchOpen) Modifier.hazeSource(hazeState) else Modifier)
                .layerBackdrop(glass)
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
                // Where the first row may rest: the shelf's measured edge, so the veil
                // and the grid agree on one number instead of each holding its own.
                contentTop = shelfBottom.takeIf { it > 0.dp } ?: (topInset + 72.dp),
                scrolled = gridScrolled,
            )

            // Inside the Haze source and after the grid: they trim it
            // rather than take room from it.
            // The band is the shelf's own bottom edge, measured, not a number that
            // happened to fit: at 60 dp the shelf overhung it and the first row of
            // covers was sliced in half under the chrome. And it travels with the
            // shelf: held at full height once the shelf had gone, it was a dead strip.
            // pourquoi : docs/decisions/bibliotheque.md § The veils, and why the launch card is where it is
            // Only the status bar, never the header's own band: the header is glass now and
            // has to see the grid to refract it. A band that reached under it would hand the
            // lens a flat colour and the effect would vanish where it matters most.
            WallpaperVeil(band = { topInset + 8.dp }, dark = dark)
        }

        FloatingTopBar(
            glass = glass,
            profile = profile,
            onlineFriends = onlineFriends,
            layout = ui.layout,
            onPickLayout = settings::setLibraryLayout,
            sort = ui.sort,
            onPickSort = settings::setLibrarySort,
            openConsole = ui.openConsole,
            // Games, wherever they sit: counting entries let the console folders in,
            // and counting only loose games said "0 games" on a library sorted into
            // folders, which is every game there is.
            openConsoleCount = ui.entries.sumOf { entry ->
                when (entry) {
                    is Entry.Game -> 1
                    is Entry.Folder -> entry.roms.size
                }
            },
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
            folderFocus = folderFocus,
            // Down from the header leads to the grid: the keypad is the system's and is
            // not a cursor stop.
            onLeaveDown = { runCatching { gridFocus.requestFocus() } },
            barCursor = barCursor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // First in the chain, so what is reported is the shelf plus everything
                // laid around it: that edge is exactly what the veil has to clear.
                .onGloballyPositioned { coords ->
                    val bottom = coords.boundsInRoot().bottom
                    shelfBottom = with(density) { bottom.toDp() } + 8.dp
                }
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(horizontal = 20.dp, vertical = 10.dp)
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
    /** The shelf's measured bottom edge: where the first row is allowed to rest. */
    contentTop: androidx.compose.ui.unit.Dp,
    /** How far the grid has pushed the shelf away, 0 to 1. */
    scrolled: MutableFloatState,
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
                // Pushes the grid down rather than covering its first row, and by the
                // shelf's own height rather than by a number that once fitted.
                // pourquoi : docs/decisions/bibliotheque.md § The veils, and why the launch card is where it is
                top = contentTop,
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
                        contentPadding = contentPadding,
                        scrolled = scrolled
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
                    ).also {
                        // A row that only moves sideways never hides the shelf.
                        LaunchedEffect(Unit) { scrolled.floatValue = 0f }
                    }

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
                        contentPadding = contentPadding,
                        scrolled = scrolled
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
    contentPadding: PaddingValues,
    /**
     * How far the shelf has been pushed away, 0 to 1. Written here, read only inside
     * draw lambdas: read in a composition it would recompose the chrome on every frame
     * of every scroll.
     * pourquoi : docs/decisions/performance-rendu.md § One clock for everything that moves continuously
     */
    scrolled: MutableFloatState
) {
    val localWindowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
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
            // containerSize is in pixels, and everything this count is compared against
            // is in dp: read raw, a 1920 px screen asked for sixteen columns instead of
            // six and the titles no longer fit their tile.
            val widthDp = with(density) { localWindowInfo.containerSize.width.toDp() }
            max(
                GRID_COLS_PORTRAIT,
                ((widthDp - 40.dp) / (TILE_MIN_WIDTH_DP + 18).dp).toInt()
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
        // How far the shelf has been pushed away, 0 to 1, and not whether it should be:
        // a duration cannot be in step with a thumb. The travel is the shelf's own
        // height, so the shelf has finished leaving exactly when the first row reaches
        // where it used to sit.
        // pourquoi : docs/decisions/bibliotheque.md § The top bar: two shelves, never a bar
        val travelPx = with(density) { (contentPadding.calculateTopPadding() - 20.dp).toPx() }
            .coerceAtLeast(1f)
        LaunchedEffect(gridState, travelPx) {
            snapshotFlow {
                if (gridState.firstVisibleItemIndex > 0) 1f
                else (gridState.firstVisibleItemScrollOffset / travelPx).coerceIn(0f, 1f)
            }.collect { scrolled.floatValue = it }
        }
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
            // Keyed on what the cell *holds*, not on where it sits: with the slot index
            // as the key, item 0 stayed item 0 and a re-sort only swapped its contents,
            // so nothing had anywhere to travel to.
            items(
                count = totalSlots,
                key = { i -> entries.getOrNull(i)?.key ?: "empty:$i" }
            ) { i ->
                val entry = entries.getOrNull(i)
                // A derived state: reading `cursor` here would subscribe all fourteen tiles
                // on screen, and one step would recompose them all.
                // pourquoi : docs/decisions/bibliotheque.md § What the tile reads must change only for it
                val selected = remember(i) {
                    derivedStateOf { padFocusedState.value && i == cursorState.intValue }
                }
                val held = remember(i) { derivedStateOf { hold.down && i == cursorState.intValue } }
                // The whole point of the identity key above: a re-sort slides the tiles
                // to their new cells instead of redrawing the grid in place.
                //
                // Placement only. `animateItem` fades appearances and disappearances by
                // default, and a lazy grid appears and disappears items for a living:
                // running the cursor down the rows put a fade on every tile crossing an
                // edge, and the frame's animation phase went to 43 ms. Measured on the
                // Thor, 2026-09-10. A re-sort moves tiles, it does not summon them.
                // pourquoi : docs/decisions/performance-rendu.md § One clock for everything that moves continuously
                val travel = Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = Motion.arrival(),
                    fadeOutSpec = null
                )
                when (entry) {
                    null -> EmptySlot(modifier = travel)
                    is Entry.Folder -> FolderTile(
                        folder = entry,
                        onClick = { onSelect(entry) },
                        selected = selected.value,
                        padHeld = held.value,
                        modifier = travel
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
                        onDismissMenu = onDismissMenu,
                        // The grid steps aside for the cursor too, just less far than the
                        // carousel: its tile grows less and the next row is right below.
                        titleDrop = TILE_TITLE_DROP,
                        modifier = travel
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
    contentPadding: PaddingValues,
    /**
     * How far the shelf has been pushed away, 0 to 1. Written here, read only inside
     * draw lambdas: read in a composition it would recompose the chrome on every frame
     * of every scroll.
     * pourquoi : docs/decisions/performance-rendu.md § One clock for everything that moves continuously
     */
    scrolled: MutableFloatState
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

/** Who is around, which is the one thing worth saying above the library's name. */
@Composable
private fun onlineLine(online: Int): String = when (online) {
    0 -> stringResource(R.string.lib_online_none)
    1 -> stringResource(R.string.lib_online_one)
    else -> stringResource(R.string.lib_online, online)
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = LocalEmufiiDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = CircleShape
    val pane = LocalGlassPane.current

    Box(
        modifier = modifier
            .focusRing(focused, shape)
            .then(
                if (pane != null) {
                    Modifier.glass(pane, shape, dark, thickness = 0.35f, lift = 3.dp)
                } else {
                    Modifier.plate(
                        shape = shape,
                        dark = dark,
                        oled = LocalEmufiiOledTheme.current,
                        lift = 3.dp,
                        bevel = false
                    )
                }
            )
            .tap(interactionSource = interaction, indication = null, onClick = onBack)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            ChevronLeft(size = 18.dp, color = MaterialTheme.colorScheme.onSurface)
            // Where it goes, not where you are: the title beside it already says the
            // console and counts its games, and the button repeated both word for word.
            // A back control names its destination.
            Text(
                stringResource(R.string.bar_root),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
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
    /** The tray and the grid, for the header to refract. */
    glass: Backdrop,
    profile: Profile,
    onlineFriends: Int,
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
    /** The way back out of a folder: where the cursor lands when it climbs out of the grid. */
    folderFocus: FocusRequester,
    onLeaveDown: () -> Unit,
    /** Hoisted: the panel needs to know when the cursor sits in the header. */
    barCursor: MutableState<Boolean>,
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
    var barFocused by barCursor
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

    // One piece again, but posed and light, and it opens on who you are rather than on
    // a title: identity, then state, then the tools, then the one thing to do.
    // pourquoi : docs/decisions/bibliotheque.md § The header is a pebble, and the game colours it
    val shelfDark = LocalEmufiiDarkTheme.current
    // The header's own rendering, for the pills standing on it.
    val headerGlass = rememberLayerBackdrop()
    Box(
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
            }
            .fillMaxWidth()
            // Glass, not plastic: the lens bends the covers passing underneath and the rim
            // catches the light. It exports its own pane, so the controls it carries refract
            // the header rather than the grid two layers down.
            // pourquoi : docs/decisions/bibliotheque.md § The header is a pebble, and the game colours it
            .glass(
                backdrop = glass,
                shape = PillShape,
                dark = shelfDark,
                exported = headerGlass
            )
    ) {
      CompositionLocalProvider(LocalGlassPane provides headerGlass) {
        Row(
            // The grid sizes its tiles to fit four rows under this: every dp taken here
            // is taken out of the artwork.
            // pourquoi : docs/decisions/bibliotheque.md § Whole rows, or nothing
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Identity first: the app opens on who you are, and the avatar is the way
            // into the profile it used to reach from the far corner.
            ProfileChip(
                profile = profile,
                onClick = onOpenProfile,
                modifier = if (searchOpen) Modifier else Modifier.focusRequester(topBarLeftFocus),
                onFocused = follow(profileFace)
            )

            // Two lines, weak over strong: what is going on, then where you are.
            if (!searchOpen) {
                // Bounded, not weighted. A weight here would compete with the spacer that
                // pushes the controls to the far end, and the free room would be split
                // between them instead of all going to the spacer -- the row then stopped
                // short and the last disc sat in from the edge. A ceiling keeps the title
                // from driving that disc off the pebble without taking the spacer's job:
                // the longest line here is "No friends online", well inside it.
                Column(
                    modifier = Modifier.widthIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        onlineLine(onlineFriends),
                        style = MaterialTheme.typography.labelMedium,
                        // Not `onSurfaceVariant`: a mid grey needs a settled colour behind
                        // it, and glass has none. The rank is carried by the alpha and the
                        // weight instead, over the same ink as the title.
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            if (openConsole != null) openConsole.label else root,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // On the teal axis: colour says *game* here, which is exactly
                        // what is being counted.
                        // pourquoi : docs/decisions/theme-duotone-shelves.md § Two semantic axes
                        Text(
                            gameCount(openConsoleCount),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (shelfDark) Teal.darkBright else Teal.deep,
                            maxLines = 1
                        )
                    }
                }
                // The way out of a folder keeps its own control: a line of text that
                // also navigates is a line nobody presses.
                if (openConsole != null) {
                    FolderHeader(
                        onBack = onLeaveFolder,
                        modifier = Modifier.focusRequester(folderFocus)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Hidden while the rear panel is lit: the only thing the two screens would
            // say word for word, a foot apart.
            // pourquoi : docs/decisions/bibliotheque.md § The service lamp goes out when the panel is lit
            if (!searchOpen && !panelLive) VpsLamp(dotSize = 10.dp)

            // The bar grows out of the bank rather than replacing it. The width is what
            // carries the movement, so it is the width that is animated: this used to
            // `snap()`, which put the whole bar there in one frame and left the crossfade
            // looking like a glitch. The two contents still cross quickly -- what travels
            // is the shape, not the text.
            // pourquoi : docs/decisions/bibliotheque.md § Search takes the shelf, and the two states do not cross
            androidx.compose.animation.AnimatedContent(
                targetState = searchOpen,
                transitionSpec = {
                    androidx.compose.animation.fadeIn(
                        tween(
                            durationMillis = 130,
                            delayMillis = 110,
                            easing = androidx.compose.animation.core.LinearOutSlowInEasing
                        )
                    ).togetherWith(
                        androidx.compose.animation.fadeOut(
                            tween(
                                durationMillis = 90,
                                easing = androidx.compose.animation.core.FastOutLinearInEasing
                            )
                        )
                    ).using(
                        // Clipped, so the bar is revealed as it opens instead of its far
                        // end hanging in the air over the title.
                        androidx.compose.animation.SizeTransform(clip = true) { _, _ ->
                            androidx.compose.animation.core.spring(
                                dampingRatio = 0.86f,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            )
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
                    ToolBank {
                        SearchChip(onClick = onSearchOpen, onFocused = follow(searchFace))
                        BankSeam()
                        LayoutChip(
                            current = layout,
                            onPick = onPickLayout,
                            onFocused = follow(layoutFace)
                        )
                        BankSeam()
                        SortChip(
                            current = sort,
                            onPick = onPickSort,
                            onFocused = follow(sortFace)
                        )
                    }
                }
            }

            // The cursor says the zone: every ring inside turns coral, the library's own
            // controls staying on the teal axis.
            // pourquoi : docs/decisions/theme-duotone-shelves.md § GAMEPAD FOCUS
            CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FriendsChip(onClick = onOpenFriends, onFocused = follow(friendsFace))
                    // The one thing this app is for, and the only outlined control:
                    // a filled one would fight the cover's colour behind it.
                    // pourquoi : docs/decisions/bibliotheque.md § The header is a pebble, and the game colours it
                    SessionsChip(
                        onClick = onOpenFinder,
                        modifier = Modifier.focusRequester(topBarFocus),
                        onFocused = follow(sessionsFace),
                        outlined = true
                    )
                }
            }
        }
      }
    }
}



/**
 * One destination, one pill.
 * pourquoi : docs/decisions/bibliotheque.md § The top bar: two shelves, never a bar
 */
@Composable
private fun EmptySlot(modifier: Modifier = Modifier) {
    val dark = LocalEmufiiDarkTheme.current
    // pourquoi : docs/decisions/bibliotheque.md § The top bar: two shelves, never a bar
    Column(
        modifier = modifier.fillMaxWidth(),
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