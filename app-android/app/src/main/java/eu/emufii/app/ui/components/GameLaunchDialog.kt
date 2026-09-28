package eu.emufii.app.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import eu.emufii.app.ui.theme.ErrorLight
import eu.emufii.app.ui.theme.ErrorDark
import eu.emufii.app.compat.CompatRating
import eu.emufii.app.ui.ShadowsFollow
import androidx.compose.ui.graphics.CompositingStrategy
import eu.emufii.app.ui.sounded
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import eu.emufii.app.ui.theme.WarnLight
import eu.emufii.app.ui.theme.WarnDark
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import eu.emufii.app.psp.PpssppIni
import eu.emufii.app.psp.PspServerPick
import eu.emufii.app.psp.PspServers
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import eu.emufii.app.ui.Sfx
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.layout.layout
import eu.emufii.app.ui.rememberAppear
import eu.emufii.app.ui.bloom
import eu.emufii.app.ui.TrailerSpinner
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
    val online = publicMode
    // The DS's online side is Kaeru: the game opens straight on it, unlike the PSP's
    // public ad hoc, which leaves a server to pick inside PPSSPP.
    val kaeru = publicMode && rom.console == Console.DS

    /**
     * A PSP session leans on the per-game INI; the public online mode is not blocked.
     * pourquoi : docs/decisions/lancement-et-navigation.md § What replaces the buttons when a prerequisite is missing
     */
    val ppssppReady = rom.console == Console.PSP && rememberPpssppReady()
    // Online too: without the folder the one-tap route cannot write its server.
    val pspBlocked = rom.console == Console.PSP && !ppssppReady

    // PSP online goes where the players of this game already are; the pick can be changed.
    val pickingServer = publicMode && rom.console == Console.PSP && ppssppReady
    var servers by remember { mutableStateOf<List<PspServerPick>?>(null) }
    var chosenHost by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pickingServer) {
        if (pickingServer && servers == null) {
            val discId = PpssppIni.resolveDiscId(rom.productCode, rom.filename, rom.displayName)
            servers = PspServers.rank(discId, rom.displayName)
        }
    }
    val shownServer = servers?.let { list -> list.firstOrNull { it.server.host == chosenHost } ?: list.firstOrNull() }
    SideEffect { PspServers.chosenHost = shownServer?.server?.host }
    // A game rated broken gets no way in: a session for it would only fail, later and
    // without saying why.
    // pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Library
    // The DS has two verdicts, local wireless and Wi-Fi Connection; the card shows its mode's.
    val listed = LocalCompatDb.current.ratingFor(rom.compatKeys())
    val compat = listed?.let {
        val mode = if (kaeru) it.online else it.wireless
        mode?.let { verdict -> it.copy(rating = verdict) } ?: it
    }
    val incompatible = compat?.rating == CompatRating.BROKEN
    // Broken in every mode (the listed verdict is the better of the two): nothing to switch to.
    val hasModes = onPlayOnline != null && listed?.rating != CompatRating.BROKEN
    val setupBlocked = ps2Blocked || pspBlocked || incompatible

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

    // A touch on the rear panel, then one back on this card, leaves no focus in the card:
    // the next key hands the cursor to the first focusable, the library behind. The card
    // is modal, so whenever the cursor is nowhere inside it, it is put back.
    var cardHasCursor by remember { mutableStateOf(true) }
    LaunchedEffect(cardHasCursor) {
        if (cardHasCursor) return@LaunchedEffect
        delay(40)
        inputMode.requestInputMode(InputMode.Keyboard)
        if (!runCatching { firstAction.requestFocus() }.getOrDefault(false)) {
            runCatching { cardRoot.requestFocus() }
        }
    }

    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "launch-card-entrance"
    )
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
        // Lift 16, the launch card's: it stands over the grid, and the trailer's cards
        // carry no coloured rim, only their shadow.
        // pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Flat plates, dropped shadows
        // The card's shadow fades with the card: a shadow takes only its own layer's opacity.
        ShadowsFollow({ (entrance * 3f).coerceAtMost(1f) * (1f - 0.45f * back.value) }) {
        SoftCard(
            lift = 16.dp,
            modifier = Modifier
                // L and R flip the mode switch from anywhere on the card, like shoulder tabs.
                // Ahead of `focusable()`: when the cursor falls back on the card itself (an
                // incompatible mode has no button), handlers below it never see a key.
                .onPreviewKeyEvent { event ->
                    val side = when (event.key) {
                        Key.ButtonL1 -> false
                        Key.ButtonR1 -> true
                        else -> return@onPreviewKeyEvent false
                    }
                    if (!hasModes || starting) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && publicMode != side) {
                        Sfx.toggle()
                        publicMode = side
                    }
                    true
                }
                // In preview: otherwise the first press only took the cursor off the button.
                // pourquoi : docs/decisions/lancement-et-navigation.md § The cursor has to enter the card, and not leave it again
                .onPreviewKeyEvent { event ->
                    val back = event.key == Key.Back || event.key == Key.ButtonB
                    if (!back || starting) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    true
                }
                .focusRequester(cardRoot)
                .onFocusEvent { rootHasCursor = it.isFocused; cardHasCursor = it.hasFocus }
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
                    // Per draw, or the card's shadow is cut square by the fade's buffer.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    translationX =
                        (if (backEdge == BackEventCompat.EDGE_LEFT) 1f else -1f) *
                            24.dp.toPx() * leaving
                }
                // Here, not at the head of the chain: `drawWithContent` takes the wrapped size.
                
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
                    kaeru -> R.string.lib_play_online
                    // Ready, the PSP goes straight in; otherwise it shows what to set by hand.
                    publicMode -> if (ppssppReady) R.string.lib_play_online else R.string.lib_open_emulator
                    online -> R.string.lib_play_online
                    else -> R.string.lib_create_session
                }
            )


            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(26.dp)
                ) {
                    // The game as an object: the cover, and the verdict stamped under
                    // it. A figure beside its text, so it centres on the column that
                    // sets the card's height instead of being asked to match it.
                    // pourquoi : docs/decisions/lancement-et-navigation.md § What gives way, and in what order
                    Column(
                        modifier = Modifier
                            .width(150.dp)
                            .align(Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RomArtwork(rom, size = 134.dp)
                        // The tile is scanned, this card is read: here there is room for
                        // what the mark means.
                        // pourquoi : docs/decisions/lancement-et-navigation.md § The compatibility verdict, where the decision is made
                        // The DS verdict changes with the mode: it fades across, it does not blink.
                        Crossfade(compat, animationSpec = tween(180), label = "launch-compat") { shown ->
                            shown?.let { known -> CompatNote(known) }
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
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Tight against its own label, generous from what follows: the
                        // name and the mode are one thing.
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

                        // A selector rather than a link: it rewrites the card, it does not act.
                        // Above the verdict: on the DS the verdict depends on the mode, so the
                        // way back must survive an incompatible one.
                        // pourquoi : docs/decisions/lancement-et-navigation.md § The choice of world comes first, not last
                        if (hasModes) {
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

                        // Centred in the room actually left: between the title or the top
                        // of the column and the card's bottom, with no empty column
                        // or spacing slot pushing it down.
                        // pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Incompatible games
                        // Faded and resized rather than swapped: on the DS the verdict follows
                        // the mode switch, and the card rewrote itself in one frame.
                        val actionsSize = Motion.morph<IntSize>()
                        AnimatedContent(
                            targetState = incompatible,
                            transitionSpec = {
                                (fadeIn(tween(180)) togetherWith fadeOut(tween(120)))
                                    .using(SizeTransform(clip = false) { _, _ -> actionsSize })
                            },
                            contentAlignment = Alignment.Center,
                            label = "launch-verdict"
                        ) { shownIncompatible ->
                        if (shownIncompatible) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) { IncompatibleNotice() }
                        } else {

                        // The actions are one group: tighter among themselves than the
                        // gap that separates them from what they act on.
                        // pourquoi : docs/decisions/lancement-et-navigation.md § The buttons are stacked, and it is a trap avoided
                        // Gaps ride inside the animated rows: a spacedBy slot vanished in one frame
                        // once a row finished leaving, and the card snapped the last 10 dp.
                        Column {
                            AnimatedVisibility(
                                visible = !online && !incompatible,
                                enter = expandVertically(Motion.morph()) + fadeIn(Motion.morph()),
                                exit = shrinkVertically(Motion.morph()) + fadeOut(Motion.morph())
                            ) {
                                CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
                                    // Centred between the mode switch and the button: the
                                    // group's gap alone left it hugging the button.
                                    PrivacyToggle(
                                        checked = isPrivate,
                                        enabled = !starting,
                                        onChange = { isPrivate = it },
                                        // Under the title rather than a mode switch, it sat 4 dp low (measured on the Thor).
                                        modifier = Modifier.padding(bottom = if (!hasModes) 22.dp else 18.dp)
                                    )
                                }
                            }
                            AnimatedVisibility(
                                visible = pickingServer,
                                enter = expandVertically(Motion.morph()) + fadeIn(Motion.morph()),
                                exit = shrinkVertically(Motion.morph()) + fadeOut(Motion.morph())
                            ) {
                                ServerPicker(
                                    servers = servers,
                                    shown = shownServer,
                                    enabled = !starting,
                                    onPick = { chosenHost = it },
                                    // Same 18 dp as the column's gap above: 14 left it hugging the button.
                                    modifier = Modifier.padding(bottom = 18.dp)
                                )
                            }
                            if (incompatible) {
                                IncompatibleNotice()
                            } else if (ps2Blocked) {
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
                            AnimatedVisibility(
                                visible = !setupBlocked && onJoinWithCode != null && !publicMode,
                                enter = expandVertically(Motion.morph()) + fadeIn(Motion.morph()),
                                exit = shrinkVertically(Motion.morph()) + fadeOut(Motion.morph())
                            ) {
                                CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
                                    OutlinedButton(
                                        onClick = sounded(onJoinWithCode ?: {}),
                                        enabled = !starting,
                                        shape = PillShape,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (dark) Teal.darkBright else Teal.deep
                                        ),
                                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(52.dp)
                                            .controlRing(PillShape)
                                    ) { Text(stringResource(R.string.lib_join_by_code)) }
                                }
                            }
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
                if (hasModes) {
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

                    TitleBlock(rom, online, compat)
                }

                // One group, spaced from inside, for the same reason as the wide card's.
                val gap = if (compact) 10.dp else 14.dp
                Column(Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = !online && !incompatible,
                    enter = expandVertically(Motion.morph()) + fadeIn(Motion.morph()),
                    exit = shrinkVertically(Motion.morph()) + fadeOut(Motion.morph())
                ) {
                    CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
                        PrivacyToggle(
                            checked = isPrivate,
                            enabled = !starting,
                            onChange = { isPrivate = it },
                            modifier = Modifier.padding(bottom = gap)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = pickingServer,
                    enter = expandVertically(Motion.morph()) + fadeIn(Motion.morph()),
                    exit = shrinkVertically(Motion.morph()) + fadeOut(Motion.morph())
                ) {
                    ServerPicker(
                        servers = servers,
                        shown = shownServer,
                        enabled = !starting,
                        onPick = { chosenHost = it },
                        // The gap above it, so it sits midway between the switch and the button.
                        modifier = Modifier.padding(bottom = gap)
                    )
                }
                if (incompatible) {
                    IncompatibleNotice()
                } else if (ps2Blocked) {
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
                AnimatedVisibility(
                    visible = !setupBlocked && onJoinWithCode != null && !publicMode,
                    enter = expandVertically(Motion.morph()) + fadeIn(Motion.morph()),
                    exit = shrinkVertically(Motion.morph()) + fadeOut(Motion.morph())
                ) {
                    CompositionLocalProvider(LocalRingTone provides RingTone.CORAL) {
                        OutlinedButton(
                            onClick = sounded(onJoinWithCode ?: {}),
                            enabled = !starting,
                            shape = PillShape,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (dark) Teal.darkBright else Teal.deep
                            ),
                            modifier = Modifier.padding(top = gap).fillMaxWidth().height(52.dp)
                                .controlRing(PillShape)
                        ) { Text(stringResource(R.string.lib_join_by_code)) }
                    }
                }
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
private fun TitleBlock(rom: Rom, online: Boolean, compat: CompatEntry?) {
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
        compat?.let { known ->
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
            .tap(enabled = enabled, sound = Sfx::toggle) { onChange(!checked) }
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
    // The plate slides between the halves rather than jumping: the change of world is
    // a move, and the eye follows it to the side it lands on.
    val slide by animateFloatAsState(
        targetValue = if (publicMode) 1f else 0f,
        animationSpec = Motion.morph(),
        label = "mode-plate"
    )
    val plate = softCardFill()
    val edge = MaterialTheme.colorScheme.outline
    // Drawn behind the halves rather than laid out: the incompatible card measures its
    // row intrinsically, and a BoxWithConstraints there crashed the card.
    Box(
        modifier = modifier
            .fillMaxWidth()
            // A notch, not a tint: the plate's low cut, so the selector sits in the
            // card.
            // pourquoi : docs/decisions/theme-duotone-shelves.md § Hollows become notches
            // Shaped, not clipped: a clip cut the halves' cursor ring at the edges.
            .background(MaterialTheme.colorScheme.surfaceVariant, PillShape)
            .padding(4.dp)
            .drawBehind {
                val gap = 4.dp.toPx()
                val half = (size.width - gap) / 2
                val corner = CornerRadius(size.height / 2)
                val left = (half + gap) * slide
                drawRoundRect(plate, Offset(left, 0f), Size(half, size.height), corner)
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    edge,
                    Offset(left + stroke / 2, stroke / 2),
                    Size(half - stroke, size.height - stroke),
                    CornerRadius(size.height / 2 - stroke / 2),
                    style = Stroke(stroke)
                )
            }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
}

@Composable
private fun ModeSegment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The plate is drawn once, under both halves, by the switch; the label only fades.
    val tone by animateColorAsState(
        targetValue =
            if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.morph(),
        label = "mode-label"
    )
    Box(
        modifier = modifier
            .controlRing(PillShape)
            .clip(PillShape)
            .tap(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tone,
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
private fun Ps2ProfileMissing() = SetupNotice(R.string.launch_ps2_profile_missing, R.string.launch_ps2_profile_hint)

/** Red and final, in place of the buttons: there is nothing to do but know it. */
@Composable
private fun IncompatibleNotice() {
    val dark = LocalEmufiiDarkTheme.current
    val red = if (dark) ErrorDark else ErrorLight
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(red)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        CrossIcon(size = 18.dp, color = Color.White)
        Text(
            stringResource(R.string.launch_incompatible),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

/** As [Ps2ProfileMissing]: the prerequisite, where to settle it, nothing else. */
@Composable
private fun PpssppSetupMissing() = SetupNotice(R.string.launch_ppsspp_setup_missing, R.string.launch_ppsspp_setup_hint)

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
    // The trailer's button: pressed, it closes into a round pill holding the spinner, and
    // opens back if the start fails. Width and corners move together, a pill at every
    // width, so the radius never jumps.
    // pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Objects transform, screens do not replace each other
    val shrink by animateFloatAsState(
        targetValue = if (starting) 1f else 0f,
        animationSpec = Motion.morph(),
        label = "launch-shrink"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
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
        contentPadding = PaddingValues(horizontal = 24.dp * (1f - shrink).coerceIn(0f, 1f)),
        modifier = Modifier
            .height(52.dp)
            .layout { measurable, constraints ->
                val full = constraints.maxWidth
                val round = 52.dp.roundToPx()
                val width = (full + (round - full) * shrink).toInt().coerceIn(round, full.coerceAtLeast(round))
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(full, placeable.height) {
                    placeable.place((full - width) / 2, 0)
                }
            }
            .controlRing(PillShape)
    ) {
        if (starting) {
            // In the button, not replacing it, so nothing jumps while the pause runs.
            TrailerSpinner(
                color = ink,
                size = 22.dp,
                stroke = 2.5.dp,
                modifier = Modifier.bloom(rememberAppear()::value)
            )
        } else {
            // No maxLines: capping at one clipped "Créer une session" silently.
            // pourquoi : docs/decisions/lancement-et-navigation.md § The buttons are stacked, and it is a trap avoided
            // The label crossfades when the mode switch rewrites it, and its width follows
            // on the same spring: a Crossfade kept the old width, then snapped to the new.
            val widthSpring = Motion.morph<IntSize>()
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    (fadeIn(tween(180)) togetherWith fadeOut(tween(180)))
                        .using(SizeTransform(clip = false) { _, _ -> widthSpring })
                },
                contentAlignment = Alignment.Center,
                label = "launch-label"
            ) { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = if (shrink > 0.01f) 1 else Int.MAX_VALUE,
                    softWrap = shrink <= 0.01f,
                    modifier = Modifier.bloom(rememberAppear()::value)
                )
            }
        }
    }
    }
}

/**
 * The server row of PSP online: the app's pick, what it is based on, and arrows to
 * step to another when the lobby there turns out empty. One cursor stop: left and
 * right step, A steps forward, touch takes the arrows.
 */
@Composable
private fun ServerPicker(
    servers: List<PspServerPick>?,
    shown: PspServerPick?,
    enabled: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val list = servers.orEmpty()
    val index = list.indexOf(shown).coerceAtLeast(0)
    val canStep = enabled && list.size > 1
    // Remembered so the slide goes the way the press went.
    var direction by remember { mutableIntStateOf(1) }
    fun step(by: Int) {
        if (!canStep) return
        direction = by
        Sfx.click()
        onPick(list[(index + by + list.size) % list.size].server.host)
    }
    val live = shown?.players?.let { it > 0 } == true
    val dot by animateColorAsState(
        if (live) Color(0xFF3DDC84) else MaterialTheme.colorScheme.outline,
        animationSpec = Motion.morph(),
        label = "server-live"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .controlRing(PillShape)
            .clip(PillShape)
            // A light plate under the text, so it lifts off the card.
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .onPreviewKeyEvent { event ->
                val by = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) step(by)
                true
            }
            .clickable(enabled = canStep, interactionSource = null, indication = null) { step(1) }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ServerArrow("‹", canStep) { step(-1) }
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                (slideInHorizontally { it / 3 * direction } + fadeIn(tween(160))) togetherWith
                    (slideOutHorizontally { -it / 3 * direction } + fadeOut(tween(120)))
            },
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f),
            label = "server-shown"
        ) { pick ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    pick?.let { "${it.server.flag} ${it.server.name}".trim() }
                        ?: stringResource(R.string.psp_server_label),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pick != null) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (pick == null) stringResource(R.string.psp_server_finding) else serverLine(pick),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        ServerArrow("›", canStep) { step(1) }
    }
}

/** Touch only: the row is the one cursor stop, the arrows never take it. */
@Composable
private fun ServerArrow(glyph: String, enabled: Boolean, onTap: () -> Unit) {
    val tone by animateColorAsState(
        if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        label = "server-arrow"
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .pointerInput(enabled) { if (enabled) detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = tone)
    }
}

@Composable
private fun serverLine(pick: PspServerPick): String = when (val n = pick.players) {
    null -> stringResource(R.string.psp_server_unknown)
    0 -> stringResource(R.string.psp_server_nobody)
    else -> stringResource(R.string.psp_server_players, n)
}

/**
 * The incompatible notice's shape in amber: something to fix once, not a dead end. The
 * title says what is missing, the hint where to set it.
 */
@Composable
private fun SetupNotice(title: Int, hint: Int) {
    val amber = if (LocalEmufiiDarkTheme.current) WarnDark else WarnLight
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(amber)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text("!", fontSize = 14.sp, fontWeight = FontWeight.Black, color = amber)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}
