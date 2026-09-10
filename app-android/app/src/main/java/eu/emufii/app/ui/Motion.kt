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
import androidx.compose.ui.unit.dp

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

    /** A screen replacing another: long enough to read as a move, short enough to obey. */
    const val SCREEN_IN_MS = 260

    const val SCREEN_OUT_MS = 160

    /** How far a screen travels while it fades. A hint of direction, never a full slide. */
    val SCREEN_SHIFT = 28.dp

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
    private fun <T> spec(wanted: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
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
