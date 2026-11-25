// BackdropBlurTransformation.kt

package com.mochyy.mochymusic.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.createBitmap
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.max
import kotlin.math.roundToInt

class BackdropBlurTransformation(private val radiusPx: Int) : Transformation() {

    override val cacheKey: String = "${this::class.qualifiedName}-$radiusPx"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (radiusPx <= 0) return input
        val width = input.width
        val height = input.height
        if (width <= 1 || height <= 1) return input

        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)
        boxBlurPixels(pixels, width, height, radiusPx)

        val output = createBitmap(width, height, input.config ?: Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}

// three sliding-window box passes give a gaussian blur in o(w*h) regardless of radius, reusing one line buffer
internal fun boxBlurPixels(pixels: IntArray, width: Int, height: Int, radius: Int) {
    if (radius <= 0 || width <= 1 || height <= 1) return
    val r = radius.coerceAtMost(max(width, height) - 1)
    val scratch = IntArray(max(width, height))
    repeat(3) {
        if (width > 1) boxBlurHorizontal(pixels, width, height, r.coerceAtMost(width - 1), scratch)
        if (height > 1) boxBlurVertical(pixels, width, height, r.coerceAtMost(height - 1), scratch)
    }
}

private fun boxBlurHorizontal(pixels: IntArray, width: Int, height: Int, radius: Int, row: IntArray) {
    if (radius <= 0) return
    val win = radius * 2 + 1
    val last = width - 1
    for (y in 0 until height) {
        val rowStart = y * width
        System.arraycopy(pixels, rowStart, row, 0, width)
        val first = row[0]
        var a = ((first ushr 24) and 0xFF) * (radius + 1)
        var r = ((first ushr 16) and 0xFF) * (radius + 1)
        var g = ((first ushr 8) and 0xFF) * (radius + 1)
        var b = (first and 0xFF) * (radius + 1)
        for (i in 1..radius) {
            val p = row[if (i <= last) i else last]
            a += (p ushr 24) and 0xFF
            r += (p ushr 16) and 0xFF
            g += (p ushr 8) and 0xFF
            b += p and 0xFF
        }
        for (x in 0 until width) {
            pixels[rowStart + x] = ((a / win) shl 24) or ((r / win) shl 16) or ((g / win) shl 8) or (b / win)
            val addIdx = x + radius + 1
            val subIdx = x - radius
            val add = row[if (addIdx <= last) addIdx else last]
            val sub = row[if (subIdx >= 0) subIdx else 0]
            a += ((add ushr 24) and 0xFF) - ((sub ushr 24) and 0xFF)
            r += ((add ushr 16) and 0xFF) - ((sub ushr 16) and 0xFF)
            g += ((add ushr 8) and 0xFF) - ((sub ushr 8) and 0xFF)
            b += (add and 0xFF) - (sub and 0xFF)
        }
    }
}

private fun boxBlurVertical(pixels: IntArray, width: Int, height: Int, radius: Int, col: IntArray) {
    if (radius <= 0) return
    val win = radius * 2 + 1
    val last = height - 1
    for (x in 0 until width) {
        for (y in 0 until height) col[y] = pixels[y * width + x]
        val first = col[0]
        var a = ((first ushr 24) and 0xFF) * (radius + 1)
        var r = ((first ushr 16) and 0xFF) * (radius + 1)
        var g = ((first ushr 8) and 0xFF) * (radius + 1)
        var b = (first and 0xFF) * (radius + 1)
        for (i in 1..radius) {
            val p = col[if (i <= last) i else last]
            a += (p ushr 24) and 0xFF
            r += (p ushr 16) and 0xFF
            g += (p ushr 8) and 0xFF
            b += p and 0xFF
        }
        for (y in 0 until height) {
            pixels[y * width + x] = ((a / win) shl 24) or ((r / win) shl 16) or ((g / win) shl 8) or (b / win)
            val addIdx = y + radius + 1
            val subIdx = y - radius
            val add = col[if (addIdx <= last) addIdx else last]
            val sub = col[if (subIdx >= 0) subIdx else 0]
            a += ((add ushr 24) and 0xFF) - ((sub ushr 24) and 0xFF)
            r += ((add ushr 16) and 0xFF) - ((sub ushr 16) and 0xFF)
            g += ((add ushr 8) and 0xFF) - ((sub ushr 8) and 0xFF)
            b += (add and 0xFF) - (sub and 0xFF)
        }
    }
}

// builds a small coil-cached blurred image request for api < 31 so static module backgrounds blur once off the main thread
@Composable
internal fun rememberFallbackBlurredImage(model: Any?, blurDp: Float, targetPx: Int = 192): Any? {
    val context = LocalContext.current
    val radiusPx = (blurDp * 0.45f).roundToInt().coerceIn(2, 24)
    return remember(context, model, radiusPx, targetPx) {
        if (model == null) null else buildFallbackBlurredRequest(context, model, radiusPx, targetPx)
    }
}

internal fun buildFallbackBlurredRequest(context: Context, model: Any, radiusPx: Int, targetPx: Int = 192): ImageRequest =
    ImageRequest.Builder(context)
        .data(model)
        .allowHardware(false)
        .transformations(BackdropBlurTransformation(radiusPx))
        .size(Size(targetPx, targetPx))
        .build()
