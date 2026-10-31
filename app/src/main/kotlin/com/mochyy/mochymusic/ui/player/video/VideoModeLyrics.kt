package com.mochyy.mochymusic.ui.player.video

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.LyricsHighBloomKey
import com.mochyy.mochymusic.constants.LyricsMotionStyle
import com.mochyy.mochymusic.constants.LyricsMotionStyleKey
import com.mochyy.mochymusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsTranslationHelper
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.ui.player.LyricRow
import com.mochyy.mochymusic.ui.player.LyricsAlignment
import com.mochyy.mochymusic.ui.player.LyricsGlowLine
import com.mochyy.mochymusic.ui.player.LyricsInterludeDots
import com.mochyy.mochymusic.ui.player.LyricsLineState
import com.mochyy.mochymusic.ui.player.LyricsLoadingDots
import com.mochyy.mochymusic.ui.player.LyricsReadings
import com.mochyy.mochymusic.ui.player.LyricsRecenterDurationMs
import com.mochyy.mochymusic.ui.player.LyricsStagger
import com.mochyy.mochymusic.ui.player.NoInterlude
import com.mochyy.mochymusic.ui.player.RubyPlacement
import com.mochyy.mochymusic.ui.player.TickerSlot
import com.mochyy.mochymusic.ui.player.activeInterludeOf
import com.mochyy.mochymusic.ui.player.alignmentFor
import com.mochyy.mochymusic.ui.player.blurEffectForRadius
import com.mochyy.mochymusic.ui.player.buildLyricRows
import com.mochyy.mochymusic.ui.player.lineLook
import com.mochyy.mochymusic.ui.player.lyricAnchorIndex
import com.mochyy.mochymusic.ui.player.recenterEasingFor
import com.mochyy.mochymusic.ui.player.styles.smooth
import com.mochyy.mochymusic.ui.player.tickerSlot
import com.mochyy.mochymusic.ui.player.usesAgentAlignment
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.LyricsScreenViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlin.math.abs

// the lyrics page's lines at three quarters of their size, so a line fits the pane the way it fits the phone held upright
private const val VideoLyricScale = 0.75f

// the bottom line's row: its text starts level with the artwork and ends level with the seek bar
private val RegularStartPad = 12.dp
private val RegularEndPad = 18.dp
private val RegularBottom = 8.dp

// with the controls up the line moves over the seek bar and steps back
private val ControlsLift = 54.dp
private const val ControlsDim = 0.55f

// the soft shade the bottom line sits in: rising from its corner, fading up and across, about the mock's 512 x 189 on a 874 x 401 screen
private const val ShadeWidth = 0.62f
private const val ShadeHeight = 0.5f
private const val ShadeAlpha = 0.38f
private const val ShadeMoveMs = 700

// a line changing slides this share of its height, blurring as it goes
private const val LineChangeMs = 520
private val LineChangeBlur = 12.dp

// the pane's list starts under its handle, and rests the sung line this far down it
internal val PaneListTop = 40.dp
private const val PaneRestFraction = 0.2f
private val PaneRestMin = 56.dp

// the pane's other lines rise this far as it opens, the further from the sung line the later
private val PaneRise = 56.dp
private const val PaneRowDelay = 0.07f

private const val IdleRecenterDelayMs = 3_500L

// the song's lyrics as the video mode shows them; loaded turns true once the lookup has an answer, even "none"
@Immutable
internal class VideoLyrics(val lines: List<LyricsEntry>, val unsynced: Boolean, val loaded: Boolean)

// how lines move and glow, from the same settings the lyrics page reads
@Immutable
internal class VideoLyricLook(
    val motion: LyricsMotionStyle,
    val highBloom: Boolean,
    val blurAllowed: Boolean,
    val translating: Boolean,
)

