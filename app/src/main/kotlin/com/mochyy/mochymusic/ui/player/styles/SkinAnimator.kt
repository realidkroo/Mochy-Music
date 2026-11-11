// a styled block's motion, all worked out in its own layer each frame: nudges, loops, keyframes on any clock, states blending in and out, event animations, its entrance and its blur

package com.mochyy.mochymusic.ui.player.styles

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.modules.skin.EnterKind
import com.mochyy.mochymusic.modules.skin.PoseProp
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.modules.skin.SkinClock
import com.mochyy.mochymusic.modules.skin.SkinEvent
import com.mochyy.mochymusic.modules.skin.SkinFlag
import com.mochyy.mochymusic.modules.skin.SkinKeyframes
import com.mochyy.mochymusic.modules.skin.SkinRepeat
import com.mochyy.mochymusic.modules.skin.SkinTimeline
import com.mochyy.mochymusic.modules.skin.SkinTrigger
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.media3.common.Player

/** the player's own state the built-in conditions read; only the parts the layout uses are kept up to date */
@Stable
internal class SkinLive {
    var playing by mutableStateOf(false)
    var liked by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeat by mutableStateOf(false)
}

@Composable
internal fun rememberSkinLive(flags: Set<SkinFlag>, preview: Boolean): SkinLive {
    val connection = LocalPlayerConnection.current
    // filled from what's true right now, so a state doesn't animate in on the frame the player opens
    val live = remember(connection) {
        SkinLive().apply {
            val transport = connection?.uiState?.transportState?.value
            playing = transport?.isPlaying == true
            shuffle = transport?.shuffleModeEnabled == true
            repeat = transport != null && transport.repeatMode != Player.REPEAT_MODE_OFF
            liked = connection?.uiState?.trackInfo?.value?.liked == true
        }
    }
    val needsTransport = flags.any { it == SkinFlag.Playing || it == SkinFlag.Paused || it == SkinFlag.Shuffle || it == SkinFlag.Repeat }
    val needsLike = SkinFlag.Liked in flags
    LaunchedEffect(connection, needsTransport, needsLike, preview) {
        if (connection == null || preview) return@LaunchedEffect
        coroutineScope {
            if (needsTransport) launch {
                connection.uiState.transportState.collect {
                    live.playing = it.isPlaying
                    live.shuffle = it.shuffleModeEnabled
                    live.repeat = it.repeatMode != Player.REPEAT_MODE_OFF
                }
            }
            if (needsLike) launch { connection.uiState.trackInfo.collect { live.liked = it.liked } }
        }
    }
    return live
}

/** where the player's events go out to every block listening for one */
@Stable
internal class SkinEvents {
    val flow = MutableSharedFlow<SkinEvent>(extraBufferCapacity = 16)
}

/** the events the layout listens for, worked out from the player and the music; null when nothing listens */
@Composable
internal fun rememberSkinEvents(events: Set<SkinEvent>, pulse: SkinPulse, preview: Boolean): SkinEvents? {
    if (events.isEmpty() || preview) return null
    val hub = remember { SkinEvents() }
    val connection = LocalPlayerConnection.current
    val showing = rememberStyleShowing()
    LaunchedEffect(connection, events) {
        coroutineScope {
            if (SkinEvent.Open in events) launch {
                snapshotFlow { showing() }.distinctUntilChanged().collect { if (it) hub.flow.tryEmit(SkinEvent.Open) }
            }
            if (connection == null) return@coroutineScope
            if (SkinEvent.Song in events) launch {
                connection.mediaMetadata.map { it?.id }.distinctUntilChanged().drop(1).collect { hub.flow.tryEmit(SkinEvent.Song) }
            }
            if (SkinEvent.Play in events || SkinEvent.Pause in events) launch {
                connection.uiState.transportState.map { it.isPlaying }.distinctUntilChanged().drop(1)
                    .collect { hub.flow.tryEmit(if (it) SkinEvent.Play else SkinEvent.Pause) }
            }
            if (SkinEvent.Like in events || SkinEvent.Unlike in events) launch {
                // a new song that happens to be liked isn't someone pressing like, so only a change on the same song counts
                var last: Pair<String?, Boolean>? = null
                combine(connection.mediaMetadata.map { it?.id }, connection.uiState.trackInfo.map { it.liked }) { id, liked -> id to liked }
                    .distinctUntilChanged()
                    .collect { now ->
                        val before = last
                        last = now
                        if (before != null && before.first == now.first && before.second != now.second) {
                            hub.flow.tryEmit(if (now.second) SkinEvent.Like else SkinEvent.Unlike)
                        }
                    }
            }
            if (SkinEvent.Beat in events) launch {
                // a kick lifts the bass past the line, and it has to fall back before the next one counts
                var armed = true
                var lastAt = 0L
                snapshotFlow { pulse.bass }.collect { bass ->
                    val now = System.nanoTime()
                    if (armed && bass > BeatOn && now - lastAt > BeatGapNs) {
                        hub.flow.tryEmit(SkinEvent.Beat)
                        armed = false
                        lastAt = now
                    } else if (bass < BeatOff) {
                        armed = true
                    }
                }
            }
        }
    }
    return hub
}

