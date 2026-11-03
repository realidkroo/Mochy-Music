package com.mochyy.mochymusic.ui.player.styles

import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.mochyy.mochymusic.ui.player.MinInterludeGapMs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** big type with no padding above or below the glyphs, so stacked lines sit tight like a poster */
internal fun posterType(size: TextUnit, weight: FontWeight = FontWeight.Bold) = TextStyle(
    fontFamily = com.mochyy.mochymusic.ui.theme.InterFontFamily,
    fontSize = size,
    lineHeight = size * 0.96f,
    fontWeight = weight,
    letterSpacing = (-0.035).em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    // these letters rise, zoom and slide every frame: snapped to whole pixels they shook against each other
    textMotion = TextMotion.Animated,
)

/** whether [text] reads right to left (Arabic, Hebrew...), judged by its first letter that has a direction */
internal fun isRtlText(text: CharSequence): Boolean =
    androidx.core.text.TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR.isRtl(text, 0, text.length)

/**
 * How big each word of a title or line is set: one short word (three letters or more, the first
 * shortest) is set much larger than the rest, the way a poster picks the word to shout, and very
 * long words step down so they still fit.
 */
internal fun wordScales(words: List<String>): FloatArray {
    val emphasised = words.withIndex()
        .filter { it.value.length >= 3 }
        .minByOrNull { it.value.length }?.index ?: -1
    return FloatArray(words.size) { i ->
        val length = words[i].length
        when {
            i == emphasised && words.size > 1 -> 1.55f
            length >= 11 -> 0.74f
            length >= 9 -> 0.86f
            else -> 1f
        }
    }
}

/**
 * A song title as a poster: mixed sizes ([wordScales]), tight lines, and on every new song the
 * words rise into place one after another. A right-to-left title runs its words from the right.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpressiveTitle(
    text: String,
    baseSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val words = remember(text) { text.split(' ').filter { it.isNotBlank() } }
    val scales = remember(words) { wordScales(words) }
    val rtl = remember(text) { isRtlText(text) }
    val enter = remember(text) { Animatable(0f) }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val fontFamilyResolver = LocalFontFamilyResolver.current
        val layoutDirection = LocalLayoutDirection.current
        val maxWidth = constraints.maxWidth
        val maxHeight = constraints.maxHeight
        val fitKey = TitleFitKey(text, baseSize, maxWidth, maxHeight, density.density, density.fontScale)
        // kept through a resize until the new size is ready, so the words never blink out
        val fit = remember(text) { mutableStateOf<TextUnit?>(TitleFits.get(fitKey)) }
        // a long title tries up to nine sizes, which hitched the swipe that brought a new song in, so it's fitted off the main thread
        LaunchedEffect(fitKey) {
            fit.value = TitleFits.get(fitKey) ?: withContext(Dispatchers.Default) {
                fitTitle(TextMeasurer(fontFamilyResolver, density, layoutDirection, 16), words, scales, text.length, baseSize, maxWidth, maxHeight, density)
            }.also { TitleFits.put(fitKey, it) }
        }
        val fitted = fit.value ?: return@BoxWithConstraints
        // the words rise in once they have a size to rise in at
        LaunchedEffect(text) { enter.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 180f)) }
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(fitted.value.dp * TitleSpacing),
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                words.forEachIndexed { index, word ->
                    Text(
                        text = word,
                        color = color,
                        style = titleWordStyle(fitted, scales[index]),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            val delay = index * 0.09f
                            val p = ((enter.value - delay) / (1f - delay).coerceAtLeast(0.2f)).coerceIn(0f, 1f)
                            translationY = (1f - p) * 30.dp.toPx()
                            alpha = p
                        },
                    )
                }
            }
        }
    }
}

/** the gap between a title's words, as a share of its size */
private const val TitleSpacing = 0.24f

// everything a title's fitted size depends on
private data class TitleFitKey(
    val text: String,
    val baseSize: TextUnit,
    val maxWidth: Int,
    val maxHeight: Int,
    val density: Float,
    val fontScale: Float,
)

// titles already fitted, so a song coming back round (or its next slot in the row) is set at once
private val TitleFits = android.util.LruCache<TitleFitKey, TextUnit>(48)

private fun titleWordStyle(size: TextUnit, scale: Float) =
    posterType(size * scale, if (scale > 1f) FontWeight.Black else FontWeight.Bold)

/**
 * The size a title is set at. Long titles start smaller, the way a poster sets them, then step
 * down until no word runs past [maxWidth] and the rows fit in [maxHeight]. Without this a long
 * title ran off the side or down over the credits.
 */
private fun fitTitle(
    measurer: TextMeasurer,
    words: List<String>,
    scales: FloatArray,
    length: Int,
    baseSize: TextUnit,
    maxWidth: Int,
    maxHeight: Int,
    density: androidx.compose.ui.unit.Density,
): TextUnit {
    var size = baseSize * kotlin.math.sqrt((14f / length.coerceAtLeast(1)).coerceIn(0.52f, 1f))
    repeat(8) {
        if (titleFits(measurer, words, scales, size, maxWidth, maxHeight, density)) return size
        size *= 0.9f
    }
    return size
}

/** whether the title's words, wrapped the way its FlowRow wraps them, fit the box */
private fun titleFits(
    measurer: TextMeasurer,
    words: List<String>,
    scales: FloatArray,
    size: TextUnit,
    maxWidth: Int,
    maxHeight: Int,
    density: androidx.compose.ui.unit.Density,
): Boolean {
    val spacing = with(density) { (size.value * TitleSpacing).dp.toPx() }
    var rowWidth = 0f
    var rowHeight = 0f
    var total = 0f
    words.forEachIndexed { i, word ->
        val layout = measurer.measure(word, titleWordStyle(size, scales[i]), maxLines = 1, softWrap = false, constraints = Constraints())
        val w = layout.size.width.toFloat()
        if (w > maxWidth) return false
        if (rowWidth > 0f && rowWidth + spacing + w > maxWidth) {
            total += rowHeight
            rowWidth = 0f
            rowHeight = 0f
        }
        rowWidth += (if (rowWidth > 0f) spacing else 0f) + w
        rowHeight = max(rowHeight, layout.size.height.toFloat())
    }
    return total + rowHeight <= maxHeight
}

