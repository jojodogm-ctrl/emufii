package eu.emufii.app.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.focus.focusRequester
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
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.PillShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration

/**
 * What you get when you pick a game: the game itself, what is about to happen,
 * and the one button that starts it.
 *
 * This replaced a bottom sheet that showed a title and two bare buttons. The
 * sheet was wrong twice over. It read as a system menu bolted to the bottom edge
 * - a rectangle anchored to a screen whose whole visual direction is floating,
 * borderless shapes, and it said nothing about what pressing the button would
 * do, which for DS online play is a genuinely different thing from creating a
 * session.
 *
 * A floating card instead, carrying the artwork the player just tapped, so the
 * object they chose is still the object in front of them.
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
     * Open the game straight into its console's *public* multiplayer, with no
     * session and no tunnel, null for a console that has no such thing.
     *
     * Only the PSP has both worlds today. Its ad hoc has a public side, indexed
     * live by PPSSPP itself, and a player who wants it was getting a session
     * code nobody would join and a VPN carrying nothing. Asking which one they
     * meant is the whole point; see `PHASE1_SCOUT_PPSSPP_ONLINE.md`.
     */
    onPlayOnline: (() -> Unit)? = null,
) {
    val dark = LocalEmufiiDarkTheme.current
    var starting by remember { mutableStateOf(false) }

    /**
     * A PS2 session with no network configuration on the memory card cannot be
     * played, whatever the tunnel does: the game's local menu never opens. The
     * card is prepared once, in Settings, and until the player says it is in
     * ARMSX2 there is nothing worth starting here — so the actions are replaced
     * by what to do about it, rather than left to fail twenty minutes later.
     */
    val ps2Blocked = rom.console == Console.PS2 &&
        !Ps2NetworkProfile.isReady(LocalContext.current)

    /**
     * Will the session be hidden from the finder?
     *
     * Public by default, because that is what keeps the finder alive: an app
     * whose every game is invisible has no list left to show, and nobody finds
     * anybody any more. The choice is offered, not imposed.
     */
    var isPrivate by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    // Laid on its side when the screen is. Stacked, this card runs floor to
    // ceiling on a landscape handheld, measured at 100..970 of 1080 px on the
    // Thor, while leaving some 470 dp of width empty on either side. Turning
    // the stack into two columns spends that width instead of rationing height,
    // which is what every `compact` concession below was paying for.
    val wide = configuration.screenWidthDp > configuration.screenHeightDp
    // Still needed in the stacked arrangement, which portrait keeps.
    val compact = !wide && configuration.screenHeightDp < 520

    // Which world the card is currently describing. Starts on "with friends"
    // because that is what Emufii is for; the public side is one tap away and
    // rewrites the card rather than opening a second screen, it is the same
    // game and the same decision, just a different answer.
    var publicMode by remember { mutableStateOf(false) }
    val online = rom.console.backend == Backend.MELONDS_WFC || publicMode

    // A beat between the press and the full-screen wait, so the card
    // acknowledges the tap instead of vanishing under it. Deliberately a fixed
    // pause and not a real measurement: the work it precedes has its own
    // progress screen, and this one is for the eye.
    LaunchedEffect(starting) {
        if (starting) {
            delay(START_PAUSE_MS)
            if (publicMode) onPlayOnline?.invoke() else onPrimary(isPrivate)
        }
    }

    // Dismissable by the system gesture too, which a Dialog gave for free and an
    // overlay has to ask for.
    //
    // Always live, including while starting up: disabling it handed back to the
    // screen underneath, which has nowhere to go from the library, so a B during
    // the launch closed the app. The card swallows the gesture and does nothing:
    // the action is already under way and cancelling here would leave the caller
    // half gone.
    BackHandler { if (!starting) onDismiss() }

    // Flipped from a LaunchedEffect rather than started at 1f: an animation whose
    // initial value already equals its target never runs. Same idiom as the
    // tiles' arrival.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    // The cursor enters the box by its primary button.
    //
    // The grid holds its own index and keeps focus: without this request the box
    // opened with none of its buttons reachable, and the directions went on
    // moving the selection behind it.
    //
    // The request fails while we are in touch mode, and that is correct: Compose
    // only makes a `clickable` focusable in keyboard mode, so a box opened with a
    // finger has no cursor, which is the intended behaviour. Opened with a
    // gamepad, the mode is already keyboard and the request succeeds. That
    // distinction cost us dearly: `adb input tap` opens the box in touch mode,
    // which produces exactly the symptoms of broken focus.
    //
    // Retried a few times because the node does not exist at the first
    // composition, then given up on silently. And `getOrDefault` rather than
    // `isSuccess`: `requestFocus` returns `false` without throwing, so
    // `runCatching` succeeds with `false` and testing `isSuccess` reads as a win
    // on the very first round.
    val firstAction = remember { FocusRequester() }
    /**
     * The card's own root, and the reason it exists.
     *
     * Opened with a finger, the request above fails — a `clickable` is only
     * focusable in keyboard mode — and the card was then left with no claim on
     * the pad at all: the grid behind it still held the cursor and still
     * answered directions, so the box sat on screen, unusable, while the
     * selection moved underneath it. That is the bug, and "the box opened with a
     * finger has no cursor" was the wrong conclusion drawn from it: a handheld
     * is touched *and* held, and the very next thing the player does after
     * tapping a tile is reach for the stick.
     *
     * A plain `focusable()` node can take focus in touch mode where a
     * `clickable` cannot. So the card claims the keys either way: the buttons
     * take the cursor when the mode allows it, and otherwise the root does, with
     * nothing shown — and the first direction that arrives hands the cursor to
     * the primary button instead of moving the grid.
     */
    val cardRoot = remember { FocusRequester() }
    var rootHasCursor by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Retried a few times because the node does not exist at the first
        // composition. `getOrDefault` rather than `isSuccess`: `requestFocus`
        // returns `false` without throwing, so `runCatching` succeeds with
        // `false` and testing `isSuccess` reads as a win on the very first
        // round.
        repeat(10) {
            if (runCatching { firstAction.requestFocus() }.getOrDefault(false)) {
                return@LaunchedEffect
            }
            delay(40)
        }
        // Touch mode: the buttons refused, so the card itself takes the keys.
        runCatching { cardRoot.requestFocus() }
    }

    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "launch-card-entrance"
    )
    val steps = if (publicMode) {
        // The PSP is the only console with a public side now: the PS2's was set
        // aside on 2026-08-19 (see `docs/PS2_ONLINE_MIS_DE_COTE.md`), so this
        // branch no longer has two cases to tell apart.
        //
        // Not the DS's steps: the DS dials a revival server on its own, while the
        // PSP player has two settings to pick in PPSSPP first. Saying "we handle
        // it" here would be the lie the first scout already warned about.
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
            // The tray dims, it does not frost. A blur of the grid behind was the
            // glass world's move; here the card is a plate lifted off the tray,
            // and what sits it there is the tray going dark under it, the way a
            // console dims its home screen when a title card comes up. Dimming
            // also keeps the box art behind honest — blurring it turned six
            // covers into one coloured smear.
            .background(
                Color(0xFF060A12).copy(
                    alpha = (if (dark) 0.74f else 0.62f) * entrance
                )
            )
            // Tapping the backdrop closes, the way tapping outside a dialog did.
            // No indication: a ripple across the whole screen would be absurd.
            // The backdrop swallows taps, it is not a cursor stop: without this
            // the traversal halted on it, at a node with no ring and no visible
            // effect.
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !starting,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        SoftCard(
            modifier = Modifier
                .focusRequester(cardRoot)
                .onFocusEvent { rootHasCursor = it.isFocused }
                // The root holds the keys, but never the cursor's look: the
                // first direction that arrives while it does is spent handing
                // the cursor to the primary button, and swallowed, so it never
                // reaches the grid behind.
                .onPreviewKeyEvent { event ->
                    if (!rootHasCursor || event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    runCatching { firstAction.requestFocus() }.getOrDefault(false)
                }
                .focusable()
                // The cursor does not leave the card. This is a modal box: the
                // grid is still there behind it, focusable, and an upward
                // direction moved the selection back into it, the box stayed open
                // on top but no key reached it any more.
                //
                // `exit` refuses the crossing in every direction, where blocking
                // one specific key would only have covered one edge. Not to be
                // confused with `canFocus = false`, which on the contrary
                // disables the whole subtree, the mistake next door.
                .focusGroup()
                .focusProperties { onExit = { cancelFocusChange() } }
                // B closes, and it does so here rather than relying on the
                // `BackHandler` alone: measured, the first press did not reach
                // it, it merely took the cursor off the button, and a second one
                // was needed to close. Seen in preview, hence before anything
                // else gets a chance to use it.
                .onPreviewKeyEvent { event ->
                    val back = event.key == Key.Back || event.key == Key.ButtonB
                    if (!back || starting) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) onDismiss()
                    true
                }
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = if (wide) 648.dp else 360.dp)
                // Bounded by the screen, not by a number: in landscape this
                // device has about 415 dp of height, and a game whose title
                // wraps onto two lines pushed the card past the bottom edge.
                // The buttons were still there, drawn off-screen, so the last
                // one showed as a bar with no text in it.
                .heightIn(max = (configuration.screenHeightDp - 32).dp)
                // Arrives from slightly under its final size, like the tiles.
                .scale(0.92f + 0.08f * entrance)
                .alpha(entrance)
                // Swallows taps so a press inside the card doesn't reach the
                // backdrop's dismiss.
                // No `focusProperties { canFocus = false }` here: placed on the
                // card, it disables its whole subtree, buttons included, which
                // then became unreachable. This node only swallows taps; it does
                // not show up in the traversal because no direction stops on
                // it.
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

            if (wide) {
                // Two columns: the object on the left, what happens to it on the
                // right. The artwork keeps its full size and the steps keep their
                // reading size, because the room they need is taken from the
                // width that was going spare rather than from each other.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(26.dp)
                ) {
                    // Centred vertically, and it only shows when there is
                    // something to centre. The right column decides the row's
                    // height: on a talkative card, the PSP with its
                    // instructions, the cover and the title stayed stuck at the
                    // top with a large gap underneath. When both columns are the
                    // same height, which is the ordinary case, centring moves
                    // nothing: there is no rule to add to tell the two cases
                    // apart, the geometry handles it.
                    Column(
                        modifier = Modifier
                            .width(186.dp)
                            .align(Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        RomArtwork(rom, size = 120.dp)
                        TitleBlock(rom, online)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            // Centred as well, for the opposite reason to the
                            // left column: on a short card, the DS with three
                            // steps and a single button, this one was shorter
                            // than the cover, and it stayed hooked at the top
                            // with the gap underneath.
                            .align(Alignment.CenterVertically),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // The choice of world comes first, not last.
                        //
                        // It was a text link under the buttons, and that did not
                        // work: a blue sentence placed after two pills reads as a
                        // third action of the same family, when it does nothing,
                        // it rewrites the card. On the PSP, the only console to
                        // offer it, you ended up with three things to weigh under
                        // the steps.
                        //
                        // At the top and as a selector, it says what it is: the
                        // question the steps below are already answering.
                        if (onPlayOnline != null) {
                            ModeSwitch(
                                publicMode = publicMode,
                                enabled = !starting,
                                onPick = { publicMode = it }
                            )
                        }
                        // Yields first, and yields alone. Without the weight this
                        // column was measured at whatever height it wanted, and
                        // the actions below it were laid out past the card's
                        // lower edge and clipped away, the primary button simply
                        // stopped existing on any card whose steps ran long.
                        // Two columns bought room; they did not buy infinite room,
                        // so the rule the stacked card already had still holds:
                        // the explanation scrolls, the actions never do.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            steps.forEachIndexed { index, text -> Step(index + 1, text) }
                        }

                        // Stacked, at the full width of the right column.
                        //
                        // Side by side was tried first and is a trap: two pills
                        // sharing ~400 dp cannot hold these labels on one line,
                        // and the failure is silent, Text clips by default, so
                        // "Créer une session" was drawn as "Créer une" with no
                        // ellipsis to admit it. Widening one half only moves the
                        // clipping to the other, and no split survives being
                        // translated. The height these two cost is the one thing
                        // the two-column card has to spare.
                        // Above the button, not among the steps that scroll:
                        // this is a decision taken while pressing, and a decision
                        // you cannot see is not on offer. Absent everywhere no
                        // session is created: the DS dials its revival server on
                        // its own, and the PSP's public ad hoc is picked inside
                        // PPSSPP. Hiding from the finder something that never
                        // appears there has nothing to offer.
                        if (!online) {
                            PrivacyToggle(
                                checked = isPrivate,
                                enabled = !starting,
                                onChange = { isPrivate = it }
                            )
                        }
                        if (ps2Blocked) Ps2ProfileMissing() else
                        PrimaryAction(
                            label = primaryLabel,
                            starting = starting,
                            onClick = { starting = true },
                            modifier = Modifier.fillMaxWidth().focusRequester(firstAction)
                        )
                        if (!ps2Blocked && onJoinWithCode != null && !publicMode) {
                            OutlinedButton(
                                onClick = onJoinWithCode,
                                enabled = !starting,
                                shape = PillShape,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                                    .controlRing(PillShape)
                            ) { Text(stringResource(R.string.lib_join_by_code)) }
                        }

                    }
                }
                return@SoftCard
            }

            Column(
                // Tighter when height is the scarce resource. Every dp taken off
                // the padding is a dp the explanation gets to keep, and the
                // explanation is the only part of this card that says anything.
                modifier = Modifier.fillMaxWidth().padding(if (compact) 16.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
            ) {
                // As in two columns: the choice of world opens the card, it does
                // not conclude it.
                if (onPlayOnline != null) {
                    ModeSwitch(
                        publicMode = publicMode,
                        enabled = !starting,
                        onPick = { publicMode = it }
                    )
                }

                // What can be given up when there isn't room: the artwork, the
                // title and the explanation scroll; the two buttons never do.
                // An action the player cannot see is worse than one they have to
                // scroll to reach, and on this card the actions *are* the card.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
                ) {
                    // Smaller when the card is height-constrained, which on a
                    // landscape handheld it always is. The steps are the part
                    // that teaches, in public mode they are the whole
                    // instruction, and a full-size cover pushed them out of the
                    // scroll area entirely, leaving a card that showed a title
                    // and three buttons and explained nothing. The artwork is
                    // decoration; it yields first.
                    RomArtwork(rom, size = if (compact) 72.dp else 104.dp)

                    TitleBlock(rom, online)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        steps.forEachIndexed { index, text -> Step(index + 1, text, compact) }
                    }
                }

                // Same reason as in two columns: no session, nothing to hide.
                if (!online) {
                    PrivacyToggle(
                        checked = isPrivate,
                        enabled = !starting,
                        onChange = { isPrivate = it }
                    )
                }

                if (ps2Blocked) Ps2ProfileMissing() else
                PrimaryAction(
                    label = primaryLabel,
                    starting = starting,
                    onClick = { starting = true },
                    modifier = Modifier.fillMaxWidth().focusRequester(firstAction)
                )

                // Hidden in public mode: there is no session to join, exactly as
                // for DS online play.
                if (!ps2Blocked && onJoinWithCode != null && !publicMode) {
                    OutlinedButton(
                        onClick = onJoinWithCode,
                        enabled = !starting,
                        shape = PillShape,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                            .controlRing(PillShape)
                    ) { Text(stringResource(R.string.lib_join_by_code)) }
                }

            }
        }
    }
}

