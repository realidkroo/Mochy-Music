// the mixer's strips: a fader and meter per bus, pan, mute and solo, and the routes between them drawn as cables

package com.mochyy.mochymusic.ui.screens.audiolab.mixer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.eq.mixer.MixBus
import com.mochyy.mochymusic.eq.mixer.MixerNative
import com.mochyy.mochymusic.eq.mixer.MixerState
import com.mochyy.mochymusic.eq.mixer.MixerStore
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val StripWidth = 80.dp
private val StripGap = 10.dp
private val FaderHeight = 220.dp
private val CableRoom = 54.dp

private val MeterGreen = Color(0xFF8FD98B)
private val MeterYellow = Color(0xFFF2D46B)
private val MeterRed = Color(0xFFF26B6B)

/** the fader's travel: the top quarter is 0 to +12 db, the rest falls off on a curve to -60, so the useful range gets most of the length */
fun faderPosition(db: Float): Float = if (db >= 0f) 0.75f + (db / MixerStore.MaxGainDb).coerceAtMost(1f) * 0.25f
else 0.75f * (1f - sqrt((-db / -MixerStore.MinGainDb).coerceIn(0f, 1f)))

fun faderDb(position: Float): Float {
    val p = position.coerceIn(0f, 1f)
    if (p >= 0.75f) return (p - 0.75f) / 0.25f * MixerStore.MaxGainDb
    val r = 1f - p / 0.75f
    return MixerStore.MinGainDb * r * r
}

@Composable
fun MixerStrips(
    state: MixerState,
    meters: MixerMeterState,
    store: MixerStore,
    onOpen: (Int) -> Unit,
) {
    val all = state.buses + state.master
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(StripGap), modifier = Modifier.padding(horizontal = 4.dp)) {
                for (bus in all) {
                    androidx.compose.runtime.key(bus.id) {
                        BusStrip(bus, meters, store, onOpen = { onOpen(bus.id) })
                    }
                }
                if (state.buses.size < MixerNative.MaxBuses) AddBusTile(onAdd = store::addBus)
            }
            Cables(all, Modifier.padding(horizontal = 4.dp).height(CableRoom))
        }
    }
}

@Composable
private fun BusStrip(bus: MixBus, meters: MixerMeterState, store: MixerStore, onOpen: () -> Unit) {
    val accent = busColor(bus)
    val label = busLabel(bus)
    val meterIndex = if (bus.isMaster) MixerNative.Master else bus.id
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(StripWidth)
            .clip(RoundedCornerShape(22.dp))
            .background(LabColors.Row)
            .padding(vertical = 10.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(2.dp, accent, CircleShape)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (bus.isMaster) "M" else "${bus.id + 1}", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LabColors.Primary)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LabColors.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp).clickable(onClick = onOpen))
        Text(
            pluralStringResource(R.plurals.mixer_effect_count, bus.slots.size, bus.slots.size),
            fontFamily = InterFontFamily,
            fontSize = 11.sp,
            color = accent,
            maxLines = 1,
            modifier = Modifier.clickable(onClick = onOpen),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Fader(bus.gainDb, accent, stringResource(R.string.mixer_fader_label, label)) { store.setGain(bus.id, it) }
            Spacer(Modifier.width(6.dp))
            StereoMeter(meters, meterIndex, Modifier.width(14.dp).height(FaderHeight))
        }
        Spacer(Modifier.height(8.dp))
        Text(dbText(bus.gainDb), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LabColors.Primary)
        Spacer(Modifier.height(8.dp))
        PanDial(bus.pan, accent, stringResource(R.string.mixer_pan_label, label)) { store.setPan(bus.id, it) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundToggle(stringResource(R.string.mixer_mute), stringResource(R.string.mixer_mute_label), bus.mute, MeterRed) { store.setMute(bus.id, !bus.mute) }
            if (!bus.isMaster) RoundToggle(stringResource(R.string.mixer_solo), stringResource(R.string.mixer_solo_label), bus.solo, MeterYellow) { store.setSolo(bus.id, !bus.solo) }
        }
        Spacer(Modifier.height(8.dp))
        if (bus.isMaster) MasterLoudness(meters) else SongPill(bus.songIn, accent) { store.setSongIn(bus.id, !bus.songIn) }
    }
}

