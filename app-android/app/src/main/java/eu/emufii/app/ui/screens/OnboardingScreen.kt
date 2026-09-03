package eu.emufii.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.emufii.app.R
import eu.emufii.app.artwork.ArtworkFrontend
import eu.emufii.app.artwork.FrontendMedia
import eu.emufii.app.azahar.AzaharLauncher
import eu.emufii.app.library.Console
import eu.emufii.app.profile.Profile
import eu.emufii.app.ps2.Ps2NetworkProfile
import eu.emufii.app.psp.PpssppConfigStore
import eu.emufii.app.settings.SettingsStore
import eu.emufii.app.ui.components.ChipMark
import eu.emufii.app.ui.components.ConsoleGrid
import eu.emufii.app.ui.components.DetailActions
import eu.emufii.app.ui.components.DetailTone
import eu.emufii.app.ui.components.FolderMark
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.GridMark
import eu.emufii.app.ui.components.PadTextField
import eu.emufii.app.ui.components.PaintMark
import eu.emufii.app.ui.components.PersonMark
import eu.emufii.app.ui.components.PrimaryButton
import eu.emufii.app.ui.components.SignalMark
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.SteamGridDbMark
import eu.emufii.app.ui.components.waitTrim
import eu.emufii.app.ui.screens.settings.AutofillBlock
import eu.emufii.app.ui.screens.settings.BlockFact
import eu.emufii.app.ui.screens.settings.BlockNotice
import eu.emufii.app.ui.screens.settings.ChoiceRow
import eu.emufii.app.ui.screens.settings.PpssppBlock
import eu.emufii.app.ui.screens.settings.Ps2Block
import eu.emufii.app.ui.screens.settings.SettingsSteps
import eu.emufii.app.ui.screens.settings.StatePill
import eu.emufii.app.ui.theme.ArtworkShape
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.Teal
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.wallpaper.TrayBackdrop
import kotlinx.coroutines.delay
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The run has no fixed length: the emulator pages are drawn from what the player
 * answers on the consoles page.
 * pourquoi : docs/decisions/onboarding.md § The walkthrough has no fixed length
 */
