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

/** False when ANIMATOR_DURATION_SCALE is 0; callers must still render a finished state. */
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

object Motion {

    const val AWAY_MS = 130

    const val BACK_MS = 90

    @Composable
    fun <T> press(): FiniteAnimationSpec<T> = spec(
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    )

    @Composable
    fun <T> morph(): FiniteAnimationSpec<T> = spec(spring(0.80f, 360f))

    @Composable
    fun <T> enter(): FiniteAnimationSpec<T> = spec(spring(1f, 900f))

    @Composable
    fun <T> exit(): FiniteAnimationSpec<T> = spec(spring(1f, 1760f))

    @Composable
    fun <T> rise(): FiniteAnimationSpec<T> = spec(spring(1f, 784f))

    @Composable
    fun <T> pop(): FiniteAnimationSpec<T> = spec(spring(0.70f, 400f))

    @Composable
    fun <T> snapIn(): FiniteAnimationSpec<T> = spec(spring(0.88f, 1024f))

    @Composable
    fun <T> camera(): FiniteAnimationSpec<T> = spec(spring(0.88f, 144f))

    @Composable
    fun <T> cursor(): FiniteAnimationSpec<T> = spec(spring(0.90f, 256f))

    @Composable
    fun <T> tint(): FiniteAnimationSpec<T> = spec(spring(0.95f, 324f))

    @Composable
    fun <T> draw(): FiniteAnimationSpec<T> = spec(spring(1f, 484f))

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

    @Composable
    fun <T> spec(wanted: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (rememberAnimationsEnabled()) wanted else snap()
}

@Composable
fun <T> motionTween(
    durationMillis: Int,
    delayMillis: Int = 0,
    easing: Easing = FastOutSlowInEasing
): FiniteAnimationSpec<T> = Motion.tween(durationMillis, delayMillis, easing)
