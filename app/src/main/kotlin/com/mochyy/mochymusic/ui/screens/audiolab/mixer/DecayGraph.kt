// the decay picture: the effect's real impulse response, measured in the background, with handles for pre-delay and decay

package com.mochyy.mochymusic.ui.screens.audiolab.mixer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.eq.mixer.FxKind
import com.mochyy.mochymusic.eq.mixer.MixerNative
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.LabStage
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

private const val Points = 120
private const val FloorDb = -72f

/** a measured tail: per slice (whole, bass, middle, treble) in db, and the four decay times */
private class Tail(val levels: FloatArray, val seconds: Float) {
    fun level(slice: Int, band: Int) = levels[slice * 4 + band]
    fun decay(band: Int) = levels[Points * 4 + band]
}

private val BassColor = Color(0xFFF2A46B)
private val TrebleColor = Color(0xFF6BBAF2)

@OptIn(FlowPreview::class)
@Composable
fun DecayGraph(
    kind: FxKind,
    source: String,
    values: () -> List<Float>,
    onKnob: (Int, Float) -> Unit,
    accent: Color,
) {
    val decayKnob = kind.knobs.indexOfFirst { it.id == "decay" || it.id == "rt60" || it.id == "time" }
    val preKnob = kind.knobs.indexOfFirst { it.id == "preDelay" || (it.id == "delay" && it.unit == "ms") }
    var tail by remember(kind.key, source) { mutableStateOf<Tail?>(null) }
    // held while a handle is dragged, so the time axis doesn't rescale under the finger
    var heldSeconds by remember { mutableFloatStateOf(0f) }
    val latestValues by rememberUpdatedState(values)

    LaunchedEffect(kind.key, source) {
        snapshotFlow { latestValues() }
            .distinctUntilChanged()
            .debounce(160)
            .collect { knobs ->
                val seconds = heldSeconds.takeIf { it > 0f } ?: spanFor(kind, knobs, decayKnob)
                val levels = withContext(Dispatchers.Default) {
                    runCatching {
                        MixerNative.renderTail(kind.engineType, source, kind.vars.toTypedArray(), knobs.toFloatArray(), RenderRate, seconds, Points)
                    }.getOrNull()
                }
                if (levels != null && levels.size >= Points * 4 + 4) tail = Tail(levels, seconds)
            }
    }

    Column(Modifier.fillMaxWidth()) {
        LabStage(height = 150.dp) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .pointerInput(kind.key, decayKnob, preKnob) {
                        if (decayKnob < 0 && preKnob < 0) return@pointerInput
                        var grabbed = -1
                        detectHorizontalDragGestures(
                            onDragStart = { at ->
                                val t = tail ?: return@detectHorizontalDragGestures
                                heldSeconds = t.seconds
                                val knobs = latestValues()
                                val decayX = if (decayKnob >= 0) xOfSeconds(decaySeconds(kind, knobs, decayKnob), t.seconds, size.width.toFloat()) else Float.MAX_VALUE
                                val preX = if (preKnob >= 0) xOfSeconds(knobs[preKnob] / 1000f, t.seconds, size.width.toFloat()) else Float.MAX_VALUE
                                grabbed = if (abs(at.x - preX) < abs(at.x - decayX)) preKnob else decayKnob
                            },
                            onDragEnd = {
                                heldSeconds = 0f
                                grabbed = -1
                            },
                            onDragCancel = {
                                heldSeconds = 0f
                                grabbed = -1
                            },
                        ) { change, _ ->
                            change.consume()
                            val span = heldSeconds.takeIf { it > 0f } ?: return@detectHorizontalDragGestures
                            val seconds = (change.position.x / size.width).coerceIn(0f, 1f) * span
                            when (grabbed) {
                                preKnob -> onKnob(preKnob, kind.knobs[preKnob].clamp(seconds * 1000f))
                                decayKnob -> onKnob(decayKnob, kind.knobs[decayKnob].clamp(if (kind.knobs[decayKnob].unit == "ms") seconds * 1000f else seconds))
                            }
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                fun y(db: Float) = (db / FloorDb).coerceIn(0f, 1f) * h
                for (db in listOf(-24f, -48f)) {
                    drawLine(LabColors.StageLine, Offset(0f, y(db)), Offset(w, y(db)), strokeWidth = 1.dp.toPx())
                }
                val t = tail ?: return@Canvas
                fun x(slice: Int) = slice / (Points - 1f) * w
                fun line(band: Int): Path = Path().apply {
                    moveTo(0f, y(t.level(0, band)))
                    for (i in 1 until Points) lineTo(x(i), y(t.level(i, band)))
                }
                val whole = line(0)
                val fill = Path().apply {
                    addPath(whole)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.02f))))
                drawPath(line(1), BassColor.copy(alpha = 0.85f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                drawPath(line(3), TrebleColor.copy(alpha = 0.85f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                drawPath(whole, accent, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
                val knobs = latestValues()
                if (preKnob >= 0) handle(xOfSeconds(knobs[preKnob] / 1000f, t.seconds, w), h, accent, solid = false)
                if (decayKnob >= 0) handle(xOfSeconds(decaySeconds(kind, knobs, decayKnob), t.seconds, w), h, accent, solid = true)
            }
        }
        Spacer(Modifier.height(8.dp))
        val measured = tail
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DecayLabel(stringResource(R.string.mixer_decay_whole), measured?.decay(0), accent)
            DecayLabel(stringResource(R.string.mixer_decay_bass), measured?.decay(1), BassColor)
            DecayLabel(stringResource(R.string.mixer_decay_treble), measured?.decay(3), TrebleColor)
        }
    }
}

@Composable
private fun DecayLabel(name: String, seconds: Float?, color: Color) {
    val value = when {
        seconds == null -> "…"
        seconds < 0f -> stringResource(R.string.mixer_decay_long)
        seconds < 1f -> "${(seconds * 1000).toInt()} ms"
        else -> String.format(Locale.ROOT, "%.1f s", seconds)
    }
    Row {
        Text(name, fontFamily = InterFontFamily, fontSize = 12.sp, color = color)
        Spacer(Modifier.padding(start = 5.dp))
        Text(value, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LabColors.Primary)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.handle(x: Float, height: Float, color: Color, solid: Boolean) {
    drawLine(color.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, height), strokeWidth = 1.dp.toPx())
    val r = 7.dp.toPx()
    val at = Offset(x.coerceIn(r, size.width - r), r + 2.dp.toPx())
    drawCircle(Color(0xFF0E0E10), r, at)
    drawCircle(color, r, at, style = Stroke(2.dp.toPx()))
    if (solid) drawCircle(color, r * 0.45f, at)
}

private fun xOfSeconds(seconds: Float, span: Float, width: Float) = (seconds / span).coerceIn(0f, 1f) * width

private fun decaySeconds(kind: FxKind, knobs: List<Float>, index: Int): Float {
    val v = knobs.getOrElse(index) { 0f }
    return if (kind.knobs[index].unit == "ms") v / 1000f else v
}

// long enough to watch a tail fade, short enough to render in a blink
private fun spanFor(kind: FxKind, knobs: List<Float>, decayKnob: Int): Float {
    if (decayKnob < 0) return 2.5f
    val decay = decaySeconds(kind, knobs, decayKnob)
    val repeats = if (kind.knobs[decayKnob].unit == "ms") 8f else 1.4f
    return (decay * repeats + 0.4f).coerceIn(0.6f, 24f)
}

// the decay picture doesn't need hi-res: 24 khz shows every tail at a quarter of the work of 96
private const val RenderRate = 24_000