// ---- lyrics ------------------------------------------------------------------------------

/** one word of a line, with when it's sung */
private class TimedWord(val text: String, val startMs: Long, val endMs: Long) {
    // letters' worth of singing: a kanji or kana is a syllable's worth, so one sung at a normal pace isn't taken for a held note
    val sungLength = com.mochyy.mochymusic.ui.player.sungLength(text)
}

/**
 * The words of [entry] with their timings: the line's own word timings when it has them, or the
 * time until [nextStartMs] shared out across the words by length (with a little held back at the
 * end, where a singer breathes).
 */
private fun timedWords(entry: LyricsEntry, nextStartMs: Long?): List<TimedWord> {
    val synced = entry.words?.filter { it.text.isNotBlank() }
    if (!synced.isNullOrEmpty()) {
        return synced.map { TimedWord(it.text.trim(), (it.startTime * 1_000).toLong(), (it.endTime * 1_000).toLong()) }
    }
    val words = entry.text.split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    val lineEnd = (nextStartMs ?: (entry.time + 4_000L)).coerceAtLeast(entry.time + 600L)
    val singing = ((lineEnd - entry.time) * 0.86f).coerceAtMost(words.size * 900f)
    val weights = words.map { it.length + 1.5f }
    val total = weights.sum()
    var cursor = entry.time.toFloat()
    return words.mapIndexed { i, word ->
        val length = singing * weights[i] / total
        TimedWord(word, cursor.toLong(), (cursor + length).toLong()).also { cursor += length }
    }
}

/** a line starts coming in this long before it's sung, so it has landed by its first word */
private const val EnterLeadMs = 420L
/** each word's entrance (fade, un-blur, rise), and the gap between one word's start and the next's */
private const val EnterMs = 560f
private const val EnterStaggerMs = 65f
/** the same for leaving: up and away, blurring out */
private const val ExitMs = 460f
private const val ExitStaggerMs = 40f
/** how much of a letter's rise is its own rather than its word's (the flagship's mix) */
private const val RiseShare = 0.55f
/**
 * A letter starts rising this many letters before the singing reaches it and is fully up this
 * many further on: a long, soft slope, so neighbouring letters rise together as one wave.
 */
private const val RiseLeadChars = 0.8f
private const val RiseSpanChars = 3.2f
/** after its last word a line flies off this soon when a long instrumental gap follows */
private const val GapExitDelayMs = 450L
/** a word keeps its wave and glow this long after its last letter, fading out */
private const val ActiveFadeMs = 450f
/** a lit letter's glow: how much of its colour, and how soft as a share of the word's size; tight, so it reads as light and not a smudge */
private const val GlowAlpha = 0.6f
private const val GlowSoftness = 0.13f
/** the glow follows the voice: brightest on the letters just sung, easing off over this many behind */
private const val GlowTrailChars = 2.5f
/** what's left on the rest of the sung word while it's being sung */
private const val GlowRest = 0.3f
/** the glow is blurred at this share of the word's resolution: it's soft anyway, and a full-size blur every frame was most of the gpu's time */
private const val GlowRes = 0.5f
/** fainter than this a letter doesn't glow at all */
private const val GlowCutoff = 0.04f

/**
 * Which side a line sits on. The lead voice takes the side the song's words start from (the left,
 * or the right for a right-to-left song), the answering voice the other side, both together the
 * middle.
 */
private enum class LineSide { LEFT, CENTER, RIGHT }

private fun sideOf(entry: LyricsEntry, duet: Boolean, rtl: Boolean): LineSide {
    if (duet && entry.agent == "v1000") return LineSide.CENTER
    val answering = duet && entry.agent == "v2"
    return if (answering != rtl) LineSide.RIGHT else LineSide.LEFT
}

/** a song whose lines mostly read right to left: they all sit on the right, so none jump sides */
private fun readsRightToLeft(lines: List<LyricsEntry>): Boolean =
    lines.count { isRtlText(it.text) } * 2 > lines.size

/**
 * One word of a line, measured once at the size it peaks at: an emphasised word is measured big
 * and drawn at [rest] of that until it's sung, every other word sits at its own size throughout.
 */
private class WordCell(
    val word: TimedWord,
    val letters: Array<TextLayoutResult>,
    /** each letter's left, within the word at its peak size */
    val letterX: FloatArray,
    val width: Float,
    val height: Float,
    val baseline: Float,
    /** its resting size as a share of its peak (1 for a word that doesn't grow) */
    val rest: Float,
    val sizePx: Float,
    val row: Int,
    /** its first letter's place among the line's letters */
    val charStart: Int,
    /** room round the word's layer for its rise, zoom, glow and blur */
    val bleed: Float,
    /** read right to left, so it lights from the right. Always one piece (see [letterBounds]) */
    val rtl: Boolean,
) {
    // this frame's rise, zoom and glow for each letter, worked out once for both the glow and the letters
    val lift = FloatArray(letters.size)
    val zoom = FloatArray(letters.size)
    val glow = FloatArray(letters.size)
}

/** where a word is in its singing: before it and after it, it looks the same every frame */
private enum class WordStage { Waiting, Live, Done }

/**
 * Where [word] can be pulled apart into letters that move on their own: between its grapheme
 * clusters (so an accent or an emoji stays whole), or nowhere for a joined or right-to-left
 * script. Drawn one at a time, Arabic letters lost their joins and came out as loose shapes.
 */
private fun letterBounds(word: String, graphemes: java.text.BreakIterator): IntArray {
    if (word.isEmpty() || !com.mochyy.mochymusic.ui.player.splitsIntoLetters(word, 0, word.length)) {
        return intArrayOf(0, word.length)
    }
    graphemes.setText(word)
    val bounds = ArrayList<Int>(word.length + 1)
    var at = graphemes.first()
    while (at != java.text.BreakIterator.DONE) {
        bounds += at
        at = graphemes.next()
    }
    return bounds.toIntArray()
}

/**
 * The order a line's words sit in from left to right, row by row, as indexes into [words]. A
 * right-to-left line puts its first word on the right; a run in the other direction inside it (a
 * Latin name in an Arabic line, say) still reads its own way.
 */
