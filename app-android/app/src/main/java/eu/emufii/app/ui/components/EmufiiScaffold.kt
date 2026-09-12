package eu.emufii.app.ui.components

import eu.emufii.app.ui.Motion
import eu.emufii.app.ui.sounded
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
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
import androidx.compose.ui.input.InputMode
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInputModeManager
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
import eu.emufii.app.ui.theme.ShellDarkLow
import eu.emufii.app.ui.tap

/**
 * [first] must sit on a genuinely focusable control, never on a container: a request on a
 * `focusGroup` succeeds by focusing the group itself.
 * pourquoi : docs/decisions/coquille-ecrans.md § The header is declared before the content, and drawn over it
 */
class ScaffoldFocus(val first: FocusRequester, val header: FocusRequester)

val LocalScaffoldFocus = compositionLocalOf<ScaffoldFocus?> { null }

/** Goes on a screen's first control: the header's "down" destination, and "up" goes back there. */
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
 * The header floats; it is never a bar with a background.
 * pourquoi : docs/decisions/coquille-ecrans.md § The header floats, and what that costs
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmufiiScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    /**
     * A screen whose back closes rather than goes up says so before it is pressed: in a
     * session this button ends the session, and a chevron promised the opposite.
     * pourquoi : docs/decisions/session.md § Back closes the session, and it says so
     */
    backIcon: (@Composable (Color) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /**
     * False when the screen fits whole: the veil and its 32 dp only serve content rising
     * under the header, and cost 7 % of the Thor's height otherwise.
     * pourquoi : docs/decisions/coquille-ecrans.md § The header floats, and what that costs
     */
    contentScrolls: Boolean = true,
    /** False for a screen placing its own cursor; nothing uses it today, the library not being scaffolded. */
    autoFocus: Boolean = true,
    content: @Composable (topPadding: Dp) -> Unit
) {
    val dark = LocalEmufiiDarkTheme.current
    val scaffoldFocus = remember { ScaffoldFocus(FocusRequester(), FocusRequester()) }

    /**
     * Two traps, in this order: ask for keyboard mode before focus, and ask again on each
     * of the first frames without testing whether it worked.
     * pourquoi : docs/decisions/coquille-ecrans.md § The cursor arrives with the screen
     */
    val inputMode = LocalInputModeManager.current
    if (autoFocus) {
        LaunchedEffect(Unit) {
            repeat(AUTO_FOCUS_FRAMES) {
                withFrameNanos { }
                inputMode.requestInputMode(InputMode.Keyboard)
                runCatching { scaffoldFocus.first.requestFocus() }
            }
        }
    }
    // See LibraryScreen: the bar hides behind the logo, and its height must be the same
    // before and after, or the header jumps at the changeover.
    val statusBar = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues()
        .calculateTopPadding()
    val band = statusBar + HEADER_HEIGHT + 24.dp

    Box(modifier = modifier.fillMaxSize()) {
        TrayBackdrop(modifier = Modifier.fillMaxSize(), dark = dark)

        /**
         * Declared before the content, traversal following declaration order, and drawn
         * above it by `zIndex`. Focus properties, group and `moveFocus` were all tried and
         * none crossed the boundary between two layers of one Box: name the destination.
         * pourquoi : docs/decisions/coquille-ecrans.md § The header is declared before the content, and drawn over it
         */
        Row(
            modifier = Modifier
                .zIndex(1f)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        // Consumed only if the destination exists, or the cursor is trapped
                        // on a screen with no first control.
                        // pourquoi : docs/decisions/coquille-ecrans.md § The header is declared before the content, and drawn over it
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
                ) { tint ->
                    if (backIcon != null) backIcon(tint)
                    else ChevronLeft(size = 20.dp, color = tint)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                // The header floats in a plain Box, not a Surface: with no colour to
                // inherit, Text falls back to black, invisible on the dark wallpaper.
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }

        // Past the band *and* past the fade: the veil is still fully opaque at the band's
        // lower edge, and content parked there sits under an undiluted copy of the
        // wallpaper, all but erased in dark, where the first list header read as a smudge.
        CompositionLocalProvider(
            LocalScaffoldFocus provides scaffoldFocus,
            // What the header covers: the cursor reads it so as never to stop underneath.
            LocalScaffoldBand provides if (contentScrolls) band + FADE_HEIGHT else band
        ) {
            content(if (contentScrolls) band + FADE_HEIGHT else band)
        }

        if (contentScrolls) WallpaperVeil(band = band, dark = dark)
    }
}

/** Six frames, about a hundred milliseconds: layout settles well before that on the Thor. */
private const val AUTO_FOCUS_FRAMES = 6

/**
 * A second copy of the wallpaper, erased except where the floating chrome sits; [fromTop]
 * false anchors it to the bottom. Put it *inside* the Haze source where one exists.
 * pourquoi : docs/decisions/coquille-ecrans.md § The header floats, and what that costs
 */
@Composable
fun WallpaperVeil(
    band: Dp,
    dark: Boolean,
    modifier: Modifier = Modifier,
    fromTop: Boolean = true,
    fade: Dp = FADE_HEIGHT
) = WallpaperVeil({ band }, dark, modifier, fromTop, fade)

/**
 * The band as a lambda, for a veil that has to follow a scroll: read at composition it
 * would recompose the wallpaper on every frame, read in the draw it only repaints.
 * pourquoi : docs/decisions/performance-rendu.md § One clock for everything that moves continuously
 */
