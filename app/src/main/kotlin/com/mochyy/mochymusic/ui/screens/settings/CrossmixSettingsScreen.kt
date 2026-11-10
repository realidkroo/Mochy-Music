package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.CrossfadeDurationKey
import com.mochyy.mochymusic.constants.CrossfadeEnabledKey
import com.mochyy.mochymusic.constants.CrossfadeGaplessKey
import com.mochyy.mochymusic.constants.CrossmixAvailable
import com.mochyy.mochymusic.constants.CrossmixMode
import com.mochyy.mochymusic.constants.CrossmixModeKey
import com.mochyy.mochymusic.constants.CrossmixTestToolsKey
import com.mochyy.mochymusic.constants.playable
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabCollapsible
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.LabSlider
import com.mochyy.mochymusic.ui.screens.audiolab.LabStage
import com.mochyy.mochymusic.ui.screens.audiolab.LabToggleRow
import com.mochyy.mochymusic.ui.screens.audiolab.LevelFollower
import com.mochyy.mochymusic.ui.screens.audiolab.ListenToAudio
import com.mochyy.mochymusic.ui.screens.audiolab.rememberLabTime
import com.mochyy.mochymusic.ui.screens.audiolab.rememberOnScreen
import com.mochyy.mochymusic.ui.screens.audiolab.rememberPicturesHeld
import com.mochyy.mochymusic.ui.screens.audiolab.trackOnScreen
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val AutoCrossfadeDurationSeconds = 5f
private const val ManualMinSeconds = 1f
private const val ManualMaxSeconds = 20f

