package eu.emufii.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import eu.emufii.app.library.GameTitles
import eu.emufii.app.library.LibraryLayout
import eu.emufii.app.library.LibrarySort
import eu.emufii.app.library.Rom
import eu.emufii.app.library.RomTagReader
import eu.emufii.app.library.RomsRepository
import eu.emufii.app.library.byConsole
import eu.emufii.app.library.compatKeys
import eu.emufii.app.library.shortLabel
import eu.emufii.app.library.sortedFor
import eu.emufii.app.meta.LocalGameMetaDb
import eu.emufii.app.profile.Profile
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.secondscreen.PanelFeed
import eu.emufii.app.secondscreen.PanelMark
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.SecondScreenModel
import eu.emufii.app.secondscreen.rememberPresentationDisplay
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.CONFIRM_KEYS
import eu.emufii.app.ui.LocalRingTone
import eu.emufii.app.ui.RING_IN_MS
import eu.emufii.app.ui.RingTone
import eu.emufii.app.ui.Sfx
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
import eu.emufii.app.ui.components.UPDATE_BANNER_ROOM
import eu.emufii.app.ui.components.UpdateBanner
import eu.emufii.app.ui.components.VpsLamp
import eu.emufii.app.ui.components.WallpaperVeil
import eu.emufii.app.ui.components.artworkRim
import eu.emufii.app.ui.components.consoleArtwork
import eu.emufii.app.ui.components.tilePlate
import eu.emufii.app.ui.focusRing
import eu.emufii.app.ui.gamepadClick
import eu.emufii.app.ui.ringColor
import eu.emufii.app.ui.sounded
import eu.emufii.app.ui.tap
import eu.emufii.app.ui.tapOrHold
import eu.emufii.app.ui.theme.ArtworkShape
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.InfoLight
import eu.emufii.app.ui.theme.InkText
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.Teal
import eu.emufii.app.ui.theme.TileShape
import eu.emufii.app.ui.theme.Violet
import eu.emufii.app.ui.theme.VioletDark
import eu.emufii.app.ui.theme.WarnLight
import eu.emufii.app.ui.theme.moldedRim
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.wallpaper.TrayBackdrop
import eu.emufii.app.update.LatestVersion
import eu.emufii.app.update.UpdateCheck
import eu.emufii.app.update.UpdateDismissals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Portrait keeps three big tiles; landscape follows the width instead.
 * pourquoi : docs/decisions/bibliotheque.md § Whole rows, or nothing
 */
private const val GRID_COLS_PORTRAIT = 3
private const val TILE_MIN_WIDTH_DP = 104
private const val MIN_ROWS = 4
private const val EXTRA_ROWS_AFTER = 1

/** Two lines of `labelMedium`; the grid needs it before laying out. */
private val TILE_TITLE_ROOM = 32.dp

/**
 * The grid takes it as a floor on its slack, the list adds it outright; its value is
 * the cursor's, like [SHELF_INSET].
 * pourquoi : docs/decisions/bibliotheque.md § The air under the bar is the cursor's, and it is computed
 */
private val HEADER_GAP = 22.dp

/**
 * The cursor spills 8.7 dp around a pill, and the socket has to hold it.
 * pourquoi : docs/decisions/bibliotheque.md § The air under the bar is the cursor's, and it is computed
 */
private val SHELF_INSET = 10.dp

/**
 * Armed when the screen opens and disarmed after; a rescan or a folder rearms it.
 * pourquoi : docs/decisions/bibliotheque.md § The tiles' arrival is armed, then disarmed
 */
internal val LocalTileEntrance = staticCompositionLocalOf { false }

/** Thinner than the default: cover art is what the grid serves. */
private const val TILE_BAND = 0.070f

private const val ENTRANCE_WINDOW_MS = 900L

/**
 * How far the selected tile slides on the diagonal: the logo's staircase step.
 * pourquoi : docs/decisions/theme-duotone-shelves.md § The diagonal staircase
 */
/** Just clear of the tile's moulding; beyond that a pill floats in the artwork. */
private val BADGE_INSET = 9.dp

private val TILE_RISE = 2.5.dp

private enum class TileAction { ICON, RENAME, HIDE }

/**
 * What a library cell holds: a game or a folder. Shared by all three layouts.
 * pourquoi : docs/decisions/bibliotheque.md § Three layouts, one cursor contract
 */
private sealed interface Entry {
    val key: String

    data class Game(val rom: Rom) : Entry {
        override val key get() = rom.uri.toString()
    }

