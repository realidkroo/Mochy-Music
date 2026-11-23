// ShareCinematicFlow.kt
//
// The picked lines in a video, on the card and in the Cinematic poster alike: powered by the same
// karaoke engine as the regular lyrics view (Style 1 per-letter rise + held-note swell/zoom + AGSL
// bloom, Style 2 AGSL lift-and-zoom wave + bloom, soft horizontal sweep edge, exact line boundaries
// so wrapped lines never bleed into the line below). A picture keeps them all lit.

package com.mochyy.mochymusic.ui.player.share

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.constants.LyricsMotionStyle
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.ui.player.KaraokeHead
import com.mochyy.mochymusic.ui.player.MotionBleed
import com.mochyy.mochymusic.ui.player.TimedRange
import com.mochyy.mochymusic.ui.player.advance
import com.mochyy.mochymusic.ui.player.isRtlLine
import com.mochyy.mochymusic.ui.player.karaokeHeadFor
import com.mochyy.mochymusic.ui.player.layerBleed
import com.mochyy.mochymusic.ui.player.rememberBloomModifier
import com.mochyy.mochymusic.ui.player.rememberLegacyWaveModifier
import com.mochyy.mochymusic.ui.player.rememberWordMotionModifier
import com.mochyy.mochymusic.ui.player.shownLineFor
import com.mochyy.mochymusic.ui.player.sungLength
import com.mochyy.mochymusic.ui.player.sweepHeadX
import com.mochyy.mochymusic.ui.player.withLetterMotionFeatures
import kotlin.math.abs
import kotlin.math.exp

// how faded a word waits before it's sung
private const val WaitingAlpha = 0.3f

// subtle emphasis zoom on the line currently being sung, anchored on its reading edge so it never overflows the card
private const val ActiveLineZoom = 0.025f

// soft sweep edge proportions, expressed relative to the line's font height so preview, 1080p and 4k look identical
private const val EdgeSoftChars = 1
private const val LatinLetterEm = 0.55f
private const val EdgeLeadRatio = 0.08f
private const val EdgeSoftRatio = 0.36f

// how long before/after a line's own timestamps its wave/active emphasis eases in and out
private const val ActiveLeadInSec = 0.14
private const val ActiveTailOutSec = 0.24
private const val WaveTailOutSec = 0.28

private val WordRun = Regex("\\S+")

/** One line fed into the share's karaoke clock: its drawn text, its source entry, and when the next line in the song starts. */
@Immutable
internal data class ShareKaraokeLineSpec(
    val text: String,
    val entry: LyricsEntry,
    val nextLineMs: Long?,
)

/** Live karaoke state for one line: driven by the parent block's single frame clock so there are no per-line coroutines. */
@Stable
internal class ShareKaraokeLine(
    val text: String,
    val timed: List<TimedRange>,
    val head: KaraokeHead,
) {
    val startSec: Double = timed.firstOrNull()?.startTime ?: 0.0
    val endSec: Double = timed.lastOrNull()?.endTime ?: startSec

    // 0 when idle/settled (shader skipped), 1 while the line is singing
    val waveAmp = mutableFloatStateOf(0f)

    // 0 when resting, 1 while this line is actively being sung (subtle line zoom + reading emphasis)
    val activeZoom = mutableFloatStateOf(0f)

    fun reset() {
        head.offset.floatValue = 0f
        head.glow.floatValue = 0f
        head.rise.fill(0f)
        head.riseVelocity.fill(0f)
        head.swell.fill(0f)
        head.swellVelocity.fill(0f)
        head.motionTick.intValue++
        waveAmp.floatValue = 0f
        activeZoom.floatValue = 0f
    }

    /** Steps this line to [posSec] over [dt] seconds; returns true while anything is still moving or settling. */
    fun step(posSec: Double, dt: Float, wordMotion: Boolean, snap: Boolean): Boolean {
        val activeForWave = posSec >= startSec - ActiveLeadInSec && posSec <= endSec + WaveTailOutSec
        val activeForZoom = posSec >= startSec - ActiveLeadInSec && posSec <= endSec + ActiveTailOutSec

        val targetWave = if (activeForWave) 1f else 0f
        val targetZoom = if (activeForZoom) 1f else 0f

        // Skip lines that are completely idle (not yet reached or already finished and settled)
        val idleBefore = posSec < startSec - ActiveLeadInSec &&
            head.offset.floatValue == 0f && head.glow.floatValue == 0f &&
            waveAmp.floatValue == 0f && activeZoom.floatValue == 0f
        val idleAfter = posSec > endSec + 1.2 &&
            head.offset.floatValue >= (timed.lastOrNull()?.end ?: 0).toFloat() &&
            head.glow.floatValue <= 0.005f &&
            waveAmp.floatValue == 0f && activeZoom.floatValue == 0f
        if (!snap && (idleBefore || idleAfter)) return false

        val headMoved = head.advance(timed, posSec, if (snap) 1.5f else dt, wordMotion)
        if (head.glow.floatValue < 0.005f && posSec > endSec) {
            head.glow.floatValue = 0f
        }

        val waveMoved = approach(waveAmp, targetWave, if (targetWave > waveAmp.floatValue) 12f else 5f, dt, snap)
        val zoomMoved = approach(activeZoom, targetZoom, 7.5f, dt, snap)
        return headMoved || waveMoved || zoomMoved
    }
}

