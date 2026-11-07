// TouchFeedback.kt

package com.mochyy.mochymusic.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** the pressed row from the Figma: #1E1E1E on the black pages, and the same lift on any darker colour */
val HighlightColor = Color.White.copy(alpha = 0.12f)

/**
 * a soft rounded box that fades in behind whatever is pressed, in place of material's square
 * ripple. it's drawn under the content so nothing gets clipped, and it can spill past the bounds
 * (or sit inside them, with a negative spill) so a card or a row gets some room around it.
 */
@Immutable
class RoundedHighlight(
    private val cornerRadius: Dp,
    private val spillX: Dp = 0.dp,
    private val spillY: Dp = 0.dp,
    private val color: Color = HighlightColor,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        RoundedHighlightNode(interactionSource, cornerRadius, spillX, spillY, color)

    override fun equals(other: Any?): Boolean =
        other is RoundedHighlight &&
            other.cornerRadius == cornerRadius &&
            other.spillX == spillX &&
            other.spillY == spillY &&
            other.color == color

    override fun hashCode(): Int {
        var result = cornerRadius.hashCode()
        result = 31 * result + spillX.hashCode()
        result = 31 * result + spillY.hashCode()
        result = 31 * result + color.hashCode()
        return result
    }
}

private class RoundedHighlightNode(
    private val interactionSource: InteractionSource,
    private val cornerRadius: Dp,
    private val spillX: Dp,
    private val spillY: Dp,
    private val color: Color,
) : Modifier.Node(), DrawModifierNode {
    private val strength = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            val presses = ArrayList<PressInteraction.Press>()
            var focused = false
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses.add(interaction)
                    is PressInteraction.Release -> presses.remove(interaction.press)
                    is PressInteraction.Cancel -> presses.remove(interaction.press)
                    // the tv remote resting on it keeps the box lit, so people can see where they are
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                    else -> return@collect
                }
                if (presses.isNotEmpty() || focused) {
                    launch { strength.animateTo(1f, tween(90)) }
                } else {
                    launch {
                        // a quick tap lets go before the box has shown at all, so flash it first
                        if (strength.value < 1f) strength.animateTo(1f, tween(70))
                        strength.animateTo(0f, tween(340))
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val s = strength.value
        if (s > 0f) {
            val dx = spillX.toPx()
            val dy = spillY.toPx()
            drawRoundRect(
                color = color.copy(alpha = color.alpha * s),
                topLeft = Offset(-dx, -dy),
                size = Size(size.width + dx * 2f, size.height + dy * 2f),
                cornerRadius = CornerRadius(cornerRadius.toPx()),
            )
        }
        drawContent()
    }
}

/** a soft wash and a thin outline on whatever the tv remote rests on, fingers never focus so phones never see it */
object RemoteFocus : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = RemoteFocusNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = javaClass.hashCode()
}

private class RemoteFocusNode(private val interactionSource: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val strength = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                val target = when (interaction) {
                    is FocusInteraction.Focus -> 1f
                    is FocusInteraction.Unfocus -> 0f
                    else -> return@collect
                }
                launch { strength.animateTo(target, tween(if (target > 0f) 120 else 200)) }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val s = strength.value
        if (s <= 0f) return
        // round enough to hug a circle icon, capped so a big card keeps roughly its own corners
        val corner = CornerRadius(minOf(size.minDimension / 2f, RemoteFocusCorner.toPx()))
        drawRoundRect(color = Color.White.copy(alpha = 0.16f * s), cornerRadius = corner)
        val stroke = RemoteFocusStroke.toPx()
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f * s),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = corner,
            style = Stroke(stroke),
        )
    }
}

private val RemoteFocusCorner = 20.dp
private val RemoteFocusStroke = 2.dp

/** a backdrop that still catches taps but never takes the tv remote's focus, or it swallows the first press */
fun Modifier.backdropClick(onClick: () -> Unit = {}): Modifier =
    focusProperties { canFocus = false }.clickable(interactionSource = null, indication = null, onClick = onClick)

/**
 * squishes under the finger and springs back with a little overshoot. a quick tap inside a list
 * reports its press and release together, so the release plays the squish first when it never showed.
 */
@Composable
fun Modifier.pressBounce(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.88f,
): Modifier {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactionSource, pressedScale) {
        var pressed = 0
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressed++
                    launch { scale.animateTo(pressedScale, spring(dampingRatio = 1f, stiffness = 1600f)) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressed = (pressed - 1).coerceAtLeast(0)
                    if (pressed == 0) {
                        launch {
                            if (scale.value > pressedScale + 0.03f) scale.animateTo(pressedScale, tween(70))
                            scale.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 520f))
                        }
                    }
                }
            }
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
