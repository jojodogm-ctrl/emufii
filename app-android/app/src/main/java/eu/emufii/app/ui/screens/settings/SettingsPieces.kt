package eu.emufii.app.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import eu.emufii.app.ui.EntryScroll
import eu.emufii.app.ui.LocalEntryScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.emufii.app.library.Console
import eu.emufii.app.library.Rom
import eu.emufii.app.library.emulatorInfo
import eu.emufii.app.ui.components.CardBounds
import eu.emufii.app.ui.components.ChevronRight
import eu.emufii.app.ui.components.LocalCardBounds
import eu.emufii.app.ui.components.cardSliceFill
import eu.emufii.app.ui.components.RomArtwork
import eu.emufii.app.ui.components.DetailTone
import eu.emufii.app.ui.components.EmufiiScaffold
import eu.emufii.app.ui.components.SoftCard
import eu.emufii.app.ui.components.padEntry
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.theme.ArtworkShape
import eu.emufii.app.ui.theme.CardShape
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.ErrorDark
import eu.emufii.app.ui.theme.ErrorLight
import eu.emufii.app.ui.theme.GoodDark
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.InfoDark
import eu.emufii.app.ui.theme.InfoLight
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.Teal
import eu.emufii.app.ui.theme.WarnDark
import eu.emufii.app.ui.theme.WarnLight
import eu.emufii.app.ui.theme.plateColors
import eu.emufii.app.ui.components.StateBead
import eu.emufii.app.ui.theme.socket
import eu.emufii.app.ui.tap

/**
 * The settings' shared pieces: a page shell, a content block, a hub entry, a state pill.
 * pourquoi : docs/decisions/reglages-ecran.md § One hub and seven pages, plus an accordion
 */

/**
 * Past this a settings row stops reading as one thing.
 * pourquoi : docs/decisions/reglages-ecran.md § The three shape constants of a row
 */
internal val SETTINGS_MAX_WIDTH = 620.dp

internal val ROW_INSET = 18.dp

internal val ROW_SHAPE = RoundedCornerShape(14.dp)

/**
 * For gestures with no undo: the theme's error, coral-leaning, never a hardcoded hex.
 * pourquoi : docs/decisions/theme-duotone-shelves.md § Semantics (centralised)
 */
internal val DANGER = ErrorLight

@Composable
internal fun dangerInk(): Color = if (LocalEmufiiDarkTheme.current) ErrorDark else ErrorLight

/**
 * Which axis a hub entry belongs to: teal for system, coral for social.
 * pourquoi : docs/decisions/theme-duotone-shelves.md § Settings
 */
internal enum class EntryDomain { SYSTEM, SOCIAL }

@Composable
internal fun domainInk(domain: EntryDomain): Color {
    val dark = LocalEmufiiDarkTheme.current
    return when (domain) {
        EntryDomain.SYSTEM -> if (dark) Teal.darkBright else Teal.deep
        EntryDomain.SOCIAL -> if (dark) Coral.darkBright else Coral.deep
    }
}

/**
 * Below this a page stays on one column.
 * pourquoi : docs/decisions/reglages-ecran.md § Two columns, once the accordion is gone
 */
private val TWO_COLUMN_FROM = 700.dp

private val ONE_COLUMN_MAX = SETTINGS_MAX_WIDTH

private val TWO_COLUMN_MAX = 980.dp