/**
 * How long the card holds before handing over.
 *
 * Long enough for the press to register on the button it was aimed at, and no
 * longer. It used to be two full seconds, spent covering a wait the player had
 * no other sign of; the screen that follows names its own progress now, so the
 * padding has nothing left to hide and is pure delay.
 */
private const val START_PAUSE_MS = 350L

/** The title and the one line that says what pressing the button will do. */
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
                // The full label, not the tile badge's short one: "GC/Wii" is an
                // abbreviation that only makes sense squeezed into a corner of a
                // square.
                rom.console.label
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // What the database knows, where the decision is actually taken.
        //
        // The tile already carries the bead, but the tile is scanned and this
        // card is read: here there is room to say what the mark means, and this
        // is the last moment before a player spends a session code and someone
        // else's evening on a game that does not work. Nothing at all for a
        // game nobody has rated, exactly as on the tile.
        LocalCompatDb.current.ratingFor(rom.compatKeys())?.let { known ->
            CompatNote(known)
        }
    }
}

/**
 * The verdict under the title: the bead, and its meaning in words.
 *
 * The bead is repeated rather than replaced by text alone so the mark the player
 * saw on the tile is the same object here, and the sentence teaches what the
 * colour means for the next time they meet it in the grid.
 *
 * The rater's own note is deliberately not shown. It was, for one release day,
 * and it read as a second voice arguing with the verdict right where the player
 * is deciding: the four words of the verdict are the whole message this card
 * needs to carry. The field stays in the database and in the tool, for wherever
 * there is room to argue.
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
 * The button the card exists to get pressed.
 *
 * Keeps its colour while it waits. Material greys a disabled button out, which
 * says "you can't press this", but the reason it can't be pressed is that it is
 * already working, and a grey button under a spinner reads as a fault.
 */

