// TurntableArm.kt

package com.mochyy.mochymusic.ui.player.customize

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.PlayerCoverStyle
import com.mochyy.mochymusic.utils.makeTimeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * The arm, in fractions of the box's width (the record is laid out the same way, see
 * discGeometryFor(TURNTABLE)): its pivot up in the top right corner over a slab the record sits on,
 * a long tube reaching down over the record to a headshell bent off its end. At the song's start the stylus sits in the
 * lead-in by the record's rim; it swings in toward the label as the song plays, so where it sits is
 * how far in the song is. The pieces themselves are drawn in TurntableArt.
 */
private const val PivotX = 0.895f
private const val PivotY = 0.125f

private val Record = discGeometryFor(PlayerCoverStyle.TURNTABLE)
private val RecordX = Record.centerX
private val RecordY = Record.centerYByWidth ?: Record.centerY
private val RecordRadius = Record.diameter / 2f

private fun rad(degrees: Float) = Math.toRadians(degrees.toDouble()).toFloat()
private fun deg(radians: Float) = Math.toDegrees(radians.toDouble()).toFloat()

/** how far the stylus is from the pivot, and how far round from the tube's line the headshell's bend puts it */
private val StylusReach = hypot(ArmLength + ArmStylusAlong * cos(rad(ArmHeadBend)), ArmStylusAlong * sin(rad(ArmHeadBend)))
private val StylusDegrees = deg(atan2(ArmStylusAlong * sin(rad(ArmHeadBend)), ArmLength + ArmStylusAlong * cos(rad(ArmHeadBend))))

/** the tube's angle (screen degrees, 0 = right, 90 = down) that sets the stylus [fraction] of the record's radius out */
private fun tubeDegreesFor(fraction: Float): Float {
    val vx = RecordX - PivotX
    val vy = RecordY - PivotY
    val v = hypot(vx, vy)
    val r = fraction * RecordRadius
    val turn = acos(((StylusReach * StylusReach + v * v - r * r) / (2f * StylusReach * v)).coerceIn(-1f, 1f))
    return deg(atan2(vy, vx)) - deg(turn) - StylusDegrees
}

/** the tube's angle at the song's start (the lead-in, by the rim) and at its end (just short of the run-out) */
private val StartDegrees = tubeDegreesFor(0.94f)
private val EndDegrees = tubeDegreesFor(Record.art + 0.075f)

/** the time arc that shows while the arm is held, just outside the headshell's path */
private const val ArcRadius = 1.03f
/** how long the arc stays up when the music starts */
private const val PeekMs = 3_000L
private const val LabelGapDegrees = 3.5f

/**
 * The Turntable's arm: drawn after the record, follows the song, and is its seek bar. Hold it and
 * swing it (along the record, left and right) to scrub; the arc of the song shows round its path
 * with where it's at, and letting go plays from there.
 *
 * The slab, the arm and the arm's shadow are each drawn once, pointing where the song starts, and
 * only turned about the pivot after that - following the song never redraws them, and the shadow's
 * blur is worked out once.
 */
