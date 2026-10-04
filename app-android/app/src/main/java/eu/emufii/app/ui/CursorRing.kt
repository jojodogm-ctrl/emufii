package eu.emufii.app.ui

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun Modifier.neonFocusRing(
    focused: Boolean,
    shape: Shape,
    start: Color,
    end: Color,
    minBand: Dp,
    bandFraction: Float = 0.12f,
    maxBand: Dp = 24.dp,
    inMs: Int,
    outMs: Int,
): Modifier {
    val density = LocalDensity.current
    val grow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(if (focused) inMs else outMs),
        label = "neon-ring"
    )
    if (grow <= 0f) return this

    // Read in composition, not in draw, so cursor and background repaint in one pass.
    val step = rememberFlowStep()

    val paints = remember { NeonPaints() }
    return this.drawWithCache {
        val band = bandWidth(size, bandFraction, minBand, maxBand, density) * grow
        val radius = cornerRadiusOf(shape, size, density)
        val hair = (0.16f * band).coerceIn(1.5f * density.density, 4f * density.density)
        val blur = (0.7f * band).coerceIn(4f * density.density, 14f * density.density)

        val w = size.width
        val h = size.height
        val rOuter = radius + band
        val rInner = radius

        // One rounded rect per path; other shapes rasterise on the CPU.
        val midline = Path().apply {
            val half = band / 2f
            val r = (rOuter + rInner) / 2f
            addRoundRect(RectF(-half, -half, w + half, h + half), r, r, Path.Direction.CW)
        }
        val outerEdge = Path().apply {
            val i = -band + hair / 2f
            val r = (rOuter - hair / 2f).coerceAtLeast(0f)
            addRoundRect(RectF(i, i, w - i, h - i), r, r, Path.Direction.CW)
        }
        val innerEdge = Path().apply {
            val i = -hair / 2f
            val r = (rInner + hair / 2f).coerceAtLeast(0f)
            addRoundRect(RectF(i, i, w - i, h - i), r, r, Path.Direction.CW)
        }

        // Stacked strokes rather than BlurMaskFilter, which has no GPU path.
        val halo = listOf(
            (band + blur * 1.6f) to 0.16f,
            (band + blur * 0.8f) to 0.30f,
            band to 0.55f,
        )

        val plain = LinearGradient(
            0f, -band, 0f, h + band,
            start.copy(alpha = start.alpha * grow).toArgb(),
            end.copy(alpha = end.alpha * grow).toArgb(),
            Shader.TileMode.CLAMP
        )
        paints.outer.strokeWidth = hair
        paints.outer.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.White.copy(alpha = 0.50f * grow).toArgb(),
            Color.White.copy(alpha = 0.30f * grow).toArgb(),
            Shader.TileMode.CLAMP
        )
        paints.inner.strokeWidth = hair
        paints.inner.color = Color.White.copy(alpha = 0.40f * grow).toArgb()

        onDrawWithContent {
            drawContent()
            // The phase is read here: the gradient flows without recomposing anything.
            paints.band.shader = CursorFlow.shader(
                w = w + 2f * band,
                h = h + 2f * band,
                band = band,
                radius = (rOuter + rInner) / 2f,
                start = start.copy(alpha = start.alpha * grow),
                end = end.copy(alpha = end.alpha * grow),
                step = step
            ) ?: plain
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                for ((width, share) in halo) {
                    paints.glow.strokeWidth = width
                    paints.glow.color =
                        start.copy(alpha = start.alpha * grow * share).toArgb()
                    native.drawPath(midline, paints.glow)
                }
                paints.band.strokeWidth = band
                native.drawPath(midline, paints.band)
                native.drawPath(outerEdge, paints.outer)
                native.drawPath(innerEdge, paints.inner)
            }
        }
    }
}

private fun bandWidth(size: Size, fraction: Float, min: Dp, max: Dp, density: Density): Float {
    val floor = with(density) { min.toPx() }
    val ceiling = with(density) { max.toPx() }
    return (fraction * minOf(size.width, size.height)).coerceIn(floor, ceiling)
}

private fun cornerRadiusOf(shape: Shape, size: Size, density: Density): Float =
    if (shape is RoundedCornerShape) {
        shape.topStart.toPx(size, density)
    } else {
        val outline = shape.createOutline(size, LayoutDirection.Ltr, density)
        when (outline) {
            is androidx.compose.ui.graphics.Outline.Rounded ->
                outline.roundRect.topLeftCornerRadius.x
            else -> 0f
        }
    }

private class NeonPaints {
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
}
