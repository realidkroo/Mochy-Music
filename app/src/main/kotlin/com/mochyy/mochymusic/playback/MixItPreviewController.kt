// MixItPreviewController.kt

package com.mochyy.mochymusic.playback

import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mochyy.mochymusic.crossmix.CrossmixPlan
import com.mochyy.mochymusic.crossmix.CrossmixRole
import com.mochyy.mochymusic.crossmix.TrackAnalysis
import com.mochyy.mochymusic.db.entities.PlaylistTransition
import com.mochyy.mochymusic.mixit.MixItPlanner
import com.mochyy.mochymusic.playback.audio.CrossmixTransitionAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Live playback state for the Mix It Transition Editor preview button and animated playhead. */
@Immutable
data class MixItPreviewState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /**
     * Playhead position relative to the transition window:
     * `< 0` = lead-in before window, `0..1` = inside the yellow transition window, `> 1` = lead-out after window.
     */
    val windowProgress: Float? = null,
    val outPositionMs: Long = 0L,
    val inPositionMs: Long = 0L,
    val error: String? = null,
)

/**
 * Plays a real-time, dual-player audio preview of a draft Mix It transition right inside the
 * Transition Editor — using the exact same [CrossmixTransitionAudioProcessor] DSP chain and
 * beat-lock engine that runs during real playlist playback.
 */
