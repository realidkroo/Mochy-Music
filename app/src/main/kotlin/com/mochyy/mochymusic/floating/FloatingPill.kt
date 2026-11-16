// FloatingPill.kt

package com.mochyy.mochymusic.floating

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mochyy.mochymusic.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CoverPlaceholder = Color(0xFF55585C)

private val BubbleCorner = 18.dp
private val TailWidth = 8.dp
private val TailHeight = 13.dp

// where the bubble's tail points back at the cover, measured down a two-row bubble so it stays put when the bubble grows
private const val TailAt = 0.64f

// soft shadows, wide and faint, dropped a little below what casts them
private val BubbleShadowBlur = 14.dp
private val BubbleShadowDrop = 5.dp
private const val BubbleShadowAlpha = 0.2f
private val CoverShadowBlur = 12.dp
private val CoverShadowDrop = 4.dp
private const val CoverShadowAlpha = 0.28f

// while it's carried the cover lifts a little and its shadow drops further and darkens
private const val LiftScale = 1.08f
private val LiftShadowDrop = 5.dp
private const val LiftShadowAlpha = 0.1f

// how far the tucking bubble slides into the cover, and how small it gets
private val TuckSlide = 18.dp
private const val TuckShrink = 0.72f

// the controls tuck away again after this long without a touch
private const val AutoCollapseMs = 4_500L

// the bubble waits this long after the cover starts popping in, so the two land one after the other
private const val AppearStaggerMs = 90L

// the controls glide open on a long ease-out that slows right down before it stops, and close a little quicker the same way
private val OpenEase = tween<Float>(durationMillis = 640, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
private val CloseEase = tween<Float>(durationMillis = 460, easing = CubicBezierEasing(0.33f, 0.9f, 0.3f, 1f))
private val TuckSpring = spring<Float>(dampingRatio = 1f, stiffness = 900f)
private val PopSpring = spring<Float>(dampingRatio = 0.58f, stiffness = 420f)
private val SlotsSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 320f)
private val AppearSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 380f)

// what the window wraps: the cover alone while it's carried, the cover and bubble at rest, and the open controls with room for the bar
internal enum class PillShape { COVER, PILL, OPEN }

// the pill's sizes; the bubble's width comes from the screen so the open controls always fit
@Immutable
internal class PillMetrics(val bubbleWidth: Dp) {
    val pillWidth: Dp get() = Margin * 2 + Cover + Gap + bubbleWidth

    fun width(shape: PillShape): Dp = when (shape) {
        PillShape.COVER -> Margin * 2 + Cover
        PillShape.PILL -> pillWidth
        PillShape.OPEN -> pillWidth + ButtonExtra
    }

    fun height(shape: PillShape, slots: Int): Dp = when (shape) {
        PillShape.COVER -> Margin * 2 + Cover
        PillShape.PILL -> Margin * 2 + bodyHeight(slots.toFloat())
        PillShape.OPEN -> Margin * 2 + bodyHeight(slots.toFloat()) + BarArea
    }

    companion object {
        // room around the pill for its soft shadow
        val Margin = 20.dp
        val Cover = 62.dp
        val Gap = 10.dp
        val BubbleHeight = 58.dp

        // the bubble's rows sit this far apart
        val RowPitch = 21.dp

        // a two-row bubble is centred on the cover; a taller one grows down from there
        val BubbleTop = (Cover - BubbleHeight) / 2

        // how far the bubble moves aside for play and pause once the controls are open
        val ButtonExtra = 48.dp

        // the progress bar and its times under the open controls
        val BarArea = 40.dp
        val BarTop = 14.dp
        val BarHeight = 4.dp

        // how far above and below the bar a finger still grabs it
        val SeekReach = 22.dp

        fun bubbleHeight(slots: Float): Dp = BubbleHeight + RowPitch * (slots - BaseSlots)

        // the cover and the bubble together, measured from the cover's top
        fun bodyHeight(slots: Float): Dp = maxOf(Cover, BubbleTop + bubbleHeight(slots))

        // the middle of the progress bar, down from the window's top
        fun barY(slots: Float): Dp = Margin + bodyHeight(slots) + BarTop + BarHeight / 2

        fun forScreen(screenWidth: Dp): PillMetrics {
            val room = screenWidth - Margin * 2 - Cover - Gap - ButtonExtra - 12.dp
            return PillMetrics(room.coerceIn(150.dp, 226.dp))
        }
    }
}

