// the pieces the storage page is built from: usage bars, legends, the cache limit slider and the action buttons

package com.mochyy.mochymusic.ui.screens.settings.storage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlin.math.roundToInt
import com.mochyy.mochymusic.ui.component.RemoteFocus

internal val StorageCard = Color(0xFF1C1C1E)
internal val StorageTrack = Color(0xFF3A3A3C)
internal val StorageMuted = Color(0xFF8E8E93)
internal val StorageDanger = Color(0xFFF07A70)

internal val MochyGreen = Color(0xFF86EF7F)
internal val OthersGrey = Color(0xFF6E6E73)
internal val CacheOrange = Color(0xFFF5B26B)
internal val DatabaseRed = Color(0xFFEE6E5E)
internal val AppDataBlue = Color(0xFF6878F0)

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private val SliderHeight = 32.dp

internal fun storageText(
    size: Float,
    weight: FontWeight = FontWeight.Bold,
    color: Color = Color.White,
    tracking: Float = -0.5f,
) = TextStyle(fontFamily = InterFontFamily, fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.sp, color = color)

/** bytes the way android's own storage screen writes them: powers of 1000, one decimal past 10 */
internal fun formatSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.coerceAtLeast(0L).toDouble()
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) {
        value /= 1000
        unit++
    }
    val number = if (value >= 10 || unit == 0) value.roundToInt().toString() else String.format("%.1f", value)
    return "$number ${units[unit]}"
}

@Composable
internal fun StorageCardColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(StorageCard)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = content,
    )
}

internal class BarPart(val color: Color, val bytes: Long)

/** a rounded bar split into parts over total bytes, a part too small to see still gets a sliver so nothing on the legend is missing */
@Composable
internal fun UsageBar(parts: List<BarPart>, total: Long, modifier: Modifier = Modifier) {
    val grow = remember { Animatable(0f) }
    val ready = total > 0
    LaunchedEffect(ready) { if (ready) grow.animateTo(1f, tween(1100, easing = Emphasized)) }
    // each edge eases to where the latest numbers put it
    val fractions = parts.map { part ->
        animateFloatAsState(
            targetValue = if (total > 0) (part.bytes.toFloat() / total).coerceIn(0f, 1f) else 0f,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 120f),
            label = "usageBarPart",
        )
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(StorageTrack)
            .drawBehind {
                val minSliver = 3.dp.toPx()
                var x = 0f
                parts.forEachIndexed { i, part ->
                    if (part.bytes <= 0L) return@forEachIndexed
                    val width = (fractions[i].value * size.width).coerceAtLeast(minSliver) * grow.value
                    // the box's own round clip shapes the two ends
                    drawRect(
                        color = part.color,
                        topLeft = Offset(x, 0f),
                        size = Size(width.coerceAtMost(size.width - x), size.height),
                    )
                    x += width
                }
            },
    )
}

@Composable
internal fun LegendSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
    )
}

/** a legend line under a bar: the colour, what it is, and how big it is on the right */
@Composable
internal fun LegendRow(color: Color, label: String, size: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        LegendSwatch(color)
        Spacer(Modifier.width(10.dp))
        Text(label, style = storageText(17f, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
        if (size != null) {
            AnimatedContent(
                targetState = size,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()).togetherWith(slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "legendSize",
            ) { shown ->
                Text(shown, style = storageText(12f, FontWeight.Medium, StorageMuted, 0f))
            }
        }
    }
}

// a thick pill the white fill grows across with a small grip at its end, snapping to steps with a haptic tick on each
@Composable
internal fun StepSlider(
    steps: List<Int>,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val index = steps.indexOf(value).takeIf { it >= 0 } ?: steps.indices.minBy { kotlin.math.abs(steps[it] - value) }
    val last = (steps.size - 1).toFloat()
    // follows the finger exactly while dragging, then springs onto the step it lands on
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val settled by animateFloatAsState(index / last, spring(dampingRatio = 0.8f, stiffness = 420f), label = "sliderFill")
    // the pill swells a touch under the finger, so it feels picked up
    val held by animateFloatAsState(if (dragFraction >= 0f) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "sliderHeld")
    val currentIndex by rememberUpdatedState(index)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val density = LocalDensity.current
    val pillPx = with(density) { SliderHeight.toPx() }

    // where along the pill a finger is, with the grip's half-width kept clear at both ends
    fun fractionAt(x: Float, width: Int): Float = ((x - pillPx / 2f) / (width - pillPx)).coerceIn(0f, 1f)

    fun pick(fraction: Float) {
        val i = (fraction * last).roundToInt()
        if (i != currentIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnChange(steps[i])
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(SliderHeight + 8.dp)
            .pointerInput(steps) {
                detectTapGestures { pick(fractionAt(it.x, size.width)) }
            }
            .pointerInput(steps) {
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = fractionAt(it.x, size.width) },
                    onDragEnd = { dragFraction = -1f },
                    onDragCancel = { dragFraction = -1f },
                ) { change, _ ->
                    dragFraction = fractionAt(change.position.x, size.width)
                    pick(dragFraction)
                }
            }
            .drawBehind {
                val height = pillPx + 6.dp.toPx() * held
                val top = (size.height - height) / 2f
                val centerY = size.height / 2f
                val round = CornerRadius(height / 2f)
                val fraction = if (dragFraction >= 0f) dragFraction else settled
                // never thinner than a circle, so even "no cache" leaves a round end to grab
                val fill = pillPx + fraction * (size.width - pillPx)
                val gripX = fill - pillPx / 2f
                drawRoundRect(StorageTrack, Offset(0f, top), Size(size.width, height), round)
                drawRoundRect(Color.White, Offset(0f, top), Size(fill, height), round)
                // dots on the empty part only, so the stops read without cluttering the fill
                for (i in steps.indices) {
                    val x = pillPx / 2f + i / last * (size.width - pillPx)
                    if (x > gripX + pillPx * 0.5f) drawCircle(Color.White.copy(alpha = 0.35f), radius = 2.dp.toPx(), center = Offset(x, centerY))
                }
                val gripWidth = 3.dp.toPx()
                val gripHeight = height * 0.42f
                drawRoundRect(
                    color = StorageCard.copy(alpha = 0.6f),
                    topLeft = Offset(gripX - gripWidth / 2f, centerY - gripHeight / 2f),
                    size = Size(gripWidth, gripHeight),
                    cornerRadius = CornerRadius(gripWidth / 2f),
                )
            },
    )
}

/** a full-width pill with its label in the middle, danger ones are written in red */
@Composable
internal fun StorageActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(CircleShape)
            .background(StorageCard)
            .clickable(interactionSource = interaction, indication = RemoteFocus, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = storageText(15f, FontWeight.SemiBold, if (danger) StorageDanger else Color.White, -0.4f),
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Text(detail, style = storageText(11f, FontWeight.Medium, StorageMuted, 0f), textAlign = TextAlign.Center)
        }
    }
}
