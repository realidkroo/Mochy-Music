package com.mochyy.mochymusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.ui.component.damageBoundary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.exp
import kotlin.math.min

/** the note's own text style, shared with the like note so the two sit in the same spot the same size */
internal val NoteTextStyle = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)

/** quieter than the artist line (white at 0.7) */
internal val NoteColor = Color.White.copy(alpha = 0.45f)

/** a faint dark halo behind the like note, so it still reads over a bright cover */
internal fun noteShadow(density: Density): Shadow = with(density) {
    Shadow(color = Color.Black.copy(alpha = 0.42f), offset = Offset(0f, 0.5.dp.toPx()), blurRadius = 4.dp.toPx())
}

/** the mixing note: a soft light drifts through it letter by letter, brightening each one with a bloom like the lyrics' sweep, then rests and goes again; it blurs in and out as it shows and hides */
@Composable
internal fun MixingNote(text: String, shown: Float, active: () -> Boolean) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // every letter laid out once, so the sweep only moves and lights them and never measures
    val layout = remember(text, measurer, density) { NoteLayout.of(text, measurer.measure(text, NoteTextStyle)) { measurer.measure(it, NoteTextStyle) } }
    // the bloom is the lit letters again on a blurred layer under the sharp ones; with no blur (old phones, element blur off) a soft white text glow stands in
    val bloom = remember(density) { blurEffectForRadius(with(density) { BloomBlur.toPx() }) }
    val fallbackGlows = remember(density) {
        val blur = with(density) { 4.dp.toPx() }
        Array(GlowLevels) { Shadow(Color.White.copy(alpha = 0.75f * (it + 1) / GlowLevels), Offset.Zero, blur) }
    }
    // held while the player isn't open: nobody sees it, and it would draw a frame every few ms for nothing
    val held by remember { derivedStateOf { !active() } }
    val head = remember { Animatable(HeadStart) }
    LaunchedEffect(head) {
        while (true) {
            if (held) snapshotFlow { held }.first { !it }
            head.snapTo(HeadStart)
            head.animateTo(HeadEnd, tween(SweepMs, easing = SweepEasing))
            delay(RestMs)
        }
    }

    val bleed = NoteBleed
    Box(
        modifier = Modifier
            .layerBleed(bleed)
            // the light loops for as long as the mix runs: bounded, it repaints these few pixels and not the whole player
            .damageBoundary()
            .graphicsLayer {
                alpha = shown
                // soft while it comes and goes, sharp once it's shown, so the blur costs nothing at rest
                renderEffect = blurEffectForRadius(min((1f - shown) * AppearBlur.toPx(), MaxAppearBlurPx))
            }
            .padding(bleed)
            .size(with(density) { layout.width.toDp() }, with(density) { layout.height.toDp() }),
    ) {
        if (bloom != null) {
            Canvas(
                Modifier
                    .matchParentSize()
                    // only blurred while a letter is lit, so the resting note costs no blur pass
                    .graphicsLayer { renderEffect = if (head.value < HeadDark) bloom else null },
            ) {
                drawLetters(layout, head.value) { letter, glow ->
                    if (glow > 0.02f) drawText(letter.text, color = Color.White, alpha = BloomAlpha * glow)
                }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            drawLetters(layout, head.value) { letter, glow ->
                val shadow = if (bloom == null && glow > 0.03f) fallbackGlows[(glow * GlowLevels).toInt().coerceIn(0, GlowLevels - 1)] else null
                drawText(letter.text, color = lerp(NoteColor, Color.White, glow), shadow = shadow)
            }
        }
    }
}

// every letter where the light puts it: lifted a touch and swelled a little as the light passes, both canvases the same so the bloom sits under its letter
private inline fun DrawScope.drawLetters(layout: NoteLayout, headFraction: Float, draw: DrawScope.(NoteLetter, Float) -> Unit) {
    val headX = headFraction * size.width
    val reach = GlowReach.toPx()
    val lift = GlowLift.toPx()
    for (letter in layout.letters) {
        val center = letter.left + letter.text.size.width / 2f
        val distance = (center - headX) / reach
        val glow = exp(-distance * distance)
        val grow = 1f + GlowGrow * glow
        translate(left = letter.left, top = -lift * glow) {
            scale(grow, grow, pivot = Offset(letter.text.size.width / 2f, letter.text.size.height.toFloat())) {
                draw(letter, glow)
            }
        }
    }
}

/** the note laid out letter by letter, each at the spot it holds in the whole line */
private class NoteLayout(val letters: List<NoteLetter>, val width: Int, val height: Int) {
    companion object {
        fun of(text: String, whole: TextLayoutResult, measure: (String) -> TextLayoutResult): NoteLayout {
            val letters = text.indices.filter { !text[it].isWhitespace() }.map { i ->
                NoteLetter(measure(text[i].toString()), whole.getBoundingBox(i).left)
            }
            return NoteLayout(letters, whole.size.width, whole.size.height)
        }
    }
}

private class NoteLetter(val text: TextLayoutResult, val left: Float)

// the head runs from before the first letter to past the last, so every letter gets a full swell
private const val HeadStart = -0.25f
private const val HeadEnd = 1.25f
// past this the light has left the last letter, so the bloom layer can stop blurring
private const val HeadDark = 1.2f
// slow and even, a sine ease rather than a snap, so the light drifts instead of darting
private const val SweepMs = 2_400
private val SweepEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
// then the letters sit still a moment before it goes again
private const val RestMs = 800L
private const val GlowLevels = 8
private const val GlowGrow = 0.05f
private const val BloomAlpha = 0.9f
private const val MaxAppearBlurPx = 12f
private val GlowReach = 13.dp
private val GlowLift = 0.8.dp
private val BloomBlur = 3.dp
private val AppearBlur = 4.dp
// room around the letters for the lift, the swell, the bloom and the blur, so the layer never cuts them
private val NoteBleed = 8.dp
