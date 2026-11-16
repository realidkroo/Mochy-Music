// FloatingLyrics.kt

package com.mochyy.mochymusic.floating

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.mochyy.mochymusic.MainActivity
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.car.CarLyricsSource
import com.mochyy.mochymusic.constants.FloatingLyricsKey
import com.mochyy.mochymusic.constants.FloatingLyricsXKey
import com.mochyy.mochymusic.constants.FloatingLyricsYKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.lyrics.LyricsTranslationHelper
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.ui.player.LineReading
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// the line being sung floating over other apps: shown while it's turned on and allowed, a song is loaded and mochy itself is out of sight
class FloatingLyrics(
    private val context: Context,
    private val database: MusicDatabase,
    private val source: CarLyricsSource,
    private val song: StateFlow<MediaMetadata?>,
    private val player: StateFlow<Player?>,
    private val scope: CoroutineScope,
) {
    private val appVisible = MutableStateFlow(true)

    // dropped on close: hidden until mochy is opened again
    private val dismissed = MutableStateFlow(false)
    private val state = PillState { player.value }
    private var job: Job? = null
    private var window: PillWindow? = null
    private var follow: Job? = null

    private val visibility = LifecycleEventObserver { _, _ ->
        val visible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        appVisible.value = visible
        if (visible) dismissed.value = false
    }

    // google's translation of the last song, so the pill coming back doesn't ask for it again
    private var translationMemo: Pair<String, List<String?>>? = null

    private val host = object : PillHost {
        override fun reserveFrame(shape: PillShape, slots: Int) {
            window?.reserveFrame(shape, slots)
        }

        override fun fitFrame(shape: PillShape, slots: Int) {
            window?.fitFrame(shape, slots)
        }

        override fun openApp() {
            state.expanded = false
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            runCatching { context.startActivity(open) }
        }

        // the same as the widget's button
        override fun togglePlay() {
            val current = player.value ?: return
            if (current.isPlaying) current.pause() else current.play()
        }

        override fun seekTo(positionMs: Long) {
            player.value?.seekTo(positionMs)
        }
    }

    private val windowListener = object : PillWindowListener {
        override fun onDocked(end: Boolean) {
            state.dockEnd = end
        }

        override fun onDragStarted() {
            state.expanded = false
            state.tucked = true
            state.lifted = true
        }

        override fun onSettling() {
            state.tucked = false
            state.lifted = false
        }

        override fun onMoved(x: Int, y: Int) {
            scope.launch {
                context.dataStore.edit {
                    it[FloatingLyricsXKey] = x
                    it[FloatingLyricsYKey] = y
                }
            }
        }

        override fun onDropped(drop: PillDrop) {
            dismissed.value = true
            if (drop != PillDrop.NEVER) return
            scope.launch { context.dataStore.edit { it[FloatingLyricsKey] = false } }
            Toast.makeText(context, R.string.floating_lyrics_turned_off, Toast.LENGTH_LONG).show()
        }

        override fun onTouched() {
            if (state.expanded) state.touchTick++
        }

        override fun onTouchedOutside() {
            state.expanded = false
        }
    }

    // call on the main thread, the app's lifecycle is watched from there
    fun start() {
        if (job != null) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(visibility)
        job = scope.launch {
            val wanted = context.dataStore.data.map { it[FloatingLyricsKey] ?: false }.distinctUntilChanged()
            val hasSong = song.map { it != null }.distinctUntilChanged()
            combine(wanted, appVisible, dismissed, hasSong) { on, visible, gone, playing -> on && !visible && !gone && playing }
                .distinctUntilChanged()
                .collect { show -> if (show && Settings.canDrawOverlays(context)) open() else close() }
        }
    }

    fun stop() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(visibility)
        job?.cancel()
        job = null
        close()
    }

    private suspend fun open() {
        if (window != null) return
        val prefs = context.dataStore.data.first()
        val pill = PillWindow(context, prefs[FloatingLyricsXKey], prefs[FloatingLyricsYKey], windowListener)
        if (!pill.show { FloatingPill(state, pill.metrics, host) }) return
        window = pill
        follow = scope.launch { followSong() }
    }

    private fun close() {
        follow?.cancel()
        follow = null
        window?.remove()
        window = null
        state.expanded = false
        state.tucked = false
        state.lifted = false
    }

    // keeps the pill's song, lyrics and play state current for as long as it's on screen; nothing runs while it's hidden
    private suspend fun followSong() = coroutineScope {
        launch { followPlayer() }
        song.distinctUntilChangedBy { it?.id }.collectLatest { metadata ->
            state.song = metadata
            state.script = null
            if (metadata != null) followLyrics(metadata)
        }
    }

    // one song's cover colour and lyrics, both dropped the moment the song changes
    private suspend fun followLyrics(metadata: MediaMetadata) = coroutineScope {
        val artist = metadata.artists.joinToString { it.name }
        val appRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        // the bubble keeps the last song's colour until this one's is read
        launch { state.accent = coverAccent(context, metadata.thumbnailUrl) ?: state.accent }
        val offset = database.song(metadata.id).map { it?.song?.lyricsOffset ?: 0 }
        // follows the saved lyrics, so a fix, a new source or a saved translation in the app reaches the pill straight away
        combine(
            database.lyrics(metadata.id),
            offset,
            readingSettings(context),
            LyricsTranslationHelper.translationOn,
        ) { saved, shift, settings, translate -> SongLyrics(saved, shift, settings, translate) }
            .distinctUntilChanged()
            .collectLatest { found ->
                state.offsetMs = found.shift
                val raw = found.saved?.lyrics
                if (raw == null) {
                    // nothing saved yet: fetched once and saved, and the saved copy comes back through here
                    source.fetch(metadata)
                    return@collectLatest
                }
                showLyrics(metadata.id, raw, metadata.title, artist, appRtl, found)
            }
    }

    // the plain lines go up first; the romanisation and then the translation swap in as each is ready
    private suspend fun showLyrics(songId: String, raw: String, title: String, artist: String, appRtl: Boolean, found: SongLyrics) {
        val lines = withContext(Dispatchers.Default) { PillScript.lines(raw) }
        val plain = withContext(Dispatchers.Default) { PillScript.build(lines, title, artist, appRtl) }
        // after a song's first lines, its rows stay up until the new ones are ready, so a setting change doesn't shrink the bubble and grow it back
        if (state.script == null || !plain.sung) state.script = plain
        if (!plain.sung) return
        val settings = found.settings
        val withReadings: suspend (List<LineReading?>, List<String?>) -> PillScript = { romanized, translated ->
            withContext(Dispatchers.Default) {
                PillScript.build(lines, title, artist, appRtl, PillReadings(romanized, translated, settings.romanizedAsMain))
            }
        }
        val romanized = romanizedLines(lines, settings)
        val key = "$songId|${settings.language}|${settings.sourceLanguage}|${settings.mode}|${found.saved?.translatedLyrics.hashCode()}"
        val remembered = translationMemo?.takeIf { found.translate && it.first == key }?.second
        if (remembered == null) state.script = if (romanized == null) plain else withReadings(romanized, emptyList())
        if (!found.translate) return
        val translated = remembered
            ?: translatedLines(lines, found.saved, settings)?.also { translationMemo = key to it }
            ?: return
        state.script = withReadings(romanized.orEmpty(), translated)
    }

    // a crossfade hands over to a new player, so the listener moves with it
    private suspend fun followPlayer() {
        player.collectLatest { current ->
            if (current == null) return@collectLatest
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    state.playing = isPlaying
                }

                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    state.seekTick++
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    state.seekTick++
                }
            }
            state.playing = current.isPlaying
            state.seekTick++
            current.addListener(listener)
            try {
                awaitCancellation()
            } finally {
                current.removeListener(listener)
            }
        }
    }
}


// everything the pill's lyrics are built from; a change to any of it lays them out again
private data class SongLyrics(
    val saved: LyricsEntity?,
    val shift: Int,
    val settings: ReadingSettings,
    val translate: Boolean,
)
