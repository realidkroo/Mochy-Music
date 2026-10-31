// PlayerProgressSlider.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.PlayerHorizontalPadding
import com.mochyy.mochymusic.ui.component.SeekBarExpandSpring
import com.mochyy.mochymusic.utils.makeTimeString
import com.mochyy.mochymusic.ui.utils.rubberBand
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

@Composable
fun PlayerProgressSlider(
    modifier: Modifier = Modifier,
    /**
     * Whether the bar is on screen. The player stays composed while it's collapsed, and this used
     * to follow the playback position (15 times a second) all the same - recomposing the slider
     * and asking for a new frame each time, behind whatever screen was open. While inactive it
     * holds the last position and does nothing.
     */
    active: Boolean = true,
    /** Where the bar and the right-hand time sit, for the editor's copies of them. */
    onSeekBarPlaced: ((androidx.compose.ui.layout.LayoutCoordinates) -> Unit)? = null,
    onEndTimePlaced: ((androidx.compose.ui.layout.LayoutCoordinates) -> Unit)? = null,
    /** On the bar and the end time themselves: the editor lifts them off to edit them. */
    seekBarModifier: Modifier = Modifier,
    endTimeModifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val endTimestamp by com.mochyy.mochymusic.utils.rememberEnumPreference(
        com.mochyy.mochymusic.constants.PlayerEndTimestampKey,
        com.mochyy.mochymusic.constants.PlayerEndTimestamp.END_TIME,
    )
    val progressFlow = playerConnection.uiState.progressState
    // only the bar and the time texts read the position, so a tick redraws them and not the whole slider
    val progress = remember { mutableStateOf(progressFlow.value) }
    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    // a seek still on its way to the player: shown until it lands, so the bar doesn't flick back to where it was
    var pendingSeek by remember { mutableStateOf<Long?>(null) }
    val durationKnown by remember {
        derivedStateOf { progress.value.duration.let { it != C.TIME_UNSET && it > 0L } }
    }
    val rangeEnd by remember { derivedStateOf { if (durationKnown) progress.value.duration.toFloat() else 0f } }
    val sliderState = remember(rangeEnd) {
        val start = Snapshot.withoutReadObservation { sliderPosition ?: pendingSeek ?: progress.value.position }
        SliderState(valueRange = 0f..rangeEnd).also { it.value = start.toFloat() }
    }
    LaunchedEffect(active, sliderState) {
        if (!active) return@LaunchedEffect
        progressFlow.collect { latest ->
            progress.value = latest
            pendingSeek?.let { if (abs(latest.position - it) < SeekLandedMs) pendingSeek = null }
            if (sliderPosition == null) sliderState.value = (pendingSeek ?: latest.position).toFloat()
        }
    }
    // a seek the player never confirms (a failed stream, a song change) stops holding the bar after a moment
    LaunchedEffect(pendingSeek) {
        if (pendingSeek == null) return@LaunchedEffect
        delay(SeekGiveUpMs)
        pendingSeek = null
    }
    sliderState.onValueChange = { raw ->
        val position = raw.toLong()
        sliderPosition = position
        sliderState.value = position.toFloat()
    }
    sliderState.onValueChangeFinished = {
        sliderPosition?.let { target ->
            playerConnection.player.seekTo(target)
            pendingSeek = target
        }
        sliderPosition = null
        sliderState.value = (pendingSeek ?: progress.value.position).toFloat()
    }
    val transport = playerConnection.uiState.transportState.collectAsState()
    val transportState = transport.value
    val seekBarStyle by com.mochyy.mochymusic.utils.rememberEnumPreference(
        com.mochyy.mochymusic.constants.SeekBarStyleKey,
        com.mochyy.mochymusic.ui.component.SeekBarStyle.DEFAULT,
    )

    // The Material 3 bars follow the artwork palette; the plain and default bars stay white so
    // they keep working over any cover.
    val artworkColor = com.mochyy.mochymusic.LocalArtworkColor.current
    val isM3Bar = seekBarStyle == com.mochyy.mochymusic.ui.component.SeekBarStyle.M3_EXPRESSIVE ||
        seekBarStyle == com.mochyy.mochymusic.ui.component.SeekBarStyle.M3_EXPRESSIVE_LINE
    val barActiveColor = if (isM3Bar) {
        com.mochyy.mochymusic.ui.theme.ArtworkPalette.from(artworkColor).accent
    } else {
        Color.White
    }

    val failureProgress = rememberStreamFailureProgress()
    val failureLabel by rememberStreamFailureLabel(failureProgress)

    val buffering = transportState.playbackState == Player.STATE_BUFFERING
    // Loading the track itself (not a rebuffer mid-song): no length yet, or still at the start.
    // That's when the time read a meaningless 00:00 with nothing on the right.
    val loadingTrack by remember(buffering) {
        derivedStateOf { buffering && (!durationKnown || progress.value.position < LoadingStartWindowMs) }
    }
    // Held back a moment, so a short rebuffer after a seek doesn't flash the bar.
    val showBuffering by rememberSettled(buffering)
    val showLoading by rememberSettled(loadingTrack)
    // the time strings change once a second, so only their texts recompose on a tick
    val elapsedText = remember {
        derivedStateOf { makeTimeString(sliderPosition ?: pendingSeek ?: progress.value.position) }
    }
    val endText = remember(showLoading, endTimestamp) {
        derivedStateOf {
            when {
                !durationKnown || showLoading -> ""
                endTimestamp == com.mochyy.mochymusic.constants.PlayerEndTimestamp.TIME_LEFT ->
                    "-" + makeTimeString((progress.value.duration - (sliderPosition ?: pendingSeek ?: progress.value.position)).coerceAtLeast(0L))
                else -> makeTimeString(progress.value.duration)
            }
        }
    }

    // the fill glides to wherever it's sent (a tap, a seek, a new song) and moves with the music every frame
    val clock = remember(playerConnection) { PlaybackClock({ playerConnection.player }) }
    val fill = remember { SeekFill() }
    LaunchedEffect(active, fill, clock) {
        if (!active) return@LaunchedEffect
        fill.follow(
            target = {
                val duration = progress.value.duration
                if (duration <= 0L) 0f else ((sliderPosition ?: pendingSeek ?: clock()).toFloat() / duration).coerceIn(0f, 1f)
            },
            moving = { transport.value.isPlaying || sliderPosition != null },
        )
    }

    val trackInteractionSource = remember { MutableInteractionSource() }
    val draggedState = trackInteractionSource.collectIsDraggedAsState()
    val isTrackDragged by draggedState
    val isTrackPressed by trackInteractionSource.collectIsPressedAsState()
    // how far a drag past either end has stretched the bar out after the finger, the way the volume line does; read while drawing only
    val stretch = remember { mutableFloatStateOf(0f) }
    val stretchScope = rememberCoroutineScope()
    val stretchSettle = remember { arrayOfNulls<Job>(1) }
    val held = isTrackDragged || isTrackPressed
    // the bar opening under the finger, read only while drawing: the bar, its thumb and the times all ride it
    val expand = animateFloatAsState(
        targetValue = if (held) 1f else 0f,
        animationSpec = SeekBarExpandSpring,
        label = "playerSeekExpand",
    )
    val timeColor = remember(expand) { ColorProducer { Color.White.copy(alpha = TimeRestAlpha + (1f - TimeRestAlpha) * expand.value) } }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        Slider(
            state = sliderState,
            interactionSource = trackInteractionSource,
            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
            track = {
                com.mochyy.mochymusic.ui.component.SeekBarTrack(
                    style = seekBarStyle,
                    fractionProvider = { fill.fraction },
                    activeColor = barActiveColor,
                    inactiveColor = Color.White.copy(alpha = 0.24f),
                    animateWave = transportState.isPlaying,
                    active = held,
                    loading = showBuffering,
                    clockRunning = active,
                    expandProvider = { expand.value },
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = PlayerHorizontalPadding)
                .onSizeChanged { fill.widthPx = it.width.toFloat() }
                .then(onSeekBarPlaced?.let { Modifier.onGloballyPositioned(it) } ?: Modifier)
                .then(seekBarModifier)
                .streamFailureOverlay(failureProgress)
                .seekStretch(stretch, draggedState, stretchSettle, stretchScope)
        )
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding + 4.dp)
    ) {
        val timeStyle = MaterialTheme.typography.labelMedium
        val restColor = Color.White.copy(alpha = TimeRestAlpha)
        AnimatedContent(
            targetState = if (failureLabel != null) FailureKey else if (showLoading) LoadingKey else null,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "seekTimeLoading",
            modifier = Modifier.timeLift(expand, pivotX = 0f) { minOf(stretch.floatValue, 0f) },
        ) { state ->
            if (state == LoadingKey) {
                LoadingLabel(style = timeStyle, color = restColor)
            } else if (state == FailureKey) {
                Text(
                    text = failureLabel ?: "Error",
                    style = timeStyle,
                    color = StreamFailureRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                TimeText(text = elapsedText, style = timeStyle, color = timeColor)
            }
        }
        TimeText(
            text = endText,
            style = timeStyle,
            color = timeColor,
            modifier = (onEndTimePlaced?.let { Modifier.onGloballyPositioned(it) } ?: Modifier)
                .then(endTimeModifier)
                .timeLift(expand, pivotX = 1f) { maxOf(stretch.floatValue, 0f) },
        )
    }
}

