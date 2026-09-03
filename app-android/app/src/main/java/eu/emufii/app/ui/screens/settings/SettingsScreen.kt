package eu.emufii.app.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.emufii.app.BuildConfig
import eu.emufii.app.R
import eu.emufii.app.azahar.AzaharLauncher
import eu.emufii.app.library.Console
import eu.emufii.app.library.HiddenRoms
import eu.emufii.app.library.Rom
import eu.emufii.app.library.RomsRepository
import eu.emufii.app.profile.FriendStore
import eu.emufii.app.profile.Profile
import eu.emufii.app.profile.ProfileStore
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.ps2.Ps2NetworkProfile
import eu.emufii.app.psp.PpssppConfigStore
import eu.emufii.app.secondscreen.PanelMark
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.SecondScreenModel
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.components.Avatar
import eu.emufii.app.ui.components.ChipMark
import eu.emufii.app.ui.components.DetailTone
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.GridMark
import eu.emufii.app.ui.components.InfoMark
import eu.emufii.app.ui.components.PadDialog
import eu.emufii.app.ui.components.PadDialogText
import eu.emufii.app.ui.components.PaintMark
import eu.emufii.app.ui.components.ShelfMark
import eu.emufii.app.ui.components.SlidersMark
import eu.emufii.app.ui.components.labelRes
import eu.emufii.app.wg.WgKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A hub holding nothing but entries, and seven pages: it is crossed, not read.
 * pourquoi : docs/decisions/reglages-ecran.md § One hub and seven pages, plus an accordion
 */