@Composable
fun TurntableArm(
    /** the player's open and showing: the arm follows the song only then */
    active: Boolean,
    /** the editor and previews: drawn, never dragged */
    interactive: Boolean,
    modifier: Modifier = Modifier,
    /** how far the records are from rest (0 still, 1 a record along): it lifts a little as they slide under it */
    awayProvider: () -> Float = { 0f },
) {
    val connection = LocalPlayerConnection.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val angle = remember { Animatable(StartDegrees) }
    // 0 resting, 1 held: lifts it and its shadow and brings the arc up
    val held = remember { Animatable(0f) }
    val dragging = remember { booleanArrayOf(false) }
    val scrubMs = remember { mutableLongStateOf(-1L) }
    // the arc on its own, for a few seconds when the music starts: [peek] shows it, [peekGrow]
    // draws it along the stylus's path from the song's start (the arm stays down meanwhile)
    val peek = remember { Animatable(0f) }
    val peekGrow = remember { Animatable(0f) }

    LaunchedEffect(connection, active) {
        if (connection == null || !active) return@LaunchedEffect
        var wasPlaying = connection.isPlaying.value
        var lastId = connection.mediaMetadata.value?.id
        var showing: kotlinx.coroutines.Job? = null
        kotlinx.coroutines.flow.combine(
            connection.isPlaying,
            connection.mediaMetadata.map { it?.id }.distinctUntilChanged(),
        ) { playing, id -> playing to id }.collect { (playing, id) ->
            val started = playing && (!wasPlaying || id != lastId)
            wasPlaying = playing
            lastId = id
            if (!started) return@collect
            showing?.cancel()
            showing = launch {
                if (peek.value < 0.05f) peekGrow.snapTo(0f)
                launch { peek.animateTo(1f, tween(240)) }
                peekGrow.animateTo(1f, tween(760, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)))
                delay(PeekMs - 760L)
                peek.animateTo(0f, tween(420))
            }
        }
    }

    // follows the song: small steps snap (it moves a fraction of a degree a second), a new song or
    // a seek swings it there
    LaunchedEffect(connection, active) {
        val flow = connection?.uiState?.progressState ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        flow.collect { progress ->
            if (dragging[0]) return@collect
            val target = angleFor(progress.position, progress.duration)
            if (abs(target - angle.value) > 1.5f) {
                launch { angle.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 120f)) }
            } else if (!angle.isRunning) {
                angle.snapTo(target)
            }
        }
    }

    val failureProgress = com.mochyy.mochymusic.ui.player.rememberStreamFailureProgress()
    val failureLabel by com.mochyy.mochymusic.ui.player.rememberStreamFailureLabel(failureProgress)

    val measurer = rememberTextMeasurer(cacheSize = 16)
    val fontFamily = com.mochyy.mochymusic.ui.theme.InterFontFamily
    val labelStyle = remember(fontFamily) {
        TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = Color.White,
        )
    }
    val currentConnection by rememberUpdatedState(connection)

    // the shadow's soft edge is a blur where the platform has one; before that it's fainter instead
    val softShadow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val density = LocalDensity.current
    val shadowBlur = remember(density, softShadow) {
        if (softShadow) with(density) { BlurEffect(5.dp.toPx(), 5.dp.toPx(), TileMode.Clamp) } else null
    }

    Box(
        modifier = modifier.then(
            if (!interactive) Modifier else Modifier.pointerInput(Unit) {
                val grabPx = 26.dp.toPx()
                awaitEachGesture {
                    val w = size.width.toFloat()
                    val pivot = Offset(w * PivotX, w * PivotY)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!onArm(down.position, pivot, angle.value, w, grabPx)) return@awaitEachGesture
                    val player = currentConnection?.player ?: return@awaitEachGesture
                    val duration = player.duration.takeIf { it > 0 } ?: return@awaitEachGesture
                    down.consume()
                    dragging[0] = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch { angle.stop() }
                    scope.launch { held.animateTo(1f, tween(180)) }
                    // it turns by as much as the finger does round the pivot, from wherever it was taken
                    val grabbed = angle.value - degreesOf(down.position, pivot)
                    var lastTick = -1L
                    scrubMs.longValue = msFor(angle.value, duration)

                    drag(down.id) { change ->
                        change.consume()
                        val next = (degreesOf(change.position, pivot) + grabbed).coerceIn(StartDegrees, EndDegrees)
                        scope.launch { angle.snapTo(next) }
                        val ms = msFor(next, duration)
                        scrubMs.longValue = ms
                        // a tick every ten seconds of song, like grooves under the needle
                        val tick = ms / 10_000L
                        if (tick != lastTick) {
                            if (lastTick >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastTick = tick
                        }
                    }

                    player.seekTo(scrubMs.longValue.coerceIn(0L, duration))
                    dragging[0] = false
                    scope.launch {
                        delay(450)
                        held.animateTo(0f, tween(320))
                        if (!dragging[0]) scrubMs.longValue = -1L
                    }
                }
            }
        )
    ) {
        // its shadow on the record, further off (and fainter) while it's lifted
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    turnAboutPivot(angle.value)
                    val lift = maxOf(held.value, (awayProvider() * 2.5f).coerceAtMost(0.6f))
                    val shift = size.width * (0.012f + 0.024f * lift)
                    translationX = shift * 0.55f
                    translationY = shift * 1.1f
                    alpha = (if (softShadow) 0.8f else 0.42f) - 0.2f * lift
                    renderEffect = shadowBlur
                }
                .drawBehind { drawTonearm(pivotIn(size), StartDegrees, size.width, silhouette = true) }
        )

        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    turnAboutPivot(angle.value)
                    // lifted, it comes a hair closer
                    val s = 1f + 0.012f * maxOf(held.value, (awayProvider() * 2.5f).coerceAtMost(0.6f))
                    scaleX = s
                    scaleY = s
                }
                .drawBehind { drawTonearm(pivotIn(size), StartDegrees, size.width, silhouette = false) }
        )

        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val failing = failureProgress.value >= 0f
                    val lift = if (failing) 1f else maxOf(held.value, peek.value)
                    if (lift > 0.001f) {
                        drawTimeArc(
                            pivot = pivotIn(size),
                            degrees = angle.value,
                            w = size.width,
                            alpha = lift,
                            // held (or failing), it's all there at once; peeking, it draws itself in
                            grow = if (failing || held.value >= peek.value) 1f else peekGrow.value,
                            positionMs = scrubMs.longValue,
                            durationMs = currentConnection?.player?.duration ?: 0L,
                            measurer = measurer,
                            labelStyle = labelStyle,
                            failureProgress = failureProgress.value,
                            failureLabel = failureLabel,
                        )
                    }
                }
        )
    }
}

private fun pivotIn(size: Size) = Offset(size.width * PivotX, size.width * PivotY)

/** the slab the arm stands on: drawn before the record, which sits over it */
@Composable
fun TurntableBase(modifier: Modifier = Modifier) {
    Spacer(modifier.drawBehind { drawArmBase(size.width) })
}

