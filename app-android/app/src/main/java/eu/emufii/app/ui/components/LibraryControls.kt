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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import eu.emufii.app.ui.components.LandOn
import eu.emufii.app.ui.components.CheckIcon
import eu.emufii.app.ui.components.CrossIcon
import eu.emufii.app.ui.components.LensMark
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import eu.emufii.app.R
import eu.emufii.app.library.LibraryLayout
import eu.emufii.app.library.LibrarySort
import eu.emufii.app.ui.theme.CardShape
import eu.emufii.app.ui.theme.InkText
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.PlateDark
import eu.emufii.app.ui.theme.PlateLight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.ui.graphics.SolidColor
import eu.emufii.app.ui.theme.PillShape
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.LocalGlassPane
import eu.emufii.app.ui.glass
import eu.emufii.app.ui.theme.shelfFill
import eu.emufii.app.ui.tap
import eu.emufii.app.ui.focusRing
import eu.emufii.app.ui.theme.edgeColor

/**
 * The glyph shows the state, not the function.
 * pourquoi : docs/decisions/bibliotheque.md § The two library settings took the logo's corner
 */
@Composable
fun LayoutChip(
    current: LibraryLayout,
    onPick: (LibraryLayout) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Boolean) -> Unit = {}
) {
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        BankCell(onClick = { open = true }, onFocused = onFocused) {
            val tint = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.size(21.dp)) { drawLayoutGlyph(current, tint) }
        }
        ChipMenu(
            expanded = open,
            title = stringResource(R.string.lib_layout),
            onDismiss = { open = false }
        ) {
            LibraryLayout.entries.forEach { layout ->
                val selected = layout == current
                TrayMenuRow(
                    label = stringResource(layout.labelRes),
                    onClick = { open = false; onPick(layout) },
                    glyph = { tint -> drawLayoutGlyph(layout, tint) },
                    landing = LocalMenuLanding.current.takeIf { selected },
                    trailing = {
                        // A tick, not a coloured ground: two highlights on top of each
                        // other read as one, misplaced.
                        if (selected) CheckIcon(size = 14.dp, color = MaterialTheme.colorScheme.primary)
                        else Spacer(Modifier.size(14.dp))
                    }
                )
            }
        }
    }
}