private fun visualOrder(words: List<TimedWord>, rows: IntArray): IntArray {
    val order = IntArray(words.size) { it }
    val joined = words.joinToString(" ") { it.text }
    if (!java.text.Bidi.requiresBidi(joined.toCharArray(), 0, joined.length)) return order
    val bidi = java.text.Bidi(joined, java.text.Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
    val levels = ByteArray(words.size)
    var offset = 0
    words.forEachIndexed { i, w ->
        levels[i] = bidi.getLevelAt(offset).toByte()
        offset += w.text.length + 1
    }
    val boxed = Array<Any>(words.size) { it }
    var start = 0
    while (start < words.size) {
        var end = start
        while (end < words.size && rows[end] == rows[start]) end++
        java.text.Bidi.reorderVisually(levels, start, boxed, start, end - start)
        start = end
    }
    return IntArray(words.size) { boxed[it] as Int }
}

/** a line under the sung one: how it's said (romanised), or what it means (translated) */
private class SubCell(val layout: TextLayoutResult, val alpha: Float, val bleed: Float)

/**
 * A line, measured: its words in rows, set to its [side], and the sub lines under them. Where each
 * word sits moves every frame - an emphasised word growing as it's sung pushes the words after it
 * along (or, on a right-hand line, the words before it back) and the rows and sub lines below it
 * down - and is worked out once per frame from the playback time ([frameAt]), so every piece of
 * the line moves from the same numbers.
 */
private class LineCells(
    val words: List<WordCell>,
    val spacePx: Float,
    val maxWidth: Float,
    val side: LineSide,
    val subs: List<SubCell>,
    val subGapPx: Float,
    /** each row's words left to right, from [visualOrder] */
    val visual: IntArray = IntArray(words.size) { it },
) {
    val chars = words.sumOf { it.letters.size }
    val x = FloatArray(words.size)
    val y = FloatArray(words.size)
    val scale = FloatArray(words.size)
    val subY = FloatArray(subs.size)
    /** the whole line's height this frame, sub lines included: what a line stacked under it sits below */
    var totalHeight = 0f
        private set
    private var frameT = Long.MIN_VALUE

    // when each word starts and stops changing how it's drawn
    private val liveFrom = LongArray(words.size)
    private val liveUntil = LongArray(words.size)

    init {
        // the singing only gets past a word once every word before it is done too
        var settled = Long.MIN_VALUE
        words.forEachIndexed { i, cell ->
            val w = cell.word
            settled = max(settled, max(w.startMs, w.endMs))
            liveFrom[i] = w.startMs
            liveUntil[i] = maxOf(settled, w.endMs + ActiveFadeMs.toLong(), w.startMs + growSpanOf(w).toLong()) + 1L
        }
    }

    fun stageAt(index: Int, t: Long): WordStage = when {
        t < liveFrom[index] -> WordStage.Waiting
        t >= liveUntil[index] -> WordStage.Done
        else -> WordStage.Live
    }

    /** the time a word is drawn at: one fixed moment while it waits or once it's done, so its picture doesn't change */
    fun drawTimeOf(index: Int, stage: WordStage, t: Long): Long = when (stage) {
        WordStage.Waiting -> liveFrom[index] - 1L
        WordStage.Done -> liveUntil[index]
        WordStage.Live -> t
    }

    fun frameAt(t: Long) {
        if (t == frameT) return
        frameT = t
        var i = 0
        var rowTop = 0f
        var bottom = 0f
        while (i < words.size) {
            val row = words[i].row
            var j = i
            var baseline = 0f
            var height = 0f
            var rowWidth = 0f
            while (j < words.size && words[j].row == row) {
                val w = words[j]
                val s = if (w.rest >= 1f) 1f else w.rest + (1f - w.rest) * grow(w.word, t)
                scale[j] = s
                baseline = max(baseline, w.baseline * s)
                height = max(height, w.height * s)
                rowWidth += w.width * s + (if (j > i) spacePx else 0f)
                j++
            }
            var cx = when (side) {
                LineSide.LEFT -> 0f
                LineSide.CENTER -> (maxWidth - rowWidth) / 2f
                LineSide.RIGHT -> maxWidth - rowWidth
            }
            // mixed sizes share a baseline
            for (p in i until j) {
                val k = visual[p]
                val w = words[k]
                x[k] = cx
                y[k] = rowTop + baseline - w.baseline * scale[k]
                cx += w.width * scale[k] + spacePx
            }
            bottom = rowTop + height
            rowTop += height * 0.98f
            i = j
        }
        var subTop = bottom
        subs.forEachIndexed { k, sub ->
            subTop += subGapPx
            subY[k] = subTop
            subTop += sub.layout.size.height
        }
        totalHeight = subTop
    }

    /**
     * Where the singing is, in letters along the line: it runs through each word over the word's
     * time and waits at the end of a word until the next one starts, so it never jumps.
     */
    fun headAt(t: Long): Float {
        for (w in words) {
            val n = w.letters.size
            if (t < w.word.startMs) return w.charStart.toFloat()
            if (t < w.word.endMs) {
                val span = (w.word.endMs - w.word.startMs).coerceAtLeast(1L)
                return w.charStart + n * ((t - w.word.startMs).toFloat() / span)
            }
        }
        return chars.toFloat()
    }
}

/** an emphasised word grows from its resting size to its peak as it's sung, slowly and evenly */
private fun grow(word: TimedWord, t: Long): Float = smootherstep((t - word.startMs) / growSpanOf(word))

/** how long an emphasised word takes to grow to its peak */
private fun growSpanOf(word: TimedWord): Float = max(520f, (word.endMs - word.startMs) * 0.75f)

private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** smoothstep with flat ends as well as a flat start: no kink where a motion begins or lands */
private fun smootherstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * t * (t * (t * 6f - 15f) + 10f)
}

private fun easeOutCubic(x: Float): Float {
    val u = 1f - x.coerceIn(0f, 1f)
    return 1f - u * u * u
}

/**
 * Measures [entry] to fit [maxWidth] x [maxHeight] with every word at its peak at once and its
 * [reading] and [translation] under it, so the line can never grow past its block - no word is
 * wider than the block and no row runs off it.
 */
