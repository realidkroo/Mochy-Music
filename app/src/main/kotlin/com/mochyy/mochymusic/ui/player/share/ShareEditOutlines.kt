// ShareEditOutlines.kt
//
// CoolEditor's boxes round the song and the words on the picture. Each one chases its part on a
// spring, so the outline moves along when the song slides past the words or a word goes big,
// breathes softly so it reads as something to tap, and squishes a little under the finger.

package com.mochyy.mochymusic.ui.player.share

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.mochyy.mochymusic.ui.component.RemoteFocus

/** How the editor's boxes chase what they outline: quick, with a little give. */
private val BoxSpring = spring<Rect>(dampingRatio = 0.72f, stiffness = 420f)

/**
 * One editor box. It chases its part of the picture on a spring rather than snapping to it, so
 * when the song slides past the words, or a word goes big and the lines grow, the outline follows
 * along with a little give.
 */
@Stable
private class ChasingBox {
    val rect = Animatable(Rect.Zero, Rect.VectorConverter)
    var placed by mutableStateOf(false)
        private set

    suspend fun follow(target: () -> Rect?) = coroutineScope {
        snapshotFlow(target).filterNotNull().collect { to ->
            if (placed) {
                // a fresh animation takes over the running one with its speed; cancelling the
                // old one first would drop that speed and the box would lag behind a slide
                launch { rect.animateTo(to, BoxSpring) }
            } else {
                rect.snapTo(to)
                placed = true
            }
        }
    }
}

/**
 * The editor's outlines round the song and the words, breathing softly so they read as things to
 * tap, and squishing a little under the finger. They're drawn over the picture, never into it, so
 * the shared image stays clean.
 */
@Composable
internal fun BoxScope.EditOutlines(
    regions: ShareRegions,
    headerBelow: Boolean,
    onWords: () -> Unit,
    onMoveHeader: () -> Unit,
) {
    val header = remember { ChasingBox() }
    val words = remember { ChasingBox() }
    LaunchedEffect(regions) { launch { header.follow { regions.header } }; words.follow { regions.words } }
    if (!header.placed && !words.placed) return

    val pad = with(LocalDensity.current) { 10.dp.toPx() }
    val headerTouch = remember { MutableInteractionSource() }
    val wordsTouch = remember { MutableInteractionSource() }
    val headerSquish = rememberSquish(headerTouch)
    val wordsSquish = rememberSquish(wordsTouch)
    val breathe = rememberInfiniteTransition(label = "outlines").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "outlineAlpha",
    )
    Box(
        Modifier
            .matchParentSize()
            .drawBehind {
                // read here, so the breathing and the chasing only redraw, never recompose
                listOf(header to headerSquish, words to wordsSquish).forEach { (box, squish) ->
                    if (!box.placed) return@forEach
                    val r = box.rect.value.inflate(pad).scaledAboutCenter(squish.value)
                    drawRoundRect(
                        Color.White.copy(alpha = breathe.value),
                        topLeft = r.topLeft,
                        size = r.size,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(3.dp.toPx()),
                    )
                }
            },
    )
    if (words.placed) TapArea(words, pad, wordsTouch, onWords)
    if (header.placed) {
        TapArea(header, pad, headerTouch, onMoveHeader)
        MoveSongArrow(header, pad, headerBelow, onMoveHeader)
    }
}

/** 1 at rest, a touch smaller while pressed, springing back past 1 on release. */
@Composable
private fun rememberSquish(interaction: MutableInteractionSource): androidx.compose.runtime.State<Float> {
    val pressed by interaction.collectIsPressedAsState()
    return animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "outlineSquish")
}

private fun Rect.scaledAboutCenter(s: Float): Rect {
    if (s == 1f) return this
    val c = center
    return Rect(c.x + (left - c.x) * s, c.y + (top - c.y) * s, c.x + (right - c.x) * s, c.y + (bottom - c.y) * s)
}

/** An invisible tap target that rides along with its box. */
@Composable
private fun TapArea(box: ChasingBox, pad: Float, interaction: MutableInteractionSource, onClick: () -> Unit) {
    Box(
        Modifier
            .layout { measurable, _ ->
                val r = box.rect.value.inflate(pad)
                val placeable = measurable.measure(
                    Constraints.fixed(r.width.roundToInt().coerceAtLeast(0), r.height.roundToInt().coerceAtLeast(0)),
                )
                layout(placeable.width, placeable.height) { placeable.place(r.left.roundToInt(), r.top.roundToInt()) }
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick),
    )
}

/** The arrow by the song that moves it to the other side of the words, riding along as it goes. */
@Composable
private fun MoveSongArrow(header: ChasingBox, pad: Float, headerBelow: Boolean, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (headerBelow) 0f else 180f, spring(dampingRatio = 0.7f, stiffness = 300f), label = "arrowTurn")
    val gap = with(LocalDensity.current) { 6.dp.toPx() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset {
                val r = header.rect.value.inflate(pad)
                val size = 26.dp.toPx()
                // the arrow sits on the side the song would move to
                val y = if (headerBelow) r.bottom + gap else r.top - size - gap
                IntOffset((r.center.x - size / 2).roundToInt(), y.roundToInt())
            }
            .size(26.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(
            painterResource(R.drawable.expand_more),
            contentDescription = stringResource(if (headerBelow) R.string.ps_share_move_song_above else R.string.ps_share_move_song_under),
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { rotationZ = turn },
        )
    }
}
