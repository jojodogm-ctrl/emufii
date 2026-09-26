package eu.emufii.app.secondscreen

import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import androidx.compose.foundation.layout.Spacer
import eu.emufii.app.ui.rememberElastic
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import eu.emufii.app.ui.ShadowsFollow
import eu.emufii.app.ui.bloom
import eu.emufii.app.ui.rememberAppear
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import eu.emufii.app.ui.awaitSeen
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.graphicsLayer
import eu.emufii.app.ui.rememberAnimationsEnabled
import eu.emufii.app.ui.TrailerSpinner
import eu.emufii.app.ui.DrawnCheck
import eu.emufii.app.ui.Sfx
import eu.emufii.app.ui.Motion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import eu.emufii.app.artwork.COVER_REQUEST_PX
import eu.emufii.app.artwork.CoverTone
import eu.emufii.app.ui.theme.PlateLight
import eu.emufii.app.ui.theme.PlateDark
import androidx.compose.animation.ExitTransition
import eu.emufii.app.ui.fadeInPlace
import eu.emufii.app.ui.theme.liftShadow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.emufii.app.BuildConfig
import eu.emufii.app.R
import androidx.compose.ui.text.TextStyle
import eu.emufii.app.ui.RevealCode
import eu.emufii.app.artwork.rememberTileArt
import eu.emufii.app.library.Console
import eu.emufii.app.ui.ActionShape
import eu.emufii.app.ui.SilenceSystemSfx
import eu.emufii.app.ui.components.Avatar
import eu.emufii.app.ui.components.ChipMark
import eu.emufii.app.ui.components.CompatBadge
import eu.emufii.app.ui.components.CopyMark
import eu.emufii.app.ui.components.CrossIcon
import eu.emufii.app.ui.components.GhostButton
import eu.emufii.app.ui.components.GridMark
import eu.emufii.app.ui.components.BugMark
import eu.emufii.app.ui.components.InfoMark
import eu.emufii.app.ui.components.LensMark
import eu.emufii.app.ui.components.PaintMark
import eu.emufii.app.ui.components.PersonMark
import eu.emufii.app.ui.components.ShelfMark
import eu.emufii.app.ui.components.SignalMark
import eu.emufii.app.ui.components.SlidersMark
import eu.emufii.app.ui.components.VpsLamp
import eu.emufii.app.ui.components.compatLabel
import eu.emufii.app.ui.copyToClipboard
import eu.emufii.app.ui.focusRing
import eu.emufii.app.ui.sounded
import eu.emufii.app.ui.tap
import eu.emufii.app.ui.theme.ArtworkShape
import eu.emufii.app.ui.theme.CardShape
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.ErrorDark
import eu.emufii.app.ui.theme.ErrorLight
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.InkText
import eu.emufii.app.ui.theme.LocalAccent
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.Teal
import eu.emufii.app.ui.theme.TileShape
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.theme.tilePlateBrush
import eu.emufii.app.ui.wallpaper.TrayBackdrop
import eu.emufii.app.ui.glass
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * What the second panel draws, whoever is holding the window.
 *
 * pourquoi : docs/decisions/second-ecran.md § The panel has no style of its own
 */
@Composable
fun SecondScreenContent(model: SecondScreenModel) {
    SilenceSystemSfx()
    val dark = LocalEmufiiDarkTheme.current
    val page by SecondScreen.page.collectAsStateWithLifecycle()

    // One listener per screen: keyboard focus goes to a window, not to the device.
    // pourquoi : docs/decisions/second-ecran.md § R turns the page from both screens
    val keys = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keys.requestFocus() } }

    // The tray this panel paints, for the faces standing on it to refract.
    val panelGlass = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(keys)
            .focusProperties { canFocus = true }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                // The same keys as the front, for when this window holds the focus.
                if (SecondScreen.gallery.value != null) {
                    when (event.key) {
                        Key.DirectionLeft -> SecondScreen.moveGallery(-1)
                        Key.DirectionRight -> SecondScreen.moveGallery(1)
                        Key.ButtonR1 -> SecondScreen.flipPage()
                        else -> SecondScreen.closeGallery()
                    }
                    return@onKeyEvent true
                }
                when (event.key) {
                    Key.ButtonR1 -> { SecondScreen.flipPage(); true }
                    Key.ButtonX -> SecondScreen.openGallery()
                    else -> false
                }
            }
    ) {
        // This window has no wallpaper behind it, so the tray is painted rather
        // than shown through.
        TrayBackdrop(modifier = Modifier.fillMaxSize().layerBackdrop(panelGlass), dark = dark)

        // Every face centres in the same place: a crossfade tolerates only one
        // geometry.
        // pourquoi : docs/decisions/second-ecran.md § Every face centres in the same place
        Column(modifier = Modifier.fillMaxSize()) {
            PanelHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp, end = 26.dp, top = 18.dp)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)

                    .padding(start = 36.dp, end = 36.dp, top = 10.dp)
            ) {
                // Keyed on the game's identity, not the model: late facts fill in
                // without dissolving a face.
                // pourquoi : docs/decisions/second-ecran.md § The fade between two faces is not decoration
                // pourquoi : docs/decisions/second-ecran.md § Every face centres in the same place
                Crossfade(
                    targetState = faceKey(model),
                    animationSpec = tween(220),
                    label = "panel-face",
                    modifier = Modifier.fillMaxSize()
                ) { key ->
                    // Read here, not captured with the key: during a fade the outgoing
                    // face is still composed.
                    val shown = remember(key) { model }
                    // A face reads only the page turned for its own game, and keeps the
                    // last one it saw once the page belongs to another: the outgoing face
                    // stays where it was instead of sliding back to the summary while it
                    // fades, and the incoming one starts on its summary.
                    // pourquoi : docs/decisions/second-ecran.md § The fade between two faces is not decoration
                    val kept = remember(key) { mutableIntStateOf(0) }
                    val own = (shown as? SecondScreenModel.Browsing)
                        ?.takeIf { page.rom == it.rom.uri.toString() }
                        ?.let { page.index }
                    if (own != null) SideEffect { kept.intValue = own }
                    val shownPage = own ?: kept.intValue
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (shown) {
                            is SecondScreenModel.Idle -> Idle()
                            // Live, not frozen: every console shares one key, so a
                            // remembered value never changes.
                            // pourquoi : docs/decisions/second-ecran.md § The console is read live, the other faces are frozen
                            is SecondScreenModel.ConsoleFolder -> ConsoleCard(
                                (model as? SecondScreenModel.ConsoleFolder)?.console
                                    ?: shown.console,
                                pane = panelGlass
                            )
                            is SecondScreenModel.Browsing -> BrowsingPages(shown, shownPage)
                            // Live, like the console card: every entry shares one face
                            // key.
                            // pourquoi : docs/decisions/second-ecran.md § The console is read live, the other faces are frozen
                            is SecondScreenModel.SettingsEntry -> SettingsFace(
                                (model as? SecondScreenModel.SettingsEntry) ?: shown
                            )
                            is SecondScreenModel.Friends -> FriendsFace(
                                // Live: the list changes while you watch it, as people
                                // connect and disconnect.
                                // pourquoi : docs/decisions/second-ecran.md § The console is read live, the other faces are frozen
                                (model as? SecondScreenModel.Friends) ?: shown
                            )
                            is SecondScreenModel.Asking -> AskingFace(
                                (model as? SecondScreenModel.Asking) ?: shown
                            )
                            is SecondScreenModel.InSession -> InSession(shown)
                        }
                    }
                }
            }

            Legend(
                legend = model.legend,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 16.dp)
            )
        }
    }
}