@Composable
fun SettingsScreen(
    profile: Profile,
    profileStore: ProfileStore,
    friendStore: FriendStore,
    settingsStore: SettingsStore,
    romsRepo: RomsRepository,
    libraryFolder: String?,
    /** Adds to the first, never replaces it. */
    librarySecondFolder: String?,
    libraryScanning: Boolean,
    libraryCount: Int?,
    onFolderPicked: (Uri) -> Unit,
    onSecondFolderPicked: (Uri) -> Unit,
    onSecondFolderRemoved: () -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    /** The hub is this screen's root, not one more page. */
    var page by remember { mutableStateOf(SettingsPageId.HUB) }

    var name by remember(profile.id) {
        mutableStateOf(profile.name.takeIf { profile.isNamed } ?: "")
    }
    var photoError by remember { mutableStateOf<String?>(null) }
    var confirmingReset by remember { mutableStateOf(false) }

    val language by settingsStore.language.collectAsStateWithLifecycle()
    val theme by settingsStore.theme.collectAsStateWithLifecycle()
    val artworkKey by settingsStore.steamGridDbKey.collectAsStateWithLifecycle()
    val hiddenConsoles by settingsStore.hiddenConsoles.collectAsStateWithLifecycle()

    val ppssppConfig = remember(context) { PpssppConfigStore(context) }
    var ppssppConfigReady by remember { mutableStateOf(ppssppConfig.isReady()) }

    // Nothing here can check whether the player imported it into ARMSX2. Cheap answer
    // first, confirmed off the main thread: opening the settings must not wait 175 ms of
    // card reading.
    var ps2ProfileReady by remember { mutableStateOf(Ps2NetworkProfile.isReadyQuick(context)) }
    LaunchedEffect(Unit) { ps2ProfileReady = Ps2NetworkProfile.verifyReady(context) }

    var hiddenCount by remember { mutableStateOf(HiddenRoms(context).count()) }

    // From the cache warmed at startup, off the main thread, and only those carrying an
    // image: a strip of empty plates would show nothing.
    // pourquoi : docs/decisions/reglages-ecran.md § The pages' images come from the device, not from a stock library
    var artworkSample by remember { mutableStateOf<List<Rom>>(emptyList()) }
    LaunchedEffect(libraryCount) {
        artworkSample = withContext(Dispatchers.IO) {
            runCatching { romsRepo.cachedOrScan() }.getOrDefault(emptyList())
                .filter { it.iconFile != null }
                .take(ARTWORK_SAMPLE)
        }
    }

    // Re-read while the screen is up: the answer only exists on return from Android's
    // settings.
    // pourquoi : docs/decisions/reglages-ecran.md § The status lines, and what nobody would guess
    val autofillLauncher = remember { AzaharLauncher(context) }
    var autofillOn by remember { mutableStateOf(autofillLauncher.isNetplayAutomationEnabled()) }
    LaunchedEffect(Unit) {
        while (true) {
            autofillOn = autofillLauncher.isNetplayAutomationEnabled()
            delay(700)
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) photoError = profileStore.setAvatar(uri).exceptionOrNull()?.message
    }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) onFolderPicked(uri) }
    val secondFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> if (uri != null) onSecondFolderPicked(uri) }

    /** The nickname is written on leaving the settings, not on every keystroke. */
    val leave = {
        profileStore.setName(name)
        onBack()
    }

    // A page is a sub-level: B returns to the hub before leaving the screen.
    BackHandler(enabled = page != SettingsPageId.HUB) { page = SettingsPageId.HUB }

    /**
     * The hub publishes the aimed tile and clears on the way out; nothing is published on
     * the hub itself: two publishers for one face.
     * pourquoi : docs/decisions/reglages-ecran.md § The hub is a grid, and the panel shows the selected cell
     */
    val face = settingsFace(
        page = page,
        displayName = playerDisplayName(name.ifBlank { Profile.DEFAULT_NAME }),
        libraryFolder = libraryFolder,
        libraryCount = libraryCount,
        libraryScanning = libraryScanning,
        hiddenConsoleCount = hiddenConsoles.size,
        emulatorsReady = listOf(ppssppConfigReady, ps2ProfileReady, autofillOn).count { it },
        themeLabel = stringResource(theme.labelRes),
        languageLabel = stringResource(language.labelRes),
    )
    LaunchedEffect(face) { face?.let { SecondScreen.publish(it) } }
    // Leaving from a page must not leave a settings face lit: the hub's own net is not
    // there while a page is open.
    DisposableEffect(Unit) { onDispose { SecondScreen.clear() } }

    val toHub = { page = SettingsPageId.HUB }

    when (page) {
        SettingsPageId.HUB -> SettingsHub(
            profile = profile,
            name = name,
            libraryFolder = libraryFolder,
            libraryCount = libraryCount,
            libraryScanning = libraryScanning,
            hiddenConsoleCount = hiddenConsoles.size,
            emulatorsReady = listOf(ppssppConfigReady, ps2ProfileReady, autofillOn).count { it },
            // The configurable accent is gone: the row now names the theme alone.
            // theme. pourquoi : theme-duotone-shelves.md § Réglages
            themeLabel = stringResource(theme.labelRes),
            languageLabel = stringResource(language.labelRes),
            onOpen = { page = it },
            onBack = leave,
            modifier = modifier
        )

        SettingsPageId.PROFILE -> ProfilePage(
            profile = profile,
            name = name,
            onNameChange = { name = it },
            photoError = photoError,
            onPickPhoto = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClearPhoto = { profileStore.clearAvatar() },
            onReset = { confirmingReset = true },
            onBack = toHub,
            modifier = modifier
        )

        SettingsPageId.LIBRARY -> LibraryPage(
            folder = libraryFolder,
            secondFolder = librarySecondFolder,
            scanning = libraryScanning,
            count = libraryCount,
            onPickFolder = { folderPicker.launch(null) },
            onPickSecondFolder = { secondFolderPicker.launch(null) },
            onRemoveSecondFolder = onSecondFolderRemoved,
            onRescan = onRescan,
            artworkKey = artworkKey,
            onArtworkKeyChange = { settingsStore.setSteamGridDbKey(it) },
            artworkSample = artworkSample,
            hiddenCount = hiddenCount,
            onRestoreHidden = {
                HiddenRoms(context).clear()
                hiddenCount = 0
            },
            onBack = toHub,
            modifier = modifier
        )

        SettingsPageId.CONSOLES -> ConsolesPage(
            hidden = hiddenConsoles,
            onSetVisible = { console, visible -> settingsStore.setConsoleVisible(console, visible) },
            onBack = toHub,
            modifier = modifier
        )

        SettingsPageId.EMULATORS -> EmulatorsPage(
            ppssppConfig = ppssppConfig,
            ppssppReady = ppssppConfigReady,
            onPpssppReadyChanged = { ppssppConfigReady = it },
            ps2Ready = ps2ProfileReady,
            profileName = name.ifBlank { Profile.DEFAULT_NAME },
            onPs2ReadyChanged = { ps2ProfileReady = it },
            autofillOn = autofillOn,
            onOpenAutofill = { autofillLauncher.openAccessibilitySettings() },
            onBack = toHub,
            modifier = modifier
        )

        SettingsPageId.APPEARANCE -> AppearancePage(
            theme = theme,
            onTheme = settingsStore::setTheme,
            onBack = toHub,
            modifier = modifier
        )

        SettingsPageId.GENERAL -> GeneralPage(
            settingsStore = settingsStore,
            language = language,
            onBack = toHub,
            modifier = modifier
        )

        SettingsPageId.ABOUT -> AboutPage(onBack = toHub, modifier = modifier)
    }

    if (confirmingReset) {
        val done = stringResource(R.string.profile_reset_done)
        PadDialog(
            title = stringResource(R.string.profile_reset),
            onDismiss = { confirmingReset = false },
            actions = {
                GhostButton(
                    label = stringResource(R.string.friends_cancel),
                    onClick = { confirmingReset = false }
                )
                GhostButton(
                    label = stringResource(R.string.profile_reset),
                    onClick = {
                        // Both, always: the friends list is indexed on an identity that
                        // no longer exists, and leaving it would show rows that never
                        // come back online.
                        friendStore.clear()
                        profileStore.reset()
                        // The WireGuard public key is a stable identifier the
                        // coordinator sees; leaving it would outlive the profile it
                        // belonged to.
                        WgKeys.reset(context)
                        name = ""
                        confirmingReset = false
                        Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
                    },
                    tint = dangerInk()
                )
            }
        ) {
            PadDialogText(stringResource(R.string.profile_reset_confirm))
        }
    }
}

