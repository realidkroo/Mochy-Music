// a module player's flowing lyrics: lines rolling up as the song goes, with optional line blur, edge fade, karaoke sweep, translation and tap-to-seek (docs/wiki, blocks)

package com.mochyy.mochymusic.ui.player.styles

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.DisableBlurKey
import com.mochyy.mochymusic.constants.ElementBlurKey
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.modules.skin.SkinAlign
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import com.mochyy.mochymusic.utils.rememberPreference
import kotlin.math.floor
import kotlin.math.min

@Composable
internal fun SkinFlowLyrics(lines: List<LyricsEntry>, block: SkinBlock.Lyrics, context: SkinContext, modifier: Modifier) {
    val connection = LocalPlayerConnection.current
    val align = block.align ?: context.layout.align
    FlowLyricsView(
        lines = lines,
        position = context.lyricsPosition,
        clock = context.lyricClock,
        height = block.height,
        fontSize = block.size,
        color = context.color(block.color),
        activeColor = block.active?.let(context::color) ?: context.color(block.color),
        prefix = block.prefix,
        align = align,
        blur = block.blur,
        pastBlur = block.pastBlur,
        nextBlur = block.nextBlur,
        edgeFade = block.edgeFade,
        gap = block.gap,
        weight = block.weight,
        activeScale = block.activeScale,
        pastOpacity = block.pastOpacity,
        nextOpacity = block.nextOpacity,
        linesAbove = block.linesAbove,
        sweep = block.sweep,
        translation = block.translation,
        preview = context.preview,
        onSeek = if (block.seek && !context.preview && connection != null) { ms -> connection.seekTo(ms) } else null,
        modifier = modifier,
    )
}

