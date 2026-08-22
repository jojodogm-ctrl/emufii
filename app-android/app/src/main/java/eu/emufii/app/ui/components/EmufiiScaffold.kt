package eu.emufii.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import eu.emufii.app.ui.LocalScaffoldBand
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.focusRing
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import eu.emufii.app.ui.wallpaper.TrayBackdrop
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.PlateDark
import eu.emufii.app.ui.theme.PlateLightLow

/**
 * The two gamepad destinations of a scaffolded screen.
 *
 * Carried by a `CompositionLocal` rather than by the content's signature: every
 * screen has only one control to name, and hoisting it into a parameter would
 * have meant touching every call site for a piece of information a single place
 * in the content uses.
 *
 * [first] must be placed on a genuinely focusable control, never on a container.
 * A focus request on a `focusGroup` succeeds by giving focus to the group
 * itself, which reads as a vanished cursor: that is what defeated three attempts
 * before this one.
 */
class ScaffoldFocus(val first: FocusRequester, val header: FocusRequester)

val LocalScaffoldFocus = compositionLocalOf<ScaffoldFocus?> { null }

/**
 * To be placed on a screen's first control: it becomes the "down" destination
 * from the header, and "up" from it goes back there.
 */
@Composable
fun Modifier.padEntry(): Modifier {
    val focus = LocalScaffoldFocus.current ?: return this
    return this
        .focusRequester(focus.first)
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                runCatching { focus.header.requestFocus() }
                true
            } else {
                false
            }
        }
}

/**
 * The shell every screen sits in.
 *
 * Two jobs. First, system insets: content is handed a [topPadding] that already
 * clears the status bar, so nothing ends up under the clock, the failure this
 * was written to fix. Second, consistency: the same wallpaper, the same
 * floating header, on every screen.
 *
 * The header floats over the content instead of being a bar with a background.
 * A delimited top bar was tried and rejected on this project; the room is meant
 * to feel like a 3DS home screen, where nothing is boxed in.
 *
 * Floating has a cost the first version did not pay: [topPadding] clears the
 * header at rest, but a screen that scrolls sends its content straight under the
 * title, and the two draw on top of each other. So the top band of the wallpaper
 * is drawn a second time above the content, faded out over its lower edge.
 * Content dissolves into the backdrop as it rises instead of colliding with the
 * title, and because it is the same wallpaper at the same size, the pixels match
 * the ones underneath exactly, no seam, and still no box.
 */
@Composable
fun EmufiiScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /**
     * False when the screen fits whole and does not scroll.
     *
     * The veil and the fade margin only exist for content rising under the
     * header. A screen that does not scroll has nothing to dissolve, and the
     * 32 dp reserved for the fade become an empty band; on the Thor's 468 dp
     * that is 7 % of the height paid for nothing.
     */
    contentScrolls: Boolean = true,
    content: @Composable (topPadding: Dp) -> Unit
) {
    val dark = LocalEmufiiDarkTheme.current
    val scaffoldFocus = remember { ScaffoldFocus(FocusRequester(), FocusRequester()) }
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Header height + its vertical padding (2 × 12) + the status bar.
    val band = statusBar + HEADER_HEIGHT + 24.dp

    Box(modifier = modifier.fillMaxSize()) {
        TrayBackdrop(modifier = Modifier.fillMaxSize(), dark = dark)

        /**
         * The header is declared before the content, and drawn on top of it by
         * its `zIndex`.
         *
         * Compose's traversal follows declaration order, and with the content
         * coming first, "down" from the back button had nothing after it. The
         * order is therefore put back the right way round; the drawing does not
         * change, the header floats above the content scrolling underneath.
         *
         * That alone is still not enough to bring the cursor down into the page,
         * and it is worth knowing before coming back to this: three attempts
         * failed to cross the boundary between these two layers of a single
         * `Box`, `focusProperties { down = ... }` on a `focusGroup`, an explicit
         * focus request (which returns `Success(true)` by giving focus to the
         * group itself, not to one of its children), and a `moveFocus(Down)`
         * from the header. Each time `uiautomator dump` showed focus still in
         * the header.
         *
         * What works in this repo, and what the row below does, is
         * `LibraryScreen`'s method: name the destination with a
         * `FocusRequester` placed on a genuinely focusable control, not on a
         * container.
         */
        Row(
            modifier = Modifier
                .zIndex(1f)
                // The way down is named, as in the library. Automatic traversal
                // does not cross the boundary between these two layers of a
                // single Box, and none of the variants tried before, focus
                // properties, group, `moveFocus`, ever managed it.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        // Consumed only if the destination exists. A screen
                        // whose first control is conditional may have none:
                        // swallowing the key there would trap the cursor,
                        // whereas handing it back lets ordinary traversal have a
                        // go.
                        runCatching { scaffoldFocus.first.requestFocus() }.isSuccess
                    } else {
                        false
                    }
                }
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onBack != null) {
                CircleIconButton(
                    onClick = onBack,
                    modifier = Modifier.focusRequester(scaffoldFocus.header)
                ) { tint -> ChevronLeft(size = 20.dp, color = tint) }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                // Explicit: the header floats in a plain Box, not a Surface, so
                // there is nothing to inherit a colour from and Text falls back
                // to black, invisible on the dark wallpaper.
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }

        // Past the band *and* past the fade. The veil is still fully opaque at
        // the band's lower edge, so content parked exactly there is drawn under
        // an undiluted copy of the wallpaper: legible in light mode, all but
        // erased in dark, where the first list header read as a grey smudge.
        CompositionLocalProvider(
            LocalScaffoldFocus provides scaffoldFocus,
            // What the header covers: the cursor uses it so as never to stop
            // underneath while going back up.
            LocalScaffoldBand provides if (contentScrolls) band + FADE_HEIGHT else band
        ) {
            content(if (contentScrolls) band + FADE_HEIGHT else band)
        }

        if (contentScrolls) WallpaperVeil(band = band, dark = dark)
    }
}

