package eu.emufii.app.ui

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.first
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.CompositionLocalProvider
import eu.emufii.app.ui.theme.LocalShadowFade
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Transition
import androidx.compose.animation.EnterExitState
import androidx.compose.ui.graphics.CompositingStrategy
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

/*
 * The trailer's recipes, one function each so no screen copies one by hand.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Recipes
 */

/**
 * Transparent, blurred and slightly small to sharp and whole. [progress] is read inside
 * the layer, so the animation redraws without recomposing. The blur exists only while
 * the thing is still arriving: above 0.995 the render effect is dropped, or the layer
 * would be blurred at zero radius for its whole life.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Blur is paid only in flight
 */
fun Modifier.bloom(
    progress: () -> Float,
    blur: Dp = 12.dp,
    rise: Dp = 0.dp,
    from: Float = 0.965f,
): Modifier = this.graphicsLayer {
    val a = progress().coerceIn(0f, 1.2f)
    alpha = a.coerceAtMost(1f)
    val s = from + (1f - from) * a
    scaleX = s
    scaleY = s
    translationY = (1f - a) * rise.toPx()
    val r = (1f - a).coerceAtLeast(0f) * blur.toPx()
    val blurring = a < 0.995f && r > 0.5f
    renderEffect = if (blurring) BlurEffect(r, r, TileMode.Decal) else null
    // Faded per draw, not through an offscreen buffer: a buffer is the layer's own size, and
    // everything spilling out of it, the plate's shadow first, was cut square until the
    // fade ended. Only a blur needs the buffer.
    compositingStrategy = if (blurring) CompositingStrategy.Offscreen else CompositingStrategy.ModulateAlpha
}

/**
 * An `AnimatedContent` child's fade, applied per draw. Compose's own `fadeIn` goes through
 * a buffer the child's size, which cuts the child's shadow square for the whole fade; pair
 * this with `EnterTransition.None` and `ExitTransition.None`, and the transition still
 * waits for it before removing the child.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Blur is paid only in flight
 */
