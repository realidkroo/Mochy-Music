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

internal val BundledInter = FontFamily(
    Font(R.font.inter_thin, FontWeight.Thin),
    Font(R.font.inter_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

// only swapped in when the user explicitly turns on bold for inter so stock screens stay identical
internal val InterBoldOverride = FontFamily(
    Font(R.font.inter_thin, FontWeight.Thin),
    Font(R.font.inter_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

internal val BundledBbhBartle = FontFamily(
    Font(R.font.bbh_bartle_regular, FontWeight.Normal),
)

/** a look module's font, standing in for inter across the app while that look is on; null is inter */
object LookFont {
    var family: FontFamily? by mutableStateOf(null)
}

// read during composition so look changes and global font picks update direct fontfamily call sites
val InterFontFamily: FontFamily
    get() = AppFonts.resolvedFor(FontRole.GLOBAL).family

// caches a base style per role by pointer check so reading it on every recomposition allocates nothing
internal class RoleTextStyle(
    private val role: FontRole,
    rawBase: TextStyle,
) {
    private val base = if (rawBase.fontFamily == null) rawBase.copy(fontFamily = BundledInter) else rawBase
    @Volatile private var lastResolved: ResolvedRoleFont? = null
    @Volatile private var cached: TextStyle = base

    fun current(): TextStyle {
        val resolved = AppFonts.resolvedFor(role)
        if (resolved === lastResolved) return cached
        val next = resolved.applyTo(base)
        lastResolved = resolved
        cached = next
        return next
    }
}

internal fun headingTextStyle(base: TextStyle): RoleTextStyle = RoleTextStyle(FontRole.HEADING, base)

internal fun subheadingTextStyle(base: TextStyle): RoleTextStyle = RoleTextStyle(FontRole.SUBHEADING, base)

internal fun globalTextStyle(base: TextStyle): RoleTextStyle = RoleTextStyle(FontRole.GLOBAL, base)

private val GlobalFontWeight = FontWeight.SemiBold
private val GlobalLetterSpacing = (-0.05).em

/** the app's text styles in [font], kept for callers that pass a single family */
fun appTypography(font: FontFamily): Typography {
    val single = ResolvedRoleFont(fontId = BuiltinInterId, family = font, isDefaultInter = font === BundledInter)
    return appTypography(ResolvedFonts(heading = single, subheading = single, global = single))
}

/** the app's text styles with heading, subheading, and global roles applied */
internal fun appTypography(resolved: ResolvedFonts): Typography {
    fun heading(sizeSp: Int, lineSp: Int) = resolved.heading.applyTo(
        TextStyle(
            fontFamily = resolved.heading.family,
            fontWeight = GlobalFontWeight,
            fontSize = sizeSp.sp,
            lineHeight = lineSp.sp,
            letterSpacing = GlobalLetterSpacing,
        ),
    )

    fun subheading(sizeSp: Int, lineSp: Int) = resolved.subheading.applyTo(
        TextStyle(
            fontFamily = resolved.subheading.family,
            fontWeight = GlobalFontWeight,
            fontSize = sizeSp.sp,
            lineHeight = lineSp.sp,
            letterSpacing = GlobalLetterSpacing,
        ),
    )

    fun body(sizeSp: Int, lineSp: Int) = resolved.global.applyTo(
        TextStyle(
            fontFamily = resolved.global.family,
            fontWeight = GlobalFontWeight,
            fontSize = sizeSp.sp,
            lineHeight = lineSp.sp,
            letterSpacing = GlobalLetterSpacing,
        ),
    )

    return Typography(
        displayLarge = heading(57, 64),
        displayMedium = heading(45, 52),
        displaySmall = heading(36, 44),
        headlineLarge = heading(32, 40),
        headlineMedium = heading(28, 36),
        headlineSmall = heading(24, 32),
        titleLarge = heading(22, 28),
        titleMedium = subheading(16, 24),
        titleSmall = subheading(14, 20),
        bodyLarge = body(16, 24),
        bodyMedium = body(14, 20),
        bodySmall = body(12, 16),
        labelLarge = body(14, 20),
        labelMedium = body(12, 16),
        labelSmall = body(11, 16),
    )
}