/**
 * The shell and a bounded column, or two when the screen carries them.
 * pourquoi : docs/decisions/reglages-ecran.md § Two columns, once the accordion is gone
 */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Laid at the end of the title: the state of a page with one subject.
     * pourquoi : docs/decisions/reglages-ecran.md § What sits at the end of a page title
     */
    trailing: (@Composable () -> Unit)? = null,
    /**
     * True for a page whose content fits the screen: it is then centred rather than hung
     * from the top, so no scroll appears where there is nothing to reach. The floor is a
     * minimum, never a ceiling: content that outgrows the screen scrolls, or the last
     * button ends up sliced off the bottom edge with no way down to it.
     */
    centred: Boolean = false,
    content: @Composable () -> Unit
) {
    EmufiiScaffold(title = title, onBack = onBack, trailing = trailing, modifier = modifier) { topPadding ->
        val pageScroll = rememberScrollState()
        val page = remember(pageScroll) { EntryScroll(pageScroll) }
        CompositionLocalProvider(LocalEntryScroll provides page) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // The screen minus everything laid around the content. Taking the full height
            // as the floor and then adding the margins on top made the column taller than
            // the room it had, so a centred page scrolled by exactly its own margins with
            // nothing down there to reach.
            val room = (
                maxHeight - topPadding - 24.dp -
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ).coerceAtLeast(0.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(pageScroll)
                    // A floor, not a height: it is what lets the arrangement centre, since
                    // a wrapping column has no room to spare, and it still gives way when
                    // the content is taller than the screen.
                    .then(if (centred) Modifier.heightIn(min = room) else Modifier.fillMaxHeight())
                    .padding(horizontal = 20.dp)
                    .padding(top = topPadding, bottom = 24.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement =
                    if (centred) Arrangement.Center else Arrangement.Top
            ) {
                // Bounded to both columns' width; [SettingsColumns] decides how many there are.
                // pourquoi : docs/decisions/reglages-ecran.md § Two columns, once the accordion is gone
                Column(
                    modifier = Modifier.widthIn(max = TWO_COLUMN_MAX).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) { content() }
            }
        }
        }
    }
}

/**
 * Dealt alternately and never cut mid-block.
 * pourquoi : docs/decisions/reglages-ecran.md § Two columns, once the accordion is gone
 */
@Composable
internal fun SettingsColumns(vararg blocks: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < TWO_COLUMN_FROM || blocks.size < 2) {
            // On one column two blocks are no longer side by side: pairing aligns nothing.
            CompositionLocalProvider(LocalBlocksArePaired provides false) {
                Column(
                    modifier = Modifier.widthIn(max = ONE_COLUMN_MAX).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) { blocks.forEach { it() } }
            }
        } else {
            Row(
                modifier = Modifier.widthIn(max = TWO_COLUMN_MAX).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(0, 1).forEach { side ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        blocks.forEachIndexed { index, block ->
                            if (index % 2 == side) block()
                        }
                    }
                }
            }
        }
    }
}

internal val LocalBlocksArePaired = compositionLocalOf { true }

/**
 * They live in two separate `Column`s, so nothing measures them together.
 * pourquoi : docs/decisions/reglages-ecran.md § Aligning two columns means measuring, not intrinsics
 */
@Stable
internal class BlockHeights {
    var tallestPx by mutableIntStateOf(0)
        private set

    fun offer(heightPx: Int) {
        if (heightPx > tallestPx) tallestPx = heightPx
    }
}

@Composable
internal fun rememberBlockHeights(): BlockHeights = remember { BlockHeights() }

/** On the block's `modifier`, never in its content. */
@Composable
internal fun Modifier.sameHeightAs(group: BlockHeights): Modifier {
    if (!LocalBlocksArePaired.current) return this
    val floor = with(LocalDensity.current) { group.tallestPx.toDp() }
    return this
        .heightIn(min = floor)
        // After `heightIn`: what comes back is the held height, the group's maximum.
        .onSizeChanged { group.offer(it.height) }
}

internal data class BlockState(val tone: DetailTone, val label: String)

/**
 * A header carrying the name and the state, then the content, then the explanation.
 * pourquoi : docs/decisions/reglages-ecran.md § On a page, the state comes before the explanation
 */
