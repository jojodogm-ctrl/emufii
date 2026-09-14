package eu.emufii.app.ui.components

import eu.emufii.app.ui.sounded
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import eu.emufii.app.ui.Motion
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.focusRequester
import eu.emufii.app.secondscreen.SecondScreen
import eu.emufii.app.secondscreen.SecondScreenModel
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import kotlinx.coroutines.delay
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import eu.emufii.app.R
import eu.emufii.app.compat.CompatEntry
import eu.emufii.app.compat.LocalCompatDb
import eu.emufii.app.library.Backend
import eu.emufii.app.library.Console
import androidx.compose.ui.platform.LocalContext
import eu.emufii.app.ps2.Ps2NetworkProfile
import eu.emufii.app.library.Rom
import eu.emufii.app.library.compatKeys
import eu.emufii.app.ui.LocalRingTone
import eu.emufii.app.ui.RingTone
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.rememberAnimationsEnabled
import eu.emufii.app.ui.theme.CardShape
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.InkText
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.Teal
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import eu.emufii.app.ui.tap

private const val TILE_HUE_MS = 7000

/**
 * The game, what is about to happen to it, and the one button that starts it.
 * pourquoi : docs/decisions/lancement-et-navigation.md § The card replaced a bottom sheet, and for two reasons
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameLaunchDialog(
    rom: Rom,
    onDismiss: () -> Unit,
    /** [private]: the session will not show up in the finder. */
    onPrimary: (private: Boolean) -> Unit,
    onJoinWithCode: (() -> Unit)?,
    /**
     * Straight into the console's public multiplayer, no session and no tunnel.
     * pourquoi : docs/decisions/lancement-et-navigation.md § The choice of world comes first, not last
     */
    onPlayOnline: (() -> Unit)? = null,
) {
    val dark = LocalEmufiiDarkTheme.current
    var starting by remember { mutableStateOf(false) }

    /**
     * A PS2 session without a network profile on the card cannot be played.
     * pourquoi : docs/decisions/lancement-et-navigation.md § What replaces the buttons when a prerequisite is missing
     */
    val ps2Blocked = rom.console == Console.PS2 && !rememberPs2Ready()

    /**
     * Hidden from the finder? Public by default.
     * pourquoi : docs/decisions/lancement-et-navigation.md § "Private session" promises exactly what the coordinator delivers
     */
    var isPrivate by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    // Stacked, it runs floor to ceiling on a landscape handheld.
    // pourquoi : docs/decisions/lancement-et-navigation.md § The card replaced a bottom sheet, and for two reasons
    val wide = configuration.screenWidthDp > configuration.screenHeightDp
    val compact = !wide && configuration.screenHeightDp < 520

    // The public side rewrites the card, it does not open a second screen.
    var publicMode by remember { mutableStateOf(false) }
    val online = rom.console.backend == Backend.MELONDS_WFC || publicMode

    /**
     * A PSP session leans on the per-game INI; the public online mode is not blocked.
     * pourquoi : docs/decisions/lancement-et-navigation.md § What replaces the buttons when a prerequisite is missing
     */
    val pspBlocked = rom.console == Console.PSP && !online && !rememberPpssppReady()
    val setupBlocked = ps2Blocked || pspBlocked

    // A fixed beat, not a measurement: what follows has its own progress screen.
    LaunchedEffect(starting) {
        if (starting) {
            delay(START_PAUSE_MS)
            if (publicMode) onPlayOnline?.invoke() else onPrimary(isPrivate)
        }
    }

    /**
     * The card follows the thumb out rather than vanishing on release. Kept enabled even
     * while starting -- disabled, a B during launch closed the app -- but the gesture is
     * then swallowed and moves nothing.
     * pourquoi : docs/decisions/lancement-et-navigation.md § The cursor has to enter the card, and not leave it again
     */
    val back = remember { Animatable(0f) }
    var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val launching by rememberUpdatedState(starting)
    val settle = Motion.press<Float>()
    PredictiveBackHandler { events ->
        if (launching) {
            events.collect {}
            return@PredictiveBackHandler
        }
        try {
            events.collect { event ->
                backEdge = event.swipeEdge
                back.snapTo(event.progress)
            }
            onDismiss()
        } catch (cancelled: CancellationException) {
            // Let go halfway: it comes back, it does not blink back.
            back.animateTo(0f, settle)
            throw cancelled
        }
    }

    // Flipped from a LaunchedEffect: an animation starting at its target plays nothing.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    // The cursor enters by the primary button; fails in touch mode by design.
    // pourquoi : docs/decisions/lancement-et-navigation.md § The cursor has to enter the card, and not leave it again
    val firstAction = remember { FocusRequester() }
    /**
     * A plain `focusable()` takes focus in touch mode where a `clickable` cannot.
     * pourquoi : docs/decisions/lancement-et-navigation.md § The cursor has to enter the card, and not leave it again
     */
    val cardRoot = remember { FocusRequester() }
    var rootHasCursor by remember { mutableStateOf(false) }
    // The panel learns the card is open; it kept the game's face otherwise.
    // pourquoi : docs/decisions/second-ecran.md § What travels to the panel
    val askTitle = rom.displayName
    val askDetail = stringResource(R.string.panel_asking_launch)
    DisposableEffect(askTitle, askDetail) {
        val token = SecondScreen.putAside(
            SecondScreenModel.Asking(title = askTitle, detail = askDetail, social = true)
        )
        onDispose { SecondScreen.takeBack(token) }
    }

    val inputMode = LocalInputModeManager.current
    LaunchedEffect(Unit) {
        // `getOrDefault`, not `isSuccess`: `requestFocus` returns false without throwing.
        // pourquoi : docs/decisions/lancement-et-navigation.md § The cursor has to enter the card, and not leave it again
        repeat(10) {
            // Ask for keyboard mode first: the fallback below only reported the symptom.
            // pourquoi : docs/decisions/coquille-ecrans.md § The cursor arrives with the screen
            inputMode.requestInputMode(InputMode.Keyboard)
            if (runCatching { firstAction.requestFocus() }.getOrDefault(false)) {
                return@LaunchedEffect
            }
            delay(40)
        }
        // The fallback stays: a card with no primary action offers the cursor nothing.
        runCatching { cardRoot.requestFocus() }
    }

    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "launch-card-entrance"
    )
    val steps = if (publicMode) {
        // Not the DS's: it dials its revival server itself, and the PSP has two settings.
        // pourquoi : docs/decisions/lancement-et-navigation.md § The choice of world comes first, not last
        listOf(
            stringResource(R.string.launch_psp_public_1),
            stringResource(R.string.launch_psp_public_2),
            stringResource(R.string.launch_psp_public_3)
        )
    } else if (online) {
        listOf(
            stringResource(R.string.launch_online_1),
            stringResource(R.string.launch_online_2),
            stringResource(R.string.launch_online_3)
        )
    } else {
        listOf(
            stringResource(R.string.launch_session_1),
            stringResource(R.string.launch_session_2),
            stringResource(R.string.launch_session_3)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // The tray dims, it does not frost: warm ink, never blue-black.
            // pourquoi : docs/decisions/lancement-et-navigation.md § The board darkens, it does not frost
            .background(
                InkText.copy(alpha = (if (dark) 0.74f else 0.62f) * entrance)
            )
            // Swallows taps and is not a cursor stop: traversal halted on a ringless node.
            // pourquoi : docs/decisions/lancement-et-navigation.md § The cursor has to enter the card, and not leave it again
            .focusProperties { canFocus = false }
            .tap(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !starting,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        // The two axes on the card's contour, replacing a tile laid behind it.
        // pourquoi : docs/decisions/theme-duotone-shelves.md § Game card (dialog)
        val blend = if (rememberAnimationsEnabled()) {
            rememberInfiniteTransition(label = "tile-hue").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(TILE_HUE_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "tile-hue"
            ).value
        } else {
            // Animations off: a blend of both axes, neither being the right one.
            0.5f
        }

        SoftCard(
            modifier = Modifier
                .focusRequester(cardRoot)
                .onFocusEvent { rootHasCursor = it.isFocused }
                // The root holds the keys, never the cursor: the first direction hands it over.
                .onPreviewKeyEvent { event ->
                    if (!rootHasCursor || event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    runCatching { firstAction.requestFocus() }.getOrDefault(false)
                }
                .focusable()
                // `exit` refuses the crossing in every direction, unlike `canFocus = false`.
                // pourquoi : docs/decisions/lancement-et-navigation.md § The cursor has to enter the card, and not leave it again
                .focusGroup()
                .focusProperties { onExit = { cancelFocusChange() } }
                // In preview: otherwise the first press only took the cursor off the button.
                // pourquoi : docs/decisions/lancement-et-navigation.md § The cursor has to enter the card, and not leave it again
                .onPreviewKeyEvent { event ->
                    val back = event.key == Key.Back || event.key == Key.ButtonB
                    if (!back || starting) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    true
                }
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = if (wide) 648.dp else 360.dp)
                // Bounded by the screen, never by a number.
                // pourquoi : docs/decisions/lancement-et-navigation.md § The card replaced a bottom sheet, and for two reasons
                .heightIn(max = (configuration.screenHeightDp - 32).dp)
                // One layer for the arrival and the departure: two modifiers fighting
                // over scale would have the card blink at the hand-over.
                .graphicsLayer {
                    val leaving = back.value
                    val size = (0.92f + 0.08f * entrance) * (1f - 0.12f * leaving)
                    scaleX = size
                    scaleY = size
                    // Three times the speed of the geometry: the cover flying in from
                    // its tile is *inside* this layer, and at the card's own opacity it
                    // made the trip invisible -- the grid showed a hole, then the card
                    // appeared with the cover already home.
                    alpha = (entrance * 3f).coerceAtMost(1f) * (1f - 0.45f * leaving)
                    translationX =
                        (if (backEdge == BackEventCompat.EDGE_LEFT) 1f else -1f) *
                            24.dp.toPx() * leaving
                }
                // Here, not at the head of the chain: `drawWithContent` takes the wrapped size.
                .waitTrim(blend)
                // Taps only; `canFocus = false` here would disable the whole subtree.
                // pourquoi : docs/decisions/lancement-et-navigation.md § The cursor has to enter the card, and not leave it again
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            val primaryLabel = stringResource(
                when {
                    publicMode -> R.string.lib_open_emulator
                    online -> R.string.lib_play_online
                    else -> R.string.lib_create_session
                }
            )

            // Only the DS gets the tall cover with the title moved across: its right
            // column is short. Every other console stacks a mode switch, the steps, a
            // privacy toggle and two buttons in there, and the moved title left that
            // column no room -- labels clipped, and steps you had to scroll to read.
            val dsCard = rom.console == Console.DS

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(26.dp)
                ) {
                    // The game as an object: the cover, and the verdict stamped under
                    // it. A figure beside its text, so it centres on the column that
                    // sets the card's height instead of being asked to match it.
                    // pourquoi : docs/decisions/lancement-et-navigation.md § What gives way, and in what order
                    Column(
                        modifier = Modifier
                            .width(if (dsCard) 150.dp else 186.dp)
                            .align(Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (dsCard) 12.dp else 14.dp)
                    ) {
                        RomArtwork(rom, size = if (dsCard) 134.dp else 120.dp)
                        // The tile is scanned, this card is read: here there is room for
                        // what the mark means.
                        // pourquoi : docs/decisions/lancement-et-navigation.md § The compatibility verdict, where the decision is made
                        if (dsCard) {
                            LocalCompatDb.current.ratingFor(rom.compatKeys())?.let { known ->
                                CompatNote(known)
                            }
                        } else {
                            TitleBlock(rom, online)
                        }
                    }

                    // Name it, say what will happen, then act: one column read top to
                    // bottom, ending on the button. It is the tall side by construction,
                    // so nothing in the card floats in the middle.
                    // pourquoi : docs/decisions/lancement-et-navigation.md § What gives way, and in what order
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically),
                        verticalArrangement = Arrangement.spacedBy(if (dsCard) 18.dp else 14.dp)
                    ) {
                        // Tight against its own label, generous from what follows: the
                        // name and the mode are one thing.
                        if (dsCard) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    rom.displayName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    stringResource(
                                        if (online) R.string.launch_mode_online
                                        else R.string.launch_mode_session,
                                        // The full label: "GC/Wii" only makes sense squeezed
                                        // into a badge.
                                        rom.console.label
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // A selector rather than a link: it rewrites the card, it does not act.
                        // pourquoi : docs/decisions/lancement-et-navigation.md § The choice of world comes first, not last
                        if (onPlayOnline != null) {
                            // Who you play with is the social axis, cursor included.
                            // pourquoi : docs/decisions/theme-duotone-shelves.md § GAMEPAD FOCUS
                            CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
                                ModeSwitch(
                                    publicMode = publicMode,
                                    enabled = !starting,
                                    onPick = { publicMode = it }
                                )
                            }
                        }

                        // The explanation scrolls, the actions never do.
                        // pourquoi : docs/decisions/lancement-et-navigation.md § What gives way, and in what order
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            steps.forEachIndexed { index, text -> Step(index + 1, text) }
                        }

                        // The actions are one group: tighter among themselves than the
                        // gap that separates them from what they act on.
                        // pourquoi : docs/decisions/lancement-et-navigation.md § The buttons are stacked, and it is a trap avoided
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!online) {
                                CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
                                    PrivacyToggle(
                                        checked = isPrivate,
                                        enabled = !starting,
                                        onChange = { isPrivate = it }
                                    )
                                }
                            }
                            if (ps2Blocked) {
                                Ps2ProfileMissing()
                            } else if (pspBlocked) {
                                PpssppSetupMissing()
                            } else {
                                PrimaryAction(
                                    label = primaryLabel,
                                    starting = starting,
                                    onClick = { starting = true },
                                    modifier = Modifier.fillMaxWidth().focusRequester(firstAction)
                                )
                            }
                            if (!setupBlocked && onJoinWithCode != null && !publicMode) {
                                CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
                                    OutlinedButton(
                                        onClick = sounded(onJoinWithCode),
                                        enabled = !starting,
                                        shape = PillShape,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (dark) Coral.darkBright else Coral.deep
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(52.dp)
                                            .controlRing(PillShape)
                                    ) { Text(stringResource(R.string.lib_join_by_code)) }
                                }
                            }
                        }
                    }
                }
                return@SoftCard
            }

            Column(
                // Tighter when height is scarce: a dp off the padding is one the text keeps.
                modifier = Modifier.fillMaxWidth().padding(if (compact) 16.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
            ) {
                if (onPlayOnline != null) {
                    CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
                        ModeSwitch(
                            publicMode = publicMode,
                            enabled = !starting,
                            onPick = { publicMode = it }
                        )
                    }
                }

                // The explanation scrolls; the two buttons never do.
                // pourquoi : docs/decisions/lancement-et-navigation.md § What gives way, and in what order
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
                ) {
                    // pourquoi : docs/decisions/lancement-et-navigation.md § What gives way, and in what order
                    RomArtwork(rom, size = if (compact) 72.dp else 104.dp)

                    TitleBlock(rom, online)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        steps.forEachIndexed { index, text -> Step(index + 1, text, compact) }
                    }
                }

                if (!online) {
                    CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
                        PrivacyToggle(
                            checked = isPrivate,
                            enabled = !starting,
                            onChange = { isPrivate = it }
                        )
                    }
                }

                if (ps2Blocked) {
                    Ps2ProfileMissing()
                } else if (pspBlocked) {
                    PpssppSetupMissing()
                } else {
                    PrimaryAction(
                        label = primaryLabel,
                        starting = starting,
                        onClick = { starting = true },
                        modifier = Modifier.fillMaxWidth().focusRequester(firstAction)
                    )
                }

                // No session to join in public mode, exactly as for DS online play.
                if (!setupBlocked && onJoinWithCode != null && !publicMode) {
                    CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
                        OutlinedButton(
                            onClick = sounded(onJoinWithCode),
                            enabled = !starting,
                            shape = PillShape,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (dark) Coral.darkBright else Coral.deep
                            ),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                                .controlRing(PillShape)
                        ) { Text(stringResource(R.string.lib_join_by_code)) }
                    }
                }

            }
        }
    }
}

