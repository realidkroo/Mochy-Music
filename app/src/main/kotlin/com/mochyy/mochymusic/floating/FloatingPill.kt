// FloatingPill.kt

package com.mochyy.mochymusic.floating

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.makeTimeString
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt

// the panel behind the open controls, dark enough to read the times over any app
private val PanelColor = Color(0xF22A2B2D)
private val ButtonInk = Color(0xFF3A3B3D)
private val CoverPlaceholder = Color(0xFF55585C)

private val RingStroke = 2.5.dp
private val RingInset = 5.dp

// as the controls open the ring grows outward while it fades, handing over to the button
private val RingGrow = 7.dp
private val ButtonCorner = 16.dp
private val BubbleCorner = 18.dp
private val TailWidth = 8.dp
private val TailHeight = 13.dp

// where the bubble's tail points back at the cover, down its left side
private const val TailAt = 0.64f
private val PanelCorner = 26.dp
private val BarTop = 14.dp
private val BarHeight = 4.dp
private val TimesTop = 10.dp
private val PillShadow = 8.dp

// the controls tuck away again after this long without a touch
private const val AutoCollapseMs = 4_500L

private val OpenSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 380f)
private val CloseSpring = spring<Float>(dampingRatio = 1f, stiffness = 520f)

// the pill's sizes; the bubble's width comes from the screen so the open controls always fit
@Immutable
internal class PillMetrics(val bubbleWidth: Dp) {
    val collapsedWidth: Dp get() = Margin * 2 + Cover + Gap + bubbleWidth
    val collapsedHeight: Dp get() = Margin * 2 + Cover
    val expandedWidth: Dp get() = collapsedWidth + ButtonExtra
    val expandedHeight: Dp get() = collapsedHeight + BarArea

    companion object {
        // room around the pill for its shadow, and the open panel's padding
        val Margin = 14.dp
        val Cover = 62.dp
        val Gap = 10.dp
        val BubbleHeight = 58.dp

        // how far the button reaches past the cover once the controls are open
        val ButtonExtra = 48.dp

        // the progress bar and its times under the open controls
        val BarArea = 40.dp

        fun forScreen(screenWidth: Dp): PillMetrics {
            val room = screenWidth - Margin * 2 - Cover - Gap - ButtonExtra - 12.dp
            return PillMetrics(room.coerceIn(150.dp, 226.dp))
        }
    }
}

// what the pill asks of the window and the service around it
internal interface PillHost {
    fun setWindowExpanded(expanded: Boolean)
    fun openApp()
    fun togglePlay()
}

@Composable
internal fun FloatingPill(state: PillState, metrics: PillMetrics, host: PillHost) {
    val expand = remember { Animatable(0f) }
    LaunchedEffect(state.expanded) {
        if (state.expanded) {
            // the window grows first, right and down only, so nothing on screen moves when it does
            host.setWindowExpanded(true)
            expand.animateTo(1f, OpenSpring)
        } else {
            expand.animateTo(0f, CloseSpring)
            host.setWindowExpanded(false)
        }
    }
    LaunchedEffect(state.expanded, state.touchTick) {
        if (!state.expanded) return@LaunchedEffect
        delay(AutoCollapseMs)
        state.expanded = false
    }

    val progress = remember { mutableFloatStateOf(0f) }
    val elapsed = remember { mutableLongStateOf(0L) }
    val timesShown by remember { derivedStateOf { expand.value > 0.01f } }
    SongProgressTicker(state, progress, elapsed, timesShown)

    // the pill is a fixed size, so the text inside it keeps to its own size too
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
        val opened = { expand.value.coerceIn(0f, 1f) }
        Box(Modifier.fillMaxSize()) {
            PanelBackdrop(metrics, opened)
            if (timesShown) ProgressAndTimes(state, progress, elapsed, opened)
            PlayButton(state, host, opened)
            CoverDisc(state, host, progress, opened)
            Bubble(state, metrics, opened)
        }
    }
}

