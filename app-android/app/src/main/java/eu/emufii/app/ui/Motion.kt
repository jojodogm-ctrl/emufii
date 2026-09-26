package eu.emufii.app.ui

import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * False when the system has turned animations off (`ANIMATOR_DURATION_SCALE` at zero),
 * which is also what a handheld spares its battery with: everything reading this needs a
 * frozen state that still looks composed, never a blank one. Read once and remembered.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) != 0f
    }
}

/**
 * The app's motion in one place. Before this, durations were literals scattered across
 * two dozen files -- 250 here, 420 there, five different springs for the same press --
 * and nothing could be tuned without hunting them down one by one.
 *
 * Everything here answers [rememberAnimationsEnabled]: with animations off the spec
 * becomes a [snap], so a frozen state is still a composed one and never a blank one.
 * pourquoi : docs/decisions/performance-rendu.md § One clock for everything that moves continuously
 */
object Motion {

    /** Chrome stepping aside for content, and coming back. */
    const val AWAY_MS = 130

    const val BACK_MS = 90

    /**
     * One press, one personality. It was `MediumBouncy` on tiles and chips, `0.7f` with
     * `StiffnessLow` on an arrival, `0.7f/900f` on the switch, `LowBouncy` on the card:
     * the same gesture wearing five faces.
     */
    @Composable
    fun <T> press(): FiniteAnimationSpec<T> = spec(
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    )

    /*
     * The trailer's springs. It gives each as a pulsation `w` (rad/s) and a damping `z`;
     * Compose takes the same spring as `stiffness = w²`. A screen no longer slides in: it
     * blooms, see [bloom].
     * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Springs, taken from the trailer
     */

    /** A surface changing size, shape or place. w 19, z 0.80. */
    @Composable
    fun <T> morph(): FiniteAnimationSpec<T> = spec(spring(0.80f, 360f))

    /** Opacity and blur of something appearing. w 30, z 0.99. */
    @Composable
    fun <T> enter(): FiniteAnimationSpec<T> = spec(spring(1f, 900f))

    /** Opacity and blur of something leaving: faster than it came. w 42, z 0.99. */
    @Composable
    fun <T> exit(): FiniteAnimationSpec<T> = spec(spring(1f, 1760f))

    /** Rows, lines of text, buttons, coming up into place. w 28, z 0.99. */
    @Composable
    fun <T> rise(): FiniteAnimationSpec<T> = spec(spring(1f, 784f))

    /** Avatar, badge, dot: overshoots once, then settles. w 20, z 0.70. */
    @Composable
    fun <T> pop(): FiniteAnimationSpec<T> = spec(spring(0.70f, 400f))

    /** A code character or a key landing. w 32, z 0.88. */
    @Composable
    fun <T> snapIn(): FiniteAnimationSpec<T> = spec(spring(0.88f, 1024f))

    /** A whole view zooming or travelling. w 12, z 0.88. */
    @Composable
    fun <T> camera(): FiniteAnimationSpec<T> = spec(spring(0.88f, 144f))

    /** The focus ring moving between cells. w 16, z 0.90. */
    @Composable
    fun <T> cursor(): FiniteAnimationSpec<T> = spec(spring(0.90f, 256f))

    /** Any colour change: never a hard cut between two colours. w 18, z 0.95. */
    @Composable
    fun <T> tint(): FiniteAnimationSpec<T> = spec(spring(0.95f, 324f))

    /** A tick being drawn. w 22, z 0.99. */
    @Composable
    fun <T> draw(): FiniteAnimationSpec<T> = spec(spring(1f, 484f))

    /** A thing arriving on screen: softer, and it may overshoot. */
    @Composable
    fun <T> arrival(): FiniteAnimationSpec<T> = spec(
        spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow)
    )

    @Composable
    fun <T> tween(
        durationMillis: Int,
        delayMillis: Int = 0,
        easing: Easing = FastOutSlowInEasing
    ): FiniteAnimationSpec<T> = spec(
        androidx.compose.animation.core.tween(durationMillis, delayMillis, easing)
    )

    /** Animations off: the value jumps, and the frame after is the finished one. */
    @Composable
    fun <T> spec(wanted: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (rememberAnimationsEnabled()) wanted else snap()
}

/**
 * The tween every crossing uses, silenced when the system has animations off. A free
 * function because `Motion.tween` cannot be called from a `transitionSpec` lambda, which
 * is not a composable scope of its own.
 */
@Composable
fun <T> motionTween(
    durationMillis: Int,
    delayMillis: Int = 0,
    easing: Easing = FastOutSlowInEasing
): FiniteAnimationSpec<T> = Motion.tween(durationMillis, delayMillis, easing)
