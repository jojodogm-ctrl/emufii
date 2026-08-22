package eu.emufii.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.emufii.app.ui.theme.LocalAccent

/**
 * Playing Emufii with the sticks and buttons, not the screen.
 *
 * The target device is a handheld: it has a d-pad, two sticks and face buttons,
 * and reaching across it to tap a tile is the awkward way to use it. Compose
 * already moves focus on d-pad presses and already treats Enter and the d-pad
 * centre as a click, what it does not know is that `BUTTON_A` means the same
 * thing, because that keycode belongs to a game controller and Compose leaves
 * game controllers to the app.
 *
 * So there are two jobs here, and they are separate on purpose: [gamepadClick]
 * makes a thing pressable from the pad, and [focusRing] makes it obvious which
 * thing that is. A control that moves under the focus without showing it is
 * worse than one that cannot be reached at all, the player presses A and
 * something happens somewhere else.
 */

/**
 * Face buttons that mean "do it". B is deliberately absent: it means back.
 *
 * Public because the library grid handles its own keys, a *lazy* grid cannot be
 * navigated by Compose's focus traversal, which cannot aim at what is not
 * composed yet, and it has to recognise exactly the same keys as the rest of the
 * app.
 */
/** How long the ring takes to appear, then to fade out. */
/**
 * How long the cursor takes to arrive.
 *
 * Public because it is not the ring's business alone: everything that marks the
 * selected cell — the ring, its glow, the cell's own growth — has to move on
 * this one clock, or the cursor comes apart into pieces that arrive separately.
 */
const val RING_IN_MS = 140

/**
 * How long the ring takes to leave: not at all.
 *
 * It faded over 70 ms, on the reasoning that an abrupt disappearance would
 * flicker. On a d-pad held down that is not what happens — the cursor is already
 * two cells away while the last one is still lit, and the eye reads the leftover
 * glow as a second selection trailing behind the first. A cursor is a statement
 * about *now*; it has no business lingering anywhere it no longer is.
 *
 * The arrival keeps its animation: that one the eye follows on purpose.
 */
private const val RING_OUT_MS = 0

val CONFIRM_KEYS = setOf(Key.ButtonA, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Makes the element focusable and pressable from a controller.
 *
 * Meant to sit *next to* a `clickable`, not to replace it: the touch path stays
 * exactly as it was, and this adds the keys Compose doesn't cover. Both share
 * the same [interactionSource], so the press animation a tile already has plays
 * for a button press too.
 */
fun Modifier.gamepadClick(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    /**
     * Whether to make the element focusable here. Left off by default because
     * `clickable` already does it, and two focus targets in one chain means two
     * stops on the d-pad for one thing on screen.
     */
    focusable: Boolean = false,
    onClick: () -> Unit
): Modifier = this
    .onKeyEvent { event ->
        if (!enabled) return@onKeyEvent false
        if (event.type == KeyEventType.KeyUp && event.key in CONFIRM_KEYS) {
            onClick()
            true
        } else {
            // Swallow the matching key-down as well, or the platform delivers it
            // onwards and a single press reads as two.
            event.type == KeyEventType.KeyDown && event.key in CONFIRM_KEYS
        }
    }
    .then(if (focusable) Modifier.focusable(enabled = enabled, interactionSource = interactionSource) else Modifier)

/**
 * The cursor: a lit cyan contour that says "this is where you are".
 *
 * Tray cyan is spent here and on the primary action, nowhere else — one colour,
 * one meaning. It replaces the mint green the glass world used, which had to be
 * a third colour precisely because blue was already taken by every button on
 * screen; with the palette down to one accent, the cursor can simply have it.
 *
 * Three things at once, and all three are needed: a contour, a wide coloured
 * glow so the eye finds it across the tray, and a slow breath. The breath is the
 * console-menu tell — a selected object on those screens is never quite still —
 * and it also solves a real problem, that a static ring on a busy piece of box
 * art can be mistaken for part of the artwork.
 *
 * Animated in, because focus on a d-pad moves in jumps and an outline that
 * appears instantly at a distance is hard to follow with the eye.
 */
@Composable
fun Modifier.focusRing(
    focused: Boolean,
    shape: Shape,
    /**
     * The accent in force, not the tray's cyan.
     *
     * The ring is drawn by hand rather than by Material, so it is one of the two
     * places the chosen accent has to be fetched explicitly; everything else
     * reads it off the colour scheme.
     */
    color: Color = LocalAccent.current.bright,
    /**
     * The stroke's thickness and the glow's reach, scaled to what they surround.
     *
     * The defaults are the tiles', which are 150 dp wide: a stroke of 4 and a
     * glow of 28. Applied as they are to a 46 dp button, they give a ring that
     * clearly overflows the button and reads as a badly placed outline rather
     * than as a selection, as seen on the header's back button. Small controls
     * therefore pass reduced values; it is the same ring, at their size.
     */
    width: Dp = 4.dp,
    glowRadius: Dp = 28.dp
): Modifier {
    // It goes out faster than it comes on. The default spring took the same time
    // both ways, and on a cursor jumping from cell to cell that left the previous
    // one's glow trailing under the next: you saw two selections at once. The
    // appearance can take its time, it is what the eye follows; the
    // disappearance has to be over before the gaze arrives.
    val ring by animateDpAsState(
        targetValue = if (focused) width else 0.dp,
        animationSpec = tween(if (focused) RING_IN_MS else RING_OUT_MS),
        label = "focus-ring"
    )
    val glow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(if (focused) RING_IN_MS else RING_OUT_MS),
        label = "focus-glow"
    )
    // No breath. It was tried, on the grounds that a selected object on a
    // console menu is never quite still, and it has been taken back out.
    //
    // The elevation of a `shadow` is not a brightness dial: at every value it
    // recomputes the shape's shadow, and an animated elevation under a surface
    // that is not fully opaque shows *through* the surface — the glow crept
    // inside the cursor, drifted, and left a moving hole in the middle of the
    // very element it was supposed to be pointing at. A cursor whose inside
    // moves is worse than a cursor that does not breathe.
    return this
        // A wide, distinctly coloured drop shadow. At 14 dp on white plates the
        // colour was barely visible: the player had to hunt for where they were,
        // which is exactly what this ring exists to prevent. Doubled, it becomes
        // a glow, and the marker is found out of the corner of the eye.
        .shadow(
            elevation = glowRadius * glow,
            shape = shape,
            ambientColor = Color.Transparent,
            spotColor = color
        )
        .border(ring, color.copy(alpha = glow), shape)
}


