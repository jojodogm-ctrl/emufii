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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import eu.emufii.app.R
import eu.emufii.app.library.LibraryLayout
import eu.emufii.app.library.LibrarySort
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.PlateDark
import eu.emufii.app.ui.theme.PlateLight

/**
 * The library's two settings, in the same family of pills as the profile and the
 * friends.
 *
 * They took the logo's place in the top left, and it is a better use of the
 * corner: a wordmark does nothing, whereas these two buttons change what is in
 * front of you. The top bar now reads as "what I am looking at" on the left,
 * "who I am" on the right.
 *
 * The glyph shows the state, not the function. The display pill draws the
 * current layout rather than a generic settings icon: without that, nothing on
 * screen would say which mode you are in once the menu is closed, and in
 * carousel, where only one game is visible, that is precisely the question you
 * ask.
 */
@Composable
fun LayoutChip(
    current: LibraryLayout,
    onPick: (LibraryLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TopBarChip(onClick = { open = true }) {
            val tint = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.size(21.dp)) { drawLayoutGlyph(current, tint) }
        }
        ChipMenu(
            expanded = open,
            title = stringResource(R.string.lib_layout),
            onDismiss = { open = false }
        ) {
            LibraryLayout.entries.forEach { layout ->
                ChipMenuRow(
                    label = stringResource(layout.labelRes),
                    selected = layout == current,
                    onClick = { open = false; onPick(layout) },
                    glyph = { tint -> drawLayoutGlyph(layout, tint) }
                )
            }
        }
    }
}

@Composable
fun SortChip(
    current: LibrarySort,
    onPick: (LibrarySort) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TopBarChip(onClick = { open = true }) {
            val tint = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.size(21.dp)) { drawSortGlyph(current, tint) }
        }
        ChipMenu(
            expanded = open,
            title = stringResource(R.string.lib_sort),
            onDismiss = { open = false }
        ) {
            LibrarySort.entries.forEach { sort ->
                ChipMenuRow(
                    label = stringResource(sort.labelRes),
                    selected = sort == current,
                    onClick = { open = false; onPick(sort) },
                    glyph = { tint -> drawSortGlyph(sort, tint) }
                )
            }
        }
    }
}

private val LibraryLayout.labelRes: Int
    get() = when (this) {
        LibraryLayout.GRID -> R.string.lib_layout_grid
        LibraryLayout.CAROUSEL -> R.string.lib_layout_carousel
        LibraryLayout.LIST -> R.string.lib_layout_list
    }

private val LibrarySort.labelRes: Int
    get() = when (this) {
        LibrarySort.NAME -> R.string.lib_sort_name
        LibrarySort.RECENT -> R.string.lib_sort_recent
        LibrarySort.CONSOLE -> R.string.lib_sort_console
    }

/**
 * The card that unrolls under a pill.
 *
 * The same material and the same movement as [TileMenu], a rounded card revealed
 * by a sweep from the edge touching its point of origin, here the top. Reusing
 * its animation rather than inventing a second one is not thrift: two menus that
 * open differently within the same screen read as two mechanisms, when there is
 * only one.
 *
 * The window outlives the close long enough for the unroll to reverse: if the
 * parent removed it the instant of the click, there would be nothing left to
 * animate.
 */
@Composable
private fun ChipMenu(
    expanded: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val dark = LocalEmufiiDarkTheme.current
    val surface = if (dark) PlateDark else PlateLight

    var present by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (expanded) present = true }
    if (!present) return

    Popup(
        popupPositionProvider = BelowChip,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        var appeared by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appeared = true }
        val opening = expanded && appeared

        val reveal by animateFloatAsState(
            targetValue = if (opening) 1f else 0f,
            animationSpec =
                if (opening) spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                else tween(130, easing = FastOutLinearInEasing),
            finishedListener = { if (!opening) present = false },
            label = "chip-menu-reveal"
        )

        val shape = RoundedCornerShape(22.dp)
        Column(
            modifier = Modifier
                .graphicsLayer {
                    alpha = (reveal * 1.8f).coerceAtMost(1f)
                    translationY = (1f - reveal) * (-10.dp.toPx())
                }
                // The drawing is clipped, not the layout: the window is placed
                // according to its size, so animating it would make it slide at
                // every frame.
                .drawWithContent {
                    clipRect(bottom = size.height * reveal) {
                        this@drawWithContent.drawContent()
                    }
                }
                .width(210.dp)
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
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 6.dp)
            )
            content()
        }
    }
}

/**
 * A menu entry, with a tick on the current option.
 *
 * A tick and not a coloured background: these menus have three lines, they are
 * read at a glance, and a tinted background on the active line would conflict
 * with the focus highlight, which moves. Two superimposed highlights read as one,
 * badly placed.
 */
@Composable
private fun ChipMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    glyph: DrawScope.(Color) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val highlighted = pressed || focused
    val tint = MaterialTheme.colorScheme.onSurface
    // Read here and not inside the Canvas: the drawing lambda is not composable,
    // so the theme is not reachable from it.
    val checkTint = MaterialTheme.colorScheme.primary

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
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Canvas(Modifier.size(14.dp)) { drawCheckGlyph(checkTint) }
        } else {
            Spacer(Modifier.size(14.dp))
        }
    }
}