/**
 * Long enough for the press to register, and no longer.
 * pourquoi : docs/decisions/lancement-et-navigation.md § The button keeps its colour while it works
 */
private const val START_PAUSE_MS = 350L

@Composable
private fun TitleBlock(rom: Rom, online: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            rom.displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(
                if (online) R.string.launch_mode_online else R.string.launch_mode_session,
                // The full label: "GC/Wii" only makes sense squeezed into a badge.
                rom.console.label
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // The tile is scanned, this card is read: here there is room for what the mark means.
        // pourquoi : docs/decisions/lancement-et-navigation.md § The compatibility verdict, where the decision is made
        LocalCompatDb.current.ratingFor(rom.compatKeys())?.let { known ->
            CompatNote(known)
        }
    }
}

/**
 * The bead and its meaning in words; the rater's own note is not shown.
 * pourquoi : docs/decisions/lancement-et-navigation.md § The compatibility verdict, where the decision is made
 */
@Composable
private fun CompatNote(entry: CompatEntry) {
    Row(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompatBadge(entry.rating)
        Text(
            compatLabel(entry.rating),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Keeps its colour while it works: a grey button under a spinner reads as refused.
 * pourquoi : docs/decisions/lancement-et-navigation.md § The button keeps its colour while it works
 */

/**
 * The label promises exactly what the coordinator delivers.
 * pourquoi : docs/decisions/lancement-et-navigation.md § "Private session" promises exactly what the coordinator delivers
 */
@Composable
private fun PrivacyToggle(
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .controlRing(PillShape)
            .clip(PillShape)
            .tap(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // The title names the state, not the setting.
            // pourquoi : docs/decisions/lancement-et-navigation.md § "Private session" promises exactly what the coordinator delivers
            Text(
                stringResource(
                    if (checked) R.string.lib_private_session
                    else R.string.lib_open_session
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(
                    if (checked) R.string.lib_private_session_on
                    else R.string.lib_private_session_off
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // The settings' switch: the last Material control left on screen.
        // pourquoi : docs/decisions/reglages-ecran.md § A setting with only two states is a switch
        SwitchFace(checked = checked)
    }
}

/**
 * A selector, not two buttons: nothing fires when it is touched.
 * pourquoi : docs/decisions/lancement-et-navigation.md § The choice of world comes first, not last
 */
@Composable
private fun ModeSwitch(
    publicMode: Boolean,
    enabled: Boolean,
    onPick: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PillShape)
            // A notch, not a tint: the plate's low cut, so the selector sits in the
            // card.
            // pourquoi : docs/decisions/theme-duotone-shelves.md § Hollows become notches
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeSegment(
            label = stringResource(R.string.lib_mode_friends),
            selected = !publicMode,
            enabled = enabled,
            onClick = { onPick(false) },
            modifier = Modifier.weight(1f)
        )
        ModeSegment(
            label = stringResource(R.string.lib_mode_public),
            selected = publicMode,
            enabled = enabled,
            onClick = { onPick(true) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeSegment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The selected half is the plate, the other transparent: two flat fills.
    // pourquoi : docs/decisions/theme-duotone-shelves.md § MATERIAL (replaces Plastic.kt)
    val fill =
        if (selected) softCardFill() else Color.Transparent
    Box(
        modifier = modifier
            .controlRing(PillShape)
            .clip(PillShape)
            .background(fill)
            .then(if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, PillShape) else Modifier)
            .tap(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color =
                if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The prerequisite and where to settle it, nothing else.
 * pourquoi : docs/decisions/lancement-et-navigation.md § What replaces the buttons when a prerequisite is missing
 */
@Composable
private fun Ps2ProfileMissing() {
    Text(
        stringResource(R.string.launch_ps2_profile_missing),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}

/** As [Ps2ProfileMissing]: the prerequisite, where to settle it, nothing else. */
@Composable
private fun PpssppSetupMissing() {
    Text(
        stringResource(R.string.launch_ppsspp_setup_missing),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun PrimaryAction(
    label: String,
    starting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = LocalEmufiiDarkTheme.current
    // The teal axis, filled: the deep cut under white ink on the light theme.
    // pourquoi : docs/decisions/theme-duotone-shelves.md § Game card (dialog)
    val container = if (dark) Teal.darkBright else Teal.deep
    val ink = if (dark) Teal.ink else Color.White
    Button(
        onClick = sounded(onClick),
        enabled = !starting,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = ink,
            disabledContainerColor = container,
            disabledContentColor = ink
        ),
        modifier = modifier.height(52.dp).controlRing(PillShape)

    ) {
        if (starting) {
            // In the button, not replacing it, so nothing jumps while the pause runs.
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            // No maxLines: capping at one clipped "Créer une session" silently.
            // pourquoi : docs/decisions/lancement-et-navigation.md § The buttons are stacked, and it is a trap avoided
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Numbered dots, not bullets: the lines are a sequence.
 * pourquoi : docs/decisions/lancement-et-navigation.md § The button keeps its colour while it works
 */
@Composable
private fun Step(number: Int, text: String, compact: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text,
            // One notch down where height is starved: three steps do not fit above two buttons.
            style = if (compact) MaterialTheme.typography.bodySmall
                    else MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = 0.85f)
        )
    }
}
