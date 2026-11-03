package com.mochyy.mochymusic.playback

import android.os.SystemClock
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.utils.YTPlayerUtils
import com.mochyy.mochymusic.utils.reportException
import com.music.innertube.YouTube
import com.music.innertube.models.response.PlayerResponse.PlaybackTracking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// tells youtube about each song someone really listens to, like its own app does: one play once it counts, then how long it was heard
class YouTubePlayReporter(
    // read fresh each time, the service swaps in a new scope after its old one ends
    private val scope: () -> CoroutineScope,
    // where the player is in the song, only read on the main thread
    private val positionMs: () -> Long,
) {
    // the app behaviour switch with offline mode folded in, turning it off drops the song being timed
    var isEnabled = true
        set(value) {
            field = value
            if (!value) drop()
        }

    // how long a song has to play before it counts, the same wait as mochy's own history
    var countAfterMs = DEFAULT_COUNT_AFTER_MS

    // its own scope so a song's last report still goes out while the service shuts down
    private val network = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val offeredTracking = ConcurrentHashMap<String, OfferedTracking>()
    private var listen: Listen? = null

    // the stream lookup already asked youtube about this song, so its play links come for free
    fun offerTracking(videoId: String, tracking: PlaybackTracking?) {
        tracking ?: return
        val now = SystemClock.elapsedRealtime()
        offeredTracking.values.removeIf { now - it.offeredAt > TRACKING_FRESH_MS }
        offeredTracking[videoId] = OfferedTracking(tracking, now)
    }

    fun onSongStart(metadata: MediaMetadata?, durationMs: Long) {
        onSongStop()
        // local files have nothing on youtube to count against
        if (!isEnabled || metadata == null || metadata.localUri != null) return
        val lengthMs = if (durationMs > 0) durationMs else metadata.duration * 1000L
        listen = Listen(lengthMs, trackingFor(metadata.id))
        resume()
    }

    fun onPlayingChanged(isPlaying: Boolean, metadata: MediaMetadata?, durationMs: Long) {
        when {
            !isPlaying -> pause()
            listen == null -> onSongStart(metadata, durationMs)
            else -> resume()
        }
    }

    // a seek or a move to another song: the old spot ends what was heard, and a seek starts the next stretch from the new one
    fun onJump(fromMs: Long, toMs: Long, sameSong: Boolean) {
        val listen = listen ?: return
        if (listen.runningSince == 0L) return
        listen.timer?.cancel()
        endStretch(listen, fromMs)
        if (sameSong) startStretch(listen, toMs)
    }

    fun onSongStop() {
        val listen = listen ?: return
        this.listen = null
        listen.timer?.cancel()
        endStretch(listen, positionMs())
        // a song skipped before it counted sends nothing at all
        if (listen.counted) report(listen, final = true) else listen.tracking.cancel()
    }

    private fun drop() {
        val listen = listen ?: return
        this.listen = null
        listen.timer?.cancel()
        listen.tracking.cancel()
    }

    private fun resume() {
        val listen = listen ?: return
        if (listen.runningSince == 0L) startStretch(listen, positionMs())
    }

    private fun pause() {
        val listen = listen ?: return
        listen.timer?.cancel()
        endStretch(listen, positionMs())
    }

    // starts timing a stretch of listening and wakes up when the next report is due
    private fun startStretch(listen: Listen, atMs: Long) {
        listen.runningSince = SystemClock.elapsedRealtime()
        listen.stretchStartMs = atMs
        val dueAtHeardMs = if (listen.counted) listen.reportedAtHeardMs + WATCHTIME_EVERY_MS else countAfterMs
        listen.timer = scope().launch {
            delay((dueAtHeardMs - listen.heardMs).coerceAtLeast(0))
            val nowMs = positionMs()
            endStretch(listen, nowMs)
            report(listen, final = false)
            startStretch(listen, nowMs)
        }
    }

    // adds the stretch that just ended to what was heard
    private fun endStretch(listen: Listen, atMs: Long) {
        if (listen.runningSince == 0L) return
        listen.heardMs += SystemClock.elapsedRealtime() - listen.runningSince
        listen.runningSince = 0
        if (atMs > listen.stretchStartMs) listen.unsent += Stretch(listen.stretchStartMs, atMs)
    }

    // the first report sends the play, every report sends the stretches heard since the one before
    private fun report(listen: Listen, final: Boolean) {
        val sendsPlay = !listen.counted
        listen.counted = true
        listen.reportedAtHeardMs = listen.heardMs
        val watchtime = listen.unsent.takeIf { it.isNotEmpty() }?.let { watchtimeParams(listen, it, final) }
        listen.unsent.clear()
        val previous = listen.sending
        listen.sending = network.launch {
            previous?.join()
            val tracking = listen.tracking.await() ?: return@launch
            val playUrl = tracking.videostatsPlaybackUrl?.baseUrl
            if (sendsPlay && playUrl != null) {
                YouTube.registerPlayback(playbackTracking = playUrl, cpn = listen.cpn).onFailure(::reportException)
            }
            val watchtimeUrl = tracking.videostatsWatchtimeUrl?.baseUrl
            if (watchtime != null && watchtimeUrl != null) {
                YouTube.reportWatchtime(watchtimeUrl, listen.cpn, watchtime).onFailure(::reportException)
            }
        }
    }

    // the links from the stream lookup when it just ran, otherwise one quick ask of youtube as the song starts
    private fun trackingFor(videoId: String): Deferred<PlaybackTracking?> {
        val offered = offeredTracking.remove(videoId)
        if (offered != null && SystemClock.elapsedRealtime() - offered.offeredAt <= TRACKING_FRESH_MS) {
            return CompletableDeferred(offered.tracking)
        }
        return network.async {
            // one failed ask shouldn't lose the play, so it's tried again a couple of times
            repeat(TRACKING_TRIES) { attempt ->
                if (attempt > 0) delay(TRACKING_RETRY_MS * attempt)
                YTPlayerUtils.playerResponseForMetadata(videoId).getOrNull()?.playbackTracking?.let { return@async it }
            }
            null
        }
    }

    // the same fields youtube's own player sends with its watch time, all in seconds
    private fun watchtimeParams(listen: Listen, stretches: List<Stretch>, final: Boolean) = buildMap {
        put("st", stretches.joinToString(",") { seconds(it.fromMs) })
        put("et", stretches.joinToString(",") { seconds(it.toMs) })
        put("cmt", seconds(stretches.last().toMs))
        put("rt", seconds(SystemClock.elapsedRealtime() - listen.startedAt))
        put("state", if (final) "paused" else "playing")
        if (listen.lengthMs > 0) put("len", seconds(listen.lengthMs))
        if (final) put("final", "1")
    }

    private fun seconds(ms: Long) = String.format(Locale.US, "%.3f", ms / 1000.0)

    companion object {
        const val DEFAULT_COUNT_AFTER_MS = 30_000L

        // about how often youtube's own player sends watch time once a song has counted
        private const val WATCHTIME_EVERY_MS = 40_000L

        // play links older than this get asked for again, so a report never rides on a stale ask
        private const val TRACKING_FRESH_MS = 15 * 60_000L

        private const val TRACKING_TRIES = 3
        private const val TRACKING_RETRY_MS = 5_000L
    }
}

// one listen of one song, from when it starts playing until it ends, gets skipped or the app stops
private class Listen(val lengthMs: Long, val tracking: Deferred<PlaybackTracking?>) {
    val cpn = YouTube.newPlaybackNonce()
    val startedAt = SystemClock.elapsedRealtime()

    // playing time heard so far, not counting the stretch running right now
    var heardMs = 0L

    // when the running stretch began, 0 while paused
    var runningSince = 0L
    var stretchStartMs = 0L

    // heard since the last report and still waiting to be sent
    val unsent = mutableListOf<Stretch>()

    // the play went out, from here on only watch time follows
    var counted = false
    var reportedAtHeardMs = 0L
    var timer: Job? = null

    // the report still on its way, the next one waits for it so youtube gets them in order
    var sending: Job? = null
}

private class Stretch(val fromMs: Long, val toMs: Long)

private class OfferedTracking(val tracking: PlaybackTracking, val offeredAt: Long)
