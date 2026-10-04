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

const val RING_IN_MS = 140

private const val RING_OUT_MS = 0

val CONFIRM_KEYS = setOf(Key.ButtonA, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

enum class RingTone { TEAL, CORAL }

val LocalRingTone = compositionLocalOf { RingTone.TEAL }

@Composable
fun ringColor(tone: RingTone = LocalRingTone.current, dark: Boolean = LocalEmufiiDarkTheme.current): Color =
    when (tone) {
        RingTone.TEAL -> if (dark) Teal.darkBright else Teal.bright
        RingTone.CORAL -> if (dark) Teal.darkBright else Teal.bright
    }

fun Modifier.gamepadClick(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    focusable: Boolean = false,
    onClick: () -> Unit
): Modifier = this
    .onKeyEvent { event ->
        if (!enabled) return@onKeyEvent false
        if (event.type == KeyEventType.KeyUp && event.key in CONFIRM_KEYS) {
            // `tap` covers the finger and the keys Compose knows itself, never `ButtonA`.
            Sfx.click()
            onClick()
            true
        } else {
            // Swallow the matching key-down too, or one press reads as two.
            event.type == KeyEventType.KeyDown && event.key in CONFIRM_KEYS
        }
    }
    .then(if (focusable) Modifier.focusable(enabled = enabled, interactionSource = interactionSource) else Modifier)

@Composable
fun Modifier.focusRing(
    focused: Boolean,
    shape: Shape,
    color: Color = ringColor(RingTone.TEAL),
    width: Dp = 4.dp,
    glowRadius: Dp = 28.dp,
    bandFraction: Float = 0.12f
): Modifier {
    var wasFocused by remember { mutableStateOf(focused) }
    if (focused != wasFocused) {
        wasFocused = focused
        if (focused) Sfx.hover()
    }

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
        .shadow(
            elevation = glowRadius * glow,
            shape = shape,
            // shadow() clips by default, which cut off content outside the shape.
            clip = false,
            ambientColor = Color.Transparent,
            // The theme lifts the 0.19 spot-shadow cap; this glow was tuned under it.
            spotColor = color.copy(alpha = color.alpha * LEGACY_SPOT)
        )
        .border(ring, color.copy(alpha = glow), shape)
}


enum class FocusRingStyle { FLAT, NEON }

val FOCUS_RING_STYLE = FocusRingStyle.NEON

private fun deepCut(bright: Color): Color = when (bright) {
    Teal.bright, Teal.darkBright -> Teal.deep
    Coral.bright, Coral.darkBright -> Coral.deep
    else -> Color(
        red = bright.red * 0.72f,
        green = bright.green * 0.72f,
        blue = bright.blue * 0.72f,
        alpha = bright.alpha
    )
}

val LocalScaffoldBand = compositionLocalOf { 0.dp }

val LocalEntryScroll = compositionLocalOf<EntryScroll?> { null }

@Stable
class EntryScroll(val state: ScrollState) {
    private val spans = mutableStateMapOf<Any, ClosedFloatingPointRange<Float>>()

    fun signIn(key: Any, top: Float, bottom: Float) {
        spans[key] = top..bottom
    }

    fun signOut(key: Any) {
        spans.remove(key)
    }

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


val ACTION_CORNER = 18.dp

val ActionShape = RoundedCornerShape(ACTION_CORNER)

/** Must come before clickable/focusable, or it never sees focus. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.controlRing(
    shape: Shape,
    width: Dp = 4.dp,
    glowRadius: Dp = 28.dp,
    bandFraction: Float = 0.12f,
    scrollMargin: Dp = 28.dp,
    enabled: Boolean = true
): Modifier {
    var focused by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    var widthPx by remember { mutableIntStateOf(0) }
    var topInWindow by remember { mutableFloatStateOf(0f) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val requester = remember { BringIntoViewRequester() }
    val seat = remember { Any() }

    // Compose counts a control under the header as visible.
    val top = with(LocalDensity.current) {
        maxOf(scrollMargin, LocalScaffoldBand.current).toPx()
    }
    val bottom = with(LocalDensity.current) { scrollMargin.toPx() }
    val entryScroll = LocalEntryScroll.current

    DisposableEffect(entryScroll, seat) {
        onDispose { entryScroll?.signOut(seat) }
    }

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
        .zIndex(if (focused && enabled) 1f else 0f)
        .bringIntoViewRequester(requester)
        .onSizeChanged { widthPx = it.width; height = it.height }
        .onGloballyPositioned {
            topInWindow = it.positionInWindow().y
            viewportHeight = it.findRootCoordinates().size.height
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

internal const val LEGACY_SPOT = 0.19f

internal const val LEGACY_AMBIENT = 0.039f