@Composable
internal fun SettingsBlock(
    /** Null when the page holds one block: repeating its title reads as a nesting level. */
    title: String? = null,
    modifier: Modifier = Modifier,
    state: BlockState? = null,
    mark: (@Composable () -> Unit)? = null,
    /**
     * True when the block fills the height given: header at the top, actions at the bottom.
     * pourquoi : docs/decisions/reglages-ecran.md § Two columns, once the accordion is gone
     */
    spread: Boolean = false,
    /** Passed straight to [SoftCard]; a block that folds open sends a smaller share. */
    bandFraction: Float = 0.12f,
    footer: (@Composable () -> Unit)? = null,
    /**
     * Non-null when the block folds. For what is set once.
     * pourquoi : docs/decisions/reglages-ecran.md § Collapsing is reserved for what you set once
     */
    onToggleExpanded: (() -> Unit)? = null,
    expanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var bounds by remember { mutableStateOf(CardBounds(0f, 0f)) }
    SoftCard(modifier = modifier, onClick = onToggleExpanded, bandFraction = bandFraction) {
        CompositionLocalProvider(LocalCardBounds provides bounds) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        bounds = CardBounds(
                            top = it.positionInRoot().y,
                            height = it.size.height.toFloat()
                        )
                    }
                    .then(if (spread) Modifier.fillMaxHeight() else Modifier)
                    // Folding changes the card's height; without this the next column jumps.
                    .then(if (onToggleExpanded != null) Modifier.animateContentSize() else Modifier)
                    .padding(ROW_INSET),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (title != null || state != null || mark != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        mark?.invoke()
                        Text(
                            title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        // No weight: the pill carried one, and a long label squeezed the title.
                        // pourquoi : docs/decisions/reglages-ecran.md § A state badge carries two words, never a sentence
                        state?.let {
                            Box(modifier = Modifier.widthIn(max = STATE_PILL_MAX)) {
                                StatePill(it.tone, it.label)
                            }
                        }
                        if (onToggleExpanded != null) {
                            val turn by animateFloatAsState(
                                if (expanded) -90f else 90f,
                                label = "block-chevron"
                            )
                            ChevronRight(
                                size = 18.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.rotate(turn)
                            )
                        }
                    }
                }
                content()
                if (footer != null) {
                    // The gap goes between content and footer and nowhere else: that is
                    // what aligns two columns' feet.
                    if (spread) Spacer(Modifier.weight(1f))
                    footer()
                }
            }
        }
    }
}

/**
 * Four technical sentences in a row do not read as a paragraph.
 * pourquoi : docs/decisions/reglages-ecran.md § On a page, the state comes before the explanation
 */
@Composable
internal fun SettingsSteps(vararg steps: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, text -> SettingsStep(index + 1, text) }
    }
}

