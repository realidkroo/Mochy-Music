// CarLyrics.kt

package com.mochyy.mochymusic.car

import android.content.Context
import android.content.pm.PackageManager
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.mochyy.mochymusic.constants.CarLyricsKey
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// lyrics on android auto's own player: while a car is connected the song and its artist share the title and the line being sung takes the artist's place
@UnstableApi
class CarLyrics(
    private val context: Context,
    private val source: CarLyricsSource,
    private val playing: StateFlow<MediaMetadata?>,
    private val player: () -> Player,
    private var overlay: CarLyricsPlayer,
    private val scope: CoroutineScope,
) {
    private val automotive = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    private val connection = CarConnection(context)
    private val inCar = MutableStateFlow(automotive)

    // android auto's phone app stays bound to the session for its resume card, so only a real projection or a car's own screen counts
    private val onConnection = Observer<Int> { type -> inCar.value = automotive || type != CarConnection.CONNECTION_TYPE_NOT_CONNECTED }
    private var job: Job? = null
    private var shown: CarLyricsPlayer.Overlay? = null

    // call on the main thread, the connection is live data
    fun start() {
        if (job != null) return
        connection.type.observeForever(onConnection)
        job = scope.launch {
            val wanted = context.dataStore.data.map { it[CarLyricsKey] ?: true }.distinctUntilChanged()
            combine(inCar, wanted) { car, on -> car && on }
                .distinctUntilChanged()
                .collectLatest { active -> if (active) follow() else show(null) }
        }
    }

    fun stop() {
        connection.type.removeObserver(onConnection)
        job?.cancel()
        job = null
        show(null)
    }

    // a crossfade hands the session a new player, and the line on screen moves over to it instead of vanishing until the next one
    fun attach(next: CarLyricsPlayer) {
        overlay = next
        next.show(shown)
    }

    private fun show(next: CarLyricsPlayer.Overlay?) {
        shown = next
        overlay.show(next)
    }

    // follows each song for as long as lyrics are wanted, and hands the song's own title back the moment they aren't
    private suspend fun follow() {
        try {
            playing.distinctUntilChangedBy { it?.id }.collectLatest { song ->
                show(null)
                if (song != null) sing(song)
            }
        } finally {
            show(null)
        }
    }

    // only a new line is sent, a plain text change the car shows at once without any picture going over
    private suspend fun sing(song: MediaMetadata) {
        val lyrics = source.load(song)?.takeIf { it.synced } ?: return
        val title = listOf(song.title, song.artists.joinToString { it.name }).filter { it.isNotBlank() }.joinToString(" — ")
        var shownLine = Int.MIN_VALUE
        while (true) {
            val current = player()
            val position = current.currentPosition
            val index = lyrics.lineAt(position)
            // long instrumental stretches hand the song's own artist back until singing starts again
            val line = lyrics.lines.getOrNull(index)?.takeIf {
                it.text.isNotBlank() && current.currentMediaItem?.mediaId == song.id && !lyrics.isBreak(index, position)
            }
            if (line == null) {
                if (shownLine != Int.MIN_VALUE) show(null)
                shownLine = Int.MIN_VALUE
            } else if (index != shownLine) {
                show(CarLyricsPlayer.Overlay(song.id, title, line.text.trim()))
                shownLine = index
            }
            delay(if (current.isPlaying) PlayingTickMs else PausedTickMs)
        }
    }

    private companion object {
        const val PlayingTickMs = 90L
        const val PausedTickMs = 500L
    }
}
