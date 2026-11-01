package com.mochyy.mochymusic.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mochyy.mochymusic.constants.TransportEffect
import com.mochyy.mochymusic.crossmix.CrossmixRole
import com.mochyy.mochymusic.crossmix.CrossmixStyle
import com.mochyy.mochymusic.playback.audio.CrossmixTransitionAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

// a skip shouldn't keep you waiting on the old song, so its fade out is short whatever the length is set to
private const val SkipOutMaxMs = 1_500f
// the effects' last repeats are faded by volume over the end of a pause, so nothing rings into the stop
private const val TailFadeFrom = 0.8f
// a song restarts instead of going back once it's this far in, like the previous button always did
private const val RestartAfterMs = 3_000L
private const val FrameMs = 16L
// a little over the renderer's own 60 ms fade to silence
private const val TailSettleMs = 80L
// without an analysed tempo, echoes repeat as if at 120 bpm
private const val DefaultBeatMs = 500.0

// fades and crossmix effects for pause, play and skips, so stopping or switching songs eases out instead of cutting
@UnstableApi
class TransportTransitions(
    // the service rebuilds its scope after a stop, so it's looked up each time
    private val scopeProvider: () -> CoroutineScope,
    private val host: Host,
) {
    private val scope: CoroutineScope get() = scopeProvider()

    interface Host {
        val player: ExoPlayer
        fun processorFor(player: ExoPlayer): CrossmixTransitionAudioProcessor?
        fun targetVolume(): Float
        // false while casting or mid-crossmix: the buttons then act at once, as they always did
        fun canFade(): Boolean
        fun beatMsFor(mediaId: String?): Double?
    }

    @Volatile var enabled = true
    @Volatile var lengthMs = 2_000f
    @Volatile var effect = TransportEffect.OFF

    // the buttons' meaning while a fade runs (the player only catches up once it's over); null follows the player
    private val _playIntent = MutableStateFlow<Boolean?>(null)
    val playIntent: StateFlow<Boolean?> = _playIntent.asStateFlow()

    private enum class Phase { Idle, Pausing, Skipping, Resuming }

    private var phase = Phase.Idle
    private var job: Job? = null
    private var armed: CrossmixTransitionAudioProcessor? = null
    // the volume the fade has set, 0..1 of the user's; with an effect armed the renderer does the fading instead
    private var level = 1f
    // roughly how loud it sounds, which is what a change of mind fades back from
    private var heard = 1f

    val isFading: Boolean get() = phase != Phase.Idle

    fun togglePlayPause() {
        val player = host.player
        val playing = if (phase == Phase.Pausing) false else player.playWhenReady && player.playbackState != Player.STATE_ENDED
        if (playing) pause() else play()
    }

    fun pause() {
        val player = host.player
        if (phase == Phase.Pausing) {
            // a second press means now
            finishPause(player, rewindTo = null)
            return
        }
        if (!player.playWhenReady) {
            stopFade()
            return
        }
        if (!shouldFade(player)) {
            stopFade()
            player.pause()
            return
        }
        val from = currentLevel()
        stopFade(keepVolume = true)
        val anchor = player.currentPosition
        val item = player.currentMediaItem
        val processor = armOut(player, lengthMs)
        phase = Phase.Pausing
        _playIntent.value = false
        job = scope.launch {
            val done = runFade(player, lengthMs) { p ->
                if (player.currentMediaItem != item) return@runFade false
                heard = from * (1f - smoothstep(p))
                level = if (processor != null) from * tailFade(p) else heard
                true
            }
            // another song picked mid-fade wins over the pause
            if (!done) {
                stopFade()
                return@launch
            }
            settleTails(processor)
            finishPause(player, rewindTo = anchor)
        }
    }

    fun play() {
        val player = host.player
        val resumingFrom = if (phase == Phase.Pausing) currentLevel() else 0f
        if (phase != Phase.Pausing && player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            stopFade()
            return
        }
        if (!shouldFade(player)) {
            stopFade()
            startPlayer(player)
            return
        }
        stopFade(keepVolume = true)
        // a change of mind mid-pause comes back up from where the fade had got to, over what's left of it
        val length = if (resumingFrom > 0f) max(lengthMs * (1f - resumingFrom), 200f) else lengthMs
        level = resumingFrom
        heard = resumingFrom
        applyLevel(player)
        // the effect starts where the song will really play from, so a finished queue is rewound first
        readyToPlay(player)
        // coming back mid-pause is a plain swell: an entrance effect would start from silence and dip
        if (resumingFrom == 0f) armIn(player, length)
        player.playWhenReady = true
        fadeIn(player, length, resumingFrom)
    }

    fun skipNext() = skip(previous = false)

    // how long a skip pressed now keeps the old song on, fading, before it changes; 0 when it changes at once
    fun skipDelayMs(): Long {
        val player = host.player
        if (!player.playWhenReady || !shouldFade(player) || phase == Phase.Skipping) return 0L
        return (min(lengthMs / 2f, SkipOutMaxMs) + TailSettleMs).toLong()
    }

    fun skipPrevious() = skip(previous = true)

    // a seek, a stop or a new queue: drop any fade and put the volume back
    fun cancel() {
        if (phase == Phase.Idle && armed == null) return
        stopFade()
    }

    private fun skip(previous: Boolean) {
        val player = host.player
        if (!player.playWhenReady || !shouldFade(player) || phase == Phase.Skipping) {
            stopFade()
            doSkip(player, previous)
            return
        }
        val from = currentLevel()
        stopFade(keepVolume = true)
        val outMs = min(lengthMs / 2f, SkipOutMaxMs)
        val inMs = max(lengthMs / 2f, 300f)
        val item = player.currentMediaItem
        val processor = armOut(player, outMs)
        phase = Phase.Skipping
        job = scope.launch {
            val done = runFade(player, outMs) { p ->
                if (player.currentMediaItem != item) return@runFade false
                heard = from * (1f - smoothstep(p))
                level = if (processor != null) from * tailFade(p) else heard
                true
            }
            if (!done) {
                stopFade()
                return@launch
            }
            settleTails(processor)
            level = 0f
            heard = 0f
            applyLevel(player)
            disarm()
            doSkip(player, previous)
            // the next song's first buffers decide where its effect starts, so wait for it to be the current one
            var waited = 0L
            while (player.currentMediaItem == item && player.currentPosition > 1_000L && waited < 600L) {
                delay(FrameMs)
                waited += FrameMs
            }
            armIn(player, inMs)
            phase = Phase.Resuming
            fadeIn(player, inMs, from = 0f)
        }
    }

    private fun fadeIn(player: ExoPlayer, length: Float, from: Float) {
        phase = Phase.Resuming
        _playIntent.value = true
        job = scope.launch {
            runFade(player, length) { p ->
                level = from + (1f - from) * smoothstep(p)
                heard = level
                true
            }
            level = 1f
            applyLevel(player)
            armed?.finish()
            armed = null
            phase = Phase.Idle
            _playIntent.value = null
        }
    }

    private fun finishPause(player: ExoPlayer, rewindTo: Long?) {
        job?.cancel()
        job = null
        level = 0f
        applyLevel(player)
        player.pause()
        disarm()
        // the effect let the song run on under it; play picks up where pause was pressed
        if (rewindTo != null && rewindTo >= 0) player.seekTo(rewindTo)
        level = 1f
        heard = 1f
        applyLevel(player)
        phase = Phase.Idle
        _playIntent.value = null
    }

    // echo and reverb tails ring past the end of the move, so the renderer fades its whole mix out before the stop
    private suspend fun settleTails(processor: CrossmixTransitionAudioProcessor?) {
        if (processor == null) return
        processor.fadeOutNow()
        delay(TailSettleMs)
    }

    // steps the fade each frame by the clock; false if step stopped it early
    private suspend fun runFade(player: ExoPlayer, length: Float, step: (Float) -> Boolean): Boolean {
        val start = System.nanoTime()
        while (true) {
            val p = ((System.nanoTime() - start) / 1_000_000f / length).coerceIn(0f, 1f)
            if (!step(p)) return false
            applyLevel(player)
            if (p >= 1f) return true
            delay(FrameMs)
        }
    }

    private fun armOut(player: ExoPlayer, length: Float): CrossmixTransitionAudioProcessor? =
        arm(player, CrossmixRole.Outgoing, length, calibrationNanos = System.nanoTime())

    private fun armIn(player: ExoPlayer, length: Float): CrossmixTransitionAudioProcessor? =
        arm(player, CrossmixRole.Incoming, length, calibrationNanos = if (player.isPlaying) System.nanoTime() else 0L)

    // borrows the player's crossmix renderer for the layered effect, when there is one and it's free
    private fun arm(player: ExoPlayer, role: CrossmixRole, length: Float, calibrationNanos: Long): CrossmixTransitionAudioProcessor? {
        disarm()
        val move = effect.takeIf { it != TransportEffect.OFF } ?: return null
        // the renderer only notices it's free once audio flows again, so being outside a crossmix (canFade) is the check
        val processor = host.processorFor(player)?.takeIf { it.canRender } ?: return null
        val position = player.currentPosition.toDouble()
        processor.arm(
            CrossmixTransitionAudioProcessor.Script(
                role = role,
                style = CrossmixStyle.SilkBlend,
                variation = 0,
                lengthBeats = 1f,
                anchorMs = position,
                mediaPerBeatMs = length.toDouble(),
                musicalBeatMs = host.beatMsFor(player.currentMediaItem?.mediaId) ?: DefaultBeatMs,
                // the leaving side is faded out by settleTails; this hard mute only backs it up
                endBeat = if (role == CrossmixRole.Outgoing) 1.5f else 1f,
                calibrationMs = position,
                calibrationNanos = calibrationNanos,
                transport = move,
                transportLengthMs = length,
            )
        )
        armed = processor
        return processor
    }

    private fun disarm() {
        armed?.clear()
        armed = null
    }

    private fun stopFade(keepVolume: Boolean = false) {
        job?.cancel()
        job = null
        disarm()
        phase = Phase.Idle
        _playIntent.value = null
        if (!keepVolume) {
            level = 1f
            heard = 1f
            applyLevel(host.player)
        }
    }

    private fun shouldFade(player: ExoPlayer): Boolean =
        enabled && lengthMs > 0f && host.canFade() && player.mediaItemCount > 0

    private fun currentLevel(): Float = if (phase == Phase.Idle) 1f else heard

    private fun applyLevel(player: ExoPlayer) {
        player.volume = host.targetVolume() * level.coerceIn(0f, 1f)
    }

    private fun startPlayer(player: ExoPlayer) {
        readyToPlay(player)
        player.playWhenReady = true
    }

    private fun readyToPlay(player: ExoPlayer) {
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
    }

    private fun doSkip(player: ExoPlayer, previous: Boolean) {
        if (previous) {
            if (player.currentPosition > RestartAfterMs || !player.hasPreviousMediaItem()) player.seekTo(0) else player.seekToPreviousMediaItem()
        } else {
            player.seekToNext()
        }
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) player.prepare()
        player.playWhenReady = true
    }

    private fun tailFade(p: Float): Float = 1f - smoothstep(((p - TailFadeFrom) / (1f - TailFadeFrom)).coerceIn(0f, 1f))

    private fun smoothstep(x: Float): Float = x * x * (3f - 2f * x)
}