@Composable
fun Modifier.fadeInPlace(
    transition: Transition<EnterExitState>,
    enter: FiniteAnimationSpec<Float>,
    exit: FiniteAnimationSpec<Float>,
): Modifier {
    val a by transition.animateFloat(
        transitionSpec = { if (targetState == EnterExitState.Visible) enter else exit },
        label = "fade-in-place"
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return this.graphicsLayer {
        alpha = a
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** [fadeInPlace] as a container, so the shadows inside fade with it. */
@Composable
fun FadeInPlace(
    transition: Transition<EnterExitState>,
    enter: FiniteAnimationSpec<Float>,
    exit: FiniteAnimationSpec<Float>,
    content: @Composable () -> Unit,
) {
    val a by transition.animateFloat(
        transitionSpec = { if (targetState == EnterExitState.Visible) enter else exit },
        label = "fade-in-place"
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    ShadowsFollow({ a }) {
        Box(
            Modifier.graphicsLayer {
                alpha = a
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
        ) { content() }
    }
}

/**
 * When the screen holding this was first composed. A row composed later than its own
 * cascade would have played, scrolled in or recomposed, arrives whole: the cascade is
 * the screen opening, not every row that ever enters the viewport.
 */
val LocalScreenOpenedAt = compositionLocalOf { 0L }

/** Rows past the eighth arrive with the eighth: a long list must not take seconds to fill. */
private const val CASCADE_CAP = 7

private const val CASCADE_STEP_MS = 60L

/** Past this, a row joining the screen is not part of its opening any more. */
private const val CASCADE_WINDOW_MS = 700L

/**
 * One child of a card or list rising into place, [CASCADE_STEP_MS] after the one before;
 * with [pop], scaling up from nothing instead, for the one just added. A container rather
 * than a modifier: the shadows inside have to fade with it, see [LocalShadowFade]. No blur:
 * a list of twelve blurred rows is twelve offscreen layers at once.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Blur is paid only in flight
 */
@Composable
fun Cascade(
    index: Int,
    modifier: Modifier = Modifier,
    pop: Boolean = false,
    content: @Composable () -> Unit,
) {
    val on = rememberAnimationsEnabled()
    val openedAt = LocalScreenOpenedAt.current
    val late = remember { SystemClock.uptimeMillis() - openedAt > CASCADE_WINDOW_MS }
    val still = !on || (late && !pop)
    val a = remember { Animatable(if (still) 1f else 0f) }
    val scale = remember { Animatable(if (still || !pop) 1f else 0f) }
    val rise = Motion.rise<Float>()
    val popSpec = Motion.pop<Float>()
    val enter = Motion.enter<Float>()
    LaunchedEffect(Unit) {
        if (a.value < 1f) {
            if (!pop) delay(index.coerceIn(0, CASCADE_CAP) * CASCADE_STEP_MS)
            coroutineScope {
                launch { a.animateTo(1f, if (pop) enter else rise) }
                if (pop) launch { scale.animateTo(1f, popSpec) }
            }
        }
    }
    ShadowsFollow({ a.value }) {
        Box(
            modifier
                .bloom({ a.value }, blur = 0.dp, rise = if (pop) 0.dp else 12.dp, from = 0.985f)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
        ) { content() }
    }
}

/** The shadows inside [content] fade with [progress], on top of any fade already above. */
@Composable
fun ShadowsFollow(progress: () -> Float, content: @Composable () -> Unit) {
    val outer = LocalShadowFade.current
    CompositionLocalProvider(LocalShadowFade provides { progress() * outer() }) { content() }
}

/**
 * Appears with [bloom] when [visible] turns true, leaves the same way, faster. Starts at
 * zero the first time, so a component composed already visible still blooms in.
 */
@Composable
fun rememberAppear(visible: Boolean = true, delayMs: Long = 0L): State<Float> {
    val on = rememberAnimationsEnabled()
    val a = remember { Animatable(if (on) 0f else if (visible) 1f else 0f) }
    val enter = Motion.enter<Float>()
    val exit = Motion.exit<Float>()
    LaunchedEffect(visible) {
        if (visible) {
            if (delayMs > 0) delay(delayMs)
            a.animateTo(1f, enter)
        } else {
            a.animateTo(0f, exit)
        }
    }
    return a.asState()
}

/** True when composed as part of its screen opening, rather than arriving on it later. */
@Composable
fun rememberPartOfOpening(): Boolean {
    val openedAt = LocalScreenOpenedAt.current
    return remember { SystemClock.uptimeMillis() - openedAt < SETTLED_WINDOW_MS }
}

/**
 * Scale from nothing with [Motion.pop], so it passes its size once and settles, opacity
 * with [Motion.enter]. For an avatar joining, a status dot, a badge, a friend's tick.
 */
@Composable
fun Modifier.popIn(visible: Boolean = true, settled: Boolean = false): Modifier {
    val on = rememberAnimationsEnabled()
    val start = if (!on || settled) (if (visible) 1f else 0f) else 0f
    val scale = remember { Animatable(start) }
    val alpha = remember { Animatable(start) }
    val pop = Motion.pop<Float>()
    val enter = Motion.enter<Float>()
    val exit = Motion.exit<Float>()
    LaunchedEffect(visible) {
        val target = if (visible) 1f else 0f
        coroutineScope {
            launch { scale.animateTo(target, if (visible) pop else exit) }
            launch { alpha.animateTo(target, if (visible) enter else exit) }
        }
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value.coerceIn(0f, 1f)
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/**
 * An indicator that stretches as it travels: the edge in the direction of travel leaves
 * on a stiff spring, the other follows on a soft one, so it lengthens and closes up again
 * instead of sliding as a block. [start] and [end] are the two edges' offsets from the
 * track's origin, [end] already including the width.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § The elastic indicator
 */
class ElasticSpan(private val lead: State<Dp>, private val trail: State<Dp>, private val width: Dp) {
    val start: Dp get() = minOf(lead.value, trail.value)
    val end: Dp get() = maxOf(lead.value, trail.value) + width
    val length: Dp get() = end - start
}

@Composable
fun rememberElastic(target: Dp, width: Dp): ElasticSpan {
    val on = rememberAnimationsEnabled()
    val lead = animateDpAsState(
        target,
        if (on) spring(0.80f, 1024f) else snap(),
        label = "elastic-lead"
    )
    val trail = animateDpAsState(
        target,
        if (on) spring(0.80f, 256f) else snap(),
        label = "elastic-trail"
    )
    return remember(width) { ElasticSpan(lead, trail, width) }
}

/**
 * An arc turning at 420°/s whose length breathes, 60% ± 40% of its base at 5 rad/s. Its
 * own frame clock, not the app's slow one: it lives only on waiting screens, where it is
 * the one thing that moves, and at twelve steps a second it stutters.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Recipes
 */
@Composable
fun TrailerSpinner(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    stroke: Dp = 4.dp,
    /** Every repaint is the whole window's; a spinner left on for minutes asks for fewer. */
    fps: Int = 60,
) {
    val on = rememberAnimationsEnabled()
    var t by remember { mutableFloatStateOf(0.35f) }
    if (on) {
        LaunchedEffect(fps) {
            val start = withFrameMillis { it }
            val step = 1000L / fps
            var last = 0L
            while (true) withFrameMillis {
                if (it - last >= step) {
                    last = it
                    t = (it - start) / 1000f
                }
            }
        }
    }
    Canvas(modifier.size(size)) {
        val w = stroke.toPx()
        val sweep = SPINNER_BASE_SWEEP * (0.6f + 0.4f * sin(t * 5f))
        drawArc(
            color = color,
            startAngle = (t * 420f) % 360f - 90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(w / 2, w / 2),
            size = Size(this.size.width - w, this.size.height - w),
            style = Stroke(width = w, cap = StrokeCap.Round)
        )
    }
}

private const val SPINNER_BASE_SWEEP = 200f

/**
 * The circle pops, and 30 ms later the tick draws itself along its own path. The sound
 * goes with the tick, not the circle: it is the tick that says "done".
 */
@Composable
fun DrawnCheck(
    done: Boolean,
    disc: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    sound: Boolean = true,
) {
    val on = rememberAnimationsEnabled()
    // Already done when the screen opened: it was done before, and is not news. Only a
    // tick that turns true in front of the player pops, draws and sounds.
    val openedAt = LocalScreenOpenedAt.current
    val settled = remember { done && SystemClock.uptimeMillis() - openedAt < SETTLED_WINDOW_MS }
    val draw = remember { Animatable(if (done && (!on || settled)) 1f else 0f) }
    val spec = Motion.draw<Float>()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var shown by remember { mutableStateOf(done && (!on || settled)) }
    LaunchedEffect(done) {
        if (done) {
            if (draw.value < 1f) {
                awaitSeen(lifecycle)
                shown = true
                delay(CHECK_AFTER_POP_MS)
                if (sound) Sfx.confirm()
                draw.animateTo(1f, spec)
            }
        } else {
            shown = false
            draw.snapTo(0f)
        }
    }
    Box(modifier.size(size).popIn(shown, settled), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawCircle(disc)
            val path = Path().apply {
                moveTo(this@Canvas.size.width * 0.28f, this@Canvas.size.height * 0.52f)
                lineTo(this@Canvas.size.width * 0.44f, this@Canvas.size.height * 0.67f)
                lineTo(this@Canvas.size.width * 0.73f, this@Canvas.size.height * 0.36f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val part = Path()
            measure.getSegment(0f, measure.length * draw.value.coerceIn(0f, 1f), part, true)
            drawPath(
                part,
                color = ink,
                style = Stroke(
                    width = this.size.width * 0.11f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

private const val CHECK_AFTER_POP_MS = 30L

/** Composed this soon after its screen: part of the screen, not an event on it. */
private const val SETTLED_WINDOW_MS = 400L

/**
 * A code writing itself: one character every 115 ms, each dropping 28 dp from a blur of
 * 8 dp onto [Motion.snapIn], with the tick sound. Plays once per code; a code shown again
 * after a recomposition stays written.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § The session code writes itself
 */
@Composable
fun RevealCode(
    code: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    stepMs: Long = CODE_STEP_MS,
    sound: Boolean = true,
) {
    val on = rememberAnimationsEnabled()
    val once = "$code:$sound"
    var shown by remember(code) {
        mutableIntStateOf(if (on && once !in revealed) 0 else code.length)
    }
    val written = remember(code) { shown }
    LaunchedEffect(code) {
        revealed += once
        while (shown < code.length) {
            delay(if (shown == 0) 0L else stepMs)
            if (sound && !code[shown].isWhitespace()) Sfx.tick()
            shown++
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        code.forEachIndexed { i, c ->
            key(code, i) { CodeGlyph(c.toString(), i < shown, style, color, instant = i < written) }
        }
    }
}

/**
 * One character of a code, landing with [Motion.snapIn]: what the reveal writes and what
 * a keypad press drops into its slot. Starts in the air the first time it is composed.
 */
@Composable
fun CodeGlyph(
    text: String,
    landed: Boolean = true,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    /** Already in place when composed: a code shown again, not being written. */
    instant: Boolean = false,
) {
    val on = rememberAnimationsEnabled()
    val a = remember { Animatable(if (!on || (instant && landed)) 1f else 0f) }
    val spec = Motion.snapIn<Float>()
    LaunchedEffect(landed) { a.animateTo(if (landed) 1f else 0f, spec) }
    Text(
        text,
        style = style,
        color = color,
        modifier = Modifier.graphicsLayer {
            val v = a.value
            alpha = v.coerceIn(0f, 1f)
            translationY = -(1f - v) * 28.dp.toPx()
            val r = (1f - v).coerceAtLeast(0f) * 8.dp.toPx()
            val blurring = v < 0.995f && r > 0.5f
            renderEffect = if (blurring) BlurEffect(r, r, TileMode.Decal) else null
            compositingStrategy =
                if (blurring) CompositingStrategy.Offscreen else CompositingStrategy.ModulateAlpha
        }
    )
}

const val CODE_STEP_MS = 115L

/** Codes already written this process: coming back to a session does not rewrite it. */
private val revealed = mutableSetOf<String>()

/**
 * Waits until the app is in front, then a beat. A step completes while the player is in
 * the emulator: played at once, its tick drew itself in the background and the player came
 * back to a finished button, never seeing it.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § The auto-setup button, host and guest
 */
suspend fun awaitSeen(lifecycle: Lifecycle) {
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
    delay(SEEN_BEAT_MS)
}

/** Long enough for the screen to be looked at again after coming back. */
private const val SEEN_BEAT_MS = 350L