/** one event animation on a block: how far into it, in ms, and how long it lasts */
@Stable
internal class TriggerRun(val trigger: SkinTrigger) {
    val elapsed = Animatable(0f)
    val totalMs: Float = trigger.timeline.delayMs + trigger.timeline.durationMs * (if (trigger.timeline.repeat == SkinRepeat.PingPong) 2f else 1f)

    suspend fun play() {
        elapsed.snapTo(0f)
        elapsed.animateTo(totalMs, tween(totalMs.toInt().coerceAtLeast(1), easing = LinearEasing))
    }
}

/** what a styled block's layer reads while it draws, plus one pose it adds everything up in, kept so a frame allocates nothing */
@Stable
internal class BlockMotion(
    val enter: () -> Float,
    val weights: List<Animatable<Float, AnimationVector1D>>,
    val runs: List<TriggerRun>,
) {
    val pose = PoseSum()
}

@Composable
internal fun rememberBlockMotion(block: SkinBlock.Styled, context: SkinContext): BlockMotion {
    val enter = rememberEnterProgress(block.enter, context.preview)
    val current by rememberUpdatedState(context)
    // each state starts where it stands now, so opening the player never plays a switch
    val weights = remember(block.states) { block.states.map { Animatable(if (context.holds(it.condition)) 1f else 0f) } }
    val runs = remember(block.triggers) { block.triggers.map(::TriggerRun) }
    if (weights.isNotEmpty()) {
        LaunchedEffect(block.states, block.transition, context.preview) {
            val transition = block.transition
            coroutineScope {
                block.states.forEachIndexed { i, state ->
                    launch {
                        snapshotFlow { current.holds(state.condition) }.distinctUntilChanged().collectLatest { on ->
                            val target = if (on) 1f else 0f
                            when {
                                current.preview || transition == null || transition.durationMs == 0 -> weights[i].snapTo(target)
                                transition.spring -> weights[i].animateTo(target, spring(dampingRatio = 0.62f, stiffness = 260f))
                                else -> weights[i].animateTo(target, tween(transition.durationMs, easing = transition.easing))
                            }
                        }
                    }
                }
            }
        }
    }
    val events = context.events
    if (runs.isNotEmpty() && events != null) {
        LaunchedEffect(runs, events) {
            coroutineScope {
                events.flow.collect { event -> runs.filter { it.trigger.event == event }.forEach { run -> launch { run.play() } } }
            }
        }
    }
    return remember(enter, weights, runs) { BlockMotion(enter, weights, runs) }
}

/** the pose a block ends up in this frame: offsets in dp and turns add, sizes and see-through multiply */
internal class PoseSum {
    var x = 0f
    var y = 0f
    var scaleX = 1f
    var scaleY = 1f
    var rotate = 0f
    var rotateX = 0f
    var rotateY = 0f
    var alpha = 1f
    var blur = 0f