@Composable
private fun SettingsStep(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Label left, value right, one line, no recess.
 * pourquoi : docs/decisions/reglages-ecran.md § A block fact has no hollow around it
 */
@Composable
internal fun BlockFact(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Red and short: shell red appears twice in the whole app, which is why it carries.
 * pourquoi : docs/decisions/reglages-ecran.md § A warning is not an error, and does not carry the red
 */
@Composable
internal fun BlockCaveat(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

/**
 * A recess, the warning bead, and ordinary ink.
 * pourquoi : docs/decisions/reglages-ecran.md § A warning is not an error, and does not carry the red
 */
@Composable
internal fun BlockNotice(text: String) {
    val dark = LocalEmufiiDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .socket(ROW_SHAPE, dark)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        // Top, not centre: a bead floating mid-note stops reading as its mark.
        verticalAlignment = Alignment.Top
    ) {
        StateBead(DetailTone.WARN, size = 12.dp)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Each is its own plate rather than a row in a shared list.
 * pourquoi : docs/decisions/reglages-ecran.md § A hub entry is a plate, not a row
 * pourquoi : docs/decisions/reglages-ecran.md § The hub is a grid, and the panel shows the selected cell
 */
@Composable
internal fun SettingsEntry(
    label: String,
    summary: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** True for the page's first entry: the pad comes down to it and back up from it. */
    entry: Boolean = false,
    state: EntryState? = null,
    icon: (@Composable (Color) -> Unit)? = null,
    domain: EntryDomain = EntryDomain.SYSTEM,
    leading: (@Composable () -> Unit)? = null,
    /**
     * How the hub tells the second screen which tile is aimed at.
     * pourquoi : docs/decisions/reglages-ecran.md § The hub is a grid, and the panel shows the selected cell
     */
    onFocused: ((Boolean) -> Unit)? = null,
) {
    SoftCard(
        onClick = onOpen,
        // A hub tile is 92 dp tall and the full share made a band you read before the
        // label. Thinner, and the tile is still the thing lit.
        bandFraction = 0.095f,
        modifier = modifier
            .then(if (entry) Modifier.padEntry() else Modifier)
            .then(
                if (onFocused != null) Modifier.onFocusEvent { onFocused(it.hasFocus) }
                else Modifier
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = ROW_INSET, vertical = 11.dp)
        ) {
            if (leading != null) leading()
            else if (icon != null) IconSocket(icon, domain)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            state?.let { StatePill(it.tone, it.label) }
            ChevronRight(size = 18.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal data class EntryState(val tone: DetailTone, val label: String)

/**
 * A bare icon floats; seven floating icons read as a sticker sheet.
 * pourquoi : docs/decisions/theme-duotone-shelves.md § Settings
 */
@Composable
private fun IconSocket(icon: @Composable (Color) -> Unit, domain: EntryDomain) {
    val ink = domainInk(domain)
    val axis: Color = if (domain == EntryDomain.SOCIAL) Coral.bright else Teal.bright
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(axis.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) { icon(ink) }
}

/** Two words fit with margin; this is a guard, not a target. */
private val STATE_PILL_MAX = 190.dp

/**
 * The same vocabulary as [eu.emufii.app.ui.components.DetailStatus]'s bead.
 * pourquoi : docs/decisions/reglages-ecran.md § The hub badge reuses the bead, it does not invent a second one
 */
@Composable
internal fun StatePill(tone: DetailTone, label: String) {
    val dark = LocalEmufiiDarkTheme.current
    val ink = when (tone) {
        DetailTone.GOOD -> if (dark) GoodDark else GoodLight
        DetailTone.BUSY -> if (dark) InfoDark else InfoLight
        DetailTone.WARN -> if (dark) WarnDark else WarnLight
        DetailTone.BAD -> if (dark) ErrorDark else ErrorLight
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.14f))
            .border(1.dp, ink.copy(alpha = 0.35f), CircleShape)
            .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
    ) {
        StateBead(tone, size = 11.dp)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = ink,
            maxLines = 1,
            // See SessionFinderScreen: the default clip slices the glyph, an ellipsis says
            // something is missing.
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    entry: Boolean = false
) {
    val emphasis by animateFloatAsState(if (selected) 1f else 0f, label = "choice-row")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (entry) Modifier.padEntry() else Modifier)
            .controlRing(ROW_SHAPE)
            // The selection tint is translucent: on its own it never made the row opaque.
            .cardSliceFill(
                ROW_SHAPE,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f * emphasis)
            )
            .clip(ROW_SHAPE)
            .tap(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun DangerRow(label: String, onClick: () -> Unit) {
    val danger = dangerInk()
    SoftCard(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ROW_INSET, vertical = 14.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = danger
            )
            Spacer(Modifier.weight(1f))
            ChevronRight(size = 18.dp, color = danger.copy(alpha = 0.6f))
        }
    }
}

/**
 * The installed app's icon, which says whether it is there.
 * pourquoi : docs/decisions/reglages-ecran.md § The pages' images come from the device, not from a stock library
 */
@Composable
internal fun EmulatorMark(console: Console, size: Dp = 34.dp) {
    val context = LocalContext.current
    // Asked once: a launcher icon is often an adaptive drawable, rasterising is not free.
    val info = remember(console) { emulatorInfo(context, console) }
    val dark = LocalEmufiiDarkTheme.current
    Box(
        modifier = Modifier.size(size).socket(ArtworkShape, dark),
        contentAlignment = Alignment.Center
    ) {
        val icon = info.icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(ArtworkShape)
            )
        } else {
            // The console's abbreviation rather than a question mark: an absent emulator
            // is the ordinary case.
            Text(
                console.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Real cover art from the player's own library, so they see what their grid shows.
 * pourquoi : docs/decisions/reglages-ecran.md § The pages' images come from the device, not from a stock library
 */
@Composable
internal fun ArtworkStrip(roms: List<Rom>, modifier: Modifier = Modifier) {
    if (roms.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        roms.forEach { rom ->
            RomArtwork(rom = rom, size = 56.dp)
        }
    }
}