private fun measureLine(
    measurer: TextMeasurer,
    entry: LyricsEntry,
    nextStartMs: Long?,
    maxWidth: Float,
    maxHeight: Float,
    basePx: Float,
    density: androidx.compose.ui.unit.Density,
    color: Color,
    side: LineSide,
    reading: String?,
    translation: String?,
): LineCells {
    val words = timedWords(entry, nextStartMs)
    val subGap = with(density) { 6.dp.toPx() }
    if (words.isEmpty()) return LineCells(emptyList(), 0f, maxWidth, side, emptyList(), subGap)

    // the sub lines first: whatever room they take, the sung line has the rest
    val subPx = (basePx * 0.3f).coerceIn(with(density) { 13.sp.toPx() }, with(density) { 19.sp.toPx() })
    // by the side the line is on, not by the sub line's own direction: an English translation
    // under an Arabic line sits on the right with it
    val align = when (side) {
        LineSide.LEFT -> TextAlign.Left
        LineSide.CENTER -> TextAlign.Center
        LineSide.RIGHT -> TextAlign.Right
    }
    val subWidth = maxWidth.roundToInt().coerceAtLeast(1)
    val subs = listOfNotNull(reading?.let { it to 0.74f }, translation?.let { it to 0.56f }).map { (text, alpha) ->
        val style = TextStyle(
            fontFamily = com.mochyy.mochymusic.ui.theme.InterFontFamily,
            fontSize = with(density) { subPx.toSp() },
            lineHeight = with(density) { (subPx * 1.22f).toSp() },
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.02).em,
            color = color,
            textAlign = align,
            textMotion = TextMotion.Animated,
        )
        val layout = measurer.measure(
            text = text,
            style = style,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = subWidth),
        )
        SubCell(layout, alpha, with(density) { 14.dp.toPx() })
    }
    val subsHeight = subs.sumOf { it.layout.size.height } + subs.size * subGap
    val lineHeightBudget = (maxHeight - subsHeight).coerceAtLeast(maxHeight * 0.45f)

    val scales = wordScales(words.map { it.text })
    fun style(i: Int, base: Float) = posterType(with(density) { (base * scales[i]).toSp() }, FontWeight.Bold).copy(color = color)
    var base = basePx
    var attempt = 0
    while (true) {
        val wholes = words.mapIndexed { i, w ->
            measurer.measure(w.text, style(i, base), maxLines = 1, softWrap = false, constraints = Constraints())
        }
        val space = base * 0.26f
        val rows = IntArray(words.size)
        var x = 0f
        var row = 0
        var rowHeight = 0f
        var total = 0f
        var widest = 0f
        wholes.forEachIndexed { i, layout ->
            val ww = layout.size.width.toFloat()
            if (x > 0f && x + ww > maxWidth) {
                total += rowHeight * 0.98f
                rowHeight = 0f
                x = 0f
                row++
            }
            rows[i] = row
            x += ww + space
            rowHeight = max(rowHeight, layout.size.height.toFloat())
            widest = max(widest, ww)
        }
        total += rowHeight
        if ((widest <= maxWidth && total <= lineHeightBudget) || attempt >= 10) {
            var charStart = 0
            val graphemes = java.text.BreakIterator.getCharacterInstance()
            val cells = words.mapIndexed { i, w ->
                val whole = wholes[i]
                val st = style(i, base)
                val bounds = letterBounds(w.text, graphemes)
                val pieces = bounds.size - 1
                val letters = if (pieces == 1) arrayOf(whole) else Array(pieces) { k ->
                    measurer.measure(w.text.substring(bounds[k], bounds[k + 1]), st, maxLines = 1, softWrap = false, constraints = Constraints())
                }
                // a whole word starts at its own left edge, whichever way it reads
                val letterX = if (pieces == 1) FloatArray(1) else FloatArray(pieces) { k ->
                    whole.getHorizontalPosition(bounds[k], usePrimaryDirection = true)
                }
                val sizePx = base * scales[i]
                WordCell(
                    word = w,
                    letters = letters,
                    letterX = letterX,
                    width = whole.size.width.toFloat(),
                    height = whole.size.height.toFloat(),
                    baseline = whole.firstBaseline,
                    rest = if (scales[i] > 1f) 1f / scales[i] else 1f,
                    sizePx = sizePx,
                    row = rows[i],
                    charStart = charStart,
                    bleed = sizePx * 0.45f + with(density) { 14.dp.toPx() },
                    rtl = pieces == 1 && isRtlText(w.text),
                ).also { charStart += pieces }
            }
            return LineCells(cells, space, maxWidth, side, subs, subGap, visualOrder(words, rows))
        }
        base *= 0.88f
        attempt++
    }
}

/**
 * When each line starts coming in: [EnterLeadMs] before it's sung. The first line comes in at the
 * start when it's sung soon after; a longer intro is counted down by the dots first.
 */
private fun enterTimes(lines: List<LyricsEntry>): LongArray = LongArray(lines.size) { i ->
    val start = lines[i].time
    if (i == 0) (if (start < MinInterludeGapMs) 0L else start - EnterLeadMs)
    else (start - EnterLeadMs).coerceAtLeast(lines[i - 1].time)
}

/** an instrumental gap: the dots count down from [startMs] to [endMs], on the side of the voice to come */
private class Interlude(val startMs: Long, val endMs: Long, val afterIndex: Int, val side: LineSide)

private fun interludesOf(lines: List<LyricsEntry>, ends: LongArray, enterAts: LongArray, duet: Boolean, rtl: Boolean): List<Interlude> {
    if (lines.isEmpty()) return emptyList()
    val gaps = ArrayList<Interlude>()
    if (lines[0].time >= MinInterludeGapMs) {
        gaps += Interlude(300L, enterAts[0], -1, sideOf(lines[0], duet, rtl))
    }
    for (i in 0 until lines.lastIndex) {
        val next = i + 1
        if (lines[next].time - ends[i] >= MinInterludeGapMs) {
            val start = ends[i] + GapExitDelayMs + 250L
            if (enterAts[next] - start > 1_500L) gaps += Interlude(start, enterAts[next], i, sideOf(lines[next], duet, rtl))
        }
    }
    return gaps
}

