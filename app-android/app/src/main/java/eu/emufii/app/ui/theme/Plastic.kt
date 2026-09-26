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

/**
 * The DUOTONE SHELVES material, flat since the trailer: a face and two drop shadows, a
 * wide soft one that lifts the plate off the tray and a short dense one that sets it down.
 * No contour and no moulding: the shadow is what separates.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Flat plates, dropped shadows
 */

/**
 * How far the surfaces below have faded in, 0 to 1, read at draw time. A shadow takes only
 * its own layer's opacity, never its parent's: a card fading in inside a cascade showed its
 * full shadow for the first frames, a black flash under a transparent card. Every shadow
 * here multiplies its strength by this instead.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Blur is paid only in flight
 */
val LocalShadowFade = compositionLocalOf<() -> Float> { { 1f } }

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

/** Hairlines and dividers only: no plate carries a contour any more. */
fun edgeColor(dark: Boolean, oled: Boolean): Color =
    if (oled) EdgeOled else if (dark) EdgeDark else EdgeLight

/**
 * The shadow under a surface raised by [lift]: the platform's own, cast by the render
 * thread from the shape's outline. `Modifier.dropShadow` blurred a bitmap on the CPU for
 * every radius it met, so an animated lift re-rasterised on every frame, and it painted
 * that bitmap through a rectangle a blurred parent layer showed square. This one costs
 * nothing to animate: [sink] and [raised] are read at draw time, 0 to 1, a press and a
 * selection; [raisedLift] is where [raised] goes. [tint] is the axis's hue, blended in
 * by [tinted].
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Flat plates, dropped shadows
 */
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
    /** Read at draw time, for a tint that arrives after composition (a cover's tone). */
    tintNow: () -> Color? = { tint },
): Modifier {
    // A shadow on black is not seen; there the plate's own tone separates it.
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
        // The ambient shadow surrounds the shape where the spot falls below it: tinted, it
        // is the halo of colour all round a cover rather than a stain under it.
        val ambient = if (dark) AMBIENT_DARK else AMBIENT_LIGHT
        val ambientTint = if (dark) AMBIENT_TINT_DARK else AMBIENT_TINT_LIGHT
        ambientShadowColor = (if (tint == null) ShadowInk else lerp(ShadowInk, tint, t))
            .copy(alpha = (ambient + (ambientTint - ambient) * t) * f)
    }
}

/** Elevation per dp of lift: the platform's spot shadow at 3x reads as the trailer's. */
private const val SHADOW_REACH = 3f

/** Measured against the trailer: 16% light, 60% dark, a little more when tinted. */
private const val SPOT_LIGHT = 0.16f
private const val SPOT_DARK = 0.60f
private const val SPOT_TINT_LIGHT = 0.35f
private const val SPOT_TINT_DARK = 0.45f

/** The contact shadow: short and dense, the platform's ambient. */
private const val AMBIENT_LIGHT = 0.06f
private const val AMBIENT_DARK = 0.30f

/** A cover's colour round it: strong enough to read as a glow, not as a border. */
private const val AMBIENT_TINT_LIGHT = 0.55f
private const val AMBIENT_TINT_DARK = 0.70f

/**
 * Lift by use: 2 dp chips and small buttons, 4 dp cards and tiles at rest, 10 dp a
 * selected tile, 16 dp the launch card and dialogs. A press scales to 0.97 and divides
 * the lift by three, both on [Motion.press], never as a jump.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Flat plates, dropped shadows
 */
@Composable
fun Modifier.plate(
    shape: Shape,
    dark: Boolean,
    oled: Boolean,
    lift: Dp = 4.dp,
    pressed: Boolean = false,
    /** A control that has to read as cut out of what it sits on takes that colour. */
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
    // One layer for the press, the shadow and the clip: a node clipped to its outline still
    // casts that outline's shadow, and three layers per plate were three render nodes on
    // every card of every list.
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
 * so a piece as wide as the screen stops reading as a slab.
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

/**
 * The plate's own face and a soft shadow under the top lip: pushed in, without a contour
 * and without a darker fill. The name stays, twenty callers still say it.
 * pourquoi : docs/decisions/matiere-et-mouvement-trailer.md § Flat plates, dropped shadows
 */
@Composable
fun Modifier.socket(shape: Shape, dark: Boolean): Modifier {
    // Read here rather than in the signature: the twenty callers already pass `dark`.
    val oled = LocalEmufiiOledTheme.current && dark
    // The plate's own colour: only the shadow under the lip says it is a hollow.
    val fill = plateColors(dark, oled).first()
    val lip = ShadowInk.copy(alpha = if (dark || oled) 0.30f else 0.10f)
    return this
        .clip(shape)
        .background(fill)
        // The inner shadow as a gradient under the top lip: `innerShadow` blurs a bitmap
        // per size on the CPU, and a hollow does not need a real blur to read as one.
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