    fun add(prop: PoseProp, value: Float, weight: Float) {
        if (prop.multiplies) {
            val factor = 1f + (value - 1f) * weight
            when (prop) {
                PoseProp.Scale -> {
                    scaleX *= factor
                    scaleY *= factor
                }
                PoseProp.ScaleX -> scaleX *= factor
                PoseProp.ScaleY -> scaleY *= factor
                else -> alpha *= factor
            }
            return
        }
        val v = value * weight
        when (prop) {
            PoseProp.X -> x += v
            PoseProp.Y -> y += v
            PoseProp.Rotate -> rotate += v
            PoseProp.RotateX -> rotateX += v
            PoseProp.RotateY -> rotateY += v
            else -> blur += v
        }
    }
}

private fun SkinKeyframes.addTo(pose: PoseSum, t: Float, fallback: Easing, weight: Float = 1f) {
    for (track in tracks) pose.add(track.prop, track.valueAt(t, fallback), weight)
}

// where a looping animation is on its own clock: time, the song's progress, or how loud the music is now
private fun SkinTimeline.at(context: SkinContext): Float = when (clock) {
    SkinClock.Time -> position((if (always) context.pulse.wall else context.pulse.time) * 1000f)
    SkinClock.Progress -> (context.position.positionMs().toFloat() / context.duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
    SkinClock.Level -> context.pulse.level
    SkinClock.Bass -> context.pulse.bass
    SkinClock.Mid -> context.pulse.mid
    SkinClock.Treble -> context.pulse.treble
}

/** everything a styled block does, in one layer, so its motion never recomposes it */
internal fun Modifier.skinStyled(block: SkinBlock.Styled, context: SkinContext, motion: BlockMotion): Modifier = graphicsLayer {
    val pose = motion.pose
    pose.x = block.x.value
    pose.y = block.y.value
    pose.scaleX = block.scale
    pose.scaleY = block.scale
    pose.rotate = block.rotate
    pose.rotateX = 0f
    pose.rotateY = 0f
    pose.alpha = block.opacity
    pose.blur = block.blur
    for (timeline in block.timelines) timeline.frames.addTo(pose, timeline.at(context), timeline.easing)
    block.states.forEachIndexed { i, state ->
        val weight = motion.weights[i].value
        if (weight > 0.001f) state.values.forEach { (prop, value) -> pose.add(prop, value, weight) }
    }
    for (run in motion.runs) {
        val elapsed = run.elapsed.value
        if (elapsed > 0f && elapsed < run.totalMs) run.trigger.timeline.let { it.frames.addTo(pose, it.position(elapsed), it.easing) }
    }
    val enter = block.enter
    if (enter?.frames != null) {
        val progress = motion.enter()
        if (progress < 1f) enter.frames.addTo(pose, progress, FastOutSlowInEasing)
    }
    translationX = pose.x.dp.toPx()
    translationY = pose.y.dp.toPx()
    scaleX = pose.scaleX
    scaleY = pose.scaleY
    rotationZ = pose.rotate
    rotationX = pose.rotateX
    rotationY = pose.rotateY
    // a tilt seen from further away, so a turned card doesn't stretch toward the camera
    if (pose.rotateX != 0f || pose.rotateY != 0f) cameraDistance = 14f * density
    alpha = pose.alpha.coerceIn(0f, 1f)
    transformOrigin = TransformOrigin(block.originX, block.originY)
    block.motions.forEach { applyMotion(it, context.pulse) }
    if (enter != null && enter.kind != EnterKind.Keyframes) applyEnter(enter.kind, motion.enter())
    // a blur on a layer nobody sees still costs a pass, so it goes when the block is hidden
    renderEffect = if (alpha > 0.01f) blockBlur(pose.blur.dp.toPx()) else null
}

/** a block's own blur; decal so its edges fade out rather than smearing the edge row outward */
internal fun blockBlur(px: Float): RenderEffect? =
    if (px > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) decalBlur(px) else null

@RequiresApi(Build.VERSION_CODES.S)
private fun decalBlur(px: Float): RenderEffect? = BlurEffectCache.get(px, android.graphics.Shader.TileMode.DECAL)

private const val BeatOn = 0.62f
private const val BeatOff = 0.42f
private const val BeatGapNs = 160_000_000L
