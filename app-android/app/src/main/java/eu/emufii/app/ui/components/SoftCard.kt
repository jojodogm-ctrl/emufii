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
import androidx.compose.ui.unit.Dp
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

@Composable
fun softCardFill(): Color = when {
    LocalEmufiiOledTheme.current -> PlateOled
    LocalEmufiiDarkTheme.current -> PlateDark
    else -> PlateLight
}

@Composable
fun tilePlate(): Brush = tilePlateBrush(
    dark = LocalEmufiiDarkTheme.current,
    oled = LocalEmufiiOledTheme.current
)

@Composable
fun artworkRim(): Color =
    if (LocalEmufiiDarkTheme.current) eu.emufii.app.ui.theme.EdgeDark else eu.emufii.app.ui.theme.EdgeLight

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    bandFraction: Float = 0.12f,
    lift: Dp = 4.dp,
    content: @Composable () -> Unit
) {
    val shape = CardShape
    val dark = LocalEmufiiDarkTheme.current
    val oled = LocalEmufiiOledTheme.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.controlRing(shape, bandFraction = bandFraction)
                else Modifier
            )
            .plate(shape = shape, dark = dark, oled = oled, lift = lift)
            .then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier)
    ) {
        // A Box sets no content colour, and Text falls back to black.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}
