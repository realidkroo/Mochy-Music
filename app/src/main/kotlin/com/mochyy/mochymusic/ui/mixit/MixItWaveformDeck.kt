// MixItWaveformDeck.kt

package com.mochyy.mochymusic.ui.mixit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.crossmix.TrackAnalysis
import com.mochyy.mochymusic.db.entities.PlaylistTransition
import com.mochyy.mochymusic.mixit.MixItEffect
import com.mochyy.mochymusic.mixit.MixItLimits
import com.mochyy.mochymusic.mixit.MixItPlanner
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class ActiveDrag {
    None,
    OutWave,
    InWave,
    OutFadeStart,
    OutFadeEnd,
    InFadeStart,
    InFadeEnd,
    WindowLeft,
    WindowRight,
}

/**
 * High-resolution dual-waveform studio deck for Mix It.
 *
 * - Draws real 32 Hz PCM peak + inner RMS body waveforms with crisp 2 dp bar resolution so drum
 *   transients, vocal breaks, and drops are clearly visible.
 * - Start/end timestamps sit directly on the yellow box edges so cue points are unambiguous.
 * - Drag either track horizontally (or scrub the full-song strip) to place the transition anywhere.
 * - In Curve & FX mode: drag the 4 glowing yellow volume handles, tap yellow `Fx` for effects,
 *   or tap yellow `Mix` for Voice / Bass / Instrument sliders.
 * - In Edit Point mode: drag `<` / `>` handles to resize the transition window.
 */