private fun lastAtOrBefore(times: LongArray, t: Long): Int {
    var lo = 0
    var hi = times.lastIndex
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (times[mid] <= t) {
            found = mid
            lo = mid + 1
        } else hi = mid - 1
    }
    return found
}

/** what a measured line was measured with: its readings arrive later than the line itself */
private data class LineKey(val index: Int, val reading: String?, val translation: String?)

/** one block's measured lines; only touched on the main thread, the measuring itself happens elsewhere */
private class LineCache {
    private val measured = LinkedHashMap<LineKey, LineCells>()
    // read in composition, so a line that lands shows on the next frame
    private val landed = androidx.compose.runtime.mutableIntStateOf(0)

    fun has(key: LineKey): Boolean = key in measured

    /** the line as asked for, or while that's still being measured, the latest look it had (say, before its translation came) */
    fun cellsFor(key: LineKey): LineCells? {
        landed.intValue
        return measured[key] ?: measured.entries.lastOrNull { it.key.index == key.index }?.value
    }

    fun put(key: LineKey, cells: LineCells, shows: Boolean) {
        measured[key] = cells
        if (shows) landed.intValue++
    }

    fun keepOnly(indices: IntRange) {
        measured.keys.retainAll { it.index in indices }
    }
}

/** the pronunciation of [entry]: its romanisation, or the readings of its words joined up */
private fun readingOf(roman: String?, ruby: List<com.mochyy.mochymusic.lyrics.RubyToken>?, text: String): String? =
    roman?.takeIf { it.isNotBlank() && it != text }
        ?: ruby?.joinToString(" ") { it.text.trim() }?.takeIf { it.isNotBlank() && it != text }

/**
 * The current lyric line, set big and performed the way the flagship's lyrics move: the singing
 * runs through each word letter by letter, the letters rising in one soft wave as it reaches them
 * and lighting with a gradient that flows across the letters rather than switching them on one by
 * one. Words still to come wait greyed out. One word of the line is the shout: it sits at the size
 * of the rest until it's sung, then grows, nudging the words around it along. A held note swells
 * and glows.
 *
 * Like the lyrics page: in a duet the lead voice's lines sit on the left and the answering voice's
 * on the right; the pronunciation and the translation sit under the line when there are any; and a
 * long instrumental gap (or intro) is counted down by the three dots. A right-to-left song (Arabic,
 * Hebrew) is mirrored: its lines sit on the right, words run from the right, and joined words are
 * kept whole so their letters stay connected.
 *
 * A new line comes in word after word - fading in, sharpening out of a blur, rising into place -
 * while the line before it flies up and blurs away. All of it is worked out from the playback time
 * once per frame, so seeking, pausing and skipping lines can never put it out of step, and nothing
 * moves by steps.
 */
@Composable
fun ExpressiveLyrics(
    lines: List<LyricsEntry>,
    position: LivePosition,
    color: Color,
    baseSize: TextUnit,
    modifier: Modifier = Modifier,
    dimAlpha: Float = 0.32f,
) {
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current
    // only a song with more than one voice puts lines on both sides
    val duet = remember(lines) { lines.map { it.agent ?: "v1" }.distinct().size > 1 }
    val rtl = remember(lines) { readsRightToLeft(lines) }
    val enterAts = remember(lines) { enterTimes(lines) }
    val ends = remember(lines) {
        LongArray(lines.size) { i ->
            timedWords(lines[i], lines.getOrNull(i + 1)?.time).lastOrNull()?.endMs ?: lines[i].time
        }
    }
    val interludes = remember(lines, ends, enterAts, duet, rtl) { interludesOf(lines, ends, enterAts, duet, rtl) }
    // a line still being sung when the next one comes in: the two show together, stacked
    val overlaps = remember(lines, ends, enterAts) {
        BooleanArray(lines.size) { i -> i < lines.lastIndex && ends[i] - 100L > enterAts[i + 1] }
    }
    // when each line leaves (null: the last line, which stays)
    val exitAts = remember(lines, ends, enterAts, interludes, overlaps) {
        Array(lines.size) { index ->
            val next = index + 1
            when {
                next !in lines.indices -> null
                // a gap follows: it flies off soon after its last word
                interludes.any { it.afterIndex == index } -> (ends[index] + GapExitDelayMs).coerceAtMost(enterAts[next])
                // it overlaps the next: it stays on top, singing, until it's done
                overlaps[index] -> ends[index] + 150L
                // it leaves as the next line comes in, but not before its last word is nearly done
                else -> (ends[index] - 100L).coerceIn(enterAts[next], max(enterAts[next], lines[next].time))
            }
        }
    }
    val shown by remember(lines, position) {
        derivedStateOf { lastAtOrBefore(enterAts, position.positionMs()) }
    }
    // the first line still on screen: the one before the newest while it flies off, or further back
    // while overlapping lines are still being sung above it
    val first by remember(lines, position, exitAts) {
        derivedStateOf {
            val t = position.positionMs()
            val top = lastAtOrBefore(enterAts, t)
            var firstOn = top
            var j = top - 1
            while (j >= 0 && top - j <= 3) {
                val exit = exitAts[j] ?: break
                val gone = t >= exit + ExitMs + 12 * ExitStaggerMs + 120L
                if (!gone) firstOn = j else if (!overlaps[j]) break
                j--
            }
            firstOn
        }
    }
    val gap by remember(interludes, position) {
        derivedStateOf {
            val t = position.positionMs()
            interludes.firstOrNull { t >= it.startMs - 150L && t < it.endMs + 700L }
        }
    }
    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else widthPx
        val basePx = with(density) { baseSize.toPx() }
        val stackGapPx = basePx * 0.28f
        val cache = remember(lines, widthPx, heightPx, basePx, color) { LineCache() }
        // a line is dozens of text layouts, so it's always measured off the main thread, each time with a measurer of its own
        suspend fun measure(key: LineKey): LineCells = withContext(Dispatchers.Default) {
            val index = key.index
            // a line that shares the block with another gets half of it
            val shared = overlaps[index] || (index > 0 && overlaps[index - 1])
            val budget = if (shared) (heightPx * 0.94f - stackGapPx) / 2f else heightPx * 0.94f
            measureLine(
                TextMeasurer(fontFamilyResolver, density, layoutDirection, 128), lines[index], lines.getOrNull(index + 1)?.time,
                widthPx, budget, basePx, density, color, sideOf(lines[index], duet, rtl), key.reading, key.translation,
            )
        }
        // the next line is measured a frame after this one lands, long before it's needed
        androidx.compose.runtime.LaunchedEffect(shown, cache) {
            androidx.compose.runtime.withFrameMillis { }
            cache.keepOnly((first - 1)..(shown + 1))
            val next = shown + 1
            if (next in lines.indices) {
                val e = lines[next]
                val reading = readingOf(e.romanizedTextFlow.value, e.rubyFlow.value, e.text)
                val translation = e.translatedTextFlow.value?.takeIf { it.isNotBlank() && it != e.text }
                val key = LineKey(next, reading, translation)
                // quietly: the line isn't on screen yet, so nothing needs composing again for it
                if (!cache.has(key)) cache.put(key, measure(key), shows = false)
            }
        }

        gap?.let { interlude ->
            androidx.compose.runtime.key(interlude.startMs) {
                InterludeRow(interlude, position, color, baseSize)
            }
        }

        if (shown in lines.indices) {
            // each line on screen, top first, and how far down the lines still above it push it:
            // an overlapping line above holds its place while it's sung, then gives it back as it
            // flies off, so the line under it glides up into the top spot
            val above = ArrayList<LineCells>()
            val aboveExits = ArrayList<Long>()
            for (index in first.coerceAtLeast(0)..shown) {
                androidx.compose.runtime.key(index) {
                    val entry = lines[index]
                    val roman by entry.romanizedTextFlow.collectAsState()
                    val ruby by entry.rubyFlow.collectAsState()
                    val translated by entry.translatedTextFlow.collectAsState()
                    val key = LineKey(
                        index,
                        readingOf(roman, ruby, entry.text),
                        translated?.takeIf { it.isNotBlank() && it != entry.text },
                    )
                    // not measured yet (a seek, a turn, a translation just in): it lands a frame or two later instead of hitching this one
                    if (!cache.has(key)) {
                        androidx.compose.runtime.LaunchedEffect(key, cache) { cache.put(key, measure(key), shows = true) }
                    }
                    val cells = cache.cellsFor(key)
                    if (cells != null) {
                        val pushers = above.toList()
                        val pusherExits = aboveExits.toList()
                        LyricLine(
                            cells = cells,
                            enterAt = enterAts[index],
                            exitAt = exitAts[index],
                            position = position,
                            color = color,
                            dimAlpha = dimAlpha,
                            offsetY = { t ->
                                var y = 0f
                                for (k in pushers.indices) {
                                    val holder = pushers[k]
                                    holder.frameAt(t)
                                    val hold = 1f - smootherstep((t - pusherExits[k]) / ExitMs)
                                    y += (holder.totalHeight + stackGapPx) * hold
                                }
                                y
                            },
                        )
                        val exit = exitAts[index]
                        if (overlaps[index] && exit != null) {
                            above += cells
                            aboveExits += exit
                        }
                    }
                }
            }
        }
    }
}

