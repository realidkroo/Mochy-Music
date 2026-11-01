// ShareKit.kt
//
// The small pieces every lyrics share sheet uses: the big title with its BETA tag, the picker
// row's look (shared with the flying line so it lands on exactly that), and the shake for a tap
// that can't do anything.

package com.mochyy.mochymusic.ui.player.share

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.ui.component.BetaBadge
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal val ShareMuted = Color(0xFF9A9AA0)

// A picker row, shared with the flying line so it lands on exactly this.
internal val ShareRowCorner = 16.dp
internal val ShareRowPadH = 16.dp
internal val ShareRowFontSize = 15.sp
internal val ShareRowTextStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = ShareRowFontSize,
    letterSpacing = (-0.3).sp,
    color = Color.White,
)

/** A picked line's fill: the cover's two strongest colours, side to side across [width]. */
internal fun pickedBrush(palette: List<Color>, width: Float = Float.POSITIVE_INFINITY): Brush {
    val a = palette.getOrNull(0) ?: Color(0xFF5A5A5E)
    val b = palette.getOrNull(1) ?: a.copy(alpha = 0.7f)
    return Brush.horizontalGradient(listOf(a, b), startX = 0f, endX = width)
}

/**
 * A picker row's surface: grey when free, filled with the cover's colours when picked, and only
 * outlined in them when picked past what a card holds, since only Cinematic can show those.
 */
internal fun DrawScope.drawShareRowLook(
    fill: Float,
    outline: Float,
    palette: List<Color>,
    corner: Float,
    size: Size = this.size,
    alpha: Float = 1f,
) {
    val radius = CornerRadius(corner)
    drawRoundRect(MenuRowSurface, size = size, cornerRadius = radius, alpha = alpha)
    val brush = pickedBrush(palette, size.width)
    if (fill > 0f) drawRoundRect(brush, size = size, cornerRadius = radius, alpha = fill * alpha)
    if (outline > 0f) {
        val line = 2.dp.toPx()
        drawRoundRect(brush, size = size, cornerRadius = radius, alpha = 0.2f * outline * alpha)
        drawRoundRect(
            brush,
            topLeft = Offset(line / 2f, line / 2f),
            size = Size(size.width - line, size.height - line),
            cornerRadius = CornerRadius(corner - line / 2f),
            style = Stroke(line),
            alpha = outline * alpha,
        )
    }
    val free = (1f - fill - outline).coerceIn(0f, 1f)
    if (free > 0f) {
        drawRoundRect(Color.White.copy(alpha = 0.07f * free), size = size, cornerRadius = radius, style = Stroke(1.dp.toPx()), alpha = alpha)
    }
}

@Composable
internal fun ShareTitle(
    title: String,
    subtitle: String? = null,
    beta: Boolean = false,
    // a live line under the title instead of the fixed subtitle, for editors whose hint changes
    subtitleSlot: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.padding(top = 6.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                letterSpacing = (-1.6).sp,
                color = Color.White,
            )
            if (beta) {
                Spacer(Modifier.width(12.dp))
                BetaBadge()
            }
        }
        if (subtitleSlot != null) {
            subtitleSlot()
        } else if (subtitle != null) {
            Text(subtitle, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ShareMuted)
        }
    }
}

/** A quick side-to-side shake, for a tap that can't do anything. */
internal fun CoroutineScope.shake(offset: Animatable<Float, AnimationVector1D>) = launch {
    for (x in listOf(14f, -11f, 8f, -5f, 0f)) offset.animateTo(x, tween(45))
}