@Composable
fun WallpaperVeil(
    band: () -> Dp,
    dark: Boolean,
    modifier: Modifier = Modifier,
    fromTop: Boolean = true,
    fade: Dp = FADE_HEIGHT
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val full = maxHeight
        // The strip is as tall as the band plus its fade, and not the screen. The layer
        // below is offscreen -- a real buffer, allocated and flushed every frame -- and
        // at full height it was 1920x1080 of it to show 130 dp. Measured on the Thor,
        // 2026-09-10: `flush layers` was 3.7 ms a frame with two veils up.
        // pourquoi : docs/decisions/performance-rendu.md § An offscreen layer is not a drawing setting
        val strip = (band() + fade).coerceIn(0.dp, full)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(strip)
                .align(if (fromTop) Alignment.TopStart else Alignment.BottomStart)
                // DstIn only sees what the layer holds: without an offscreen layer the
                // mask punches through to the content below instead.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Starting the fade inside the band left a ghost line of text on the
                    // title's baseline. The stops are read against the strip now, not the
                    // screen, which is the same place in the same pixels.
                    val solid = if (size.height > 0f) band().toPx() / size.height else 0f
                    val stops = if (fromTop) {
                        arrayOf(
                            0f to Color.Black,
                            solid.coerceIn(0f, 1f) to Color.Black,
                            1f to Color.Transparent
                        )
                    } else {
                        arrayOf(
                            0f to Color.Transparent,
                            (1f - solid).coerceIn(0f, 1f) to Color.Black,
                            1f to Color.Black
                        )
                    }
                    drawRect(
                        brush = Brush.verticalGradient(colorStops = stops),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            // Full height inside the strip, and hung so that the wallpaper lands exactly
            // where the screen's own does: the band shows the tray, not a squeezed copy.
            TrayBackdrop(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(full)
                    .offset(y = if (fromTop) 0.dp else -(full - strip)),
                dark = dark
            )
        }
    }
}

/**
 * Round, moulded, floating over the tray, with a drawn glyph inside.
 * pourquoi : docs/decisions/coquille-ecrans.md § The round button is a moulded disc
 */
@Composable
fun CircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit
) {
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    // Travel on top of the material's own depression: on the two dark themes a plate that
    // only loses its shadow loses almost nothing, there being little shadow to lose.
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = Motion.press(),
        label = "circle-press"
    )
    Box(
        modifier = modifier
            .size(HEADER_HEIGHT)
            .scale(press)
            .focusRing(focused, CircleShape, width = 3.dp, glowRadius = 18.dp)
            .plate(shape = CircleShape, dark = dark, oled = oled, lift = 5.dp, pressed = pressed)
            .tap(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon(MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * The name of a group of rows, in the app's own voice: sentence case at body
 * weight, never a tracked uppercase eyebrow.
 * pourquoi : docs/decisions/coquille-ecrans.md § The group title speaks the app's voice
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

@Composable
fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    /**
     * True for a lone pill that takes the width of its card. Explicit, never
     * inferred: the frame is what receives the caller's modifier.
     * pourquoi : docs/decisions/coquille-ecrans.md § The label is centred both ways, and both are needed
     */
    fillWidth: Boolean = false,
    /** Drawn instead of the label; [label] still travels with it, as the spoken name. */
    icon: (@Composable (Color) -> Unit)? = null,
    /**
     * Laid before the label, which stays; distinct from [icon], which replaces it. A service
     * logo, never a decorative pictogram.
     * pourquoi : docs/decisions/reglages-ecran.md § The two outbound links, and their order
     */
    leading: (@Composable () -> Unit)? = null
) {
    val accent = tint ?: MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Around the pill, never inside it, and the gap exists at all times: appearing on
    // selection would make a row of pills jump.
    // pourquoi : docs/decisions/coquille-ecrans.md § The ring surrounds the chip, it does not bite into it
    Box(modifier = modifier.controlRing(shape), propagateMinConstraints = true) {
    Surface(
        onClick = sounded(onClick),
        shape = shape,
        color = accent.copy(alpha = 0.12f),
        interactionSource = interaction,
        // `Surface(onClick)` reserves 48 dp and draws its background smaller, which the
        // ring followed.
        // pourquoi : docs/decisions/coquille-ecrans.md § The chip is the size of its touch target
        modifier = Modifier
            .heightIn(min = TOUCH_TARGET)
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
    ) {
        // `textAlign` cannot do the vertical, and NO `fillMaxWidth` here: it broke any row
        // of two unweighted pills.
        // pourquoi : docs/decisions/coquille-ecrans.md § The label is centred both ways, and both are needed
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                icon(accent)
            } else if (leading != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    leading()
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        textAlign = TextAlign.Center
                    )
                }
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

@Composable
fun AvatarStack(
    names: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    max: Int = 4
) {
    val dark = LocalEmufiiDarkTheme.current
    val ring = if (dark) ShellDarkLow else Color.White
    val shown = names.take(max)
    val extra = names.size - shown.size
    // A negative Spacer width isn't a thing in Compose, and edge to edge reads as a list.
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
 * pourquoi : docs/decisions/coquille-ecrans.md § The header floats, and what that costs
 */
private val FADE_HEIGHT = 32.dp