/**
 * The lyrics page's three dots, counting an instrumental gap down at the top of the block, on the
 * side of the voice that sings next and sized up to sit with the big type.
 */
@Composable
private fun InterludeRow(gap: Interlude, position: LivePosition, color: Color, baseSize: TextUnit) {
    val visible by remember(gap, position) {
        derivedStateOf {
            val t = position.positionMs()
            t >= gap.startMs && t < gap.endMs
        }
    }
    val provider = remember(position) { { position.positionMs() } }
    val scale = (baseSize.value / 30f).coerceIn(1f, 1.8f)
    val origin = when (gap.side) {
        LineSide.LEFT -> 0f
        LineSide.CENTER -> 0.5f
        LineSide.RIGHT -> 1f
    }
    com.mochyy.mochymusic.ui.player.LyricsInterludeDots(
        startMs = gap.startMs,
        endMs = gap.endMs,
        positionProvider = provider,
        accentColor = color,
        visible = visible,
        alignment = when (gap.side) {
            LineSide.LEFT -> com.mochyy.mochymusic.ui.player.LyricsAlignment.START
            LineSide.CENTER -> com.mochyy.mochymusic.ui.player.LyricsAlignment.CENTER
            LineSide.RIGHT -> com.mochyy.mochymusic.ui.player.LyricsAlignment.END
        },
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(origin, 0f)
            }
            // the row pads itself for the lyrics page; here the dots line up with the words' edge
            .layout { measurable, constraints ->
                val pad = com.mochyy.mochymusic.ui.player.LyricsHeaderArtX.roundToPx()
                val wide = constraints.maxWidth + pad * 2
                val p = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
                layout(constraints.maxWidth, p.height) { p.place(-pad, 0) }
            },
    )
}

