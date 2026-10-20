package eu.emufii.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import eu.emufii.app.ui.components.SheetLabel
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.aspectRatio
import eu.emufii.app.ui.components.consoleArtwork
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.style.TextOverflow
import eu.emufii.app.library.EmulatorInfo
import eu.emufii.app.library.emulatorInfo
import eu.emufii.app.ui.components.FactTile
import eu.emufii.app.ui.components.FitColumn
import eu.emufii.app.ui.components.LocalSheetMaxHeight
import eu.emufii.app.ui.components.Optional
import eu.emufii.app.ui.components.SheetHeader
import eu.emufii.app.ui.components.SheetStep
import eu.emufii.app.ui.components.SheetWarning
import eu.emufii.app.ui.components.accented
import eu.emufii.app.ui.screens.settings.rememberPpssppSetup
import eu.emufii.app.ui.screens.settings.rememberPs2Setup
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.WarnDark
import eu.emufii.app.ui.theme.WarnLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

    val notificationsRuntimePermission =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var notificationsGranted by remember {
        mutableStateOf(
            !notificationsRuntimePermission ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationsRefused by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        notificationsRefused = !granted
    }

    val ppssppConfig = remember(context) { PpssppConfigStore(context) }
    var ppssppReady by remember { mutableStateOf(ppssppConfig.isReady()) }
    var ps2Ready by remember { mutableStateOf(Ps2NetworkProfile.isReadyQuick(context)) }
    LaunchedEffect(Unit) { ps2Ready = Ps2NetworkProfile.verifyReady(context) }

    val launcher = remember { AzaharLauncher(context) }
    var autofillOn by remember { mutableStateOf(launcher.isNetplayAutomationEnabled()) }

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

    BackHandler(enabled = index > 0) { goBack() }

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

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (wide) Modifier else Modifier.verticalScroll(rememberScrollState())),
                contentAlignment = Alignment.Center
            ) {
              CompositionLocalProvider(
                  LocalSheetMaxHeight provides if (wide) maxHeight else Dp.Unspecified
              ) {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = {
                        val forward = steps.indexOf(targetState) > steps.indexOf(initialState)
                        (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn())
                            .togetherWith(
                                slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut()
                            )
                            .using(SizeTransform(clip = false))
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
                            if (notificationsRuntimePermission) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                notificationsGranted = true
                            }
                        },
                    )
                }
              }
            }

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
                    enabled = current != OnbStep.NAME || !nameTooShort,
                    modifier = Modifier.weight(1f).height(actionHeight)
                )

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

private fun defaultFolderOf(frontend: ArtworkFrontend): Uri = DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents",
    frontend.defaultFolderId
)

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

@Composable
private fun StepLayout(
    wide: Boolean,
    mark: @Composable () -> Unit,
    title: String,
    body: String,
    state: (@Composable () -> Unit)? = null,
    fullWidthWork: Boolean = false,
    balanceMark: Boolean = true,
    work: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    var markHeight by remember { mutableStateOf(0.dp) }
    val why: @Composable (Modifier) -> Unit = { m ->
        Column(
            modifier = m,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = if (wide && work != null) Alignment.Start else Alignment.CenterHorizontally
        ) {
            Box(Modifier.onSizeChanged { markHeight = with(density) { it.height.toDp() } }) { mark() }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = if (wide && work != null) TextAlign.Start else TextAlign.Center
            )
            Text(
                accented(body, onbInks().accent),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (wide && work != null) TextAlign.Start else TextAlign.Center
            )
            state?.invoke()
            if (wide && work != null && balanceMark) Spacer(Modifier.height(markHeight))
        }
    }

    when {
        work == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SoftCard(modifier = Modifier) {
                Box(Modifier.fillMaxWidth().padding(26.dp)) { why(Modifier.fillMaxWidth()) }
            }
        }

        wide && fullWidthWork -> Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
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
                        accented(body, onbInks().accent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            state?.invoke()
            val max = LocalSheetMaxHeight.current
            CompositionLocalProvider(
                LocalSheetMaxHeight provides if (max == Dp.Unspecified) max else max - BANNER_HEIGHT
            ) { work() }
        }

        wide -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

