// LikedCover.kt
//
// Liked songs' cover, the same everywhere it shows (Home, the Library, playlist lists, the Liked
// page itself): the colours of the four songs liked last, blended softly corner to corner, with a
// white heart on top. it's drawn rather than a picture, so it's a handful of gradients at any size
// and doesn't need blur, which older phones can't do. LikedCoverImage saves the same drawing as a
// picture for the places that only take an image link.

package com.mochyy.mochymusic.ui.component

import android.content.Context
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.theme.CoverThemeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

object LikedCoverColors {
    /** before anything is liked, or a cover can't be read: the cover from the design */
    val Default = listOf(Color(0xFF6E3F73), Color(0xFF9B6A80), Color(0xFF8C3B3E), Color(0xFF6B7438))

    private val cache = LruCache<String, Int>(64)

    /** the last set worked out, so a second copy of the cover (the page it opens) starts on it */
    @Volatile
    var last: List<Color> = Default
        private set

    /** the colours for these covers, top-left, top-right, bottom-left, bottom-right */
    suspend fun of(context: Context, urls: List<String>): List<Color> {
        val read = urls.take(4).mapNotNull { extract(context, it) }
        val colors = when {
            read.isEmpty() -> Default
            // fewer than four liked: the ones there are fill the rest, a shade apart so it isn't flat
            else -> List(4) { i -> read.getOrNull(i) ?: shade(read[i % read.size], if (i % 2 == 0) -0.08f else 0.07f) }
        }
        last = colors
        return colors
    }

    /** the Liked page's theme colour: the bottom of the cover, pushed dark like a sampled cover is */
    fun theme(colors: List<Color>): Color {
        val bottom = ColorUtils.blendARGB(colors[2].toArgb(), colors[3].toArgb(), 0.5f)
        return Color(CoverThemeColor.darken(bottom))
    }