private const val ARTWORK_SAMPLE = 5

/**
 * One source for both moments, the tile and the page, or they tell two stories.
 * `@Composable` because everything in it is translated.
 * pourquoi : docs/decisions/reglages-ecran.md § The hub is a grid, and the panel shows the selected cell
 */
@Composable
private fun settingsFace(
    page: SettingsPageId,
    displayName: String,
    libraryFolder: String?,
    libraryCount: Int?,
    libraryScanning: Boolean,
    hiddenConsoleCount: Int,
    emulatorsReady: Int,
    themeLabel: String,
    languageLabel: String,
): SecondScreenModel.SettingsEntry? {
    val root = stringResource(R.string.settings_title)
    fun face(title: String, summary: String, mark: PanelMark, social: Boolean = false) =
        SecondScreenModel.SettingsEntry(
            title = title,
            summary = summary,
            root = root,
            mark = mark,
            social = social
        )
    return when (page) {
        // The hub has no face of its own: the aimed tile speaks.
        SettingsPageId.HUB -> null
        SettingsPageId.PROFILE -> face(
            stringResource(R.string.settings_page_profile),
            displayName,
            PanelMark.PROFILE,
            social = true
        )
        SettingsPageId.LIBRARY -> face(
            stringResource(R.string.settings_page_library),
            stringResource(R.string.settings_sub_library),
            PanelMark.LIBRARY
        )
        SettingsPageId.CONSOLES -> face(
            stringResource(R.string.settings_page_consoles),
            stringResource(
                R.string.settings_pill_consoles,
                Console.entries.size - hiddenConsoleCount,
                Console.entries.size
            ),
            PanelMark.CONSOLES
        )
        SettingsPageId.EMULATORS -> face(
            stringResource(R.string.settings_page_emulators),
            stringResource(R.string.settings_sub_emulators),
            PanelMark.EMULATORS
        )
        SettingsPageId.APPEARANCE -> face(
            stringResource(R.string.settings_page_appearance),
            themeLabel,
            PanelMark.APPEARANCE
        )
        SettingsPageId.GENERAL -> face(
            stringResource(R.string.settings_page_general),
            languageLabel + " · " + stringResource(R.string.settings_sub_general),
            PanelMark.GENERAL
        )
        SettingsPageId.ABOUT -> face(
            stringResource(R.string.settings_page_about),
            BuildConfig.VERSION_NAME,
            PanelMark.ABOUT
        )
    }
}