private val BANNER_HEIGHT = 86.dp

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

private data class OnbInks(val accent: Color, val alarm: Color, val good: Color, val warn: Color)

@Composable
private fun onbInks(): OnbInks {
    val dark = LocalEmufiiDarkTheme.current
    return OnbInks(
        accent = if (dark) Teal.darkBright else Teal.deep,
        alarm = if (dark) Coral.darkBright else Coral.deep,
        good = if (dark) GoodDark else GoodLight,
        warn = if (dark) WarnDark else WarnLight,
    )
}

@Composable
private fun OnbSheet(content: @Composable (OnbInks) -> Unit) {
    val inks = onbInks()
    val maxHeight = LocalSheetMaxHeight.current
    SoftCard(modifier = Modifier) {
        FitColumn(
            maxHeight = if (maxHeight == Dp.Unspecified) maxHeight else maxHeight - SheetPadV * 2,
            spacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = SheetPadV),
            strictOrder = true,
        ) { content(inks) }
    }
}

private val SheetPadV = 16.dp

@Composable
private fun EmulatorHeader(console: Console, title: String, inks: OnbInks) {
    val context = LocalContext.current
    val emulator by produceState<EmulatorInfo?>(null, console) {
        value = withContext(Dispatchers.IO) { runCatching { emulatorInfo(context, console) }.getOrNull() }
    }
    val missing = emulator?.installed == false
    SheetHeader(
        icon = emulator?.icon,
        fallbackLetter = title.take(1),
        title = title,
        subtitle = if (missing) stringResource(R.string.console_sheet_not_installed) else emulator?.version,
        accent = inks.accent,
        subtitleInk = if (missing) inks.alarm else inks.accent,
        iconSize = 44.dp,
    )
}

@Composable
private fun StateTile(done: Boolean, doneLabel: String, todoLabel: String, inks: OnbInks, modifier: Modifier) {
    FactTile(
        label = stringResource(R.string.onb_tile_state),
        value = if (done) doneLabel else todoLabel,
        ink = if (done) inks.good else inks.warn,
        modifier = modifier
    )
}

@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun Steps(inks: OnbInks, vararg steps: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { i, text -> SheetStep(i + 1, text, inks.accent) }
    }
}

