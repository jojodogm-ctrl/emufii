package eu.emufii.app.ui.screens

import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import eu.emufii.app.ui.FadeInPlaceBox
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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.EnterExitState
import eu.emufii.app.ui.LocalLayoutShown
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
import eu.emufii.app.ui.components.LampMode
import eu.emufii.app.ui.components.ServerPicker
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import eu.emufii.app.ui.components.serverMenuBackdrop
import eu.emufii.app.ui.screens.library.LocalLibraryExitBottom
import eu.emufii.app.ui.Sfx
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
    onlineFriends: Int = 0,
    onOpenProfile: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenFinder: () -> Unit,
    onCreate: (Rom, private: Boolean) -> Unit,
    onJoinWith: (Rom) -> Unit,
    onPlayPublic: (Rom) -> Unit,
    onFolderPicked: (Uri) -> Unit,
    libraryRevision: Int
) {
    val dark = LocalEmufiiDarkTheme.current
    val state = rememberLibraryScreenState()
    val compatDb = LocalCompatDb.current
    LaunchedEffect(compatDb) { state.setCompat(compatDb) }
    val ui by state.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val settings = remember(context) { SettingsStore.get(context) }

    val topBarLeftFocus = remember { FocusRequester() }
    val topBarFocus = remember { FocusRequester() }
    val folderFocus = remember { FocusRequester() }
    fun headerFocus(side: HeaderSide) = when {
        ui.openConsole != null && !ui.searchOpen -> folderFocus
        side == HeaderSide.LEFT -> topBarLeftFocus
        else -> topBarFocus
    }

    val gridFocus = remember { FocusRequester() }

    val panelDisplay by rememberPresentationDisplay()
    val panelWanted by remember(context) { SettingsStore.get(context).secondScreen }
        .collectAsStateWithLifecycle()
    val panelLive = panelWanted && panelDisplay != null
    /** Holds the front's focus, unseen, while the panel's lamp has the cursor: one cursor, not two. */
    val lampPilot = remember { FocusRequester() }
    // The panel going dark mid-aim must not leave the pad talking to nothing.
    LaunchedEffect(panelLive) { if (!panelLive) ServerPicker.release() }
    val exitBottom: () -> Boolean = {
        if (panelLive) {
            ServerPicker.aim()
            runCatching { lampPilot.requestFocus() }
            true
        } else {
            false
        }
    }

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

    BackHandler(enabled = ui.openConsole != null) { state.closeFolder() }
    // After the folder's, so back closes the search first: one layer at a time.
    BackHandler(enabled = ui.searchOpen) { state.closeSearch() }

    val onEntry: (Entry) -> Unit = { entry -> state.onEntry(entry) }

    // IgnoringVisibility: the loading screen hides the bars, and their return must not re-measure the grid.
    val topInset = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues()
        .calculateTopPadding()
    val bottomInset = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
        .calculateBottomPadding()

    SharedTransitionLayout(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (ServerPicker.aimed.value) {
                        ServerPicker.release()
                        runCatching { gridFocus.requestFocus() }
                    }
                }
            }
            .serverMenuBackdrop(LampMode.FOCUSABLE)
    ) {
    CompositionLocalProvider(
        LocalSharedMotion provides this,
        LocalCardRom provides ui.selected?.uri,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        val hazeState = rememberHazeState()
        var shelfBottom by remember { mutableStateOf(0.dp) }
        val gridScrolled = remember { mutableFloatStateOf(0f) }
        val barCursor = remember { mutableStateOf(false) }
        /** 0 to 1. A lambda so it is read in draw only; read in composition it recomposes the chrome every scroll frame. */
        val shelfAway = { if (barCursor.value) 0f else gridScrolled.floatValue }
        LaunchedEffect(ui.openConsole, ui.revision, ui.layout) { gridScrolled.floatValue = 0f }
        val density = LocalDensity.current

        val glass = rememberLayerBackdrop()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (ui.searchOpen) Modifier.hazeSource(hazeState) else Modifier)
                .layerBackdrop(glass)
        ) {
            TrayBackdrop(modifier = Modifier.fillMaxSize(), dark = dark)

            Box(
                Modifier
                    .size(1.dp)
                    .focusRequester(lampPilot)
                    // Focus taken elsewhere (a touch, a dialog): the panel lets the pad go.
                    .onFocusChanged { if (!it.isFocused && ServerPicker.aimed.value) ServerPicker.release() }
                    .onPreviewKeyEvent { event ->
                        if (!ServerPicker.aimed.value) return@onPreviewKeyEvent false
                        val used = ServerPicker.handleKey(event, remote = true)
                        if (!ServerPicker.aimed.value) runCatching { gridFocus.requestFocus() }
                        used
                    }
                    .focusable()
            )

            CompositionLocalProvider(LocalLibraryExitBottom provides exitBottom) {
            HandleState(
                ui = ui,
                state = state,
                onEntry = onEntry,
                onPickFolder = { folderPicker.launch(null) },
                onExitTop = { side -> runCatching { headerFocus(side).requestFocus() } },
                gridFocus = gridFocus,
                topInset = topInset,
                bottomInset = bottomInset,
                contentTop = shelfBottom.takeIf { it > 0.dp } ?: (topInset + 72.dp),
                scrolled = gridScrolled,
            )
            }

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
            onLeaveDown = { runCatching { gridFocus.requestFocus() } },
            barCursor = barCursor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    val bottom = coords.boundsInRoot().bottom
                    shelfBottom = with(density) { bottom.toDp() } + 8.dp
                }
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )

        ui.renameFor?.let { rom ->
            RenameRomDialog(
                rom = rom,
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
                onPrimary = { private -> onCreate(rom, private) },
                onJoinWithCode = { state.clearSelection(); onJoinWith(rom) },
                onPlayOnline =
                    if (rom.console.backend == Backend.PPSSPP || rom.console.backend == Backend.MELONDS ||
                        rom.console.backend == Backend.ARMSX2)
                        ({ onPlayPublic(rom) })
                    else null
            )
        }
    }
    }
    }
}

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
                top = contentTop,
                bottom = bottomInset + 88.dp
            )

            var arriving by remember(ui.openConsole, ui.revision) { mutableStateOf(true) }
            LaunchedEffect(ui.openConsole, ui.revision) {
                arriving = true
                delay(ENTRANCE_WINDOW_MS.milliseconds)
                arriving = false
            }

            val layoutIn: FiniteAnimationSpec<Float> = Motion.enter()
            val layoutOut: FiniteAnimationSpec<Float> = Motion.exit()
            val zoom: FiniteAnimationSpec<Float> = Motion.morph()
            var lastFolder by remember { mutableStateOf<Console?>(null) }
            LaunchedEffect(ui.openConsole) { ui.openConsole?.let { lastFolder = it } }
            CompositionLocalProvider(LocalTileEntrance provides arriving) {
              androidx.compose.animation.AnimatedContent(
                targetState = LibraryView(ui.layout, ui.openConsole, ui.entries),
                contentKey = { it.layout to it.console },
                transitionSpec = {
                    val entering = initialState.console == null && targetState.console != null
                    val leaving = initialState.console != null && targetState.console == null
                    when {
                        entering -> (fadeIn(layoutIn) + scaleIn(zoom, initialScale = 0.9f)) togetherWith
                            (fadeOut(layoutOut) + scaleOut(zoom, targetScale = 1.08f))
                        leaving -> (fadeIn(layoutIn) + scaleIn(zoom, initialScale = 1.08f)) togetherWith
                            (fadeOut(layoutOut) + scaleOut(zoom, targetScale = 0.9f))
                        else -> fadeIn(layoutIn) togetherWith fadeOut(layoutOut)
                    }.using(SizeTransform(clip = false))
                },
                label = "library-layout"
              ) { view ->
                val layout = view.layout
                val entries = view.entries
                val startAt = remember {
                    if (view.console != null) 0
                    else entries.indexOfFirst { it is Entry.Folder && it.console == lastFolder }
                        .coerceAtLeast(0)
                }
                CompositionLocalProvider(
                    LocalLayoutShown provides (transition.targetState == EnterExitState.Visible)
                ) {
                when (layout) {
                    LibraryLayout.GRID -> RomsGrid(
                        entries = entries,
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
                        scrolled = scrolled,
                        startAt = startAt
                    )

                    LibraryLayout.CAROUSEL -> RomsCarousel(
                        entries = entries,
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
                        startAt = startAt
                    ).also {
                        // A row that only moves sideways never hides the shelf.
                        LaunchedEffect(Unit) { scrolled.floatValue = 0f }
                    }

                    LibraryLayout.LIST -> RomsList(
                        entries = entries,
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
                        scrolled = scrolled,
                        startAt = startAt
                    )
                }
                }
              }
            }
        }
    }
}

