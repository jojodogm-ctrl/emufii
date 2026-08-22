package eu.emufii.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The material every raised thing in this app is made of. See [Direction].
 *
 * A moulded plate is four things, always in this order, and the order is the
 * whole trick:
 *
 * 1. a shadow with a real vertical offset — light comes from above the tray, so
 *    a plate casts *below* itself. A zero-offset halo is decoration, not depth,
 *    and it is what the glass world used to draw;
 * 2. a fill that is lighter at the top than at the bottom, which is what a
 *    curved plastic face does under that light;
 * 3. a hairline edge, the moulding's own contour;
 * 4. a lit bevel one hairline inside it, along the top.
 *
 * Take one away and the plate flattens into a rectangle of colour.
 */

/** How deep the lit edge of a moulding runs, whatever the object's size. */
private val BEVEL_DEPTH = 20.dp

/** The plate's face, as a brush, lit from the top. */
@Composable
fun plateBrush(dark: Boolean, oled: Boolean): Brush =
    Brush.verticalGradient(plateColors(dark, oled))

/**
 * The plate's two colours, before they are made into a gradient.
 *
 * Exposed because a caller sometimes has to build the gradient itself: a
 * settings row fills opaquely with the slice of its card's face that belongs to
 * it, which means the same colours over shifted bounds. Taking the list rather
 * than copying the values is what keeps the two from drifting apart.
 *
 * Barely a gradient on the light theme: white plastic under a diffuse light has
 * a very short falloff, and any more reads as a grey smear.
 */
fun plateColors(dark: Boolean, oled: Boolean): List<Color> = when {
    oled -> listOf(PlateOled, PlateOledLow)
    dark -> listOf(PlateDark, PlateDarkLow)
    else -> listOf(PlateLight, PlateLightLow)
}

/** The moulding's contour. Carries the whole separation on OLED. */
fun edgeColor(dark: Boolean, oled: Boolean): Color =
    if (oled) EdgeOled else if (dark) EdgeDark else EdgeLight

/**
 * A moulded plate: shadow, face, edge, bevel.
 *
 * [lift] is how far off the tray it sits — 0.dp for something flush, 10.dp for a
 * tile the cursor has picked up. The shadow's offset follows it, because a plate
 * that rises without its shadow moving reads as growing, not lifting.
 */
@Composable
fun Modifier.plate(
    shape: Shape,
    dark: Boolean,
    oled: Boolean,
    lift: Dp = 4.dp,
    bevel: Boolean = true,
    /**
     * True while the control is held down.
     *
     * A moulded button that never travels is a picture of a button. Pressed, the
     * plate loses its lift and its lit edge and takes a shade of the tray's
     * shadow: the same three parts that made it stand proud, taken away, which
     * is what "pushed in" is made of.
     */
    pressed: Boolean = false
): Modifier {
    val brush = plateBrush(dark, oled)
    val edge = edgeColor(dark, oled)
    return this
        .shadow(
            elevation = if (oled || pressed) 0.dp else lift,
            shape = shape,
            clip = false,
            // Warm-neutral rather than pure black: a black shadow on a cool grey
            // tray turns the ground green under the plate.
            ambientColor = Color(0xFF0A1220).copy(alpha = if (dark) 0.55f else 0.16f),
            spotColor = Color(0xFF0A1220).copy(alpha = if (dark) 0.65f else 0.22f)
        )
        .clip(shape)
        .background(brush)
        .then(
            if (pressed) Modifier.background(Color(0xFF0A1220).copy(alpha = if (dark) 0.50f else 0.12f))
            else Modifier
        )
        .then(if (bevel && !pressed) Modifier.bevel(shape, dark) else Modifier)
        .border(1.dp, edge, shape)
}

/**
 * The lit top edge of a moulding, drawn inside the plate.
 *
 * Only along the top third: a highlight that runs all the way round is a stroke,
 * and a stroke is what the outline already is. Drawn over the content on
 * purpose — it is one hairline, and it belongs to the surface, not under it.
 */
