package eu.emufii.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AccentCuts(
    val bright: Color,
    val deep: Color,
    val ink: Color
) {
    val soft: Color get() = bright.copy(alpha = 0.20f)
}

val TealCuts = AccentCuts(Teal.bright, Teal.deep, Teal.ink)

val CoralCuts = AccentCuts(Coral.bright, Coral.deep, Coral.ink)

val LocalAccent = staticCompositionLocalOf { TealCuts }