@Composable
private fun LibraryEffects(
    state: LibraryScreenState,
    ui: LibraryUiState,
    libraryRevision: Int,
    gridFocus: FocusRequester,
    topBarLeftFocus: FocusRequester,
) {
    // The holder already scanned on init, so the first fire is skipped.
    val firstRevision = remember { mutableStateOf(true) }
    LaunchedEffect(libraryRevision) {
        if (firstRevision.value) firstRevision.value = false
        else state.refresh()
    }

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
    /** 0 to 1. Read only inside draw lambdas, to avoid recomposing the chrome on every scroll frame. */
    scrolled: MutableFloatState,
    startAt: Int = 0
) {
    val localWindowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val landscape = localWindowInfo.containerSize.width > localWindowInfo.containerSize.height
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gutter = 18.dp
        val rowGap = 24.dp
        val topPad = contentPadding.calculateTopPadding()
        // Ignoring visibility: the splash hides then restores the bars, which changed the column count.
        val bottomLimit = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
            .calculateBottomPadding() + 14.dp
        val available = maxHeight - topPad - bottomLimit

        fun cellFor(c: Int) = (maxWidth - 40.dp - gutter * (c - 1)) / c
        fun rowFor(c: Int) = cellFor(c) + 8.dp + TILE_TITLE_ROOM
        val wantRows = if (landscape) 2 else 3
        val widthCols = if (landscape) {
            // containerSize is in px; everything it is compared against is in dp.
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
        val slack = (available - (rowHeight * wholeRows + rowGap * (wholeRows - 1)))
            .coerceIn(0.dp, 20.dp)
            .coerceAtLeast(HEADER_GAP)

        // The single column count: the cursor moves by ±columns and reads nothing else.
        val columns = cols

        val rowsFromEntries = if (entries.isEmpty()) 0 else (entries.size + columns - 1) / columns
        val totalRows = max(if (landscape) 2 else MIN_ROWS, rowsFromEntries + EXTRA_ROWS_AFTER)
        val totalSlots = totalRows * columns

        val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = startAt)
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

        // The state, not its value: reading `cursor` in a composable body subscribes it.
        val cursorState = rememberSaveable { mutableIntStateOf(startAt) }
        var cursor by cursorState
        val padFocusedState = remember { mutableStateOf(false) }
        var padFocused by padFocusedState
        PublishHovered(entries, cursorState)

        // A rescan, or entering a folder, can shorten the list under the cursor.
        LaunchedEffect(entries.size) {
            if (cursor > entries.lastIndex) cursor = entries.lastIndex.coerceAtLeast(0)
        }

        LaunchedEffect(Unit) { runCatching { gridFocus.requestFocus() } }

        // The tile menu's window takes focus; without this the d-pad is dead after it closes.
        LaunchedEffect(menuFor) {
            if (menuFor == null) runCatching { gridFocus.requestFocus() }
        }

        fun reveal(index: Int) {
            scope.launch {
                val info = gridState.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                if (item == null) {
                    gridState.animateScrollToItem(index, -info.beforeContentPadding)
                    return@launch
                }
                val top = item.offset.y
                val bottom = top + item.size.height
                val safeTop = info.viewportStartOffset + info.beforeContentPadding
                val safeBottom = info.viewportEndOffset - info.afterContentPadding
                // The focused tile is scaled 7% with a glow, so it spills past its layout bounds.
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

        val exitBottom = LocalLibraryExitBottom.current

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
                Key.DirectionDown -> when {
                    moveTo(cursor + columns) -> true
                    // A short last row: from above it, down lands on its last tile.
                    cursor / columns < entries.lastIndex / columns -> moveTo(entries.lastIndex)
                    else -> exitBottom()
                }
                Key.DirectionUp ->
                    if (cursor < columns) {
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
            // Keyed by content, not slot, so a re-sort has something to animate.
            items(
                count = totalSlots,
                key = { i -> entries.getOrNull(i)?.key ?: "empty:$i" }
            ) { i ->
                val entry = entries.getOrNull(i)
                // Derived: reading `cursor` directly would recompose every visible tile on each step.
                val selected = remember(i) {
                    derivedStateOf { padFocusedState.value && i == cursorState.intValue }
                }
                val held = remember(i) { derivedStateOf { hold.down && i == cursorState.intValue } }
                // Placement only: the default appear/disappear fade cost ~43 ms per frame while scrolling.
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
                        selected = selected.value,
                        padHeld = held.value,
                        menuOpen = menuFor?.uri == entry.rom.uri,
                        onChangeIcon = { onMenuAction(entry.rom, TileAction.ICON) },
                        onRename = { onMenuAction(entry.rom, TileAction.RENAME) },
                        onHide = { onMenuAction(entry.rom, TileAction.HIDE) },
                        onDismissMenu = onDismissMenu,
                        titleDrop = TILE_TITLE_DROP,
                        modifier = travel
                    )
                }
            }
        }
    }
}

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
    /** 0 to 1. Read only inside draw lambdas, to avoid recomposing the chrome on every scroll frame. */
    scrolled: MutableFloatState,
    startAt: Int = 0
) {
    val marginPx = marginPx()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startAt)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // The strip the bottom veil paints back over the list; layout does not know it.
    val veilPx = with(density) {
        (WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
            .calculateBottomPadding() + 14.dp).roundToPx()
    }
    val cursorState = rememberSaveable { mutableIntStateOf(startAt) }
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

    val exitBottom = LocalLibraryExitBottom.current

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
            Key.DirectionDown -> moveTo(cursor + 1) || exitBottom()
            Key.DirectionUp ->
                if (cursor == 0) {
                    onExitTop(HeaderSide.RIGHT)
                    true
                } else {
                    moveTo(cursor - 1)
                }
            Key.DirectionLeft, Key.DirectionRight -> true
            else -> null
        }
    }

    LazyColumn(
        state = listState,
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
            // Ring first, before anything that clips and before the opaque fill.
            .focusRing(selected, shape)
            .plate(
                shape = shape,
                dark = dark,
                oled = LocalEmufiiOledTheme.current,
                lift = if (selected) 7.dp else 3.dp
            )
            // Scaling a full-width row would push its neighbours.
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

@Composable
internal fun consolePlate(console: Console): Brush {
    val (c1, c2) = paletteFor(console.name)
    return Brush.linearGradient(colors = listOf(c1, c2), start = Offset.Zero, end = Offset.Infinite)
}

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

@Composable
private fun FolderHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Boolean) -> Unit = {}
) {
    val dark = LocalEmufiiDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocused(focused) }
    val shape = CircleShape
    Box(
        modifier = modifier
            .focusRing(focused, shape)
            .then(
                run {
                    Modifier.plate(
                        shape = shape,
                        dark = dark,
                        oled = LocalEmufiiOledTheme.current,
                        lift = 3.dp
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
            Text(
                stringResource(R.string.bar_root),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun FloatingTopBar(
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
    folderFocus: FocusRequester,
    onLeaveDown: () -> Unit,
    barCursor: MutableState<Boolean>,
    modifier: Modifier = Modifier
) {
    // The setting is not enough: the device may have only one screen.
    val context = LocalContext.current
    val panelDisplay by rememberPresentationDisplay()
    val panelWanted by remember(context) { SettingsStore.get(context).secondScreen }
        .collectAsStateWithLifecycle()
    val panelLive = panelWanted && panelDisplay != null

    var headerAside by remember { mutableStateOf<Any?>(null) }
    DisposableEffect(Unit) {
        onDispose { headerAside?.let { SecondScreen.takeBack(it) } }
    }

    var headerFace by remember { mutableStateOf<SecondScreenModel?>(null) }

    /** Delayed: Compose drops focus before granting it to the next pill, which read as leaving. */
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
    val backFace = chipFace(
        root,
        stringResource(R.string.bar_back_summary),
        PanelMark.LIBRARY
    )
    val profileFace = chipFace(
        playerDisplayName(profile.name),
        stringResource(R.string.bar_profile_summary),
        PanelMark.PROFILE,
        social = true
    )

    val shelfDark = LocalEmufiiDarkTheme.current
    val headerGlass = rememberLayerBackdrop()
    Box(
        modifier = modifier
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
            .glass(
                backdrop = glass,
                shape = PillShape,
                dark = shelfDark,
                exported = headerGlass
            )
    ) {
      CompositionLocalProvider(LocalGlassPane provides headerGlass) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProfileChip(
                profile = profile,
                onClick = onOpenProfile,
                modifier = if (searchOpen) Modifier else Modifier.focusRequester(topBarLeftFocus),
                onFocused = follow(profileFace)
            )

            if (!searchOpen) {
                val swapOut = tween<Float>(durationMillis = 110, easing = FastOutLinearInEasing)
                val swapIn = tween<Float>(durationMillis = 170, delayMillis = 110, easing = LinearOutSlowInEasing)
                val swapSize = Motion.morph<androidx.compose.ui.unit.IntSize>()
                androidx.compose.animation.AnimatedContent(
                    targetState = openConsole to openConsoleCount,
                    contentKey = { it.first },
                    transitionSpec = {
                        (EnterTransition.None togetherWith ExitTransition.None).using(
                            androidx.compose.animation.SizeTransform(clip = false) { _, _ -> swapSize }
                        )
                    },
                    label = "shelf-folder-swap"
                ) { shown ->
                  val shownConsole = shown.first
                  FadeInPlaceBox(transition, swapIn, swapOut) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                    // A ceiling, not a weight: a weight would share the free room with the trailing spacer.
                    Column(
                        modifier = Modifier.widthIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            onlineLine(onlineFriends),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                shownConsole?.label ?: root,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                gameCount(shown.second),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (shelfDark) Teal.darkBright else Teal.deep,
                                maxLines = 1
                            )
                        }
                    }
                    if (shownConsole != null) {
                        FolderHeader(
                            onBack = onLeaveFolder,
                            modifier = Modifier.focusRequester(folderFocus),
                            onFocused = follow(backFace)
                        )
                    }
                    }
                  }
                }
            }

            Spacer(Modifier.weight(1f))

            androidx.compose.animation.AnimatedVisibility(
                visible = !searchOpen && !panelLive && openConsole == null,
                enter = androidx.compose.animation.fadeIn(tween(170, delayMillis = 110)),
                exit = androidx.compose.animation.fadeOut(tween(110))
            ) {
                val leaving = transition.targetState == androidx.compose.animation.EnterExitState.PostExit
                VpsLamp(
                    dotSize = 10.dp,
                    mode = LampMode.FOCUSABLE,
                    modifier = if (!leaving) Modifier else Modifier.layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                        layout(0, p.height) { p.place(-p.width, 0) }
                    }
                )
            }

            val searchIn = tween<Float>(durationMillis = 130, delayMillis = 110, easing = LinearOutSlowInEasing)
            val searchOut = tween<Float>(durationMillis = 90, easing = FastOutLinearInEasing)
            androidx.compose.animation.AnimatedContent(
                targetState = searchOpen,
                transitionSpec = {
                    (EnterTransition.None togetherWith ExitTransition.None).using(
                        androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                            androidx.compose.animation.core.spring(
                                dampingRatio = 0.86f,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            )
                        }
                    )
                },
                label = "shelf-search-swap"
            ) { open ->
              FadeInPlaceBox(transition, searchIn, searchOut) {
                if (open) {
                    SearchField(
                        value = query,
                        onValueChange = onQueryChange,
                        onClose = onSearchClose,
                        modifier = Modifier.focusRequester(topBarLeftFocus)
                    )
                } else {
                    ToolBank {
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
            }

            CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FriendsChip(onClick = onOpenFriends, onFocused = follow(friendsFace))
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

@Composable
private fun EmptySlot(modifier: Modifier = Modifier) {
    val dark = LocalEmufiiDarkTheme.current
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

private data class LibraryView(
    val layout: LibraryLayout,
    val console: Console?,
    val entries: List<Entry>,
)
