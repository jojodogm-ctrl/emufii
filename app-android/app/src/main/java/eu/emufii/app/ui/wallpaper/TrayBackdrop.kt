package eu.emufii.app.ui.wallpaper

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import eu.emufii.app.ui.rememberSlowMillis
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.Shelf
import eu.emufii.app.ui.theme.ShellDark
import eu.emufii.app.ui.theme.ShellDarkLow
import eu.emufii.app.ui.theme.ShellLight
import eu.emufii.app.ui.theme.ShellLightLow
import eu.emufii.app.ui.theme.ShellOled
import eu.emufii.app.ui.theme.Teal
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

@Composable
fun TrayBackdrop(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    oled: Boolean = LocalEmufiiOledTheme.current,
    animated: Boolean = true
) {
    val time = (if (animated) rememberSlowMillis() else FROZEN_MS) / CYCLE_MS

    val still = remember { mutableStateOf<ImageBitmap?>(null) }
    var baked by remember { mutableStateOf<StillKey?>(null) }

    Canvas(modifier = modifier.graphicsLayer()) {
        val top = if (oled) ShellOled else if (dark) ShellDark else ShellLight
        val bottom = if (oled) ShellOled else if (dark) ShellDarkLow else ShellLightLow
        val geometry = TrayGeometry(size)

        val key = StillKey(size.width, size.height, dark, oled)
        if (baked != key && size.width >= 2f && size.height >= 2f) {
            val w = (size.width * STILL_SCALE).toInt().coerceAtLeast(1)
            val h = (size.height * STILL_SCALE).toInt().coerceAtLeast(1)
            val bitmap = ImageBitmap(w, h)
            CanvasDrawScope().draw(
                density = Density(density * STILL_SCALE, fontScale),
                layoutDirection = LayoutDirection.Ltr,
                canvas = androidx.compose.ui.graphics.Canvas(bitmap),
                size = Size(w.toFloat(), h.toFloat())
            ) { drawStillTray(TrayGeometry(Size(w.toFloat(), h.toFloat())), dark, oled, top, bottom) }
            still.value = bitmap
            baked = key
        }
        still.value?.let {
            drawImage(
                it,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Low
            )
        }

        drawWaves(geometry, time, dark, oled)
    }
}

private const val STILL_SCALE = 0.5f

private data class StillKey(
    val width: Float,
    val height: Float,
    val dark: Boolean,
    val oled: Boolean,
)

private class TrayGeometry(size: Size) {
    val side = 0.58f * max(size.width, size.height)
    val radius = CornerRadius(side * 0.30f, side * 0.30f)

    val coral = shelfRect(size.width * 0.02f, size.height * -0.06f)
    val teal = shelfRect(size.width * 0.98f, size.height * 1.06f)

    val coralCorner = Offset(coral.right, coral.bottom)
    val tealCorner = Offset(teal.left, teal.top)

    private fun shelfRect(cx: Float, cy: Float) = RoundRect(
        left = cx - side / 2f,
        top = cy - side / 2f,
        right = cx + side / 2f,
        bottom = cy + side / 2f,
        cornerRadius = radius
    )
}

private fun DrawScope.drawStillTray(
    geometry: TrayGeometry,
    dark: Boolean,
    oled: Boolean,
    top: Color,
    bottom: Color,
) {
    drawRect(brush = Brush.verticalGradient(listOf(top, bottom)))

    val fill = when {
        oled -> Shelf.fillOled
        dark -> Shelf.fillDark
        else -> Shelf.fillLight
    }
    val glowAlpha = when {
        oled -> 0.075f
        dark -> 0.110f
        else -> 0.130f
    }

    fun shelf(
        rect: RoundRect,
        corner: Offset,
        axisBright: Color,
        axisDeep: Color,
        ground: Color,
    ) {
        val path = Path().apply { addRoundRect(rect) }
        if (!oled) {
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(
                        ((if (dark) 0.40f else 0.10f) * 255).toInt(), 36, 22, 16
                    )
                    maskFilter = android.graphics.BlurMaskFilter(
                        geometry.side * 0.06f,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
                val shadow = Path().apply {
                    addRoundRect(rect)
                    translate(Offset(0f, geometry.side * 0.025f))
                }
                canvas.nativeCanvas.drawPath(shadow.asAndroidPath(), paint)
            }
        }
        drawPath(
            path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    axisBright.copy(alpha = fill),
                    axisDeep.copy(alpha = fill * 0.72f)
                ),
                startY = rect.top,
                endY = rect.bottom
            )
        )

        if (dark || oled) {
            drawPath(
                path,
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.52f to Color.Transparent,
                        1f to ground
                    ),
                    center = corner,
                    radius = geometry.side * 1.10f
                )
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to axisBright.copy(alpha = glowAlpha),
                    0.45f to axisBright.copy(alpha = glowAlpha * 0.45f),
                    1f to Color.Transparent
                ),
                center = corner,
                radius = geometry.side * 0.82f
            ),
            radius = geometry.side * 0.82f,
            center = corner
        )
    }

    shelf(
        geometry.coral,
        geometry.coralCorner,
        if (dark) Coral.darkBright else Coral.bright,
        Coral.deep,
        top
    )
    shelf(
        geometry.teal,
        geometry.tealCorner,
        if (dark) Teal.darkBright else Teal.bright,
        Teal.deep,
        bottom
    )

    if (!oled) {
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = if (dark) 0.32f else 0.14f)
                ),
                center = Offset(size.width * 0.5f, size.height * 0.42f),
                radius = max(size.width, size.height) * 0.78f
            ),
            size = Size(size.width, size.height)
        )
    }
}

private fun DrawScope.drawWaves(
    geometry: TrayGeometry,
    time: Double,
    dark: Boolean,
    oled: Boolean,
) {
    val waveAlpha = when {
        oled -> 0.055f
        dark -> 0.085f
        else -> 0.100f
    }

    fun waves(rect: RoundRect, axisBright: Color) {
        repeat(WAVES) { i ->
            val p = ((time * WAVE_SPEED) + i.toDouble() / WAVES).mod(1.0).toFloat()
            val alpha = waveAlpha * sin(p * PI).toFloat()
            if (alpha <= 0.002f) return@repeat
            val reach = p * geometry.side * 0.75f
            val ripple = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = rect.left - reach,
                        top = rect.top - reach,
                        right = rect.right + reach,
                        bottom = rect.bottom + reach,
                        cornerRadius = CornerRadius(
                            geometry.radius.x + reach,
                            geometry.radius.y + reach
                        )
                    )
                )
            }
            for ((width, share) in WAVE_HALO) {
                drawPath(
                    ripple,
                    color = axisBright.copy(alpha = alpha * share),
                    style = Stroke(width = width.toPx())
                )
            }
        }
    }

    waves(geometry.coral, if (dark) Coral.darkBright else Coral.bright)
    waves(geometry.teal, if (dark) Teal.darkBright else Teal.bright)
}

private const val WAVES = 2

private const val WAVE_SPEED = 0.55

private val WAVE_STROKE = 2.5.dp

private val WAVE_HALO: List<Pair<Dp, Float>> = listOf(
    13.dp to 0.22f,
    WAVE_STROKE to 1.0f,
)

private const val CYCLE_MS = 19_000

private const val FROZEN_MS = 8_000.0