// the ring and the times are all that follow the song between lines, and they only need a nudge now and then
@Composable
private fun SongProgressTicker(state: PillState, progress: MutableFloatState, elapsed: androidx.compose.runtime.MutableLongState, timesShown: Boolean) {
    val ringLength = with(LocalDensity.current) { (PillMetrics.Cover / 2 - RingInset).toPx() * 2f * PI.toFloat() }
    LaunchedEffect(state.playing, state.seekTick, state.song?.id, timesShown) {
        while (true) {
            val duration = state.durationMs()
            val position = state.songPositionMs()
            progress.floatValue = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            elapsed.longValue = position
            if (!state.playing) break
            // about a pixel of ring at a time, so a long song wakes up less often than a short one
            val ringStep = if (duration > 0L) (duration / ringLength).toLong().coerceIn(300L, 2_000L) else 1_000L
            val wait = if (timesShown) minOf(ringStep, 1_000L - position % 1_000L + 8L) else ringStep
            delay((wait / state.speed()).toLong().coerceAtLeast(50L))
        }
    }
}

// the dark panel grows out of the pill as the controls open
@Composable
private fun PanelBackdrop(metrics: PillMetrics, opened: () -> Float) {
    Spacer(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val t = opened()
                if (t <= 0.001f) return@drawBehind
                val margin = PillMetrics.Margin.toPx()
                val cover = PillMetrics.Cover.toPx()
                val pillRight = margin + cover + PillMetrics.Gap.toPx() + metrics.bubbleWidth.toPx()
                val left = margin * (1f - t)
                val top = margin * (1f - t)
                val right = pillRight + (size.width - pillRight) * t
                val bottom = margin + cover + (size.height - margin - cover) * t
                val corner = cover / 2f + (PanelCorner.toPx() - cover / 2f) * t
                drawRoundRect(
                    color = PanelColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(corner),
                    alpha = t,
                )
            },
    )
}

// the cover's ring unrolled: a bar across the open panel with the time gone and the time left
@Composable
private fun ProgressAndTimes(state: PillState, progress: MutableFloatState, elapsed: androidx.compose.runtime.MutableLongState, opened: () -> Float) {
    Spacer(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val t = opened()
                val margin = PillMetrics.Margin.toPx()
                val y = margin + PillMetrics.Cover.toPx() + BarTop.toPx() + BarHeight.toPx() / 2f
                val full = size.width - margin * 2f
                val shown = full * t
                val stroke = BarHeight.toPx()
                drawLine(Color.White.copy(alpha = 0.22f * t), Offset(margin, y), Offset(margin + shown, y), stroke, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = t), Offset(margin, y), Offset(margin + shown * progress.floatValue, y), stroke, StrokeCap.Round)
            },
    )
    val style = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color.White.copy(alpha = 0.62f))
    val timesY = PillMetrics.Margin + PillMetrics.Cover + BarTop + BarHeight + TimesTop
    BasicText(
        text = makeTimeString(elapsed.longValue),
        style = style,
        modifier = Modifier
            .offset(PillMetrics.Margin, timesY)
            .graphicsLayer { alpha = opened() },
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        BasicText(
            text = makeTimeString(state.durationMs()),
            style = style,
            modifier = Modifier
                .offset(-PillMetrics.Margin, timesY)
                .graphicsLayer { alpha = opened() },
        )
    }
}

// the light button the ring turns into: it slides out from behind the cover with play or pause in it
@Composable
private fun PlayButton(state: PillState, host: PillHost, opened: () -> Float) {
    val margin = PillMetrics.Margin
    val cover = PillMetrics.Cover
    val extra = PillMetrics.ButtonExtra
    Spacer(
        Modifier
            .offset(margin + cover / 2, margin)
            .size(cover / 2 + extra, cover)
            .drawBehind {
                val t = opened()
                if (t <= 0.001f) return@drawBehind
                drawRoundRect(
                    color = BubbleColor,
                    size = Size(size.height / 2f + extra.toPx() * t, size.height),
                    cornerRadius = CornerRadius(ButtonCorner.toPx()),
                    alpha = (t * 4f).coerceAtMost(1f),
                )
            },
    )
    val tapToggle = if (state.expanded) {
        Modifier.pointerInput(Unit) {
            detectTapGestures {
                state.touchTick++
                host.togglePlay()
            }
        }
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .offset(margin + cover, margin)
            .size(extra, cover)
            .then(tapToggle),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(if (state.playing) R.drawable.pause_applemusic else R.drawable.play_applemusic),
            contentDescription = stringResource(if (state.playing) R.string.pause else R.string.play),
            colorFilter = ColorFilter.tint(ButtonInk),
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    val t = opened()
                    val shown = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    alpha = shown
                    scaleX = 0.6f + 0.4f * shown
                    scaleY = scaleX
                    translationX = -extra.toPx() / 2f * (1f - t)
                },
        )
    }
}

