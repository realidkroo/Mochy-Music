// CarLyrics.kt

package com.mochyy.mochymusic.car

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.mochyy.mochymusic.constants.CarLyricsKey
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

// lyrics on android auto's own player: while a car is connected the line being sung becomes the title and the cover a lyric card, so it shows on every car screen size without any template
@UnstableApi
class CarLyrics(
    private val context: Context,
    private val source: CarLyricsSource,
    private val playing: StateFlow<MediaMetadata?>,
    private val player: () -> Player,
    private val overlay: CarLyricsPlayer,
    private val scope: CoroutineScope,
) {
    private val automotive = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    private val connection = CarConnection(context)
    private val inCar = MutableStateFlow(automotive)

    // android auto's phone app stays bound to the session for its resume card, so only a real projection or a car's own screen counts
    private val onConnection = Observer<Int> { type -> inCar.value = automotive || type != CarConnection.CONNECTION_TYPE_NOT_CONNECTED }
    private val card = CarLyricsCard(context)
    private var job: Job? = null

    // call on the main thread, the connection is live data
    fun start() {
        if (job != null) return
        connection.type.observeForever(onConnection)
        job = scope.launch {
            val wanted = context.dataStore.data.map { it[CarLyricsKey] ?: true }.distinctUntilChanged()
            combine(inCar, wanted) { car, on -> car && on }
                .distinctUntilChanged()
                .collectLatest { active -> if (active) follow() else overlay.show(null) }
        }
    }

    fun stop() {
        connection.type.removeObserver(onConnection)
        job?.cancel()
        job = null
        overlay.show(null)
    }

    // follows each song for as long as lyrics are wanted, and hands the song's own title back the moment they aren't
    private suspend fun follow() {
        try {
            playing.distinctUntilChangedBy { it?.id }.collectLatest { song ->
                overlay.show(null)
                if (song != null) sing(song)
            }
        } finally {
            overlay.show(null)
        }
    }

    private suspend fun sing(song: MediaMetadata) {
        val lyrics = source.load(song)?.takeIf { it.synced } ?: return
        val background = withContext(Dispatchers.IO) { runCatching { card.background(song.thumbnailUrl) }.getOrNull() }
        val subtitle = listOf(song.title, song.artists.joinToString { it.name }).filter { it.isNotBlank() }.joinToString(" · ")
        var shownLine = Int.MIN_VALUE
        var shownWords = Int.MIN_VALUE
        var lastSentAt = 0L
        while (true) {
            val current = player()
            val position = current.currentPosition
            val index = lyrics.lineAt(position)
            val line = lyrics.lines.getOrNull(index)
            if (current.currentMediaItem?.mediaId != song.id || line == null || line.text.isBlank() || lyrics.isBreak(index, position)) {
                if (shownLine != Int.MIN_VALUE) overlay.show(null)
                shownLine = Int.MIN_VALUE
            } else {
                val sung = line.words?.takeIf { it.isNotEmpty() }?.count { it.startTime * 1000 <= position + lyrics.offsetMs } ?: -1
                val now = SystemClock.elapsedRealtime()
                // each word lit is a new picture sent to the car, so they're spaced out a little; a new line always goes at once
                val due = index != shownLine || (sung != shownWords && now - lastSentAt >= WordStepMs)
                if (due) {
                    val art = withContext(Dispatchers.Default) { runCatching { card.render(background, lyrics, index, sung) }.getOrNull() }
                    overlay.show(CarLyricsPlayer.Overlay(song.id, line.text, subtitle, art))
                    shownLine = index
                    shownWords = sung
                    lastSentAt = now
                }
            }
            delay(if (current.isPlaying) PlayingTickMs else PausedTickMs)
        }
    }

    private companion object {
        const val PlayingTickMs = 90L
        const val PausedTickMs = 500L
        const val WordStepMs = 280L
    }
}
