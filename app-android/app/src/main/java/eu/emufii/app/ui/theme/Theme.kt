package eu.emufii.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.RippleDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private fun lightScheme(accent: AccentCuts) = lightColorScheme(
    primary = accent.deep,
    onPrimary = Color.White,
    primaryContainer = accent.soft,
    onPrimaryContainer = accent.ink,
    secondary = GoodLight,
    onSecondary = Color.White,
    tertiary = Coral.deep,
    onTertiary = Color.White,
    background = ShellLight,
    onBackground = InkText,
    surface = PlateLight,
    onSurface = InkText,
    surfaceVariant = PlateLightLow,
    onSurfaceVariant = InkTextMuted,
    surfaceContainer = PlateLight,
    surfaceContainerLow = PlateLightLow,
    surfaceContainerHigh = PlateLight,
    outline = EdgeLight,
    outlineVariant = EdgeLight,
    error = ErrorLight,
    onError = Color.White
)

private fun darkScheme(accent: AccentCuts) = darkColorScheme(
    primary = accent.bright,
    onPrimary = accent.ink,
    primaryContainer = accent.soft,
    onPrimaryContainer = Color.White,
    secondary = GoodDark,
    onSecondary = Color(0xFF04241C),
    tertiary = Coral.darkBright,
    onTertiary = Coral.ink,
    background = ShellDark,
    onBackground = InkDarkText,
    surface = PlateDark,
    onSurface = InkDarkText,
    surfaceVariant = PlateDarkLow,
    onSurfaceVariant = InkDarkTextMuted,
    surfaceContainer = PlateDark,
    surfaceContainerLow = PlateDarkLow,
    surfaceContainerHigh = PlateDark,
    outline = EdgeDark,
    outlineVariant = EdgeDark,
    error = ErrorDark,
    onError = Color(0xFF2B0805)
)

private fun oledScheme(accent: AccentCuts) = darkScheme(accent).copy(
    background = ShellOled,
    surface = PlateOled,
    surfaceVariant = PlateOledLow,
    surfaceContainer = PlateOled,
    surfaceContainerLow = PlateOledLow,
    surfaceContainerHigh = PlateOled,
    outline = EdgeOled,
    outlineVariant = EdgeOled
)

/** Use this, not isSystemInDarkTheme: the in-app setting can differ from the system. */
val LocalEmufiiDarkTheme = staticCompositionLocalOf { false }

val LocalEmufiiOledTheme = staticCompositionLocalOf { false }

@Composable
fun EmufiiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    oled: Boolean = false,
    content: @Composable () -> Unit
) {
    val cuts = TealCuts
    val oledTheme = oled && darkTheme
    CompositionLocalProvider(
        LocalEmufiiDarkTheme provides darkTheme,
        LocalEmufiiOledTheme provides oledTheme,
        LocalAccent provides cuts
    ) {
        // A gamepad always holds focus, so Material's focus veil would grey every selection.
        CompositionLocalProvider(LocalRippleConfiguration provides NoFocusRipple) {
            MaterialTheme(
                colorScheme = when {
                    oledTheme -> oledScheme(cuts)
                    darkTheme -> darkScheme(cuts)
                    else -> lightScheme(cuts)
                },
                typography = Typography,
            ) {
                // Text and icons without a color outside a Surface default to black, whatever the theme.
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                    content = content
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private val NoFocusRipple = RippleConfiguration(
    rippleAlpha = RippleAlpha(
        draggedAlpha = RippleDefaults.RippleAlpha.draggedAlpha,
        focusedAlpha = 0f,
        hoveredAlpha = 0f,
        pressedAlpha = RippleDefaults.RippleAlpha.pressedAlpha
    )
)
