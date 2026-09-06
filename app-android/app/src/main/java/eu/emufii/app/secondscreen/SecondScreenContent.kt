package eu.emufii.app.secondscreen

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.emufii.app.BuildConfig
import eu.emufii.app.R
import eu.emufii.app.artwork.rememberTileArt
import eu.emufii.app.library.Console
import eu.emufii.app.ui.ActionShape
import eu.emufii.app.ui.SilenceSystemSfx
import eu.emufii.app.ui.components.Avatar
import eu.emufii.app.ui.components.ChipMark
import eu.emufii.app.ui.components.CompatBadge
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
import eu.emufii.app.ui.theme.moldedRim
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.theme.tilePlateBrush
import eu.emufii.app.ui.wallpaper.TrayBackdrop
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(keys)
            .focusProperties { canFocus = true }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.ButtonR1) {
                    SecondScreen.flipPage()
                    true
                } else {
                    false
                }
            }
    ) {
        // This window has no wallpaper behind it, so the tray is painted rather
        // than shown through.
        TrayBackdrop(modifier = Modifier.fillMaxSize(), dark = dark)

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
                                    ?: shown.console
                            )
                            is SecondScreenModel.Browsing -> BrowsingPages(shown, page)
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
            (slideInVertically(tween(260)) { -it } + fadeIn(tween(260)))
                .togetherWith(fadeOut(tween(200)))
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
 * The cursor is on a console's folder: what playing together means on that
 * machine. The machine's name leads, then two lines, then a warning if it has
 * one.
 * pourquoi : docs/decisions/second-ecran.md § The console card: what it says, and what it does not
 */