/** reusable flowing lyrics list for both player blocks and custom pages */
@Composable
internal fun FlowLyricsView(
    lines: List<LyricsEntry>,
    position: LivePosition,
    clock: LyricClock,
    height: Dp,
    fontSize: TextUnit,
    color: Color,
    activeColor: Color,
    prefix: String = "",
    align: SkinAlign = SkinAlign.Start,
    blur: Float = 0f,
    pastBlur: Float? = null,
    nextBlur: Float? = null,
    edgeFade: Boolean = true,
    gap: Dp = 6.dp,
    weight: FontWeight? = null,
    activeScale: Float = 1.06f,
    pastOpacity: Float = 0.35f,
    nextOpacity: Float = 0.6f,
    linesAbove: Int = 1,
    sweep: Boolean = false,
    translation: Boolean = false,
    preview: Boolean = false,
    onSeek: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // the position moves every frame, but the list only hears about it when the sung line changes
    val current by remember(lines, position) { derivedStateOf { LyricsUtils.findCurrentLineIndex(lines, position.positionMs()) } }
    val state = rememberLazyListState()
    LaunchedEffect(current, lines, linesAbove) {
        val target = (current - linesAbove).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        if (state.firstVisibleItemIndex == target && state.firstVisibleItemScrollOffset == 0) return@LaunchedEffect
        if (preview) state.scrollToItem(target) else state.animateScrollToItem(target)
    }
    val disableBlur by rememberPreference(DisableBlurKey, defaultValue = false)
    val elementBlur by rememberPreference(ElementBlurKey, defaultValue = true)
    val allowBlur = !disableBlur && elementBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blank = remember(prefix) { " ".repeat(prefix.length) }
    val origin = when (align) {
        SkinAlign.Start -> TransformOrigin(0f, 0.5f)
        SkinAlign.Center -> TransformOrigin(0.5f, 0.5f)
        SkinAlign.End -> TransformOrigin(1f, 0.5f)
    }
    val fadeModifier = if (!edgeFade) Modifier else Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.16f to Color.Black,
                    0.84f to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    LazyColumn(
        state = state,
        userScrollEnabled = onSeek != null,
        contentPadding = PaddingValues(vertical = if (edgeFade) 8.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = modifier.fillMaxWidth().height(height).then(fadeModifier),
    ) {
        itemsIndexed(lines, key = { index, _ -> index }, contentType = { _, _ -> 0 }) { index, line ->
            val sung = index == current
            val isPast = index < current
            val blurDp = if (sung || !allowBlur) 0f else if (isPast) (pastBlur ?: blur) else (nextBlur ?: blur)
            val targetAlpha = if (sung) 1f else if (isPast) pastOpacity else nextOpacity
            val targetScale = if (sung) activeScale else 1f
            val scale = animateFloatAsState(
                targetValue = if (preview) targetScale else targetScale,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 360f),
                label = "flowScale",
            )
            val alphaAnim = animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f),
                label = "flowAlpha",
            )
            val text = (if (sung) prefix else blank) + line.text.ifBlank { Instrumental }
            val layoutHolder = remember(text) { FlowLayoutHolder() }
            val wordRanges = remember(line, prefix) { if (sweep) flowWordRanges(line, prefix.length) else emptyList() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onSeek != null) Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSeek(line.time) } else Modifier,
                    )
                    .graphicsLayer {
                        alpha = alphaAnim.value.coerceIn(0f, 1f)
                        scaleX = scale.value
                        scaleY = scale.value
                        transformOrigin = origin
                        renderEffect = if (blurDp > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            BlurEffectCache.element(blurDp.dp.toPx(), android.graphics.Shader.TileMode.DECAL)
                        } else null
                    },
            ) {
                Text(
                    text = text,
                    color = if (sung && !sweep) activeColor else color,
                    fontSize = fontSize,
                    fontWeight = weight ?: if (sung) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = align.textAlign,
                    onTextLayout = { layoutHolder.result = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (sweep && sung && !preview) Modifier.drawWithContent {
                                drawContent()
                                val result = layoutHolder.result ?: return@drawWithContent
                                val chars = flowSungChars(line, wordRanges, clock, prefix.length, text.length)
                                drawFlowSweep(result, chars, activeColor)
                            } else Modifier,
                        ),
                )
                if (translation && sung) {
                    val romanized by line.romanizedTextFlow.collectAsState()
                    val translated by line.translatedTextFlow.collectAsState()
                    val sub = translated?.takeIf { it.isNotBlank() } ?: romanized?.takeIf { it.isNotBlank() }
                    if (sub != null) {
                        Text(
                            text = sub,
                            color = activeColor.copy(alpha = 0.72f),
                            fontSize = (fontSize.value * 0.78f).coerceAtLeast(10f).sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = align.textAlign,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private class FlowLayoutHolder {
    var result: TextLayoutResult? = null
}

private fun flowWordRanges(line: LyricsEntry, prefixLen: Int): List<IntRange> {
    val words = line.words ?: return emptyList()
    var cursor = 0
    return words.map { word ->
        val text = word.text.trim()
        val at = if (text.isEmpty()) -1 else line.text.indexOf(text, cursor)
        val start = if (at >= 0) at else cursor
        val end = if (at >= 0) at + text.length else (cursor + text.length).coerceAtMost(line.text.length)
        cursor = end
        (start + prefixLen) until (end + prefixLen)
    }
}

private fun flowSungChars(line: LyricsEntry, words: List<IntRange>, clock: LyricClock, prefixLen: Int, totalLen: Int): Float {
    val word = clock.word()
    if (words.isEmpty() || word < 0) return prefixLen + (totalLen - prefixLen) * clock.lineProgress()
    val range = words.getOrNull(word) ?: return totalLen.toFloat()
    return range.first + (range.last + 1 - range.first) * clock.wordProgress()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlowSweep(result: TextLayoutResult, chars: Float, sung: Color) {
    if (chars <= 0f) return
    for (l in 0 until result.lineCount) {
        val start = result.getLineStart(l)
        val end = result.getLineEnd(l, visibleEnd = true)
        if (chars <= start) break
        val left = result.getLineLeft(l)
        val right = result.getLineRight(l)
        val rtl = result.getParagraphDirection(start) == ResolvedTextDirection.Rtl
        val whole = floor(chars).toInt().coerceIn(0, end)
        val next = (whole + 1).coerceAtMost(end)
        val edge = if (chars >= end) {
            if (rtl) left else right
        } else {
            val a = result.getHorizontalPosition(whole, usePrimaryDirection = true)
            val b = result.getHorizontalPosition(next, usePrimaryDirection = true)
            a + (b - a) * (chars - whole)
        }
        val top = result.getLineTop(l)
        val bottom = result.getLineBottom(l)
        clipRect(left = if (rtl) edge else min(left, edge), top = top, right = if (rtl) right else edge, bottom = bottom) {
            drawText(result, color = sung)
        }
    }
}

private const val Instrumental = "♪"