@Composable
fun CrossmixSettingsScreen(navController: NavController) {
    val (storedMode, onStoredModeChange) = rememberEnumPreference(
        CrossmixModeKey,
        defaultValue = CrossmixMode.AUTO_CROSSFADE,
    )
    val (crossfadeEnabled, onCrossfadeEnabledChange) = rememberPreference(
        CrossfadeEnabledKey,
        defaultValue = false,
    )
    val (crossfadeDuration, onCrossfadeDurationChange) = rememberPreference(
        CrossfadeDurationKey,
        defaultValue = AutoCrossfadeDurationSeconds,
    )
    val (disableForGapless, onDisableForGaplessChange) = rememberPreference(
        CrossfadeGaplessKey,
        defaultValue = true,
    )
    val (testTools, _) = rememberPreference(CrossmixTestToolsKey, defaultValue = false)

    // people upgrading from the old crossfade switch land on the equivalent manual mode, and a saved crossmix shows what really plays
    val selectedMode = if (storedMode == CrossmixMode.OFF && crossfadeEnabled) {
        CrossmixMode.MANUAL_CROSSFADE
    } else {
        storedMode.playable()
    }

    fun selectMode(mode: CrossmixMode) {
        onStoredModeChange(mode)
        onCrossfadeEnabledChange(mode != CrossmixMode.OFF)
        if (mode == CrossmixMode.AUTO_CROSSFADE) {
            onCrossfadeDurationChange(AutoCrossfadeDurationSeconds)
        }
    }

    /** the mode switches act as one choice: turning one off falls back to off */
    fun toggle(mode: CrossmixMode, on: Boolean) = selectMode(if (on) mode else CrossmixMode.OFF)

    val crossmixTools = crossmixTestItems(isVisible = testTools && selectedMode == CrossmixMode.CROSSMIX)
    val manualSeconds = crossfadeDuration.coerceIn(ManualMinSeconds, ManualMaxSeconds)

    // the crossmix picture glows with the music
    ListenToAudio()

    SubSettingsScaffold(
        title = stringResource(R.string.xmix_title),
        backLabel = stringResource(R.string.xmix_back),
        onBack = { navController.navigateUp() },
    ) {
        LabCard(padding = 10.dp) {
            LabToggleRow(
                title = stringResource(R.string.xmix_off),
                subtitle = stringResource(R.string.xmix_off_desc),
                checked = selectedMode == CrossmixMode.OFF,
                onCheckedChange = { selectMode(CrossmixMode.OFF) },
                leadingDot = true,
            )
        }
        Spacer(Modifier.height(12.dp))

        LabCard(padding = 14.dp) {
            LabStage(height = 124.dp) {
                TransitionPicture(TransitionStyle.Auto, overlap = 0.3f, active = selectedMode == CrossmixMode.AUTO_CROSSFADE)
            }
            Spacer(Modifier.height(10.dp))
            LabToggleRow(
                title = stringResource(R.string.xmix_auto),
                subtitle = stringResource(R.string.xmix_auto_desc, AutoCrossfadeDurationSeconds.toInt()),
                checked = selectedMode == CrossmixMode.AUTO_CROSSFADE,
                onCheckedChange = { toggle(CrossmixMode.AUTO_CROSSFADE, it) },
            )
        }
        Spacer(Modifier.height(12.dp))

        LabCard(padding = 14.dp) {
            LabStage(height = 124.dp) {
                TransitionPicture(
                    TransitionStyle.Manual,
                    overlap = 0.08f + 0.42f * (manualSeconds - ManualMinSeconds) / (ManualMaxSeconds - ManualMinSeconds),
                    active = selectedMode == CrossmixMode.MANUAL_CROSSFADE,
                )
            }
            Spacer(Modifier.height(10.dp))
            LabToggleRow(
                title = stringResource(R.string.xmix_manual),
                subtitle = stringResource(R.string.xmix_manual_desc),
                checked = selectedMode == CrossmixMode.MANUAL_CROSSFADE,
                onCheckedChange = { toggle(CrossmixMode.MANUAL_CROSSFADE, it) },
            )
            Box(Modifier.padding(horizontal = 6.dp)) {
                LabSlider(
                    label = stringResource(R.string.xmix_length),
                    valueText = "${manualSeconds.roundToInt()}s",
                    value = manualSeconds,
                    onValueChange = { v ->
                        // sliding is choosing: the manual blend at this length
                        if (selectedMode != CrossmixMode.MANUAL_CROSSFADE) selectMode(CrossmixMode.MANUAL_CROSSFADE)
                        onCrossfadeDurationChange(v.roundToInt().toFloat())
                    },
                    valueRange = ManualMinSeconds..ManualMaxSeconds,
                    steps = (ManualMaxSeconds - ManualMinSeconds).toInt() - 1,
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                Text("1s", fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary, modifier = Modifier.weight(1f))
                Text("20s", fontFamily = InterFontFamily, fontSize = 11.sp, color = LabColors.Secondary)
            }
        }
        Spacer(Modifier.height(12.dp))

        LabCard(padding = 14.dp) {
            Box {
                // while it's in progress the picture keeps playing its glow, just dimmed, so it still shows what's coming
                LabStage(height = 124.dp, modifier = Modifier.graphicsLayer { alpha = if (CrossmixAvailable) 1f else 0.55f }) {
                    TransitionPicture(
                        TransitionStyle.CrossMix,
                        overlap = 0.42f,
                        active = selectedMode == CrossmixMode.CROSSMIX || !CrossmixAvailable,
                    )
                }
                if (!CrossmixAvailable) InProgressTag(Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
            Spacer(Modifier.height(10.dp))
            LabToggleRow(
                title = stringResource(R.string.xmix_crossmix),
                subtitle = stringResource(if (CrossmixAvailable) R.string.xmix_crossmix_desc else R.string.xmix_crossmix_desc_unavailable),
                checked = selectedMode == CrossmixMode.CROSSMIX,
                onCheckedChange = { toggle(CrossmixMode.CROSSMIX, it) },
                enabled = CrossmixAvailable,
            )
            LabCollapsible(crossmixTools.any { it.isVisible }) {
                Spacer(Modifier.height(8.dp))
                SettingsGroup(style = SettingsGroupStyle.Grouped, items = crossmixTools)
            }
        }
        Spacer(Modifier.height(12.dp))

        LabCollapsible(selectedMode != CrossmixMode.OFF) {
            LabCard(padding = 10.dp) {
                LabToggleRow(
                    title = stringResource(R.string.xmix_gapless),
                    subtitle = stringResource(R.string.xmix_gapless_desc),
                    checked = disableForGapless,
                    onCheckedChange = onDisableForGaplessChange,
                )
            }
        }

        TransportFadeSection()
        Spacer(Modifier.height(120.dp))
    }
}

private enum class TransitionStyle { Auto, Manual, CrossMix }

/** two songs as waveforms, the old one fading under the new one, with a playhead that lights each song's lyric while it's heard */
@Composable
private fun TransitionPicture(style: TransitionStyle, overlap: Float, active: Boolean) {
    val width by animateFloatAsState(overlap, spring(dampingRatio = 0.85f, stiffness = 200f), label = "overlap")
    val on by animateFloatAsState(if (active) 1f else 0f, tween(400), label = "active")
    val measurer = rememberTextMeasurer()
    val onScreen = rememberOnScreen()
    val held = rememberPicturesHeld(onScreen)
    val time = rememberLabTime(held) { active }
    val level = remember { LevelFollower() }
    val lyricStyle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)

    Canvas(Modifier.fillMaxSize().trackOnScreen(onScreen)) {
        level.update(0.016f)
        val w = size.width
        val h = size.height
        val left = 18.dp.toPx()
        val right = w - 18.dp.toPx()
        val cy = h / 2f
        val half = h * 0.26f
        val mid = (left + right) / 2f
        val o = (right - left) * width
        val fadeStart = mid - o / 2
        val fadeEnd = mid + o / 2
        val playhead = if (on > 0.01f) left + ((time.value * 0.16f) % 1f) * (right - left) else -1f

        fun progress(x: Float) = ((x - fadeStart) / (fadeEnd - fadeStart)).coerceIn(0f, 1f)
        fun outgoing(x: Float): Float {
            val p = progress(x)
            return if (style == TransitionStyle.Manual) 1f - p else cos(p * PI.toFloat() / 2f)
        }
        fun incoming(x: Float): Float {
            val p = progress(x)
            return if (style == TransitionStyle.Manual) p else sin(p * PI.toFloat() / 2f)
        }
        fun wave(x: Float, seed: Float): Float =
            (0.55f + 0.3f * sin(x * 0.11f + seed) + 0.15f * sin(x * 0.37f + seed * 2f)).coerceIn(0.1f, 1f)

        // the two songs as bars, each scaled by its fade
        val step = 3.dp.toPx()
        var x = left
        while (x <= right) {
            val bright = if (playhead >= 0f && x <= playhead) 1f else 0.62f
            if (x <= fadeEnd) {
                val a = wave(x, 0f) * outgoing(x) * half
                if (a > 0.5f) drawLine(Color.White.copy(alpha = 0.5f * bright), Offset(x, cy - a), Offset(x, cy + a), 1.2.dp.toPx())
            }
            if (x >= fadeStart) {
                val a = wave(x, 2.7f) * incoming(x) * half
                if (a > 0.5f) drawLine(Color.White.copy(alpha = 0.5f * bright), Offset(x + step / 2, cy - a), Offset(x + step / 2, cy + a),
                    1.2.dp.toPx())
            }
            x += step
        }

        // crossmix: the meeting point glows, with the music
        if (style == TransitionStyle.CrossMix) {
            val glow = 0.25f + 0.35f * on + 0.3f * level.punch
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = glow), Color.Transparent), center = Offset(mid, cy), radius = o * 0.55f),
                o * 0.55f, Offset(mid, cy),
            )
        }

        // the volume curves, crossing
        fun envelope(f: (Float) -> Float, sign: Float): Path = Path().apply {
            var px = left
            moveTo(px, cy - sign * f(px) * half * 1.15f)
            while (px <= right) {
                lineTo(px, cy - sign * f(px) * half * 1.15f)
                px += 2.dp.toPx()
            }
        }
        val curveColor = Color.White.copy(alpha = 0.55f + 0.45f * on)
        if (style == TransitionStyle.CrossMix) {
            drawPath(envelope(::outgoing, 1f), Color.White.copy(alpha = 0.12f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
            drawPath(envelope(::incoming, 1f), Color.White.copy(alpha = 0.12f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
        }
        drawPath(envelope(::outgoing, 1f), curveColor, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(envelope(::incoming, 1f), curveColor, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(envelope(::outgoing, -1f), curveColor.copy(alpha = curveColor.alpha * 0.5f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
        drawPath(envelope(::incoming, -1f), curveColor.copy(alpha = curveColor.alpha * 0.5f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))

        if (style != TransitionStyle.Manual) {
            // each song's line, lit while it's the one you hear
            val outHeard = if (playhead < 0f) 0.55f else 0.35f + 0.65f * outgoing(playhead)
            val inHeard = if (playhead < 0f) 0.55f else 0.35f + 0.65f * incoming(playhead)
            val a = measurer.measure("i miss you", lyricStyle.copy(color = Color.White.copy(alpha = outHeard)))
            drawText(a, topLeft = Offset(left + (right - left) * 0.12f, cy + half + 6.dp.toPx()))
            val b = measurer.measure("ei i i eii", lyricStyle.copy(color = Color.White.copy(alpha = inHeard)))
            drawText(b, topLeft = Offset(right - b.size.width - (right - left) * 0.06f, cy - half - b.size.height - 6.dp.toPx()))
        }

        if (playhead >= 0f) drawPlayhead(playhead, cy, half * 1.4f, on)
    }
}

private fun DrawScope.drawPlayhead(x: Float, cy: Float, reach: Float, alpha: Float) {
    drawLine(Color.White.copy(alpha = 0.7f * alpha), Offset(x, cy - reach), Offset(x, cy + reach), 1.dp.toPx())
    drawCircle(Color.White.copy(alpha = alpha), 2.5.dp.toPx(), Offset(x, cy - reach))
}

// the little pill on a card whose feature isn't ready yet
@Composable
private fun InProgressTag(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.xmix_in_progress),
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        color = Color.White,
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
