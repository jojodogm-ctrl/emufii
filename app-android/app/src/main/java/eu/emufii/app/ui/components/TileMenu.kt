package eu.emufii.app.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.PlateDark
import eu.emufii.app.ui.theme.PlateLight

/**
 * A tile's menu, pulled out of the tile.
 *
 * The previous version was a centred box that dimmed the whole screen, including
 * the game being acted on, the one element that needed to stay in front of the
 * player. It floated without indicating where it came from: nothing tied the
 * menu to the game that had opened it.
 *
 * Here the card opens beside the tile, at its height, and unrolls from the edge
 * facing it, the movement saying where it comes from. A triangular tail hooks it
 * to the tile, painted in the game's colour (the one the cover art gave the
 * glow). The chrome stays neutral and the content brings the colour: every game
 * therefore opens a menu of its own, without a palette having been invented for
 * it.
 *
 * The side is chosen at runtime: a tile in the right-hand half opens to the
 * left. Otherwise games at the right edge would have had their menu folded
 * against the border, straddling the next tile.
 */
@Composable
fun TileMenu(
    expanded: Boolean,
    title: String,
    changeIconLabel: String,
    renameLabel: String,
    hideLabel: String,
    accent: Color?,
    onChangeIcon: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit
) {
    val dark = LocalEmufiiDarkTheme.current
    // The arrow is the only coloured part: enough to hook the card to its tile,
    // too little to compete with the cover art.
    val tail = accent ?: MaterialTheme.colorScheme.primary
    val surface = if (dark) PlateDark else PlateLight

    // Filled in at the first measure, before the card is visible: this is what
    // decides the side, and therefore the animation's origin.
    val placement = remember { SidePlacement() }

    // The window outlives the request to close, long enough for the unroll to
    // reverse. That is the whole difficulty of an exit animation: if the parent
    // removes the component the instant of the click, there is nothing left to
    // animate, which is why the menu decides its own disappearance.
    var present by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (expanded) present = true }
    if (!present) return

    Popup(
        popupPositionProvider = placement,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        // The first state is always "closed", even when the menu is born open:
        // an animated value that starts at its target does not animate, and the
        // card would appear all at once.
        var appeared by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appeared = true }
        val opening = expanded && appeared

        // An unroll, rather than a scale.
        //
        // The card used to open with a scaleIn: it grew *already whole* from
        // 82 %, so it existed before it had arrived, hence the impression that it
        // sprang out of nowhere. Here it is revealed by a band sweeping from the
        // edge touching the tile: halfway through, only half of it exists, and
        // the eye follows where it comes from.
        val reveal by animateFloatAsState(
            targetValue = if (opening) 1f else 0f,
            // The exit is not the entrance played backwards, and that is
            // deliberate: opening presents something to read, hence a barely
            // bouncy spring that takes its time; closing, the decision is already
            // made and all that is left to do is free the screen, quickly and
            // without bounce, otherwise you are waiting on your own menu.
            animationSpec =
                if (opening) spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow
                )
                else tween(130, easing = FastOutLinearInEasing),
            // The window is only withdrawn once the unroll has closed back up.
            finishedListener = { if (!opening) present = false },
            label = "menu-reveal"
        )

        // Re-read at every frame of the animation: the window is only measured
        // after the first composition, so the side is not known until then.
        val openLeft = placement.openLeft

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .graphicsLayer {
                    // The opacity runs ahead of the sweep: without it, the first
                    // pixel revealed arrives at full strength and snaps.
                    alpha = (reveal * 1.8f).coerceAtMost(1f)
                    // And the card travels the last few millimetres from the
                    // tile, which finishes saying where it comes out of.
                    translationX =
                        (1f - reveal) * SLIDE.toPx() * (if (openLeft) 1f else -1f)
                }
                // The size does not move during the animation: the window is
                // placed according to its size, so animating it would make it
                // slide at every frame. It is the drawing that gets clipped, not
                // the layout.
                .drawWithContent {
                    val shown = size.width * reveal
                    val left = if (openLeft) size.width - shown else 0f
                    clipRect(left = left, top = 0f, right = left + shown, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            if (openLeft) {
                MenuCard(title, changeIconLabel, renameLabel, hideLabel, surface, dark, onChangeIcon, onRename, onHide)
                Tail(tail, pointsLeft = false)
            } else {
                Tail(tail, pointsLeft = true)
                MenuCard(title, changeIconLabel, renameLabel, hideLabel, surface, dark, onChangeIcon, onRename, onHide)
            }
        }
    }
}

/** How far the card travels from the tile as it opens. */
private val SLIDE = 14.dp

@Composable
private fun MenuCard(
    title: String,
    changeIconLabel: String,
    renameLabel: String,
    hideLabel: String,
    surface: Color,
    dark: Boolean,
    onChangeIcon: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .width(206.dp)
            .shadow(
                elevation = if (dark) 0.dp else 26.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.14f)
            )
            .clip(shape)
            .background(surface)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 6.dp)
        )
        MenuRow(label = changeIconLabel, onClick = onChangeIcon) { drawImageGlyph(it) }
        MenuRow(label = renameLabel, onClick = onRename) { drawPencilGlyph(it) }
        MenuRow(label = hideLabel, onClick = onHide) { drawHideGlyph(it) }
    }
}

