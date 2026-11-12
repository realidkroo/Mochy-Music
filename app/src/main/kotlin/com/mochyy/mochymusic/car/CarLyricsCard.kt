// CarLyricsCard.kt

package com.mochyy.mochymusic.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.ui.utils.resize
import java.io.ByteArrayOutputStream

// the picture the car shows in place of the cover while lyrics are on: the line before faint, the sung line big with its sung words lit, and the next one under it
internal class CarLyricsCard(private val context: Context) {
    private val bold: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_bold) }.getOrNull()
        ?: Typeface.create("sans-serif", Typeface.BOLD)
    private val medium: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_medium) }.getOrNull()
        ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)

    // the song's cover blurred and dimmed for the card to sit on; null falls back to a plain dark gradient
    suspend fun background(thumbnailUrl: String?): Bitmap? {
        val url = thumbnailUrl?.resize(BackgroundSourcePx, BackgroundSourcePx) ?: return null
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        val cover = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null
        // shrinking to a few pixels and growing back with filtering is a soft blur that costs nothing on the cpu
        return cover.scale(BlurTinyPx, BlurTinyPx).scale(BlurMidPx, BlurMidPx).scale(SizePx, SizePx)
    }

    // [sung] is how many words of the line have started, or -1 for a line without word timing
    fun render(background: Bitmap?, lines: CarLyricLines, index: Int, sung: Int): ByteArray {
        val card = createBitmap(SizePx, SizePx)
        val canvas = Canvas(card)
        drawBackdrop(canvas, background)

        val width = SizePx - MarginPx * 2
        val line = lines.lines[index]
        val previous = lines.lines.take(index).lastOrNull { it.text.isNotBlank() }?.text
        val next = lines.nextSungAfter(index)?.text

        val current = fitCurrentLine(sungText(line.text, line.words, sung), width)
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = medium; textSize = SideTextPx; color = Color.argb(95, 255, 255, 255) }
        val nextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = NextTextPx; color = Color.argb(150, 255, 255, 255) }
        val before = previous?.let { layout(it, small, width, 2) }
        val after = next?.let { layout(it, nextPaint, width, 2) }

        val total = (before?.height?.plus(GapPx) ?: 0) + current.height + (after?.height?.plus(GapPx) ?: 0)
        var top = ((SizePx - total) / 2f).coerceAtLeast(MarginPx.toFloat())
        for (block in listOfNotNull(before, current, after)) {
            canvas.save()
            canvas.translate(MarginPx.toFloat(), top)
            block.draw(canvas)
            canvas.restore()
            top += block.height + GapPx
        }
        return card.toJpeg().also { card.recycle() }
    }

    private fun drawBackdrop(canvas: Canvas, background: Bitmap?) {
        if (background != null) {
            canvas.drawBitmap(background, 0f, 0f, null)
        } else {
            val fill = LinearGradient(0f, 0f, SizePx.toFloat(), SizePx.toFloat(), 0xFF2B2B33.toInt(), 0xFF0B0B0F.toInt(), Shader.TileMode.CLAMP)
            canvas.drawPaint(Paint().apply { shader = fill })
        }
        // dimmed enough that white text reads on any cover, darker at the bottom where the car may lay its own controls
        canvas.drawColor(Color.argb(120, 0, 0, 0))
        val fade = LinearGradient(0f, 0f, 0f, SizePx.toFloat(), Color.argb(40, 0, 0, 0), Color.argb(150, 0, 0, 0), Shader.TileMode.CLAMP)
        canvas.drawPaint(Paint().apply { shader = fade })
    }

    // the sung part bright and the rest dim, matched to the words by where they sit in the line's own text
    private fun sungText(text: String, words: List<com.mochyy.mochymusic.lyrics.WordTimestamp>?, sung: Int): CharSequence {
        if (sung < 0 || words.isNullOrEmpty()) return text
        val ranges = LyricsUtils.wordCharRanges(text, words)
        val sungEnd = ranges.take(sung).lastOrNull { !it.isEmpty() }?.let { it.last + 1 } ?: 0
        return SpannableString(text).apply {
            if (sungEnd > 0) setSpan(ForegroundColorSpan(Color.WHITE), 0, sungEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (sungEnd < text.length) setSpan(ForegroundColorSpan(Color.argb(105, 255, 255, 255)), sungEnd, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    // as big as fits in three lines and half the card, so a long line shrinks instead of getting cut
    private fun fitCurrentLine(text: CharSequence, width: Int): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; color = Color.WHITE }
        var size = CurrentTextMaxPx
        while (true) {
            paint.textSize = size
            val fitted = layout(text, paint, width, 4)
            if ((fitted.lineCount <= 3 && fitted.height <= SizePx * 0.5f) || size <= CurrentTextMinPx) return fitted
            size -= 4f
        }
    }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.04f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    private fun Bitmap.toJpeg(): ByteArray = ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.JPEG, JpegQuality, out)
        out.toByteArray()
    }

    private companion object {
        // big enough to stay sharp on a wide screen, small enough that each word step is a quick send to the car
        const val SizePx = 720
        const val MarginPx = 56
        const val GapPx = 30
        const val CurrentTextMaxPx = 74f
        const val CurrentTextMinPx = 40f
        const val SideTextPx = 34f
        const val NextTextPx = 38f
        const val JpegQuality = 86
        const val BackgroundSourcePx = 226
        const val BlurTinyPx = 12
        const val BlurMidPx = 90
    }
}