@Composable
private fun ConsoleCard(console: Console) {
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current

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
            .plate(CardShape, dark = dark, oled = oled, lift = 8.dp)
    ) { shown ->
        val brief = remember(shown) { consoleBrief(shown) }
        Column(
            verticalArrangement = Arrangement.spacedBy(15.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 26.dp)
        ) {
            Text(
                stringResource(R.string.brief_console_title, shown.label),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(brief.first),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(brief.second),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            brief.warning?.let { warning ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // A bar, never a warning triangle: this panel does not shout.
                    // pourquoi : docs/decisions/second-ecran.md § The panel does not shout
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(34.dp)
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        stringResource(warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
        if (dark) Coral.darkBright else Coral.deep
    } else {
        if (dark) Teal.darkBright else Teal.deep
    }
    val axis = if (model.social) Coral.bright else Teal.bright

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
        if (dark) Coral.darkBright else Coral.deep
    } else {
        if (dark) Teal.darkBright else Teal.deep
    }
    val axis = if (model.social) Coral.bright else Teal.bright

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

    // Laid out to fit, never to scroll; the paragraph yields its lines first.
    // pourquoi : docs/decisions/second-ecran.md § Nothing scrolls, so everything has to fit
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            model.rom.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val facts = listOfNotNull(
            meta?.genreFor(locale),
            // The year alone: `2016-01-21` makes the eye parse a date at a glance.
            meta?.released?.take(4)?.let { stringResource(R.string.panel_released, it) },
            model.tags.line(),
        )
        if (facts.isNotEmpty()) {
            // `FlowRow`, not `Row`: the French genre of a game can be twice the
            // length of its English one ("jeu de construction de paquet de
            // cartes roguelike"), and a plain row sent the date pill off the
            // panel instead of onto the line below.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                facts.forEach { fact ->
                    Text(
                        fact,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .socket(PillShape, LocalEmufiiDarkTheme.current)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // The card's own pictures first; the catalogue only fills in for a game the
        // frontend never saw.
        val local = rememberFrontendStills(model.rom)
        val stills = local.ifEmpty { meta?.screenshots.orEmpty() }
        val summary = meta?.summaryFor(locale)

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (summary != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (stills.isEmpty()) 9 else 5,
                        overflow = TextOverflow.Ellipsis
                    )
                    // The licence asks for it, and the panel has room.
                    meta.source?.let { source ->
                        Text(
                            source,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (stills.isEmpty() && (meta == null || meta.isEmpty(locale))) {
                // Only when the page really has nothing; pictures count.
                Text(
                    stringResource(R.string.panel_details_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (stills.isNotEmpty()) Screenshots(stills)

        PageTurn(
            up = true,
            label = stringResource(R.string.panel_page_back),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

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
private fun Screenshots(urls: List<Any>) {
    val context = LocalContext.current
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        urls.take(3).forEach { url ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(16f / 9f)
                    .plate(TileShape, dark = dark, oled = oled, lift = 0.dp)
                    .padding(5.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(url).build(),
                    contentDescription = null,
                    // Fit, not crop: these are pictures of a screen, and a
                    // screen cropped loses exactly the words printed on it.
                    contentScale = ContentScale.Fit,
                    placeholder = ColorPainter(Color.Transparent),
                    error = ColorPainter(Color.Transparent),
                    modifier = Modifier.fillMaxSize().clip(ArtworkShape)
                )
            }
        }
    }
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

    val tone = model.rom.accentArgb?.let { Color(it) }
    val shadow = tone ?: InkText

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(
                // On OLED the tray is truly off and a shadow draws nothing; the
                // edge and bevel below carry the separation alone.
                elevation = if (oled) 0.dp else 20.dp,
                shape = CoverShape,
                clip = false,
                ambientColor = shadow.copy(alpha = if (dark) 0.55f else 0.30f),
                spotColor = shadow.copy(alpha = if (dark) 0.75f else 0.42f)
            )
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
                .moldedRim(CoverArtworkShape, dark = dark, oled = oled)
        ) {
            val cover = art.model
            if (cover != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(cover).build(),
                    contentDescription = null,
                    contentScale = if (art.isPixelArt) ContentScale.Fit else ContentScale.Crop,
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
            modifier = Modifier
                .plate(CardShape, dark = dark, oled = oled, lift = 10.dp)
                .padding(horizontal = 34.dp, vertical = if (steps.isEmpty()) 20.dp else 12.dp)
        ) {
            val codeSize = if (steps.isEmpty()) 80.sp else 64.sp
            Text(
                model.code,
                fontSize = codeSize,
                lineHeight = codeSize * 1.05f,
                // Monospace: at a distance a 2 and a Z differ by stroke width as much
                // as by shape.
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = accent.bright,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }

        // Engraved, not plated: a reference to read off, not an object to reach for.
        // pourquoi : docs/decisions/second-ecran.md § The session code carries no label
        if (model.hostAddress != null || model.port != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                model.hostAddress?.let { Fact(stringResource(R.string.session_host_address), it) }
                model.port?.let { Fact(stringResource(R.string.session_port), it) }
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
            Text(
                stringResource(R.string.friends_panel_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .socket(RoundedCornerShape(14.dp), dark)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Avatar(name = friend.name, size = 34.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    friend.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(
                            if (friend.online) if (dark) GoodDark else GoodLight
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                )
            }
            Text(
                friend.line,
                style = MaterialTheme.typography.bodySmall,
                color = if (friend.inSession) if (dark) GoodDark else GoodLight
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            CrossIcon(size = 16.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Box(
        modifier = modifier
            .padding(6.dp)
            .focusRing(selected, ActionShape)
    ) {
        Button(
            onClick = sounded(step.onPress),
            enabled = step.enabled,
            shape = ActionShape,
            // A locked step keeps a solid plate and ink: it is not out of order, it is
            // next.
            // pourquoi : docs/decisions/second-ecran.md § A locked step has to stay readable at arm's length
            colors = if (step.done) {
                ButtonDefaults.buttonColors(containerColor = if (dark) GoodDark else GoodLight)
            } else {
                ButtonDefaults.buttonColors(
                    disabledContainerColor =
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    disabledContentColor =
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            },
            // Material's 24 dp are set for a dialog button, not two plates sharing a
            // row.
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight().heightIn(min = 64.dp)
        ) {
            // The tick, which green alone does not replace.
            // pourquoi : docs/decisions/second-ecran.md § The panel takes the steps, because it is touch
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step.done) StepCheck(color = LocalContentColor.current, size = 20.dp)
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
private fun Fact(label: String, value: String) {
    val dark = LocalEmufiiDarkTheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier
            .socket(RoundedCornerShape(14.dp), dark)
            .padding(horizontal = 18.dp, vertical = 9.dp)
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
