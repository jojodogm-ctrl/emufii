package eu.emufii.app.ui.screens.settings

import eu.emufii.app.compat.LocalCompatDb
import eu.emufii.app.ui.ShadowsFollow
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import eu.emufii.app.ui.Cascade
import eu.emufii.app.ui.bloom
import eu.emufii.app.ui.rememberAppear
import eu.emufii.app.ui.LocalScreenOpenedAt
import androidx.compose.runtime.key
import android.os.SystemClock
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
import eu.emufii.app.ui.components.BugMark
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
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SettingsScreen(
    profile: Profile,
    profileStore: ProfileStore,
    friendStore: FriendStore,
    settingsStore: SettingsStore,
    romsRepo: RomsRepository,
    libraryFolder: String?,
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

    var page by remember { mutableStateOf(SettingsPageId.HUB) }

    var name by remember(profile.id) {
        mutableStateOf(profile.name.takeIf { profile.isNamed } ?: "")
    }
    var photoError by remember { mutableStateOf<String?>(null) }
    var confirmingReset by remember { mutableStateOf(false) }

    val language by settingsStore.language.collectAsStateWithLifecycle()
    val theme by settingsStore.theme.collectAsStateWithLifecycle()
    val shareLastGame by settingsStore.shareLastGame.collectAsStateWithLifecycle()
    val artworkKey by settingsStore.steamGridDbKey.collectAsStateWithLifecycle()
    val hiddenConsoles by settingsStore.hiddenConsoles.collectAsStateWithLifecycle()

    val ppssppConfig = remember(context) { PpssppConfigStore(context) }
    var ppssppConfigReady by remember { mutableStateOf(ppssppConfig.isReady()) }

    var ps2ProfileReady by remember { mutableStateOf(Ps2NetworkProfile.isReadyQuick(context)) }
    LaunchedEffect(Unit) { ps2ProfileReady = Ps2NetworkProfile.verifyReady(context) }

    var hiddenCount by remember { mutableStateOf(HiddenRoms(context).count()) }

    var artworkSample by remember { mutableStateOf<List<Rom>>(emptyList()) }
    val hideIncompatible by settingsStore.hideIncompatible.collectAsStateWithLifecycle()
    val compatDb = LocalCompatDb.current
    var incompatibleCount by remember { mutableStateOf(0) }
    LaunchedEffect(libraryCount, compatDb) {
        incompatibleCount = withContext(Dispatchers.IO) {
            runCatching { romsRepo.cachedOrScan() }.getOrDefault(emptyList()).count(compatDb::isBroken)
        }
    }
    LaunchedEffect(libraryCount) {
        artworkSample = withContext(Dispatchers.IO) {
            runCatching { romsRepo.cachedOrScan() }.getOrDefault(emptyList())
                .filter { it.iconFile != null }
                .take(ARTWORK_SAMPLE)
        }
    }

    val autofillLauncher = remember { AzaharLauncher(context) }
    var autofillOn by remember { mutableStateOf(autofillLauncher.isNetplayAutomationEnabled()) }
    LaunchedEffect(Unit) {
        while (true) {
            autofillOn = autofillLauncher.isNetplayAutomationEnabled()
            delay(700.milliseconds)
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

    val face = settingsFace(
        page = page,
        displayName = playerDisplayName(name.ifBlank { Profile.DEFAULT_NAME }),
        hiddenConsoleCount = hiddenConsoles.size,
        themeLabel = stringResource(theme.labelRes),
        languageLabel = stringResource(language.labelRes),
    )
    LaunchedEffect(face) { face?.let { SecondScreen.publish(it) } }
    DisposableEffect(Unit) { onDispose { SecondScreen.clear() } }

    val toHub = { page = SettingsPageId.HUB }

    key(page) {
    val openedAt = remember { SystemClock.uptimeMillis() }
    val appear by rememberAppear()
    CompositionLocalProvider(LocalScreenOpenedAt provides openedAt) {
    ShadowsFollow({ appear }) {
    Box(Modifier.fillMaxSize().bloom({ appear }, blur = 0.dp)) {
    when (page) {
        SettingsPageId.HUB -> SettingsHub(
            profile = profile,
            name = name,
            libraryFolder = libraryFolder,
            libraryCount = libraryCount,
            libraryScanning = libraryScanning,
            hiddenConsoleCount = hiddenConsoles.size,
            emulatorsReady = listOf(ppssppConfigReady, ps2ProfileReady, autofillOn).count { it },
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
            shareLastGame = shareLastGame,
            onSetShareLastGame = settingsStore::setShareLastGame,
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
            hideIncompatible = hideIncompatible,
            incompatibleCount = incompatibleCount,
            onSetHideIncompatible = settingsStore::setHideIncompatible,
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

        SettingsPageId.CRASH_LOGS -> CrashLogsPage(onBack = toHub, modifier = modifier)
    }
    }
    }
    }
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
                        friendStore.clear()
                        profileStore.reset()
                        // The WireGuard key is a stable identifier the coordinator sees; it must not outlive the profile.
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

@Composable
private fun settingsFace(
    page: SettingsPageId,
    displayName: String,
    hiddenConsoleCount: Int,
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
        SettingsPageId.CRASH_LOGS -> face(
            stringResource(R.string.settings_page_crash_logs),
            stringResource(R.string.settings_sub_crash_logs),
            PanelMark.CRASH_LOGS
        )
    }
}

internal enum class SettingsPageId {
    HUB, PROFILE, LIBRARY, CONSOLES, EMULATORS, APPEARANCE, GENERAL, ABOUT, CRASH_LOGS
}

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

    // No clear on exit: the page being opened republishes its face, a clear here would erase it.
    SettingsPage(
        title = root,
        onBack = onBack,
        modifier = modifier
    ) {
        val displayName = playerDisplayName(name.ifBlank { Profile.DEFAULT_NAME })

        @Composable
        fun faceOf(page: SettingsPageId) = settingsFace(
            page = page,
            displayName = displayName,
            hiddenConsoleCount = hiddenConsoleCount,
            themeLabel = themeLabel,
            languageLabel = languageLabel,
        )!!

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
            },
            { first, mod ->
                val face = faceOf(SettingsPageId.CRASH_LOGS)
                SettingsEntry(
                    label = face.title,
                    summary = face.summary,
                    onOpen = { onOpen(SettingsPageId.CRASH_LOGS) },
                    entry = first,
                    modifier = mod,
                    icon = { BugMark(color = it) },
                    onFocused = { if (it) SecondScreen.publish(face) }
                )
            }
        )

        HubGrid(entries)
    }
}

@Composable
private fun HubGrid(entries: List<@Composable (Boolean, Modifier) -> Unit>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(HUB_GAP),
        modifier = Modifier.fillMaxWidth()
    ) {
        entries.chunked(HUB_COLUMNS).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(HUB_GAP)) {
                chunk.forEachIndexed { column, entry ->
                    Cascade(
                        row * HUB_COLUMNS + column,
                        Modifier.weight(1f).height(HUB_TILE_HEIGHT)
                    ) {
                        entry(row == 0 && column == 0, Modifier.fillMaxSize())
                    }
                }
                repeat(HUB_COLUMNS - chunk.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private const val HUB_COLUMNS = 2

private val HUB_GAP = 12.dp

private val HUB_TILE_HEIGHT = 92.dp

private const val EMULATOR_STEPS = 3