@Composable
private fun SheetNote(text: String, inks: OnbInks) {
    Text(
        accented(text, inks.accent),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
        mark = { LogoMark(size = 72.dp) },
        title = stringResource(R.string.onb_welcome_title),
        body = stringResource(R.string.onb_welcome_body),
        work = { WelcomeSheet() }
    )

    OnbStep.NAME -> StepLayout(
        wide = wide,
        mark = { StepMark { PersonMark(size = 34.dp, color = it) } },
        title = stringResource(R.string.onb_name_title),
        body = stringResource(R.string.onb_name_body),
        work = {
            OnbSheet { inks ->
                PadTextField(
                    value = name,
                    onValueChange = onNameChange,
                    isError = nameTooShort,
                    shape = PillShape,
                    label = stringResource(R.string.onb_name_field),
                    selectAllOnEdit = true,
                    supportingText = {
                        if (nameTooShort) {
                            Text(stringResource(R.string.onb_name_too_short, Profile.MIN_NAME_LENGTH))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Optional { SheetNote(stringResource(R.string.onb_name_note), inks) }
            }
        }
    )

    OnbStep.FOLDER -> StepLayout(
        wide = wide,
        mark = { StepMark { FolderMark(size = 36.dp, color = it) } },
        title = stringResource(R.string.onb_folder_title),
        body = stringResource(R.string.onb_folder_body),
        work = {
            OnbSheet { inks ->
                CheckedTile(done = romFolder != null, inks = inks) {
                    FactTile(
                        stringResource(R.string.onb_tile_folder),
                        romFolder?.let { folderLabel(it) } ?: stringResource(R.string.onb_tile_none),
                        if (romFolder != null) inks.good else inks.warn,
                        Modifier.fillMaxWidth()
                    )
                }
                if (romFolder == null) {
                    Steps(
                        inks,
                        stringResource(R.string.onb_folder_step1),
                        stringResource(R.string.onb_folder_step2),
                    )
                } else {
                    Optional { SheetNote(stringResource(R.string.onb_folder_after), inks) }
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
            OnbSheet { inks ->
                ConsoleGrid(
                    hidden = hiddenConsoles,
                    onSetVisible = onSetConsoleVisible,
                    compact = wide,
                    oneLine = wide,
                )
                Optional { SheetNote(stringResource(R.string.onb_consoles_note), inks) }
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
            work = {
                OnbSheet { inks ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ArtworkFrontend.entries.forEachIndexed { index, option ->
                            Box(Modifier.weight(1f)) {
                                ChoiceRow(
                                    label = stringResource(option.labelRes),
                                    selected = option == artworkFrontend,
                                    onClick = { onSetFrontend(option) },
                                    entry = index == 0
                                )
                            }
                        }
                    }
                    if (has) {
                        CheckedTile(done = true, inks = inks) {
                            FactTile(
                                stringResource(R.string.onb_tile_folder),
                                folderLabel(frontendFolder.toUri()),
                                inks.good,
                                Modifier.fillMaxWidth()
                            )
                        }
                        Optional { SheetNote(stringResource(R.string.onb_cocoon_after, frontendName), inks) }
                    } else {
                        Steps(
                            inks,
                            stringResource(R.string.onb_cocoon_step1, frontendName),
                            stringResource(
                                when (artworkFrontend) {
                                    ArtworkFrontend.COCOON -> R.string.onb_frontend_step2_cocoon
                                    ArtworkFrontend.ESDE -> R.string.onb_frontend_step2_esde
                                    ArtworkFrontend.IISU -> R.string.onb_frontend_step2_iisu
                                }
                            ),
                        )
                        Optional { SheetNote(stringResource(R.string.onb_cocoon_step3, frontendName), inks) }
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
            OnbSheet { inks ->
                Optional { SteamGridDbMark() }
                Steps(
                    inks,
                    stringResource(R.string.onb_artwork_step1),
                    stringResource(R.string.onb_artwork_step2),
                )
                PadTextField(
                    value = artworkKey,
                    onValueChange = onArtworkKeyChange,
                    shape = PillShape,
                    label = stringResource(R.string.settings_artwork_field),
                    modifier = Modifier.fillMaxWidth()
                )
                Optional { SheetNote(stringResource(R.string.onb_artwork_step3), inks) }
            }
        }
    )

    OnbStep.PPSSPP -> StepLayout(
        wide = wide,
        mark = { StepMark { ChipMark(size = 34.dp, color = it) } },
        title = stringResource(R.string.onb_ppsspp_title),
        body = stringResource(R.string.onb_ppsspp_body),
        work = { PpssppSheet(ppssppConfig, ppssppReady, onPpssppReady) }
    )

    OnbStep.PS2 -> StepLayout(
        wide = wide,
        mark = { StepMark { ChipMark(size = 34.dp, color = it) } },
        title = stringResource(R.string.onb_ps2_title),
        body = stringResource(R.string.onb_ps2_body),
        work = { Ps2Sheet(ps2Ready, profileName, onPs2Ready) }
    )

    OnbStep.AUTOFILL -> StepLayout(
        wide = wide,
        mark = { StepMark { ChipMark(size = 34.dp, color = it) } },
        title = stringResource(R.string.onb_fill_title),
        body = stringResource(R.string.onb_fill_body),
        work = {
            OnbSheet { inks ->
                TileRow {
                    StateTile(
                        autofillOn,
                        stringResource(R.string.onb_fill_on),
                        stringResource(R.string.onb_fill_off),
                        inks, Modifier.weight(1f)
                    )
                    FactTile(
                        stringResource(R.string.onb_tile_consoles),
                        AUTOMATED.filter { it !in hiddenConsoles }.joinToString(" · ") {
                            if (it == Console.GAMECUBE) "GC" else it.label
                        },
                        MaterialTheme.colorScheme.onSurface,
                        Modifier.weight(2.4f)
                    )
                }
                if (!autofillOn) {
                    Steps(
                        inks,
                        stringResource(R.string.onb_fill_step1),
                        stringResource(R.string.onb_fill_step2),
                    )
                    Optional { SheetWarning(stringResource(R.string.onb_fill_restricted), inks.alarm) }
                }
                Optional { SheetNote(stringResource(R.string.onb_fill_scope), inks) }
                if (!autofillOn) Optional { SheetNote(stringResource(R.string.onb_fill_without), inks) }
                DetailActions {
                    if (autofillOn) {
                        GhostButton(
                            label = stringResource(R.string.settings_autofill_open),
                            onClick = onOpenAutofill,
                            fillWidth = true
                        )
                    } else {
                        PrimaryButton(
                            label = stringResource(R.string.settings_autofill_open),
                            onClick = onOpenAutofill,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    )

    OnbStep.NOTIF -> StepLayout(
        wide = wide,
        mark = { StepMark { SignalMark(size = 34.dp, color = it) } },
        title = stringResource(R.string.onb_notif_title),
        body = stringResource(R.string.onb_notif_body),
        work = {
            OnbSheet { inks ->
                StateTile(
                    notificationsGranted,
                    stringResource(R.string.onb_notif_pill_on),
                    stringResource(R.string.onb_notif_pill_off),
                    inks, Modifier.fillMaxWidth()
                )
                when {
                    notificationsGranted -> Optional { SheetNote(stringResource(R.string.onb_notif_after), inks) }
                    notificationsRefused -> SheetWarning(stringResource(R.string.onb_notif_refused), inks.alarm)
                    else -> {
                        Steps(inks, stringResource(R.string.onb_notif_step1))
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
        }
    )

    OnbStep.DONE -> StepLayout(
        wide = wide,
        mark = { LogoMark(size = 76.dp) },
        title = stringResource(R.string.onb_done_title),
        body = stringResource(R.string.onb_done_body),
        balanceMark = false,
        work = {
            OnbSheet { inks ->
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
                Optional { SheetNote(stringResource(R.string.onb_done_where), inks) }
            }
        }
    )
}

@Composable
private fun WelcomeSheet() {
    val dark = LocalEmufiiDarkTheme.current
    OnbSheet { inks ->
        SheetLabel(stringResource(R.string.onb_tile_consoles))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Console.entries.forEach { console ->
                consoleArtwork(console, dark)?.let { art ->
                    Image(
                        painter = painterResource(art),
                        contentDescription = console.label,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
        SheetLabel(stringResource(R.string.onb_welcome_how))
        SheetStep(1, stringResource(R.string.onb_welcome_step1), inks.accent)
        SheetStep(2, stringResource(R.string.onb_welcome_step2), inks.accent)
        Optional { SheetStep(3, stringResource(R.string.onb_welcome_step3), inks.accent) }
    }
}

@Composable
private fun CheckedTile(done: Boolean, inks: OnbInks, tile: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.CenterEnd) {
        tile()
        if (done) DrawnCheck(inks.good, Modifier.padding(end = 14.dp).size(30.dp))
    }
}

@Composable
private fun DrawnCheck(color: Color, modifier: Modifier) {
    val disc = remember { Animatable(0f) }
    val stroke = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        disc.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
    }
    LaunchedEffect(Unit) {
        delay(140)
        stroke.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
    }
    val tick = remember { Path() }
    val part = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(modifier.graphicsLayer { scaleX = disc.value; scaleY = disc.value }) {
        drawCircle(color)
        tick.reset()
        tick.moveTo(size.width * 0.28f, size.height * 0.53f)
        tick.lineTo(size.width * 0.44f, size.height * 0.68f)
        tick.lineTo(size.width * 0.73f, size.height * 0.36f)
        measure.setPath(tick, false)
        part.reset()
        measure.getSegment(0f, measure.length * stroke.value, part, true)
        drawPath(
            part,
            Color.White,
            style = Stroke(width = size.width * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun PpssppSheet(store: PpssppConfigStore, ready: Boolean, onReadyChanged: (Boolean) -> Unit) {
    val setup = rememberPpssppSetup(store, onReadyChanged)
    OnbSheet { inks ->
        Optional { EmulatorHeader(Console.PSP, "PPSSPP", inks) }
        TileRow {
            StateTile(
                ready,
                stringResource(R.string.settings_pill_ready),
                stringResource(R.string.settings_pill_todo),
                inks, Modifier.weight(1f)
            )
            FactTile(
                stringResource(R.string.onb_tile_folder),
                (if (ready) store.rootLabel() else null) ?: stringResource(R.string.onb_tile_none),
                MaterialTheme.colorScheme.onSurface,
                Modifier.weight(1f)
            )
        }
        setup.error?.let { SheetWarning(stringResource(it), inks.alarm) }
        if (!ready && setup.rootUri != null && setup.error == null) {
            SheetWarning(stringResource(R.string.settings_ppsspp_config_not_ready), inks.alarm)
        }
        if (!ready) Steps(inks, stringResource(R.string.onb_ppsspp_step1))
        Optional { SheetNote(stringResource(R.string.onb_ppsspp_step2), inks) }
        Optional { SheetWarning(stringResource(R.string.onb_ppsspp_caveat), inks.accent) }
        DetailActions {
            if (ready) {
                GhostButton(
                    label = stringResource(R.string.settings_ppsspp_config_change),
                    onClick = setup::pick,
                    fillWidth = true,
                )
            } else {
                PrimaryButton(
                    label = stringResource(R.string.settings_ppsspp_config_choose),
                    onClick = setup::pick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Ps2Sheet(ready: Boolean, profileName: String, onReadyChanged: (Boolean) -> Unit) {
    val setup = rememberPs2Setup(profileName, onReadyChanged)
    OnbSheet { inks ->
        Optional { EmulatorHeader(Console.PS2, "ARMSX2", inks) }
        TileRow {
            FactTile(
                label = stringResource(R.string.onb_tile_state),
                value = stringResource(
                    when {
                        setup.busy -> R.string.settings_pill_working
                        setup.error != null -> R.string.settings_pill_failed
                        ready -> R.string.settings_pill_ready
                        else -> R.string.settings_pill_todo
                    }
                ),
                ink = when {
                    setup.error != null -> inks.alarm
                    ready -> inks.good
                    setup.busy -> inks.accent
                    else -> inks.warn
                },
                modifier = Modifier.weight(1f)
            )
            FactTile(
                stringResource(R.string.onb_tile_card),
                setup.receipt?.cardName ?: stringResource(R.string.onb_tile_none),
                MaterialTheme.colorScheme.onSurface,
                Modifier.weight(1f)
            )
        }
        setup.error?.let { SheetWarning(it, inks.alarm) }
        if (!ready) {
            SheetStep(1, stringResource(R.string.onb_ps2_step1), inks.accent)
            Optional { SheetStep(2, stringResource(R.string.onb_ps2_step2), inks.accent) }
        }
        Optional { SheetWarning(stringResource(R.string.onb_ps2_safe), inks.accent) }
        DetailActions {
            if (ready) {
                GhostButton(
                    label = stringResource(R.string.hint_ps2_profile_redo),
                    onClick = setup::prepare,
                    fillWidth = true
                )
            } else {
                PrimaryButton(
                    label = stringResource(
                        if (setup.rootUri == null) R.string.hint_ps2_profile_choose_folder
                        else R.string.hint_ps2_profile_button
                    ),
                    onClick = setup::prepare,
                    enabled = !setup.busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (setup.rootUri != null) {
                GhostButton(
                    label = stringResource(R.string.hint_ps2_profile_change_folder),
                    onClick = setup::changeFolder,
                    fillWidth = true,
                )
            }
        }
    }
}

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
        shown.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { (label, done) ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        StatePill(
                            if (done) DetailTone.GOOD else DetailTone.WARN,
                            stringResource(if (done) R.string.settings_pill_ready else R.string.onb_recap_later)
                        )
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

private fun folderLabel(uri: Uri): String {
    val raw = uri.lastPathSegment ?: return uri.toString()
    return raw.substringAfterLast(':').substringAfterLast('/').ifBlank { raw }
}

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
