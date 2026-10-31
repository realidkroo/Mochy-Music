// CustomisationLivePreviews.kt

package com.mochyy.mochymusic.ui.screens.gesture

import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.LyricsHighBloomKey
import com.mochyy.mochymusic.constants.LyricsMotionStyle
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.WordTimestamp
import com.mochyy.mochymusic.ui.component.BlurDirection
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import com.mochyy.mochymusic.ui.component.GlassChromeBlurRadius
import com.mochyy.mochymusic.ui.component.GlassChromeColor
import com.mochyy.mochymusic.ui.component.GlassPillBackground
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.ProgressiveGlassBackground
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.player.LyricsAlignment
import com.mochyy.mochymusic.ui.player.LyricsGlowLine
import com.mochyy.mochymusic.ui.player.LyricsLineState
import com.mochyy.mochymusic.ui.screens.audiolab.rememberLabTime
import com.mochyy.mochymusic.ui.screens.audiolab.rememberOnScreen
import com.mochyy.mochymusic.ui.screens.audiolab.rememberPicturesHeld
import com.mochyy.mochymusic.ui.screens.audiolab.trackOnScreen
import com.mochyy.mochymusic.ui.utils.AppMotion
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

private val PreviewDot = 26.dp
private val DotRoom = 64.dp

// the colours under the blur preview's bars, bright enough that the frost shows
private val PreviewArt = Brush.linearGradient(listOf(Color(0xFFF28BA8), Color(0xFF8C9EF2), Color(0xFFF2A46B)))
private val PreviewTile = Brush.linearGradient(listOf(Color(0xFF6BD9C4), Color(0xFFB98CF2)))