private fun approach(state: androidx.compose.runtime.MutableFloatState, target: Float, rate: Float, dt: Float, snap: Boolean): Boolean {
    val cur = state.floatValue
    if (cur == target) return false
    if (snap) {
        state.floatValue = target
        return true
    }
    if (dt <= 0f) return false
    val next = cur + (target - cur) * (1f - exp(-rate * dt))
    val settled = if (abs(next - target) < 0.004f) target else next
    state.floatValue = settled
    return settled != cur
}

/**
 * Shared karaoke state for all picked lines on a share card or poster. One `withFrameNanos` loop
 * drives every line with zero `delay()` calls, so both live preview and `OffscreenFrames` manual
 * `BroadcastFrameClock` exports advance deterministically on every single frame.
 */
@Stable
internal class ShareKaraokeState(val lines: List<ShareKaraokeLine>)

@Composable
internal fun rememberShareKaraokeState(
    specs: List<ShareKaraokeLineSpec>,
    positionMs: () -> Long,
    wordMotion: Boolean,
): ShareKaraokeState {
    val state = remember(specs) {
        ShareKaraokeState(
            specs.map { spec ->
                val timed = buildShareTimedRanges(spec.text, spec.entry, spec.nextLineMs)
                ShareKaraokeLine(spec.text, timed, karaokeHeadFor(spec.text))
            },
        )
    }

    LaunchedEffect(state, positionMs, wordMotion) {
        var lastFrameNanos = -1L
        var lastPosSec = Double.NaN
        while (true) {
            withFrameNanos { frameNanos ->
                val posSec = positionMs() / 1000.0
                val firstFrame = lastFrameNanos < 0L
                val loopedBack = !lastPosSec.isNaN() && posSec < lastPosSec - 0.25
                val dt = if (firstFrame) 0f else ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.064f)

                if (loopedBack) {
                    state.lines.forEach { it.reset() }
                }

                // Skip work during warm-up frames where clock hasn't moved after initial snap
                if (firstFrame || loopedBack || dt > 0f || posSec != lastPosSec) {
                    val snap = firstFrame || loopedBack
                    for (i in state.lines.indices) {
                        state.lines[i].step(posSec, dt, wordMotion, snap)
                    }
                }

                lastFrameNanos = frameNanos
                lastPosSec = posSec
            }
        }
    }

    return state
}

/**
 * Builds [TimedRange]s for [text] from [entry]'s syllable or word timestamps (matching case-insensitively
 * so Cinematic ALL-CAPS words still use exact provider timing), falling back to a natural letter-weighted
 * spread across the line when provider word timing isn't present or can't be matched.
 */