    data class Folder(val console: Console, val roms: List<Rom>) : Entry {
        override val key get() = "console:${console.name}"
    }
}

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
    val context = LocalContext.current
    val dark = LocalEmufiiDarkTheme.current
    val repo = remember { RomsRepository(context) }
    val settings = remember { SettingsStore.get(context) }
    val artworkKey by settings.steamGridDbKey.collectAsStateWithLifecycle()
    val layout by settings.libraryLayout.collectAsStateWithLifecycle()
    val sort by settings.librarySort.collectAsStateWithLifecycle()
    val hiddenConsoles by settings.hiddenConsoles.collectAsStateWithLifecycle()

    val topBarLeftFocus = remember { FocusRequester() }
    val topBarFocus = remember { FocusRequester() }
    fun headerFocus(side: HeaderSide) =
        if (side == HeaderSide.LEFT) topBarLeftFocus else topBarFocus
    val gridFocus = remember { FocusRequester() }
    var folderUri by remember(libraryRevision) { mutableStateOf(repo.savedFolderUri()) }
    var roms by remember { mutableStateOf<List<Rom>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Rom?>(null) }

    // Without it the cursor went up into the top bar and you came back down by hand.
    LaunchedEffect(selected) {
        if (selected == null) runCatching { gridFocus.requestFocus() }
    }

    // Two states, not one: conflating them reopens the menu on closing the icon choice.
    var menuFor by remember { mutableStateOf<Rom?>(null) }
    var pickIconFor by remember { mutableStateOf<Rom?>(null) }
    var renameFor by remember { mutableStateOf<Rom?>(null) }
    var hideFor by remember { mutableStateOf<Rom?>(null) }

    var reload by remember { mutableStateOf(0) }

    val dismissals = remember { UpdateDismissals(context) }
    var update by remember { mutableStateOf<LatestVersion?>(null) }
    LaunchedEffect(Unit) {
        val latest = UpdateCheck.fetch()
        if (latest != null && UpdateCheck.isNewer(latest) && !dismissals.isDismissed(latest.versionCode)) {
            update = latest
            // Mirrored: the panel keeps saying it once an emulator owns that screen.
            PanelFeed.post(
                context.getString(R.string.notify_update_title, latest.versionName),
                PanelFeed.Kind.UPDATE
            )
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) onFolderPicked(uri) }

    LaunchedEffect(folderUri, libraryRevision, reload) {
        if (folderUri != null) {
            loading = true
            // Never forced: the explicit rescan already refreshed the cache.
            roms = withContext(Dispatchers.IO) { repo.scan() }
            loading = false
            // Names encrypted dumps kept to themselves, asked for by the ids they did
            // give up; a late overlay, the grid having shown on the first frame.
            if (GameTitles.refresh(context, roms)) {
                roms = withContext(Dispatchers.IO) { repo.cachedOrScan() }
            }
        } else {
            roms = emptyList()
        }
    }

    /**
     * pourquoi : docs/decisions/bibliotheque.md § Hidden consoles are hidden here, not in the scan
     */
    var openConsole by remember { mutableStateOf<Console?>(null) }
    LaunchedEffect(sort) { if (sort != LibrarySort.CONSOLE) openConsole = null }

    // Above the folders: "where is that game" crosses consoles.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    // Here, never in the scan: the cache is shared with the session flow, which
    // must still find a hidden console's ROM.
    // pourquoi : docs/decisions/bibliotheque.md § Hidden consoles are hidden here, not in the scan
    val shown = remember(roms, hiddenConsoles) {
        if (hiddenConsoles.isEmpty()) roms else roms.filter { it.console !in hiddenConsoles }
    }

    val entries = remember(shown, sort, openConsole, query) {
        val needle = query.trim()
        when {
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
    }

    // A folder emptied by a rescan would be a blank screen with no way out.
    LaunchedEffect(entries.isEmpty(), openConsole) {
        if (openConsole != null && entries.isEmpty()) openConsole = null
    }

    // Without it, entering a console was a one-way trip for anyone without a controller.
    BackHandler(enabled = openConsole != null) { openConsole = null }
    // After the folder's, so back closes the search first: one layer at a time.
    BackHandler(enabled = searchOpen) {
        searchOpen = false; query = ""
    }
    LaunchedEffect(searchOpen) {
        if (searchOpen) runCatching { topBarLeftFocus.requestFocus() }
    }

    val onEntry: (Entry) -> Unit = { entry ->
        when (entry) {
            is Entry.Game -> selected = entry.rom
            is Entry.Folder -> openConsole = entry.console
        }
    }

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
                .then(if (searchOpen) Modifier.hazeSource(hazeState) else Modifier)
        ) {
            TrayBackdrop(modifier = Modifier.fillMaxSize(), dark = dark)

            when {
                folderUri == null -> EmptyState(
                    title = stringResource(R.string.lib_no_folder_title),
                    subtitle = stringResource(R.string.lib_no_folder_body),
                    cta = stringResource(R.string.lib_choose_folder),
                    onCta = { folderPicker.launch(null) },
                    topPadding = topInset + 72.dp,
                    bottomPadding = bottomInset + 24.dp
                )

                loading -> Box(
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
                        menuFor = null
                        when (action) {
                            TileAction.ICON -> pickIconFor = rom
                            TileAction.RENAME -> renameFor = rom
                            TileAction.HIDE -> hideFor = rom
                        }
                    }
                    val contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp,
                        // Pushes the grid down rather than covering its first
                        // row. The air after the header is [HEADER_GAP].
                        // pourquoi : docs/decisions/bibliotheque.md § The veils, and why the launch card is where it is
                        top = topInset + 72.dp +
                            (if (update != null) UPDATE_BANNER_ROOM else 0.dp),
                        // Travel, not empty space: the last row must rise
                        // fully into the usable area.
                        // pourquoi : docs/decisions/bibliotheque.md § Whole rows, or nothing
                        bottom = bottomInset + 88.dp
                    )

                    // No need to suppress it at startup: the grid composes behind the
                    // loading screen and has settled by the time it clears.
                    // pourquoi : docs/decisions/bibliotheque.md § The tiles' arrival is armed, then disarmed
                    var arriving by remember(openConsole, reload) { mutableStateOf(true) }
                    LaunchedEffect(openConsole, reload) {
                        arriving = true
                        delay(ENTRANCE_WINDOW_MS)
                        arriving = false
                    }

                    CompositionLocalProvider(LocalTileEntrance provides arriving) {
                    when (layout) {
                        LibraryLayout.GRID -> RomsGrid(
                            entries = entries,
                            onSelect = onEntry,
                            onLongPress = { menuFor = it },
                            menuFor = menuFor,
                            onMenuAction = onMenuAction,
                            onDismissMenu = { menuFor = null },
                            onExitTop = { side -> runCatching { headerFocus(side).requestFocus() } },
                            onBack = { openConsole = null },
                            canGoBack = openConsole != null,
                            gridFocus = gridFocus,
                            contentPadding = contentPadding
                        )

                        LibraryLayout.CAROUSEL -> RomsCarousel(
                            entries = entries,
                            onSelect = onEntry,
                            onLongPress = { menuFor = it },
                            menuFor = menuFor,
                            onMenuAction = onMenuAction,
                            onDismissMenu = { menuFor = null },
                            onExitTop = { side -> runCatching { headerFocus(side).requestFocus() } },
                            onBack = { openConsole = null },
                            canGoBack = openConsole != null,
                            gridFocus = gridFocus,
                            contentPadding = contentPadding
                        )

                        LibraryLayout.LIST -> RomsList(
                            entries = entries,
                            onSelect = onEntry,
                            onLongPress = { menuFor = it },
                            menuFor = menuFor,
                            onMenuAction = onMenuAction,
                            onDismissMenu = { menuFor = null },
                            onExitTop = { side -> runCatching { headerFocus(side).requestFocus() } },
                            onBack = { openConsole = null },
                            canGoBack = openConsole != null,
                            gridFocus = gridFocus,
                            contentPadding = contentPadding
                        )
                    }
                    }
                }
            }

            // Inside the Haze source and after the grid: they trim it
            // rather than take room from it.
            // pourquoi : docs/decisions/bibliotheque.md § The veils, and why the launch card is where it is
            WallpaperVeil(band = topInset + 60.dp, dark = dark)
            // Just enough that the last row does not touch the screen edge while scrolling.
            WallpaperVeil(band = bottomInset + 14.dp, dark = dark, fromTop = false)
        }

        FloatingTopBar(
            profile = profile,
            layout = layout,
            onPickLayout = settings::setLibraryLayout,
            sort = sort,
            onPickSort = settings::setLibrarySort,
            openConsole = openConsole,
            openConsoleCount = entries.size,
            onLeaveFolder = { openConsole = null },
            searchOpen = searchOpen,
            query = query,
            onSearchOpen = { searchOpen = true },
            onQueryChange = { query = it },
            onSearchClose = { searchOpen = false; query = "" },
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


        update?.let { latest ->
            UpdateBanner(
                latest = latest,
                onDismiss = { dismissals.dismiss(latest.versionCode); update = null },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                    .padding(start = 20.dp, end = 20.dp, top = 76.dp)
            )
        }

        // OVERLAY : the launch card. Sibling of the Haze source (so it can
        // blur the grid) and last (so a modal covers the chrome).
        // pourquoi : docs/decisions/bibliotheque.md § The veils, and why the launch card is where it is
        renameFor?.let { rom ->
            RenameRomDialog(
                rom = rom,
                // The name is applied when the repository builds the list: without a rebuild
                // the rename looks ignored until the next scan.
                onRenamed = {
                    renameFor = null
                    reload++
                },
                onDismiss = { renameFor = null }
            )
        }

        hideFor?.let { rom ->
            HideRomDialog(
                rom = rom,
                onHidden = {
                    hideFor = null
                    reload++
                },
                onDismiss = { hideFor = null }
            )
        }

        pickIconFor?.let { rom ->
            IconPickerDialog(
                rom = rom,
                apiKey = artworkKey,
                onDismiss = { pickIconFor = null }
            )
        }

        selected?.let { rom ->
            GameLaunchDialog(
                rom = rom,
                onDismiss = { selected = null },
                // Deliberately left up: nothing else publishes a screen until
                // the tunnel leg, so this spinner is what covers the wait.
                // pourquoi : docs/decisions/bibliotheque.md § The veils, and why the launch card is where it is
                onPrimary = { private -> onCreate(rom, private) },
                // DS online play has no session to create or join: each console dials the
                // revival server itself.
                onJoinWithCode =
                    if (rom.console.backend == Backend.MELONDS_WFC) null
                    else ({ selected = null; onJoinWith(rom) }),
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
 * All three layouts keep their own index.
 * pourquoi : docs/decisions/bibliotheque.md § Three layouts, one cursor contract
 */
private class Cursor(val moveTo: (Int) -> Boolean)

/**
 * Shared by all three layouts, so a fix in one cannot leave the others broken.
 * pourquoi : docs/decisions/bibliotheque.md § Three layouts, one cursor contract
 */
/** How long A is held before the tile's menu opens, matching touch's own delay. */
private const val HOLD_TO_MENU_MS = 480L

/**
 * A press does exactly one thing: menu on the hold, or launch on release.
 * pourquoi : docs/decisions/bibliotheque.md § Holding A, and the title that fades out
 */
private class ConfirmHold(private val scope: CoroutineScope) {
    /** Compose state, not a plain field: the tile reads it to sink while held. */
    var down by mutableStateOf(false)
        private set
    private var fired = false
    private var job: Job? = null

    fun press(onHold: () -> Unit) {
        down = true
        fired = false
        job = scope.launch {
            delay(HOLD_TO_MENU_MS)
            fired = true
            onHold()
        }
    }

    fun release(): Boolean {
        down = false
        job?.cancel()
        job = null
        return !fired
    }
}

@Composable
private fun rememberConfirmHold(): ConfirmHold {
    val scope = rememberCoroutineScope()
    return remember(scope) { ConfirmHold(scope) }
}

private fun entryKeys(
    entries: List<Entry>,
    cursorIndex: () -> Int,
    onSelect: (Entry) -> Unit,
    onLongPress: (Rom) -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean,
    hold: ConfirmHold,
    directions: (Key) -> Boolean?
): (KeyEvent) -> Boolean = keys@{ event ->
    // The one key read on the way up as well as down: that separates a press from a
    // hold, everything else being decided on KeyDown.
    if (event.key in CONFIRM_KEYS) {
        val entry = entries.getOrNull(cursorIndex())
        return@keys when (event.type) {
            KeyEventType.KeyDown -> {
                // Auto-repeat sends KeyDown again while held; only the first starts the timer,
                // or the menu fires on the last repeat instead of on time.
                if (!hold.down) {
                    hold.press { (entry as? Entry.Game)?.let { Sfx.click(); onLongPress(it.rom) } }
                }
                true
            }
            KeyEventType.KeyUp -> {
                // A hold that opened the menu must not also launch on release.
                if (hold.release() && entry != null) { Sfx.click(); onSelect(entry) }
                true
            }
            else -> false
        }
    }
    if (event.type != KeyEventType.KeyDown) return@keys false
    directions(event.key)?.let { return@keys it }
    when (event.key) {
        // Y opens the menu outright, with no wait; the hold is what someone coming from
        // touch tries first.
        Key.ButtonY ->
            (entries.getOrNull(cursorIndex()) as? Entry.Game)
                ?.let { Sfx.click(); onLongPress(it.rom); true } ?: false
        // B goes up a folder, as on every console; `false` when there is nowhere to go
        // lets the system close the screen.
        Key.ButtonB, Key.Back -> if (canGoBack) { onBack(); true } else false
        // Keyboard focus goes to a window, and on a one-screen machine this is the one
        // that has it.
        // pourquoi : docs/decisions/second-ecran.md § R turns the page from both screens
        Key.ButtonR1 -> { SecondScreen.flipPage(); true }
        else -> false
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
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
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
        max(GRID_COLS_PORTRAIT, (configuration.screenWidthDp - 40) / (TILE_MIN_WIDTH_DP + 18))
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
    val MARGIN_PX = with(LocalDensity.current) { 14.dp.roundToPx() }

    /**
     * An index we compute ourselves, so it cannot get lost with a component.
     * pourquoi : docs/decisions/bibliotheque.md § The cursor is a computed index, never a guessed focus
     */
    // The state, not its value: reading `cursor` in a composable body subscribes it.
    // pourquoi : docs/decisions/bibliotheque.md § What the tile reads must change only for it
    val cursorState = rememberSaveable { mutableStateOf(0) }
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
            val margin = MARGIN_PX
            val delta = when {
                top < safeTop + margin -> top - safeTop - margin
                bottom > safeBottom - margin -> bottom - safeBottom + margin
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
                derivedStateOf { padFocusedState.value && i == cursorState.value }
            }
            val held = remember(i) { derivedStateOf { hold.down && i == cursorState.value } }
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

private const val CAROUSEL_CARD_FRACTION = 0.38f

/** What the title claims under the card: two lines, plus the gap. */
private val CAROUSEL_TITLE_ROOM = 66.dp

/**
 * The active card carries the cursor's 7 % scale and its ring, which spill past its
 * layout bounds and would cross the title's first line. A drawing offset, not a layout
 * one, or the active card would grow the row and re-centre its neighbours at every step.
 */
private val CAROUSEL_TITLE_DROP = 18.dp

/**
 * pourquoi : docs/decisions/bibliotheque.md § The carousel has to follow the finger without turning on the gamepad
 */
@Composable
private fun RomsCarousel(
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val cursorState = rememberSaveable { mutableStateOf(0) }
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
     * To the centre, not "somewhere on screen".
     * pourquoi : docs/decisions/bibliotheque.md § The carousel has to follow the finger without turning on the gamepad
     */
    // Centre-following must be off while *we* scroll, or a double press computes
    // from a passing card.
    var settling by remember { mutableStateOf(false) }

    fun reveal(index: Int) {
        scope.launch {
            val info = listState.layoutInfo
            // Leading padding is already in `animateScrollToItem`'s frame;
            // passing it again applied it twice.
            // pourquoi : docs/decisions/bibliotheque.md § The carousel has to follow the finger without turning on the gamepad
            val viewport = info.viewportEndOffset - info.viewportStartOffset
            val itemWidth = info.visibleItemsInfo.firstOrNull()?.size ?: 0
            val offset = ((viewport - itemWidth) / 2 - info.beforeContentPadding)
            settling = true
            try {
                listState.animateScrollToItem(index, -offset)
            } finally {
                settling = false
            }
        }
    }

    /**
     * The card nearest the middle, whatever put it there.
     * pourquoi : docs/decisions/bibliotheque.md § The carousel has to follow the finger without turning on the gamepad
     */
    val centred by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2 - middle) }
                ?.index
        }
    }

    // While the finger has it, the cursor is whatever is in the middle: the card grows
    // as it arrives rather than after the fact.
    LaunchedEffect(centred, settling) {
        val index = centred
        if (!settling && index != null && index in entries.indices) cursor = index
    }

    /**
     * A drag is the only honest signal that a person moved the row.
     * pourquoi : docs/decisions/bibliotheque.md § The carousel has to follow the finger without turning on the gamepad
     */
    var dragged by remember { mutableStateOf(false) }
    LaunchedEffect(listState.interactionSource) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) dragged = true
        }
    }

    // Left where a fling ends, the row rests between two cards.
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && dragged) {
            dragged = false
            centred?.let { if (it in entries.indices) reveal(it) }
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
            Key.DirectionUp -> { onExitTop(HeaderSide.RIGHT); true }
            // Letting it through hands control back to Compose's traversal, which hunts
            // for a focusable elsewhere.
            Key.DirectionDown -> true
            else -> null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(gridFocus)
            .onFocusChanged { padFocused = it.hasFocus }
            .focusable()
            .onPreviewKeyEvent(onKey),
        contentAlignment = Alignment.Center
    ) {
        /**
         * The height actually free, never the screen's: landscape punishes that.
         * pourquoi : docs/decisions/bibliotheque.md § Three carousel measurements, all corrected from a screenshot
         */
        val free = maxHeight -
            contentPadding.calculateTopPadding() -
            contentPadding.calculateBottomPadding() -
            CAROUSEL_TITLE_ROOM
        val cardSize = minOf(maxWidth * CAROUSEL_CARD_FRACTION, free)
            .coerceIn(120.dp, 300.dp)

        /**
         * What lets the first and last card reach the centre.
         * pourquoi : docs/decisions/bibliotheque.md § Three carousel measurements, all corrected from a screenshot
         */
        val sidePad = ((maxWidth - cardSize) / 2).coerceAtLeast(16.dp)

        LaunchedEffect(cardSize) { reveal(cursor) }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(
                start = sidePad,
                end = sidePad,
                // The card is centred, not the column: the title's room is
                // *moved* bottom to top, and only half of it.
                // pourquoi : docs/decisions/bibliotheque.md § Three carousel measurements, all corrected from a screenshot
                top = contentPadding.calculateTopPadding() + CAROUSEL_TITLE_ROOM / 2,
                bottom = (contentPadding.calculateBottomPadding() - CAROUSEL_TITLE_ROOM / 2)
                    .coerceAtLeast(16.dp)
            ),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(count = entries.size, key = { entries[it].key }) { i ->
                val entry = entries[i]
                // Without it one cursor step recomposes every visible card to change two.
                val active by remember(i) { derivedStateOf { i == cursorState.value } }
                // Equally-sized cards read as a one-line grid, nothing pointing at the
                // one about to be launched.
                val recede by animateFloatAsState(
                    targetValue = if (active) 1f else 0.86f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "carousel-recede"
                )
                Box(
                    modifier = Modifier
                        .width(cardSize)
                        .scale(recede)
                        .alpha(if (active) 1f else 0.62f)
                ) {
                    // pourquoi : docs/decisions/bibliotheque.md § The carousel has to follow the finger without turning on the gamepad
                    val onTap = { if (active) onSelect(entry) else moveTo(i); Unit }
                    when (entry) {
                        is Entry.Folder -> FolderTile(
                            folder = entry,
                            onClick = onTap,
                            selected = padFocused && active,
                            padHeld = hold.down && active
                        )
                        is Entry.Game -> RomTile(
                            rom = entry.rom,
                            onClick = onTap,
                            onLongClick = { onLongPress(entry.rom) },
                            selected = padFocused && active,
                            padHeld = hold.down && active,
                            menuOpen = menuFor?.uri == entry.rom.uri,
                            onChangeIcon = { onMenuAction(entry.rom, TileAction.ICON) },
                            onRename = { onMenuAction(entry.rom, TileAction.RENAME) },
                            onHide = { onMenuAction(entry.rom, TileAction.HIDE) },
                            onDismissMenu = onDismissMenu,
                            titleDrop = CAROUSEL_TITLE_DROP
                        )
                    }
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val MARGIN_PX = with(density) { 14.dp.roundToPx() }
    // The strip the bottom veil paints back over the list; layout does not know it.
    val VEIL_PX = with(density) {
        (WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
            .calculateBottomPadding() + 14.dp).roundToPx()
    }
    val cursorState = rememberSaveable { mutableStateOf(0) }
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
            val safeTop = info.beforeContentPadding + MARGIN_PX
            val safeBottom = info.viewportEndOffset - info.viewportStartOffset -
                info.afterContentPadding - MARGIN_PX - VEIL_PX

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
                derivedStateOf { padFocusedState.value && i == cursorState.value }
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
                            modifier = Modifier.fillMaxSize().background(consolePlate(entry.console)),
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
                            contentScale = if (art.isPixelArt) ContentScale.Fit else ContentScale.Crop,
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
 * pourquoi : docs/decisions/bibliotheque.md § The console folders
 */
@Composable
private fun FolderTile(
    folder: Entry.Folder,
    onClick: () -> Unit,
    selected: Boolean,
    padHeld: Boolean
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Same clock as the ring, and gone the instant the cursor leaves; see the ROM tile.
    val mark by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(if (selected) RING_IN_MS else 0),
        label = "folder-mark"
    )
    val focusScale = 1f + 0.07f * mark
    val scale by animateFloatAsState(
        targetValue = if (pressed || padHeld) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "folder-scale"
    )
    // pourquoi : docs/decisions/theme-duotone-shelves.md § The diagonal staircase
    val riseX = TILE_RISE * mark
    val riseY = TILE_RISE * mark

    Column(
        modifier = Modifier.fillMaxWidth().zIndex(if (selected) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = -riseX, y = -riseY)
                .aspectRatio(1f)
                .scale(scale * focusScale)
                .shadow(
                    elevation = 8.dp,
                    shape = TileShape,
                    // Never clips, for the same reason as the game tile.
                    clip = false,
                    spotColor = if (selected) ringColor() else InkText.copy(alpha = 0.30f),
                    ambientColor = InkText.copy(alpha = 0.22f)
                )
                .focusRing(selected, TileShape)
                .clip(TileShape)
                .background(consolePlate(folder.console))
                .moldedRim(TileShape, dark = LocalEmufiiDarkTheme.current, oled = LocalEmufiiOledTheme.current)
                .focusProperties { canFocus = false }
                .tap(interactionSource = interaction, indication = null, onClick = onClick)
                .gamepadClick(interaction, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val plate = consoleArtwork(folder.console, LocalEmufiiDarkTheme.current)
            if (plate != null) {
                Image(
                    painter = painterResource(plate),
                    contentDescription = folder.console.label,
                    // The tile is square like the source, so nothing is cut; Fit leaves a
                    // hairline of gradient whatever rounding the grid gives the cell.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Its own ground: a bare label was legible on three consoles out of seven.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .clip(PillShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        gameCount(folder.roms.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            } else {
                // A console added later must not land on an empty tile.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(
                        text = folder.console.label,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 13.sp,
                            maxFontSize = MaterialTheme.typography.headlineSmall.fontSize,
                            stepSize = 0.5.sp
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                    )
                    Text(
                        gameCount(folder.roms.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // pourquoi : docs/decisions/bibliotheque.md § The console folders
        Spacer(Modifier.height(32.dp))
    }
}


/**
 * Indexed by name, so its colour never moves between launches.
 * pourquoi : docs/decisions/bibliotheque.md § The console folders
 */
@Composable
private fun consolePlate(console: Console): Brush {
    val (c1, c2) = paletteFor(console.name)
    return Brush.linearGradient(colors = listOf(c1, c2), start = Offset.Zero, end = Offset.Infinite)
}

@Composable
private fun gameCount(n: Int): String =
    if (n == 1) stringResource(R.string.lib_folder_count_one)
    else stringResource(R.string.lib_folder_count, n)

/**
 * Back and B do the same; this exists for the hand touching the screen.
 * pourquoi : docs/decisions/bibliotheque.md § The console folders
 */

/**
 * Layouts without columns keep [RIGHT], where the app leads.
 * pourquoi : docs/decisions/bibliotheque.md § Leaving through the top is named, and depends on the column
 */
private enum class HeaderSide { LEFT, RIGHT }

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

private inline fun androidx.compose.foundation.lazy.grid.LazyGridScope.items(
    count: Int,
    noinline key: ((Int) -> Any)? = null,
    crossinline itemContent: @Composable (Int) -> Unit
) = items(count = count, key = key) { index -> itemContent(index) }

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
            delay(HEADER_RELEASE_MS)
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
                        androidx.compose.animation.core.tween(
                            durationMillis = 120,
                            delayMillis = 100,
                            easing = androidx.compose.animation.core.LinearOutSlowInEasing
                        )
                    ).togetherWith(
                        androidx.compose.animation.fadeOut(
                            androidx.compose.animation.core.tween(
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
                    androidx.compose.foundation.layout.Row(
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
                modifier = Modifier.socket(PillShape, shelfDark).padding(SHELF_INSET)
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
private fun RomTile(
    rom: Rom,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean,
    padHeld: Boolean,
    menuOpen: Boolean,
    onChangeIcon: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onDismissMenu: () -> Unit,
    /** Zero in the grid; see [CAROUSEL_TITLE_DROP]. */
    titleDrop: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused = selected

    // Keyed on the ROM: a rescan replays the arrival for what changed, a recomposition
    // does not. Composed with it already over unless the screen has just opened.
    val playEntrance = LocalTileEntrance.current
    var shown by remember(rom.uri) { mutableStateOf(!playEntrance) }
    LaunchedEffect(rom.uri) { shown = true }



    // A bouncy spring split the cursor into two halves for a few frames; one animation
    // for the three marks.
    // pourquoi : docs/decisions/bibliotheque.md § One clock for everything that marks the cell
    // pourquoi : docs/decisions/bibliotheque.md § One animation for the cursor's three marks
    val mark by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(if (focused) RING_IN_MS else 0),
        label = "tile-mark"
    )
    val focusScale = 1f + 0.07f * mark

    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "tile-entrance"
    )

    val scale by animateFloatAsState(
        targetValue = if (pressed || padHeld) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tile-scale"
    )
    val elevation by animateFloatAsState(
        targetValue = if (pressed || padHeld) 2f else 8f,
        label = "tile-elev"
    )

    // Towards the top-left, the logo's own step; on the ring's clock and gone with it.
    // pourquoi : docs/decisions/theme-duotone-shelves.md § The diagonal staircase
    val riseX = TILE_RISE * mark
    val riseY = TILE_RISE * mark

    val lit = focused && entrance > 0.99f

    Column(
        // Above its neighbours while enlarged, or the next one draws over it and cuts
        // the glow clean off.
        modifier = Modifier.fillMaxWidth().zIndex(if (focused) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pulled from the artwork: the chrome stays neutral, the content brings the
        // palette. No colour to borrow, plain shadow.
        val accent = rom.accentArgb?.let { Color(it) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = -riseX, y = -riseY)
                .aspectRatio(1f)
                .scale(scale * focusScale * (0.88f + 0.12f * entrance))
                // `graphicsLayer`, never `alpha`: under 1, `alpha` lays a rectangular
                // clip that squares off the ring.
                // pourquoi : docs/decisions/navigation-manette.md § `Modifier.alpha` clips, and that is what made the cursor square
                .graphicsLayer { this.alpha = entrance }
                .shadow(
                    elevation = (elevation + if (accent != null) 10f else 0f).dp,
                    shape = TileShape,
                    // Never clips. `shadow` defaults to `clip = elevation > 0`, which
                    // cut the ring, since the ring surrounds the tile from outside.
                    // pourquoi : docs/decisions/navigation-manette.md § The ring surrounds, it does not clip
                    clip = false,
                    // Warm ink, never blue-black: the glow reads as light under the
                    // tile, not a coloured outline.
                    ambientColor = InkText.copy(alpha = 0.22f),
                    // pourquoi : docs/decisions/theme-duotone-shelves.md § GAMEPAD FOCUS
                    spotColor = (if (focused) ringColor() else accent)
                        ?: InkText.copy(alpha = 0.30f)
                )
                // Never on a tile still fading in: a glow is a shadow, and it draws
                // through a translucent layer. Thinner than elsewhere, so the cursor
                // circles the cover art without disputing the cell.
                // pourquoi : docs/decisions/bibliotheque.md § One clock for everything that marks the cell
                .focusRing(lit, TileShape, bandFraction = TILE_BAND)
                .clip(TileShape)
                .background(tilePlate())
                // Over the artwork: box art running to the corner turns the tile
                // back into a printed square.
                .moldedRim(TileShape, dark = LocalEmufiiDarkTheme.current, oled = LocalEmufiiOledTheme.current)
                // Clickable but NEVER focusable: the grid holds the cursor, so a
                // tile capturing focus makes it vanish.
                // pourquoi : docs/decisions/bibliotheque.md § The cursor is a computed index, never a guessed focus
                .focusProperties { canFocus = false }
                .tapOrHold(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .gamepadClick(interaction, onClick = onClick)
        ) {
            val art by rememberTileArt(rom)
            if (art.model != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(art.model).build(),
                    contentDescription = rom.displayName,
                    // The ROM's icon is left whole: at 48 px, cropping removes a visible
                    // part of the drawing.
                    contentScale = if (art.isPixelArt) ContentScale.Fit else ContentScale.Crop,
                    // Pixel art scales up without smoothing, or it turns to mush.
                    filterQuality =
                        if (art.isPixelArt) FilterQuality.None else FilterQuality.High,
                    // A thin white contour separates artwork from background whatever the
                    // box art is; wider, it reads as the white plate this used to have.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp)
                        .clip(ArtworkShape)
                        .border(2.dp, artworkRim(), ArtworkShape),
                    placeholder = ColorPainter(Color.Transparent),
                    error = ColorPainter(Color.Transparent)
                )
            } else {
                PlaceholderArtwork(rom.displayName)
            }

            // Inside the tile, the Popup's anchor, and never conditioned: it needs the
            // time to close.
            // pourquoi : docs/decisions/bibliotheque.md § Holding A, and the title that fades out
            TileMenu(
                expanded = menuOpen,
                title = rom.displayName,
                changeIconLabel = stringResource(R.string.tile_menu_icon),
                renameLabel = stringResource(R.string.tile_menu_rename),
                hideLabel = stringResource(R.string.tile_menu_hide),
                accent = accent,
                onChangeIcon = onChangeIcon,
                onRename = onRename,
                onHide = onHide,
                onDismiss = onDismissMenu
            )

            // 9 dp, not 6: the tile carries a moulding, and at 6 dp the pill bit into it.
            // pourquoi : docs/decisions/bibliotheque.md § The console badge is 9 dp from the edge, not 6
            ConsoleBadge(
                console = rom.console,
                modifier = Modifier.align(Alignment.BottomEnd).padding(BADGE_INSET)
            )

            // Opposite corner from the console badge: stacked, the pair reads as one
            // compound label.
            LocalCompatDb.current.ratingFor(rom.compatKeys())?.let { entry ->
                CompatBadge(
                    rating = entry.rating,
                    modifier = Modifier.align(Alignment.BottomStart).padding(BADGE_INSET)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TileTitle(
            rom.displayName,
            // On the ring's clock: the title moves aside while the cursor arrives.
            modifier = Modifier.graphicsLayer { translationY = titleDrop.toPx() * mark }
        )
    }
}

/**
 * Fades out at the end when it overflows. Two lines always reserved.
 * pourquoi : docs/decisions/bibliotheque.md § Holding A, and the title that fades out
 */
@Composable
private fun TileTitle(title: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    val boxHeight = TILE_TITLE_ROOM

    // Applied to every title it made a name that fitted look truncated, "Crash of the
    // Titans" losing "Titans".
    var overflows by remember(title) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(boxHeight)
            // The gradient applies to the text's rendering, hence the `DstIn` on a layer.
            // pourquoi : docs/decisions/performance-rendu.md § An offscreen layer is not a drawing setting
                        .then(
                if (overflows) {
                    Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                } else {
                    Modifier
                }
            )
            .drawWithContent {
                drawContent()
                if (overflows) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.55f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
    ) {
        Text(
            title,
            style = style,
            // Three lines in a box showing two, so an over-long title fades downwards
            // instead of stopping dead.
            maxLines = 3,
            // No Ellipsis: the dots eat three characters, and the fade already says it.
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            onTextLayout = { overflows = it.lineCount > 2 },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ConsoleBadge(console: Console, modifier: Modifier = Modifier) {
    // A dark translucent chip vanished on dark box art; the white contour holds over
    // artwork we do not control.
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = InkText,
        border = BorderStroke(1.5.dp, Color.White),
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Text(
            console.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PlaceholderArtwork(title: String) {
    val (c1, c2) = paletteFor(title)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(c1, c2),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = shortLabel(title),
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/**
 * Remixed from the logo's two axes and their neighbouring semantic tones: no invented
 * hue.
 * pourquoi : docs/decisions/theme-duotone-shelves.md § CONSTRAINTS (no hard-coded hex)
 */
private val PALETTE = listOf(
    Teal.bright to Teal.deep,
    Coral.bright to Coral.deep,
    Violet to VioletDark,
    GoodLight to Teal.ink,
    WarnLight to Coral.ink,
    InfoLight to Violet,
    Coral.darkBright to Coral.ink,
    Teal.darkBright to Teal.ink,
    VioletDark to Coral.ink,
    Coral.deep to Teal.ink,
    Teal.deep to Coral.ink
)

private fun paletteFor(seed: String): Pair<Color, Color> {
    val h = abs(seed.hashCode())
    return PALETTE[h % PALETTE.size]
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

/**
 * One place, called by all three cursor owners; restores the resting face.
 * pourquoi : docs/decisions/bibliotheque.md § What is published to the second screen
 */
@Composable
private fun PublishHovered(entries: List<Entry>, cursor: State<Int>) {
    // The only place outside the tiles subscribing to the cursor: it renders nothing,
    // so its recomposition costs only itself.
    val entry = entries.getOrNull(cursor.value)
    val hovered = (entry as? Entry.Game)?.rom
    val folder = (entry as? Entry.Folder)?.console
    val db = LocalCompatDb.current
    val meta = LocalGameMetaDb.current
    LaunchedEffect(hovered, folder, db, meta) {
        // Cancelled and restarted on each move: only what the player stopped on is
        // announced.
        // pourquoi : docs/decisions/bibliotheque.md § What is published to the second screen
        delay(SECOND_SCREEN_SETTLE_MS)
        SecondScreen.publish(
            folder?.let { SecondScreenModel.ConsoleFolder(it) } ?: hovered?.let { rom ->
                SecondScreenModel.Browsing(
                    rom = rom,
                    rating = db.ratingFor(rom.compatKeys())?.rating,
                    // Off the ROM the cursor is on, never off the disc: a move must not
                    // cost a file read.
                    tags = RomTagReader.read(rom),
                    meta = meta.metaFor(rom.compatKeys()),
                )
            } ?: SecondScreenModel.Idle
        )
    }
    DisposableEffect(Unit) { onDispose { SecondScreen.clear() } }
}

/**
 * Publishing wakes the second window, and a run down the grid fired one per tile.
 * pourquoi : docs/decisions/bibliotheque.md § What wakes the second screen has a threshold, and it was too short
 */
private const val SECOND_SCREEN_SETTLE_MS = 200L

/** Long enough to cover a focus handover between pills, which takes a frame. */
private const val HEADER_RELEASE_MS = 120L

