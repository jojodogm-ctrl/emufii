package eu.emufii.app.ui.components

import eu.emufii.app.ui.theme.LocalShadowFade
import eu.emufii.app.ui.ShadowsFollow
import androidx.compose.ui.graphics.CompositingStrategy
import eu.emufii.app.ui.theme.liftShadow
import eu.emufii.app.ui.Motion
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
import eu.emufii.app.ui.theme.CardShape
import eu.emufii.app.ui.theme.InkText
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.PlateDark
import eu.emufii.app.ui.theme.PlateLight

/** The side is chosen at runtime: a tile in the right-hand half opens left, or its menu folds against the border. */
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
    val tail = accent ?: MaterialTheme.colorScheme.primary
    val surface = if (dark) PlateDark else PlateLight

    // Filled in at the first measure, before the card is visible: it decides the side, and
    // therefore the animation's origin.
    val placement = remember { SidePlacement() }

    // The window outlives the request to close, long enough for the unroll to reverse: a
    // parent removing the component on the click leaves nothing to animate, so the menu
    // decides its own disappearance.
    var present by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (expanded) present = true }
    if (!present) return

    Popup(
        popupPositionProvider = placement,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        // Always "closed" first, even when the menu is born open: an animated value
        // starting at its target does not animate, and the card appears all at once.
        var appeared by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appeared = true }
        val opening = expanded && appeared

        val openSpec = Motion.morph<Float>()
        val closeSpec = Motion.exit<Float>()
        // An unroll, not a scale: the former `scaleIn` grew the card already whole from
        // 82 %, so it existed before it had arrived.
        val reveal by animateFloatAsState(
            targetValue = if (opening) 1f else 0f,
            // The exit is not the entrance reversed: opening presents something to read,
            // closing only frees the screen, so it goes quickly and without bounce.
            animationSpec = if (opening) openSpec else closeSpec,
            // The window is withdrawn only once the unroll has closed back up.
            finishedListener = { if (!opening) present = false },
            label = "menu-reveal"
        )

        // Re-read every frame: the window is measured only after the first composition,
        // so the side is not known before then.
        val openLeft = placement.openLeft

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .graphicsLayer {
                    // Opacity runs ahead of the sweep: without it the first pixel revealed
                    // arrives at full strength and snaps.
                    alpha = (reveal * 1.8f).coerceAtMost(1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    translationX =
                        (1f - reveal) * SLIDE.toPx() * (if (openLeft) 1f else -1f)
                }
                // The size must not move: the window is placed from its size, so animating
                // it would slide the card every frame. The drawing is clipped, not the layout.
                .drawWithContent {
                    val shown = size.width * reveal
                    val left = if (openLeft) size.width - shown else 0f
                    clipRect(left = left, top = 0f, right = left + shown, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            if (openLeft) {
                ShadowsFollow({ (reveal * 1.8f).coerceAtMost(1f) }) {
                    MenuCard(title, changeIconLabel, renameLabel, hideLabel, surface, dark, onChangeIcon, onRename, onHide)
                }
                Tail(tail, pointsLeft = false)
            } else {
                Tail(tail, pointsLeft = true)
                ShadowsFollow({ (reveal * 1.8f).coerceAtMost(1f) }) {
                    MenuCard(title, changeIconLabel, renameLabel, hideLabel, surface, dark, onChangeIcon, onRename, onHide)
                }
            }
        }
    }
}

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
    val shape = CardShape
    Column(
        modifier = Modifier
            .width(206.dp)
            .liftShadow(shape, 16.dp, dark, fade = LocalShadowFade.current)
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
        TrayMenuRow(label = changeIconLabel, onClick = onChangeIcon, glyph = { drawImageGlyph(it) })
        TrayMenuRow(label = renameLabel, onClick = onRename, glyph = { drawPencilGlyph(it) })
        TrayMenuRow(label = hideLabel, onClick = onHide, glyph = { drawHideGlyph(it) })
    }
}

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
 * The project ships no icon library, and pulling one in for two symbols would grow the
 * package more than these twenty lines do; drawn by hand, their weight answers Poppins'.
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
    // The ridge line: what makes this read as "picture" rather than "frame".
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
    drawLine(
        color,
        start = Offset(s * 0.20f, s * 0.86f),
        end = Offset(s * 0.62f, s * 0.86f),
        strokeWidth = s * 0.09f,
        cap = StrokeCap.Round
    )
}

/**
 * Places the card on whichever flank has the room, and remembers the side so the animation
 * starts from the right edge: growing from the wrong one reads as fleeing the tile.
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

        // Centred on the tile, then pulled back into the screen: on the bottom row a
        // centred card spills under the navigation bar.
        val y = anchorBounds.center.y - popupContentSize.height / 2
        return IntOffset(
            x.coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap)),
            y.coerceIn(gap, (windowSize.height - popupContentSize.height - gap).coerceAtLeast(gap))
        )
    }
}

/**
 * An eye, not a bin: nothing here deletes a file. The game leaves the grid and stays on
 * the card.
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
    drawLine(
        color,
        start = Offset(s * 0.16f, s * 0.84f),
        end = Offset(s * 0.84f, s * 0.16f),
        strokeWidth = s * 0.09f,
        cap = StrokeCap.Round
    )
}