internal fun buildShareTimedRanges(
    text: String,
    entry: LyricsEntry,
    nextLineMs: Long?,
): List<TimedRange> {
    if (text.isEmpty()) return emptyList()

    val pieces = (entry.syllables ?: entry.words)?.filter { it.text.isNotBlank() }.orEmpty()
    if (pieces.isNotEmpty()) {
        val matched = ArrayList<TimedRange>(pieces.size)
        var searchFrom = 0
        for (piece in pieces) {
            val trimmed = piece.text.trim()
            var start = text.indexOf(trimmed, searchFrom, ignoreCase = true)
            var matchedLen = trimmed.length
            if (start < 0) {
                val bare = trimmed.filterNot { it == '(' || it == ')' || it == '[' || it == ']' }.trim()
                if (bare.isNotEmpty()) {
                    start = text.indexOf(bare, searchFrom, ignoreCase = true)
                    matchedLen = bare.length
                }
            }
            if (start < 0) continue
            val end = (start + matchedLen).coerceAtMost(text.length)
            searchFrom = end
            val startSec = piece.startTime
            val endSec = maxOf(piece.endTime, startSec + 0.05)
            matched.add(TimedRange(start, end, startSec, endSec, sungLength(text, start, end)))
        }
        if (matched.size >= (pieces.size + 1) / 2 && matched.isNotEmpty()) {
            return matched
        }
    }

    // Fallback for line-synced lyrics: spread across visual words weighted by syllable/letter count
    val words = WordRun.findAll(text).map { it.range }.toList()
    if (words.isEmpty()) return emptyList()
    val startSec = entry.time / 1000.0
    val rawEndSec = pieces.lastOrNull()?.endTime?.takeIf { it > startSec }
        ?: nextLineMs?.let { it / 1000.0 }?.takeIf { it > startSec }
        ?: (startSec + 3.0)
    // Cap untimed lines so a short line before a long instrumental break doesn't crawl for 15 seconds
    val maxNaturalSpan = maxOf(3.8, words.size * 0.55)
    val spanSec = (rawEndSec - startSec).coerceIn(0.4, maxNaturalSpan)
    val weights = IntArray(words.size) { i -> sungLength(text, words[i].first, words[i].last + 1) }
    val totalWeight = weights.sum().coerceAtLeast(1)
    var at = startSec
    return words.mapIndexed { i, range ->
        val dur = maxOf(0.05, spanSec * weights[i] / totalWeight.toDouble())
        val end = at + dur
        TimedRange(range.first, range.last + 1, at, end, weights[i]).also { at = end }
    }
}

/**
 * One animated lyric line on a share card or Cinematic poster.
 *
 * Uses the exact same rendering stack as [com.mochyy.mochymusic.ui.player.KaraokeSweepText]:
 * - Style 1 (`MOTION` / `BORING`): per-letter wave rise + held-note lift & zoom/swell + AGSL bloom
 * - Style 2 (`LEGACY`): AGSL lift-and-zoom wave + AGSL bloom
 * - `PLAIN`: silky soft-edge horizontal karaoke sweep
 * - Exact visual line boundaries between wrapped lines so singing line N never lights half of line N+1.
 */
