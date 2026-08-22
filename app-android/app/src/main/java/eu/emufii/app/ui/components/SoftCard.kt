package eu.emufii.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.emufii.app.ui.controlRing
import eu.emufii.app.ui.theme.CardShape
import eu.emufii.app.ui.theme.LocalEmufiiDarkTheme
import eu.emufii.app.ui.theme.LocalEmufiiOledTheme
import eu.emufii.app.ui.theme.PlateDark
import eu.emufii.app.ui.theme.PlateLight
import eu.emufii.app.ui.theme.PlateOled
import eu.emufii.app.ui.theme.plate
import eu.emufii.app.ui.theme.tilePlateBrush

/**
 * A panel's fill, exposed because content scrolling inside one has to be able to
 * dissolve into it: a gradient towards that exact colour, and not towards an
 * approximation, which would give the fade away with a visible seam.
 *
 * The panel's face is a gradient, so this is its *top* colour — the end a fade
 * from the header meets.
 */
@Composable
fun softCardFill(): Color = when {
    LocalEmufiiOledTheme.current -> PlateOled
    LocalEmufiiDarkTheme.current -> PlateDark
    else -> PlateLight
}

/**
 * The plate a game icon sits on, in the library and on the cards.
 *
 * White on the light theme, and that one is left alone: artwork of every
 * possible colour reads against white, which is why it was chosen. On the dark
 * themes it is moulded plastic like everything else, lit from the top, so a
 * tile reads as an object over the tray rather than as a bright square cut out
 * of it.
 */
@Composable
fun tilePlate(): Brush = tilePlateBrush(
    dark = LocalEmufiiDarkTheme.current,
    oled = LocalEmufiiOledTheme.current
)

/**
 * The hairline between the artwork and its plate.
 *
 * It exists to keep pale box art from bleeding into the plate, so it has to
 * change sides with it: a dark rim reads on white and disappears on the dark
 * plates, where a faint white one does the same job.
 */
@Composable
fun artworkRim(): Color =
    if (LocalEmufiiDarkTheme.current) Color(0x24FFFFFF) else Color(0x2E000000)

/**
 * The panel every screen is built out of: one moulded plate.
 *
 * Deliberately *not* `Surface(shadowElevation = …)`. At small elevations on a
 * pale ground that draws a hard grey band hugging the outline instead of a
 * diffuse shadow — a dirty rim at every corner. The material is assembled by
 * hand in `theme/Plastic.kt`, where the shadow's offset, the lit bevel and the
 * moulding's edge are one thing rather than three guesses.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = CardShape
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Before everything that clips. A clickable panel is an element you
            // select: it carries the cursor, in its own shape. But the ring's
            // glow overflows the node's bounds by construction, and placed after
            // the plate's clip it was sliced there, the glow stopping dead
            // inside the card. Here it precedes the clip, and stays above the
            // `clickable` in the chain, so it still reads its focus.
            .then(if (onClick != null) Modifier.controlRing(shape) else Modifier)
            .plate(shape = shape, dark = dark, oled = oled, lift = 6.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        // A Box provides no content colour, so any Text inside that doesn't name
        // one falls back to black: fine on the pale plate, invisible on the dark
        // one. Surface would do this for us, but Surface is what draws the rim
        // artifact this component exists to avoid, so it does it here instead.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}