/**
 * "Private session": the switch, and the sentence that says what it does.
 *
 * The label promises exactly what the coordinator delivers, the session leaves
 * the finder, and nothing more. Writing "nobody can get in" would be false: the
 * code protects entry to a private session exactly as it does a public one, and
 * someone who believes otherwise will share their code more lightly.
 *
 * The whole row is clickable: aiming at a switch with a thumb, on a card already
 * tight for height, is the kind of target you miss.
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
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.lib_private_session),
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
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/**
 * With friends, or online: the two worlds of one game, side by side.
 *
 * A selector and not two buttons, because this is not an action: nothing sets
 * off when it is touched, the card rewrites itself. Two pills of equal weight
 * under the buttons gave exactly the opposite impression, a third possible
 * departure, and that is what cluttered the PSP card, the only console to offer
 * both.
 *
 * Each half carries the ring on its own side: with a gamepad you traverse a
 * choice, not a block.
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
            // A gutter distinctly darker than the card. At 55 % of
            // `surfaceVariant` it was barely distinguishable from it, and with no
            // visible gutter the selected half has nothing to stand out against:
            // the two labels read as two words dropped there.
            .background(
                if (LocalEmufiiDarkTheme.current) Color(0x1FFFFFFF) else Color(0x14000000)
            )
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
    // The selected half is solid, the other transparent: the selection reads
    // without reading either label. The card's background rather than an accent
    // tint, this app's colour comes from the content, not from the chrome.
    val fill =
        if (selected) softCardFill() else Color.Transparent
    Box(
        modifier = modifier
            .controlRing(PillShape)
            // The shadow is what makes the selection. Two flat tints, however
            // contrasted, read as two colours; a pill lifting off the gutter
            // reads as a choice taken. It is the same gesture as the app's cards,
            // more discreet.
            .then(
                if (selected) Modifier.shadow(3.dp, PillShape, spotColor = Color.Black)
                else Modifier
            )
            .clip(PillShape)
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
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
 * What replaces the launch buttons when the PS2 profile is missing.
 *
 * States the prerequisite and where to settle it, and nothing else: there is no
 * shortcut into Settings from here because this card lives inside the library's
 * own tree, and wiring navigation through it for one message would cost more
 * than the sentence saves.
 */
@Composable
private fun Ps2ProfileMissing() {
    Text(
        stringResource(R.string.launch_ps2_profile_missing),
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
    Button(
        onClick = onClick,
        enabled = !starting,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = modifier.height(52.dp).controlRing(PillShape)

    ) {
        if (starting) {
            // In the button rather than replacing it: the card keeps its size,
            // so nothing jumps while the pause runs.
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            // No maxLines. Capping it at one clipped "Créer une session" to
            // "Créer une" on the device, silently, because Text clips rather
            // than ellipsises by default. A label that wraps is readable; a
            // label cut mid-phrase looks like a broken build.
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * One line of the walkthrough.
 *
 * Numbered dots rather than bullets, because the three lines are a sequence and
 * a bullet would not say so. Kept to one short line each, this card exists to
 * be glanced at on the way to the button, not read.
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
            // One notch down where the card is height-starved. Three steps at
            // bodyMedium do not fit above two pinned buttons on a landscape
            // handheld, and a step cut through the middle of its own line reads
            // as a broken layout rather than as something to scroll.
            style = if (compact) MaterialTheme.typography.bodySmall
                    else MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = 0.85f)
        )
    }
}