@Composable
private fun Fader(db: Float, accent: Color, description: String, onChange: (Float) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val latest by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(db)
    val grab = remember { mutableFloatStateOf(0f) }
    Canvas(
        Modifier
            .width(26.dp)
            .height(FaderHeight)
            .semantics {
                contentDescription = description
                stateDescription = dbText(db)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragStart = { grab.floatValue = faderPosition(current) }) { change, dy ->
                    change.consume()
                    val before = faderPosition(current)
                    grab.floatValue = (grab.floatValue - dy / size.height).coerceIn(0f, 1f)
                    var next = faderDb(grab.floatValue)
                    // unity is sticky, so it's easy to land on exactly 0 db
                    if (abs(next) < 0.6f) next = 0f
                    if ((before < 0.75f) != (faderPosition(next) < 0.75f) || (next == 0f && current != 0f)) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    latest(next)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    latest(0f)
                })
            },
    ) {
        val w = size.width
        val h = size.height
        val track = 6.dp.toPx()
        val x = w / 2
        val thumbH = 16.dp.toPx()
        fun y(p: Float) = thumbH / 2 + (1f - p) * (h - thumbH)
        for (mark in listOf(12f, 6f, 0f, -6f, -12f, -24f, -48f)) {
            val my = y(faderPosition(mark))
            drawLine(LabColors.Faint.copy(alpha = if (mark == 0f) 0.9f else 0.45f), Offset(0f, my), Offset(w * 0.22f, my), strokeWidth = 1.dp.toPx())
            drawLine(LabColors.Faint.copy(alpha = if (mark == 0f) 0.9f else 0.45f), Offset(w * 0.78f, my), Offset(w, my), strokeWidth = 1.dp.toPx())
        }
        val r = CornerRadius(track / 2, track / 2)
        drawRoundRect(LabColors.Stage, Offset(x - track / 2, 0f), Size(track, h), r)
        val ty = y(faderPosition(db))
        drawRoundRect(accent.copy(alpha = 0.85f), Offset(x - track / 2, ty), Size(track, h - ty), r)
        drawRoundRect(Color(0xFF3A3A40), Offset(0f, ty - thumbH / 2), Size(w, thumbH), CornerRadius(5.dp.toPx(), 5.dp.toPx()))
        drawLine(accent, Offset(w * 0.2f, ty), Offset(w * 0.8f, ty), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** two segmented bars, left and right, with a held peak line; drawn from the meter arrays, so a moving level never recomposes */
@Composable
fun StereoMeter(meters: MixerMeterState, index: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        meters.tick.longValue
        val gap = 2.dp.toPx()
        val bar = (size.width - gap) / 2
        val segment = 3.dp.toPx()
        val pitch = segment + 1.dp.toPx()
        fun y(db: Float) = size.height * (1f - ((db - MixerMeterState.Floor) / (MeterTopDb - MixerMeterState.Floor)).coerceIn(0f, 1f))
        for (side in 0..1) {
            val x = side * (bar + gap)
            val level = if (side == 0) meters.levelL[index] else meters.levelR[index]
            val hold = if (side == 0) meters.holdL[index] else meters.holdR[index]
            var top = size.height - segment
            val litFrom = y(level)
            while (top > 0f) {
                val db = MixerMeterState.Floor + (1f - top / size.height) * (MeterTopDb - MixerMeterState.Floor)
                val color = when {
                    db > 0f -> MeterRed
                    db > -6f -> MeterYellow
                    else -> MeterGreen
                }
                drawRect(if (top >= litFrom) color else LabColors.Stage, Offset(x, top), Size(bar, segment))
                top -= pitch
            }
            if (hold > MixerMeterState.Floor + 1f) {
                val hy = y(hold)
                drawRect(if (hold > 0f) MeterRed else LabColors.Primary, Offset(x, hy), Size(bar, 1.5.dp.toPx()))
            }
        }
    }
}

private const val MeterTopDb = 6f

@Composable
private fun PanDial(pan: Float, accent: Color, description: String, onChange: (Float) -> Unit) {
    val latest by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(pan)
    Canvas(
        Modifier
            .size(34.dp)
            .semantics {
                contentDescription = description
                stateDescription = panText(pan)
            }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    // sideways or up and down both turn it, whichever the thumb finds easier
                    val delta = (drag.x - drag.y) / (size.width * 4f)
                    var next = (current + delta).coerceIn(-1f, 1f)
                    if (abs(next) < 0.03f) next = 0f
                    latest(next)
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { latest(0f) }) },
    ) {
        val stroke = 3.dp.toPx()
        val inset = stroke
        drawArc(LabColors.StageLine, 135f, 270f, false, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        val sweep = 135f * pan
        if (abs(sweep) > 0.5f) drawArc(accent, 270f, sweep, false, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        val angle = Math.toRadians((270f + 135f * pan).toDouble())
        val r = size.minDimension / 2 - inset * 2.5f
        drawCircle(Color.White, 2.5.dp.toPx(), Offset(center.x + cos(angle).toFloat() * r, center.y + sin(angle).toFloat() * r))
    }
}

@Composable
private fun RoundToggle(text: String, description: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (on) color else LabColors.Chip)
            .semantics {
                contentDescription = description
                stateDescription = if (on) "on" else "off"
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (on) Color.Black else LabColors.Secondary)
    }
}

