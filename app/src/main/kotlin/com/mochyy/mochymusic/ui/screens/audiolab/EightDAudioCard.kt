package com.mochyy.mochymusic.ui.screens.audiolab

import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.eq.EightDSettings
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.LocalNavAnimatedContentScope
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.viewmodels.AudioLabViewModel
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun EightDAudioCard(viewModel: AudioLabViewModel) {
    val settings by viewModel.eightD.collectAsState()
    val enabled = settings.enabled
    val space = when {
        settings.reverbLevel < 0.4f -> stringResource(R.string.eight_d_room_small)
        settings.reverbLevel < 0.75f -> stringResource(R.string.eight_d_room_medium)
        else -> stringResource(R.string.eight_d_room_large)
    }

    LabCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.eight_d_title),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = LabColors.Primary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(R.string.eight_d_description),
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = LabColors.Secondary,
                )
            }
            IconButton(
                onClick = { viewModel.updateEightD { EightDSettings() } },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.refresh),
                    contentDescription = stringResource(R.string.eight_d_reset),
                    tint = LabColors.Secondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            AppSwitch(
                checked = enabled,
                onCheckedChange = { on -> viewModel.updateEightD { it.copy(enabled = on) } },
            )
        }

        Spacer(Modifier.height(12.dp))
        OrbitIndicator(settings)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = settings.matches(Chill),
                onClick = { viewModel.updateEightD { Chill } },
                label = { Text(stringResource(R.string.eight_d_preset_chill), maxLines = 1) },
            )
            FilterChip(
                selected = settings.matches(Balanced),
                onClick = { viewModel.updateEightD { Balanced } },
                label = { Text(stringResource(R.string.eight_d_preset_balanced), maxLines = 1) },
            )
            FilterChip(
                selected = settings.matches(Extreme),
                onClick = { viewModel.updateEightD { Extreme } },
                label = { Text(stringResource(R.string.eight_d_preset_extreme), maxLines = 1) },
            )
        }
        Spacer(Modifier.height(4.dp))
        LabPanel {
            LabSlider(
                label = stringResource(R.string.eight_d_rotation_speed),
                valueText = stringResource(
                    R.string.eight_d_rotation_value,
                    settings.rotationSpeedHz,
                    1f / settings.rotationSpeedHz,
                ),
                value = settings.rotationSpeedHz,
                onValueChange = { value ->
                    viewModel.updateEightD { it.copy(rotationSpeedHz = value) }
                },
                valueRange = 0.05f..0.5f,
                enabled = enabled,
                steps = 8,
            )
            LabSlider(
                label = stringResource(R.string.eight_d_spatial_depth),
                valueText = pct(settings.depth),
                value = settings.depth,
                onValueChange = { value -> viewModel.updateEightD { it.copy(depth = value) } },
                valueRange = 0f..1f,
                enabled = enabled,
            )
            LabSlider(
                label = stringResource(R.string.eight_d_room_reverb),
                valueText = "${pct(settings.reverbLevel)} · $space",
                value = settings.reverbLevel,
                onValueChange = { value -> viewModel.updateEightD { it.copy(reverbLevel = value) } },
                valueRange = 0f..1f,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun OrbitIndicator(settings: EightDSettings) {
    val angle = remember { mutableFloatStateOf(0f) }
    val obscured = LocalAppContentObscured.current
    val navTransition = LocalNavAnimatedContentScope.current?.transition
    val held = remember(obscured, navTransition) {
        derivedStateOf {
            obscured.value || (navTransition != null &&
                (navTransition.currentState != EnterExitState.Visible || navTransition.targetState != EnterExitState.Visible))
        }
    }
    // turns at the real rotation speed, and holds still while the player covers the page or the page is moving
    LaunchedEffect(settings.enabled, settings.rotationSpeedHz) {
        if (!settings.enabled) {
            angle.floatValue = 0f
            return@LaunchedEffect
        }
        var last = 0L
        while (true) {
            if (held.value) {
                snapshotFlow { held.value }.first { !it }
                last = 0L
            }
            withFrameNanos { now ->
                if (last != 0L) angle.floatValue = (angle.floatValue + (now - last) / 1e9f * 360f * settings.rotationSpeedHz) % 360f
                last = now
            }
        }
    }

    // the angle is only read in draw, so the turning dot repaints this box and never recomposes the card
    Canvas(Modifier.fillMaxWidth().height(148.dp).damageBoundary()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width * 0.34f, size.height * 0.43f)
        val line = LabColors.StageLine
        repeat(3) { index ->
            drawCircle(
                color = line,
                radius = radius * (index + 1) / 3f,
                center = center,
                style = Stroke(1.dp.toPx()),
            )
        }
        drawLine(line, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
        drawLine(line, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())

        val radians = (angle.floatValue - 90f) * PI.toFloat() / 180f
        val orbitRadius = radius * (0.25f + settings.depth * 0.75f)
        val source = Offset(
            center.x + cos(radians) * orbitRadius,
            center.y + sin(radians) * orbitRadius,
        )
        drawLine(
            color = Color.White.copy(alpha = if (settings.enabled) 0.32f else 0.12f),
            start = center,
            end = source,
            strokeWidth = 1.dp.toPx(),
        )
        drawCircle(
            color = Color.White.copy(alpha = if (settings.enabled) 1f else 0.35f),
            radius = 5.dp.toPx(),
            center = source,
        )

        val headsetRadius = radius * 0.22f
        drawArc(
            color = Color.White,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - headsetRadius, center.y - headsetRadius),
            size = Size(headsetRadius * 2f, headsetRadius * 2f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(center.x - headsetRadius, center.y - 2.dp.toPx()),
            size = Size(5.dp.toPx(), 13.dp.toPx()),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(center.x + headsetRadius - 5.dp.toPx(), center.y - 2.dp.toPx()),
            size = Size(5.dp.toPx(), 13.dp.toPx()),
        )
    }
}

private fun EightDSettings.matches(preset: EightDSettings): Boolean =
    enabled && rotationSpeedHz == preset.rotationSpeedHz && depth == preset.depth && reverbLevel == preset.reverbLevel

private val Chill = EightDSettings(enabled = true, rotationSpeedHz = 0.08f, depth = 0.45f, reverbLevel = 0.2f)
private val Balanced = EightDSettings(enabled = true, rotationSpeedHz = 0.15f, depth = 0.85f, reverbLevel = 0.4f)
private val Extreme = EightDSettings(enabled = true, rotationSpeedHz = 0.5f, depth = 1f, reverbLevel = 0.85f)
