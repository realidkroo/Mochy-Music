// CastConnectionHandler.kt

package com.mochyy.mochymusic.playback

import android.content.Context
import androidx.compose.runtime.Stable
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.cast.CastDiscovery
import com.mochyy.mochymusic.cast.ChromecastRenderer
import com.mochyy.mochymusic.cast.DlnaRenderer
import com.mochyy.mochymusic.cast.FoundReceiver
import com.mochyy.mochymusic.cast.RemoteMedia
import com.mochyy.mochymusic.cast.RemoteRenderer
import com.mochyy.mochymusic.cast.RemoteState
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.utils.resize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import com.mochyy.mochymusic.models.MediaMetadata as AppMediaMetadata

/** casting without google play services: chromecasts over the cast protocol and dlna renderers over upnp (docs/notes/cast.md) */
@Stable
class CastConnectionHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val musicService: MusicService,
) {
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _castDeviceName = MutableStateFlow<String?>(null)
    val castDeviceName: StateFlow<String?> = _castDeviceName.asStateFlow()

    private val _castPosition = MutableStateFlow(0L)
    val castPosition: StateFlow<Long> = _castPosition.asStateFlow()

    private val _castDuration = MutableStateFlow(0L)
    val castDuration: StateFlow<Long> = _castDuration.asStateFlow()

    private val _castIsPlaying = MutableStateFlow(false)
    val castIsPlaying: StateFlow<Boolean> = _castIsPlaying.asStateFlow()

    private val _castIsBuffering = MutableStateFlow(false)
    val castIsBuffering: StateFlow<Boolean> = _castIsBuffering.asStateFlow()

    private val _castVolume = MutableStateFlow(1.0f)
    val castVolume: StateFlow<Float> = _castVolume.asStateFlow()

    // always false: the phone's player follows the receiver by stepping its own queue, which the service is meant to see
    var isSyncingFromCast: Boolean = false
        private set

    /** a cast receiver on the network, as the player's device sheet lists it */
    data class CastTarget(val id: String, val name: String, val detail: String?)

    private val discovery = CastDiscovery(context)

    val targets: StateFlow<List<CastTarget>> = discovery.found
        .map { found -> found.map { CastTarget(it.id, it.name, it.detail) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var renderer: RemoteRenderer? = null
    private var watchJob: Job? = null
    private var positionJob: Job? = null
    private var currentMediaId: String? = null

    fun initialize(): Boolean = true

    // only while the device sheet is open: searching keeps the wifi radio busy
    fun startDiscovery() = discovery.start()

    fun stopDiscovery() = discovery.stop()

    fun connectTo(id: String) {
        val receiver = discovery.found.value.firstOrNull { it.id == id } ?: return
        scope.launch {
            if (renderer != null) endSession(stopReceiver = true)
            _isConnecting.value = true
            val next: RemoteRenderer = when (receiver) {
                is FoundReceiver.Chromecast -> ChromecastRenderer(receiver.host, receiver.port)
                is FoundReceiver.Dlna -> DlnaRenderer(receiver.avTransportUrl, receiver.renderingControlUrl)
            }
            try {
                next.connect()
            } catch (e: Exception) {
                Timber.w(e, "Couldn't connect to ${receiver.name}")
                runCatching { next.close() }
                _isConnecting.value = false
                TopToaster.error(context.getString(R.string.cast_couldnt_connect, receiver.name))
                return@launch
            }
            renderer = next
            _castDeviceName.value = receiver.name
            _castVolume.value = next.state.value.volume
            _isCasting.value = true
            _isConnecting.value = false
            watch(next)
            loadCurrentMedia()
        }
    }

    fun disconnect() {
        scope.launch { endSession(stopReceiver = true) }
    }

    fun loadCurrentMedia() {
        val metadata = musicService.currentMediaMetadata.value ?: return
        loadMedia(metadata)
    }

    fun loadMedia(metadata: AppMediaMetadata) {
        val target = renderer ?: return
        currentMediaId = metadata.id
        scope.launch {
            _castIsBuffering.value = true
            val player = musicService.player
            val start = if (player.currentMediaItem?.mediaId == metadata.id) player.currentPosition else 0L
            // the receiver plays it now, so the phone stays quiet
            player.pause()
            val stream = musicService.getCastStream(metadata.id)
            if (stream == null || renderer !== target || currentMediaId != metadata.id) {
                if (stream == null) {
                    _castIsBuffering.value = false
                    TopToaster.error(context.getString(R.string.cast_song_failed, _castDeviceName.value ?: ""))
                }
                return@launch
            }
            val media = RemoteMedia(
                url = stream.url,
                mimeType = stream.mimeType,
                title = metadata.title,
                artist = metadata.artists.joinToString(", ") { it.name },
                album = metadata.album?.title,
                artworkUrl = metadata.thumbnailUrl?.resize(1080, 1080),
                durationMs = metadata.duration.toLong().coerceAtLeast(0L) * 1000L,
            )
            runCatching { target.load(media, start) }.onFailure { Timber.w(it, "Couldn't load the song on the receiver") }
        }
    }

    fun play() = command { it.play() }

    fun pause() = command { it.pause() }

    fun seekTo(position: Long) {
        _castPosition.value = position
        command { it.seek(position) }
    }

    fun setVolume(volume: Float) {
        _castVolume.value = volume.coerceIn(0f, 1f)
        command { it.setVolume(volume) }
    }

    // there's no queue on the receiver, the phone keeps it: stepping the phone's queue makes the service load the new song here
    fun skipToNext() {
        val player = musicService.player
        if (player.hasNextMediaItem()) {
            player.pause()
            player.seekToNextMediaItem()
        }
    }

    fun skipToPrevious() {
        val player = musicService.player
        if (player.hasPreviousMediaItem()) {
            player.pause()
            player.seekToPreviousMediaItem()
        }
    }

    fun navigateToMediaIfInQueue(mediaId: String): Boolean = false

    fun release() {
        discovery.stop()
        watchJob?.cancel()
        positionJob?.cancel()
        val last = renderer ?: return
        renderer = null
        // the service is going away with its scope, so the receiver is let go of on a scope of its own
        CoroutineScope(Dispatchers.IO).launch { runCatching { last.close() } }
    }

    private fun command(action: suspend (RemoteRenderer) -> Unit) {
        val target = renderer ?: return
        scope.launch {
            runCatching { action(target) }.onFailure { Timber.w(it, "The receiver didn't take that") }
        }
    }

    private fun watch(target: RemoteRenderer) {
        watchJob?.cancel()
        watchJob = scope.launch {
            var finishedHandled = false
            var failedHandled = false
            target.state.collect { state: RemoteState ->
                if (renderer !== target) return@collect
                _castIsPlaying.value = state.playing
                _castIsBuffering.value = state.buffering
                if (state.durationMs > 0) _castDuration.value = state.durationMs
                _castVolume.value = state.volume
                if (state.lost) {
                    TopToaster.error(context.getString(R.string.cast_lost, _castDeviceName.value ?: ""))
                    endSession(stopReceiver = false)
                    return@collect
                }
                if (state.failed && !failedHandled) {
                    failedHandled = true
                    TopToaster.error(context.getString(R.string.cast_song_failed, _castDeviceName.value ?: ""))
                }
                if (!state.failed) failedHandled = false
                // the song played to its end on the receiver: the phone steps on, and the service loads the next one there
                if (state.finished && !finishedHandled) {
                    finishedHandled = true
                    if (musicService.player.hasNextMediaItem()) skipToNext()
                }
                if (!state.finished) finishedHandled = false
            }
        }
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive && renderer === target) {
                _castPosition.value = target.positionNow()
                delay(PositionTickMs)
            }
        }
    }

    private suspend fun endSession(stopReceiver: Boolean) {
        val last = renderer ?: return
        renderer = null
        watchJob?.cancel()
        positionJob?.cancel()
        val position = last.positionNow()
        runCatching { last.close(stopPlayback = stopReceiver) }
        _isCasting.value = false
        _isConnecting.value = false
        _castDeviceName.value = null
        _castIsPlaying.value = false
        _castIsBuffering.value = false
        // the phone carries on from where the receiver was, paused, as the google cast build does
        val player = musicService.player
        if (position > 0 && player.currentMediaItem?.mediaId == currentMediaId) player.seekTo(position)
        player.pause()
    }

    private companion object {
        const val PositionTickMs = 500L
    }
}
