// AlbumGradient.kt

package com.mochyy.mochymusic.ui.component

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.mochyy.mochymusic.ui.theme.PlayerColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// colours already read from a cover, so a card scrolling back in starts on them instead of decoding the cover again
private val readGradients = LruCache<String, List<Color>>(96)

@Composable
fun AlbumGradient(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val fallbackColorInt = MaterialTheme.colorScheme.primaryContainer.toArgb()

    val known = remember(thumbnailUrl) { thumbnailUrl?.let { readGradients[it] } }
    var extractedColors by remember(thumbnailUrl) { mutableStateOf(known.orEmpty()) }
    // how far the colours have come in from the surface; colours read before start there
    val fade = remember(thumbnailUrl) { Animatable(if (known != null) 1f else 0f) }

    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl == null || extractedColors.isNotEmpty()) return@LaunchedEffect
        val colors = withContext(Dispatchers.IO) { readGradientColors(context, thumbnailUrl, fallbackColorInt) }
        if (colors.isEmpty()) return@LaunchedEffect
        readGradients.put(thumbnailUrl, colors)
        extractedColors = colors
        fade.animateTo(1f, tween(durationMillis = 800))
    }

    // read while drawing, so the colours fading in repaint this box instead of recomposing it every frame
    Box(
        modifier = modifier.drawBehind {
            val t = fade.value
            val top = extractedColors.getOrNull(0)?.copy(alpha = 0.5f) ?: surfaceColor
            val middle = extractedColors.getOrNull(1)?.copy(alpha = 0.3f) ?: surfaceColor
            drawRect(
                Brush.verticalGradient(
                    0.0f to lerp(surfaceColor, top, t),
                    0.5f to lerp(surfaceColor, middle, t),
                    1.0f to surfaceColor,
                )
            )
        }
    )
}

// the cover's gradient colours, read from a small software copy of it
private suspend fun readGradientColors(context: Context, url: String, fallbackColor: Int): List<Color> = try {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(100, 100)
        .allowHardware(false)
        .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
        .build()
    val bitmap = context.imageLoader.execute(request).image?.toBitmap()
    if (bitmap == null) {
        emptyList()
    } else {
        val palette = withContext(Dispatchers.Default) {
            Palette.from(bitmap)
                .maximumColorCount(8)
                .resizeBitmapArea(100 * 100)
                .generate()
        }
        PlayerColorExtractor.extractGradientColors(palette = palette, fallbackColor = fallbackColor)
    }
} catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    emptyList()
}
