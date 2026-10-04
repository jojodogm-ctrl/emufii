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
