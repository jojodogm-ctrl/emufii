package eu.emufii.app.ui.components

import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.key
import eu.emufii.app.ui.rememberPartOfOpening
import eu.emufii.app.ui.popIn
import eu.emufii.app.ui.Motion
import eu.emufii.app.ui.sounded
import androidx.compose.foundation.BorderStroke
import eu.emufii.app.ui.theme.ShellOled
import eu.emufii.app.ui.theme.ShellLightLow
import eu.emufii.app.ui.theme.ShellLight
import eu.emufii.app.ui.theme.ShellDark
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/** [first] must be a focusable control: requesting focus on a `focusGroup` focuses the group. */
class ScaffoldFocus(val first: FocusRequester, val header: FocusRequester)

val LocalScaffoldFocus = compositionLocalOf<ScaffoldFocus?> { null }

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

@Composable
fun Modifier.upToHeader(): Modifier {
    val focus = LocalScaffoldFocus.current ?: return this
    return this.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
            runCatching { focus.header.requestFocus() }.isSuccess
        } else {
            false
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmufiiScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backIcon: (@Composable (Color) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentScrolls: Boolean = true,
    autoFocus: Boolean = true,
    content: @Composable (topPadding: Dp) -> Unit
) {
    val dark = LocalEmufiiDarkTheme.current
    val scaffoldFocus = remember { ScaffoldFocus(FocusRequester(), FocusRequester()) }

    // Ask for keyboard mode before focus, and retry on each early frame without checking.
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
    // Same height with the bar hidden or shown, or the header jumps.
    val statusBar = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues()
        .calculateTopPadding()
    val band = statusBar + HEADER_HEIGHT + 24.dp

    Box(modifier = modifier.fillMaxSize()) {
        TrayBackdrop(modifier = Modifier.fillMaxSize(), dark = dark)

        Row(
            modifier = Modifier
                .zIndex(1f)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        // Consume only if the target exists, or the cursor gets trapped.
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
                // Not in a Surface: Text would fall back to black.
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }

        CompositionLocalProvider(
            LocalScaffoldFocus provides scaffoldFocus,
            // What the header covers: the cursor reads it so as never to stop underneath.
            LocalScaffoldBand provides if (contentScrolls) band + FADE_HEIGHT else band
        ) {
            val focusManager = LocalFocusManager.current
            Box(
                Modifier.fillMaxSize().onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) {
                        return@onKeyEvent false
                    }
                    // Consumed either way: the move is done here, not a second time after.
                    if (!focusManager.moveFocus(FocusDirection.Up)) {
                        runCatching { scaffoldFocus.header.requestFocus() }
                    }
                    true
                }
            ) {
                content(if (contentScrolls) band + FADE_HEIGHT else band)
            }
        }

        if (contentScrolls) WallpaperVeil(band = band, dark = dark)
    }
}

private const val AUTO_FOCUS_FRAMES = 6

@Composable
fun WallpaperVeil(
    band: Dp,
    dark: Boolean,
    modifier: Modifier = Modifier,
    fromTop: Boolean = true,
    fade: Dp = FADE_HEIGHT
) = WallpaperVeil({ band }, dark, modifier, fromTop, fade)

@Composable
fun WallpaperVeil(
    band: () -> Dp,
    dark: Boolean,
    modifier: Modifier = Modifier,
    fromTop: Boolean = true,
    fade: Dp = FADE_HEIGHT
) {
    val oled = LocalEmufiiOledTheme.current
    val ground = when {
        oled -> ShellOled
        fromTop -> if (dark) ShellDark else ShellLight
        else -> if (dark) ShellDarkLow else ShellLightLow
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val solid = band().toPx().coerceAtLeast(0f)
        val total = (solid + fade.toPx()).coerceAtMost(size.height)
        if (total <= 0f) return@Canvas
        val cut = (solid / total).coerceIn(0f, 1f)
        val stops = if (fromTop) {
            arrayOf(0f to ground, cut to ground, 1f to Color.Transparent)
        } else {
            arrayOf(0f to Color.Transparent, (1f - cut) to ground, 1f to ground)
        }
        val originY = if (fromTop) 0f else size.height - total
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = stops,
                startY = originY,
                endY = originY + total
            ),
            topLeft = Offset(0f, originY),
            size = Size(size.width, total)
        )
    }
}

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

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 6.dp)
    )
}

private val TOUCH_TARGET = 48.dp

@Composable
fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    fillWidth: Boolean = false,
    icon: (@Composable (Color) -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null
) {
    val accent = tint ?: MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(modifier = modifier.controlRing(shape), propagateMinConstraints = true) {
    Surface(
        onClick = sounded(onClick),
        shape = shape,
        color = accent.copy(alpha = 0.12f),
        interactionSource = interaction,
        modifier = Modifier
            .heightIn(min = TOUCH_TARGET)
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
    ) {
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
            // Keyed on the name: a player joining pops in, the others stay where they are.
            key(name) {
                Avatar(
                    name = name,
                    size = size,
                    ring = ring,
                    modifier = Modifier
                        .offset(x = -overlap * i)
                        .zIndex((shown.size - i).toFloat())
                        .popIn(settled = rememberPartOfOpening())
                )
            }
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

private val FADE_HEIGHT = 32.dp
