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
import eu.emufii.app.ui.tap

/**
 * Exposed because content scrolling inside a panel fades towards it: an approximation
 * gives the fade away with a visible seam. The face is a gradient, so this is its top
 * colour, the end a fade from the header meets.
 */
@Composable
fun softCardFill(): Color = when {
    LocalEmufiiOledTheme.current -> PlateOled
    LocalEmufiiDarkTheme.current -> PlateDark
    else -> PlateLight
}

/**
 * White on the light theme and left alone, artwork of every colour reads against white;
 * moulded plastic lit from the top on the dark ones, so a tile reads as an object over
 * the tray rather than a bright square cut out of it.
 */
@Composable
fun tilePlate(): Brush = tilePlateBrush(
    dark = LocalEmufiiDarkTheme.current,
    oled = LocalEmufiiOledTheme.current
)

/** Keeps pale box art from bleeding into the plate, so it changes sides with it. */
@Composable
fun artworkRim(): Color =
    if (LocalEmufiiDarkTheme.current) eu.emufii.app.ui.theme.EdgeDark else eu.emufii.app.ui.theme.EdgeLight

/**
 * Deliberately not `Surface(shadowElevation = …)`: at small elevations on a pale ground
 * that draws a hard grey band hugging the outline, a dirty rim at every corner. The
 * material is assembled by hand in `theme/Plastic.kt`.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /**
     * The cursor's band is a share of the card's smaller side, so a tall card wears a
     * thick one. A card that folds open doubles in height and the band followed it to the
     * ceiling; those pass a smaller share rather than let the ring become the subject.
     */
    bandFraction: Float = 0.12f,
    content: @Composable () -> Unit
) {
    val shape = CardShape
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Before everything that clips: the ring's glow overflows the node's bounds,
            // and after the plate's clip it stopped dead inside the card. It stays above
            // the `clickable` in the chain, so it still reads its focus.
            .then(
                if (onClick != null) Modifier.controlRing(shape, bandFraction = bandFraction)
                else Modifier
            )
            .plate(shape = shape, dark = dark, oled = oled, lift = 6.dp)
            .then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier)
    ) {
        // A Box provides no content colour: a Text that names none falls back to black,
        // invisible on the dark plate. Surface would do it, and draws the rim artifact.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}
