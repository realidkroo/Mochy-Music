// ChevronMenuLabel.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.mochyy.mochymusic.ui.theme.InterFontFamily

/** the words beside a chevron menu's round buttons, on every player style */
@Composable
internal fun chevronMenuLabelStyle(color: Color, fontSize: TextUnit): TextStyle {
    val density = LocalDensity.current
    return remember(color, fontSize, density) {
        // a very soft shade, dark under light words and light under dark ones, so it lifts them without a hard edge
        val shade = if (color.luminance() > 0.5f) Color.Black else Color.White
        TextStyle(
            color = color,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize,
            // semibold sets a touch narrower than bold, so a little less tightening keeps the words as wide as they were
            letterSpacing = (-0.035).em,
            shadow = with(density) { Shadow(shade.copy(alpha = 0.35f), Offset(0f, 1.dp.toPx()), 8.dp.toPx()) },
        )
    }
}