@Composable
internal fun MixItWaveformDeck(
    draft: PlaylistTransition,
    outDurationMs: Long,
    inDurationMs: Long,
    outAnalysis: TrackAnalysis?,
    inAnalysis: TrackAnalysis?,
    analyzingOut: Boolean,
    analyzingIn: Boolean,
    editPointMode: Boolean,
    isBeatMatched: Boolean,
    playheadWindowProgressProvider: () -> Float?,
    onDraftChange: (PlaylistTransition) -> Unit,
    onDragActiveChange: (Boolean) -> Unit,
    onOpenOutEffects: () -> Unit,
    onOpenInEffects: () -> Unit,
    onOpenOutMix: () -> Unit,
    onOpenInMix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val currentDraft by rememberUpdatedState(draft)
    val currentEditPoint by rememberUpdatedState(editPointMode)
    val currentOutAnalysis by rememberUpdatedState(outAnalysis)
    val currentInAnalysis by rememberUpdatedState(inAnalysis)
    val currentOutDur by rememberUpdatedState(outDurationMs.coerceAtLeast(10_000L))
    val currentInDur by rememberUpdatedState(inDurationMs.coerceAtLeast(10_000L))
    val currentOnChange by rememberUpdatedState(onDraftChange)
    val currentOnDragActive by rememberUpdatedState(onDragActiveChange)

    val outFx = remember(draft.outEffect) { MixItEffect.fromName(draft.outEffect) }
    val inFx = remember(draft.inEffect) { MixItEffect.fromName(draft.inEffect) }
    val outMixActive = draft.outVoiceLevel < 0.99f || draft.outBassLevel < 0.99f || draft.outInstrumentLevel < 0.99f
    val inMixActive = draft.inVoiceLevel < 0.99f || draft.inBassLevel < 0.99f || draft.inInstrumentLevel < 0.99f

    val rawWindowFrac = remember(draft.durationMs) {
        val norm = ((draft.durationMs - MixItLimits.MinDurationMs).toFloat() /
            (MixItLimits.MaxDurationMs - MixItLimits.MinDurationMs).toFloat()).coerceIn(0f, 1f)
        0.38f + 0.34f * norm
    }
    val windowFrac by animateFloatAsState(
        targetValue = rawWindowFrac,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "mixItWindowWidth",
    )
    val editHighlight by animateFloatAsState(
        targetValue = if (editPointMode) 1f else 0f,
        animationSpec = tween(200),
        label = "mixItEditHighlight",
    )

    // Only run the infinite scan pulse while a track is actively decoding; never tick when idle.
    val scanPhaseState = if (analyzingOut || analyzingIn) {
        rememberInfiniteTransition(label = "waveScan").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1_500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "waveScanPos",
        )
    } else {
        null
    }
    val scanPhaseProvider: () -> Float = remember(scanPhaseState) { { scanPhaseState?.value ?: 0f } }
    val outBarSet = remember(outAnalysis) { buildBarBeatSet(outAnalysis) }
    val inBarSet = remember(inAnalysis) { buildBarBeatSet(inAnalysis) }
    val waveScratch = remember { FloatArray(2) }
    val curvePath = remember { Path() }
    val fillPath = remember { Path() }
    val shapePath = remember { Path() }
    val windowBrushCache = remember { WindowBrushCache() }

    var activeDrag by remember { mutableStateOf(ActiveDrag.None) }
    var lastHapticBeat by remember { mutableStateOf(-1L) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Full-song quick-jump scrubber for Song 1
        FullSongMiniScrubber(
            analysis = outAnalysis,
            durationMs = currentOutDur,
            windowStartMs = draft.outStartMs,
            windowDurationMs = draft.durationMs,
            isLoading = analyzingOut,
            labelLeft = formatMixClock(draft.outStartMs),
            labelRight = formatMixClock(currentOutDur),
            waveColor = MixItColors.Track1Wave,
            onJumpTo = { newStart ->
                val maxStart = (currentOutDur - 1_500L).coerceAtLeast(0L)
                val snapped = if (currentDraft.snapToBeat) {
                    MixItPlanner.snapToNearestBeat(newStart, currentOutAnalysis)
                } else newStart
                currentOnChange(currentDraft.copy(outStartMs = snapped.coerceIn(0L, maxStart)))
            },
            onDragActiveChange = currentOnDragActive,
        )

        Spacer(Modifier.height(6.dp))

        // Main Dual-Waveform Deck Canvas + Floating Yellow Fx & Mix Badges + Box-Anchored Timestamps
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(228.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MixItColors.DeckBackground)
                .border(1.dp, MixItColors.Hairline, RoundedCornerShape(18.dp)),
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val laneTopPad = with(density) { 20.dp.toPx() }
            val laneGap = with(density) { 12.dp.toPx() }
            val laneHeight = (heightPx - laneTopPad * 2f - laneGap) / 2f
            val lane1Top = laneTopPad
            val lane1Bottom = lane1Top + laneHeight
            val lane2Top = lane1Bottom + laneGap
            val lane2Bottom = lane2Top + laneHeight

            val winWidthPx = widthPx * windowFrac
            val winLeftPx = (widthPx - winWidthPx) / 2f
            val winRightPx = winLeftPx + winWidthPx
            val pxPerMs = (winWidthPx / draft.durationMs.coerceAtLeast(500L).toFloat()).coerceAtLeast(0.005f)

            val currentWinLeft by rememberUpdatedState(winLeftPx)
            val currentWinRight by rememberUpdatedState(winRightPx)
            val currentWinWidth by rememberUpdatedState(winWidthPx)
            val currentPxPerMs by rememberUpdatedState(pxPerMs)
            val handleHitRadius = with(density) { 26.dp.toPx() }
            val edgeHitWidth = with(density) { 26.dp.toPx() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pos = down.position
                            val d = currentDraft
                            val wl = currentWinLeft
                            val wr = currentWinRight
                            val ww = currentWinWidth
                            val isEdit = currentEditPoint

                            val target: ActiveDrag = if (isEdit) {
                                when {
                                    abs(pos.x - wl) <= edgeHitWidth && pos.y in lane1Top..lane2Bottom -> ActiveDrag.WindowLeft
                                    abs(pos.x - wr) <= edgeHitWidth && pos.y in lane1Top..lane2Bottom -> ActiveDrag.WindowRight
                                    pos.y < (lane1Bottom + lane2Top) / 2f -> ActiveDrag.OutWave
                                    else -> ActiveDrag.InWave
                                }
                            } else {
                                val pOutStart = Offset(wl + d.outFadeStart * ww, lane1Top + 10f)
                                val pOutEnd = Offset(wl + d.outFadeEnd * ww, lane1Bottom - 10f)
                                val pInStart = Offset(wl + d.inFadeStart * ww, lane2Bottom - 10f)
                                val pInEnd = Offset(wl + d.inFadeEnd * ww, lane2Top + 10f)

                                val candidates = listOf(
                                    ActiveDrag.OutFadeStart to hypot(pos.x - pOutStart.x, pos.y - pOutStart.y),
                                    ActiveDrag.OutFadeEnd to hypot(pos.x - pOutEnd.x, pos.y - pOutEnd.y),
                                    ActiveDrag.InFadeStart to hypot(pos.x - pInStart.x, pos.y - pInStart.y),
                                    ActiveDrag.InFadeEnd to hypot(pos.x - pInEnd.x, pos.y - pInEnd.y),
                                )
                                val nearest = candidates.minByOrNull { it.second }
                                if (nearest != null && nearest.second <= handleHitRadius) {
                                    nearest.first
                                } else if (pos.y < (lane1Bottom + lane2Top) / 2f) {
                                    ActiveDrag.OutWave
                                } else {
                                    ActiveDrag.InWave
                                }
                            }

                            activeDrag = target
                            currentOnDragActive(true)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            down.consume()

                            val dragPointerId = down.id
                            var working = currentDraft

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == dragPointerId }
                                    ?: event.changes.firstOrNull()
                                    ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    change.consume()
                                    val wLeft = currentWinLeft
                                    val wWidth = currentWinWidth.coerceAtLeast(40f)
                                    val scale = currentPxPerMs

                                    working = when (target) {
                                        ActiveDrag.OutWave -> {
                                            val deltaMs = (-delta.x / scale).toLong()
                                            val maxStart = (currentOutDur - 1_500L).coerceAtLeast(0L)
                                            val next = (working.outStartMs + deltaMs).coerceIn(0L, maxStart)
                                            if (working.snapToBeat) {
                                                val beatMs = currentOutAnalysis?.beatMs?.toLong()?.takeIf { it > 200L } ?: 500L
                                                val beatIdx = next / beatMs
                                                if (beatIdx != lastHapticBeat) {
                                                    lastHapticBeat = beatIdx
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                            working.copy(outStartMs = next)
                                        }

                                        ActiveDrag.InWave -> {
                                            val deltaMs = (-delta.x / scale).toLong()
                                            val maxIn = (currentInDur - 1_500L).coerceAtLeast(0L)
                                            val next = (working.inStartMs + deltaMs).coerceIn(0L, maxIn)
                                            if (working.snapToBeat) {
                                                val beatMs = currentInAnalysis?.beatMs?.toLong()?.takeIf { it > 200L } ?: 500L
                                                val beatIdx = next / beatMs
                                                if (beatIdx != lastHapticBeat) {
                                                    lastHapticBeat = beatIdx
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                            working.copy(inStartMs = next)
                                        }

                                        ActiveDrag.OutFadeStart -> {
                                            val frac = ((change.position.x - wLeft) / wWidth)
                                                .coerceIn(0.0f, (working.outFadeEnd - 0.06f).coerceAtLeast(0.02f))
                                            working.copy(
                                                outFadeStart = frac,
                                                swapFrac = computeAutoSwapFrac(frac, working.outFadeEnd, working.inFadeStart, working.inFadeEnd),
                                                presetName = null,
                                            )
                                        }

                                        ActiveDrag.OutFadeEnd -> {
                                            val frac = ((change.position.x - wLeft) / wWidth)
                                                .coerceIn((working.outFadeStart + 0.06f).coerceAtMost(0.98f), 1.0f)
                                            working.copy(
                                                outFadeEnd = frac,
                                                swapFrac = computeAutoSwapFrac(working.outFadeStart, frac, working.inFadeStart, working.inFadeEnd),
                                                presetName = null,
                                            )
                                        }

                                        ActiveDrag.InFadeStart -> {
                                            val frac = ((change.position.x - wLeft) / wWidth)
                                                .coerceIn(0.0f, (working.inFadeEnd - 0.06f).coerceAtLeast(0.02f))
                                            working.copy(
                                                inFadeStart = frac,
                                                swapFrac = computeAutoSwapFrac(working.outFadeStart, working.outFadeEnd, frac, working.inFadeEnd),
                                                presetName = null,
                                            )
                                        }

                                        ActiveDrag.InFadeEnd -> {
                                            val frac = ((change.position.x - wLeft) / wWidth)
                                                .coerceIn((working.inFadeStart + 0.06f).coerceAtMost(0.98f), 1.0f)
                                            working.copy(
                                                inFadeEnd = frac,
                                                swapFrac = computeAutoSwapFrac(working.outFadeStart, working.outFadeEnd, working.inFadeStart, frac),
                                                presetName = null,
                                            )
                                        }

                                        ActiveDrag.WindowLeft -> {
                                            val deltaMs = (-delta.x / scale * 1.4f).toLong()
                                            val nextDur = (working.durationMs + deltaMs)
                                                .coerceIn(MixItLimits.MinDurationMs, MixItLimits.MaxDurationMs)
                                            val appliedDelta = nextDur - working.durationMs
                                            val nextOut = (working.outStartMs - appliedDelta / 2L).coerceAtLeast(0L)
                                            working.copy(durationMs = nextDur, outStartMs = nextOut)
                                        }

                                        ActiveDrag.WindowRight -> {
                                            val deltaMs = (delta.x / scale * 1.4f).toLong()
                                            val nextDur = (working.durationMs + deltaMs)
                                                .coerceIn(MixItLimits.MinDurationMs, MixItLimits.MaxDurationMs)
                                            working.copy(durationMs = nextDur)
                                        }

                                        ActiveDrag.None -> working
                                    }
                                    currentOnChange(working)
                                }
                            }

                            if (working.snapToBeat) {
                                val snapped = when (target) {
                                    ActiveDrag.OutWave -> working.copy(
                                        outStartMs = MixItPlanner.snapToNearestBeat(working.outStartMs, currentOutAnalysis)
                                            .coerceIn(0L, (currentOutDur - 1_500L).coerceAtLeast(0L)),
                                    )
                                    ActiveDrag.InWave -> working.copy(
                                        inStartMs = MixItPlanner.snapToNearestBeat(working.inStartMs, currentInAnalysis)
                                            .coerceIn(0L, (currentInDur - 1_500L).coerceAtLeast(0L)),
                                    )
                                    ActiveDrag.WindowLeft, ActiveDrag.WindowRight -> working.copy(
                                        durationMs = MixItPlanner.snapDurationToBeats(working.durationMs, currentOutAnalysis),
                                    )
                                    else -> working
                                }
                                if (snapped != working) currentOnChange(snapped)
                            }
                            activeDrag = ActiveDrag.None
                            currentOnDragActive(false)
                        }
                    },
            ) {
                val lane1Rect = Rect(0f, lane1Top, size.width, lane1Bottom)
                val lane2Rect = Rect(0f, lane2Top, size.width, lane2Bottom)

                // Dark lane rails + subtle centerline
                drawRect(
                    color = Color.White.copy(alpha = 0.03f),
                    topLeft = Offset(0f, lane1Top),
                    size = Size(size.width, laneHeight),
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.03f),
                    topLeft = Offset(0f, lane2Top),
                    size = Size(size.width, laneHeight),
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, lane1Rect.center.y),
                    end = Offset(size.width, lane1Rect.center.y),
                    strokeWidth = 0.75.dp.toPx(),
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, lane2Rect.center.y),
                    end = Offset(size.width, lane2Rect.center.y),
                    strokeWidth = 0.75.dp.toPx(),
                )

                // Beat & bar grid markers behind waveforms
                drawBeatGrid(
                    rect = lane1Rect,
                    analysis = outAnalysis,
                    barSet = outBarSet,
                    anchorMs = draft.outStartMs,
                    anchorX = winLeftPx,
                    pxPerMs = pxPerMs,
                    highlightInside = isBeatMatched,
                    winLeft = winLeftPx,
                    winRight = winRightPx,
                )
                drawBeatGrid(
                    rect = lane2Rect,
                    analysis = inAnalysis,
                    barSet = inBarSet,
                    anchorMs = draft.inStartMs,
                    anchorX = winLeftPx,
                    pxPerMs = pxPerMs,
                    highlightInside = isBeatMatched,
                    winLeft = winLeftPx,
                    winRight = winRightPx,
                )

                // High-resolution real PCM waveform — Track 1 (Outgoing)
                drawHighResWaveform(
                    rect = lane1Rect,
                    analysis = outAnalysis,
                    durationMs = currentOutDur,
                    anchorMs = draft.outStartMs,
                    anchorX = winLeftPx,
                    pxPerMs = pxPerMs,
                    winLeft = winLeftPx,
                    winRight = winRightPx,
                    baseColor = MixItColors.Track1Wave,
                    activeColor = MixItColors.Track1WaveActive,
                    isLoading = analyzingOut,
                    scanPhaseProvider = scanPhaseProvider,
                    scratch = waveScratch,
                )

                // High-resolution real PCM waveform — Track 2 (Incoming)
                drawHighResWaveform(
                    rect = lane2Rect,
                    analysis = inAnalysis,
                    durationMs = currentInDur,
                    anchorMs = draft.inStartMs,
                    anchorX = winLeftPx,
                    pxPerMs = pxPerMs,
                    winLeft = winLeftPx,
                    winRight = winRightPx,
                    baseColor = MixItColors.Track2Wave,
                    activeColor = MixItColors.Track2WaveActive,
                    isLoading = analyzingIn,
                    scanPhaseProvider = scanPhaseProvider,
                    scratch = waveScratch,
                )

                // Scroll direction hints at outer edges
                drawScrollArrow(
                    center = Offset(12.dp.toPx(), lane1Rect.center.y),
                    pointingRight = true,
                    alpha = if (activeDrag == ActiveDrag.OutWave) 0.9f else 0.4f,
                    path = shapePath,
                )
                drawScrollArrow(
                    center = Offset(size.width - 12.dp.toPx(), lane2Rect.center.y),
                    pointingRight = false,
                    alpha = if (activeDrag == ActiveDrag.InWave) 0.9f else 0.4f,
                    path = shapePath,
                )

                // Yellow Translucent Transition Window Overlay
                val boxTop = lane1Top - 4.dp.toPx()
                val boxBottom = lane2Bottom + 4.dp.toPx()
                val boxHeight = boxBottom - boxTop
                val boxCorner = CornerRadius(14.dp.toPx())

                drawRoundRect(
                    brush = windowBrushCache.get(editHighlight, boxTop, boxBottom),
                    topLeft = Offset(winLeftPx, boxTop),
                    size = Size(winWidthPx, boxHeight),
                    cornerRadius = boxCorner,
                )

                // Start-box left edge accent beam (makes the start line unmistakable when swiping!)
                drawLine(
                    color = MixItColors.Gold.copy(alpha = 0.85f),
                    start = Offset(winLeftPx, boxTop + 10.dp.toPx()),
                    end = Offset(winLeftPx, boxBottom - 10.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                // Bass swap midpoint dashed hairline
                if (draft.bassSwap) {
                    val swapX = winLeftPx + draft.swapFrac.coerceIn(0.1f, 0.9f) * winWidthPx
                    drawLine(
                        color = MixItColors.Gold.copy(alpha = 0.36f),
                        start = Offset(swapX, boxTop + 8.dp.toPx()),
                        end = Offset(swapX, boxBottom - 8.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = BassSwapDashEffect,
                    )
                }

                // Glowing yellow frame
                val borderStroke = (1.5f + 1.4f * editHighlight).dp.toPx()
                drawRoundRect(
                    color = MixItColors.Gold.copy(alpha = 0.52f + 0.48f * editHighlight),
                    topLeft = Offset(winLeftPx, boxTop),
                    size = Size(winWidthPx, boxHeight),
                    cornerRadius = boxCorner,
                    style = Stroke(width = borderStroke),
                )

                if (!editPointMode) {
                    drawFadeCurve(
                        laneRect = lane1Rect,
                        winLeft = winLeftPx,
                        winWidth = winWidthPx,
                        startFrac = draft.outFadeStart,
                        endFrac = draft.outFadeEnd,
                        isOutgoing = true,
                        strokeColor = MixItColors.Gold,
                        fillColor = MixItColors.Gold.copy(alpha = 0.15f),
                        curvePath = curvePath,
                        fillPath = fillPath,
                    )
                    drawFadeCurve(
                        laneRect = lane2Rect,
                        winLeft = winLeftPx,
                        winWidth = winWidthPx,
                        startFrac = draft.inFadeStart,
                        endFrac = draft.inFadeEnd,
                        isOutgoing = false,
                        strokeColor = MixItColors.GoldBright,
                        fillColor = MixItColors.GoldBright.copy(alpha = 0.13f),
                        curvePath = curvePath,
                        fillPath = fillPath,
                    )

                    val padY = 9f
                    drawCurveHandle(
                        center = Offset(winLeftPx + draft.outFadeStart * winWidthPx, lane1Top + padY),
                        active = activeDrag == ActiveDrag.OutFadeStart,
                    )
                    drawCurveHandle(
                        center = Offset(winLeftPx + draft.outFadeEnd * winWidthPx, lane1Bottom - padY),
                        active = activeDrag == ActiveDrag.OutFadeEnd,
                    )
                    drawCurveHandle(
                        center = Offset(winLeftPx + draft.inFadeStart * winWidthPx, lane2Bottom - padY),
                        active = activeDrag == ActiveDrag.InFadeStart,
                    )
                    drawCurveHandle(
                        center = Offset(winLeftPx + draft.inFadeEnd * winWidthPx, lane2Top + padY),
                        active = activeDrag == ActiveDrag.InFadeEnd,
                    )
                } else {
                    val centerY = (boxTop + boxBottom) / 2f
                    drawResizeChevron(
                        center = Offset(winLeftPx - 14.dp.toPx(), centerY),
                        pointingLeft = true,
                        active = activeDrag == ActiveDrag.WindowLeft,
                        path = shapePath,
                    )
                    drawResizeChevron(
                        center = Offset(winRightPx + 14.dp.toPx(), centerY),
                        pointingLeft = false,
                        active = activeDrag == ActiveDrag.WindowRight,
                        path = shapePath,
                    )
                }

                // Live Preview Playhead Beam (read inside draw scope so playhead ticking never recomposes the deck)
                val playheadWindowProgress = playheadWindowProgressProvider()
                if (playheadWindowProgress != null) {
                    val headX = winLeftPx + playheadWindowProgress * winWidthPx
                    if (headX in -10f..(size.width + 10f)) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.24f),
                            start = Offset(headX, 0f),
                            end = Offset(headX, size.height),
                            strokeWidth = 7.dp.toPx(),
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(headX, 0f),
                            end = Offset(headX, size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                        val dSize = 5.dp.toPx()
                        shapePath.reset()
                        shapePath.moveTo(headX, 2f)
                        shapePath.lineTo(headX + dSize, 2f + dSize)
                        shapePath.lineTo(headX, 2f + dSize * 2f)
                        shapePath.lineTo(headX - dSize, 2f + dSize)
                        shapePath.close()
                        drawPath(shapePath, Color.White)
                    }
                }
            }

            // Floating Yellow Badges: Fx + Mix (Voice/Bass/Instruments sliders!) on BOTH tracks!
            if (!editPointMode) {
                val badgePx = with(density) { 29.dp.toPx() }
                val gapPx = with(density) { 5.dp.toPx() }
                val marginPx = with(density) { 6.dp.toPx() }
                val outColX = (winRightPx + with(density) { 7.dp.toPx() })
                    .coerceAtMost(widthPx - badgePx - marginPx)
                val outStackTop = lane1Top + (laneHeight - (badgePx * 2f + gapPx)) / 2f

                val inColX = (winLeftPx - badgePx - with(density) { 7.dp.toPx() })
                    .coerceAtLeast(marginPx)
                val inStackTop = lane2Top + (laneHeight - (badgePx * 2f + gapPx)) / 2f

                YellowDeckBadge(
                    label = if (outFx != MixItEffect.NONE && outFx != MixItEffect.CROSSFADE) outFx.shortLabel.take(3) else "Fx",
                    highlighted = outFx != MixItEffect.NONE && outFx != MixItEffect.CROSSFADE,
                    onClick = onOpenOutEffects,
                    modifier = Modifier.offset { IntOffset(outColX.roundToInt(), outStackTop.roundToInt()) },
                )
                YellowDeckBadge(
                    label = "Mix",
                    highlighted = outMixActive,
                    onClick = onOpenOutMix,
                    modifier = Modifier.offset { IntOffset(outColX.roundToInt(), (outStackTop + badgePx + gapPx).roundToInt()) },
                )

                YellowDeckBadge(
                    label = if (inFx != MixItEffect.NONE && inFx != MixItEffect.CROSSFADE) inFx.shortLabel.take(3) else "Fx",
                    highlighted = inFx != MixItEffect.NONE && inFx != MixItEffect.CROSSFADE,
                    onClick = onOpenInEffects,
                    modifier = Modifier.offset { IntOffset(inColX.roundToInt(), inStackTop.roundToInt()) },
                )
                YellowDeckBadge(
                    label = "Mix",
                    highlighted = inMixActive,
                    onClick = onOpenInMix,
                    modifier = Modifier.offset { IntOffset(inColX.roundToInt(), (inStackTop + badgePx + gapPx).roundToInt()) },
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.78f))
                        .border(1.dp, MixItColors.Gold.copy(alpha = 0.75f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = formatMixDuration(draft.durationMs, outAnalysis?.beatMs?.toDouble()),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = MixItColors.GoldBright,
                    )
                }
            }

            // box-anchored timestamps right on the start and end edges of the yellow box, safe at any width
            val stampW = with(density) { 40.dp.toPx() }
            val maxStartStampX = (widthPx - stampW * 2f).coerceAtLeast(6f)
            val startStampX = (winLeftPx + with(density) { 2.dp.toPx() }).coerceIn(6f, maxStartStampX)
            val minEndStampX = startStampX + stampW + 4f
            val maxEndStampX = (widthPx - stampW - 6f).coerceAtLeast(minEndStampX)
            val endStampX = (winRightPx - stampW).coerceIn(minEndStampX, maxEndStampX)

            Text(
                text = formatMixClock(draft.outStartMs),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MixItColors.GoldBright,
                modifier = Modifier.offset { IntOffset(startStampX.roundToInt(), with(density) { 2.dp.roundToPx() }) },
            )
            Text(
                text = formatMixClock((draft.outStartMs + draft.durationMs).coerceAtMost(currentOutDur)),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.offset { IntOffset(endStampX.roundToInt(), with(density) { 2.dp.roundToPx() }) },
            )
            Text(
                text = formatMixClock(draft.inStartMs),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MixItColors.GoldBright,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(startStampX.roundToInt(), with(density) { (-2).dp.roundToPx() }) },
            )
            Text(
                text = formatMixClock((draft.inStartMs + draft.durationMs).coerceAtMost(currentInDur)),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(endStampX.roundToInt(), with(density) { (-2).dp.roundToPx() }) },
            )
        }

        Spacer(Modifier.height(6.dp))

        // Full-song quick-jump scrubber for Song 2
        FullSongMiniScrubber(
            analysis = inAnalysis,
            durationMs = currentInDur,
            windowStartMs = draft.inStartMs,
            windowDurationMs = draft.durationMs,
            isLoading = analyzingIn,
            labelLeft = formatMixClock(draft.inStartMs),
            labelRight = formatMixClock(currentInDur),
            waveColor = MixItColors.Track2Wave,
            onJumpTo = { newStart ->
                val maxStart = (currentInDur - 1_500L).coerceAtLeast(0L)
                val snapped = if (currentDraft.snapToBeat) {
                    MixItPlanner.snapToNearestBeat(newStart, currentInAnalysis)
                } else newStart
                currentOnChange(currentDraft.copy(inStartMs = snapped.coerceIn(0L, maxStart)))
            },
            onDragActiveChange = currentOnDragActive,
        )
    }
}