@Composable
fun OnboardingScreen(
    initialName: String,
    onSetName: (String) -> Unit,
    onPickFolder: (Uri) -> Unit,
    onSetArtworkKey: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = LocalEmufiiDarkTheme.current
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore.get(context) }
    val hiddenConsoles by settingsStore.hiddenConsoles.collectAsStateWithLifecycle()
    val frontendFolder by settingsStore.frontendFolder.collectAsStateWithLifecycle()
    val artworkFrontend by settingsStore.artworkFrontend.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf(initialName) }
    var artworkKey by remember { mutableStateOf("") }
    val nameTooShort = name.trim().length < Profile.MIN_NAME_LENGTH

    var romFolder by remember { mutableStateOf<Uri?>(null) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            onPickFolder(uri)
            romFolder = uri
        }
    }

    // Read only, as in the settings: we look at the images the frontend has already
    // downloaded and write nothing into its folder.
    val frontendPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            settingsStore.setFrontendFolder(uri.toString())
            FrontendMedia.forget()
        }
    }

    // From the real permission rather than from whether the player pressed: it may
    // already be granted, and the button would then do nothing.
    var notificationsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationsRefused by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // A refusal showed the same tick as a grant: after two refusals Android stops
        // showing the prompt and the button never does anything again.
        notificationsGranted = granted
        notificationsRefused = !granted
    }

    val ppssppConfig = remember(context) { PpssppConfigStore(context) }
    var ppssppReady by remember { mutableStateOf(ppssppConfig.isReady()) }
    var ps2Ready by remember { mutableStateOf(Ps2NetworkProfile.isReadyQuick(context)) }
    LaunchedEffect(Unit) { ps2Ready = Ps2NetworkProfile.verifyReady(context) }

    // A round trip through Android's settings: there is no result to await, the answer
    // only shows on return, so we poll.
    val launcher = remember { AzaharLauncher(context) }
    var autofillOn by remember { mutableStateOf(launcher.isNetplayAutomationEnabled()) }

    // Held by value rather than index: hiding a console removes a page, and an index
    // would then point at the next one.
    val steps = remember(hiddenConsoles) { onboardingSteps(hiddenConsoles) }
    var current by remember { mutableStateOf(OnbStep.WELCOME) }
    val index = steps.indexOf(current).coerceAtLeast(0)
    val last = index == steps.lastIndex

    LaunchedEffect(current) {
        if (current == OnbStep.AUTOFILL) {
            while (true) {
                autofillOn = launcher.isNetplayAutomationEnabled()
                delay(700)
            }
        }
    }

    fun goNext() {
        if (current == OnbStep.NAME) onSetName(name.trim())
        if (current == OnbStep.ARTWORK) onSetArtworkKey(artworkKey.trim())
        if (last) onDone() else current = steps[index + 1]
    }

    fun goBack() {
        if (index > 0) current = steps[index - 1]
    }

    // Back is the system button and B; a third control would be one more to read.
    BackHandler(enabled = index > 0) { goBack() }

    // The fixed elements tighten too: at 468 dp tall their margins alone overflowed the
    // room available.
    val configuration = LocalConfiguration.current
    val shortScreen = configuration.screenHeightDp < 520
    val wide = configuration.screenWidthDp >= 720
    val gap = if (shortScreen) 12.dp else 18.dp
    val edge = if (shortScreen) 10.dp else 18.dp
    val actionHeight = if (shortScreen) 48.dp else 56.dp

    Box(modifier = modifier.fillMaxSize()) {
        TrayBackdrop(modifier = Modifier.fillMaxSize(), dark = dark)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = if (wide) 40.dp else 22.dp, vertical = edge),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepRail(current = index, total = steps.size, label = stringResource(current.railLabel))

            // The page scrolls, the button stays: the weight serves the button first.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        val forward = steps.indexOf(targetState) > steps.indexOf(initialState)
                        (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn())
                            .togetherWith(
                                slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut()
                            )
                    },
                    label = "onboarding-step"
                ) { shown ->
                    StepBody(
                        step = shown,
                        wide = wide,
                        name = name,
                        onNameChange = { name = it.take(Profile.MAX_NAME_LENGTH) },
                        nameTooShort = nameTooShort,
                        romFolder = romFolder,
                        onPickFolder = { folderPicker.launch(null) },
                        hiddenConsoles = hiddenConsoles,
                        onSetConsoleVisible = settingsStore::setConsoleVisible,
                        frontendFolder = frontendFolder,
                        artworkFrontend = artworkFrontend,
                        onSetFrontend = { option ->
                            if (option != artworkFrontend) {
                                settingsStore.setArtworkFrontend(option)
                                // A folder linked for the other layout finds nothing here.
                                settingsStore.setFrontendFolder("")
                                FrontendMedia.forget()
                            }
                        },
                        onPickFrontend = { frontendPicker.launch(defaultFolderOf(artworkFrontend)) },
                        onForgetFrontend = {
                            settingsStore.setFrontendFolder("")
                            FrontendMedia.forget()
                        },
                        artworkKey = artworkKey,
                        onArtworkKeyChange = { artworkKey = it },
                        ppssppConfig = ppssppConfig,
                        ppssppReady = ppssppReady,
                        onPpssppReady = { ppssppReady = it },
                        ps2Ready = ps2Ready,
                        onPs2Ready = { ps2Ready = it },
                        profileName = name,
                        autofillOn = autofillOn,
                        onOpenAutofill = { launcher.openAccessibilitySettings() },
                        notificationsGranted = notificationsGranted,
                        notificationsRefused = notificationsRefused,
                        onAskNotifications = {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                }
            }

            // Both exits on one line: stacked, they cost a row the page has not got.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryButton(
                    label = stringResource(
                        when {
                            current == OnbStep.WELCOME -> R.string.onb_start
                            last -> R.string.onb_finish
                            else -> R.string.onb_next
                        }
                    ),
                    onClick = { goNext() },
                    // The one page you cannot skip past: the nickname goes into the
                    // emulator's form exactly as typed.
                    // pourquoi : docs/decisions/onboarding.md § Everything can be skipped, except the nickname
                    enabled = current != OnbStep.NAME || !nameTooShort,
                    modifier = Modifier.weight(1f).height(actionHeight)
                )

                // Offered only where something is asked: the welcome page and the
                // summary have nothing to skip.
                if (current.skippable) {
                    GhostButton(
                        label = stringResource(R.string.onb_skip),
                        onClick = { goNext() },
                        modifier = Modifier.height(actionHeight)
                    )
                }
            }
        }
    }
}

