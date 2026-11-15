package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.TransportEffect
import com.mochyy.mochymusic.constants.TransportEffectKey
import com.mochyy.mochymusic.constants.TransportFadeDefaultSeconds
import com.mochyy.mochymusic.constants.SkipBlendKey
import com.mochyy.mochymusic.constants.SkipBlendAheadKey
import com.mochyy.mochymusic.constants.SkipBlendAheadMax
import com.mochyy.mochymusic.constants.TransportFadeEnabledKey
import com.mochyy.mochymusic.constants.TransportFadeMaxSeconds
import com.mochyy.mochymusic.constants.TransportFadeMinSeconds
import com.mochyy.mochymusic.constants.TransportFadeSecondsKey
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabCollapsible
import com.mochyy.mochymusic.ui.screens.audiolab.LabSelect
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.LabSlider
import com.mochyy.mochymusic.ui.screens.audiolab.LabStage
import com.mochyy.mochymusic.ui.screens.audiolab.LabToggleRow
import com.mochyy.mochymusic.ui.screens.audiolab.rememberLabTime
import com.mochyy.mochymusic.ui.screens.audiolab.rememberOnScreen
import com.mochyy.mochymusic.ui.screens.audiolab.rememberPicturesHeld
import com.mochyy.mochymusic.ui.screens.audiolab.trackOnScreen
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberDataSaverActive
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private val EffectRed = LabColors.Nodes[9]