/** Under the pill, aligned on its left edge, and pulled back into the screen. */
private object BelowChip : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val gap = 10
        val x = anchorBounds.left
        val y = anchorBounds.bottom + gap
        return IntOffset(
            x.coerceIn(gap, (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap)),
            y.coerceIn(gap, (windowSize.height - popupContentSize.height - gap).coerceAtLeast(gap))
        )
    }
}

/**
 * The glyphs, drawn by hand like the tile menu's.
 *
 * Each one draws its own layout: three squares for the grid, a large card
 * flanked by two slices for the carousel, thumbnailed lines for the list. An
 * abstract icon (a cog, three dots) would force the menu open just to find out
 * where you are.
 */
private fun DrawScope.drawLayoutGlyph(layout: LibraryLayout, color: Color) {
    val s = size.minDimension
    when (layout) {
        LibraryLayout.GRID -> {
            // Four solid squares: at this size, a 1 px outline on a 7 px cell
            // closes up into a blob. Solid stays crisp.
            val cell = s * 0.40f
            val gapv = s * 0.20f
            listOf(0f to 0f, (cell + gapv) to 0f, 0f to (cell + gapv), (cell + gapv) to (cell + gapv))
                .forEach { (x, y) ->
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(s * 0.09f)
                    )
                }
        }

        LibraryLayout.CAROUSEL -> {
            // The side slices are dimmed: that is what the carousel itself does,
            // and the glyph says so without words.
            val cardW = s * 0.46f
            val sideW = s * 0.16f
            drawRoundRect(
                color = color.copy(alpha = 0.35f),
                topLeft = Offset(0f, s * 0.24f),
                size = Size(sideW, s * 0.52f),
                cornerRadius = CornerRadius(s * 0.06f)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset((s - cardW) / 2f, s * 0.12f),
                size = Size(cardW, s * 0.76f),
                cornerRadius = CornerRadius(s * 0.10f)
            )
            drawRoundRect(
                color = color.copy(alpha = 0.35f),
                topLeft = Offset(s - sideW, s * 0.24f),
                size = Size(sideW, s * 0.52f),
                cornerRadius = CornerRadius(s * 0.06f)
            )
        }

        LibraryLayout.LIST -> {
            val rowH = s * 0.20f
            repeat(3) { i ->
                val y = i * (rowH + s * 0.14f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, y),
                    size = Size(rowH, rowH),
                    cornerRadius = CornerRadius(s * 0.05f)
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(rowH + s * 0.14f, y + rowH * 0.28f),
                    size = Size(s - rowH - s * 0.14f, rowH * 0.44f),
                    cornerRadius = CornerRadius(rowH * 0.22f)
                )
            }
        }
    }
}

/**
 * Sorting, in three symbols.
 *
 * A-Z and date share the descending bar scale, the universal sign for sorting,
 * and are told apart by what accompanies them: nothing for alphabetical order, a
 * clock for date. "By console" is a folder, because it is not an order but a
 * filing, and the glyph has to say so before it is tried.
 */
private fun DrawScope.drawSortGlyph(sort: LibrarySort, color: Color) {
    val s = size.minDimension
    val stroke = Stroke(width = s * 0.11f, cap = StrokeCap.Round)

    fun bars(widths: List<Float>, right: Float) {
        widths.forEachIndexed { i, w ->
            val y = s * (0.22f + i * 0.28f)
            drawLine(
                color,
                start = Offset(s * 0.06f, y),
                end = Offset(s * 0.06f + (right - s * 0.06f) * w, y),
                strokeWidth = s * 0.11f,
                cap = StrokeCap.Round
            )
        }
    }

    when (sort) {
        LibrarySort.NAME -> bars(listOf(1f, 0.68f, 0.36f), s * 0.94f)

        LibrarySort.RECENT -> {
            // The bars tighten up to give the clock room to live: without that
            // the two drawings touch and the whole thing becomes a blob.
            bars(listOf(1f, 0.66f, 0.32f), s * 0.56f)
            val c = Offset(s * 0.76f, s * 0.74f)
            val r = s * 0.21f
            drawCircle(color, radius = r, center = c, style = stroke)
            drawLine(
                color,
                start = c,
                end = Offset(c.x, c.y - r * 0.55f),
                strokeWidth = s * 0.10f,
                cap = StrokeCap.Round
            )
            drawLine(
                color,
                start = c,
                end = Offset(c.x + r * 0.5f, c.y),
                strokeWidth = s * 0.10f,
                cap = StrokeCap.Round
            )
        }

        LibrarySort.CONSOLE -> {
            // A folder: the tab first, then the body, so the step at the top
            // reads at 18 px.
            val path = Path().apply {
                moveTo(s * 0.08f, s * 0.80f)
                lineTo(s * 0.08f, s * 0.26f)
                lineTo(s * 0.40f, s * 0.26f)
                lineTo(s * 0.50f, s * 0.40f)
                lineTo(s * 0.92f, s * 0.40f)
                lineTo(s * 0.92f, s * 0.80f)
                close()
            }
            drawPath(path, color, style = stroke)
        }
    }
}

private fun DrawScope.drawCheckGlyph(color: Color) {
    val s = size.minDimension
    val path = Path().apply {
        moveTo(s * 0.14f, s * 0.54f)
        lineTo(s * 0.40f, s * 0.80f)
        lineTo(s * 0.88f, s * 0.22f)
    }
    drawPath(path, color, style = Stroke(width = s * 0.16f, cap = StrokeCap.Round))
}