@Composable
internal fun rememberVideoLyrics(): VideoLyrics {
    val connection = LocalPlayerConnection.current ?: return remember { VideoLyrics(emptyList(), false, true) }
    val metadata by connection.mediaMetadata.collectAsState()
    val entity by connection.currentLyrics.collectAsState(initial = null)
    // the flagship's card is what usually asks for a song's lyrics; here nothing else does
    val loader: LyricsScreenViewModel = hiltViewModel()
    LaunchedEffect(metadata?.id) { metadata?.let(loader::ensureLyricsLoaded) }
    val parsed = remember(entity?.lyrics) {
        val raw = entity?.lyrics
        if (raw.isNullOrBlank() || raw == LYRICS_NOT_FOUND) {
            emptyList<LyricsEntry>() to false
        } else {
            val timed = LyricsUtils.parseLyricsCached(raw)
            when {
                timed.isEmpty() -> LyricsUtils.parsePlainLyrics(raw) to true
                LyricsUtils.isUnsynced(timed) -> timed to true
                else -> timed to false
            }
        }
    }
    LyricsReadings(parsed.first, metadata?.id, LocalDatabase.current, entity)
    val loaded = entity != null
    return remember(parsed, loaded) { VideoLyrics(parsed.first, parsed.second, loaded) }
}

@Composable
internal fun rememberVideoLyricLook(): VideoLyricLook {
    val (motion) = rememberEnumPreference(LyricsMotionStyleKey, defaultValue = LyricsMotionStyle.MOTION)
    val (highBloom) = rememberPreference(LyricsHighBloomKey, defaultValue = true)
    val (blurAllowed) = rememberPreference(com.mochyy.mochymusic.constants.ElementBlurKey, defaultValue = true)
    val status by LyricsTranslationHelper.status.collectAsState()
    val translating = status is LyricsTranslationHelper.TranslationStatus.Translating
    return remember(motion, highBloom, blurAllowed, translating) {
        VideoLyricLook(motion, highBloom, blurAllowed, translating)
    }
}

// every lyric in the video mode is drawn at the pane's size, by the lyrics page's own line
@Composable
internal fun ProvideVideoLyricScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scaled = remember(density) { Density(density.density, density.fontScale * VideoLyricScale) }
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}

// one lyric line as the video mode draws it: its translation above, its readings below, the same sweep and glow as the lyrics page
@Composable
internal fun VideoLyricRow(
    entry: LyricsEntry,
    state: LyricsLineState,
    blurStage: Int,
    positionProvider: () -> Long,
    alignment: LyricsAlignment,
    look: VideoLyricLook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    suppressEffects: Boolean = false,
) {
    val romanized by entry.romanizedTextFlow.collectAsState()
    val ruby by entry.rubyFlow.collectAsState()
    val translated by entry.translatedTextFlow.collectAsState()
    LyricsGlowLine(
        entry = entry,
        state = state,
        blurStage = blurStage,
        positionProvider = positionProvider,
        accentColor = Color.White,
        onClick = onClick,
        modifier = modifier,
        subLine = romanized,
        ruby = ruby,
        rubyPlacement = RubyPlacement.BELOW,
        translation = translated,
        translationLoading = look.translating && translated.isNullOrBlank(),
        translationAbove = true,
        alignment = alignment,
        waveEnabled = look.motion.shaded && !suppressEffects,
        motionStyle = look.motion,
        highBloom = look.highBloom,
        suppressEffects = suppressEffects,
    )
}

// where the pane rests its sung line, from the top of its list
internal fun paneRestingOffset(frame: VideoFrame): Dp =
    ((frame.height - PaneListTop) * PaneRestFraction).coerceAtLeast(PaneRestMin)