/**
 * A second copy of the wallpaper, drawn over the content and erased everywhere
 * except the strip the floating chrome occupies.
 *
 * This is what lets the app have floating chrome and scrolling content at the
 * same time. Content that rises under a pill has to go somewhere; without this
 * it simply draws through the title and the profile chips. Because it is the
 * same wallpaper at the same size, the pixels match the ones underneath exactly
 * - content dissolves into the backdrop rather than meeting a seam or a box.
 *
 * [fromTop] false anchors the strip to the bottom edge instead, for the dock.
 *
 * Put this *inside* the Haze source where one exists: the dock samples the
 * backdrop to blur it, and sampling the unveiled grid would blur tiles that the
 * veil has already hidden.
 */
@Composable
fun WallpaperVeil(
    band: Dp,
    dark: Boolean,
    modifier: Modifier = Modifier,
    fromTop: Boolean = true,
    fade: Dp = FADE_HEIGHT
) {
    TrayBackdrop(
        modifier = modifier
            .fillMaxSize()
            // The mask erases most of this copy, and DstIn only sees what the
            // layer holds, without an offscreen layer it would punch through
            // to the content below instead.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Fully opaque across the whole band, and only then fading.
                // Starting the fade inside the band left a ghost line of text
                // sitting on the title's baseline.
                val solid = band.toPx() / size.height
                val clear = (band + fade).toPx() / size.height
                val stops = if (fromTop) {
                    arrayOf(
                        0f to Color.Black,
                        solid to Color.Black,
                        clear.coerceAtMost(1f) to Color.Transparent,
                        1f to Color.Transparent
                    )
                } else {
                    arrayOf(
                        0f to Color.Transparent,
                        (1f - clear).coerceAtLeast(0f) to Color.Transparent,
                        (1f - solid).coerceAtLeast(0f) to Color.Black,
                        1f to Color.Black
                    )
                }
                drawRect(
                    brush = Brush.verticalGradient(colorStops = stops),
                    blendMode = BlendMode.DstIn
                )
            },
        dark = dark
    )
}

/**
 * Round, moulded, floating over the tray. The button a console puts in the
 * corner of its screen: a plastic disc with a lit top edge and a drawn glyph
 * inside, never a typed character.
 */
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit
) {
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    // Back is the first thing a gamepad looks for on a secondary screen, and it
    // showed nothing when the pad found it. Everything that takes focus must
    // show it: with no ring, the cursor vanishes and the screen passes for
    // frozen.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    // The travel, on top of the material's own depression: on the two dark
    // themes a plate that only loses its shadow loses almost nothing, because
    // there was little shadow to lose. Shrinking it is what carries the press on
    // every theme, and it is what the tiles have always done.
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "circle-press"
    )
    Box(
        modifier = modifier
            .size(HEADER_HEIGHT)
            .scale(press)
            .focusRing(focused, CircleShape, width = 3.dp, glowRadius = 18.dp)
            .plate(shape = CircleShape, dark = dark, oled = oled, lift = 5.dp, pressed = pressed)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon(MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * The name of a group of rows, in the app's own voice.
 *
 * It was an uppercase tracked micro-label — the eyebrow every dashboard ships,
 * and the one device the craft floor bans outright: a line set in small caps
 * above a heading is a costume for importance, and it makes the app read like a
 * settings screen from somewhere else. Sentence case at body weight says the
 * same thing, in the same voice the rest of the app speaks, and stops competing
 * with the content it introduces.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 6.dp)
    )
}