@UnstableApi
class MixItPreviewController(
    private val scope: CoroutineScope,
    private val host: Host,
) {
    interface Host {
        fun createPreviewPlayer(): ExoPlayer
        fun processorFor(player: Player): CrossmixTransitionAudioProcessor?
        fun releasePreviewPlayer(player: ExoPlayer)
        fun pauseMainPlayback()
        fun targetVolume(): Float
        fun learnedStartLatencyMs(): Double
        fun onPreviewActiveChanged(active: Boolean) {}
    }

    private val _state = MutableStateFlow(MixItPreviewState())
    val state: StateFlow<MixItPreviewState> = _state.asStateFlow()

    private var job: Job? = null
    private var outPlayer: ExoPlayer? = null
    private var inPlayer: ExoPlayer? = null

    fun start(
        outItem: MediaItem,
        inItem: MediaItem,
        transition: PlaylistTransition,
        outDurationMs: Long,
        inDurationMs: Long,
        outAnalysis: TrackAnalysis?,
        inAnalysis: TrackAnalysis?,
    ) {
        stop()
        host.pauseMainPlayback()
        val plan = MixItPlanner.buildPlan(
            transition = transition,
            outDurationMs = outDurationMs,
            inDurationMs = inDurationMs,
            outAnalysis = outAnalysis,
            inAnalysis = inAnalysis,
        )
        _state.value = MixItPreviewState(isPlaying = false, isBuffering = true)
        host.onPreviewActiveChanged(true)
        job = scope.launch { runPreview(outItem, inItem, plan) }
    }

    fun stop() {
        job?.cancel()
        job = null
        releasePlayers()
        host.onPreviewActiveChanged(false)
        _state.value = MixItPreviewState()
    }

    private fun releasePlayers() {
        val out = outPlayer
        val inc = inPlayer
        outPlayer = null
        inPlayer = null
        if (out != null) {
            runCatching { host.processorFor(out)?.clear() }
            runCatching { host.releasePreviewPlayer(out) }
        }
        if (inc != null) {
            runCatching { host.processorFor(inc)?.clear() }
            runCatching { host.releasePreviewPlayer(inc) }
        }
    }

    private suspend fun runPreview(
        outItem: MediaItem,
        inItem: MediaItem,
        plan: CrossmixPlan,
    ) {
        var out: ExoPlayer? = null
        var inc: ExoPlayer? = null
        try {
            val vol = host.targetVolume().coerceAtLeast(0.05f)
            // Guarantee enough lead-in for Song 2's full silent pre-roll + beat lock convergence
            val preRollMs = ((-plan.inStartBeat).coerceAtLeast(0f) * plan.outBeatMs).toLong()
            val desiredLeadIn = max(PreviewLeadInMs, preRollMs + 1_200L)
            val leadInMs = min(desiredLeadIn, plan.outStartMs)
            val previewStartMs = (plan.outStartMs - leadInMs).coerceAtLeast(0L)
            val windowDurMs = (plan.effectiveLengthBeats * plan.outBeatMs).coerceAtLeast(800.0)
            val endOutMs = plan.outStartMs + windowDurMs + PreviewLeadOutMs

            out = host.createPreviewPlayer().also { p ->
                p.setMediaItem(outItem)
                p.volume = vol
                p.playbackParameters = PlaybackParameters(plan.outSpeed.coerceIn(0.5f, 2f), 1f)
                p.seekTo(previewStartMs)
                p.playWhenReady = false
            }
            outPlayer = out

            val overlapMediaMs = (plan.effectiveLengthBeats + 8f) * plan.outBeatMs
            val inRate = plan.inMediaPerBeat / plan.outBeatMs
            val drift = (inRate - plan.inSpeed / plan.outSpeed) * overlapMediaMs
            val initialDelayMs = (BaseDelayMs + max(0.0, drift)).coerceAtMost(400.0)

            inc = host.createPreviewPlayer().also { p ->
                p.setMediaItem(inItem)
                p.volume = vol
                p.playbackParameters = PlaybackParameters(plan.inSpeed.coerceIn(0.5f, 2f), 1f)
                p.seekTo(plan.inStartMs)
                p.playWhenReady = false
            }
            inPlayer = inc

            val outProc = host.processorFor(out)
            val inProc = host.processorFor(inc)
            if (outProc == null || inProc == null) {
                _state.value = MixItPreviewState(error = "Audio preview unavailable")
                return
            }

            // Arm incoming BEFORE prepare so buffered samples stay silent until transition beat 0
            inProc.arm(buildScript(plan, CrossmixRole.Incoming, plan.inStartMs.toDouble(), 0L))
            inProc.setBeatLockDelay(initialDelayMs)

            var outFailed = false
            var inFailed = false
            val outListener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { outFailed = true }
            }
            val inListener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { inFailed = true }
            }
            out.addListener(outListener)
            inc.addListener(inListener)
            out.prepare()
            inc.prepare()

            val deadline = System.currentTimeMillis() + PrepareTimeoutMs
            while (coroutineContext.isActive) {
                if (outFailed || inFailed) {
                    _state.value = MixItPreviewState(error = "Couldn't load audio stream for preview")
                    return
                }
                val outReady = out.playbackState == Player.STATE_READY && outProc.configured
                val inReady = inc.playbackState == Player.STATE_READY && inProc.configured
                if (outReady && inReady) break
                if (System.currentTimeMillis() > deadline) {
                    _state.value = MixItPreviewState(error = "Preview timed out while loading")
                    return
                }
                delay(40)
            }
            out.removeListener(outListener)
            inc.removeListener(inListener)

            if (outProc.unsupported || inProc.unsupported) {
                _state.value = MixItPreviewState(error = "This track's audio format can't be previewed")
                return
            }

            outProc.arm(buildScript(plan, CrossmixRole.Outgoing, previewStartMs.toDouble(), System.nanoTime()))

            val playAt = plan.outMediaAt(plan.inStartBeat) -
                initialDelayMs * plan.outSpeed / plan.inSpeed -
                host.learnedStartLatencyMs() * plan.outSpeed

            var incomingStarted = false
            out.play()
            if (previewStartMs >= playAt) {
                inc.play()
                incomingStarted = true
            }

            val errors = ArrayList<Double>(5)
            var lastLockCheck = System.currentTimeMillis()
            val startWall = System.currentTimeMillis()

            while (coroutineContext.isActive) {
                val outPos = out.currentPosition
                val inPos = inc.currentPosition

                if (!incomingStarted && outPos >= playAt) {
                    inc.play()
                    incomingStarted = true
                }

                // Compute smooth playhead position relative to the transition window (0..1)
                val progress: Float = if (incomingStarted && outPos >= plan.outStartMs + windowDurMs) {
                    val pastWindowMs = (inPos - plan.inMediaAt(plan.effectiveLengthBeats)).coerceAtLeast(0.0)
                    (1f + (pastWindowMs / windowDurMs).toFloat())
                } else {
                    ((outPos - plan.outStartMs) / windowDurMs).toFloat()
                }

                val liveInPos = if (incomingStarted) inPos else plan.inStartMs
                _state.value = MixItPreviewState(
                    isPlaying = true,
                    isBuffering = false,
                    windowProgress = progress,
                    outPositionMs = outPos,
                    inPositionMs = liveInPos,
                )

                // Fast pre-roll beat-lock convergence (before Song 2 is heard) + steady tracking during overlap
                val now = System.currentTimeMillis()
                val beat = ((outPos - plan.outStartMs) / plan.outBeatMs).toFloat()
                val inPreRoll = beat < 0f
                val checkGap = if (inPreRoll) PreRollLockIntervalMs else LockIntervalMs
                val sampleWindow = if (inPreRoll) 2 else 4
                if (incomingStarted && out.isPlaying && inc.isPlaying && now - lastLockCheck >= checkGap) {
                    lastLockCheck = now
                    if (beat in -4f..(plan.effectiveLengthBeats + 1f)) {
                        val expected = plan.inMediaAt(beat)
                        val heard = inPos - inProc.currentDelayMs
                        errors += heard - expected
                        if (errors.size >= sampleWindow) {
                            val median = errors.sorted()[errors.size / 2]
                            errors.clear()
                            if (abs(median) in 2.0..600.0) {
                                inProc.setBeatLockDelay(inProc.currentDelayMs + median)
                            }
                        }
                    } else if (beat > plan.effectiveLengthBeats + 1f) {
                        inProc.setBeatLockDelay(0.0)
                    }
                }

                // End preview cleanly after lead-out past the transition window
                val pastEnd = (incomingStarted && inPos >= plan.inMediaAt(plan.effectiveLengthBeats) + PreviewLeadOutMs) ||
                    (outPos >= endOutMs && !incomingStarted) ||
                    (incomingStarted && inc.playbackState == Player.STATE_ENDED) ||
                    (System.currentTimeMillis() - startWall > MaxPreviewWallMs)
                if (pastEnd) {
                    inProc.fadeOutNow()
                    outProc.fadeOutNow()
                    delay(80)
                    break
                }

                // Switch to 2 ms precision polling right before Song 2 launches so beats lock cleanly
                val msToLaunch = if (!incomingStarted) (playAt - outPos) / plan.outSpeed.coerceAtLeast(0.5f) else 999.0
                delay(if (msToLaunch in 0.0..180.0) 2L else 28L)
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            _state.value = MixItPreviewState(error = t.message ?: "Preview stopped")
        } finally {
            releasePlayers()
            host.onPreviewActiveChanged(false)
            if (_state.value.error == null) {
                _state.value = MixItPreviewState()
            }
        }
    }

    private fun buildScript(
        plan: CrossmixPlan,
        role: CrossmixRole,
        calibrationMs: Double,
        calibrationNanos: Long,
    ) = CrossmixTransitionAudioProcessor.Script(
        role = role,
        style = plan.style,
        variation = plan.variation,
        lengthBeats = plan.effectiveLengthBeats,
        anchorMs = if (role == CrossmixRole.Outgoing) plan.outStartMs.toDouble() else plan.inMediaAt(0f),
        mediaPerBeatMs = if (role == CrossmixRole.Outgoing) plan.outBeatMs else plan.inMediaPerBeat,
        musicalBeatMs = if (role == CrossmixRole.Outgoing) plan.outBeatMs else plan.inMediaPerBeat,
        endBeat = if (role == CrossmixRole.Outgoing) plan.effectiveLengthBeats + plan.effectiveTailBeats + 2f else plan.inSettledBeat,
        calibrationMs = calibrationMs,
        calibrationNanos = calibrationNanos,
        yieldAmount = plan.yieldAmount,
        swapBeat = plan.effectiveSwapBeat,
        mixIt = plan.mixIt,
    )

    private companion object {
        const val PreviewLeadInMs = 2_500L
        const val PreviewLeadOutMs = 2_800L
        const val BaseDelayMs = 60.0
        const val PreRollLockIntervalMs = 50L
        const val LockIntervalMs = 100L
        const val PrepareTimeoutMs = 15_000L
        const val MaxPreviewWallMs = 45_000L
    }
}