/**
 * The height of the current screen's floating header, zero if there is none.
 *
 * Filled in by the scaffold, and read by [controlRing] so as never to let a
 * control come to rest under the title. A `CompositionLocal` rather than a
 * parameter: every control in the app would need it, and none of them has any
 * business knowing it.
 */
val LocalScaffoldBand = compositionLocalOf { 0.dp }

/**
 * The radius of the large action buttons, shared so their ring is too: a radius
 * guessed at every call site ended up no longer coinciding with the button it
 * surrounds.
 */
val ACTION_CORNER = 18.dp

/**
 * The shape of the large action buttons. Its radius is named separately above,
 * because the ring needs the number and not the shape: it traces its own, larger
 * outline, and has to be able to add the gap that separates them.
 */
val ActionShape = RoundedCornerShape(ACTION_CORNER)

/**
 * The standard green ring, the one on every ordinary control.
 *
 * It is exactly [focusRing], the library tiles', at the same bounds and with the
 * control's own shape, not a shape rebuilt from a radius, not a larger frame, no
 * reserve in between. The tiles always rendered correctly; everything else tried
 * here, reserving a few dp and drawing inside them, recomputing an outer radius,
 * repainting the glow by hand, amounted to reinventing what already worked, and
 * each variant missed the edge in a different place.
 *
 * What this modifier adds on top, and what justifies its existence:
 *
 * - It reads focus by itself. `onFocusEvent` sees the focus of the nodes below
 *   it in the chain, hence the control's: no more passing a
 *   `MutableInteractionSource` down to a Material `Button` that does not expose
 *   one. That is what makes it possible to fit out a screen without rewriting
 *   it. To be placed before the `clickable` or the `focusable`, never after:
 *   placed after, it sees nothing and stays dark while the cursor is very much
 *   there.
 * - It brings the control into view with a margin, and with at least the height
 *   of the header, under which the content scrolls.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.controlRing(
    shape: Shape,
    /**
     * The tiles' stroke and glow, unchanged.
     *
     * They were shrunk for a while, on the grounds that a button is smaller than
     * a tile. That was a mistake: this drawing, at these values, is what was
     * approved on the tiles and on the back button, and weakening it gave a dull
     * cursor you have to hunt for. A ring must have the same weight everywhere,
     * otherwise it stops reading as the same object.
     */
    width: Dp = 4.dp,
    glowRadius: Dp = 28.dp,
    scrollMargin: Dp = 28.dp,
    /**
     * False to silence the ring even though focus is inside it, as for a text
     * field being edited, where the cursor has moved within.
     */
    enabled: Boolean = true
): Modifier {
    var focused by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    var widthPx by remember { mutableIntStateOf(0) }
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    // The top margin is at least the height of the header.
    //
    // Content scrolls *under* the floating header, which is not a bar but a
    // layer laid over the top. As far as Compose is concerned, a control slipped
    // underneath is "visible": scrolling therefore stopped as soon as it came
    // level, and going back up to a page's first element never brought back the
    // top of that page. By asking for the whole band, the request overshoots the
    // start of the content and the scroll settles at zero.
    val top = with(LocalDensity.current) {
        maxOf(scrollMargin, LocalScaffoldBand.current).toPx()
    }
    val bottom = with(LocalDensity.current) { scrollMargin.toPx() }

    return this
        .bringIntoViewRequester(requester)
        .onSizeChanged { widthPx = it.width; height = it.height }
        .onFocusEvent { event ->
            focused = event.hasFocus
            if (event.hasFocus) {
                scope.launch {
                    runCatching {
                        requester.bringIntoView(
                            Rect(0f, -top, widthPx.toFloat(), height + bottom)
                        )
                    }
                }
            }
        }
        .focusRing(focused && enabled, shape, width = width, glowRadius = glowRadius)
}

/** Whether this source currently holds focus, for callers that want to read it once. */
@Composable
fun MutableInteractionSource.isFocused(): State<Boolean> = collectIsFocusedAsState()