/** A time label that reads its string itself, so only this text recomposes when the second changes. */
@Composable
private fun TimeText(text: State<String>, style: TextStyle, color: ColorProducer, modifier: Modifier = Modifier) {
    BasicText(
        text = text.value,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

// the times drop and grow a touch as the bar opens, pinned to their outer edge so they never crowd the middle, and ride along when the bar is stretched their way
private fun Modifier.timeLift(expand: State<Float>, pivotX: Float, stretched: () -> Float): Modifier = graphicsLayer {
    val open = expand.value
    translationX = stretched()
    translationY = TimeDrop.toPx() * open
    val s = 1f + TimeGrow * open
    scaleX = s
    scaleY = s
    transformOrigin = TransformOrigin(pivotX, 0.5f)
}

// a drag past either end pulls the bar this far at most, then it springs back with a bounce on letting go
private val SeekStretchMax = 26.dp
private val SeekStretchBack = spring<Float>(dampingRatio = 0.45f, stiffness = 420f)

// the finger past an end stretches the bar out after it, a little thinner like a band pulled tight
private fun Modifier.seekStretch(
    stretch: MutableFloatState,
    dragged: State<Boolean>,
    settle: Array<Job?>,
    scope: CoroutineScope,
): Modifier = this
    .pointerInput(Unit) {
        val reach = SeekStretchMax.toPx()
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            settle[0]?.cancel()
            // only watching: the slider still takes the drag, this just sees where the finger has gone
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: break
                if (!change.pressed) break
                val x = change.position.x
                val over = when {
                    !dragged.value -> 0f
                    x > size.width -> x - size.width
                    x < 0f -> x
                    else -> 0f
                }
                stretch.floatValue = rubberBand(over, reach)
            }
            val from = stretch.floatValue
            if (from != 0f) {
                settle[0] = scope.launch {
                    animate(from, 0f, animationSpec = SeekStretchBack) { value, _ -> stretch.floatValue = value }
                }
            }
        }
    }
    .graphicsLayer {
        val s = stretch.floatValue
        if (s == 0f || size.width <= 0f) return@graphicsLayer
        val pull = (abs(s) / SeekStretchMax.toPx()).coerceIn(0f, 1f)
        scaleX = (size.width + abs(s)) / size.width
        scaleY = 1f - 0.18f * pull
        // grows out from the far end, toward the finger
        transformOrigin = TransformOrigin(if (s > 0f) 0f else 1f, 0.5f)
    }