/** one line's words and sub lines, each on a layer of its own so it can blur, fade and fly by itself */
@Composable
private fun LyricLine(
    cells: LineCells,
    enterAt: Long,
    exitAt: Long?,
    position: LivePosition,
    color: Color,
    dimAlpha: Float,
    /** how far down the overlapping lines above push this one, this frame */
    offsetY: (Long) -> Float = { 0f },
) {
    val density = LocalDensity.current
    val blurIn = with(density) { 10.dp.toPx() }
    val blurOut = with(density) { 12.dp.toPx() }
    val dim = color.copy(alpha = color.alpha * dimAlpha)
    // render effects arrived in android 12; before that the glow stays a text shadow
    val gpuGlow = android.os.Build.VERSION.SDK_INT >= 31

    fun enterOf(order: Int, t: Long) = easeOutCubic((t - enterAt - order * EnterStaggerMs) / EnterMs)
    fun exitOf(order: Int, t: Long) = if (exitAt == null) 0f else smoothstep((t - exitAt - order * ExitStaggerMs) / ExitMs)

    /** how blurred the [order]th piece is as it comes in or goes, in px; drawn into a smaller layer of its own (see [drawBlurred]) */
    fun blurOf(order: Int, t: Long): Float =
        if (!gpuGlow || !com.mochyy.mochymusic.ui.utils.AppMotion.elementBlur) 0f
        else (1f - enterOf(order, t)) * blurIn + exitOf(order, t) * blurOut

    /** the entrance and exit, for the [order]th piece of the line */
    fun GraphicsLayerScope.motion(order: Int, t: Long, liftPx: Float, x: Float, y: Float, bleed: Float, mayGlow: Boolean) {
        val e = enterOf(order, t)
        val out = exitOf(order, t)
        translationX = x - bleed
        translationY = y + offsetY(t) - bleed + (1f - e) * liftPx * 0.55f - out * out * liftPx * 0.9f
        alpha = (e * (1f - out)).coerceIn(0f, 1f)
        val zoom = (0.94f + 0.06f * e) * (1f + 0.08f * out)
        scaleX = zoom
        scaleY = zoom
        // the whole line zooms about one point, so its words keep their places to each other instead of each swelling about its own middle
        val pivotX = when (cells.side) {
            LineSide.LEFT -> 0f
            LineSide.CENTER -> cells.maxWidth / 2f
            LineSide.RIGHT -> cells.maxWidth
        }
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin((pivotX - (x - bleed)) / size.width, (cells.totalHeight / 2f - (y - bleed)) / size.height)
        // blurred, a piece is one draw of its blurred layer, so its fade goes straight onto that; nearly in or out with no glow it goes onto each letter (lower, letters that touch would show their seams)
        val blurred = blurOf(order, t) > BlurShownPx
        compositingStrategy = if (blurred || (!mayGlow && alpha > 0.95f)) CompositingStrategy.ModulateAlpha else CompositingStrategy.Auto
    }

    cells.words.forEachIndexed { w, cell ->
        androidx.compose.runtime.key(w) {
            // only while it's being sung does a word's picture change, so only then is it drawn again every frame
            val stage = remember(cells, w, position) { derivedStateOf { cells.stageAt(w, position.positionMs()) } }
            // a word coming in or going is drawn again each frame of its blur, and stays a still picture otherwise
            val blurring = remember(cells, w, position) { derivedStateOf { blurOf(w, position.positionMs()) > BlurShownPx } }
            val glowLayer = if (gpuGlow) rememberGraphicsLayer() else null
            val blurLayer = if (gpuGlow) rememberGraphicsLayer() else null
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val p = measurable.measure(
                            Constraints.fixed(
                                (cell.width + cell.bleed * 2f).roundToInt().coerceAtLeast(1),
                                (cell.height + cell.bleed * 2f).roundToInt().coerceAtLeast(1),
                            )
                        )
                        layout(0, 0) {
                            p.placeWithLayer(0, 0) {
                                val t = position.positionMs()
                                cells.frameAt(t)
                                motion(w, t, cell.sizePx, cells.x[w], cells.y[w], cell.bleed, mayGlow = stage.value == WordStage.Live)
                            }
                        }
                    }
                    .drawBehind {
                        val now = stage.value
                        val moving = blurring.value
                        val clock = if (now == WordStage.Live || moving) position.positionMs() else 0L
                        val t = cells.drawTimeOf(w, now, clock)
                        cells.frameAt(t)
                        if (moving && blurLayer != null) {
                            drawBlurred(blurLayer, blurOf(w, clock)) { drawWord(cell, cells.scale[w], cells.headAt(t), t, color, dim, glowLayer) }
                        } else {
                            drawWord(cell, cells.scale[w], cells.headAt(t), t, color, dim, glowLayer)
                        }
                    },
            )
        }
    }
    cells.subs.forEachIndexed { k, sub ->
        val order = cells.words.size + k
        val blurring = remember(cells, k, position) { derivedStateOf { blurOf(order, position.positionMs()) > BlurShownPx } }
        val blurLayer = if (gpuGlow) rememberGraphicsLayer() else null
        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier
                .layout { measurable, _ ->
                    val p = measurable.measure(
                        Constraints.fixed(
                            (sub.layout.size.width + sub.bleed * 2f).roundToInt().coerceAtLeast(1),
                            (sub.layout.size.height + sub.bleed * 2f).roundToInt().coerceAtLeast(1),
                        )
                    )
                    layout(0, 0) {
                        p.placeWithLayer(0, 0) {
                            val t = position.positionMs()
                            cells.frameAt(t)
                            // they come in just after the words, and go with them
                            motion(order, t, sub.layout.size.height * 1.6f, 0f, cells.subY[k], sub.bleed, mayGlow = false)
                        }
                    }
                }
                .drawBehind {
                    val text: DrawScope.() -> Unit = {
                        drawText(sub.layout, color = color.copy(alpha = color.alpha * sub.alpha), topLeft = Offset(sub.bleed, sub.bleed), shadow = Shadow.None)
                    }
                    if (blurring.value && blurLayer != null) drawBlurred(blurLayer, blurOf(order, position.positionMs()), text) else text()
                },
        )
    }
}

/** under this a piece isn't blurred at all */
private const val BlurShownPx = 0.5f

/**
 * [content] blurred by [radius] in a layer of its own, drawn at half size once the blur is wide
 * enough to hide it: every word of a line blurred at full size, every frame of a line change, was
 * most of the gpu's frame on the poster.
 */
private fun DrawScope.drawBlurred(layer: GraphicsLayer, radius: Float, content: DrawScope.() -> Unit) {
    val res = if (radius >= 12f) 0.5f else 1f
    layer.renderEffect = BlurEffectCache.get(radius * res, Shader.TileMode.DECAL)
    val box = androidx.compose.ui.unit.IntSize((size.width * res).roundToInt().coerceAtLeast(1), (size.height * res).roundToInt().coerceAtLeast(1))
    layer.record(box) { scale(res, res, pivot = Offset.Zero) { content() } }
    scale(1f / res, 1f / res, pivot = Offset.Zero) { drawLayer(layer) }
}

