// SpinningArtwork.kt

package com.mochyy.mochymusic.discord

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import com.mochyy.mochymusic.ui.utils.resize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The "rotating artwork" pictures: the artwork cut into a circle and turned a full circle, as a
 * looping GIF. Discord can only show pictures that live on the web, so the GIF goes to a free
 * file host (uguu.se first, which deletes it after three hours). Each artwork is made once and
 * reused for every song that shares it (a whole album, usually).
 */
internal class SpinningArtwork(
    private val scope: CoroutineScope,
    /** a GIF finished: send the status again so it shows */
    private val onReady: () -> Unit,
    // reads artwork that's on the phone (a local song's cover), which the web download can't
    private val loadLocal: suspend (source: String, size: Int) -> Bitmap? = { _, _ -> null },
) {
    private val made = ConcurrentHashMap<String, PictureHosts.Hosted>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val working = ConcurrentHashMap.newKeySet<String>()

    /** The GIF's address when it's ready. Until then null, and it gets made in the background. */
    fun urlFor(sourceUrl: String, style: SpinStyle): String? {
        // a different speed is a different GIF
        val key = "$sourceUrl|${style.secondsPerTurn}|${style.fps}"
        val now = System.currentTimeMillis()
        made[key]?.let { if (now < it.usableUntilMs) return it.url }
        if (failedAt[key]?.let { now - it < RetryAfterFailureMs } == true) return null
        if (working.add(key)) {
            scope.launch {
                try {
                    made[key] = PictureHosts.upload(render(download(sourceUrl), style), "mochy.gif", "image/gif")
                    onReady()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(LogTag).w(e, "couldn't make the spinning artwork")
                    failedAt[key] = System.currentTimeMillis()
                } finally {
                    working.remove(key)
                }
            }
        }
        return null
    }

    private suspend fun download(url: String): Bitmap = withContext(Dispatchers.IO) {
        if (!PresenceImages.isWebImage(url)) return@withContext loadLocal(url, SourceSize) ?: error("artwork on the phone couldn't be read")
        val request = Request.Builder().url(url.resize(SourceSize, SourceSize)).build()
        val bytes = DiscordHttp.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "artwork download failed: HTTP ${response.code}" }
            response.body!!.bytes()
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("artwork isn't a picture")
    }

    /** Draws every frame of one full turn and packs them into the GIF. */
    private suspend fun render(source: Bitmap, style: SpinStyle): ByteArray = withContext(Dispatchers.Default) {
        val (frames, frameCentis) = framesFor(style)
        val square = centerSquare(source)
        val frame = Bitmap.createBitmap(Size, Size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        val shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        val matrix = Matrix()
        val pixels = IntArray(Size * Size)
        val half = Size / 2f
        val scale = Size.toFloat() / square.width

        fun draw(degrees: Float) {
            frame.eraseColor(Color.TRANSPARENT)
            matrix.setScale(scale, scale)
            matrix.postRotate(degrees, half, half)
            shader.setLocalMatrix(matrix)
            canvas.drawCircle(half, half, half, paint)
            frame.getPixels(pixels, 0, Size, 0, 0, Size, Size)
        }
        // the first frame holds every colour any frame will have
        draw(0f)
        val palette = Palette.of(pixels)
        val encoder = GifEncoder(Size, Size, palette.colors)
        val indexes = ByteArray(Size * Size)
        for (i in 0 until frames) {
            if (i > 0) draw(360f * i / frames)
            for (p in pixels.indices) indexes[p] = palette.indexOf(pixels[p]).toByte()
            encoder.addFrame(indexes, frameCentis)
        }
        frame.recycle()
        if (square !== source) square.recycle()
        encoder.finish()
    }

    /**
     * How many frames one turn gets, and how long each shows (in hundredths of a second). A slow
     * turn at a high frame rate would make a GIF of many megabytes, so frames stop at [MaxFrames]
     * and each one shows a little longer instead: the turn keeps its time, only the rate drops.
     */
    private fun framesFor(style: SpinStyle): Pair<Int, Int> {
        val turnCentis = style.secondsPerTurn * 100
        var centis = (100f / style.fps).roundToInt().coerceAtLeast(2)
        if (turnCentis / centis > MaxFrames) centis = ceil(turnCentis / MaxFrames.toFloat()).toInt()
        val frames = (turnCentis / centis.toFloat()).roundToInt().coerceAtLeast(MinFrames)
        return frames to centis
    }

    private fun centerSquare(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        if (source.width == source.height) return source
        return Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    }

    /**
     * Up to 255 colours picked from the picture by median cut, and a quick lookup from any colour
     * to its nearest one. See-through pixels map to the GIF's see-through slot.
     */
    private class Palette(val colors: IntArray) {
        // nearest colour for each 5-bit-per-channel colour, worked out the first time it's asked
        private val nearest = IntArray(1 shl 15) { -1 }

        fun indexOf(argb: Int): Int {
            if (argb ushr 24 < 128) return GifEncoder.Transparent
            val key = (argb shr 9 and 0x7C00) or (argb shr 6 and 0x3E0) or (argb shr 3 and 0x1F)
            val known = nearest[key]
            if (known >= 0) return known
            val r = argb shr 16 and 0xFF
            val g = argb shr 8 and 0xFF
            val b = argb and 0xFF
            var best = 0
            var bestDistance = Int.MAX_VALUE
            for (i in colors.indices) {
                val c = colors[i]
                val dr = (c shr 16 and 0xFF) - r
                val dg = (c shr 8 and 0xFF) - g
                val db = (c and 0xFF) - b
                val distance = 2 * dr * dr + 4 * dg * dg + 3 * db * db
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = i
                }
            }
            nearest[key] = best
            return best
        }

        companion object {
            fun of(pixels: IntArray): Palette {
                val opaque = pixels.filter { it ushr 24 >= 128 }.toIntArray()
                if (opaque.isEmpty()) return Palette(intArrayOf(0))
                val boxes = mutableListOf(opaque)
                while (boxes.size < GifEncoder.Transparent) {
                    val widest = boxes.withIndex().filter { it.value.size > 1 }.maxByOrNull { spread(it.value) } ?: break
                    if (spread(widest.value) == 0) break
                    val (low, high) = split(widest.value)
                    boxes[widest.index] = low
                    boxes.add(high)
                }
                return Palette(boxes.map(::average).toIntArray())
            }

            private fun channel(argb: Int, which: Int) = argb shr (16 - which * 8) and 0xFF

            private fun range(box: IntArray, which: Int): Int {
                var min = 255
                var max = 0
                for (c in box) {
                    val v = channel(c, which)
                    if (v < min) min = v
                    if (v > max) max = v
                }
                return max - min
            }

            private fun spread(box: IntArray) = maxOf(range(box, 0), range(box, 1), range(box, 2))

            private fun split(box: IntArray): Pair<IntArray, IntArray> {
                val which = (0..2).maxBy { range(box, it) }
                val sorted = box.sortedBy { channel(it, which) }
                val middle = sorted.size / 2
                return sorted.subList(0, middle).toIntArray() to sorted.subList(middle, sorted.size).toIntArray()
            }

            private fun average(box: IntArray): Int {
                var r = 0L
                var g = 0L
                var b = 0L
                for (c in box) {
                    r += c shr 16 and 0xFF
                    g += c shr 8 and 0xFF
                    b += c and 0xFF
                }
                val n = box.size
                return ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
            }
        }
    }

    companion object {
        private const val LogTag = "SpinningArtwork"

        /** Discord shows the small picture at 40px at most; 96 stays sharp on dense screens */
        private const val Size = 96
        private const val SourceSize = 256
        /** about 3 MB of GIF at 96px; past that it's slow to make and to upload on mobile data */
        private const val MaxFrames = 400
        private const val MinFrames = 12

        private const val RetryAfterFailureMs = 10 * 60_000L
    }
}