/** Material's minimum touch target, and therefore a pill's height. */
private val TOUCH_TARGET = 48.dp

/** Compact affordance for a secondary action inside a card. */
@Composable
fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    /**
     * True for a lone pill that takes the width of its card.
     *
     * Explicit, and not inferred from a `fillMaxWidth` set by the caller: ever
     * since the focus ring surrounds the pill, it is the frame that receives the
     * caller's modifier, and letting the pill stretch on its own would replay a
     * flaw already fixed, where in a row of two unweighted pills the first took
     * the whole width and the second dropped to zero.
     */
    fillWidth: Boolean = false,
    /**
     * Drawn instead of the label, for the buttons whose action is a symbol
     * rather than a word — removing a friend, dismissing a row. [label] still
     * travels with it, as the control's spoken name.
     */
    icon: (@Composable (Color) -> Unit)? = null
) {
    val accent = tint ?: MaterialTheme.colorScheme.primary
    // Every secondary action in the app goes through here, so this is the one
    // place that has to know about the pad for all of them, ring included.
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // The ring is drawn around the pill, not inside it.
    //
    // Placed on the pill itself, its stroke bit into the tinted background and
    // squeezed the label: it read as a badly sized border on the button rather
    // than as a selection laid over it. The header's round button never had that
    // flaw because its halo overflows its white background; here the gap plays
    // that part.
    //
    // The gap exists all the time, focused or not: making it appear on selection
    // would shift the button by that much, and a row of pills would jump every
    // time the cursor went past.
    //
    // The same shape as the pill, the one declared just above: the cursor traces
    // its outline, it does not infer it.
    Box(modifier = modifier.controlRing(shape), propagateMinConstraints = true) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = accent.copy(alpha = 0.12f),
        interactionSource = interaction,
        // The pill is the size of its touch area.
        //
        // `Surface(onClick)` reserves the 48 dp Material imposes on a touch
        // target as a matter of course, then draws its background at the size of
        // the label, centred inside. The frame, and therefore the ring, followed
        // the reservation, not the pill: five pixels of white between the green
        // stroke and the blue edge, measured at the top as at the bottom. Giving
        // the pill that height makes the drawing and the target coincide, the
        // ring sits tight, and the button becomes easier to hit with a finger
        // into the bargain.
        modifier = Modifier
            .heightIn(min = TOUCH_TARGET)
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
    ) {
        // Centred both ways, and both are needed.
        //
        // `textAlign` alone handles the horizontal. It does not handle the
        // vertical: when the pill is stretched to match a two-line neighbour, a
        // one-line label stays pinned to the top of the height it has just been
        // given. The Box is what puts it back in the middle; Surface propagates
        // its minimum constraints to its content, so the Box does fill the whole
        // pill, stretched or not.
        // No `fillMaxWidth` here. There was one, and it broke any row of two
        // unweighted pills: the first took the entire width, the second dropped
        // to zero and wrapped its label onto as many lines as it has letters,
        // with the settings' Library card standing 390 dp tall for three lines
        // of text.
        //
        // Without it the Box fits its content; and when the caller stretches the
        // pill (a weight, a `fillMaxWidth`), Surface propagates its minimum
        // constraints and the Box fills anyway. The centring holds in both
        // cases, which is all that was ever asked of it.
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                icon(accent)
            } else {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    }
}

/**
 * A row of overlapping avatars, the way a group of people is usually shown.
 * Beyond [max], the remainder becomes a "+n" chip.
 */
@Composable
fun AvatarStack(
    names: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    max: Int = 4
) {
    val dark = LocalEmufiiDarkTheme.current
    val ring = if (dark) Color(0xFF0E1116) else Color.White
    val shown = names.take(max)
    val extra = names.size - shown.size
    // Overlap has to come from an offset: a negative Spacer width isn't a
    // thing in Compose, and laying them out edge to edge reads as a list
    // rather than as a group.
    val overlap = size / 3

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { i, name ->
            Avatar(
                name = name,
                size = size,
                ring = ring,
                modifier = Modifier
                    .offset(x = -overlap * i)
                    .zIndex((shown.size - i).toFloat())
            )
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .offset(x = -overlap * shown.size)
                    .size(size)
                    .clip(CircleShape)
                    .background(if (dark) PlateDark else PlateLightLow),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+$extra",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val HEADER_HEIGHT = 44.dp

/**
 * How far below the header the backdrop takes to become transparent again.
 * Long enough to read as a dissolve rather than a cut edge, short enough not to
 * dim the first card of a screen at rest.
 */
private val FADE_HEIGHT = 32.dp