@Composable
private fun YellowDeckBadge(
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(29.dp)
            .clip(CircleShape)
            .background(if (highlighted) MixItColors.Gold else MixItColors.Gold.copy(alpha = 0.85f))
            .border(
                width = if (highlighted) 1.6.dp else 1.dp,
                color = if (highlighted) Color.White else Color.Black.copy(alpha = 0.25f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (label.length >= 3) 8.5.sp else 10.5.sp,
            color = MixItColors.OnGold,
            maxLines = 1,
        )
    }
}

/** High-resolution full-song overview strip using true peak slicing across 96 bars. */
@Composable
private fun FullSongMiniScrubber(
    analysis: TrackAnalysis?,
    durationMs: Long,
    windowStartMs: Long,
    windowDurationMs: Long,
    isLoading: Boolean,
    labelLeft: String,
    labelRight: String,
    waveColor: Color,
    onJumpTo: (Long) -> Unit,
    onDragActiveChange: (Boolean) -> Unit,
) {
    val currentDur by rememberUpdatedState(durationMs.coerceAtLeast(1_000L))
    val currentOnJump by rememberUpdatedState(onJumpTo)
    val currentOnDrag by rememberUpdatedState(onDragActiveChange)
    val miniScratch = remember { FloatArray(2) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = labelLeft,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MixItColors.Gold,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(34.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        currentOnJump((frac * currentDur).toLong())
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { currentOnDrag(true) },
                        onDragEnd = { currentOnDrag(false) },
                        onDragCancel = { currentOnDrag(false) },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            currentOnJump((frac * currentDur).toLong())
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bars = 96
                val step = size.width / bars
                val midY = size.height / 2f
                val maxHalf = size.height * 0.42f
                val hasWave = analysis != null && (analysis.waveform.isNotEmpty() || analysis.energy.isNotEmpty())

                val winStartFrac = (windowStartMs.toFloat() / currentDur).coerceIn(0f, 1f)
                val winEndFrac = ((windowStartMs + windowDurationMs).toFloat() / currentDur).coerceIn(winStartFrac, 1f)
                val hlLeft = winStartFrac * size.width
                val hlRight = max(hlLeft + 6.dp.toPx(), winEndFrac * size.width).coerceAtMost(size.width)

                for (i in 0 until bars) {
                    val x = (i + 0.5f) * step
                    val t0 = (i.toFloat() / bars * currentDur).toLong()
                    val t1 = ((i + 1).toFloat() / bars * currentDur).toLong()
                    val amp = if (hasWave) {
                        analysis.waveformSliceInto(t0, t1, miniScratch)
                        val peak = miniScratch[0]
                        if (peak.isFinite()) peak.coerceIn(0.06f, 1f) else 0.06f
                    } else if (isLoading) {
                        0.2f
                    } else {
                        0.15f
                    }
                    val inside = x in hlLeft..hlRight
                    val h = max(1.dp.toPx(), amp * maxHalf)
                    drawLine(
                        color = if (inside) MixItColors.Gold else waveColor.copy(alpha = 0.42f),
                        start = Offset(x, midY - h),
                        end = Offset(x, midY + h),
                        strokeWidth = (step * 0.58f).coerceIn(1.1f, 2.2f),
                        cap = StrokeCap.Round,
                    )
                }

                drawRoundRect(
                    color = MixItColors.Gold.copy(alpha = 0.2f),
                    topLeft = Offset(hlLeft, 1f),
                    size = Size((hlRight - hlLeft).coerceAtLeast(6.dp.toPx()), size.height - 2f),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                drawRoundRect(
                    color = MixItColors.Gold.copy(alpha = 0.85f),
                    topLeft = Offset(hlLeft, 1f),
                    size = Size((hlRight - hlLeft).coerceAtLeast(6.dp.toPx()), size.height - 2f),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
        }
        Text(
            text = labelRight,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MixItColors.TextMuted,
            modifier = Modifier.width(34.dp),
        )
    }
}

private val BassSwapDashEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))

