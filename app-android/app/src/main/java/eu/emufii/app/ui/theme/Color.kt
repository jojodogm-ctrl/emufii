package eu.emufii.app.ui.theme

import androidx.compose.ui.graphics.Color

val ShellLight = Color(0xFFF1EFEA)
val ShellLightLow = Color(0xFFE2DFD7)

val ShellDark = Color(0xFF120F1D)
val ShellDarkLow = Color(0xFF090711)

/** True black: OLED pixels stay off. */
val ShellOled = Color(0xFF000000)

val PlateLight = Color(0xFFFFFFFF)
val PlateLightLow = Color(0xFFF7F5F1)

val PlateDark = Color(0xFF272238)
val PlateDarkLow = Color(0xFF1C1929)

val PlateOled = Color(0xFF16131F)
val PlateOledLow = Color(0xFF0F0D17)

val EdgeLight = Color(0x52241610)
val EdgeDark = Color(0x2EFFFFFF)
val EdgeOled = Color(0x52FFFFFF)

val InkText = Color(0xFF221B26)
val InkTextMuted = Color(0xFF6E6475)
val InkDarkText = Color(0xFFF0EAF5)
val InkDarkTextMuted = Color(0xFF9B93AC)

val GlyphInk = InkText

object Coral {
    val bright = Color(0xFFEE6FA3)
    val deep = Color(0xFFC24B7E)
    val ink = Color(0xFF5A1D3E)
    val darkBright = Color(0xFFF793BC)
    val soft: Color get() = bright.copy(alpha = 0.20f)
}

object Teal {
    val bright = Color(0xFF3FCFC0)
    val deep = Color(0xFF0E9C8F)
    val ink = Color(0xFF0A4A44)
    val darkBright = Color(0xFF5CE0D2)
    val soft: Color get() = bright.copy(alpha = 0.20f)
}

object Shelf {
    const val fillLight = 0.17f
    const val fillDark = 0.14f
    const val fillOled = 0.13f
}

val Violet = Color(0xFF6B72E0)
val VioletDark = Color(0xFF8E93EC)

val GoodLight = Color(0xFF1FA98B)
val GoodDark = Color(0xFF3BC4A6)

val WarnLight = Color(0xFFC98A12)
val WarnDark = Color(0xFFE3A83C)

val ErrorLight = Color(0xFFE5604F)
val ErrorDark = Color(0xFFF0796A)

val InfoLight = Color(0xFF5A8FD8)
val InfoDark = Color(0xFF82AFE6)

@Deprecated("DUOTONE SHELVES: use Teal.bright", ReplaceWith("Teal.bright"))
val TrayCyan = Teal.bright

@Deprecated("DUOTONE SHELVES: use Teal.soft", ReplaceWith("Teal.soft"))
val TrayCyanSoft = Teal.soft

@Deprecated("DUOTONE SHELVES: use Teal.ink", ReplaceWith("Teal.ink"))
val TrayCyanInk = Teal.ink

@Deprecated("DUOTONE SHELVES: use Teal.deep", ReplaceWith("Teal.deep"))
val TrayCyanDeep = Teal.deep

@Deprecated("DUOTONE SHELVES: use ErrorLight/ErrorDark", ReplaceWith("ErrorLight"))
val ShellRed = ErrorLight

@Deprecated("DUOTONE SHELVES: use GoodLight/GoodDark", ReplaceWith("GoodLight"))
val AccentGreen = GoodLight

@Deprecated("DUOTONE SHELVES: use Teal.bright", ReplaceWith("Teal.bright"))
val Accent = Teal.bright

@Deprecated("DUOTONE SHELVES: use Teal.soft", ReplaceWith("Teal.soft"))
val AccentSoft = Teal.soft