@Composable
internal fun ShareKaraokeText(
    line: ShareKaraokeLine,
    waitingText: AnnotatedString,
    litText: AnnotatedString,
    waitingColor: Color,
    style: TextStyle,
    baseTextSizeSp: Float,
    motionStyle: LyricsMotionStyle,
    highBloom: Boolean,
    bleed: Dp = MotionBleed,
    modifier: Modifier = Modifier,
) {
    val wordMotion = motionStyle.risesWords
    val effectiveStyle = remember(style, wordMotion) { style.withLetterMotionFeatures(wordMotion) }
    val waitingStyle = remember(effectiveStyle, waitingColor) { effectiveStyle.copy(color = waitingColor) }
    val layoutHolder = remember(line) { arrayOfNulls<TextLayoutResult>(1) }
    val sungPath = remember(line) { Path() }
    val head = line.head
    val textLength = litText.length

    val density = LocalDensity.current
    // Scale AGSL lift/bloom pixel uniforms proportionally to this line's rendered font height
    // (regular lyrics screen is ~96px at 32sp/3x density) so preview, 1080p and 4K match 1:1.
    val pixelScale = remember(baseTextSizeSp, density) {
        with(density) { (baseTextSizeSp.sp.toPx() / 96f).coerceIn(0.45f, 2.5f) }
    }
    val motionScale = remember(baseTextSizeSp) {
        (baseTextSizeSp / 32f).coerceIn(0.65f, 1.15f)
    }

    // Only mount the lit copy + offscreen layer once the sweep has reached this line
    val reached by remember(head) { derivedStateOf { head.offset.floatValue > 0f } }

    val effect: Modifier
    val motion: Modifier
    if (wordMotion) {
        effect = if (motionStyle.shaded) {
            rememberBloomModifier(
                layoutHolder = layoutHolder,
                head = head,
                textLength = textLength,
                amplitudeProvider = { line.waveAmp.floatValue },
                highBloom = highBloom,
                bleed = bleed,
                pixelScale = pixelScale,
            )
        } else {
            Modifier
        }
        val fakeGlow = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && motionStyle.shaded
        motion = rememberWordMotionModifier(
            layoutHolder = layoutHolder,
            head = head,
            riseAmplitudeProvider = { 1f },
            fakeGlowProvider = { if (fakeGlow) line.waveAmp.floatValue else 0f },
            motionScale = motionScale,
            bleed = bleed,
        )
    } else {
        effect = if (motionStyle.shaded) {
            rememberLegacyWaveModifier(
                layoutHolder = layoutHolder,
                head = head,
                textLength = textLength,
                amplitudeProvider = { line.waveAmp.floatValue },
                highBloom = highBloom,
                bleed = bleed,
                pixelScale = pixelScale,
            )
        } else {
            Modifier
        }
        motion = Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val z = line.activeZoom.floatValue
                if (z > 0.001f) {
                    val s = 1f + ActiveLineZoom * z
                    scaleX = s
                    scaleY = s
                    val layout = layoutHolder[0]
                    val rtl = layout != null && layout.lineCount > 0 && layout.isRtlLine(0)
                    transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                }
            }
            .layerBleed(bleed)
            .then(effect)
            .padding(bleed)
            .then(motion),
    ) {
        BasicText(
            text = waitingText,
            style = waitingStyle,
            overflow = TextOverflow.Clip,
            onTextLayout = { layoutHolder[0] = it },
        )

        if (reached) {
            BasicText(
                text = litText,
                style = effectiveStyle,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .layerBleed(bleed)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .padding(bleed)
                    .drawWithContent {
                        val layout = layoutHolder[0] ?: return@drawWithContent
                        val h = head.offset.floatValue
                        if (h <= 0f || textLength == 0) return@drawWithContent

                        val reach = bleed.toPx()
                        val lastLine = layout.lineCount - 1
                        // Reach into the bleed ONLY on outer edges (above first line, below last line) —
                        // stop at exact visual line boundaries between wrapped lines so singing line N
                        // NEVER bleeds down into the top half of line N+1!
                        fun topOf(l: Int) = layout.getLineTop(l) - if (l == 0) reach else 0f
                        fun bottomOf(l: Int) = layout.getLineBottom(l) + if (l == lastLine) reach else 0f

                        val headOffset = h.toInt().coerceIn(0, textLength)
                        val headLine = layout.shownLineFor(headOffset.coerceAtMost(textLength - 1))
                        val rtl = layout.isRtlLine(headLine)
                        val flow = if (rtl) -1f else 1f

                        val headX = layout.sweepHeadX(h, headOffset, headLine, textLength, rtl)
                        val lineTop = topOf(headLine)
                        val lineBottom = bottomOf(headLine)
                        val lineHeightPx = (layout.getLineBottom(headLine) - layout.getLineTop(headLine)).coerceAtLeast(1f)

                        val softFrom = layout.getHorizontalPosition(
                            (headOffset - EdgeSoftChars).coerceAtLeast(layout.getLineStart(headLine)),
                            true,
                        )
                        val softMax = lineHeightPx * LatinLetterEm
                        val edgeSoftPx = lineHeightPx * EdgeSoftRatio
                        val edgeLeadPx = lineHeightPx * EdgeLeadRatio
                        val fromX = if ((headX - softFrom) * flow > 0f) {
                            headX - flow * minOf((headX - softFrom) * flow, softMax)
                        } else {
                            headX - edgeSoftPx * flow
                        }
                        val toX = headX + edgeLeadPx * flow

                        sungPath.rewind()
                        for (l in 0..headLine) {
                            var left = layout.getLineLeft(l) - reach
                            var right = layout.getLineRight(l) + reach
                            if (l == headLine && headOffset < textLength) {
                                if (rtl) left = maxOf(toX, left) else right = minOf(toX, right)
                            }
                            if (right > left) {
                                sungPath.addRect(Rect(left, topOf(l), right, bottomOf(l)))
                            }
                        }

                        clipPath(sungPath) { this@drawWithContent.drawContent() }

                        if (headOffset < textLength) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Black,
                                    1f to Color.Transparent,
                                    startX = fromX,
                                    endX = toX,
                                ),
                                topLeft = Offset(minOf(fromX, toX), lineTop),
                                size = Size(abs(toX - fromX), lineBottom - lineTop),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    },
            )
        }
    }
}

internal fun waitingInkOf(ink: Color): Color = ink.copy(alpha = ink.alpha * WaitingAlpha)