/** Where the frontend keeps its images, so the picker opens in the right place. */
private fun defaultFolderOf(frontend: ArtworkFrontend): Uri = DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents",
    frontend.defaultFolderId
)

/** The enum's order is the run's order. */
private enum class OnbStep(val railLabel: Int, val skippable: Boolean = true) {
    WELCOME(R.string.onb_rail_welcome, skippable = false),
    NAME(R.string.onb_rail_name, skippable = false),
    FOLDER(R.string.onb_rail_folder),
    CONSOLES(R.string.onb_rail_consoles),
    COCOON(R.string.onb_rail_cocoon),
    ARTWORK(R.string.onb_rail_artwork),
    PPSSPP(R.string.onb_rail_ppsspp),
    PS2(R.string.onb_rail_ps2),
    AUTOFILL(R.string.onb_rail_autofill),
    NOTIF(R.string.onb_rail_notif),
    DONE(R.string.onb_rail_done, skippable = false),
}

/**
 * The consoles whose multiplayer goes through driving the emulator. Neither the PSP
 * (tunnel) nor the DS (DNS) has a form to fill.
 * pourquoi : docs/decisions/onboarding.md § The walkthrough has no fixed length
 */
private val AUTOMATED = setOf(
    Console.THREE_DS,
    Console.SWITCH,
    Console.GAMECUBE,
    Console.WII,
    Console.PS2,
)

private fun onboardingSteps(hidden: Set<Console>): List<OnbStep> = buildList {
    add(OnbStep.WELCOME)
    add(OnbStep.NAME)
    add(OnbStep.FOLDER)
    add(OnbStep.CONSOLES)
    add(OnbStep.COCOON)
    add(OnbStep.ARTWORK)
    if (Console.PSP !in hidden) add(OnbStep.PPSSPP)
    if (Console.PS2 !in hidden) add(OnbStep.PS2)
    if (AUTOMATED.any { it !in hidden }) add(OnbStep.AUTOFILL)
    add(OnbStep.NOTIF)
    add(OnbStep.DONE)
}

/**
 * The why on the left, the what-to-do on the right; stacked when narrow.
 * pourquoi : docs/decisions/onboarding.md § Two columns, and they do not say the same thing
 */
@Composable
private fun StepLayout(
    wide: Boolean,
    mark: @Composable () -> Unit,
    title: String,
    body: String,
    state: (@Composable () -> Unit)? = null,
    /**
     * The why becomes a banner. One page asks for it, the consoles page.
     * pourquoi : docs/decisions/onboarding.md § The consoles page takes the full width
     */
    fullWidthWork: Boolean = false,
    work: (@Composable () -> Unit)? = null,
) {
    val why: @Composable (Modifier) -> Unit = { m ->
        Column(
            modifier = m,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = if (wide && work != null) Alignment.Start else Alignment.CenterHorizontally
        ) {
            mark()
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = if (wide && work != null) TextAlign.Start else TextAlign.Center
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (wide && work != null) TextAlign.Start else TextAlign.Center
            )
            state?.invoke()
        }
    }

    when {
        work == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SoftCard(modifier = Modifier.waitTrim()) {
                Box(Modifier.fillMaxWidth().padding(26.dp)) { why(Modifier.fillMaxWidth()) }
            }
        }

        wide && fullWidthWork -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                mark()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            state?.invoke()
            work()
        }

        wide -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // No plate: it speaks over the tray like a screen title.
            why(Modifier.weight(0.42f))
            Box(Modifier.weight(0.58f)) { work() }
        }

        else -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            why(Modifier.fillMaxWidth())
            work()
        }
    }
}