/**
 * What counts as a different face, for the fade: identity, not content.
 * pourquoi : docs/decisions/second-ecran.md § The fade between two faces is not decoration
 */
private fun faceKey(model: SecondScreenModel): String = when (model) {
    is SecondScreenModel.Idle -> "idle"
    // One key for every console: the card resizes between folders rather than being
    // replaced.
    is SecondScreenModel.ConsoleFolder -> "console"
    is SecondScreenModel.SettingsEntry -> "settings"
    is SecondScreenModel.Browsing -> "rom:${model.rom.uri}"
    is SecondScreenModel.Friends -> "friends"
    is SecondScreenModel.Asking -> "asking"
    is SecondScreenModel.InSession -> "session:${model.code}"
}

/**
 * The band across the top: are we reachable, and is there any news.
 * pourquoi : docs/decisions/second-ecran.md § The service light has its own colour
 */
@Composable
private fun PanelHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        VpsLamp()
        NoteStrip(modifier = Modifier.weight(1f))
    }
}

/**
 * It leaves on its own: nothing here can be dismissed.
 * pourquoi : docs/decisions/second-ecran.md § News arrives from above and leaves by itself
 */
@Composable
private fun NoteStrip(modifier: Modifier = Modifier) {
    val note by PanelFeed.note.collectAsStateWithLifecycle()
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current

    // Retired by the note's own id, so a later note is not swept away with the one
    // before.
    LaunchedEffect(note?.id) {
        val shown = note ?: return@LaunchedEffect
        delay(NOTE_LIFETIME_MS.milliseconds)
        PanelFeed.dismiss(shown.id)
    }

    AnimatedContent(
        targetState = note,
        transitionSpec = {
            // The fade is the child's own, per draw: `fadeIn` cut the note's shadow square.
            // pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Blur is paid only in flight
            slideInVertically(tween(260)) { -it } togetherWith ExitTransition.None
        },
        label = "panel-note",
        modifier = modifier
    ) { shown ->
        if (shown == null) {
            // Nothing to say takes no room: a greyed strip reads as something that is
            // broken.
            Box(Modifier.fillMaxWidth().height(1.dp))
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fadeInPlace(transition, tween(260), tween(200))
                    .plate(CardShape, dark = dark, oled = oled, lift = 4.dp)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    shown.text,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private const val NOTE_LIFETIME_MS = 12_000L

@Composable
private fun Idle() {
    val dark = LocalEmufiiDarkTheme.current
    val axis = if (dark) Teal.darkBright else Teal.deep

    // A mark, not a void with a number in it: this face appears many times a minute.
    // pourquoi : docs/decisions/second-ecran.md § The resting mark is a mark, not an emptiness with a number in it
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.emufii_logo_v3),
            contentDescription = null,
            modifier = Modifier.size(108.dp)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // A step smaller and dimmer than the name: a footnote to it.
            // pourquoi : docs/decisions/second-ecran.md § The version is shown on the resting face
            Text(
                stringResource(R.string.panel_idle_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * The cursor is on a console's folder: the facts of playing together on that
 * machine, laid out as a sheet by [ConsoleSheet].
 * pourquoi : docs/decisions/second-ecran.md § The console card: what it says, and what it does not
 */
@Composable
private fun ConsoleCard(console: Console, pane: Backdrop) {
    val dark = LocalEmufiiDarkTheme.current

    // One plate that stays and is resized, never replaced. Centred, so it
    // opens from its middle in both directions.
    // pourquoi : docs/decisions/second-ecran.md § The console card is a plate that grows, not a plate that gets replaced
    AnimatedContent(
        targetState = console,
        transitionSpec = {
            (fadeIn(tween(200, delayMillis = 80)) togetherWith fadeOut(tween(140)))
                .using(SizeTransform(clip = false) { _, _ -> tween(280) })
        },
        label = "console-card",
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .glass(pane, CardShape, dark = dark, lift = 8.dp)
    ) { shown ->
        ConsoleSheet(shown, dark)
    }
}

/**
 * The facts of one machine: its emulator as the system shows it, three tiles, the games,
 * and the warning if there is one. No paragraph: every line answers a question a player
 * asks before a session.
 * pourquoi : docs/decisions/second-ecran.md § The console card: what it says, and what it does not
 */
@Composable
private fun ConsoleSheet(console: Console, dark: Boolean) {
    val context = LocalContext.current
    val brief = remember(console) { consoleBrief(console) }
    val accent = if (dark) Teal.darkBright else Teal.deep
    val alarm = if (dark) Coral.darkBright else Coral.deep
    // Read from the package manager off the main thread: an icon decode is not free.
    val emulator by produceState<eu.emufii.app.library.EmulatorInfo?>(null, console) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { eu.emufii.app.library.emulatorInfo(context, console) }.getOrNull()
        }
    }
    Column(
        // Measured against the panel: its middle band is about 370 dp tall.
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = emulator?.icon
            Box(
                contentAlignment = Alignment.Center,
                // The icon keeps its own shape: a tinted plate behind it showed as a blue
                // ring. The plate is only for the letter standing in for a missing icon.
                modifier = Modifier
                    .size(58.dp)
                    .then(
                        if (icon != null) Modifier
                        else Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(accent.copy(alpha = 0.14f))
                    )
            ) {
                if (icon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        console.backend.emulatorName.take(1),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = accent
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    console.label,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val installed = emulator?.installed
                val status = when (installed) {
                    null -> ""
                    true -> listOfNotNull(
                        console.backend.emulatorName,
                        emulator?.version
                    ).joinToString(" ")
                    false -> console.backend.emulatorName + " · " +
                        stringResource(R.string.console_sheet_not_installed)
                }
                Text(
                    status,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (installed == false) alarm else accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FactTile(
                label = stringResource(R.string.console_sheet_mode),
                value = stringResource(brief.mode),
                ink = accent,
                modifier = Modifier.weight(1.4f)
            )
            FactTile(
                label = stringResource(R.string.console_sheet_code),
                value = stringResource(
                    if (brief.sessionCode) R.string.console_sheet_code_yes
                    else R.string.console_sheet_code_no
                ),
                ink = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.console_sheet_games).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                accented(stringResource(brief.games), accent),
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        brief.warning?.let { warning ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A bar, never a warning triangle: this panel does not shout.
                // pourquoi : docs/decisions/second-ecran.md § The panel does not shout
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(38.dp)
                        .clip(PillShape)
                        .background(alarm)
                )
                Text(
                    accented(stringResource(warning), alarm),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f)
                )
            }
        }
    }
}

/** One fact, its name small above it: read at a glance, the way a spec sheet is. */
@Composable
private fun FactTile(label: String, value: String, ink: Color, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Lifts the two or three words that carry the sentence. The source marks them itself,
 * `*like this*`, rather than the code counting characters: a translator moves the marks
 * with the words, and the string stays readable in the XML.
 * pourquoi : docs/decisions/second-ecran.md § A console card fits in two lines and a warning
 */
@Composable
private fun accented(raw: String, accent: Color): AnnotatedString =
    remember(raw, accent) {
        buildAnnotatedString {
            raw.split('*').forEachIndexed { index, part ->
                if (part.isEmpty()) return@forEachIndexed
                // Odd pieces are the ones between a pair of marks.
                if (index % 2 == 1) {
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                        append(part)
                    }
                } else {
                    append(part)
                }
            }
        }
    }

/**
 * The settings hub's tile, shown large. The panel completes, it takes nothing away.
 * pourquoi : docs/decisions/second-ecran.md § The hub face completes, it takes nothing
 */
@Composable
private fun SettingsFace(model: SecondScreenModel.SettingsEntry) {
    val dark = LocalEmufiiDarkTheme.current
    val ink = if (model.social) {
        if (dark) Teal.darkBright else Teal.deep
    } else {
        if (dark) Teal.darkBright else Teal.deep
    }
    val axis = if (model.social) Teal.bright else Teal.bright

    // Constant height by construction: each text has a fixed line count.
    // pourquoi : docs/decisions/second-ecran.md § Every face centres in the same place
    Crossfade(
        targetState = model,
        animationSpec = tween(180),
        label = "settings-face"
    ) { shown ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(0.86f)
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(axis.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) { PanelMarkGlyph(shown.mark, ink) }

            Text(
                shown.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                shown.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = SUMMARY_LINES,
                maxLines = SUMMARY_LINES,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                shown.root + "  ›  " + shown.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


/**
 * It repeats the question, it does not ask one.
 * pourquoi : docs/decisions/second-ecran.md § A question's face repeats, it does not ask another
 */
@Composable
private fun AskingFace(model: SecondScreenModel.Asking) {
    val dark = LocalEmufiiDarkTheme.current
    val ink = if (model.social) {
        if (dark) Teal.darkBright else Teal.deep
    } else {
        if (dark) Teal.darkBright else Teal.deep
    }
    val axis = if (model.social) Teal.bright else Teal.bright

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxWidth(0.78f)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(axis.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { AskGlyph(ink) }

        Text(
            model.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            model.detail,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Drawn rather than typed: a character would take the text font and its italic. */
@Composable
private fun AskGlyph(tint: Color) {
    Canvas(Modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.11f
        val hook = Path().apply {
            moveTo(w * 0.30f, h * 0.32f)
            cubicTo(w * 0.30f, h * 0.10f, w * 0.76f, h * 0.10f, w * 0.72f, h * 0.34f)
            cubicTo(w * 0.69f, h * 0.52f, w * 0.50f, h * 0.50f, w * 0.50f, h * 0.68f)
        }
        drawPath(hook, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawCircle(tint, radius = stroke * 0.62f, center = Offset(w * 0.50f, h * 0.86f))
    }
}

/** Two: the longest of the seven settings summaries once wrapped. */
private const val SUMMARY_LINES = 2

@Composable
private fun PanelMarkGlyph(mark: PanelMark, tint: Color) {
    val size = 38.dp
    when (mark) {
        PanelMark.PROFILE -> PersonMark(color = tint, size = size)
        PanelMark.LIBRARY -> ShelfMark(color = tint, size = size)
        PanelMark.CONSOLES -> GridMark(color = tint, size = size)
        PanelMark.EMULATORS -> ChipMark(color = tint, size = size)
        PanelMark.APPEARANCE -> PaintMark(color = tint, size = size)
        PanelMark.GENERAL -> SlidersMark(color = tint, size = size)
        PanelMark.ABOUT -> InfoMark(color = tint, size = size)
        PanelMark.CRASH_LOGS -> BugMark(color = tint, size = size)
        PanelMark.SEARCH -> LensMark(color = tint, size = size)
        PanelMark.LAYOUT -> GridMark(color = tint, size = size)
        PanelMark.SORT -> SlidersMark(color = tint, size = size)
        PanelMark.SESSIONS -> SignalMark(color = tint, size = size)
        PanelMark.FRIENDS -> PersonMark(color = tint, size = size)
    }
}

/**
 * Two pages, the second reached from the front screen. Sliding, not cross-fading.
 * pourquoi : docs/decisions/second-ecran.md § The hover face: two pages, the second genuinely optional
 */
@Composable
private fun BrowsingPages(model: SecondScreenModel.Browsing, page: Int) {
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState > initialState
            val enter = slideInVertically(tween(320)) { if (forward) it else -it } + fadeIn(tween(220))
            val exit = slideOutVertically(tween(320)) { if (forward) -it else it } + fadeOut(tween(220))
            enter togetherWith exit
        },
        label = "panel-page"
    ) { shown ->
        if (shown == 0) Browsing(model) else Details(model)
    }
}

/**
 * The game under the cursor: its box on the left, what we know of it on the right.
 * pourquoi : docs/decisions/second-ecran.md § The hover face: two pages, the second genuinely optional
 */
@Composable
private fun Browsing(model: SecondScreenModel.Browsing) {
    val rom = model.rom
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(30.dp),
            modifier = Modifier.fillMaxWidth().align(Alignment.Center)
        ) {
            // The control sits under the thing it acts on, not mid-panel.
            // pourquoi : docs/decisions/second-ecran.md § A control belongs to what it acts on
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.width(196.dp)
            ) {
                Cover(model, modifier = Modifier.fillMaxWidth())
                PageTurn(up = false, label = stringResource(R.string.panel_page_details))
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                ConsoleBadge(rom.console)
                Text(
                    rom.displayName,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                model.rating?.let { rating ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        CompatBadge(rating)
                        Text(
                            compatLabel(rating),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                // Absent halves are not printed: nothing here is guessed.
                // pourquoi : docs/decisions/second-ecran.md § The hover face: two pages, the second genuinely optional
                DumpLine(model)
            }
        }
    }
}

@Composable
private fun DumpLine(model: SecondScreenModel.Browsing) {
    val parts = listOfNotNull(
        model.tags.region,
        model.meta?.genreFor(panelLocale()),
    )
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * The second page: what the game is, rather than which file it is. Everything
 * here is editorial and can be missing; nothing is claimed.
 * pourquoi : docs/decisions/second-ecran.md § The hover face: two pages, the second genuinely optional
 */
@Composable
private fun Details(model: SecondScreenModel.Browsing) {
    val locale = panelLocale()
    val meta = model.meta

    // Laid out to fit, never to scroll: a head of fixed height, then the summary taking
    // what is left and ending on an ellipsis at that height, never on half a line. It had
    // a fixed count of lines in a box of fixed height, and was cut through the middle of a
    // line whenever the two disagreed.
    // pourquoi : docs/decisions/second-ecran.md § Nothing scrolls, so everything has to fit
    val local = rememberFrontendStills(model.rom)
    val stills = local.ifEmpty { meta?.screenshots.orEmpty() }
    val summary = meta?.summaryFor(locale)
    val facts = listOfNotNull(
        meta?.genreFor(locale),
        // The year alone: `2016-01-21` makes the eye parse a date at a glance.
        meta?.released?.take(4)?.let { stringResource(R.string.panel_released, it) },
        model.tags.line(),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Cover(model, modifier = Modifier.size(DETAILS_COVER))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Text(
                    model.rom.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (facts.isNotEmpty()) {
                    // `FlowRow`: a French genre can be twice its English length.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        maxLines = 2
                    ) {
                        facts.forEach { fact ->
                            Text(
                                fact,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .socket(PillShape, LocalEmufiiDarkTheme.current)
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            when {
                summary != null -> {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // The licence asks for it.
                    meta.source?.let { source ->
                        Text(
                            source,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Only when the page really has nothing; pictures count.
                stills.isEmpty() && (meta == null || meta.isEmpty(locale)) -> Text(
                    stringResource(R.string.panel_details_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // The pictures are one tap away, not squeezed under the text: in a band they took
        // the summary's lines, and at a size to leave it room they were too small to see.
        // pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § The rear panel
        val shots = stills.take(GALLERY_MAX)
        // The pad on the front screen opens it; it has to know there is something to open.
        DisposableEffect(shots.size) {
            SecondScreen.galleryCount = shots.size
            onDispose { SecondScreen.galleryCount = 0 }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            if (shots.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier
                        .clip(PillShape)
                        .tap { SecondScreen.openGallery() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    PadKeyCap(PadHint.SCREENSHOTS)
                    Text(
                        stringResource(R.string.panel_screenshots, shots.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // The viewer rides inside the page-turn's box: a child of the column, even an
            // empty popup, took a spacing slot the moment it opened and pushed the page up.
            Box {
                PageTurn(up = true, label = stringResource(R.string.panel_page_back))
                Gallery(shots, title = model.rom.displayName)
            }
        }
    }
}

/**
 * The screenshots over the whole panel: the one shown large in the middle, its neighbours
 * smaller and fainter at the edges, sliding on the morph spring from one to the next. Driven
 * by the front pad through [SecondScreen.gallery], or by touch: a neighbour brings itself
 * forward, the backdrop closes. It opens growing out of the page and leaves the same way.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § The rear panel
 */
@Composable
private fun Gallery(stills: List<Any>, title: String) {
    val open by SecondScreen.gallery.collectAsStateWithLifecycle()
    // Held through the exit, or the pictures vanish before the veil has faded.
    var last by remember { mutableIntStateOf(0) }
    open?.let { last = it }
    val shown = open != null && stills.isNotEmpty()
    // Three clocks: the veil, the picture in front (on the morph spring, so it overshoots
    // once as it lands), and the rest, a beat later: neighbours from the edges, then the
    // title and the legend. Leaving, all three go together, fast.
    val on = rememberAnimationsEnabled()
    val veil = remember { Animatable(0f) }
    val hero = remember { Animatable(0f) }
    val rest = remember { Animatable(0f) }
    val morph = Motion.morph<Float>()
    val enter = Motion.enter<Float>()
    val exit = Motion.exit<Float>()
    LaunchedEffect(shown) {
        if (!on) {
            val v = if (shown) 1f else 0f
            veil.snapTo(v); hero.snapTo(v); rest.snapTo(v)
            return@LaunchedEffect
        }
        coroutineScope {
            if (shown) {
                launch { veil.animateTo(1f, enter) }
                launch { hero.animateTo(1f, morph) }
                launch { delay(GALLERY_REST_DELAY_MS); rest.animateTo(1f, enter) }
            } else {
                launch { rest.animateTo(0f, exit) }
                launch { hero.animateTo(0f, exit) }
                launch { delay(GALLERY_VEIL_LAG_MS); veil.animateTo(0f, exit) }
            }
        }
    }
    if ((!shown && veil.value <= 0.001f && hero.value <= 0.001f) || stills.isEmpty()) return
    val index = last.coerceIn(0, stills.lastIndex)
    val position by animateFloatAsState(index.toFloat(), Motion.morph(), label = "gallery-slide")
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { SecondScreen.closeGallery() },
        properties = PopupProperties(focusable = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = veil.value.coerceIn(0f, 1f) }
                .background(if (dark || oled) PanelScrimDark else PanelScrimLight)
                .tap(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { SecondScreen.closeGallery() }
                )
        ) {
            val height = maxHeight * 0.64f
            val step = maxWidth * 0.56f
            val rise = maxHeight * 0.34f
            ShadowsFollow({ veil.value.coerceIn(0f, 1f) }) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    stills.forEachIndexed { i, url ->
                        Box(
                            modifier = Modifier
                                .height(height)
                                .zIndex(if (i == index) 1f else 0f)
                                .graphicsLayer {
                                    val d = i - position
                                    val far = kotlin.math.abs(d).coerceAtMost(1.4f)
                                    val front = i == index
                                    // The front picture rises from the button, growing; the
                                    // others slide in from their side.
                                    val p = if (front) hero.value else rest.value
                                    val side = if (d < 0) -1f else 1f
                                    translationX = d * step.toPx() +
                                        if (front) 0f else side * (1f - p) * step.toPx() * 0.6f
                                    translationY = if (front) (1f - p) * rise.toPx() else 0f
                                    val s = (1f - 0.26f * far.coerceAtMost(1f)) *
                                        (if (front) 0.55f + 0.45f * p else 1f)
                                    scaleX = s
                                    scaleY = s
                                    alpha = ((1f - 0.55f * far) * p.coerceIn(0f, 1f)).coerceIn(0f, 1f)
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                }
                                .tap(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (i != index) SecondScreen.moveGallery(i - index)
                                },
                            contentAlignment = Alignment.Center
                        ) { Still(url) }
                    }
                }
            }

            // The game and where you are in its pictures.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 28.dp, top = 22.dp)
                    .bloom({ rest.value }, blur = 0.dp, rise = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${index + 1} / ${stills.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Dots with the elastic mark, and what the pad does here.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 20.dp)
                    .bloom({ rest.value }, blur = 0.dp, rise = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PadHintRow(PadHint.BROWSE)
                Spacer(Modifier.weight(1f))
                GalleryDots(count = stills.size, index = index)
                Spacer(Modifier.weight(1f))
                PadHintRow(PadHint.CLOSE)
            }
        }
    }
}

@Composable
private fun GalleryDots(count: Int, index: Int) {
    val dot = 8.dp
    val gap = 10.dp
    val mark = rememberElastic(target = (dot + gap) * index, width = dot)
    val tint = if (LocalEmufiiDarkTheme.current) Teal.darkBright else Teal.deep
    Box(Modifier.width((dot + gap) * count - gap).height(dot)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .offset(x = (dot + gap) * i)
                    .size(dot)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
            )
        }
        Box(
            Modifier
                .offset { IntOffset(mark.start.roundToPx(), 0) }
                .size(width = mark.length, height = dot)
                .clip(CircleShape)
                .background(tint)
        )
    }
}

private const val GALLERY_MAX = 6

/** The neighbours, title and legend follow the front picture by this much. */
private const val GALLERY_REST_DELAY_MS = 90L

/** Leaving, the veil lingers a little behind the pictures so they do not cut to bare. */
private const val GALLERY_VEIL_LAG_MS = 40L

private val PanelScrimLight = Color(0xF2F1EFEA)
private val PanelScrimDark = Color(0xF2120F1D)

private val DETAILS_COVER = 96.dp


/**
 * Read off this window's configuration, never the process default.
 * pourquoi : docs/decisions/second-ecran.md § The language comes from the window, not from the process
 */
@Composable
private fun panelLocale(): java.util.Locale {
    val context = LocalContext.current
    return remember(context, context.resources.configuration) {
        androidx.core.os.ConfigurationCompat.getLocales(context.resources.configuration)
            .get(0) ?: java.util.Locale.getDefault()
    }
}

/** Off the main thread: the folder listing is a disk read. */
@Composable
private fun rememberFrontendStills(rom: eu.emufii.app.library.Rom): List<Any> {
    val context = LocalContext.current
    val settings = remember(context) { eu.emufii.app.settings.SettingsStore.get(context) }
    val folder by settings.frontendFolder.collectAsStateWithLifecycle()
    val frontend by settings.artworkFrontend.collectAsStateWithLifecycle()
    val stills = remember(rom.uri, folder, frontend) { mutableStateOf<List<Any>>(emptyList()) }
    LaunchedEffect(rom.uri, folder, frontend) {
        stills.value = withContext(Dispatchers.IO) {
            runCatching {
                eu.emufii.app.artwork.FrontendMedia.stillsFor(
                    context,
                    frontend,
                    folder.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse),
                    rom
                )
            }.getOrDefault(emptyList())
        }
    }
    return stills.value
}

@Composable
private fun Still(url: Any) {
    val context = LocalContext.current
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    // A screen's usual shape until the picture says otherwise.
    var ratio by remember(url) { mutableStateOf(4f / 3f) }
    AsyncImage(
        model = ImageRequest.Builder(context).data(url).build(),
        contentDescription = null,
        // Fit, not crop: these are pictures of a screen, and a screen cropped loses
        // exactly the words printed on it. The box takes the picture's own shape, so
        // Fit fills it.
        contentScale = ContentScale.Fit,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) ratio = size.width / size.height
        },
        placeholder = ColorPainter(Color.Transparent),
        error = ColorPainter(Color.Transparent),
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(ratio, matchHeightConstraintsFirst = true)
            .liftShadow(ArtworkShape, 4.dp, dark, oled)
            .clip(ArtworkShape)
    )
}

/**
 * The way to the other page: an arrow on a cap, and the button that turns it.
 * pourquoi : docs/decisions/second-ecran.md § A control belongs to what it acts on
 */
@Composable
private fun PageTurn(up: Boolean, label: String, modifier: Modifier = Modifier) {
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .plate(PillShape, dark = dark, oled = oled, lift = 3.dp)
        ) {
            ArrowGlyph(tint = MaterialTheme.colorScheme.onSurface, up = up)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
    }
}

/**
 * The arrow, drawn rather than typed.
 * pourquoi : docs/decisions/second-ecran.md § The legend, and why the symbols are drawn
 */
@Composable
private fun ArrowGlyph(tint: Color, up: Boolean) {
    Canvas(Modifier.size(13.dp).rotate(if (up) 180f else 0f)) {
        val w = size.width
        val h = size.height
        val stem = w * 0.26f
        drawRoundRect(
            color = tint,
            topLeft = Offset((w - stem) / 2f, 0f),
            size = Size(stem, h * 0.55f),
            cornerRadius = CornerRadius(stem / 2f, stem / 2f)
        )
        val head = Path().apply {
            moveTo(w * 0.12f, h * 0.48f)
            lineTo(w * 0.88f, h * 0.48f)
            lineTo(w / 2f, h)
            close()
        }
        drawPath(head, tint)
    }
}

/**
 * The box, moulded onto the tray, its shadow tinted with the colour the artwork
 * gave up (`Rom.accentArgb`). No extracted tone: the tray's own shadow, and
 * nothing else changes.
 * pourquoi : docs/decisions/second-ecran.md § The artwork is moulded into the board, and its shadow is its colour
 */
/**
 * As a fraction of its side, not in dp: it is the same tile as the grid's.
 * pourquoi : docs/decisions/second-ecran.md § The artwork's corners follow its scale, not its measurement
 */
private val CoverShape = RoundedCornerShape(17)
private val CoverArtworkShape = RoundedCornerShape(15)

@Composable
private fun Cover(model: SecondScreenModel.Browsing, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    val art by rememberTileArt(model.rom)

    val tone = CoverTone.of(art.model) ?: model.rom.accentArgb?.let { Color(it) }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .liftShadow(CoverShape, 16.dp, dark, oled, tint = tone)
            // A plate with the picture inset, not a rim: a rim survives 0.38% of the
            // cover's width.
            // pourquoi : docs/decisions/second-ecran.md § The artwork is moulded into the board, and its shadow is its colour
            .plate(CoverShape, dark = dark, oled = oled, lift = 0.dp)
            .padding(9.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CoverArtworkShape)
                .background(tilePlateBrush(dark, oled))
        ) {
            val cover = art.model
            if (cover != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(cover).size(COVER_REQUEST_PX).build(),
                    onSuccess = { CoverTone.learn(cover, it.result.image) },
                    contentDescription = null,
                    contentScale = if (art.fitsWhole) ContentScale.Fit else ContentScale.Crop,
                    filterQuality = if (art.isPixelArt) FilterQuality.None else FilterQuality.High,
                    placeholder = ColorPainter(Color.Transparent),
                    error = ColorPainter(Color.Transparent),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().socket(CoverArtworkShape, dark)
                ) {
                    Text(
                        model.rom.console.shortLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsoleBadge(console: Console) {
    val dark = LocalEmufiiDarkTheme.current
    Text(
        console.label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .socket(PillShape, dark)
            .padding(horizontal = 13.dp, vertical = 5.dp)
    )
}

/**
 * A session is up, and the code is the whole point. It carries no label.
 * pourquoi : docs/decisions/second-ecran.md § The session code carries no label
 */
@Composable
private fun InSession(model: SecondScreenModel.InSession) {
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    val accent = LocalAccent.current
    val steps by SecondScreen.steps.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // One column: split, each half fell to 268 dp and the port wrapped one digit per
    // line.
    // pourquoi : docs/decisions/second-ecran.md § One column, and the code takes the whole width
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        // No height compensation: the 62 dp hollow was there for a legend this face
        // does not draw.
        // pourquoi : docs/decisions/second-ecran.md § Both bands are permanent, the legend included
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            model.console?.let { ConsoleBadge(it) }
            model.gameTitle?.let { title ->
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 300.dp)
                )
            }
        }

        // Ten of lift rather than four: this is the one object on the screen.
        Box(
            modifier = Modifier.plate(CardShape, dark = dark, oled = oled, lift = 10.dp)
        ) {
            val codeSize = if (steps.isEmpty()) 80.sp else 64.sp
            // Coral, like the chip on the main screen: the code is the session's, and the
            // session is the social axis. It was teal here, one object in two colours.
            // pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § One look for the code
            RevealCode(
                model.code,
                style = TextStyle(
                    fontSize = codeSize,
                    lineHeight = codeSize * 1.05f,
                    // Monospace: at a distance a 2 and a Z differ by stroke width as much
                    // as by shape.
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center
                ),
                color = if (dark) Teal.darkBright else Teal.deep,
                // The main screen's chip already ticks; two panels, one sound.
                sound = false,
                modifier = Modifier.padding(
                    horizontal = 34.dp,
                    vertical = if (steps.isEmpty()) 20.dp else 12.dp
                )
            )
            CopyChip(
                onCopy = { copyToClipboard(context, model.code, model.code) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 8.dp),
            )
        }

        // Engraved, not plated: a reference to read off, not an object to reach for.
        // pourquoi : docs/decisions/second-ecran.md § The session code carries no label
        if (model.hostAddress != null || model.port != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                model.hostAddress?.let { value ->
                    val label = stringResource(R.string.session_host_address)
                    Fact(label, value, onCopy = { copyToClipboard(context, label, value) })
                }
                model.port?.let { value ->
                    val label = stringResource(R.string.session_port)
                    Fact(label, value, onCopy = { copyToClipboard(context, label, value) })
                }
            }
        }

        // The pad aims with an index carried by the singleton: focus does not cross
        // windows.
        // pourquoi : docs/decisions/second-ecran.md § One column, and the code takes the whole width
        if (steps.isNotEmpty()) {
            val stepCursor by SecondScreen.stepCursor.collectAsStateWithLifecycle()
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(IntrinsicSize.Min)
            ) {
                steps.forEachIndexed { index, step ->
                    StepButton(step, selected = index == stepCursor, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Two columns past five: the panel is wide and short.
 * pourquoi : docs/decisions/second-ecran.md § The friends list goes to the back, both cards stay in front
 */
@Composable
private fun FriendsFace(model: SecondScreenModel.Friends) {
    // The removal question lives here, where the finger just pressed.
    // pourquoi : docs/decisions/second-ecran.md § The friends list goes to the back, both cards stay in front
    var confirming by remember { mutableStateOf<PanelFriend?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // A title and a count, nothing else: the rows say the rest.
            // pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Friends
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.friends_panel_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val online = model.entries.count { it.online }
                if (online > 0) {
                    Text(
                        stringResource(R.string.friends_count_online, online),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (model.entries.isEmpty()) {
                Text(
                    stringResource(R.string.friends_none_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            // Always two columns: a full-width name and two words never fill the panel.
            val half = (model.entries.size + 1) / 2
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(model.entries.take(half), model.entries.drop(half)).forEach { column ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        column.forEach { PanelFriendRow(it, onRemove = { confirming = it }) }
                    }
                }
            }
        }

        confirming?.let { friend ->
            PanelConfirm(
                friend = friend,
                onCancel = { confirming = null },
                onConfirm = {
                    friend.onRemove()
                    confirming = null
                }
            )
        }
    }
}

/**
 * Asked on the panel itself, never a `Dialog`: that belongs to the window that opens
 * it.
 * pourquoi : docs/decisions/second-ecran.md § The friends list goes to the back, both cards stay in front
 */
@Composable
private fun PanelConfirm(friend: PanelFriend, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InkText.copy(alpha = if (dark) 0.74f else 0.62f))
            .tap(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .plate(CardShape, dark = dark, oled = oled, lift = 8.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.friends_remove_confirm, friend.name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton(
                    label = stringResource(R.string.friends_cancel),
                    onClick = onCancel
                )
                GhostButton(
                    label = stringResource(R.string.friends_remove),
                    onClick = onConfirm,
                    tint = if (dark) ErrorDark else ErrorLight
                )
            }
        }
    }
}

@Composable
private fun PanelFriendRow(friend: PanelFriend, onRemove: () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    val here = friend.online || friend.inSession
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .plate(CardShape, dark = dark, oled = oled, lift = 4.dp)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Avatar(name = friend.name, size = 36.dp)
            if (here) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (dark) PlateDark else PlateLight),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (dark) GoodDark else GoodLight)
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                friend.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (here) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // An offline friend carries no line: the muted name already says it.
            if (here) {
                Text(
                    friend.line,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 48 dp: the target was 38, under the minimum, and a miss here removes a
        // friend.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .tap(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            CrossIcon(size = 14.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The same drawing as on the front screen: an action plate, green and ticked once done.
 * pourquoi : docs/decisions/second-ecran.md § The panel takes the steps, because it is touch
 */
@Composable
private fun StepButton(step: PanelStep, selected: Boolean, modifier: Modifier = Modifier) {
    val dark = LocalEmufiiDarkTheme.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    // The front button's three looks and its two moments, on the panel too: with the panel
    // live the front one is not drawn, and the steps showed no motion at all.
    // pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § The auto-setup button, host and guest
    val container by animateColorAsState(
        when {
            step.done -> if (dark) GoodDark else GoodLight
            // A locked step keeps a solid plate and ink: it is not out of order, it is next.
            // pourquoi : docs/decisions/second-ecran.md § A locked step has to stay readable at arm's length
            !step.enabled && !step.busy -> onSurface.copy(alpha = 0.10f)
            else -> primary
        },
        Motion.tint(),
        label = "panel-step-container"
    )
    val content by animateColorAsState(
        if (!step.enabled && !step.busy && !step.done) onSurface.copy(alpha = 0.62f)
        else MaterialTheme.colorScheme.onPrimary,
        Motion.tint(),
        label = "panel-step-content"
    )
    val on = rememberAnimationsEnabled()
    val wake = remember { Animatable(1f) }
    var wasLocked by remember { mutableStateOf(!step.enabled) }
    var wasWaiting by remember { mutableStateOf(step.waiting) }
    val pop = Motion.pop<Float>()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(step.enabled) {
        if (wasLocked && step.enabled) {
            awaitSeen(lifecycle)
            if (!wasWaiting) delay(380L)
            // The guest's moment has its sound; step 2 lighting up follows a tick that rang.
            if (wasWaiting) Sfx.pop()
            if (on) {
                wake.snapTo(0.92f)
                wake.animateTo(1f, pop)
            }
        }
        wasLocked = !step.enabled
        wasWaiting = step.waiting
    }
    Box(
        modifier = modifier
            .padding(6.dp)
            .graphicsLayer {
                scaleX = wake.value
                scaleY = wake.value
            }
            .focusRing(selected, ActionShape)
    ) {
        Button(
            onClick = sounded(step.onPress),
            enabled = step.enabled,
            shape = ActionShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = container,
                disabledContentColor = content
            ),
            // Material's 24 dp are set for a dialog button, not two plates sharing a
            // row.
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight().heightIn(min = 64.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    // The tick, which green alone does not replace; it draws itself.
                    // pourquoi : docs/decisions/second-ecran.md § The panel takes the steps, because it is touch
                    step.done -> DrawnCheck(
                        done = true,
                        disc = content.copy(alpha = 0.22f),
                        ink = content,
                        size = 20.dp
                    )
                    step.busy || step.waiting -> TrailerSpinner(
                        color = content,
                        size = 18.dp,
                        stroke = 2.5.dp,
                        fps = if (step.waiting) 30 else 60
                    )
                }
                Text(
                    step.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    // Three, because a label already takes two and a longer language
                    // must not be squeezed.
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, onCopy: (() -> Unit)? = null) {
    val dark = LocalEmufiiDarkTheme.current
    Box(
        modifier = Modifier
            .socket(RoundedCornerShape(14.dp), dark)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            // Neither label nor value wraps: squeezed between two columns, "Port" broke as
            // "Por / t".
            // pourquoi : docs/decisions/second-ecran.md § The panel takes the steps, because it is touch
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
        if (onCopy != null) {
            CopyChip(
                onCopy = onCopy,
                iconSize = 14.dp,
                targetSize = 24.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (16).dp, y = (-8).dp),
            )
        }
    }
}

@Composable
private fun CopyChip(
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    targetSize: Dp = 32.dp,
) {
    Box(
        modifier = modifier.size(targetSize).tap(onClick = onCopy),
        contentAlignment = Alignment.Center,
    ) {
        CopyMark(size = iconSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Leave on the left, act on the right. An empty side takes no room.
 * pourquoi : docs/decisions/second-ecran.md § The legend, and why the symbols are drawn
 */
@Composable
private fun Legend(legend: PadLegend, modifier: Modifier = Modifier) {
    Row(
        // It keeps its height when empty, or the resting face makes the ground drop.
        // pourquoi : docs/decisions/second-ecran.md § Both bands are permanent, the legend included
        modifier = modifier.heightIn(min = LEGEND_CAP),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (legend.isEmpty) return@Row
        Cluster(legend.left)
        Cluster(legend.right)
    }
}

@Composable
private fun Cluster(hints: List<PadHint>) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        hints.forEach { hint -> PadHintRow(hint) }
    }
}

/**
 * Drawn rather than imported: it sits where it is put, where a glyph centres on its
 * line box.
 * pourquoi : docs/decisions/second-ecran.md § The panel takes the steps, because it is touch
 */
@Composable
private fun StepCheck(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.16f, cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(w * 0.16f, w * 0.55f)
            lineTo(w * 0.40f, w * 0.79f)
            lineTo(w * 0.86f, w * 0.24f)
        }
        drawPath(path, color = color, style = stroke)
    }
}
