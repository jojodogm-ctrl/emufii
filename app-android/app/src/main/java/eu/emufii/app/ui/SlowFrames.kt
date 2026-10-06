package eu.emufii.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import kotlinx.coroutines.delay

@Composable
fun rememberSlowMillis(): Double {
    val running = rememberAnimationsEnabled()
    LaunchedEffect(running) {
        if (!running) {
            slowMillis.doubleValue = FROZEN_MS
            return@LaunchedEffect
        }
        // `delay`, not withInfiniteAnimationFrameNanos: the latter wakes on every display frame.
        while (true) {
            slowMillis.doubleValue = ((System.nanoTime() - ORIGIN_NANOS) / 1_000_000).toDouble()
            delay(FRAME_INTERVAL_MS)
        }
    }
    return slowMillis.doubleValue
}

/** Shared so every layer drawing the waves stays in phase. */
private val slowMillis = mutableDoubleStateOf(FROZEN_MS)

private val ORIGIN_NANOS = System.nanoTime()

internal const val FRAME_INTERVAL_MS = 1_000L / 12

/** Not zero: at zero the waves show nothing. */
private const val FROZEN_MS = 8_000.0

/** A 0.6..0.95 glow breathing over 1.8 s, on the shared 12 fps clock: an infinite transition redraws every display frame. */
@Composable
fun rememberSlowBreath(): Float {
    val phase = (rememberSlowMillis() % (2 * BREATH_MS)) / BREATH_MS
    val t = if (phase <= 1.0) phase else 2.0 - phase
    val eased = 1.0 - (1.0 - t) * (1.0 - t)
    return (0.6 + 0.35 * eased).toFloat()
}

private const val BREATH_MS = 1_800.0