/** the arm's layers are drawn pointing where the song starts: this turns them to [degrees] */
private fun GraphicsLayerScope.turnAboutPivot(degrees: Float) {
    transformOrigin = TransformOrigin(PivotX, if (size.height > 0f) PivotY * size.width / size.height else 0f)
    rotationZ = degrees - StartDegrees
}

/** the tube's angle for a position in the song */
private fun angleFor(position: Long, duration: Long): Float {
    if (duration <= 0L) return StartDegrees
    val f = (position.toFloat() / duration).coerceIn(0f, 1f)
    return StartDegrees + (EndDegrees - StartDegrees) * f
}

private fun msFor(degrees: Float, duration: Long): Long {
    val f = ((degrees - StartDegrees) / (EndDegrees - StartDegrees)).coerceIn(0f, 1f)
    return (duration * f).toLong()
}

private fun unit(degrees: Float): Offset {
    val r = rad(degrees)
    return Offset(cos(r), sin(r))
}

private fun degreesOf(p: Offset, pivot: Offset) = deg(atan2(p.y - pivot.y, p.x - pivot.x))

/** [p] is on the tube or the headshell, give or take [slop] */
private fun onArm(p: Offset, pivot: Offset, degrees: Float, w: Float, slop: Float): Boolean {
    val u = unit(degrees)
    val joint = pivot + u * (w * ArmLength)
    val head = joint + unit(degrees + ArmHeadBend) * (w * ArmHeadLength * 0.5f)
    if (hypot(p.x - head.x, p.y - head.y) < w * ArmHeadLength * 0.5f + slop * 0.5f) return true
    // along the tube, past the block over the pivot so a touch on the slab still belongs to the sheet
    val rel = p - pivot
    val along = rel.x * u.x + rel.y * u.y
    if (along < w * 0.12f || along > w * ArmLength) return false
    return abs(rel.y * u.x - rel.x * u.y) < slop
}

/**
 * The song as an arc round the stylus's path: the part played white, the rest grey, the time at
 * each end turned along it (where the arm is at the start, the song's length at the far end).
 */
private fun DrawScope.drawTimeArc(
    pivot: Offset,
    degrees: Float,
    w: Float,
    alpha: Float,
    /** how much of the arc is drawn, from the song's start: 1 all of it */
    grow: Float = 1f,
    positionMs: Long,
    durationMs: Long,
    measurer: androidx.compose.ui.text.TextMeasurer,
    labelStyle: TextStyle,
    failureProgress: Float = -1f,
    failureLabel: String? = null,
) {
    val radius = w * ArcRadius
    val stroke = 5.dp.toPx()
    val topLeft = pivot - Offset(radius, radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    val start = StartDegrees + StylusDegrees
    val reach = (EndDegrees - StartDegrees) * grow.coerceIn(0f, 1f)
    drawArc(
        color = Color.White.copy(alpha = 0.32f * alpha),
        startAngle = start,
        sweepAngle = reach.coerceAtLeast(0.01f),
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    drawArc(
        color = Color.White.copy(alpha = alpha),
        startAngle = start,
        sweepAngle = (degrees - StartDegrees).coerceIn(0.01f, reach.coerceAtLeast(0.01f)),
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    if (failureProgress > 0f) {
        // the red comes in from the far end of the song, back towards the start
        val total = EndDegrees - StartDegrees
        val red = total * failureProgress.coerceAtMost(1f)
        drawArc(
            color = com.mochyy.mochymusic.ui.player.StreamFailureRed.copy(alpha = alpha),
            startAngle = start + total - red,
            sweepAngle = red.coerceAtLeast(0.01f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
    if (durationMs <= 0L) return
    val shown = if (positionMs >= 0L) positionMs else msFor(degrees, durationMs)
    // just past each end, clear of its round cap
    if (failureLabel != null) {
        timeLabel(
            failureLabel, pivot, start - LabelGapDegrees, radius, alpha, measurer,
            labelStyle.copy(color = com.mochyy.mochymusic.ui.player.StreamFailureRed),
        )
    } else {
        timeLabel(makeTimeString(shown), pivot, start - LabelGapDegrees, radius, alpha, measurer, labelStyle)
    }
    // the length lands at the far end as the arc reaches it
    val endAlpha = alpha * ((grow - 0.7f) / 0.3f).coerceIn(0f, 1f)
    if (endAlpha > 0.001f) {
        timeLabel(makeTimeString(durationMs), pivot, EndDegrees + StylusDegrees + LabelGapDegrees, radius, endAlpha, measurer, labelStyle)
    }
}

private fun DrawScope.timeLabel(
    text: String,
    pivot: Offset,
    degrees: Float,
    radius: Float,
    alpha: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
) {
    val layout = measurer.measure(text, style.copy(color = style.color.copy(alpha = alpha)))
    val at = pivot + unit(degrees) * radius
    // along the arc, reading the right way up
    rotate(degrees - 90f, at) {
        drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
    }
}
