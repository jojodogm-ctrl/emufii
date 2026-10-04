package eu.emufii.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.emufii.app.ui.Motion

/** Parent fade for shadows: a shadow only takes its own layer's alpha. */
val LocalShadowFade = compositionLocalOf<() -> Float> { { 1f } }

private val ShadowInk = Color(0xFF241610)

@Composable
fun plateBrush(dark: Boolean, oled: Boolean): Brush =
    Brush.verticalGradient(plateColors(dark, oled))

fun plateColors(dark: Boolean, oled: Boolean): List<Color> = when {
    oled -> listOf(PlateOled, PlateOledLow)
    dark -> listOf(PlateDark, PlateDarkLow)
    else -> listOf(PlateLight, PlateLightLow)
}

fun edgeColor(dark: Boolean, oled: Boolean): Color =
    if (oled) EdgeOled else if (dark) EdgeDark else EdgeLight

fun Modifier.liftShadow(
    shape: Shape,
    lift: Dp,
    dark: Boolean,
    oled: Boolean = false,
    tint: Color? = null,
    sink: () -> Float = { 0f },
    tinted: () -> Float = { 1f },
    raisedLift: Dp = lift,
    raised: () -> Float = { 0f },
    fade: () -> Float = { 1f },
    tintNow: () -> Color? = { tint },
): Modifier {
    if (oled) return this
    return this.graphicsLayer {
        val r = raised().coerceIn(0f, 1.2f)
        val base = lift.toPx() + (raisedLift.toPx() - lift.toPx()) * r
        shadowElevation = base * SHADOW_REACH * (1f - sink().coerceIn(0f, 1f) * 2f / 3f)
        this.shape = shape
        clip = false
        val tint = tintNow()
        val t = if (tint == null) 0f else tinted().coerceIn(0f, 1f)
        val hue = if (tint == null) ShadowInk else lerp(ShadowInk, tint, t)
        val ink = if (dark) SPOT_DARK else SPOT_LIGHT
        val tintedAlpha = if (dark) SPOT_TINT_DARK else SPOT_TINT_LIGHT
        val f = fade().coerceIn(0f, 1f)
        spotShadowColor = hue.copy(alpha = (ink + (tintedAlpha - ink) * t) * f)
        val ambient = if (dark) AMBIENT_DARK else AMBIENT_LIGHT
        val ambientTint = if (dark) AMBIENT_TINT_DARK else AMBIENT_TINT_LIGHT
        ambientShadowColor = (if (tint == null) ShadowInk else lerp(ShadowInk, tint, t))
            .copy(alpha = (ambient + (ambientTint - ambient) * t) * f)
    }
}

private const val SHADOW_REACH = 3f

private const val SPOT_LIGHT = 0.16f
private const val SPOT_DARK = 0.60f
private const val SPOT_TINT_LIGHT = 0.35f
private const val SPOT_TINT_DARK = 0.45f

private const val AMBIENT_LIGHT = 0.06f
private const val AMBIENT_DARK = 0.30f

private const val AMBIENT_TINT_LIGHT = 0.55f
private const val AMBIENT_TINT_DARK = 0.70f

@Composable
fun Modifier.plate(
    shape: Shape,
    dark: Boolean,
    oled: Boolean,
    lift: Dp = 4.dp,
    pressed: Boolean = false,
    fill: Color? = null,
    tint: Color? = null
): Modifier {
    val brush = fill?.let { SolidColor(it) } ?: plateBrush(dark, oled)
    val fade = LocalShadowFade.current
    val sink by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = Motion.press(),
        label = "plate-press"
    )
    return this
        .graphicsLayer {
            val s = 1f - PRESS_SCALE_DROP * sink
            scaleX = s
            scaleY = s
            this.shape = shape
            clip = true
            if (!oled) {
                shadowElevation = lift.toPx() * SHADOW_REACH * (1f - sink * 2f / 3f)
                val f = fade().coerceIn(0f, 1f)
                val ink = if (dark) SPOT_DARK else SPOT_LIGHT
                spotShadowColor = when (tint) {
                    null -> ShadowInk.copy(alpha = ink * f)
                    else -> tint.copy(alpha = (if (dark) SPOT_TINT_DARK else SPOT_TINT_LIGHT) * f)
                }
                ambientShadowColor = ShadowInk.copy(alpha = (if (dark) AMBIENT_DARK else AMBIENT_LIGHT) * f)
            }
        }
        .background(brush)
}

private const val PRESS_SCALE_DROP = 0.03f

fun DrawScope.engravedGrid(
    @Suppress("UNUSED_PARAMETER") step: Float,
    @Suppress("UNUSED_PARAMETER") line: Color,
    @Suppress("UNUSED_PARAMETER") highlight: Color
) {
}

@Composable
fun tilePlateBrush(dark: Boolean, oled: Boolean): Brush = when {
    oled -> Brush.verticalGradient(listOf(PlateOled, PlateOledLow))
    dark -> Brush.verticalGradient(listOf(PlateDark, PlateDarkLow))
    else -> SolidColor(PlateLight)
}

fun Modifier.dashedSlot(shape: Shape, color: Color, corner: Dp = 20.dp): Modifier =
    this.drawBehind {
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(corner.toPx()),
            style = Stroke(
                width = stroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
            )
        )
    }

fun shelfFill(dark: Boolean, oled: Boolean): Color = when {
    oled -> ShellOled
    dark -> lerp(ShellDark, PlateDarkLow, 0.45f)
    else -> lerp(ShellLight, PlateLightLow, 0.45f)
}

@Composable
fun Modifier.shelf(shape: Shape, dark: Boolean, lift: Dp = 6.dp): Modifier {
    val oled = LocalEmufiiOledTheme.current && dark
    val fade = LocalShadowFade.current
    return this
        .graphicsLayer {
            this.shape = shape
            clip = true
            if (!oled) {
                val f = fade().coerceIn(0f, 1f)
                shadowElevation = lift.toPx() * SHADOW_REACH
                spotShadowColor = ShadowInk.copy(alpha = (if (dark) SPOT_DARK else SPOT_LIGHT) * f)
                ambientShadowColor = ShadowInk.copy(alpha = (if (dark) AMBIENT_DARK else AMBIENT_LIGHT) * f)
            }
        }
        .background(shelfFill(dark, oled))
}

@Composable
fun Modifier.socket(shape: Shape, dark: Boolean): Modifier {
    val oled = LocalEmufiiOledTheme.current && dark
    val fill = plateColors(dark, oled).first()
    val lip = ShadowInk.copy(alpha = if (dark || oled) 0.30f else 0.10f)
    return this
        .clip(shape)
        .background(fill)
        .drawWithCache {
            val depth = SOCKET_LIP.toPx()
            val brush = Brush.verticalGradient(
                0f to lip,
                1f to Color.Transparent,
                startY = 0f,
                endY = depth
            )
            onDrawWithContent {
                drawRect(brush, size = Size(size.width, depth))
                drawContent()
            }
        }
}

private val SOCKET_LIP = 8.dp
