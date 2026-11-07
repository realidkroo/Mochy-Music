package com.mochyy.mochymusic.ui.player.video

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// what the right pane holds
internal enum class VideoPane { LYRICS, QUEUE }

// a double tap's jump: which way, how far all the taps in a row add up to, and when, so a repeat restarts its flash
@Immutable
internal class SeekFlash(val forward: Boolean, val seconds: Int, val atMs: Long)

// double taps closer together than this add up into one jump on screen
private const val SeekChainMs = 900L

// the pane glides open on this curve: a quick start that settles softly, no bounce over the video
internal val PaneEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val PaneOpenMs = 620
private const val PaneCloseMs = 480

// the sizes everything in the frame is laid out from, all in dp; stood upright there's no room beside the video, so the pane covers it
@Stable
internal class VideoFrame(val width: Dp, val height: Dp) {
    val upright: Boolean = height > width

    // the lyrics pane takes this much of the right side, or the whole screen upright
    val paneWidth: Dp = if (upright) width else (width * 0.4f).coerceIn(280.dp, 420.dp)

    // the video moves left by part of the pane, so more of the picture stays in view beside it
    val videoSlide: Dp = if (upright) 0.dp else paneWidth * 0.4f

    // the bottom line of lyrics is as wide as the pane beside the video, or the screen less its margins upright
    val lineWidth: Dp = if (upright) (width - 30.dp).coerceAtLeast(120.dp) else paneWidth

    // how far the controls keep from the left and right edges
    val edgeStart: Dp = if (upright) 20.dp else 48.dp
    val edgeEnd: Dp = if (upright) 20.dp else 54.dp
}

@Stable
internal class VideoModeState(private val scope: CoroutineScope) {
    // 0 with the pane away, 1 with it fully open; follows the finger while it's dragged
    val pane = Animatable(0f)

    var paneContent by mutableStateOf(VideoPane.LYRICS)

    // the pane's list is built: from the moment it starts opening until it has fully closed
    var paneMounted by mutableStateOf(false)
        private set

    var paneDragging by mutableStateOf(false)

    var controlsShown by mutableStateOf(false)

    var menuOpen by mutableStateOf(false)

    // the song info sheet: artists, album, about the song, the stream
    var songInfoOpen by mutableStateOf(false)

    var seekFlash by mutableStateOf<SeekFlash?>(null)

    // the last time a finger touched the controls, so they stay while someone is using them
    var lastTouchMs by mutableLongStateOf(0L)
        private set

    // where the bottom line's row sits and how tall it is, written while laying out and read while drawing its flight into the pane
    var regularRowHeightPx = 0
    // the pane line the bottom line turns into; -1 when there is nothing to fly to (a gap, no lyrics)
    var flyingLine by mutableIntStateOf(-1)

    private var paneJob: Job? = null

    val paneOpen: Boolean get() = pane.targetValue > 0.5f

    fun touched() {
        lastTouchMs = SystemClock.uptimeMillis()
    }

    fun flashSeek(forward: Boolean) {
        val now = SystemClock.uptimeMillis()
        val last = seekFlash
        val chained = last != null && last.forward == forward && now - last.atMs < SeekChainMs
        seekFlash = SeekFlash(forward, if (chained) last!!.seconds + 10 else 10, now)
    }

    fun toggleControls() {
        controlsShown = !controlsShown
        touched()
    }

    // builds the pane out of sight first, then glides it in: a heavy first frame mid-glide would make it jump
    fun openPane(content: VideoPane = paneContent) {
        paneContent = content
        val wasMounted = paneMounted
        paneMounted = true
        paneJob?.cancel()
        paneJob = scope.launch {
            if (!wasMounted) repeat(2) { withFrameNanos { } }
            pane.animateTo(1f, tween(PaneOpenMs, easing = PaneEasing))
        }
    }

    fun closePane() {
        paneJob?.cancel()
        paneJob = scope.launch {
            pane.animateTo(0f, tween(PaneCloseMs, easing = PaneEasing))
            paneMounted = false
        }
    }

    // where the finger has the pane, kept here so a burst of moves can't lose one between snaps
    private var dragValue = 0f

    // a drag on the pane started: it is built straight away so it can follow the finger
    fun startPaneDrag() {
        paneJob?.cancel()
        paneMounted = true
        paneDragging = true
        dragValue = pane.value
    }

    fun dragPaneBy(delta: Float) {
        dragValue = (dragValue + delta).coerceIn(0f, 1f)
        val to = dragValue
        scope.launch { pane.snapTo(to) }
    }

    // let go: a flick decides (speed in pane widths a second, opening is positive), otherwise whichever side it's nearer
    fun settlePane(velocity: Float) {
        paneDragging = false
        val open = when {
            velocity > 0.8f -> true
            velocity < -0.8f -> false
            else -> pane.value > 0.4f
        }
        paneJob?.cancel()
        paneJob = scope.launch {
            pane.animateTo(if (open) 1f else 0f, spring(dampingRatio = 1f, stiffness = 260f), initialVelocity = velocity)
            if (!open) paneMounted = false
        }
    }
}