// the pause, play, forward and backward fades: a picture of the curve, the switch, its length and the layered effect
@Composable
fun TransportFadeSection() {
    val (enabled, onEnabledChange) = rememberPreference(TransportFadeEnabledKey, defaultValue = true)
    val (storedSeconds, onSecondsChange) = rememberPreference(TransportFadeSecondsKey, defaultValue = TransportFadeDefaultSeconds)
    val (effect, onEffectChange) = rememberEnumPreference(TransportEffectKey, defaultValue = TransportEffect.OFF)
    val (blendSkips, onBlendSkipsChange) = rememberPreference(SkipBlendKey, defaultValue = false)
    val (blendAhead, onBlendAheadChange) = rememberPreference(SkipBlendAheadKey, defaultValue = 1)
    val dataSaving = rememberDataSaverActive()
    val seconds = storedSeconds.coerceIn(TransportFadeMinSeconds, TransportFadeMaxSeconds)

    TransitionHeading(stringResource(R.string.transition_transport_heading), Modifier.padding(top = 28.dp))

    LabCard(padding = 14.dp) {
        LabStage(height = 150.dp) {
            FadeCurvePicture(enabled = enabled, seconds = seconds, effect = if (enabled) effect else TransportEffect.OFF)
        }
        Spacer(Modifier.height(10.dp))
        LabToggleRow(
            title = stringResource(R.string.transition_fade_title),
            subtitle = stringResource(R.string.transition_fade_desc),
            checked = enabled,
            onCheckedChange = onEnabledChange,
        )
        LabToggleRow(
            title = androidx.compose.ui.res.stringResource(com.mochyy.mochymusic.R.string.settings_blend_skips),
            subtitle = stringResource(R.string.settings_blend_skips_desc),
            checked = blendSkips,
            onCheckedChange = onBlendSkipsChange,
        )
        LabCollapsible(blendSkips) {
            BlendAheadRow(ahead = blendAhead, dataSaving = dataSaving, onAheadChange = onBlendAheadChange)
        }
        // blended skips cross over in this length too, so it stays out while either is on
        LabCollapsible(enabled || blendSkips) {
            Box(Modifier.padding(horizontal = 6.dp)) {
                LabSlider(
                    label = "Length",
                    valueText = secondsText(seconds),
                    value = seconds,
                    onValueChange = { onSecondsChange((it * 2f).roundToInt() / 2f) },
                    valueRange = TransportFadeMinSeconds..TransportFadeMaxSeconds,
                    steps = ((TransportFadeMaxSeconds - TransportFadeMinSeconds) * 2f).toInt() - 1,
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                Text(secondsText(TransportFadeMinSeconds), fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary, modifier = Modifier.weight(1f))
                Text(secondsText(TransportFadeMaxSeconds), fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary)
            }
        }
        LabCollapsible(enabled) {
            Spacer(Modifier.height(14.dp))
            Column(Modifier.padding(horizontal = 6.dp)) {
                Text("Layered effect", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = LabColors.Primary)
                Text(
                    "A CrossMix effect over the fade, like an echo trail or the record spinning back",
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = LabColors.Secondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            LabSelect(
                options = TransportEffect.entries.map { it.label },
                selectedIndex = effect.ordinal,
                onSelect = { onEffectChange(TransportEffect.entries[it]) },
                contentDescription = "Choose an effect",
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

// how many songs coming up are loaded for blend skips; data saver holds it at one and says so
@Composable
private fun BlendAheadRow(ahead: Int, dataSaving: Boolean, onAheadChange: (Int) -> Unit) {
    val shown = if (dataSaving) 1 else ahead.coerceIn(1, SkipBlendAheadMax)
    Column(Modifier.padding(horizontal = 6.dp).padding(bottom = 6.dp)) {
        LabSlider(
            label = stringResource(R.string.settings_blend_ahead),
            valueText = pluralStringResource(R.plurals.settings_blend_ahead_value, shown, shown),
            value = shown.toFloat(),
            onValueChange = { onAheadChange(it.roundToInt().coerceIn(1, SkipBlendAheadMax)) },
            valueRange = 1f..SkipBlendAheadMax.toFloat(),
            steps = SkipBlendAheadMax - 2,
            enabled = !dataSaving,
        )
        Text(
            text = stringResource(if (dataSaving) R.string.settings_blend_ahead_saver else R.string.settings_blend_ahead_desc),
            fontFamily = InterFontFamily,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = LabColors.Secondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun secondsText(seconds: Float): String =
    if (seconds % 1f == 0f) "${seconds.toInt()}s" else "${"%.1f".format(seconds)}s"

// the white line is the volume through a fade, the red one the effect riding on it; off, the fade is a hard cut
@Composable
private fun FadeCurvePicture(enabled: Boolean, seconds: Float, effect: TransportEffect) {
    val softness by animateFloatAsState(if (enabled) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 260f), label = "softness")
    val span by animateFloatAsState(
        0.12f + 0.5f * (seconds - TransportFadeMinSeconds) / (TransportFadeMaxSeconds - TransportFadeMinSeconds),
        spring(dampingRatio = 0.85f, stiffness = 220f),
        label = "span",
    )
    val redOn by animateFloatAsState(if (effect != TransportEffect.OFF) 1f else 0f, tween(380), label = "red")
    // a new effect draws its red line in over the old one
    var shown by remember { mutableStateOf(effect) }
    var previous by remember { mutableStateOf(effect) }
    val swap = remember { Animatable(1f) }
    LaunchedEffect(effect) {
        if (effect == shown) return@LaunchedEffect
        if (effect != TransportEffect.OFF) {
            previous = shown
            shown = effect
            swap.snapTo(0f)
            swap.animateTo(1f, tween(420))
        }
    }
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    val time = rememberLabTime(held) { enabled }

    Canvas(Modifier.fillMaxSize().trackOnScreen(onScreen)) {
        val left = 24.dp.toPx()
        val right = size.width - 24.dp.toPx()
        val top = size.height * 0.27f
        val bottom = size.height * 0.72f
        val width = right - left
        val fadeWidth = width * span
        val fadeStart = left + (width - fadeWidth) * 0.48f

        fun u(x: Float) = (x - fadeStart) / fadeWidth
        fun white(x: Float): Float {
            val t = u(x).coerceIn(0f, 1f)
            val step = if (u(x) >= 0.5f) 1f else 0f
            return softness * smooth(t) + (1f - softness) * step
        }
        fun yOf(level: Float) = top + (bottom - top) * level

        val stroke = 1.6.dp.toPx()
        val nudge = Offset(7.dp.toPx() * redOn, -10.dp.toPx() * redOn)

        // the red line first, so the white volume line reads on top where they meet
        if (redOn > 0.01f) {
            val oldAlpha = (1f - swap.value) * redOn
            if (oldAlpha > 0.01f && previous != TransportEffect.OFF) {
                drawCurve(left, right, nudge, EffectRed.copy(alpha = oldAlpha), stroke) { yOf(redShape(previous, u(it), white(it))) }
            }
            drawCurve(left, right, nudge, EffectRed.copy(alpha = redOn * swap.value), stroke) { yOf(redShape(shown, u(it), white(it))) }
        }
        drawCurve(left, right, Offset.Zero, Color.White, stroke) { yOf(white(it)) }

        // a playhead riding the fade, with the red layer's own dot beside it
        if (enabled) {
            val loop = 1.4f + seconds.coerceAtMost(4f) * 0.35f
            val p = (time.value % loop) / loop
            val x = left + width * p
            val y = yOf(white(x))
            drawLine(Color.White.copy(alpha = 0.14f), Offset(x, top - 14.dp.toPx()), Offset(x, bottom + 14.dp.toPx()), 1.dp.toPx())
            drawCircle(Color.White, 3.dp.toPx(), Offset(x, y))
            if (redOn > 0.01f) {
                val ry = yOf(redShape(shown, u(x), white(x)))
                drawCircle(EffectRed.copy(alpha = redOn), 2.5.dp.toPx(), Offset(x, ry) + nudge)
            }
        }
    }
}

private fun DrawScope.drawCurve(left: Float, right: Float, offset: Offset, color: Color, width: Float, y: (Float) -> Float) {
    val path = Path()
    var x = left
    path.moveTo(x + offset.x, y(x) + offset.y)
    val step = 1.5.dp.toPx()
    while (x < right) {
        x = (x + step).coerceAtMost(right)
        path.lineTo(x + offset.x, y(x) + offset.y)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
}

// how each effect bends the red line away from the plain fade, in 0..1 of the drop
private fun redShape(effect: TransportEffect, u: Float, plain: Float): Float {
    val t = u.coerceIn(0f, 1f)
    return when (effect) {
        TransportEffect.OFF -> plain
        // repeats that bounce back up after the drop and die away
        TransportEffect.ECHO -> {
            val after = (u - 0.55f).coerceAtLeast(0f)
            plain - 0.22f * exp(-3.2f * after) * abs(sin(after * 9f * PI.toFloat())) * if (u > 0.55f) 1f else 0f
        }
        // pulls back up before it spins down
        TransportEffect.REWIND -> plain - 0.2f * bump(u, 0.42f, 0.16f)
        // slow at first, then falling faster as the tape stops
        TransportEffect.TAPE_STOP -> t * t * t
        // sinks early and drifts down slowly, like moving off into the distance
        TransportEffect.FAR_AWAY -> 0.35f * smooth((u * 2.2f).coerceIn(0f, 1f)) + 0.65f * smooth(((u - 0.2f) / 1.1f).coerceIn(0f, 1f))
        // a long soft tail that hangs on after the drop
        TransportEffect.REVERB -> plain * 0.8f + 0.2f * smooth(((u - 0.3f) / 1.2f).coerceIn(0f, 1f))
        // the resonance swelling just before it closes
        TransportEffect.FILTER -> plain - 0.16f * bump(u, 0.36f, 0.1f)
    }
}

private fun bump(u: Float, at: Float, width: Float): Float {
    val d = (u - at) / width
    return exp(-d * d)
}

private fun smooth(x: Float): Float = x * x * (3f - 2f * x)