internal enum class SettingsPageId {
    HUB, PROFILE, LIBRARY, CONSOLES, EMULATORS, APPEARANCE, GENERAL, ABOUT
}

/**
 * No setting changes here, and that is this page's only rule.
 * pourquoi : docs/decisions/reglages-ecran.md § One hub and seven pages, plus an accordion
 * pourquoi : docs/decisions/reglages-ecran.md § A hub entry is a plate, not a row
 */
@Composable
private fun SettingsHub(
    profile: Profile,
    name: String,
    libraryFolder: String?,
    libraryCount: Int?,
    libraryScanning: Boolean,
    hiddenConsoleCount: Int,
    emulatorsReady: Int,
    themeLabel: String,
    languageLabel: String,
    onOpen: (SettingsPageId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val root = stringResource(R.string.settings_title)

    // No clear on the way out: the page being opened republishes its category's face and
    // a `clear` here would erase it just after; the screen as a whole puts the panel out.
    // pourquoi : docs/decisions/reglages-ecran.md § The hub is a grid, and the panel shows the selected cell

    SettingsPage(
        title = root,
        onBack = onBack,
        modifier = modifier
    ) {
        val displayName = playerDisplayName(name.ifBlank { Profile.DEFAULT_NAME })

        // Exactly what the page republishes on opening: hovering Library then entering it
        // must change nothing on the panel.
        @Composable
        fun faceOf(page: SettingsPageId) = settingsFace(
            page = page,
            displayName = displayName,
            libraryFolder = libraryFolder,
            libraryCount = libraryCount,
            libraryScanning = libraryScanning,
            hiddenConsoleCount = hiddenConsoleCount,
            emulatorsReady = emulatorsReady,
            themeLabel = themeLabel,
            languageLabel = languageLabel,
        )!!

        // The family headings went with the column: seven tiles are found by name.
        // pourquoi : docs/decisions/reglages-ecran.md § The hub is a grid, and the panel shows the selected cell
        val entries = listOf<@Composable (Boolean, Modifier) -> Unit>(
            { first, mod ->
                val face = faceOf(SettingsPageId.PROFILE)
                val label = face.title
                val summary = face.summary
                SettingsEntry(
                    label = label,
                    summary = summary,
                    onOpen = { onOpen(SettingsPageId.PROFILE) },
                    entry = first,
                    modifier = mod,
                    domain = EntryDomain.SOCIAL,
                    // The avatar stands in for the mark: the only entry whose state is an
                    // image, and the hub's only colour, coming from content not chrome.
                    leading = {
                        Avatar(name = displayName, imageFile = profile.avatarFile, size = 34.dp)
                    },
                    onFocused = { if (it) SecondScreen.publish(face) }
                )
            },
            { first, mod ->
                val face = faceOf(SettingsPageId.LIBRARY)
                val label = face.title
                val summary = face.summary
                SettingsEntry(
                    label = label,
                    summary = summary,
                    onOpen = { onOpen(SettingsPageId.LIBRARY) },
                    entry = first,
                    modifier = mod,
                    icon = { ShelfMark(color = it) },
                    state = when {
                        libraryScanning -> EntryState(
                            DetailTone.BUSY,
                            stringResource(R.string.settings_pill_scanning)
                        )
                        libraryFolder == null -> EntryState(
                            DetailTone.WARN,
                            stringResource(R.string.settings_pill_no_folder)
                        )
                        else -> EntryState(
                            DetailTone.GOOD,
                            libraryCount?.let {
                                pluralStringResource(R.plurals.settings_pill_games, it, it)
                            } ?: stringResource(R.string.settings_pill_ready)
                        )
                    },
                    onFocused = { if (it) SecondScreen.publish(face) }
                )
            },
            { first, mod ->
                // No pill: hiding a console is a taste, not a state to catch up on, and a
                // green one would say "nothing to do" on a page where there never is.
                val face = faceOf(SettingsPageId.CONSOLES)
                val label = face.title
                val summary = face.summary
                SettingsEntry(
                    label = label,
                    summary = summary,
                    onOpen = { onOpen(SettingsPageId.CONSOLES) },
                    entry = first,
                    modifier = mod,
                    icon = { GridMark(color = it) },
                    onFocused = { if (it) SecondScreen.publish(face) }
                )
            },
            { first, mod ->
                val face = faceOf(SettingsPageId.EMULATORS)
                val label = face.title
                val summary = face.summary
                SettingsEntry(
                    label = label,
                    summary = summary,
                    onOpen = { onOpen(SettingsPageId.EMULATORS) },
                    entry = first,
                    modifier = mod,
                    icon = { ChipMark(color = it) },
                    state = EntryState(
                        // Green only once all three preparations are done: "2 / 3" in
                        // green would read as nothing to do.
                        if (emulatorsReady == EMULATOR_STEPS) DetailTone.GOOD else DetailTone.WARN,
                        stringResource(R.string.settings_pill_ratio, emulatorsReady, EMULATOR_STEPS)
                    ),
                    onFocused = { if (it) SecondScreen.publish(face) }
                )
            },
            { first, mod ->
                val face = faceOf(SettingsPageId.APPEARANCE)
                SettingsEntry(
                    label = face.title,
                    summary = face.summary,
                    onOpen = { onOpen(SettingsPageId.APPEARANCE) },
                    entry = first,
                    modifier = mod,
                    icon = { PaintMark(color = it) },
                    onFocused = { if (it) SecondScreen.publish(face) }
                )
            },
            { first, mod ->
                val face = faceOf(SettingsPageId.GENERAL)
                val label = face.title
                val summary = face.summary
                SettingsEntry(
                    label = label,
                    summary = summary,
                    onOpen = { onOpen(SettingsPageId.GENERAL) },
                    entry = first,
                    modifier = mod,
                    icon = { SlidersMark(color = it) },
                    onFocused = { if (it) SecondScreen.publish(face) }
                )
            },
            { first, mod ->
                val face = faceOf(SettingsPageId.ABOUT)
                SettingsEntry(
                    label = face.title,
                    summary = face.summary,
                    onOpen = { onOpen(SettingsPageId.ABOUT) },
                    entry = first,
                    modifier = mod,
                    icon = { InfoMark(color = it) },
                    onFocused = { if (it) SecondScreen.publish(face) }
                )
            }
        )

        HubGrid(entries)
    }
}

/**
 * Nothing lazy here: all seven tiles are composed, so focus traversal always finds its
 * destination.
 * pourquoi : docs/decisions/reglages-ecran.md § Two columns, and it goes down, never sideways
 * pourquoi : docs/decisions/reglages-ecran.md § The hub is a grid, and the panel shows the selected cell
 */
@Composable
private fun HubGrid(entries: List<@Composable (Boolean, Modifier) -> Unit>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(HUB_GAP),
        modifier = Modifier.fillMaxWidth()
    ) {
        entries.chunked(HUB_COLUMNS).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(HUB_GAP)) {
                chunk.forEachIndexed { column, entry ->
                    entry(
                        row == 0 && column == 0,
                        Modifier.weight(1f).height(HUB_TILE_HEIGHT)
                    )
                }
                // The incomplete row keeps its missing places: without them the last tile
                // stretches over two widths and reads as more important.
                repeat(HUB_COLUMNS - chunk.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private const val HUB_COLUMNS = 2

private val HUB_GAP = 12.dp

/**
 * One height for every tile: a two-line summary would grow its own tile and break the
 * row's alignment.
 * pourquoi : docs/decisions/reglages-ecran.md § Two columns, and it goes down, never sideways
 */
private val HUB_TILE_HEIGHT = 92.dp

/** PPSSPP, PS2, artwork. */
private const val EMULATOR_STEPS = 3
