package eu.emufii.app.ui.screens.library

import androidx.compose.ui.graphics.Color
import eu.emufii.app.ui.theme.Coral
import eu.emufii.app.ui.theme.GoodLight
import eu.emufii.app.ui.theme.InfoLight
import eu.emufii.app.ui.theme.Teal
import eu.emufii.app.ui.theme.Violet
import eu.emufii.app.ui.theme.VioletDark
import eu.emufii.app.ui.theme.WarnLight
import kotlin.math.abs

/**
 * Remixed from the logo's two axes and their neighbouring semantic tones: no invented
 * hue.
 * pourquoi : docs/decisions/theme-duotone-shelves.md § CONSTRAINTS (no hard-coded hex)
 */
private val PALETTE = listOf(
    Teal.bright to Teal.deep,
    Coral.bright to Coral.deep,
    Violet to VioletDark,
    GoodLight to Teal.ink,
    WarnLight to Coral.ink,
    InfoLight to Violet,
    Coral.darkBright to Coral.ink,
    Teal.darkBright to Teal.ink,
    VioletDark to Coral.ink,
    Coral.deep to Teal.ink,
    Teal.deep to Coral.ink,
)

internal fun paletteFor(seed: String): Pair<Color, Color> {
    val h = abs(seed.hashCode())
    return PALETTE[h % PALETTE.size]
}