// what the pill asks of the window and the service around it
internal interface PillHost {
    // makes the window big enough for both what's on screen and what's coming, before anything moves
    fun reserveFrame(shape: PillShape, slots: Int)

    // fits the window to what's on screen once it has settled
    fun fitFrame(shape: PillShape, slots: Int)
    fun openApp()
    fun togglePlay()
    fun seekTo(positionMs: Long)
}

@Composable
internal fun FloatingPill(state: PillState, metrics: PillMetrics, host: PillHost) {
    val expand = remember { Animatable(0f) }
    // starts tucked into the cover, so the bubble pops out of it when the pill appears
    val tuck = remember { Animatable(1f) }
    val appear = remember { Animatable(0f) }
    val slots = state.script?.slots ?: BaseSlots
    val shownSlots = remember { Animatable(slots.toFloat()) }
    val shape = when {
        state.tucked -> PillShape.COVER
        state.expanded -> PillShape.OPEN
        else -> PillShape.PILL
    }
    LaunchedEffect(Unit) { appear.animateTo(1f, AppearSpring) }
    LaunchedEffect(shape, slots) {
        // the window grows first and shrinks last, anchored at the cover, so nothing on screen jumps when it does
        host.reserveFrame(shape, slots)
        if (appear.value < 0.5f) delay(AppearStaggerMs)
        coroutineScope {
            launch { expand.animateTo(if (shape == PillShape.OPEN) 1f else 0f, if (shape == PillShape.OPEN) OpenEase else CloseEase) }
            launch { tuck.animateTo(if (shape == PillShape.COVER) 1f else 0f, if (shape == PillShape.COVER) TuckSpring else PopSpring) }
            launch { shownSlots.animateTo(slots.toFloat(), SlotsSpring) }
        }
        host.fitFrame(shape, slots)
    }
    val scrub = remember { mutableFloatStateOf(NoScrub) }
    val scrubbing by remember { derivedStateOf { scrub.floatValue >= 0f } }
    // a finger on the bar keeps the controls open however long it takes
    LaunchedEffect(state.expanded, state.touchTick, scrubbing) {
        if (!state.expanded || scrubbing) return@LaunchedEffect
        delay(AutoCollapseMs)
        state.expanded = false
    }

    val progress = remember { mutableFloatStateOf(0f) }
    val elapsed = remember { mutableLongStateOf(0L) }
    val timesShown by remember { derivedStateOf { expand.value > 0.01f } }
    SongProgressTicker(state, progress, elapsed, timesShown)

    var pressed by remember { mutableStateOf(false) }
    val press = animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "coverPress")
    val lift = animateFloatAsState(if (state.lifted) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "coverLift")

    // the bubble takes the cover's accent, fading over from one song's to the next
    val colors = remember(state.accent) { PillColors.from(state.accent) }
    val bubbleColor = animateColorAsState(colors.bubble, tween(700), label = "pillBubble")
    val inkColor = animateColorAsState(colors.ink, tween(700), label = "pillInk")
    val tint = remember { PillTint(bubbleColor, inkColor) }

    // the pill is a fixed size, so the text inside it keeps to its own size too
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f), LocalPillTint provides tint) {
        val end = state.dockEnd
        val opened = { expand.value.coerceIn(0f, 1f) }
        val tucked = { tuck.value }
        val body = { shownSlots.value }
        val coverScale = { press.value * (1f + (LiftScale - 1f) * lift.value) * (0.4f + 0.6f * appear.value) }
        val coverAlpha = { (appear.value * 2f).coerceIn(0f, 1f) }
        Box(Modifier.fillMaxSize()) {
            PlayButton(state, host, end, opened)
            CoverDisc(state, host, end, coverScale, coverAlpha, { lift.value }) { pressed = it }
            ProgressRing(progress, scrub, end, opened, body, coverScale, coverAlpha)
            if (timesShown) SongTimes(state, elapsed, scrub, opened, body)
            Bubble(state, metrics, end, opened, tucked, body)
            SeekBarTouch(state, host, scrub, progress, body)
        }
    }
}

// places this from the cover's side of the window: from the left when docked left, mirrored from the right when docked right
internal fun Modifier.fromCoverSide(end: Boolean, x: Density.() -> Float, y: Density.() -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        val left = x()
        val placed = if (end) constraints.maxWidth - left - placeable.width else left
        placeable.place(placed.roundToInt(), y().roundToInt())
    }
}

