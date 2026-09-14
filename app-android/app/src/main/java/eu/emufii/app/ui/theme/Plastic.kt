package eu.emufii.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The DUOTONE SHELVES material. A tile is drawn in this order: shadow, face, 1 dp
 * contour, moulding, the last lit along the top inner edge from one light source high
 * and slightly left. Without it a white plate sits four points of luminance above a
 * cream shell and reads as one sheet; volume lives in the rim, never in a hard shadow.
 * pourquoi : docs/decisions/theme-duotone-shelves.md § MATERIAL (replaces Plastic.kt)
 */

/** Warm black on the warm neutrals, never blue-black. */
private val ShadowInk = Color(0xFF241610)

@Composable
fun plateBrush(dark: Boolean, oled: Boolean): Brush =
    Brush.verticalGradient(plateColors(dark, oled))

/** Exposed so a caller can build the gradient over shifted bounds. */
fun plateColors(dark: Boolean, oled: Boolean): List<Color> = when {
    oled -> listOf(PlateOled, PlateOledLow)
    dark -> listOf(PlateDark, PlateDarkLow)
    else -> listOf(PlateLight, PlateLightLow)
}

fun edgeColor(dark: Boolean, oled: Boolean): Color =
    if (oled) EdgeOled else if (dark) EdgeDark else EdgeLight

/** Reversed for a recess: the same light on a hollow. */
private fun bevelStops(dark: Boolean, oled: Boolean, inverted: Boolean): Pair<Color, Color> {
    val lit = if (dark || oled) BevelDark else BevelLight
    val shade = if (dark || oled) BevelShadeDark else BevelShadeLight
    return if (inverted) shade to lit else lit to shade
}

private fun DrawScope.drawMoulding(
    shape: Shape,
    dark: Boolean,
    oled: Boolean,
    inverted: Boolean,
    width: Dp
) {
    val (top, bottom) = bevelStops(dark, oled, inverted)
    val w = width.toPx()
    // A plate smaller than its own lip is skipped: `inset` needs what the trim leaves.
    if (size.width <= w || size.height <= w) return
    // A stroke straddles its path, and the outer half would sit on the 1 dp contour.
    val brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to top,
            0.42f to top.copy(alpha = top.alpha * 0.12f),
            0.62f to bottom.copy(alpha = bottom.alpha * 0.12f),
            1f to bottom
        ),
        startY = 0f,
        endY = size.height
    )
    inset(w / 2f) {
        drawOutline(
            shape.createOutline(size, layoutDirection, this),
            brush = brush,
            style = Stroke(width = w)
        )
    }
}

/**
 * Both shadow and moulding grow with [lift].
 * pourquoi : docs/decisions/theme-duotone-shelves.md § MATERIAL (replaces Plastic.kt)
 */
@Composable
fun Modifier.plate(
    shape: Shape,
    dark: Boolean,
    oled: Boolean,
    lift: Dp = 4.dp,
    bevel: Boolean = true,
    /** The tile sinks and flips its light: the lit lip becomes the shaded one. */
    pressed: Boolean = false,
    /** A control that has to read as cut out of what it sits on takes that colour. */
    fill: Color? = null
): Modifier {
    val brush = fill?.let { SolidColor(it) } ?: plateBrush(dark, oled)
    val edge = edgeColor(dark, oled)
    val elevation = if (pressed) (lift / 3) else lift
    // A wider lip on the highest surfaces, so a dialog does not carry a chip's rim.
    val lipWidth = if (lift >= 10.dp) 2.dp else 1.5.dp
    return this
        .graphicsLayer {
            scaleX = if (pressed) 0.98f else 1f
            scaleY = if (pressed) 0.98f else 1f
        }
        .shadow(
            elevation = if (oled) 0.dp else elevation,
            shape = shape,
            clip = false,
            ambientColor = ShadowInk.copy(alpha = if (dark) 0.55f else 0.20f),
            spotColor = ShadowInk.copy(alpha = if (dark) 0.62f else 0.26f)
        )
        .clip(shape)
        .background(brush)
        .then(
            if (pressed) Modifier.background(ShadowInk.copy(alpha = if (dark) 0.22f else 0.07f))
            else Modifier
        )
        .then(
            if (bevel) Modifier.drawWithContent {
                drawContent()
                drawMoulding(shape, dark, oled, inverted = pressed, width = lipWidth)
            } else Modifier
        )
        .border(1.dp, edge, shape)
}

/** For a caller that has drawn its own face and wants only the volume. */
@Composable
fun Modifier.bevel(shape: Shape, dark: Boolean): Modifier {
    val oled = false
    return this.drawWithContent {
        drawContent()
        drawMoulding(shape, dark, oled, inverted = false, width = 1.5.dp)
    }
}

/** Kept empty for compatibility: the tray's texture is the backdrop's shelves now. */
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

/** Contour and moulding over whatever fills the shape; [plate] draws its edge first. */
@Composable
fun Modifier.moldedRim(shape: Shape, dark: Boolean, oled: Boolean): Modifier {
    val edge = edgeColor(dark, oled)
    return this.drawWithContent {
        drawContent()
        drawMoulding(shape, dark, oled, inverted = false, width = 1.5.dp)
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, color = edge, style = Stroke(width = 1.dp.toPx()))
    }
}

/** Not [socket], which is a carved hollow. */
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

/**
 * A bed for the chrome: halfway between the shell it lies on and the plate's low tint,
 * so a piece as wide as the screen stops reading as a slab. Its contour separates it,
 * not its brightness — the rule a 46 dp chip needs does not hold at this size.
 * pourquoi : docs/decisions/bibliotheque.md § The header is a pebble, and it keeps the wallpaper's luminance
 */
fun shelfFill(dark: Boolean, oled: Boolean): Color = when {
    oled -> ShellOled
    dark -> lerp(ShellDark, PlateDarkLow, 0.45f)
    else -> lerp(ShellLight, PlateLightLow, 0.45f)
}

@Composable
fun Modifier.shelf(shape: Shape, dark: Boolean, lift: Dp = 6.dp): Modifier {
    val oled = LocalEmufiiOledTheme.current && dark
    val fill = shelfFill(dark, oled)
    return this
        .shadow(
            elevation = if (oled) 0.dp else lift,
            shape = shape,
            clip = false,
            ambientColor = ShadowInk.copy(alpha = if (dark) 0.55f else 0.18f),
            spotColor = ShadowInk.copy(alpha = if (dark) 0.62f else 0.24f)
        )
        .clip(shape)
        .background(fill)
        .border(1.dp, edgeColor(dark, oled), shape)
}

/**
 * The plate's low tint with the moulding run backwards.
 * pourquoi : docs/decisions/theme-duotone-shelves.md § Hollows become notches
 */
@Composable
fun Modifier.socket(shape: Shape, dark: Boolean): Modifier {
    // Read here rather than in the signature: the twenty callers already pass `dark`.
    val oled = LocalEmufiiOledTheme.current && dark
    val fill = when {
        oled -> PlateOledLow
        dark -> PlateDarkLow
        else -> PlateLightLow
    }
    val edge = when {
        // The hollow stops using its fill as separation; its contour draws it.
        oled -> EdgeOled
        dark -> Color(0x1FFFFFFF)
        else -> Color(0x1F241610)
    }
    return this
        .clip(shape)
        .background(fill)
        .drawWithContent {
            drawContent()
            drawMoulding(shape, dark, oled = oled, inverted = true, width = 2.dp)
        }
        .border(1.dp, edge, shape)
}