@Composable
private fun SongPill(on: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (on) accent.copy(alpha = 0.22f) else LabColors.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.mixer_takes_song), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (on) accent else LabColors.Faint)
    }
}

@Composable
private fun MasterLoudness(meters: MixerMeterState) {
    // a few times a second is plenty for a number
    val shortTerm by remember { androidx.compose.runtime.derivedStateOf { (meters.tick.longValue / 8).let { meters.loudness[1] } } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(lufsText(shortTerm), fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LabColors.Primary)
        Text("LUFS", fontFamily = InterFontFamily, fontSize = 10.sp, color = LabColors.Secondary)
    }
}

@Composable
private fun AddBusTile(onAdd: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(StripWidth)
            .height(FaderHeight + 200.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, LabColors.StageLine, RoundedCornerShape(22.dp))
            .clickable(onClick = onAdd),
    ) {
        Icon(painterResource(R.drawable.add), null, tint = LabColors.Primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.mixer_add_bus), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LabColors.Primary)
    }
}

/** each route as a cable hanging under the strips, from the bus that sends to the one that takes it, as thick as it's loud */
@Composable
private fun Cables(all: List<MixBus>, modifier: Modifier) {
    val count = all.size
    Canvas(modifier.width(StripWidth * (count + 1) + StripGap * count)) {
        val pitch = (StripWidth + StripGap).toPx()
        fun x(index: Int) = index * pitch + StripWidth.toPx() / 2
        val indexOf = all.withIndex().associate { (i, b) -> b.id to i }
        for ((i, bus) in all.withIndex()) {
            if (bus.isMaster) continue
            for ((to, level) in bus.sends) {
                if (level <= 0f) continue
                val j = indexOf[to] ?: continue
                val from = Offset(x(i), 0f)
                val dest = Offset(x(j), 0f)
                val sag = (size.height * 0.35f + abs(j - i) * 6.dp.toPx()).coerceAtMost(size.height - 4.dp.toPx())
                val path = Path().apply {
                    moveTo(from.x, from.y)
                    cubicTo(from.x, sag, dest.x, sag, dest.x, dest.y)
                }
                drawPath(path, busColor(bus).copy(alpha = 0.35f + 0.6f * level), style = Stroke((1.5f + 2.5f * level) * density, cap = StrokeCap.Round))
            }
        }
    }
}

fun dbText(db: Float): String = when {
    db <= MixerStore.MinGainDb + 0.05f -> "−∞"
    db > 0f -> String.format(Locale.ROOT, "+%.1f", db)
    else -> String.format(Locale.ROOT, "%.1f", db).replace('-', '−')
}

fun lufsText(lufs: Float): String = if (lufs < -150f) "–" else String.format(Locale.ROOT, "%.1f", lufs).replace('-', '−')

private fun panText(pan: Float): String = when {
    abs(pan) < 0.01f -> "C"
    pan < 0f -> "L${(-pan * 100).roundToInt()}"
    else -> "R${(pan * 100).roundToInt()}"
}
