package eu.emufii.app.ui.screens.library

import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import eu.emufii.app.ui.Motion
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import eu.emufii.app.library.Rom
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val CAROUSEL_CARD_FRACTION = 0.38f

private val CAROUSEL_TITLE_ROOM = 66.dp

/** A draw offset, not a layout one: the active card's 7% scale and ring would otherwise reflow the row. */
private val CAROUSEL_TITLE_DROP = 13.dp

private const val CAROUSEL_TILE_BAND = 0.055f

@Composable
internal fun RomsCarousel(
    entries: List<Entry>,
    onSelect: (Entry) -> Unit,
    onLongPress: (Rom) -> Unit,
    menuFor: Rom?,
    onMenuAction: (Rom, TileAction) -> Unit,
    onDismissMenu: () -> Unit,
    onExitTop: (HeaderSide) -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean,
    gridFocus: FocusRequester,
    contentPadding: PaddingValues,
    startAt: Int = 0,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startAt)
    val scope = rememberCoroutineScope()
    val cursorState = rememberSaveable { mutableIntStateOf(startAt) }
    var cursor by cursorState
    val padFocusedState = remember { mutableStateOf(false) }
    var padFocused by padFocusedState
    PublishHovered(entries, cursorState)

    LaunchedEffect(entries.size) {
        if (cursor > entries.lastIndex) cursor = entries.lastIndex.coerceAtLeast(0)
    }
    LaunchedEffect(Unit) { runCatching { gridFocus.requestFocus() } }
    LaunchedEffect(menuFor) {
        if (menuFor == null) runCatching { gridFocus.requestFocus() }
    }

    // Centre-following must be off while we scroll, or a double press computes from a passing card.
    var settling by remember { mutableStateOf(false) }

    val revealId = remember { mutableIntStateOf(0) }

    fun reveal(index: Int) {
        // Raised here, not in the coroutine: a launch lands a frame late, with the old card still centred.
        settling = true
        val id = ++revealId.intValue
        scope.launch {
            val info = listState.layoutInfo
            // Leading padding is already in animateScrollToItem's frame; passing it again doubles it.
            val viewport = info.viewportEndOffset - info.viewportStartOffset
            val itemWidth = info.visibleItemsInfo.firstOrNull()?.size ?: 0
            val offset = ((viewport - itemWidth) / 2 - info.beforeContentPadding)
            try {
                listState.animateScrollToItem(index, -offset)
            } finally {
                // Only the latest reveal hands the row back to the centre-follower.
                if (revealId.intValue == id) settling = false
            }
        }
    }

    val centred by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2 - middle) }
                ?.index
        }
    }

    LaunchedEffect(centred, settling) {
        val index = centred
        if (!settling && index != null && index in entries.indices) cursor = index
    }

    var dragged by remember { mutableStateOf(false) }
    LaunchedEffect(listState.interactionSource) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) dragged = true
        }
    }

    // Left where a fling ends, the row rests between two cards.
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && dragged) {
            dragged = false
            centred?.let { if (it in entries.indices) reveal(it) }
        }
    }

    fun moveTo(index: Int): Boolean {
        if (index !in entries.indices) return false
        cursor = index
        reveal(index)
        return true
    }

    val exitBottom = LocalLibraryExitBottom.current

    val hold = rememberConfirmHold()
    val onKey = entryKeys(
        entries = entries,
        cursorIndex = { cursor },
        onSelect = onSelect,
        onLongPress = onLongPress,
        onBack = onBack,
        canGoBack = canGoBack,
        hold = hold
    ) { key ->
        when (key) {
            Key.DirectionLeft -> moveTo(cursor - 1)
            Key.DirectionRight -> moveTo(cursor + 1)
            Key.DirectionUp -> {
                onExitTop(HeaderSide.RIGHT); true
            }
            // Consumed either way, or Compose's traversal hunts for a focusable elsewhere.
            Key.DirectionDown -> {
                exitBottom(); true
            }
            else -> null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(gridFocus)
            .onFocusChanged { padFocused = it.hasFocus }
            .focusable()
            .onPreviewKeyEvent(onKey),
        contentAlignment = Alignment.Center
    ) {
        val free = maxHeight -
            contentPadding.calculateTopPadding() -
            contentPadding.calculateBottomPadding() -
            CAROUSEL_TITLE_ROOM
        val cardSize = minOf(maxWidth * CAROUSEL_CARD_FRACTION, free)
            .coerceIn(120.dp, 300.dp)

        val sidePad = ((maxWidth - cardSize) / 2).coerceAtLeast(16.dp)

        LaunchedEffect(cardSize) { reveal(cursor) }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(
                start = sidePad,
                end = sidePad,
                top = contentPadding.calculateTopPadding() + CAROUSEL_TITLE_ROOM / 2,
                bottom = (contentPadding.calculateBottomPadding() - CAROUSEL_TITLE_ROOM / 2)
                    .coerceAtLeast(16.dp)
            ),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(count = entries.size, key = { entries[it].key }) { i ->
                val entry = entries[i]
                // Without it one cursor step recomposes every visible card to change two.
                val active by remember(i) { derivedStateOf { i == cursorState.intValue } }
                val recede by animateFloatAsState(
                    targetValue = if (active) 1f else 0.86f,
                    animationSpec = Motion.morph(),
                    label = "carousel-recede"
                )
                Box(modifier = Modifier.width(cardSize)) {
                    val onTap = { if (active) onSelect(entry) else moveTo(i); Unit }
                    when (entry) {
                        is Entry.Folder -> FolderTile(
                            folder = entry,
                            onClick = onTap,
                            selected = padFocused && active,
                            padHeld = hold.down && active,
                            band = CAROUSEL_TILE_BAND,
                            rest = { recede }
                        )

                        is Entry.Game -> RomTile(
                            rom = entry.rom,
                            onClick = onTap,
                            onLongClick = { onLongPress(entry.rom) },
                            selected = padFocused && active,
                            padHeld = hold.down && active,
                            menuOpen = menuFor?.uri == entry.rom.uri,
                            onChangeIcon = { onMenuAction(entry.rom, TileAction.ICON) },
                            onRename = { onMenuAction(entry.rom, TileAction.RENAME) },
                            onHide = { onMenuAction(entry.rom, TileAction.HIDE) },
                            onDismissMenu = onDismissMenu,
                            titleDrop = CAROUSEL_TITLE_DROP,
                            band = CAROUSEL_TILE_BAND,
                            rest = { recede }
                        )
                    }
                }
            }
        }
    }
}
