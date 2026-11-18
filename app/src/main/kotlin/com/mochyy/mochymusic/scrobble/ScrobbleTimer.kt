// ScrobbleTimer.kt

package com.mochyy.mochymusic.scrobble

import android.os.SystemClock
import com.mochyy.mochymusic.models.MediaMetadata
import com.music.lastfm.ScrobbleTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Decides when a play counts, by Last.fm's rule: a song longer than 30 seconds, once it has played
 * for half its length or four minutes, whichever comes first. Only time spent playing counts, so a
 * pause holds the clock. MusicService says when songs start, pause, resume and stop.
 */
class ScrobbleTimer(
    // read fresh each time, the service swaps in a new scope after its old one ends and a kept one would drop every scrobble
    private val scope: () -> CoroutineScope,
    private val scrobbler: Scrobbler,
) {
    var minSongSeconds = DEFAULT_MIN_SONG_SECONDS
    var playedFraction = DEFAULT_PLAYED_FRACTION
    var maxWaitSeconds = DEFAULT_MAX_WAIT_SECONDS

    private var track: ScrobbleTrack? = null
    private var trackId: String? = null

    /** When the song started, in seconds since 1970: what a scrobble is sent with. */
    private var startedAt = 0L

    /** Playing time still to go before it counts; 0 once scrobbled, or for a song too short to count. */
    private var remainingMs = 0L

    /** When the clock last started running, or 0 while paused. */
    private var runningSince = 0L
    private var job: Job? = null

    /** [durationMs] is the player's; until it knows, the song's own length is used. */
    fun onSongStart(metadata: MediaMetadata?, durationMs: Long) {
        onSongStop()
        val song = metadata?.toScrobbleTrack() ?: return
        val seconds = if (durationMs > 0) (durationMs / 1000).toInt() else metadata.duration
        track = song.copy(durationSeconds = seconds.takeIf { it > 0 })
        trackId = metadata.id
        startedAt = System.currentTimeMillis() / 1000
        scrobbler.nowPlaying(track!!)
        if (seconds <= minSongSeconds) return
        remainingMs = min((seconds * 1000L * playedFraction).toLong(), maxWaitSeconds * 1000L)
        resume()
    }

    fun onPlayingChanged(isPlaying: Boolean, metadata: MediaMetadata?, durationMs: Long) {
        // an external skip while crossfading can swap the song before the stop arrives, so restart for the new id
        if (track != null && metadata?.id != null && metadata.id != trackId) {
            onSongStop()
        }
        when {
            !isPlaying -> pause()
            track == null -> onSongStart(metadata, durationMs)
            else -> resume()
        }
    }

    fun onSongStop() {
        job?.cancel()
        job = null
        track = null
        trackId = null
        remainingMs = 0
        runningSince = 0
    }

    private fun resume() {
        val song = track ?: return
        if (remainingMs <= 0 || runningSince != 0L) return
        runningSince = SystemClock.elapsedRealtime()
        job = scope().launch {
            delay(remainingMs)
            remainingMs = 0
            runningSince = 0
            scrobbler.scrobble(song, startedAt)
        }
    }

    private fun pause() {
        if (runningSince == 0L) return
        job?.cancel()
        job = null
        remainingMs = (remainingMs - (SystemClock.elapsedRealtime() - runningSince)).coerceAtLeast(1)
        runningSince = 0
    }

    companion object {
        const val DEFAULT_MIN_SONG_SECONDS = 30
        const val DEFAULT_PLAYED_FRACTION = 0.5f
        const val DEFAULT_MAX_WAIT_SECONDS = 240
    }
}

/**
 * The first artist only: services keep one artist per play, and "A, B" would show up there as
 * an artist of its own. Null for a song with no title or artist, which no service takes.
 */
private fun MediaMetadata.toScrobbleTrack(): ScrobbleTrack? {
    val artist = artists.firstOrNull()?.name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val title = title.trim().takeIf { it.isNotEmpty() } ?: return null
    return ScrobbleTrack(artist = artist, track = title, album = album?.title?.trim()?.takeIf { it.isNotEmpty() })
}