/** One menu entry: a glyph, a word, a full-width touch area. */
@Composable
private fun MenuRow(
    label: String,
    onClick: () -> Unit,
    glyph: DrawScope.(Color) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val highlighted = pressed || focused
    val tint = MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (highlighted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                else Color.Transparent
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Canvas(Modifier.size(18.dp)) { glyph(tint) }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

/** The tail that hooks the card to its tile. */
@Composable
private fun Tail(color: Color, pointsLeft: Boolean) {
    Canvas(Modifier.size(width = 9.dp, height = 20.dp).rotate(if (pointsLeft) 0f else 180f)) {
        val path = Path().apply {
            moveTo(0f, size.height / 2f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, color)
    }
}

/**
 * Three glyphs drawn by hand.
 *
 * The project ships no icon library, and pulling one in for two symbols would
 * grow the package more than these twenty lines do. Drawing them also allows
 * their weight to be chosen so it answers Poppins', which an off-the-shelf set
 * never guarantees.
 */
private fun DrawScope.drawImageGlyph(color: Color) {
    val s = size.minDimension
    val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
    drawRoundRect(
        color = color,
        topLeft = Offset(s * 0.08f, s * 0.14f),
        size = androidx.compose.ui.geometry.Size(s * 0.84f, s * 0.72f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.18f),
        style = stroke
    )
    drawCircle(color, radius = s * 0.075f, center = Offset(s * 0.33f, s * 0.36f))
    // The ridge line: it is what makes this read as "picture" rather than "frame".
    val ridge = Path().apply {
        moveTo(s * 0.14f, s * 0.76f)
        lineTo(s * 0.40f, s * 0.50f)
        lineTo(s * 0.62f, s * 0.72f)
        lineTo(s * 0.74f, s * 0.60f)
        lineTo(s * 0.86f, s * 0.76f)
    }
    drawPath(ridge, color, style = stroke)
}

private fun DrawScope.drawPencilGlyph(color: Color) {
    val s = size.minDimension
    val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
    val body = Path().apply {
        moveTo(s * 0.20f, s * 0.80f)
        lineTo(s * 0.28f, s * 0.56f)
        lineTo(s * 0.64f, s * 0.20f)
        lineTo(s * 0.80f, s * 0.36f)
        lineTo(s * 0.44f, s * 0.72f)
        close()
    }
    drawPath(body, color, style = stroke)
    // The stroke under the nib: the act of writing, not just the tool.
    drawLine(
        color,
        start = Offset(s * 0.20f, s * 0.86f),
        end = Offset(s * 0.62f, s * 0.86f),
        strokeWidth = s * 0.09f,
        cap = StrokeCap.Round
    )
}

/**
 * Places the card against the tile's flank, on whichever side has the room.
 *
 * Remembers the chosen side so the animation starts from the right edge: a card
 * that opens to the left but grows from its own left looks like it is fleeing the
 * tile rather than coming out of it.
 */
private class SidePlacement : PopupPositionProvider {
    var openLeft: Boolean = false
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val gap = 12
        val spaceRight = windowSize.width - anchorBounds.right
        openLeft = spaceRight < popupContentSize.width + gap

        val x =
            if (openLeft) anchorBounds.left - popupContentSize.width - gap
            else anchorBounds.right + gap

        // Centred on the tile, then pulled back into the screen: on the bottom
        // row a centred card would spill under the navigation bar.
        val y = anchorBounds.center.y - popupContentSize.height / 2
        return IntOffset(
            x.coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap)),
            y.coerceIn(gap, (windowSize.height - popupContentSize.height - gap).coerceAtLeast(gap))
        )
    }
}

/**
 * A crossed-out eye, and not a bin.
 *
 * The bin is the wrong promise: nothing here deletes a file, and a player who
 * reads "delete" on a menu that only hides is right to be angry when they go
 * looking for the ROM afterwards. An eye says what actually happens — the game
 * leaves the grid and stays on the card.
 */
private fun DrawScope.drawHideGlyph(color: Color) {
    val s = size.minDimension
    val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
    val eye = Path().apply {
        moveTo(s * 0.10f, s * 0.50f)
        cubicTo(s * 0.30f, s * 0.20f, s * 0.70f, s * 0.20f, s * 0.90f, s * 0.50f)
        cubicTo(s * 0.70f, s * 0.80f, s * 0.30f, s * 0.80f, s * 0.10f, s * 0.50f)
        close()
    }
    drawPath(eye, color, style = stroke)
    drawCircle(color, radius = s * 0.11f, center = Offset(s * 0.50f, s * 0.50f))
    // The bar crosses the whole glyph: half a stroke reads as a scratch on the
    // screen rather than as a deliberate mark.
    drawLine(
        color,
        start = Offset(s * 0.16f, s * 0.84f),
        end = Offset(s * 0.84f, s * 0.16f),
        strokeWidth = s * 0.09f,
        cap = StrokeCap.Round
    )
}
