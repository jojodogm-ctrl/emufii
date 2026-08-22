package eu.emufii.app.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import eu.emufii.app.profile.Profile
import eu.emufii.app.profile.playerDisplayName
import eu.emufii.app.ui.focusRing
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.LocalAccent
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.PlateDark
import eu.emufii.app.ui.theme.PlateLight

/**
 * Both buttons in the home screen's top bar are 46 across, a 40 avatar in 3 of
 * padding, so the friends one has to match or the pair reads as misaligned.
 */
private val CHIP_SIZE = 46.dp

/**
 * The floating pill the top-bar buttons are cut from.
 *
 * A borderless circle with a low, soft shadow: it reads as an object above the
 * wallpaper rather than a widget drawn on it, which is the whole point of the
 * app's visual direction.
 *
 * The dark fill is deliberately lighter than it looks like it should be. The
 * earlier value sat a hair off the wallpaper's own colour, and a shadow does
 * nothing on a dark backdrop, so the pill was invisible. That went unnoticed
 * while the profile avatar was the only thing in one, because a bright circle
 * filling the pill needs no pill. It shows immediately behind a glyph.
 *
 * No Material indication, and a press animation instead. `Surface(onClick)`
 * brings a ripple whose state layer also covers *focus*, and Android hands
 * focus to the first focusable view the moment a keyboard or a game pad is
 * attached, always, on a handheld like the Thor. The result was a flat 10%
 * wash sitting permanently on this chip, which reads as "disabled" rather than
 * "selected". The profile chip had it too, hidden all along under its avatar.
 * The scale-on-press below is the feedback the dock and the tiles already use,
 * and the chips stay focusable, so a d-pad still reaches them.
 */
@Composable
fun TopBarChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dark = LocalEmufiiDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "chip-scale"
    )

    // The grid hands back to the top bar when going up from the first row:
    // without a ring the cursor simply became invisible there and the screen had
    // to be touched to find out where you were.
    val focused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(CHIP_SIZE)
            .scale(scale)
            .focusRing(focused, CircleShape, width = 2.5.dp, glowRadius = 10.dp)
            .plate(
                shape = CircleShape,
                dark = dark,
                oled = LocalEmufiiOledTheme.current,
                lift = 5.dp,
                pressed = pressed
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * The profile as it appears in the top bar. Opens the profile and settings page.
 *
 * Just the avatar. It used to carry a "pick a nickname" nudge beside it while
 * the profile was unnamed, which made the chip change width depending on state
 * and put a permanent chore on the home screen for something a session works
 * fine without. The settings page is where a nickname gets chosen anyway.
 */
@Composable
fun ProfileChip(
    profile: Profile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopBarChip(onClick = onClick, modifier = modifier) {
        Box(modifier = Modifier.padding(3.dp)) {
            Avatar(
                name = playerDisplayName(profile.name),
                imageFile = profile.avatarFile,
                size = 40.dp,
                ring = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.clip(CircleShape)
            )
        }
    }
}

/**
 * Friends, straight from the home screen.
 *
 * They used to be reachable only through the profile page, two taps in and
 * filed under settings, which is the wrong shelf: seeing who is online and
 * joining them is something you do *instead* of browsing the library, not a
 * preference you adjust. So it sits beside the profile, same shape, same
 * weight.
 */
@Composable
fun FriendsChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopBarChip(onClick = onClick, modifier = modifier) {
        // Not a pictogram of people. Two avatars, stacked.
        //
        // The chip started as the 👥 emoji, which arrived with the system font's
        // own palette next to a chip whose only other occupant is a photo or two
        // initials in the app's colours, half a two-button bar coming from
        // somewhere else, and redrawn by every Android version besides. Drawing
        // the same pictogram by hand fixed the palette and kept the problem: a
        // little figure of a person is still a symbol pasted onto a bar that
        // otherwise contains no symbols at all.
        //
        // This app already says "other players" a specific way, `AvatarStack`,
        // overlapping discs with a ring cut between them, on the session screen
        // and the presence card. Saying it again here costs nothing and makes
        // the pair read as one family of shapes: you on the right, the others on
        // the left.
        FriendsAvatars()
    }
}

/**
 * Open games, in the same family of pills as the friends and the profile.
 *
 * This used to be a solid blue pill floating alone at the bottom of the screen.
 * It did say it led somewhere, but it said so in a language nothing else spoke,
 * and it scrolled over the cover art. Here it joins the two buttons that are,
 * like it, navigation: all three now have the same shape, the same size and the
 * same relief.
 *
 * Two linked screens, not two people. The friends chip already carries two
 * overlapping discs, which are people. A session is two consoles talking to each
 * other, and that is what the glyph draws. The distinction is carried by the
 * shape (discs against rectangles), not by a decorative detail.
 */
@Composable
fun SessionsChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = MaterialTheme.colorScheme.onSurface
    TopBarChip(onClick = onClick, modifier = modifier) {
        Canvas(Modifier.size(23.dp)) {
            val w = size.width
            val h = size.height
            val screenW = w * 0.46f
            val screenH = h * 0.34f
            val stroke = Stroke(width = w * 0.10f)
            val radius = androidx.compose.ui.geometry.CornerRadius(w * 0.09f)

            // Offset diagonally: two consoles side by side would read as a
            // single object cut in half.
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
            // The link, dotted: a session goes over the network, it is not a
            // cable between two devices sitting next to each other.
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
 * Two blank avatars, overlapped the way [AvatarStack] overlaps real ones.
 *
 * Blank on purpose. Filling them with the first two friends was considered and
 * dropped: the state everyone sees on a fresh install is the empty one, so the
 * icon would mostly be this anyway, with a branch to maintain and an appearance
 * that changes under the user.
 *
 * The ring is the chip's own fill rather than plain white, so the gap between
 * the discs stays a gap in either theme, the same reasoning `AvatarStack` uses
 * when it rings against the page background.
 */
@Composable
private fun FriendsAvatars(modifier: Modifier = Modifier) {
    val dark = LocalEmufiiDarkTheme.current
    val ring = if (dark) PlateDark else PlateLight

    // Placed by offset from the centre, not by corner alignment. Aligned to
    // TopEnd and BottomStart they only met at a diagonal, two discs touching,
    // with the pair sitting off-centre in a round chip. The overlap is the whole
    // point of the shape: it is what makes two circles read as two people rather
    // than as a diagram.
    Box(modifier = modifier.size(34.dp), contentAlignment = Alignment.Center) {
        // Behind: muted, up and to the right. Cooler and lower-contrast so the
        // two read as depth rather than as two things of equal weight.
        Disc(
            colors = if (dark) listOf(Color(0xFF515A6B), Color(0xFF3E4757))
                     else listOf(Color(0xFFB9C0CF), Color(0xFF9AA3B6)),
            ring = ring,
            modifier = Modifier.offset(x = 6.dp, y = (-4).dp)
        )
        // In front: the accent in force, down and to the left.
        //
        // It said "the app's accent" and was a hardcoded iOS blue — a leftover
        // of the glass world that never followed the cyan either, and therefore
        // the last thing on the tray whose colour answered to nothing. Now it
        // is the accent, which is also what makes this pill visibly change with
        // the setting.
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
