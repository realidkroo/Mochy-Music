// PillLook.kt

package com.mochyy.mochymusic.floating

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.mochyy.mochymusic.ui.theme.extractThemeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val DarkInk = Color(0xFF0D0D0E)
private val LightInk = Color.White

// the ink has to read this clearly on the bubble, whatever colour the cover gives it
private const val MinContrast = 4.5

// the waiting and soft greys are the ink at these strengths, so they follow it onto any bubble colour
private const val WaitingAlpha = 0.42f
private const val SoftAlpha = 0.58f

// the bubble's colours: the cover's accent as the fill, and the ink that reads on it
@Immutable
internal class PillColors(val bubble: Color, val ink: Color) {
    companion object {
        val Default = PillColors(Color(0xFFE8EEF1), DarkInk)

        // keeps the accent's hue, nudged lighter or darker only as far as the ink needs to read
        fun from(accent: Color?): PillColors {
            if (accent == null) return Default
            var fill = accent.copy(alpha = 1f).toArgb()
            val darkInk = ColorUtils.calculateContrast(DarkInk.toArgb(), fill) >= ColorUtils.calculateContrast(LightInk.toArgb(), fill)
            val ink = if (darkInk) DarkInk else LightInk
            val away = if (darkInk) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            var steps = 0
            while (ColorUtils.calculateContrast(ink.toArgb(), fill) < MinContrast && steps < 12) {
                fill = ColorUtils.blendARGB(fill, away, 0.1f)
                steps++
            }
            return PillColors(Color(fill), ink)
        }
    }
}

// the colours as they fade from one cover to the next, read while drawing so a fade repaints the pill instead of recomposing it
@Stable
internal class PillTint(
    private val bubbleState: State<Color>,
    private val inkState: State<Color>,
    private val waitingOverride: Color? = null,
    private val softOverride: Color? = null,
    private val progressOverride: Color? = null,
    private val trackOverride: Color? = null,
) {
    val bubble: Color get() = bubbleState.value
    val ink: Color get() = inkState.value

    // a line not sung yet, the same grey whether it waits underneath or is about to be sung on top, so moving up never flashes
    val waiting: Color get() = waitingOverride ?: inkState.value.copy(alpha = WaitingAlpha)

    // a reading or translation of the line being sung, quieter than the line but not waiting
    val soft: Color get() = softOverride ?: inkState.value.copy(alpha = SoftAlpha)

    /** optional ring/bar accent from a floating lyrics skin */
    val progress: Color? get() = progressOverride
    val track: Color? get() = trackOverride
}

internal val LocalPillTint = staticCompositionLocalOf<PillTint> { error("the pill's colours are provided by FloatingPill") }

// the cover's accent, read from a small software copy of it off the main thread; null when it can't be read
internal suspend fun coverAccent(context: Context, url: String?): Color? {
    if (url.isNullOrBlank()) return null
    return try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(96, 96)
            .allowHardware(false)
            .build()
        val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return null
        withContext(Dispatchers.Default) { bitmap.extractThemeColor() }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }
}

// a wide, soft shadow under one of the pill's rounded shapes; the system's elevation shadow came out tight and dark over other apps
internal fun Modifier.softShadow(corner: Dp, blur: Dp, offsetY: () -> Dp, alpha: () -> Float): Modifier {
    // a blurred draw only runs on the gpu from android 9, so older phones keep a light elevation shadow
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return shadow(3.dp, RoundedCornerShape(corner), clip = false)
    return drawWithCache {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL) }
        val radius = corner.toPx().coerceAtMost(size.minDimension / 2f)
        // pulled in a little at the sides, so it reads as a shape resting above the screen instead of a dark outline
        val inset = blur.toPx() * 0.2f
        onDrawBehind {
            val strength = alpha()
            if (strength <= 0f) return@onDrawBehind
            paint.color = android.graphics.Color.argb((strength.coerceIn(0f, 1f) * 255).roundToInt(), 0, 0, 0)
            val dy = offsetY().toPx()
            drawIntoCanvas { it.nativeCanvas.drawRoundRect(inset, dy, size.width - inset, size.height + dy, radius, radius, paint) }
        }
    }
}
