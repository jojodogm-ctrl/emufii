package eu.emufii.app.ui.components

import eu.emufii.app.ui.Motion
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.emufii.app.profile.Profile
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.ui.focusRing
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.LocalGlassPane
import eu.emufii.app.ui.glass
import eu.emufii.app.ui.theme.shelfFill
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.LocalAccent
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.PlateDark
import eu.emufii.app.ui.theme.PlateLight
import eu.emufii.app.ui.tap

/**
 * The glyph chips. The two ends of the header are anchors at [ANCHOR_SIZE], the avatar
 * opening it and the session button closing it; what sits between them stays a chip.
 * pourquoi : docs/decisions/direction-visuelle.md § The top bar's chips are one family
 */
private val CHIP_SIZE = 46.dp

/** You are not a third glyph: the avatar anchors the row's far end. */
private val ANCHOR_SIZE = 52.dp

/**
 * No Material indication: its state layer also covers focus, which a gamepad grants
 * permanently, leaving a "disabled"-looking wash.
 * pourquoi : docs/decisions/direction-visuelle.md § No Material indication: a press animation
 */
@Composable
fun TopBarChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = CHIP_SIZE,
    /**
     * How the rear panel learns what is aimed at.
     * pourquoi : docs/decisions/second-ecran.md § What travels to the panel
     */
    onFocused: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val dark = LocalEmufiiDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = Motion.press(),
        label = "chip-scale"
    )

    // The grid hands back to the top bar going up from the first row: without a ring
    // the cursor became invisible there.
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocused(focused) }

    val pane = LocalGlassPane.current

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .focusRing(focused, CircleShape, width = 2.5.dp, glowRadius = 10.dp)
            .then(
                // Glass on glass, plastic elsewhere: the disc is the header's family, and
                // the header is the only surface that hands one down.
                if (pane != null) {
                    Modifier.glass(pane, CircleShape, dark, thickness = 0.35f, lift = 3.dp)
                } else {
                    Modifier.plate(
                        shape = CircleShape,
                        dark = dark,
                        oled = LocalEmufiiOledTheme.current,
                        lift = 3.dp,
                        // The same face as the shelf it sits on: what separates it is its
                        // contour and its shadow, not a lighter fill.
                        fill = shelfFill(dark, LocalEmufiiOledTheme.current && dark),
                        // The shelf it sits on carries the volume; a moulded lip on a 46 dp
                        // disc reads as a dome and brings the old world back.
                        // pourquoi : docs/decisions/theme-duotone-shelves.md § The shelf is posed, never carved
                        bevel = false,
                        pressed = pressed
                    )
                }
            )
            .tap(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * Just the avatar, never a badge that would change the chip's width with its state.
 * pourquoi : docs/decisions/direction-visuelle.md § The top bar's chips are one family
 */
@Composable
fun ProfileChip(
    profile: Profile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Boolean) -> Unit = {}
) {
    TopBarChip(onClick = onClick, modifier = modifier, size = ANCHOR_SIZE, onFocused = onFocused) {
        Box(modifier = Modifier.padding(3.dp)) {
            Avatar(
                name = playerDisplayName(profile.name),
                imageFile = profile.avatarFile,
                size = 46.dp,
                ring = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.clip(CircleShape)
            )
        }
    }
}

/** pourquoi : docs/decisions/direction-visuelle.md § The top bar's chips are one family */
@Composable
fun FriendsChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Boolean) -> Unit = {}
) {
    TopBarChip(onClick = onClick, modifier = modifier, onFocused = onFocused) {
        // The icon system's silhouette, the one the finder's empty state carries.
        // pourquoi : docs/decisions/direction-visuelle.md § The glyphs say "other players" the way the rest of the app does
        PersonMark(size = 22.dp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Two linked screens, not two people: discs are people here.
 * pourquoi : docs/decisions/direction-visuelle.md § The glyphs say "other players" the way the rest of the app does
 */
@Composable
fun SessionsChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (Boolean) -> Unit = {},
    /** Coral contour and coral glyph: what the app is for, named without being filled. */
    outlined: Boolean = false
) {
    val dark = LocalEmufiiDarkTheme.current
    val coral = if (dark) Coral.darkBright else Coral.deep
    val tint = if (outlined) coral else MaterialTheme.colorScheme.onSurface
    TopBarChip(
        onClick = onClick,
        modifier = if (outlined) modifier.border(2.dp, coral, CircleShape) else modifier,
        // The far end of the header, and it answers the avatar at the near end: the two
        // were 52 and 46 dp, so the row ended on a smaller disc set further in than the
        // one it started on, and the header read as unfinished on the right.
        size = ANCHOR_SIZE,
        onFocused = onFocused
    ) {
        Canvas(Modifier.size(26.dp)) {
            val w = size.width
            val h = size.height
            val screenW = w * 0.46f
            val screenH = h * 0.34f
            val stroke = Stroke(width = w * 0.10f)
            val radius = androidx.compose.ui.geometry.CornerRadius(w * 0.09f)

            // Offset diagonally: two consoles side by side read as one object cut in half.
            drawRoundRect(
                color = tint,
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(screenW, screenH),
                cornerRadius = radius,
                style = stroke
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(w - screenW, h - screenH),
                size = androidx.compose.ui.geometry.Size(screenW, screenH),
                cornerRadius = radius,
                style = stroke
            )
            // The link is dotted: a session goes over the network, not down a cable.
            drawLine(
                color = tint,
                start = Offset(screenW * 0.55f, screenH * 1.25f),
                end = Offset(w - screenW * 0.55f, h - screenH * 1.25f),
                strokeWidth = w * 0.10f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.02f, w * 0.13f))
            )
        }
    }
}

/**
 * Blank on purpose: the empty state is what most installs show.
 * pourquoi : docs/decisions/direction-visuelle.md § The glyphs say "other players" the way the rest of the app does
 */
@Composable
private fun FriendsAvatars(modifier: Modifier = Modifier) {
    val dark = LocalEmufiiDarkTheme.current
    val ring = if (dark) PlateDark else PlateLight

    // By offset from the centre, never corner alignment: the overlap is the shape.
    // pourquoi : docs/decisions/direction-visuelle.md § The glyphs say "other players" the way the rest of the app does
    Box(modifier = modifier.size(34.dp), contentAlignment = Alignment.Center) {
        // Behind: muted, so the two read as depth. The depth comes from value, not
        // from temperature.
        // pourquoi : docs/decisions/theme-duotone-shelves.md § Two semantic axes
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val ground = MaterialTheme.colorScheme.surfaceVariant
        Disc(
            colors = listOf(lerp(ground, muted, 0.52f), lerp(ground, muted, 0.34f)),
            ring = ring,
            modifier = Modifier.offset(x = 6.dp, y = (-4).dp)
        )
        // pourquoi : docs/decisions/direction-visuelle.md § The glyphs say "other players" the way the rest of the app does
        val accent = LocalAccent.current
        Disc(
            colors = listOf(accent.bright, accent.deep),
            ring = ring,
            modifier = Modifier.offset(x = (-6).dp, y = 4.dp)
        )
    }
}

@Composable
private fun Disc(colors: List<Color>, ring: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors))
            .border(BorderStroke(2.dp, ring), CircleShape)
    )
}
