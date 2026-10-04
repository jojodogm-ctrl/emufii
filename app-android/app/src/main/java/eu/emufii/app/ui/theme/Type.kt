package eu.emufii.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import eu.emufii.app.R

/** Rounded M+ (OFL), Latin subset only: ~105 KB a weight instead of 3.4 MB. */
private val Rounded = FontFamily(
    Font(R.font.rounded_regular, FontWeight.Normal),
    Font(R.font.rounded_medium, FontWeight.Medium),
    // The family has no semibold cut; bold stands in.
    Font(R.font.rounded_bold, FontWeight.SemiBold),
    Font(R.font.rounded_bold, FontWeight.Bold),
    Font(R.font.rounded_extrabold, FontWeight.ExtraBold),
    Font(R.font.rounded_black, FontWeight.Black)
)

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
    // Tracked out: at this size a rounded face sets too tight to read at arm's length.
    labelSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.8.sp)
)