@Composable
fun SortChip(
    current: LibrarySort,
    onPick: (LibrarySort) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Boolean) -> Unit = {}
) {
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        BankCell(onClick = { open = true }, onFocused = onFocused) {
            val tint = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.size(21.dp)) { drawSortGlyph(current, tint) }
        }
        ChipMenu(
            expanded = open,
            title = stringResource(R.string.lib_sort),
            onDismiss = { open = false }
        ) {
            LibrarySort.entries.forEach { sort ->
                val selected = sort == current
                TrayMenuRow(
                    label = stringResource(sort.labelRes),
                    onClick = { open = false; onPick(sort) },
                    glyph = { tint -> drawSortGlyph(sort, tint) },
                    landing = LocalMenuLanding.current.takeIf { selected },
                    trailing = {
                        // A tick, not a coloured ground: two highlights on top of each
                        // other read as one, misplaced.
                        if (selected) CheckIcon(size = 14.dp, color = MaterialTheme.colorScheme.primary)
                        else Spacer(Modifier.size(14.dp))
                    }
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
 * The open menu's cursor holder, claimed by the active row.
 * pourquoi : docs/decisions/bibliotheque.md § A menu's cursor lands on the current option
 */
private val LocalMenuLanding = compositionLocalOf<FocusRequester?> { null }

/**
 * The window outlives the close while the unroll reverses.
 * pourquoi : docs/decisions/bibliotheque.md § A menu's cursor lands on the current option
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

        // Without this there was no ring at all on opening.
        // pourquoi : docs/decisions/bibliotheque.md § A menu's cursor lands on the current option
        // pourquoi : docs/decisions/coquille-ecrans.md § The cursor arrives with the screen
        val landing = remember { FocusRequester() }
        LandOn(landing, key = title, enabled = opening)

        val shape = CardShape
        Column(
            modifier = Modifier
                .graphicsLayer {
                    alpha = (reveal * 1.8f).coerceAtMost(1f)
                    translationY = (1f - reveal) * (-10.dp.toPx())
                }
                // The drawing is clipped, not the layout: the window is placed on its
                // size, and animating it would make it slide at every frame.
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
                    ambientColor = InkText.copy(alpha = 0.10f),
                    spotColor = InkText.copy(alpha = 0.14f)
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
            CompositionLocalProvider(LocalMenuLanding provides landing) { content() }
        }
    }
}

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
 * An abstract icon would force opening the menu to get your bearings.
 * pourquoi : docs/decisions/bibliotheque.md § The two library settings took the logo's corner
 */
private fun DrawScope.drawLayoutGlyph(layout: LibraryLayout, color: Color) {
    val s = size.minDimension
    when (layout) {
        LibraryLayout.GRID -> {
            // At this size a 1 px outline on a 7 px cell closes up into a blob.
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
            // The side slices are dimmed: that is what the carousel itself does.
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
 * By console is a folder: not an order but a filing.
 * pourquoi : docs/decisions/bibliotheque.md § The two library settings took the logo's corner
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
            // The bars tighten to give the clock room: otherwise the two drawings touch.
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
            // The tab first, then the body, so the step at the top reads at 18 px.
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

/**
 * The glyph is the field's magnifier: button and field are one control in two states.
 * pourquoi : docs/decisions/bibliotheque.md § Search, and the cross that closes it
 */
/** What the bar opens out to. Read by the header's size transition as well. */
val SEARCH_WIDTH = 430.dp

@Composable
fun SearchChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Boolean) -> Unit = {}
) {
    BankCell(onClick = onClick, modifier = modifier, onFocused = onFocused) {
        val tint = MaterialTheme.colorScheme.onSurface
        LensMark(size = 21.dp, color = tint)
    }
}

/**
 * The cross is the only control that ends the search. The system keyboard writes here:
 * the app's own keypad only avoided landscape's extract mode.
 * pourquoi : docs/decisions/bibliotheque.md § Search, and the cross that closes it
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dark = LocalEmufiiDarkTheme.current
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        runCatching { field.requestFocus() }
        keyboard?.show()
    }
    val pane = LocalGlassPane.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .height(40.dp)
            // The room the search takes when it opens: wide enough for a game's name,
            // and the spacer on its left gives way, so the bar reads as stretching out
            // of the tool bank rather than appearing beside it.
            // pourquoi : docs/decisions/bibliotheque.md § Search takes the shelf, and the two states do not cross
            .width(SEARCH_WIDTH)
            // Glass, like everything else standing on the header. A plate here was the
            // one opaque object left on the pebble.
            .then(
                if (pane != null) {
                    Modifier.glass(pane, PillShape, dark, thickness = 0.35f, lift = 3.dp)
                } else {
                    Modifier.plate(
                        PillShape,
                        dark,
                        LocalEmufiiOledTheme.current,
                        lift = 3.dp,
                        bevel = false,
                        fill = shelfFill(dark, LocalEmufiiOledTheme.current && dark)
                    )
                }
            )
            .padding(start = 14.dp, end = 6.dp)
    ) {
        val tint = MaterialTheme.colorScheme.onSurface
        val raise = Modifier.tap(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                runCatching { field.requestFocus() }
                keyboard?.show()
                onTap()
            }
        )
        LensMark(size = 19.dp, color = tint, modifier = raise)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // Search closes the keyboard, not the search: the list filters on every keystroke.
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = tint),
            cursorBrush = SolidColor(tint),
            modifier = Modifier.weight(1f).focusRequester(field).then(raise)
        ) {
            if (value.isEmpty()) {
                Text(
                    stringResource(R.string.lib_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            it()
        }
        // The area grows, the glyph does not: 32 dp is what a 36 dp bar allows.
        // pourquoi : docs/decisions/bibliotheque.md § Search, and the cross that closes it
        Box(
            modifier = Modifier
                .size(32.dp)
                .tap(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            CrossIcon(size = 18.dp, color = tint)
        }
    }
}

/**
 * The three library settings as one piece with three cells, the way a console puts its
 * switches on one plate. Each cell shows its own state; the separators are hairlines
 * that stop short of the edges, or the bank reads as three buttons again.
 * pourquoi : docs/decisions/bibliotheque.md § The tools are one bank, the people are discs
 */
@Composable
fun ToolBank(content: @Composable () -> Unit) {
    val dark = LocalEmufiiDarkTheme.current
    // Glass when it stands on glass, plastic everywhere else: the bank is used outside
    // the library's header too, where there is no pane to refract.
    val pane = LocalGlassPane.current
    Row(
        modifier = Modifier
            .then(
                if (pane != null) {
                    // A third of the header's bend: the same figures on a shape this
                    // small read as a much thicker glass.
                    Modifier.glass(pane, PillShape, dark, thickness = 0.35f, lift = 3.dp)
                } else {
                    Modifier.plate(
                        shape = PillShape,
                        dark = dark,
                        oled = LocalEmufiiOledTheme.current,
                        lift = 3.dp,
                        bevel = false,
                        fill = shelfFill(dark, LocalEmufiiOledTheme.current && dark)
                    )
                }
            )
            // The plate clips: without this margin a cell touches the contour and the
            // cursor's glow is cut off square along it.
            .padding(BANK_MARGIN),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/** Between two cells, never at the ends. */
@Composable
fun BankSeam() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .background(edgeColor(LocalEmufiiDarkTheme.current, LocalEmufiiOledTheme.current))
    )
}

/**
 * A cell does not scale under the thumb: it sits in a plate that would have to scale
 * with it. It darkens instead.
 */
@Composable
private fun BankCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocused(focused) }

    Box(
        modifier = modifier
            .size(width = CELL_WIDTH, height = CELL_HEIGHT)
            .tap(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(CURSOR_INSET)
                .focusRing(focused, CellShape, width = 2.dp, glowRadius = 4.dp)
                .clip(CellShape)
                .then(
                    if (pressed) {
                        Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f))
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) { content() }
    }
}

private val CELL_WIDTH = 54.dp
private val CELL_HEIGHT = 44.dp

/**
 * What the bank keeps clear inside its contour: the plate clips, so this has to cover
 * the cursor's glow whole or the halo comes out cut off square along the pill.
 */
private val BANK_MARGIN = 6.dp

/** The cursor stays off the bank's contour: drawn on it, it cuts the piece in three. */
private val CURSOR_INSET = 1.dp

/** One cursor for the three cells, and it is the pill the rest of the header wears. */
private val CellShape = RoundedCornerShape(percent = 50)