/** one word this frame: its glow in a blurred layer under it ([glowLayer], android 12+), then its letters */
private fun DrawScope.drawWord(cell: WordCell, scale: Float, head: Float, t: Long, color: Color, dim: Color, glowLayer: GraphicsLayer?) {
    val word = cell.word
    val n = cell.letters.size
    if (n == 0) return
    val k = (head - cell.charStart).coerceIn(0f, n.toFloat())
    val through = k / n
    val wordRise = smootherstep(through / 0.85f)
    // the lit edge: a soft gradient a letter and a bit wide, running just behind the singing and
    // carried past the last letter as the word finishes, so the whole word ends lit
    val soft = cell.width / n * 1.15f
    // at an even pace across the word: letter by letter, a narrow i and a wide m took the same time and the edge sped up and slowed down
    val frontAtHead = cell.width * through
    val front = frontAtHead + soft * through
    val litFrom = front - soft
    val span = (word.endMs - word.startMs).coerceAtLeast(1L).toFloat()
    // long notes swell and glow; quick ones don't
    val hold = smoothstep((span / word.sungLength - 140f) / 200f)
    val within = ((t - word.startMs) / span).coerceIn(0f, 1f)
    val swellEnvelope = hold * smootherstep(within / 0.2f) * smootherstep((1f - within) / 0.12f)
    // the word being sung, easing in as the singing enters it and out after it's over
    val active = smootherstep(through / 0.15f) * (1f - smootherstep((t - word.endMs) / ActiveFadeMs))

    var glowing = false
    for (i in 0 until n) {
        val lx = cell.letterX[i]
        val lw = cell.letters[i].size.width.toFloat()
        val c = i.toFloat()
        val letterRise = if (k >= n) 1f else smootherstep((k - c + RiseLeadChars) / RiseSpanChars)
        val rise = wordRise + (letterRise - wordRise) * RiseShare
        // a soft swell a few letters wide rides the singing so no letter hops on its own: it builds over the letter and a half before and dies away behind
        val d = k - (c + 0.5f)
        val wave = smootherstep((d + 1.6f) / 1.6f) * (1f - smootherstep(d / 3.2f)) * active
        val swell = swellEnvelope * smootherstep((k - c + 0.35f) / 1.4f)
        cell.lift[i] = (rise * 0.07f + wave * 0.025f + swell * 0.02f) * cell.sizePx
        cell.zoom[i] = 1f + wave * 0.035f + swell * 0.07f
        val lit = ((front - (lx + lw / 2f)) / soft).coerceIn(0f, 1f)
        val since = k - (c + 0.5f)
        val trail = if (since <= 0f) 1f else 1f - smootherstep(since / GlowTrailChars)
        // a letter still to be sung never glows; a held note lights the whole word
        val glow = if (lx >= front) 0f else (active * lit * (GlowRest + (1f - GlowRest) * trail) + swell * 0.6f).coerceIn(0f, 1f)
        cell.glow[i] = if (glow > GlowCutoff) glow else 0f
        if (glow > GlowCutoff) glowing = true
    }
    // the glowing letters once more in their glow colour, blurred on the gpu; as text shadows they were blurred again on the cpu every frame they moved
    if (glowing && glowLayer != null) {
        // one radius for the word, so the glow doesn't step through blur sizes as an emphasised word grows
        glowLayer.renderEffect = BlurEffectCache.get(cell.sizePx * GlowSoftness * GlowRes, Shader.TileMode.DECAL)
        val half = androidx.compose.ui.unit.IntSize((size.width * GlowRes).roundToInt().coerceAtLeast(1), (size.height * GlowRes).roundToInt().coerceAtLeast(1))
        glowLayer.record(half) {
            scale(GlowRes, GlowRes, pivot = Offset.Zero) { drawLetters(cell, scale, litFrom, front, color, dim, LetterPass.Glow) }
        }
        scale(1f / GlowRes, 1f / GlowRes, pivot = Offset.Zero) { drawLayer(glowLayer) }
    }
    drawLetters(cell, scale, litFrom, front, color, dim, if (glowLayer == null) LetterPass.Shadowed else LetterPass.Plain)
}

/** which copy of a word's letters is drawn */
private enum class LetterPass {
    // the letters as they are
    Plain,
    // the letters with their glow as text shadows, on phones older than android 12
    Shadowed,
    // only the glowing letters, in their glow colour, for the blurred layer under the word
    Glow,
}

/** [cell]'s letters where [drawWord] put them this frame, lit up to [litFrom] and fading to dim by [front] */
private fun DrawScope.drawLetters(cell: WordCell, scale: Float, litFrom: Float, front: Float, color: Color, dim: Color, pass: LetterPass) {
    withTransform({
        translate(cell.bleed, cell.bleed)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        for (i in cell.letters.indices) {
            val glow = cell.glow[i]
            if (pass == LetterPass.Glow && glow == 0f) continue
            val layout = cell.letters[i]
            val lx = cell.letterX[i]
            val lw = layout.size.width.toFloat()
            val lh = layout.size.height.toFloat()
            val zoom = cell.zoom[i]
            // always passed, never null: a letter's layout keeps the last shadow it was drawn with
            val shadow = if (pass == LetterPass.Shadowed && glow > 0f) {
                Shadow(color.copy(alpha = GlowAlpha * glow), Offset.Zero, cell.sizePx * GlowSoftness)
            } else Shadow.None
            withTransform({
                translate(lx, -cell.lift[i])
                scale(zoom, zoom, pivot = Offset(lw / 2f, lh * 0.85f))
            }) {
                when {
                    lx + lw <= litFrom -> drawText(
                        layout,
                        color = if (pass == LetterPass.Glow) color.copy(alpha = GlowAlpha * glow) else color,
                        shadow = shadow,
                    )
                    lx >= front -> drawText(layout, color = dim, shadow = Shadow.None)
                    else -> {
                        // the lit edge is measured from where the word starts reading, so a right-to-left word (always one piece) lights from its right edge
                        val litX = if (cell.rtl) cell.width - litFrom - lx else litFrom - lx
                        val frontX = if (cell.rtl) cell.width - front - lx else front - lx
                        // a shadow takes the gradient too, faded to its glow, so the glow copy does the same
                        drawText(
                            layout,
                            brush = Brush.horizontalGradient(0f to color, 1f to dim, startX = litX, endX = frontX),
                            alpha = if (pass == LetterPass.Glow) GlowAlpha * glow else Float.NaN,
                            shadow = shadow,
                        )
                    }
                }
            }
        }
    }
}

/** sp to the matching pixel size of a Dp, for the few sizes that are laid out by hand */
internal fun Dp.asSp(density: androidx.compose.ui.unit.Density): TextUnit = with(density) { this@asSp.toSp() }

internal val LyricsBase = 46.sp
