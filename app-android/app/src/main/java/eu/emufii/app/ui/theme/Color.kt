package eu.emufii.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The HOME MENU palette. See [Direction].
 *
 * Three grounds, one accent, and nothing else has a hue. A handheld's menu is a
 * tray of coloured objects on a neutral shell: every colour in this file that
 * isn't [TrayCyan] is a grey with a few degrees of blue in it, because the box
 * art has to be the only thing shouting.
 */

// ---------------------------------------------------------------- the shell

/**
 * Daylight. A cool silver, not white: the plates are white, and a white shell
 * would leave them nothing to sit on. Sampled dark enough that a plate's
 * hairline is legible without an outline being drawn twice.
 */
val ShellLight = Color(0xFFDCE1E9)
val ShellLightLow = Color(0xFFC9D0DB)

/** Night. The same tray under a lamp: blue-black, never neutral grey. */
val ShellDark = Color(0xFF121721)
val ShellDarkLow = Color(0xFF0C1017)

/** OLED. Exactly off. A black pixel is a pixel not lit, and 0xFF050505 is lit. */
val ShellOled = Color(0xFF000000)

// --------------------------------------------------------------- the plates

/** White plastic, the light theme's plate. */
val PlateLight = Color(0xFFFDFDFE)
val PlateLightLow = Color(0xFFF0F2F6)

/** Night plastic. Lit from the top, so it comes in a pair. */
val PlateDark = Color(0xFF222A38)
val PlateDarkLow = Color(0xFF19202B)

val PlateOled = Color(0xFF15181F)
val PlateOledLow = Color(0xFF0B0D11)

/**
 * The moulded edge: a hairline a shade darker than the plate on light, a shade
 * lighter on dark. It is what makes a plate an object rather than a fill, and it
 * is the only separator left on OLED, where a shadow draws nothing.
 */
// Deepened on 2026-08-22: at 0x1F the contour was gone at glance distance and a
// white plate read as a flat card carried by its shadow alone. A moulding has an
// edge you can see without looking for it.
val EdgeLight = Color(0x3D0B1220)
val EdgeDark = Color(0x1FFFFFFF)
val EdgeOled = Color(0x33FFFFFF)

/** The lit top of a moulded edge, one hairline inside the plate's contour. */
val BevelLight = Color(0xCCFFFFFF)
val BevelDark = Color(0x1AFFFFFF)

// ------------------------------------------------------------------- the ink

val InkText = Color(0xFF1B2430)
val InkTextMuted = Color(0xFF5C6675)
val InkDarkText = Color(0xFFEAEFF6)
val InkDarkTextMuted = Color(0xFF95A0B1)

// --------------------------------------------------------------- the cursor

/**
 * Tray cyan: the cursor, the primary action, the current selection. Nothing
 * else. Spending it on decoration is what would make "where am I?" a question
 * again, and on a handheld the cursor is permanently somewhere.
 */
val TrayCyan = Color(0xFF14B4E4)
val TrayCyanSoft = Color(0x3314B4E4)

/** Its deep end, for text and icons that have to sit ON cyan-lit surfaces. */
val TrayCyanInk = Color(0xFF04384B)

/**
 * The filled-button cyan, and the reason there are two.
 *
 * [TrayCyan] is a light: at 4.7:1 against black it is perfect for a cursor
 * glowing on a dark tray, and hopeless as a background for white text — 2.2:1,
 * which is unreadable and not arguable. So the light theme fills its primary
 * button with this deeper cut (4.6:1 under white) and keeps the bright one for
 * the cursor. The dark theme has no such problem: there the bright cyan carries
 * [TrayCyanInk] on top, at 8:1.
 */
val TrayCyanDeep = Color(0xFF0A7899)

/**
 * Warning red, borrowed from the shell of the console this world comes from.
 * Errors and destructive confirmations only; it appears perhaps twice in the
 * whole app, which is why it reads when it does.
 */
val ShellRed = Color(0xFFE0452F)

/**
 * Legacy alias. The green ring is gone — the cursor is cyan now, one accent for
 * one meaning — but several screens still name this colour for a "ready" state.
 */
val AccentGreen = Color(0xFF3ECF9A)
val Accent = TrayCyan
val AccentSoft = TrayCyanSoft