// a little screen under a real top bar and nav pill, blurred by the same glass the app uses, so every blur setting shows here as it is
@Composable
internal fun BlurPreview() {
    val glass = remember { GlassState() }
    Box(Modifier.fillMaxSize().damageBoundary()) {
        // only the page sits in the glass root, the bars are its siblings so they never capture themselves
        Box(Modifier.fillMaxSize().glassRoot(glass)) { BlurPreviewPage() }
        ProgressiveGlassBackground(
            state = glass,
            maxBlurRadius = { 24f },
            foundationColor = FrameFill,
            tint = Color.Black.copy(alpha = 0.12f),
            direction = BlurDirection.BottomToTop,
            steps = 3,
            modifier = Modifier.fillMaxWidth().height(40.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 9.dp, end = 9.dp, top = 13.dp),
        ) {
            Box(Modifier.size(13.dp).background(Ink.copy(alpha = 0.7f), CircleShape))
            Spacer(Modifier.weight(1f))
            Bar(16.dp, 4.dp, Ink.copy(alpha = 0.7f))
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp)
                .size(width = 80.dp, height = 20.dp),
        ) {
            GlassPillBackground(
                state = glass,
                blurRadius = { GlassChromeBlurRadius },
                tint = GlassChromeColor.copy(alpha = 0.45f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BlurPreviewPage() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(FrameFill)
        val side = 9.dp.toPx()
        drawRoundRect(
            brush = PreviewArt,
            topLeft = Offset(side, 6.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width - side * 2, 64.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
        )
        var y = 78.dp.toPx()
        repeat(4) { row ->
            drawRoundRect(
                brush = if (row == 3) PreviewTile else Brush.linearGradient(listOf(Bone, Bone)),
                topLeft = Offset(side, y),
                size = androidx.compose.ui.geometry.Size(14.dp.toPx(), 14.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            )
            drawRoundRect(
                color = Ink.copy(alpha = 0.6f),
                topLeft = Offset(side + 19.dp.toPx(), y + 2.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(38.dp.toPx(), 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
            drawRoundRect(
                color = BoneDim,
                topLeft = Offset(side + 19.dp.toPx(), y + 8.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(24.dp.toPx(), 3.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f.dp.toPx()),
            )
            y += 20.dp.toPx()
        }
    }
}

// a dot that comes into focus and drifts back out, blurred on the way like a lyric line or a cover zooming away
@Composable
internal fun ElementBlurPreview() {
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    val focus = remember { Animatable(0f) }
    LaunchedEffect(held) {
        while (true) {
            snapshotFlow { held.value }.first { !it }
            focus.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            delay(900)
            focus.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
            delay(500)
        }
    }
    Box(Modifier.fillMaxSize().trackOnScreen(onScreen).damageBoundary(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .offset(y = (-14).dp)
                .size(DotRoom)
                .graphicsLayer {
                    val f = focus.value
                    val zoom = 0.62f + 0.38f * f
                    scaleX = zoom
                    scaleY = zoom
                    alpha = 0.35f + 0.65f * f
                    // reads the element blur switch while drawing, so flipping it shows straight away
                    renderEffect = BlurEffectCache.element((1f - f) * 10.dp.toPx(), Shader.TileMode.CLAMP)
                }
                .drawBehind { drawCircle(Color.White, radius = PreviewDot.toPx() / 2f) },
        )
    }
}

// a dot hopping from side to side at the app's animation speed, with animations off it just jumps
@Composable
internal fun AnimationPreview() {
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    val travel = remember { Animatable(0f) }
    LaunchedEffect(held) {
        var target = 1f
        while (true) {
            snapshotFlow { held.value }.first { !it }
            travel.animateTo(target, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
            delay(650)
            target = 1f - target
        }
    }
    Box(Modifier.fillMaxSize().trackOnScreen(onScreen).damageBoundary()) {
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(y = (-14).dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(BoneDim, CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset {
                    // from 10dp in on the left to 10dp in on the right of the frame
                    val span = (PreviewWidth - 20.dp - PreviewDot).toPx()
                    IntOffset((10.dp.toPx() + travel.value * span).roundToInt(), (-14).dp.roundToPx())
                }
                .size(PreviewDot)
                .background(Color.White, CircleShape),
        )
    }
}

private const val LyricsLoopMs = 7_400L
private val LyricsPreviewWidth = 300.dp
private const val LyricsPreviewScale = 0.42f

// the words from the design, timed word by word so every style has something to sing
private val PreviewLyrics: List<Pair<LyricsEntry, LyricsAlignment>> = listOf(
    previewLine(0L, 1_600L, "if i am a stone") to LyricsAlignment.START,
    previewLine(1_700L, 3_700L, "would you still love me?") to LyricsAlignment.START,
    previewLine(3_900L, 6_400L, "of course, what do you mean my love") to LyricsAlignment.END,
)

private fun previewLine(start: Long, end: Long, text: String): LyricsEntry {
    val words = text.split(' ')
    val each = (end - start) / words.size
    var at = start
    val timed = words.mapIndexed { i, word ->
        val from = at
        at += each
        // a space after every word but the last, so the timed pieces add up to the line's own text
        WordTimestamp(if (i < words.lastIndex) "$word " else word, from / 1000.0, at / 1000.0)
    }
    return LyricsEntry(time = start, text = text, words = timed)
}

// the real lyric line drawn small, singing on a loop in [style], with element blur and the shader as they're set
@Composable
internal fun LyricsStylePreview(style: LyricsMotionStyle) {
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    val seconds = rememberLabTime(held)
    val position = remember { { (seconds.value * 1000f).toLong() % LyricsLoopMs } }
    val current by remember {
        derivedStateOf { PreviewLyrics.indexOfLast { it.first.time <= position() }.coerceAtLeast(0) }
    }
    val (highBloom) = rememberPreference(LyricsHighBloomKey, defaultValue = true)
    val heights = remember { mutableStateListOf(0, 0, 0) }
    val scroll by animateFloatAsState(
        targetValue = heights.take(current).sum().toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "lyricsPreviewScroll",
    )

    Box(Modifier.fillMaxSize().trackOnScreen(onScreen)) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredWidth(LyricsPreviewWidth)
                .graphicsLayer {
                    scaleX = LyricsPreviewScale
                    scaleY = LyricsPreviewScale
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationY = 16.dp.toPx() - scroll * LyricsPreviewScale
                },
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(verticalArrangement = Arrangement.Top) {
                    PreviewLyrics.forEachIndexed { index, (entry, alignment) ->
                        val distance = index - current
                        val state = when {
                            distance == 0 -> LyricsLineState.ACTIVE
                            distance == 1 -> LyricsLineState.UPCOMING
                            distance < 0 -> LyricsLineState.PAST
                            else -> LyricsLineState.DEFAULT
                        }
                        LyricsGlowLine(
                            entry = entry,
                            state = state,
                            blurStage = if (AppMotion.elementBlur) abs(distance).coerceAtMost(2) else 0,
                            positionProvider = position,
                            accentColor = Color.White,
                            onClick = {},
                            waveEnabled = style.shaded,
                            highBloom = highBloom,
                            alignment = alignment,
                            motionStyle = style,
                            modifier = Modifier.onSizeChanged { heights[index] = it.height },
                        )
                    }
                }
            }
        }
        // the lines are tappable on the lyrics page, here a tap should pick the card instead
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        )
    }
}

// a tiny open player with the phone's clock and icons along its top, sliding away when the top bar is off
@Composable
internal fun StatusBarPreview(shown: Boolean) {
    val visible by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "statusBarPreview")
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .padding(horizontal = 10.dp)
                .graphicsLayer {
                    alpha = visible
                    translationY = -(1f - visible) * 8.dp.toPx()
                },
        ) {
            Bar(18.dp, 5.dp)
            Spacer(Modifier.weight(1f))
            repeat(3) {
                Box(Modifier.padding(start = 2.5.dp).size(4.dp).background(Ink, CircleShape))
            }
        }
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .height(72.dp)
                .background(ArtFill, RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(10.dp))
        TitleBars(Modifier.padding(start = 10.dp))
    }
}

// a tiny open player with the phone's recents, home and back buttons along its foot, sinking away when the bottom bar is off
@Composable
internal fun NavBarPreview(shown: Boolean) {
    val visible by animateFloatAsState(if (shown) 1f else 0f, tween(260), label = "navBarPreview")
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val stripHeight = 21.dp.toPx()
                val lip = 2.dp.toPx()
                val strip = Brush.verticalGradient(listOf(Color(0xFF707072), Color(0xFF505052)), startY = 0f, endY = stripHeight)
                val centerY = stripHeight / 2f
                val spacing = 38.dp.toPx()
                val recentsX = size.width / 2f - spacing
                val backX = size.width / 2f + spacing
                val lineHalf = 5.5.dp.toPx()
                val lineGap = 1.9.dp.toPx()
                val lineWidth = 1.dp.toPx()
                val back = Path().apply {
                    moveTo(backX - 4.5.dp.toPx(), centerY)
                    lineTo(backX + 4.5.dp.toPx(), centerY - 5.dp.toPx())
                    lineTo(backX + 4.5.dp.toPx(), centerY + 5.dp.toPx())
                    close()
                }
                val roundedTip = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round)
                onDrawBehind {
                    drawRect(NavPreviewScreen)
                    // read here, so the slide only redraws
                    val shownBy = visible
                    if (shownBy == 0f) return@onDrawBehind
                    translate(top = size.height - stripHeight - lip + (1f - shownBy) * 8.dp.toPx()) {
                        drawRect(strip, size = Size(size.width, stripHeight), alpha = shownBy)
                        drawRect(NavPreviewLip, topLeft = Offset(0f, stripHeight), size = Size(size.width, lip), alpha = shownBy)
                        drawLine(Color.White, Offset(recentsX - lineHalf, centerY - lineGap), Offset(recentsX + lineHalf, centerY - lineGap), strokeWidth = lineWidth, alpha = shownBy)
                        drawLine(Color.White, Offset(recentsX - lineHalf, centerY + lineGap), Offset(recentsX + lineHalf, centerY + lineGap), strokeWidth = lineWidth, alpha = shownBy)
                        drawCircle(NavPreviewIcon, radius = 5.dp.toPx(), center = Offset(size.width / 2f, centerY), alpha = shownBy)
                        drawPath(back, NavPreviewIcon, alpha = shownBy)
                        drawPath(back, NavPreviewIcon, alpha = shownBy, style = roundedTip)
                    }
                }
            },
    )
}

// the player's screen in the bottom bar preview, shaded under where the phone's top edge would be
private val NavPreviewScreen = Brush.verticalGradient(0f to FrameFill, 0.13f to Color(0xFF545456))
private val NavPreviewLip = Color(0xFF414143)
private val NavPreviewIcon = Color(0xFFD9D9D9)
