// Type.kt

package com.mochyy.mochymusic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R

private val Inter = FontFamily(
    Font(R.font.inter_thin, FontWeight.Thin),
    Font(R.font.inter_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

/** a look module's font, standing in for inter across the app while that look is on; null is inter */
object LookFont {
    var family: FontFamily? by mutableStateOf(null)
}

// read through a state, so text set in it follows a look's font turning on or off
val InterFontFamily: FontFamily get() = LookFont.family ?: Inter

private val GlobalFontWeight = FontWeight.SemiBold
private val GlobalLetterSpacing = (-0.05).em

/** the app's text styles in [font], rebuilt when a look's font comes or goes */
fun appTypography(font: FontFamily) = Typography(
    displayLarge = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    displayMedium = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    displaySmall = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    headlineLarge = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    headlineMedium = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    headlineSmall = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    titleLarge = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    titleMedium = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    titleSmall = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    bodyLarge = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    bodyMedium = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    bodySmall = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    labelLarge = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    labelMedium = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = GlobalLetterSpacing
    ),
    labelSmall = TextStyle(
        fontFamily = font,
        fontWeight = GlobalFontWeight,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = GlobalLetterSpacing
    )
)
