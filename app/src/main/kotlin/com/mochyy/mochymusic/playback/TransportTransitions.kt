package com.mochyy.mochymusic.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mochyy.mochymusic.constants.TransportEffect
import com.mochyy.mochymusic.crossmix.CrossmixRole
import com.mochyy.mochymusic.crossmix.CrossmixStyle
import com.mochyy.mochymusic.playback.audio.CrossmixTransitionAudioProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min

// a skip shouldn't keep you waiting on the old song, so its fade out is short whatever the length is set to
private const val SkipOutMaxMs = 1_500f
// the effects' last repeats are faded by volume over the end of a pause or skip, so nothing rings into the stop
private const val TailFadeFrom = 0.8f
// a song restarts instead of going back once it's this far in, like the previous button always did
private const val RestartAfterMs = 3_000L
private const val FrameMs = 16L
// a little over the renderer's own 60 ms fade to silence
private const val TailSettleMs = 80L
// without an analysed tempo, echoes repeat as if at 120 bpm
private const val DefaultBeatMs = 500.0
// how long a skip waits, silent, for the next song to start before it fades in anyway
private const val StartWaitMs = 6_000L
// a picked song gets this long to be found before the old one makes way for it anyway
private const val PickReadyWaitMs = 5_000L
// how far the old song dips while a picked one is still loading: enough to hear the tap land, never the silence that sounded like a stop
private const val PickHoldLevel = 0.35f
// the shortest the old song's last stretch down can be once the picked one is ready
private const val PickOutMinMs = 200f

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
        // with "blend skips" on, the service plays the skip as a crossfade on a second player; false leaves it to the fade here
        fun blendSkip(previous: Boolean): Boolean = false
        // the same crossfade into a song picked from the queue
        fun blendJump(index: Int): Boolean = false
        // whether a skip pressed now would be a crossfade, which changes the song at once instead of after a fade out
        fun blendsSkips(): Boolean = false
        // finds a picked song's stream and fetches its first seconds, so it starts the moment the old one is gone
        suspend fun getReady(mediaId: String) {}
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
    // the song a pick is on its way to, so tapping it again while it loads doesn't start over
    private var pickedSong: String? = null

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
        // a service scope that's already been cancelled would never run the fade, so the pause happens at once
        if (!scope.isActive) {
            stopFade()
            player.pause()
            return
        }
        val from = currentLevel()
        stopFade(keepVolume = true)
        val anchor = player.currentPosition
        val songId = player.currentMediaItem?.mediaId
        val processor = armOut(player, lengthMs)
        phase = Phase.Pausing
        _playIntent.value = false
        job = scope.launch {
            val done = runFade(player, lengthMs) { p ->
                if (player.currentMediaItem?.mediaId != songId) return@runFade false
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
        if (!player.playWhenReady || !shouldFade(player) || phase == Phase.Skipping || host.blendsSkips()) return 0L
        return (min(lengthMs / 2f, SkipOutMaxMs) + TailSettleMs).toLong()
    }

    fun skipPrevious() = skip(previous = true)

    // a seek, a stop or a new queue: drop any fade and put the volume back
    fun cancel() {
        if (phase == Phase.Idle && armed == null) return
        stopFade()
    }

    /** a swipe back to the song before: crossfaded with blend skips on, otherwise faded like a skip, on the timing the row's glide expects; [index] is read again when the song changes, in case the queue moved meanwhile */
    fun jumpTo(index: () -> Int) {
        val player = host.player
        val target = index()
        if (target == C.INDEX_UNSET || target >= player.mediaItemCount) return
        if (player.playWhenReady && phase != Phase.Pausing && host.blendJump(target)) {
            stopFade(keepVolume = true)
            return
        }
        switchSong(keepIfInterrupted = true) { seekToPicked(it, index) }
    }

    /** a song tapped in the queue or picked on the lock screen: crossfaded with blend skips on, otherwise picked like a song from a list; [index] is read again when the song changes */
    fun pickFromQueue(index: () -> Int) {
        val player = host.player
        val target = index()
        if (target == C.INDEX_UNSET || target >= player.mediaItemCount) return
        if (player.playWhenReady && phase != Phase.Pausing && host.blendJump(target)) {
            stopFade(keepVolume = true)
            return
        }
        val song = player.getMediaItemAt(target).mediaId
        pick(song, ready = { host.getReady(song) }) { seekToPicked(it, index) }
    }

    /** a new queue picked from a list while a song plays: [blend] tries the crossfade first, otherwise [ready] reads the queue and fetches the start of its first song ([song], when that's known up front) while the old one dips, and [start] loads it */
    fun startQueue(song: String?, ready: suspend () -> Unit, blend: () -> Boolean, start: () -> Unit) {
        val player = host.player
        if (player.playWhenReady && phase != Phase.Pausing && blend()) {
            stopFade(keepVolume = true)
            return
        }
        pick(song, ready) { start() }
    }

    private fun skip(previous: Boolean) {
        val player = host.player
        // the two songs cross over on two players; the crossfade drives both volumes from here, so this fade lets go without touching them
        if (player.playWhenReady && phase != Phase.Pausing && host.blendSkip(previous)) {
            stopFade(keepVolume = true)
            return
        }
        // a song that moved on by itself mid-fade has already skipped, so the skip isn't made twice
        switchSong(keepIfInterrupted = false) { doSkip(it, previous) }
    }

    // fades the playing song out, makes [change] once it's quiet and swells the next song in; [keepIfInterrupted] still makes the change if the song moved on by itself mid-fade, so a song picked by hand is never dropped
    private fun switchSong(keepIfInterrupted: Boolean, change: (ExoPlayer) -> Unit) {
        val player = host.player
        if (!player.playWhenReady || !shouldFade(player) || phase == Phase.Skipping || !scope.isActive) {
            stopFade()
            change(player)
            return
        }
        val from = currentLevel()
        stopFade(keepVolume = true)
        val outMs = min(lengthMs / 2f, SkipOutMaxMs)
        val inMs = max(lengthMs / 2f, 300f)
        val songId = player.currentMediaItem?.mediaId
        val index = player.currentMediaItemIndex
        val processor = armOut(player, outMs)
        phase = Phase.Skipping
        job = scope.launch {
            val done = runFade(player, outMs) { p ->
                if (player.currentMediaItem?.mediaId != songId) return@runFade false
                heard = from * (1f - smoothstep(p))
                level = if (processor != null) from * tailFade(p) else heard
                true
            }
            if (!done) {
                stopFade()
                if (keepIfInterrupted) change(player)
                return@launch
            }
            settleTails(processor)
            level = 0f
            heard = 0f
            applyLevel(player)
            disarm()
            change(player)
            // the swell waits until the next song is really heard: timed from the press, a slow start came in at full volume with no fade at all
            awaitNewSong(player, songId, index)
            armIn(player, inMs)
            phase = Phase.Resuming
            fadeIn(player, inMs, from = 0f)
        }
    }

    // a song picked by hand: the old one dips at once while [ready] gets the new one going, goes the rest of the way once it's ready, then [change] switches and the new one swells in
    private fun pick(song: String?, ready: suspend () -> Unit, change: (ExoPlayer) -> Unit) {
        val player = host.player
        // tapped again while it's still on its way: the first tap is already bringing it
        if (song != null && song == pickedSong && phase == Phase.Skipping) return
        // paused, still loading with nothing heard yet, or no fade to make: the change happens at once
        if (!player.playWhenReady || !shouldFade(player) || !scope.isActive || (phase == Phase.Idle && !player.isPlaying)) {
            stopFade()
            change(player)
            return
        }
        // a pick on top of another one carries on from wherever that one had got to, never jumping back up
        val from = currentLevel()
        stopFade(keepVolume = true)
        val outMs = min(lengthMs / 2f, SkipOutMaxMs)
        val inMs = max(lengthMs / 2f, 300f)
        phase = Phase.Skipping
        pickedSong = song
        job = scope.launch {
            val readying = launch { awaitReady(ready) }
            val dipStart = System.nanoTime()
            while (!readying.isCompleted) {
                val p = ((System.nanoTime() - dipStart) / 1_000_000f / outMs).coerceIn(0f, 1f)
                heard = from * (1f - (1f - PickHoldLevel) * smoothstep(p))
                level = heard
                applyLevel(host.player)
                delay(FrameMs)
            }
            // the rest of the way down takes the share of the fade that's left
            val down = heard
            val outgoing = host.player
            if (down > 0f) {
                val length = max(outMs * down / max(from, 0.01f), PickOutMinMs)
                val processor = armOut(outgoing, length)
                runFade(outgoing, length) { p ->
                    heard = down * (1f - smoothstep(p))
                    level = if (processor != null) down * tailFade(p) else heard
                    true
                }
                settleTails(processor)
            }
            level = 0f
            heard = 0f
            applyLevel(host.player)
            disarm()
            // read just before the switch: the old song may have rolled on by itself while it dipped
            val oldSong = host.player.currentMediaItem?.mediaId
            val oldIndex = host.player.currentMediaItemIndex
            change(host.player)
            val incoming = host.player
            awaitNewSong(incoming, oldSong, oldIndex)
            armIn(incoming, inMs)
            fadeIn(incoming, inMs, from = 0f)
        }
    }

    // gives [ready] up to PickReadyWaitMs; if it fails the pick still goes ahead and the player finds the song itself
    private suspend fun awaitReady(ready: suspend () -> Unit) {
        withTimeoutOrNull(PickReadyWaitMs) {
            try {
                ready()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    // waits, silent, until something other than [oldSong] is playing (or it starts over), for at most StartWaitMs
    private suspend fun awaitNewSong(player: ExoPlayer, oldSong: String?, oldIndex: Int) {
        var waited = 0L
        while (waited < StartWaitMs && !(player.isPlaying && (player.currentMediaItemIndex != oldIndex || player.currentMediaItem?.mediaId != oldSong || player.currentPosition < 1_000L))) {
            delay(FrameMs)
            waited += FrameMs
        }
    }

    // moves to the song at [index] as it is now and plays it
    private fun seekToPicked(player: ExoPlayer, index: () -> Int) {
        val now = index()
        if (now == C.INDEX_UNSET || now >= player.mediaItemCount) return
        player.seekToDefaultPosition(now)
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) player.prepare()
        player.playWhenReady = true
    }

    private fun fadeIn(player: ExoPlayer, length: Float, from: Float) {
        phase = Phase.Resuming
        pickedSong = null
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
        // the renderer only notices it's free once audio flows again, so being outside a crossmix (canFade) is the check
        val processor = host.processorFor(player)?.takeIf { it.canRender } ?: return null
        val script = effectScript(player, role, length, calibrationNanos) ?: return null
        processor.arm(script)
        armed = processor
        return processor
    }

    /** one side of a blended skip's layered effect, timed to [lengthMs] as heard; null with the fade off or no effect picked */
    fun blendScript(player: ExoPlayer, role: CrossmixRole, lengthMs: Float, calibrationNanos: Long): CrossmixTransitionAudioProcessor.Script? =
        if (enabled) effectScript(player, role, lengthMs, calibrationNanos) else null

    // the picked effect for one side, starting where the player is now and lasting [length] ms as heard
    private fun effectScript(player: ExoPlayer, role: CrossmixRole, length: Float, calibrationNanos: Long): CrossmixTransitionAudioProcessor.Script? {
        val move = effect.takeIf { it != TransportEffect.OFF } ?: return null
        val position = player.currentPosition.toDouble()
        val speed = player.playbackParameters.speed.takeIf { it > 0f } ?: 1f
        return CrossmixTransitionAudioProcessor.Script(
            role = role,
            style = CrossmixStyle.SilkBlend,
            variation = 0,
            lengthBeats = 1f,
            anchorMs = position,
            // the renderer counts the song's own time, which runs ahead of the clock on a sped-up song and ended the effect early
            mediaPerBeatMs = length.toDouble() * speed,
            musicalBeatMs = host.beatMsFor(player.currentMediaItem?.mediaId) ?: DefaultBeatMs,
            // the leaving side is faded out by volume first; this hard mute only backs it up
            endBeat = if (role == CrossmixRole.Outgoing) 1.5f else 1f,
            calibrationMs = position,
            calibrationNanos = calibrationNanos,
            transport = move,
            transportLengthMs = length,
        )
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
        pickedSong = null
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
}

// the volume under a leaving effect at progress p: held while the effect does the fading, then down over the last fifth
fun tailFade(p: Float): Float = 1f - smoothstep(((p - TailFadeFrom) / (1f - TailFadeFrom)).coerceIn(0f, 1f))

private fun smoothstep(x: Float): Float = x * x * (3f - 2f * x)