// the song's cover in a circle with how far along it is as a ring; a tap opens mochy, a hold opens the controls
@Composable
private fun CoverDisc(state: PillState, host: PillHost, progress: MutableFloatState, opened: () -> Float) {
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val press = animateFloatAsState(if (pressed) 0.93f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "coverPress")
    val context = LocalContext.current
    val coverPx = with(LocalDensity.current) { PillMetrics.Cover.roundToPx() }
    val url = state.song?.thumbnailUrl
    val request = remember(url, coverPx) { ImageRequest.Builder(context).data(url).size(coverPx).build() }
    Box(
        Modifier
            .offset(PillMetrics.Margin, PillMetrics.Margin)
            .size(PillMetrics.Cover)
            .graphicsLayer {
                scaleX = press.value
                scaleY = press.value
                shadowElevation = PillShadow.toPx() * (1f - opened())
                shape = CircleShape
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { host.openApp() },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.touchTick++
                        state.expanded = !state.expanded
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
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val t = opened()
                    val fade = 1f - t
                    if (fade <= 0f) return@drawBehind
                    val stroke = RingStroke.toPx()
                    val radius = size.minDimension / 2f - RingInset.toPx() + RingGrow.toPx() * t
                    val topLeft = Offset(center.x - radius, center.y - radius)
                    drawCircle(Color.Black.copy(alpha = 0.2f * fade), radius, style = Stroke(stroke))
                    drawArc(
                        color = Color.White.copy(alpha = fade),
                        startAngle = -90f,
                        sweepAngle = 360f * progress.floatValue,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                },
        )
    }
}

// the speech bubble with the lyrics, pushed along by the button as the controls open
@Composable
private fun Bubble(state: PillState, metrics: PillMetrics, opened: () -> Float) {
    val height = PillMetrics.BubbleHeight
    Box(
        Modifier
            .offset {
                val x = PillMetrics.Margin + PillMetrics.Cover + PillMetrics.Gap
                val y = PillMetrics.Margin + (PillMetrics.Cover - height) / 2
                IntOffset((x.toPx() + PillMetrics.ButtonExtra.toPx() * opened()).roundToInt(), y.roundToPx())
            }
            .size(metrics.bubbleWidth, height)
            .shadow(PillShadow, RoundedCornerShape(BubbleCorner), clip = false)
            .drawWithCache {
                val outline = bubblePath(size, BubbleCorner.toPx(), TailWidth.toPx(), TailHeight.toPx())
                onDrawBehind { drawPath(outline, BubbleColor) }
            },
    ) {
        PillLyrics(
            state = state,
            width = metrics.bubbleWidth,
            height = height,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(BubbleCorner)),
        )
    }
}

// the bubble's body with its little rounded tail sticking out to the left, toward the cover
private fun bubblePath(size: Size, corner: Float, tailWidth: Float, tailHeight: Float): Path {
    val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner))) }
    val y = size.height * TailAt
    val tail = Path().apply {
        moveTo(2f, y - tailHeight / 2f)
        quadraticTo(-tailWidth * 0.55f, y - tailHeight * 0.1f, -tailWidth, y + tailHeight * 0.08f)
        quadraticTo(-tailWidth * 0.4f, y + tailHeight * 0.4f, 2f, y + tailHeight / 2f)
        close()
    }
    return Path().apply { op(body, tail, PathOperation.Union) }
}