@Composable
fun Modifier.bevel(shape: Shape, dark: Boolean): Modifier {
    val color = if (dark) BevelDark else BevelLight
    return this.drawWithContent {
        drawContent()
        // A fixed depth, never a fraction of the height. Proportional, a tall
        // settings panel got half its face washed pale — the moulding's lit
        // edge is a property of the edge, and an edge does not get deeper
        // because the object is bigger.
        val h = BEVEL_DEPTH.toPx().coerceAtMost(size.height)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(color, Color.Transparent),
                startY = 0f,
                endY = h
            ),
            size = Size(size.width, h),
            // A single hairline of it, at the very top, plus the faintest wash
            // below: the wash is what keeps the line from looking drawn on.
            alpha = 0.55f
        )
        drawRect(color = color, size = Size(size.width, 1.dp.toPx()))
    }
}

/**
 * The tray's engraved grid.
 *
 * The ground of a console menu is never a flat fill: it carries a fine repeating
 * texture that gives the eye a scale reference, so the plates read as objects of
 * a size rather than shapes on a plane. Two hairline families, one dark one
 * light, a millimetre apart — that is an engraving, not a checkerboard.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.engravedGrid(
    step: Float,
    line: Color,
    highlight: Color
) {
    var x = 0f
    while (x < size.width) {
        drawRect(color = line, topLeft = Offset(x, 0f), size = Size(1f, size.height))
        drawRect(color = highlight, topLeft = Offset(x + 1f, 0f), size = Size(1f, size.height))
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawRect(color = line, topLeft = Offset(0f, y), size = Size(size.width, 1f))
        drawRect(color = highlight, topLeft = Offset(0f, y + 1f), size = Size(size.width, 1f))
        y += step
    }
}

/** The artwork plate a game icon sits on, in the library and on the cards. */
@Composable
fun tilePlateBrush(dark: Boolean, oled: Boolean): Brush = when {
    oled -> Brush.verticalGradient(listOf(PlateOled, PlateOledLow))
    dark -> Brush.verticalGradient(listOf(PlateDark, PlateDarkLow))
    // Left white: artwork of every possible colour reads against white, which
    // is why it was chosen in the first place.
    else -> SolidColor(Color.White)
}

/**
 * The moulding's contour, drawn *over* whatever fills the shape.
 *
 * [plate] puts its edge in the modifier chain, which draws it before the
 * content: fine for a panel, whose content is inset, useless on a tile whose
 * artwork covers the whole face. Here the rim is drawn after the content, plus
 * the single lit hairline along the top, so a tile with a photograph in it still
 * has a moulded edge catching the tray light.
 */
@Composable
fun Modifier.moldedRim(shape: Shape, dark: Boolean, oled: Boolean): Modifier {
    val edge = edgeColor(dark, oled)
    val lit = if (dark) BevelDark else BevelLight
    return this.drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, color = edge, style = Stroke(width = 1.5.dp.toPx()))
        // The lit hairline stops a third of the way down each side: light from
        // above catches the top of a moulding, never its flanks, and carrying it
        // round turns the rim into a drawn stroke.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(lit.copy(alpha = lit.alpha * 0.9f), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.18f
            ),
            size = Size(size.width, size.height * 0.18f)
        )
    }
}

/**
 * An empty socket in the tray: a recess, not a plate.
 *
 * The inverse lighting of everything else — dark at the top where a plate is
 * lit — which is the whole reason a hole reads as a hole. The grid's empty
 * slots use it, and they are what keeps the tray rectangular when the library
 * does not fill the last row.
 */
@Composable
fun Modifier.socket(shape: Shape, dark: Boolean): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            if (dark) listOf(Color(0x1F000000), Color(0x0AFFFFFF))
            else listOf(Color(0x14000000), Color(0x66FFFFFF))
        )
    )
    .border(1.dp, if (dark) Color(0x14FFFFFF) else Color(0x14000000), shape)
