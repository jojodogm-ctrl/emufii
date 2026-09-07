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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.Teal

/**
 * [gamepadClick] makes a thing pressable from the pad, [focusRing] makes it obvious
 * which thing that is.
 * pourquoi : docs/decisions/navigation-manette.md § Two jobs, separated on purpose
 */

/**
 * Everything marking the selected cell moves on this one clock.
 * pourquoi : docs/decisions/navigation-manette.md § The cursor never lingers
 */
const val RING_IN_MS = 140

/** The ring leaves at once; do not restore a fade here. */
private const val RING_OUT_MS = 0

/** B is absent: it means back. Public because the library grid recognises exactly these. */
val CONFIRM_KEYS = setOf(Key.ButtonA, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Teal for play and system, coral on the social zones: the cursor's colour names the zone.
 * pourquoi : docs/decisions/theme-duotone-shelves.md § GAMEPAD FOCUS
 */
enum class RingTone { TEAL, CORAL }

val LocalRingTone = compositionLocalOf { RingTone.TEAL }

@Composable
fun ringColor(tone: RingTone = LocalRingTone.current, dark: Boolean = LocalEmufiiDarkTheme.current): Color =
    when (tone) {
        RingTone.TEAL -> if (dark) Teal.darkBright else Teal.bright
        RingTone.CORAL -> if (dark) Coral.darkBright else Coral.bright
    }

/** Sits next to a `clickable`, never replaces it; the shared [interactionSource] keeps the press animation. */
fun Modifier.gamepadClick(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    /** Off by default: `clickable` already focuses, and two focus targets are two d-pad stops for one thing. */
    focusable: Boolean = false,
    onClick: () -> Unit
): Modifier = this
    .onKeyEvent { event ->
        if (!enabled) return@onKeyEvent false
        if (event.type == KeyEventType.KeyUp && event.key in CONFIRM_KEYS) {
            // `tap` covers the finger and the keys Compose knows itself, never `ButtonA`.
            // pourquoi : docs/decisions/sons.md § The sound and the click are one call
            Sfx.click()
            onClick()
            true
        } else {
            // Swallow the matching key-down too, or the platform delivers it onwards and
            // one press reads as two.
            event.type == KeyEventType.KeyDown && event.key in CONFIRM_KEYS
        }
    }
    .then(if (focusable) Modifier.focusable(enabled = enabled, interactionSource = interactionSource) else Modifier)

/**
 * A lit contour and a wide coloured glow, animated in.
 * pourquoi : docs/decisions/navigation-manette.md § Three things at once, and the breath that was taken back
 */
@Composable
fun Modifier.focusRing(
    focused: Boolean,
    shape: Shape,
    color: Color = ringColor(),
    /**
     * Defaults are the tiles' (150 dp wide); small controls pass reduced values.
     * pourquoi : docs/decisions/navigation-manette.md § The ring keeps the same weight everywhere
     */
    width: Dp = 4.dp,
    glowRadius: Dp = 28.dp,
    /**
     * Game tiles pass under the default, where the band would take over the cover art it
     * points at. No effect on [FocusRingStyle.FLAT], whose thickness is [width] alone.
     */
    bandFraction: Float = 0.12f
): Modifier {
    // The cursor's sound lives here and nowhere else: the one point everything carrying
    // the cursor passes through, `controlRing` and grid tiles alike.
    // pourquoi : docs/decisions/sons.md § Hover fires where the cursor is drawn
    var wasFocused by remember { mutableStateOf(focused) }
    if (focused != wasFocused) {
        wasFocused = focused
        if (focused) Sfx.hover()
    }

    // Out faster than in: an even spring leaves two selections lit at once.
    // pourquoi : docs/decisions/navigation-manette.md § The cursor never lingers
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
    // No breath, and do not add one: an animated `shadow` elevation shows through a
    // non-opaque surface and drifts inside the cursor.
    if (FOCUS_RING_STYLE == FocusRingStyle.NEON) {
        return this.neonFocusRing(
            focused = focused,
            shape = shape,
            start = color,
            end = deepCut(color),
            minBand = width,
            bandFraction = bandFraction,
            inMs = RING_IN_MS,
            outMs = RING_OUT_MS
        )
    }
    return this
        // Doubled on purpose: at 14 dp on white plates the colour barely showed.
        .shadow(
            elevation = glowRadius * glow,
            shape = shape,
            // `shadow` defaults to `clip = elevation > 0`, which cut the control to the
            // ring's own shape: the profile avatar's pencil pill sits at the corner of a
            // square box, outside the circle, and half vanished under focus.
            // pourquoi : docs/decisions/navigation-manette.md § The ring surrounds, it does not clip
            clip = false,
            ambientColor = Color.Transparent,
            spotColor = color
        )
        .border(ring, color.copy(alpha = glow), shape)
}


/**
 * [FLAT] is kept on purpose: a 4 dp stroke plus the shadow bent into a halo, holding on
 * every ground with no `BlurMaskFilter`. If the neon costs too much on a scrolling grid
 * or reads badly on light cover art, this constant is the whole switch.
 */
enum class FocusRingStyle { FLAT, NEON }

val FOCUS_RING_STYLE = FocusRingStyle.NEON

/** The ring receives one colour: the axis is recovered here rather than threaded through forty callers. */
private fun deepCut(bright: Color): Color = when (bright) {
    Teal.bright, Teal.darkBright -> Teal.deep
    Coral.bright, Coral.darkBright -> Coral.deep
    // A colour named by a caller: darkened in place rather than snapped to a foreign axis.
    else -> Color(
        red = bright.red * 0.72f,
        green = bright.green * 0.72f,
        blue = bright.blue * 0.72f,
        alpha = bright.alpha
    )
}

/**
 * Filled in by the scaffold, read by [controlRing]; zero when there is no header.
 * pourquoi : docs/decisions/navigation-manette.md § Nothing must stop under the header
 */
val LocalScaffoldBand = compositionLocalOf { 0.dp }

/**
 * The scrolling column a control sits in, filled in by the screen, read by [controlRing];
 * null in a page that does not scroll, and in the library, whose grid scrolls itself.
 * pourquoi : docs/decisions/navigation-manette.md § The outermost control pulls the page to its edge
 */
val LocalEntryScroll = compositionLocalOf<EntryScroll?> { null }

/**
 * A scrolling page and the roll of the controls inside it.
 *
 * Every control carrying a cursor signs in with where it sits in the *content*, which does
 * not change as the page scrolls. Being the first or the last is then read off that roll
 * instead of guessed from a distance: three attempts at guessing failed on real pages,
 * the last of them because the topmost control of Emulators is a button with its card's
 * title and note above it, so it never looked close to the top of anything.
 * pourquoi : docs/decisions/navigation-manette.md § The outermost control pulls the page to its edge
 */
@Stable
class EntryScroll(val state: ScrollState) {
    private val spans = mutableStateMapOf<Any, ClosedFloatingPointRange<Float>>()

    fun signIn(key: Any, top: Float, bottom: Float) {
        spans[key] = top..bottom
    }

    fun signOut(key: Any) {
        spans.remove(key)
    }

    /** A pixel of slack: two controls on one row are level, never equal to the float. */
    private fun leader(pick: (ClosedFloatingPointRange<Float>) -> Float, top: Boolean): Float? =
        spans.values.map(pick).let { if (it.isEmpty()) null else if (top) it.min() else it.max() }

    fun isFirst(key: Any): Boolean {
        val mine = spans[key] ?: return false
        val edge = leader({ it.start }, top = true) ?: return false
        return mine.start <= edge + 1f
    }

    fun isLast(key: Any): Boolean {
        val mine = spans[key] ?: return false
        val edge = leader({ it.endInclusive }, top = false) ?: return false
        return mine.endInclusive >= edge - 1f
    }
}


/**
 * Named apart from [ActionShape] because the ring needs the number.
 * pourquoi : docs/decisions/navigation-manette.md § One radius, named once
 */
val ACTION_CORNER = 18.dp

val ActionShape = RoundedCornerShape(ACTION_CORNER)

/**
 * Reads focus itself, so place it before the `clickable` or `focusable`; after, it sees
 * nothing and stays dark.
 * pourquoi : docs/decisions/navigation-manette.md § The ring reads focus itself, and the order matters
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.controlRing(
    shape: Shape,
    /**
     * Do not shrink these.
     * pourquoi : docs/decisions/navigation-manette.md § The ring keeps the same weight everywhere
     */
    width: Dp = 4.dp,
    glowRadius: Dp = 28.dp,
    bandFraction: Float = 0.12f,
    scrollMargin: Dp = 28.dp,
    /** False to silence the ring while focus is inside it, as in a field being edited. */
    enabled: Boolean = true
): Modifier {
    var focused by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    var widthPx by remember { mutableIntStateOf(0) }
    var topInWindow by remember { mutableFloatStateOf(0f) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val requester = remember { BringIntoViewRequester() }
    val seat = remember { Any() }

    // Compose counts a control under the header as visible, so a top margin below the
    // header's height never reaches the top.
    val top = with(LocalDensity.current) {
        maxOf(scrollMargin, LocalScaffoldBand.current).toPx()
    }
    val bottom = with(LocalDensity.current) { scrollMargin.toPx() }
    val entryScroll = LocalEntryScroll.current

    // Signed out on the way past: a control left on the roll would keep an empty page
    // believing it still had a bottom.
    DisposableEffect(entryScroll, seat) {
        onDispose { entryScroll?.signOut(seat) }
    }

    // Two different jobs behind one effect, and telling them apart is the whole trick.
    //
    // The cursor arriving moves the page once, smoothly. A control growing under the
    // cursor -- a block folding open -- is followed *instantly* instead, frame by frame,
    // so the page travels in step with the growth: animating that, or animating it only
    // until the block became the page's last control and snapping from then on, is what
    // jolted.
    var grownTo by remember { mutableIntStateOf(-1) }
    LaunchedEffect(focused, height, entryScroll) {
        if (!focused) {
            grownTo = -1
            return@LaunchedEffect
        }
        val arriving = grownTo < 0
        val growing = !arriving && height > grownTo
        grownTo = height
        if (!arriving && !growing) return@LaunchedEffect

        runCatching {
            val page = entryScroll
            val scroll = page?.state
            if (page == null || scroll == null || scroll.maxValue == Int.MAX_VALUE) {
                if (arriving) requester.bringIntoView(
                    Rect(0f, -top, widthPx.toFloat(), height + bottom)
                )
                return@runCatching
            }

            val edge = when {
                // Both, on a page barely longer than the screen: the nearer edge is the
                // one being walked towards.
                page.isFirst(seat) && page.isLast(seat) ->
                    if (scroll.value <= scroll.maxValue - scroll.value) 0 else scroll.maxValue
                page.isFirst(seat) -> 0
                page.isLast(seat) -> scroll.maxValue
                else -> null
            }

            if (arriving) {
                if (edge != null) scroll.animateScrollTo(edge)
                else requester.bringIntoView(
                    Rect(0f, -top, widthPx.toFloat(), height + bottom)
                )
                return@runCatching
            }

            // Growing: keep its foot on screen, or ride the page's end if it owns it.
            // Never an animation here -- the growth is the animation.
            val overflow = (topInWindow + height + bottom) - viewportHeight
            val by = when {
                edge == scroll.maxValue -> scroll.maxValue
                overflow > 0f -> (scroll.value + overflow).toInt().coerceAtMost(scroll.maxValue)
                else -> return@runCatching
            }
            scroll.scrollTo(by)
        }
    }

    return this
        // The ring overflows and the last sibling drawn wins; a multi-row grid raises its
        // row too.
        // pourquoi : docs/decisions/navigation-manette.md § The selected control draws in front of its neighbours
        .zIndex(if (focused && enabled) 1f else 0f)
        .bringIntoViewRequester(requester)
        .onSizeChanged { widthPx = it.width; height = it.height }
        .onGloballyPositioned {
            topInWindow = it.positionInWindow().y
            viewportHeight = it.findRootCoordinates().size.height
            // Content coordinates, not window ones: the page scrolls under the control and
            // the window figure moves with it, where this one holds still.
            entryScroll?.let { page ->
                val contentTop = topInWindow + page.state.value
                page.signIn(seat, contentTop, contentTop + it.size.height)
            }
        }
        .onFocusEvent { event -> focused = event.hasFocus }
        .focusRing(
            focused && enabled,
            shape,
            width = width,
            glowRadius = glowRadius,
            bandFraction = bandFraction
        )
}

@Composable
fun MutableInteractionSource.isFocused(): State<Boolean> = collectIsFocusedAsState()
