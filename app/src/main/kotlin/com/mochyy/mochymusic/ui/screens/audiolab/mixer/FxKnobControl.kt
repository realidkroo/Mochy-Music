// one effect knob: drag up or down to turn it, double tap to put it back where it started

package com.mochyy.mochymusic.ui.screens.audiolab.mixer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.eq.mixer.FxKnob
import com.mochyy.mochymusic.eq.mixer.KnobScale
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// the knob turns through 270 degrees, gap at the bottom like a hardware one
private const val SweepDegrees = 270f
private const val StartDegrees = 135f

@Composable
fun FxKnobControl(
    knob: FxKnob,
    label: String,
    value: () -> Float,
    onChange: (Float) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
) {
    val haptics = LocalHapticFeedback.current
    val latest by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(value)
    // the fraction under the finger, kept apart from the rounded value so a stepped knob still moves smoothly
    val dragFraction = remember(knob) { mutableFloatStateOf(-1f) }
    val shown = value()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(size + 22.dp)
            .semantics {
                contentDescription = label
                stateDescription = formatKnob(knob, shown)
            },
    ) {
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = LabColors.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .size(size)
                .pointerInput(knob) {
                    // a full turn is about two and a half knob heights of drag
                    val travel = this.size.height * 2.5f
                    detectVerticalDragGestures(
                        onDragStart = { dragFraction.floatValue = knob.toFraction(current()) },
                        onDragEnd = { dragFraction.floatValue = -1f },
                        onDragCancel = { dragFraction.floatValue = -1f },
                    ) { change, dy ->
                        change.consume()
                        val next = (dragFraction.floatValue - dy / travel).coerceIn(0f, 1f)
                        dragFraction.floatValue = next
                        val v = knob.fromFraction(next)
                        if (v != current()) {
                            if (knob.scale == KnobScale.Int || knob.scale == KnobScale.Choice) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            latest(v)
                        }
                    }
                }
                .pointerInput(knob) {
                    detectTapGestures(onDoubleTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        latest(knob.default)
                    })
                },
        ) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(LabColors.StageLine, StartDegrees, SweepDegrees, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val fraction = knob.toFraction(value())
            // a knob centred on zero (pan, gain) fills out from the middle
            val zero = if (knob.min < 0f && knob.max > 0f) knob.toFraction(0f) else 0f
            val from = StartDegrees + SweepDegrees * minOf(zero, fraction)
            val sweep = SweepDegrees * abs(fraction - zero)
            if (sweep > 0.5f) drawArc(accent, from, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val radius = this.size.minDimension / 2 - inset - stroke * 2.2f
            drawCircle(Color(0xFF2A2A2F), radius)
            val angle = Math.toRadians((StartDegrees + SweepDegrees * fraction).toDouble())
            val tip = Offset(center.x + cos(angle).toFloat() * radius * 0.78f, center.y + sin(angle).toFloat() * radius * 0.78f)
            drawLine(Color.White, center + (tip - center) * 0.35f, tip, strokeWidth = 2.6.dp.toPx(), cap = StrokeCap.Round)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatKnob(knob, shown),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = LabColors.Primary,
            maxLines = 1,
        )
    }
}

/** the knob's value as people read it: whole steps without decimals, kilohertz past a thousand, one decimal where it matters */
fun formatKnob(knob: FxKnob, value: Float): String {
    if (knob.scale == KnobScale.Choice) return knob.choices.getOrNull(value.roundToInt()) ?: value.roundToInt().toString()
    if (knob.scale == KnobScale.Int) return "${value.roundToInt()}${unitSuffix(knob.unit)}"
    val unit = knob.unit
    if (unit == "Hz" && abs(value) >= 1000f) return String.format(Locale.ROOT, if (abs(value) >= 10000f) "%.1f kHz" else "%.2f kHz", value / 1000f)
    if (unit == "s" && abs(value) < 1f) return "${(value * 1000f).roundToInt()} ms"
    val span = knob.max - knob.min
    val text = when {
        span >= 100f || abs(value) >= 100f -> value.roundToInt().toString()
        span >= 10f -> String.format(Locale.ROOT, "%.1f", value)
        else -> String.format(Locale.ROOT, "%.2f", value)
    }
    return text + unitSuffix(unit)
}

private fun unitSuffix(unit: String) = when {
    unit.isEmpty() -> ""
    unit == "%" || unit == "°" || unit == "×" || unit.startsWith(":") -> unit
    else -> " $unit"
}
