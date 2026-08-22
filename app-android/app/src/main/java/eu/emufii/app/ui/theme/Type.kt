package eu.emufii.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import eu.emufii.app.R

/**
 * Rounded M+ (M PLUS Rounded 1c), under the OFL — `assets/ROUNDED-MPLUS-OFL.txt`.
 *
 * A rounded-terminal humanist sans, which is the voice of every console home
 * menu ever shipped: the strokes end in a moulded cap, the same radius the
 * plastic has. Poppins, which stood here before, is a geometric sans with cut
 * terminals — precise, cold, and the house style of a hundred startup decks. It
 * argued with the world this app is now built in.
 *
 * Shipped Latin-subset (~105 KB a weight instead of 3.4 MB): the family carries
 * the whole of Japanese, and the app speaks French and English.
 */
private val Rounded = FontFamily(
    Font(R.font.rounded_regular, FontWeight.Normal),
    Font(R.font.rounded_medium, FontWeight.Medium),
    // The family has no semibold cut; bold stands in, and the scale below simply
    // stops asking for the weight that does not exist.
    Font(R.font.rounded_bold, FontWeight.SemiBold),
    Font(R.font.rounded_bold, FontWeight.Bold),
    Font(R.font.rounded_extrabold, FontWeight.ExtraBold),
    Font(R.font.rounded_black, FontWeight.Black)
)

/**
 * One family, a tight scale (about 1.2 between steps), and letter-spacing that
 * opens up on small labels and closes on the big ones — the two habits of a
 * console's own type: nothing is set loose, and nothing shouts except a title.
 */
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Black, fontSize = 42.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp),
    displaySmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
    titleLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    // The engraved label: the small caps-ish line that names a section on a
    // console's own screens. Tracked out, because at this size a rounded face
    // sets too tight to read at arm's length.
    labelSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.8.sp)
)