// the line being sung, across the bottom of the video on its singer's side; it flies up into the pane as the pane opens
@Composable
internal fun VideoRegularLyrics(
    lyrics: VideoLyrics,
    look: VideoLyricLook,
    frame: VideoFrame,
    state: VideoModeState,
    controls: () -> Float,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current ?: return
    val lines = lyrics.lines
    val usable = lines.isNotEmpty() && !lyrics.unsynced
    // only the slot (which line, or a gap) comes from the position, so this recomposes once a line
    val slot by remember(lines, connection) {
        combine(connection.progressState, connection.lyricsOffsetMs) { progress, offsetMs ->
            tickerSlot(lines, progress.position + offsetMs)
        }.distinctUntilChanged()
    }.collectAsState(initial = tickerSlot(lines, positionProvider()))
    val anchor by remember(lines, connection) {
        combine(connection.progressState, connection.lyricsOffsetMs) { progress, offsetMs ->
            lyricAnchorIndex(lines, progress.position + offsetMs)
        }.distinctUntilChanged()
    }.collectAsState(initial = -1)
    val useAgents = remember(lines) { usesAgentAlignment(lines) }

    // the line flies only into the row the lyrics pane rests on; anything else (a gap, the queue) just fades across
    val flying = if (
        usable && state.paneContent == VideoPane.LYRICS && slot.kind == TickerSlot.LINE && slot.index == anchor
    ) slot.index else -1
    SideEffect { state.flyingLine = flying }
    if (!usable) return

    val alignment = when (slot.kind) {
        TickerSlot.LINE -> lines.getOrNull(slot.index)?.let { alignmentFor(it, useAgents) } ?: LyricsAlignment.START
        else -> lines.getOrNull(slot.index + 1)?.let { alignmentFor(it, useAgents) } ?: LyricsAlignment.START
    }
    val currentAlignment by rememberUpdatedState(alignment)
    val density = LocalDensity.current
    val geometry = remember(frame, density) {
        with(density) {
            RegularGeometry(
                frameWidth = frame.width.toPx(),
                frameHeight = frame.height.toPx(),
                paneWidth = frame.paneWidth.toPx(),
                startPad = RegularStartPad.toPx(),
                endPad = RegularEndPad.toPx(),
                bottom = RegularBottom.toPx(),
                lift = ControlsLift.toPx(),
                paneTop = (PaneListTop + paneRestingOffset(frame)).toPx(),
            )
        }
    }

    // the shade follows the line to its side, and goes as the pane takes the line or there's no line to read
    val shadeSide by animateFloatAsState(
        targetValue = when (alignment) {
            LyricsAlignment.START -> 0f
            LyricsAlignment.CENTER -> 0.5f
            LyricsAlignment.END -> 1f
        },
        animationSpec = tween(ShadeMoveMs, easing = FastOutSlowInEasing),
        label = "videoLyricShadeSide",
    )
    val shadeOn by animateFloatAsState(
        targetValue = if (slot.kind == TickerSlot.LINE || slot.kind == TickerSlot.GAP) 1f else 0f,
        animationSpec = tween(ShadeMoveMs, easing = FastOutSlowInEasing),
        label = "videoLyricShade",
    )

    ProvideVideoLyricScale {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val strength = shadeOn * (1f - smooth(state.pane.value * 1.6f))
                        if (strength < 0.01f) return@drawBehind
                        // one round gradient squashed flat: darkest in the corner, fading up and across in a single draw
                        val radius = size.width * ShadeWidth
                        val center = Offset(size.width * shadeSide, size.height)
                        scale(scaleX = 1f, scaleY = size.height * ShadeHeight / radius, pivot = center) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0f to Color.Black.copy(alpha = ShadeAlpha * strength),
                                    0.45f to Color.Black.copy(alpha = ShadeAlpha * 0.55f * strength),
                                    1f to Color.Transparent,
                                    center = center,
                                    radius = radius,
                                ),
                                radius = radius,
                                center = center,
                            )
                        }
                    },
            )
            AnimatedContent(
                targetState = slot,
                transitionSpec = { lineChange() },
                contentAlignment = Alignment.BottomStart,
                label = "videoLyricLine",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(bottom = RegularBottom)
                    .graphicsLayer {
                        val p = state.pane.value
                        val c = controls()
                        val resting = 1f - (1f - ControlsDim) * c
                        if (state.flyingLine >= 0) {
                            // the same row as the pane's, so moving it is all it takes to become the pane's line
                            val restTop = geometry.frameHeight - geometry.bottom - state.regularRowHeightPx
                            translationX = (geometry.paneLeft - geometry.rowLeft(currentAlignment)) * p
                            translationY = -geometry.lift * c * (1f - p) + (geometry.paneTop - restTop) * p
                            alpha = if (p >= 0.999f) 0f else resting + (1f - resting) * p
                        } else {
                            translationY = -geometry.lift * c
                            alpha = resting * (1f - smooth(p * 1.6f))
                        }
                    },
            ) { current ->
                val leaving = transition.targetState != EnterExitState.Visible
                val blur by transition.animateFloat(
                    transitionSpec = { tween(LineChangeMs, easing = FastOutSlowInEasing) },
                    label = "videoLyricBlur",
                ) { if (it == EnterExitState.Visible) 0f else 1f }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { renderEffect = blurEffectForRadius(blur * LineChangeBlur.toPx()) },
                ) {
                    RegularSlot(
                        current = current,
                        lines = lines,
                        useAgents = useAgents,
                        leaving = leaving,
                        look = look,
                        frame = frame,
                        state = state,
                        positionProvider = positionProvider,
                        // a tap on the line opens the whole lyrics, the line flying up into them
                        onTap = { state.openPane(VideoPane.LYRICS) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.RegularSlot(
    current: TickerSlot,
    lines: List<LyricsEntry>,
    useAgents: Boolean,
    leaving: Boolean,
    look: VideoLyricLook,
    frame: VideoFrame,
    state: VideoModeState,
    positionProvider: () -> Long,
    onTap: () -> Unit,
) {
    when (current.kind) {
        TickerSlot.LINE -> {
            val entry = lines.getOrNull(current.index) ?: return
            val alignment = alignmentFor(entry, useAgents)
            VideoLyricRow(
                entry = entry,
                // the line leaving dims on its way out, as a sung line does on the lyrics page
                state = if (leaving) LyricsLineState.PAST else LyricsLineState.ACTIVE,
                blurStage = 0,
                positionProvider = positionProvider,
                alignment = alignment,
                look = look,
                onClick = { if (!leaving) onTap() },
                modifier = Modifier
                    .regularPlace(alignment, frame)
                    .onSizeChanged { if (!leaving) state.regularRowHeightPx = it.height },
            )
        }

        TickerSlot.GAP -> {
            val startMs = if (current.index < 0) 0L else LyricsUtils.lineEndMs(lines, current.index)
            val endMs = lines.getOrNull(current.index + 1)?.time ?: startMs
            val alignment = lines.getOrNull(current.index + 1)?.let { alignmentFor(it, useAgents) } ?: LyricsAlignment.START
            LyricsInterludeDots(
                startMs = startMs,
                endMs = endMs,
                positionProvider = positionProvider,
                accentColor = Color.White,
                alignment = alignment,
                modifier = Modifier.regularPlace(alignment, frame),
            )
        }

        else -> Unit
    }
}

// the row is as wide as the pane, on its singer's side of the video
private fun Modifier.regularPlace(alignment: LyricsAlignment, frame: VideoFrame): Modifier = when (alignment) {
    LyricsAlignment.START -> this.padding(start = RegularStartPad).width(frame.paneWidth)
    LyricsAlignment.END -> this.padding(start = frame.width - RegularEndPad - frame.paneWidth).width(frame.paneWidth)
    LyricsAlignment.CENTER -> this.padding(start = (frame.width - frame.paneWidth) / 2).width(frame.paneWidth)
}

// the bottom line's places in px, worked out once per size
private class RegularGeometry(
    val frameWidth: Float,
    val frameHeight: Float,
    val paneWidth: Float,
    val startPad: Float,
    val endPad: Float,
    val bottom: Float,
    val lift: Float,
    val paneTop: Float,
) {
    val paneLeft = frameWidth - paneWidth

    fun rowLeft(alignment: LyricsAlignment): Float = when (alignment) {
        LyricsAlignment.START -> startPad
        LyricsAlignment.END -> frameWidth - endPad - paneWidth
        LyricsAlignment.CENTER -> (frameWidth - paneWidth) / 2f
    }
}

// the old line slides up and away as it blurs and fades, the new one rises in from below and comes into focus
private fun AnimatedContentTransitionScope<TickerSlot>.lineChange(): ContentTransform {
    val slide = spring(
        dampingRatio = 0.86f,
        stiffness = Spring.StiffnessLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )
    val fade = tween<Float>(LineChangeMs, easing = FastOutSlowInEasing)
    return (slideInVertically(slide) { it / 2 } + fadeIn(fade))
        .togetherWith(slideOutVertically(slide) { -it / 2 } + fadeOut(fade))
        .using(SizeTransform(clip = false))
}

// the whole song in the pane, following the music like the lyrics page; the sung line waits hidden while the bottom line flies into its place
@Composable
internal fun VideoPaneLyrics(
    lyrics: VideoLyrics,
    look: VideoLyricLook,
    frame: VideoFrame,
    state: VideoModeState,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    FollowingLyrics(
        lyrics = lyrics,
        look = look,
        restingOffset = paneRestingOffset(frame),
        bottomPadding = (frame.height - PaneListTop) * 0.6f,
        positionProvider = positionProvider,
        modifier = modifier.fillMaxSize().padding(top = PaneListTop),
        opening = { state.pane.value },
        flyingLine = { state.flyingLine },
    )
}

// the song's lines in a list that follows the music, for the video pane and the bedside; opening and flyingLine are the pane's rise and the line flying into it
@Composable
internal fun FollowingLyrics(
    lyrics: VideoLyrics,
    look: VideoLyricLook,
    restingOffset: Dp,
    bottomPadding: Dp,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
    opening: () -> Float = { 1f },
    flyingLine: () -> Int = { -1 },
    userScroll: Boolean = true,
    // laid over the finished lines while drawing, so a colour fade never recomposes them
    tint: (() -> Color)? = null,
) {
    val connection = LocalPlayerConnection.current ?: return
    val lines = lyrics.lines
    val unsynced = lyrics.unsynced
    val density = LocalDensity.current

    Box(modifier = modifier) {
        if (lines.isEmpty()) {
            if (!lyrics.loaded) {
                LyricsLoadingDots(modifier = Modifier.align(Alignment.Center))
            } else {
                Text(
                    text = stringResource(R.string.video_mode_no_lyrics),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            return@Box
        }

        val progress by connection.progressState.collectAsState()
        val offsetMs by connection.lyricsOffsetMs.collectAsState()
        val currentIndex by remember(lines, unsynced) {
            derivedStateOf { if (unsynced) -1 else LyricsUtils.findCurrentLineIndex(lines, progress.position + offsetMs) }
        }
        val anchorIndex by remember(lines, unsynced) {
            derivedStateOf { if (unsynced) -1 else lyricAnchorIndex(lines, progress.position + offsetMs) }
        }
        val rows = remember(lines) { buildLyricRows(lines) }
        val rowOfLine = remember(rows, lines) {
            IntArray(lines.size).also { map ->
                rows.forEachIndexed { rowIndex, row -> if (row is LyricRow.Line) map[row.index] = rowIndex }
            }
        }
        val activeInterlude by remember(lines, rows) {
            derivedStateOf { activeInterludeOf(rows, progress.position + offsetMs) }
        }
        val useAgents = remember(lines) { usesAgentAlignment(lines) }
        val anchorRow by remember(rowOfLine) { derivedStateOf { rowOfLine.getOrNull(anchorIndex) ?: -1 } }

        // opens already on the sung line, which has to be in place for the line flying in
        val initialRow = Snapshot.withoutReadObservation {
            rowOfLine.getOrNull(anchorIndex.coerceIn(0, lines.lastIndex)) ?: 0
        }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)
        val stagger = remember(rows) { LyricsStagger(rows.size + 1) }
        LaunchedEffect(stagger) { stagger.run() }

        var followPlayback by remember { mutableStateOf(true) }
        var isAutoScrolling by remember { mutableStateOf(false) }
        val userScrolling by remember { derivedStateOf { listState.isScrollInProgress && !isAutoScrolling } }
        LaunchedEffect(userScrolling) { if (userScrolling) followPlayback = false }
        LaunchedEffect(userScrolling, followPlayback) {
            if (!followPlayback && !userScrolling) {
                delay(IdleRecenterDelayMs)
                followPlayback = true
            }
        }

        val restingPx = with(density) { restingOffset.roundToPx() }
        val cascade = look.motion.risesWords
        LaunchedEffect(followPlayback, lines, look.motion) {
            if (!followPlayback) return@LaunchedEffect
            snapshotFlow { anchorIndex }.collectLatest { target ->
                if (target !in lines.indices) return@collectLatest
                val targetRow = rowOfLine[target]
                snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
                try {
                    isAutoScrolling = true
                    // still opening or closing: the line goes straight to its place, the flight needs it there
                    if (opening() < 0.999f) {
                        listState.scrollToItem(targetRow, 0)
                        return@collectLatest
                    }
                    val layoutInfo = listState.layoutInfo
                    val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetRow }
                    if (visible == null) {
                        listState.animateScrollToItem(targetRow, 0)
                        return@collectLatest
                    }
                    val current = visible.offset - layoutInfo.viewportStartOffset
                    // a gap row above it collapses during this same scroll and lifts it by its own height
                    val collapsing = layoutInfo.visibleItemsInfo
                        .filter { it.index < targetRow && rows.getOrNull(it.index) is LyricRow.Interlude }
                        .sumOf { it.size }
                    val distance = (current - restingPx - collapsing).toFloat()
                    stagger.anchorRow = targetRow
                    listState.scroll {
                        var previous = 0f
                        animate(
                            initialValue = 0f,
                            targetValue = distance,
                            animationSpec = tween(LyricsRecenterDurationMs, easing = recenterEasingFor(look.motion)),
                        ) { value, _ ->
                            val consumed = scrollBy(value - previous)
                            previous += consumed
                            if (cascade && consumed != 0f) {
                                for (item in listState.layoutInfo.visibleItemsInfo) {
                                    if (item.index > targetRow) stagger.holdBack(item.index, consumed)
                                }
                            }
                        }
                    }
                } finally {
                    isAutoScrolling = false
                }
            }
        }

        val risePx = with(density) { PaneRise.toPx() }
        // the pane opens: each row rises into place, the further from the sung line the later
        fun androidx.compose.ui.graphics.GraphicsLayerScope.enter(rowIndex: Int) {
            val p = opening()
            if (p >= 0.999f) return
            val distance = abs(rowIndex - anchorRow).coerceAtMost(6)
            val delay = distance * PaneRowDelay
            val t = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
            alpha *= smooth(t)
            translationY += (1f - t) * risePx
        }

        ProvideVideoLyricScale {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = restingOffset, bottom = bottomPadding),
                userScrollEnabled = userScroll,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val fade = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.12f to Color.Black,
                            0.78f to Color.Black,
                            1f to Color.Transparent,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush = fade, blendMode = BlendMode.DstIn)
                            tint?.let { drawRect(it(), blendMode = BlendMode.Modulate) }
                        }
                    },
            ) {
                itemsIndexed(
                    items = rows,
                    key = { _, row -> row.key },
                    contentType = { _, row -> if (row is LyricRow.Interlude) "interlude" else "lyric" },
                ) { listIndex, row ->
                    when (row) {
                        is LyricRow.Interlude -> LyricsInterludeDots(
                            startMs = row.startMs,
                            endMs = row.endMs,
                            positionProvider = positionProvider,
                            accentColor = Color.White,
                            visible = activeInterlude == row.afterIndex,
                            collapseEasing = recenterEasingFor(look.motion),
                            alignment = lines.getOrNull(row.afterIndex + 1)?.let { alignmentFor(it, useAgents) }
                                ?: LyricsAlignment.CENTER,
                            modifier = Modifier.graphicsLayer {
                                translationY = stagger.lagOf(listIndex)
                                enter(listIndex)
                            },
                        )

                        is LyricRow.Line -> {
                            val index = row.index
                            // worked out per row, so a line change only recomposes the few rows whose look changes
                            val lineState by remember(index, lines, unsynced) {
                                derivedStateOf {
                                    lineLook(index, currentIndex, anchorIndex, unsynced, activeInterlude != NoInterlude)
                                }
                            }
                            val rowIndex = rowOfLine[index]
                            VideoLyricRow(
                                entry = row.entry,
                                state = lineState.state,
                                blurStage = if (userScrolling || !look.blurAllowed) 0 else lineState.blurStage,
                                positionProvider = positionProvider,
                                alignment = alignmentFor(row.entry, useAgents),
                                look = look,
                                suppressEffects = userScrolling,
                                onClick = {
                                    if (!unsynced) {
                                        connection.player.seekTo(row.entry.time)
                                        followPlayback = true
                                    }
                                },
                                modifier = Modifier.graphicsLayer {
                                    translationY = stagger.lagOf(rowIndex)
                                    // the bottom line is flying into this very row; it shows once that has landed
                                    if (index == flyingLine() && opening() < 0.999f) {
                                        alpha = 0f
                                    } else {
                                        enter(rowIndex)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