private fun buildBarBeatSet(analysis: TrackAnalysis?): Set<Long> {
    if (analysis == null || !analysis.isBeatReliable || analysis.barBeats.isEmpty()) return emptySet()
    val set = HashSet<Long>(analysis.barBeats.size)
    for (bIdx in analysis.barBeats) {
        if (bIdx in analysis.beatsMs.indices) set.add(analysis.beatsMs[bIdx])
    }
    return set
}

private fun DrawScope.drawBeatGrid(
    rect: Rect,
    analysis: TrackAnalysis?,
    barSet: Set<Long>,
    anchorMs: Long,
    anchorX: Float,
    pxPerMs: Float,
    highlightInside: Boolean,
    winLeft: Float,
    winRight: Float,
) {
    if (analysis == null || !analysis.isBeatReliable) return
    val visibleStartMs = (anchorMs - anchorX / pxPerMs).toLong()
    val visibleEndMs = (anchorMs + (size.width - anchorX) / pxPerMs).toLong()
    for (beatMs in analysis.beatsMs) {
        if (beatMs < visibleStartMs) continue
        if (beatMs > visibleEndMs) break
        val x = anchorX + (beatMs - anchorMs) * pxPerMs
        val isBar = beatMs in barSet
        val inWin = x in winLeft..winRight
        val color = when {
            isBar && inWin && highlightInside -> MixItColors.Gold.copy(alpha = 0.42f)
            isBar -> MixItColors.Gold.copy(alpha = 0.22f)
            inWin && highlightInside -> MixItColors.Gold.copy(alpha = 0.16f)
            else -> Color.White.copy(alpha = 0.06f)
        }
        drawLine(
            color = color,
            start = Offset(x, rect.top + 1f),
            end = Offset(x, rect.bottom - 1f),
            strokeWidth = if (isBar) 1.3.dp.toPx() else 0.8.dp.toPx(),
        )
    }
}