// the song's cover in a circle; a tap opens the controls, a hold opens mochy
@Composable
private fun CoverDisc(
    state: PillState,
    host: PillHost,
    end: Boolean,
    coverScale: () -> Float,
    coverAlpha: () -> Float,
    lift: () -> Float,
    onPressed: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val coverPx = with(LocalDensity.current) { PillMetrics.Cover.roundToPx() }
    val url = state.song?.thumbnailUrl
    val request = remember(url, coverPx) { ImageRequest.Builder(context).data(url).size(coverPx).build() }
    Box(
        Modifier
            .fromCoverSide(end, { PillMetrics.Margin.toPx() }, { PillMetrics.Margin.toPx() })
            .size(PillMetrics.Cover)
            .graphicsLayer {
                val scale = coverScale()
                scaleX = scale
                scaleY = scale
                alpha = coverAlpha()
            }
            .softShadow(
                corner = PillMetrics.Cover / 2,
                blur = CoverShadowBlur,
                offsetY = { CoverShadowDrop + LiftShadowDrop * lift() },
                alpha = { CoverShadowAlpha + LiftShadowAlpha * lift() },
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressed(true)
                        tryAwaitRelease()
                        onPressed(false)
                    },
                    onTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        state.touchTick++
                        state.expanded = !state.expanded
                    },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        host.openApp()
                    },
                )
            },
    ) {
        AsyncImage(
            model = request,
            contentDescription = stringResource(R.string.floating_lyrics_open),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(CoverPlaceholder),
        )
    }
}

// the speech bubble with the lyrics: pushed aside as the controls open, tucked into the cover while it's carried
@Composable
private fun Bubble(state: PillState, metrics: PillMetrics, end: Boolean, opened: () -> Float, tucked: () -> Float, body: () -> Float) {
    val toCover = if (end) 1f else -1f
    val tint = LocalPillTint.current
    Box(
        Modifier
            .fromCoverSide(
                end,
                { (PillMetrics.Margin + PillMetrics.Cover + PillMetrics.Gap).toPx() + PillMetrics.ButtonExtra.toPx() * opened() },
                { (PillMetrics.Margin + PillMetrics.BubbleTop).toPx() },
            )
            .layout { measurable, _ ->
                val width = metrics.bubbleWidth.roundToPx()
                val height = PillMetrics.bubbleHeight(body()).roundToPx()
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                val k = tucked()
                // a spring past zero swells it a touch, so it pops out instead of just appearing
                val scale = 1f - TuckShrink * k
                scaleX = scale
                scaleY = scale
                alpha = (1f - k * 1.6f).coerceIn(0f, 1f)
                translationX = toCover * TuckSlide.toPx() * k
                transformOrigin = TransformOrigin(if (end) 1f else 0f, (PillMetrics.BubbleHeight.toPx() * TailAt / size.height).coerceIn(0f, 1f))
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .softShadow(BubbleCorner, BubbleShadowBlur, { BubbleShadowDrop }, { BubbleShadowAlpha })
            .drawWithCache {
                val outline = bubblePath(size, BubbleCorner.toPx(), TailWidth.toPx(), TailHeight.toPx(), PillMetrics.BubbleHeight.toPx() * TailAt, end)
                onDrawBehind { drawPath(outline, tint.bubble) }
            },
    ) {
        PillLyrics(
            state = state,
            width = metrics.bubbleWidth,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(BubbleCorner)),
        )
    }
}

// the bubble's body with its little rounded tail sticking out toward the cover, on the left or mirrored to the right
private fun bubblePath(size: Size, corner: Float, tailWidth: Float, tailHeight: Float, tailY: Float, end: Boolean): Path {
    val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner))) }
    val x = { from: Float -> if (end) size.width - from else from }
    val tail = Path().apply {
        moveTo(x(2f), tailY - tailHeight / 2f)
        quadraticTo(x(-tailWidth * 0.55f), tailY - tailHeight * 0.1f, x(-tailWidth), tailY + tailHeight * 0.08f)
        quadraticTo(x(-tailWidth * 0.4f), tailY + tailHeight * 0.4f, x(2f), tailY + tailHeight / 2f)
        close()
    }
    return Path().apply { op(body, tail, PathOperation.Union) }
}