    private suspend fun extract(context: Context, url: String): Color? {
        cache.get(url)?.let { return Color(it) }
        return try {
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(112, 112)
                    .allowHardware(false)
                    .build()
                val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return@withContext null
                val argb = withContext(Dispatchers.Default) {
                    val palette = Palette.from(bitmap).maximumColorCount(12).generate()
                    // the colour the cover is mostly made of, leaning towards ones with some colour in
                    // them and away from near-black and near-white backgrounds
                    palette.swatches.maxByOrNull { swatch ->
                        val (_, saturation, lightness) = swatch.hsl
                        swatch.population * (0.35f + saturation) * (1.15f - abs(lightness - 0.45f) * 1.6f).coerceAtLeast(0.1f)
                    }?.rgb?.let(::tone)
                } ?: return@withContext null
                cache.put(url, argb)
                Color(argb)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    // keeps the hue and evens out the rest, so no one cover shouts over the others and the heart
    // always reads. a grey cover stays grey: lifting its saturation would turn it red (hue 0)
    private fun tone(argb: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        if (hsl[1] >= 0.08f) hsl[1] = hsl[1].coerceIn(0.2f, 0.58f)
        hsl[2] = hsl[2].coerceIn(0.28f, 0.5f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun shade(color: Color, by: Float): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[2] = (hsl[2] + by).coerceIn(0.2f, 0.58f)
        return Color(ColorUtils.HSLToColor(hsl))
    }
}

/** the colours of the four songs liked last; each cover is read once and remembered */
@Composable
fun rememberLikedCoverColors(): List<Color> {
    val database = LocalDatabase.current
    val context = LocalContext.current
    val urls by remember(database) { database.likedCovers() }.collectAsState(initial = null)
    var colors by remember { mutableStateOf(LikedCoverColors.last) }
    LaunchedEffect(urls) {
        urls?.let { colors = LikedCoverColors.of(context, it) }
    }
    return colors
}

// where each colour sits: just inside its corner, so the four run into each other
private val Anchors = listOf(Offset(0.1f, 0.08f), Offset(0.94f, 0.14f), Offset(0.06f, 0.9f), Offset(0.92f, 0.94f))

/**
 * the cover itself; it fills [modifier]'s size and is clipped by the caller. [heartSize] is the
 * heart's width against the cover's.
 */
@Composable
fun LikedCover(
    modifier: Modifier = Modifier,
    heartSize: Float = 0.4f,
    heartAlignment: Alignment = Alignment.Center,
) {
    val colors = rememberLikedCoverColors()
    val c0 by animateColorAsState(colors[0], tween(600), label = "likedCover0")
    val c1 by animateColorAsState(colors[1], tween(600), label = "likedCover1")
    val c2 by animateColorAsState(colors[2], tween(600), label = "likedCover2")
    val c3 by animateColorAsState(colors[3], tween(600), label = "likedCover3")

    Box(
        contentAlignment = heartAlignment,
        modifier = modifier.drawWithCache {
            val paints = LikedCoverPaints(listOf(c0, c1, c2, c3), size.width, size.height)
            onDrawBehind {
                drawIntoCanvas { canvas -> paints.draw(canvas.nativeCanvas, size.width, size.height) }
            }
        },
    ) {
        Icon(
            painter = painterResource(R.drawable.favorite_border),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .fillMaxWidth(heartSize)
                .aspectRatio(1f),
        )
    }
}

// the cover's gradients for one size and set of colours, so the live cover and its saved picture are drawn the same
private class LikedCoverPaints(corners: List<Color>, width: Float, height: Float) {
    // the four averaged, under everything, so no corner of it is ever left bare
    private val base = android.graphics.Paint().apply {
        color = corners.fold(0 to Color.Transparent) { (n, acc), c ->
            (n + 1) to Color(ColorUtils.blendARGB(acc.toArgb(), c.toArgb(), 1f / (n + 1)))
        }.second.toArgb()
    }

    // dithered: a soft blend this wide bands on 8-bit screens otherwise
    private val corners = corners.mapIndexed { i, c ->
        android.graphics.Paint().apply {
            isDither = true
            shader = RadialGradient(
                width * Anchors[i].x,
                height * Anchors[i].y,
                maxOf(width, height) * 0.9f,
                intArrayOf(c.toArgb(), c.copy(alpha = 0.72f).toArgb(), c.copy(alpha = 0f).toArgb()),
                floatArrayOf(0f, 0.36f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }

    // a little darker towards the edges, which gives it depth and keeps the heart on top
    private val shade = android.graphics.Paint().apply {
        isDither = true
        shader = RadialGradient(
            width / 2f,
            height / 2f,
            maxOf(width, height) * 0.78f,
            intArrayOf(0, 0, Color.Black.copy(alpha = 0.22f).toArgb()),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    fun draw(canvas: android.graphics.Canvas, width: Float, height: Float) {
        // a rect, not drawColor: that fills the whole clip, and on the liked page nothing clips the cover, so it greyed the screen under the songs
        canvas.drawRect(0f, 0f, width, height, base)
        corners.forEach { canvas.drawRect(0f, 0f, width, height, it) }
        canvas.drawRect(0f, 0f, width, height, shade)
    }
}

// liked songs' cover saved as a picture, for the places that only take an image link (home's cards, their colours, lists)
object LikedCoverImage {
    private const val Folder = "liked_cover"
    private const val Size = 512
    private const val HeartShare = 0.4f

    // the picture for the songs liked last, drawn once per set of colours and reused; null if it couldn't be written
    suspend fun url(context: Context, likedCovers: List<String>): String? {
        val colors = LikedCoverColors.of(context, likedCovers)
        val key = colors.joinToString("-") { Integer.toHexString(it.toArgb()) }
        return withContext(Dispatchers.IO) {
            val folder = java.io.File(context.filesDir, Folder).apply { mkdirs() }
            val file = java.io.File(folder, "liked.$key.webp")
            if (!file.exists()) {
                val written = runCatching { write(context, colors, file) }.isSuccess
                if (!written) return@withContext null
                // the colours moved on, so the older pictures are no use
                folder.listFiles()?.forEach { if (it != file) it.delete() }
            }
            android.net.Uri.fromFile(file).toString()
        }
    }

    private fun write(context: Context, colors: List<Color>, file: java.io.File) {
        val bitmap = android.graphics.Bitmap.createBitmap(Size, Size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        LikedCoverPaints(colors, Size.toFloat(), Size.toFloat()).draw(canvas, Size.toFloat(), Size.toFloat())
        androidx.core.content.ContextCompat.getDrawable(context, R.drawable.favorite_border)?.mutate()?.let { heart ->
            val side = (Size * HeartShare).toInt()
            val start = (Size - side) / 2
            heart.setBounds(start, start, start + side, start + side)
            heart.setTint(android.graphics.Color.WHITE)
            heart.draw(canvas)
        }
        @Suppress("DEPRECATION")
        val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            android.graphics.Bitmap.CompressFormat.WEBP
        }
        // written beside it first, so a cut-short write never leaves a broken picture in its place
        val partial = java.io.File(file.parentFile, file.name + ".part")
        partial.outputStream().buffered().use { bitmap.compress(format, 92, it) }
        bitmap.recycle()
        check(partial.renameTo(file)) { "couldn't put the liked cover in place" }
    }
}