private val TimeDrop = 4.dp
private const val TimeGrow = 0.1f
private const val TimeRestAlpha = 0.85f

// the player has caught up once it reports within this of where the seek went
private const val SeekLandedMs = 1_500L

// how long a seek holds the bar if the player never says it landed
private const val SeekGiveUpMs = 1_500L

// critically damped follow speed for the fill: quick enough to stay under the finger, soft enough to glide a jump
private const val FillOmega = 50f

// the fill as drawn: chases where it's headed and only asks for a redraw once it has moved a quarter of a pixel
@Stable
private class SeekFill {
    var fraction by mutableFloatStateOf(0f)
        private set

    // the bar's width, so tiny moves can be skipped
    var widthPx = 0f

    private var shown = 0f
    private var velocity = 0f

    suspend fun follow(target: () -> Float, moving: () -> Boolean) {
        shown = target()
        velocity = 0f
        fraction = shown
        while (true) {
            // asleep while paused and in place; a seek, a scrub, a new song or play wakes it
            snapshotFlow { moving() || abs(target() - shown) > SettledFraction }.first { it }
            var lastNanos = 0L
            while (true) {
                val settled = withFrameNanos { now ->
                    val dt = if (lastNanos == 0L) 1f / 120f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    lastNanos = now
                    val to = target()
                    step(to, dt)
                    !moving() && abs(to - shown) < SettledFraction && abs(velocity) < SettledSpeed
                }
                if (settled) {
                    shown = target()
                    velocity = 0f
                    fraction = shown
                    break
                }
            }
        }
    }

    private fun step(to: Float, dt: Float) {
        val y = shown - to
        val e = exp(-FillOmega * dt)
        val tmp = (velocity + FillOmega * y) * dt
        shown = to + (y + tmp) * e
        velocity = (velocity - FillOmega * tmp) * e
        val minStep = if (widthPx > 0f) 0.25f / widthPx else 0f
        if (abs(shown - fraction) >= minStep) fraction = shown
    }
}

private const val SettledFraction = 0.0002f
private const val SettledSpeed = 0.002f

/** Stand for the loading and failure labels in the time's switch, so the failure's ticking text doesn't re-fade each second. */
private const val LoadingKey = "\u0000loading"
private const val FailureKey = "\u0000failure"

/** Buffering this close to the start of a track counts as the track loading. */
private const val LoadingStartWindowMs = 1_500L

/** How long a loading state has to last before it shows. */
private const val LoadingShowDelayMs = 200L

/** [value], except a true only shows once it has held for [LoadingShowDelayMs]. False is instant. */
@Composable
private fun rememberSettled(value: Boolean): State<Boolean> = produceState(initialValue = false, value) {
    if (value) {
        delay(LoadingShowDelayMs)
        this.value = true
    } else {
        this.value = false
    }
}

/** "Loading" with its three dots lighting one after another. */
@Composable
private fun LoadingLabel(style: TextStyle, color: Color) {
    val transition = rememberInfiniteTransition(label = "loadingLabel")
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = "Loading", style = style, color = color, maxLines = 1)
        repeat(3) { index ->
            val lit by transition.animateFloat(
                initialValue = 0.15f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 170),
                ),
                label = "loadingDot$index",
            )
            Text(
                text = ".",
                style = style,
                color = color,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = lit },
            )
        }
    }
}