/**
 * Draws a crisp DAW-grade dual-layer waveform (outer sharp peak + bright inner RMS core) sampled
 * directly from real 32 Hz decoded PCM buckets via [TrackAnalysis.waveformSliceInto].
 */
private fun DrawScope.drawHighResWaveform(
    rect: Rect,
    analysis: TrackAnalysis?,
    durationMs: Long,
    anchorMs: Long,
    anchorX: Float,
    pxPerMs: Float,
    winLeft: Float,
    winRight: Float,
    baseColor: Color,
    activeColor: Color,
    isLoading: Boolean,
    scanPhaseProvider: () -> Float,
    scratch: FloatArray,
) {
    val barSpacing = 2.1.dp.toPx()
    val barWidth = 1.25.dp.toPx()
    val count = (rect.width / barSpacing).toInt().coerceAtLeast(32)
    val midY = rect.center.y
    val maxHalfH = rect.height * 0.44f
    val hasRealWave = analysis != null && (analysis.waveform.isNotEmpty() || analysis.energy.isNotEmpty())
    val msPerBar = (barSpacing / pxPerMs).toLong().coerceAtLeast(16L)
    val minPeakH = 1.2.dp.toPx()
    val minBodyH = 1.dp.toPx()

    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        // Out-of-bounds shading before 0:00 and past track end
        val startX = anchorX + (0L - anchorMs) * pxPerMs
        if (startX > 0f) {
            drawRect(
                color = Color.Black.copy(alpha = 0.45f),
                topLeft = Offset(0f, rect.top),
                size = Size(startX.coerceAtMost(rect.width), rect.height),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(startX, rect.top),
                end = Offset(startX, rect.bottom),
                strokeWidth = 1.2.dp.toPx(),
            )
        }
        val endX = anchorX + (durationMs - anchorMs) * pxPerMs
        if (endX < rect.width) {
            val left = endX.coerceAtLeast(0f)
            drawRect(
                color = Color.Black.copy(alpha = 0.45f),
                topLeft = Offset(left, rect.top),
                size = Size(rect.width - left, rect.height),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(endX, rect.top),
                end = Offset(endX, rect.bottom),
                strokeWidth = 1.2.dp.toPx(),
            )
        }

        if (hasRealWave) {
            val outerActiveCol = activeColor.copy(alpha = 0.72f)
            val outerBaseCol = baseColor.copy(alpha = 0.56f)
            val coreBaseCol = baseColor.copy(alpha = 0.92f)
            for (i in 0 until count) {
                val x = (i + 0.5f) * barSpacing
                val tCenter = (anchorMs + (x - anchorX) / pxPerMs).toLong()
                if (tCenter < 0L || tCenter > durationMs) continue
                analysis.waveformSliceInto(tCenter - msPerBar / 2L, tCenter + msPerBar / 2L, scratch)
                waveformBarNorms(scratch[0], scratch[1], scratch)
                val inWindow = x in winLeft..winRight
                val peakH = max(minPeakH, scratch[0] * maxHalfH)
                val bodyH = max(minBodyH, scratch[1] * maxHalfH)
                val outerCol = if (inWindow) outerActiveCol else outerBaseCol
                val coreCol = if (inWindow) activeColor else coreBaseCol
                // Crisp outer peak spike
                drawLine(
                    color = outerCol,
                    start = Offset(x, midY - peakH),
                    end = Offset(x, midY + peakH),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
                // Brighter inner RMS body core (classic Serato/DAW depth)
                drawLine(
                    color = coreCol,
                    start = Offset(x, midY - bodyH),
                    end = Offset(x, midY + bodyH),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
        } else {
            // Honest subtle scanning pulse while real audio PCM is decoding (never a fake music wave)
            val scanX = if (isLoading) scanPhaseProvider() * rect.width else 0f
            val baseDim = baseColor.copy(alpha = 0.22f)
            val baseH = 2.dp.toPx()
            val pulseH = 8.dp.toPx()
            for (i in 0 until count) {
                val x = (i + 0.5f) * barSpacing
                val tCenter = (anchorMs + (x - anchorX) / pxPerMs).toLong()
                if (tCenter < 0L || tCenter > durationMs) continue
                val dist = abs(x - scanX) / (rect.width * 0.18f).coerceAtLeast(1f)
                val pulse = if (isLoading) (1f - dist).coerceIn(0f, 1f) else 0f
                val h = baseH + pulse * pulseH
                drawLine(
                    color = if (pulse > 0.15f) MixItColors.Gold.copy(alpha = 0.25f + 0.45f * pulse) else baseDim,
                    start = Offset(x, midY - h),
                    end = Offset(x, midY + h),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.drawFadeCurve(
    laneRect: Rect,
    winLeft: Float,
    winWidth: Float,
    startFrac: Float,
    endFrac: Float,
    isOutgoing: Boolean,
    strokeColor: Color,
    fillColor: Color,
    curvePath: Path,
    fillPath: Path,
) {
    val topY = laneRect.top + 8f
    val bottomY = laneRect.bottom - 8f
    val steps = 40
    curvePath.reset()
    fillPath.reset()

    for (i in 0..steps) {
        val f = i.toFloat() / steps
        val x = winLeft + f * winWidth
        val gain = if (isOutgoing) {
            when {
                f <= startFrac -> 1f
                f >= endFrac -> 0f
                else -> cos(((f - startFrac) / (endFrac - startFrac).coerceAtLeast(0.01f)) * (PI.toFloat() / 2f))
            }
        } else {
            when {
                f <= startFrac -> 0f
                f >= endFrac -> 1f
                else -> sin(((f - startFrac) / (endFrac - startFrac).coerceAtLeast(0.01f)) * (PI.toFloat() / 2f))
            }
        }
        val y = bottomY - gain * (bottomY - topY)
        if (i == 0) {
            curvePath.moveTo(x, y)
            fillPath.moveTo(x, bottomY)
            fillPath.lineTo(x, y)
        } else {
            curvePath.lineTo(x, y)
            fillPath.lineTo(x, y)
        }
    }
    fillPath.lineTo(winLeft + winWidth, bottomY)
    fillPath.close()

    drawPath(fillPath, color = fillColor)
    drawPath(
        path = curvePath,
        color = strokeColor,
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawCurveHandle(center: Offset, active: Boolean) {
    val haloRadius = (if (active) 16.dp else 11.dp).toPx()
    val dotRadius = (if (active) 8.5.dp else 6.5.dp).toPx()
    drawCircle(
        color = MixItColors.Gold.copy(alpha = if (active) 0.35f else 0.18f),
        radius = haloRadius,
        center = center,
    )
    drawCircle(
        color = MixItColors.Gold,
        radius = dotRadius,
        center = center,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.92f),
        radius = dotRadius,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
    )
}

private fun DrawScope.drawResizeChevron(center: Offset, pointingLeft: Boolean, active: Boolean, path: Path) {
    val arm = (if (active) 8.dp else 6.5.dp).toPx()
    val dir = if (pointingLeft) -1f else 1f
    path.reset()
    path.moveTo(center.x - dir * arm * 0.55f, center.y - arm)
    path.lineTo(center.x + dir * arm * 0.55f, center.y)
    path.lineTo(center.x - dir * arm * 0.55f, center.y + arm)
    drawPath(
        path = path,
        color = if (active) Color.White else MixItColors.Gold,
        style = Stroke(width = (if (active) 3.dp else 2.4.dp).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawScrollArrow(center: Offset, pointingRight: Boolean, alpha: Float, path: Path) {
    val arm = 5.dp.toPx()
    val dir = if (pointingRight) 1f else -1f
    path.reset()
    path.moveTo(center.x - dir * arm * 0.45f, center.y - arm)
    path.lineTo(center.x + dir * arm * 0.45f, center.y)
    path.lineTo(center.x - dir * arm * 0.45f, center.y + arm)
    drawPath(
        path = path,
        color = Color.White.copy(alpha = alpha),
        style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun computeAutoSwapFrac(
    outStart: Float,
    outEnd: Float,
    inStart: Float,
    inEnd: Float,
): Float {
    val outMid = (outStart + outEnd) * 0.5f
    val inMid = (inStart + inEnd) * 0.5f
    return ((outMid + inMid) * 0.5f).coerceIn(0.15f, 0.9f)
}

// clamps peak first so silent buckets (peak == 0) never hand coerceIn a max smaller than its min
internal fun waveformBarNorms(peak: Float, body: Float, out: FloatArray) {
    val peakNorm = if (peak.isFinite()) peak.coerceIn(0.03f, 1f) else 0.03f
    val bodyNorm = if (body.isFinite()) (body * 0.72f).coerceIn(0.02f, peakNorm) else 0.02f
    out[0] = peakNorm
    out[1] = bodyNorm
}

private class WindowBrushCache {
    private var lastHighlight = Float.NaN
    private var lastTop = Float.NaN
    private var lastBottom = Float.NaN
    private var cached: Brush? = null

    fun get(editHighlight: Float, boxTop: Float, boxBottom: Float): Brush {
        val current = cached
        if (current != null && editHighlight == lastHighlight && boxTop == lastTop && boxBottom == lastBottom) {
            return current
        }
        lastHighlight = editHighlight
        lastTop = boxTop
        lastBottom = boxBottom
        return Brush.verticalGradient(
            colors = listOf(
                MixItColors.Gold.copy(alpha = 0.13f + 0.08f * editHighlight),
                MixItColors.Gold.copy(alpha = 0.06f + 0.06f * editHighlight),
                MixItColors.Gold.copy(alpha = 0.13f + 0.08f * editHighlight),
            ),
            startY = boxTop,
            endY = boxBottom,
        ).also { cached = it }
    }
}