/** A page's mark: the app's glyph in the same socket as the emulator icons. */
@Composable
private fun StepMark(size: Dp = 64.dp, glyph: @Composable (Color) -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    Box(
        modifier = Modifier.size(size).socket(ArtworkShape, dark),
        contentAlignment = Alignment.Center
    ) {
        glyph(MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LogoMark(size: Dp = 96.dp) {
    Image(
        painter = painterResource(R.drawable.emufii_logo_v3),
        contentDescription = null,
        modifier = Modifier.size(size)
    )
}

/** Emulator pages lay the settings block here instead. */
@Composable
private fun WorkCard(content: @Composable () -> Unit) {
    SoftCard(modifier = Modifier.waitTrim()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) { content() }
    }
}

@Composable
private fun StepBody(
    step: OnbStep,
    wide: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    nameTooShort: Boolean,
    romFolder: Uri?,
    onPickFolder: () -> Unit,
    hiddenConsoles: Set<Console>,
    onSetConsoleVisible: (Console, Boolean) -> Unit,
    frontendFolder: String,
    artworkFrontend: ArtworkFrontend,
    onSetFrontend: (ArtworkFrontend) -> Unit,
    onPickFrontend: () -> Unit,
    onForgetFrontend: () -> Unit,
    artworkKey: String,
    onArtworkKeyChange: (String) -> Unit,
    ppssppConfig: PpssppConfigStore,
    ppssppReady: Boolean,
    onPpssppReady: (Boolean) -> Unit,
    ps2Ready: Boolean,
    onPs2Ready: (Boolean) -> Unit,
    profileName: String,
    autofillOn: Boolean,
    onOpenAutofill: () -> Unit,
    notificationsGranted: Boolean,
    notificationsRefused: Boolean,
    onAskNotifications: () -> Unit,
) = when (step) {

    OnbStep.WELCOME -> StepLayout(
        wide = wide,
        mark = { LogoMark() },
        title = stringResource(R.string.onb_welcome_title),
        body = stringResource(R.string.onb_welcome_body),
    )

    OnbStep.NAME -> StepLayout(
        wide = wide,
        mark = { StepMark { PersonMark(size = 34.dp, color = it) } },
        title = stringResource(R.string.onb_name_title),
        body = stringResource(R.string.onb_name_body),
        work = {
            WorkCard {
                PadTextField(
                    value = name,
                    onValueChange = onNameChange,
                    isError = nameTooShort,
                    shape = PillShape,
                    label = stringResource(R.string.onb_name_field),
                    supportingText = {
                        if (nameTooShort) {
                            Text(
                                stringResource(
                                    R.string.onb_name_too_short,
                                    Profile.MIN_NAME_LENGTH
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.onb_name_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )

    OnbStep.FOLDER -> StepLayout(
        wide = wide,
        mark = { StepMark { FolderMark(size = 36.dp, color = it) } },
        title = stringResource(R.string.onb_folder_title),
        body = stringResource(R.string.onb_folder_body),
        work = {
            WorkCard {
                if (romFolder == null) {
                    SettingsSteps(
                        stringResource(R.string.onb_folder_step1),
                        stringResource(R.string.onb_folder_step2),
                        stringResource(R.string.onb_folder_step3),
                    )
                } else {
                    BlockFact(
                        stringResource(R.string.settings_library_fact_folder),
                        folderLabel(romFolder)
                    )
                    BlockNotice(stringResource(R.string.onb_folder_after))
                }
                DetailActions {
                    if (romFolder == null) {
                        PrimaryButton(
                            label = stringResource(R.string.lib_choose_folder),
                            onClick = onPickFolder,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        GhostButton(
                            label = stringResource(R.string.onb_folder_change),
                            onClick = onPickFolder,
                            fillWidth = true
                        )
                    }
                }
            }
        }
    )

    OnbStep.CONSOLES -> StepLayout(
        wide = wide,
        mark = { StepMark(size = 52.dp) { GridMark(size = 28.dp, color = it) } },
        title = stringResource(R.string.consoles_pick_title),
        body = stringResource(R.string.onb_consoles_body),
        fullWidthWork = true,
        work = {
            WorkCard {
                ConsoleGrid(
                    hidden = hiddenConsoles,
                    onSetVisible = onSetConsoleVisible,
                    compact = wide,
                )
                Text(
                    stringResource(R.string.onb_consoles_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )

    OnbStep.COCOON -> {
        val has = frontendFolder.isNotBlank()
        val frontendName = stringResource(artworkFrontend.labelRes)
        StepLayout(
            wide = wide,
            mark = { StepMark { PaintMark(size = 34.dp, color = it) } },
            title = stringResource(R.string.onb_cocoon_title),
            body = stringResource(R.string.onb_cocoon_body),
            state = {
                StatePill(
                    if (has) DetailTone.GOOD else DetailTone.WARN,
                    stringResource(
                        if (has) R.string.onb_cocoon_pill_on else R.string.onb_cocoon_pill_off
                    )
                )
            },
            work = {
                WorkCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ArtworkFrontend.entries.forEachIndexed { index, option ->
                            ChoiceRow(
                                label = stringResource(option.labelRes),
                                selected = option == artworkFrontend,
                                onClick = { onSetFrontend(option) },
                                entry = index == 0
                            )
                        }
                    }
                    if (has) {
                        BlockFact(
                            stringResource(R.string.settings_library_fact_folder),
                            folderLabel(frontendFolder.toUri())
                        )
                        BlockNotice(stringResource(R.string.onb_cocoon_after, frontendName))
                    } else {
                        SettingsSteps(
                            stringResource(R.string.onb_cocoon_step1, frontendName),
                            stringResource(
                                when (artworkFrontend) {
                                    ArtworkFrontend.COCOON -> R.string.onb_frontend_step2_cocoon
                                    ArtworkFrontend.ESDE -> R.string.onb_frontend_step2_esde
                                }
                            ),
                            stringResource(R.string.onb_cocoon_step3, frontendName),
                        )
                    }
                    DetailActions {
                        if (has) {
                            GhostButton(
                                label = stringResource(R.string.settings_cocoon_change),
                                onClick = onPickFrontend,
                                fillWidth = true
                            )
                            GhostButton(
                                label = stringResource(R.string.settings_cocoon_forget),
                                onClick = onForgetFrontend,
                                fillWidth = true
                            )
                        } else {
                            PrimaryButton(
                                label = stringResource(R.string.settings_frontend_choose, frontendName),
                                onClick = onPickFrontend,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        )
    }

    OnbStep.ARTWORK -> StepLayout(
        wide = wide,
        mark = { StepMark { PaintMark(size = 34.dp, color = it) } },
        title = stringResource(R.string.onb_artwork_title),
        body = stringResource(R.string.onb_artwork_body),
        work = {
            WorkCard {
                SteamGridDbMark()
                SettingsSteps(
                    stringResource(R.string.onb_artwork_step1),
                    stringResource(R.string.onb_artwork_step2),
                    stringResource(R.string.onb_artwork_step3),
                )
                PadTextField(
                    value = artworkKey,
                    onValueChange = onArtworkKeyChange,
                    shape = PillShape,
                    label = stringResource(R.string.settings_artwork_field),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )

    OnbStep.PPSSPP -> StepLayout(
        wide = wide,
        mark = { StepMark { ChipMark(size = 34.dp, color = it) } },
        title = stringResource(R.string.onb_ppsspp_title),
        body = stringResource(R.string.onb_ppsspp_body),
        work = {
            PpssppBlock(
                store = ppssppConfig,
                ready = ppssppReady,
                onReadyChanged = onPpssppReady,
            )
        }
    )

    OnbStep.PS2 -> StepLayout(
        wide = wide,
        mark = { StepMark { ChipMark(size = 34.dp, color = it) } },
        title = stringResource(R.string.onb_ps2_title),
        body = stringResource(R.string.onb_ps2_body),
        work = {
            Ps2Block(
                ready = ps2Ready,
                profileName = profileName,
                onReadyChanged = onPs2Ready,
            )
        }
    )

    OnbStep.AUTOFILL -> StepLayout(
        wide = wide,
        mark = { StepMark { ChipMark(size = 34.dp, color = it) } },
        title = stringResource(R.string.onb_fill_title),
        body = stringResource(R.string.onb_fill_body),
        work = { AutofillBlock(enabled = autofillOn, onOpen = onOpenAutofill) }
    )

    OnbStep.NOTIF -> StepLayout(
        wide = wide,
        mark = { StepMark { SignalMark(size = 34.dp, color = it) } },
        title = stringResource(R.string.onb_notif_title),
        body = stringResource(R.string.onb_notif_body),
        state = {
            StatePill(
                if (notificationsGranted) DetailTone.GOOD else DetailTone.WARN,
                stringResource(
                    if (notificationsGranted) R.string.onb_notif_pill_on
                    else R.string.onb_notif_pill_off
                )
            )
        },
        work = {
            WorkCard {
                if (notificationsGranted) {
                    BlockNotice(stringResource(R.string.onb_notif_after))
                } else {
                    SettingsSteps(
                        stringResource(R.string.onb_notif_step1),
                        stringResource(R.string.onb_notif_step2),
                    )
                    if (notificationsRefused) {
                        BlockNotice(stringResource(R.string.onb_notif_refused))
                    }
                }
                if (!notificationsGranted && !notificationsRefused) {
                    DetailActions {
                        PrimaryButton(
                            label = stringResource(R.string.onb_notif_enable),
                            onClick = onAskNotifications,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    )

    OnbStep.DONE -> StepLayout(
        wide = wide,
        mark = { LogoMark(size = 76.dp) },
        title = stringResource(R.string.onb_done_title),
        body = stringResource(R.string.onb_done_body),
        work = {
            WorkCard {
                Recap(
                    stringResource(R.string.onb_recap_folder) to (romFolder != null),
                    stringResource(R.string.onb_recap_artwork) to
                        (frontendFolder.isNotBlank() || artworkKey.isNotBlank()),
                    stringResource(R.string.onb_recap_ppsspp) to ppssppReady,
                    stringResource(R.string.onb_recap_ps2) to ps2Ready,
                    stringResource(R.string.onb_recap_autofill) to autofillOn,
                    stringResource(R.string.onb_recap_notif) to notificationsGranted,
                    hidden = hiddenConsoles,
                )
                BlockNotice(stringResource(R.string.onb_done_where))
            }
        }
    )
}

/**
 * Rows that do not concern this player do not appear.
 * pourquoi : docs/decisions/onboarding.md § The summary names what was skipped
 */
@Composable
private fun Recap(vararg rows: Pair<String, Boolean>, hidden: Set<Console>) {
    val shown = rows.filterIndexed { i, _ ->
        when (i) {
            2 -> Console.PSP !in hidden
            3 -> Console.PS2 !in hidden
            4 -> AUTOMATED.any { it !in hidden }
            else -> true
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { (label, done) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                StatePill(
                    if (done) DetailTone.GOOD else DetailTone.WARN,
                    stringResource(
                        if (done) R.string.settings_pill_ready else R.string.onb_recap_later
                    )
                )
            }
        }
    }
}

/** The last segment of a document tree, which is what the player recognises. */
private fun folderLabel(uri: Uri): String {
    val raw = uri.lastPathSegment ?: return uri.toString()
    return raw.substringAfterLast(':').substringAfterLast('/').ifBlank { raw }
}

/**
 * Dots alone announced a length that changed under the player's eyes.
 * pourquoi : docs/decisions/onboarding.md § Where you are, and what it is about
 */
@Composable
private fun StepRail(current: Int, total: Int, label: String) {
    val dark = LocalEmufiiDarkTheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(total) { i ->
                val active = i == current
                Box(
                    Modifier
                        .height(7.dp)
                        .width(if (active) 20.dp else 7.dp)
                        .clip(if (active) PillShape else CircleShape)
                        .background(
                            // The teal axis, deep enough to hold on the cream.
                            if (active) (if (dark) Teal.darkBright else Teal.deep)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
                        )
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
