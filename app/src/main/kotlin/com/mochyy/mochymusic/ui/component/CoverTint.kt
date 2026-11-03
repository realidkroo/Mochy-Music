// CoverTint.kt

package com.mochyy.mochymusic.ui.component

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.mochyy.mochymusic.ui.theme.PlayerColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// what a big card sits on until its covers are read, picked by a hash so each card keeps its own
private val CardFallbackGradients = listOf(
    listOf(Color(0xFF2B1B38), Color(0xFF151521), Color.Black),
    listOf(Color(0xFF1B2838), Color(0xFF0F1521), Color.Black),
    listOf(Color(0xFF381B24), Color(0xFF1F0F15), Color.Black),
    listOf(Color(0xFF1B382D), Color(0xFF0F1F17), Color.Black),
    listOf(Color(0xFF2D2B55), Color(0xFF151521), Color.Black),
)

// how far each stop is pulled toward black, top to bottom, so the white text stays readable on any cover
private val CardTintDarkening = floatArrayOf(0.65f, 0.75f, 0.85f)

// colours already read from a set of covers, so a card scrolling back in starts on them instead of tinting all over again
private val readTints = LruCache<List<String>, List<Color>>(48)

/** the dark gradient behind Home's big community and artist cards, tinted by their first covers once those are read */
@Composable
fun Modifier.coverTintBackground(thumbnails: List<String>, seed: String): Modifier {
    val context = LocalContext.current
    var tint by remember(thumbnails) { mutableStateOf(readTints[thumbnails].orEmpty()) }
    LaunchedEffect(thumbnails) {
        if (tint.isNotEmpty() || thumbnails.isEmpty()) return@LaunchedEffect
        val read = withContext(Dispatchers.IO) { readCoverTint(context, thumbnails) }
        if (read.isNotEmpty()) {
            readTints.put(thumbnails, read)
            tint = read
        }
    }

    val fallback = CardFallbackGradients[(seed.hashCode() and 0x7FFFFFFF) % CardFallbackGradients.size]
    val top = animateColorAsState(tintStop(tint, fallback, 0), tween(800), label = "cardTintTop")
    val middle = animateColorAsState(tintStop(tint, fallback, 1), tween(800), label = "cardTintMiddle")
    val bottom = animateColorAsState(tintStop(tint, fallback, 2), tween(800), label = "cardTintBottom")

    // read in draw only, so the tint fading in repaints the card instead of recomposing it every frame
    return drawBehind {
        drawRect(Brush.verticalGradient(listOf(top.value, middle.value, bottom.value)))
    }
}

private fun tintStop(tint: List<Color>, fallback: List<Color>, index: Int): Color =
    tint.getOrNull(index)
        ?.let { Color(ColorUtils.blendARGB(it.toArgb(), android.graphics.Color.BLACK, CardTintDarkening[index])) }
        ?: fallback[index]

// up to two colours from each cover, read from a small software copy of it
private suspend fun readCoverTint(context: Context, thumbnails: List<String>): List<Color> = try {
    val colors = mutableListOf<Color>()
    for (thumbnail in thumbnails) {
        val request = ImageRequest.Builder(context)
            .data(thumbnail)
            .size(100, 100)
            .allowHardware(false)
            .build()
        val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: continue
        val palette = Palette.from(bitmap)
            .maximumColorCount(4)
            .resizeBitmapArea(100 * 100)
            .generate()
        colors += PlayerColorExtractor.extractGradientColors(
            palette = palette,
            fallbackColor = android.graphics.Color.DKGRAY,
        ).take(2)
    }
    colors.distinct().take(3)
} catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    emptyList()
}
