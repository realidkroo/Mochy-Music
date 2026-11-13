// PlayerConnection.kt

package com.mochyy.mochymusic.playback

import android.content.Context
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.R
import androidx.compose.runtime.Stable
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.extensions.currentMetadata
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.lyrics.VideoLyricsSync
import com.mochyy.mochymusic.extensions.getCurrentQueueIndex
import com.mochyy.mochymusic.extensions.getQueueWindows
import com.mochyy.mochymusic.extensions.isAutoplay
import com.mochyy.mochymusic.extensions.isManualQueued
import com.mochyy.mochymusic.extensions.withAutoplay
import com.mochyy.mochymusic.extensions.metadata
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.songedit.SongEdits
import com.mochyy.mochymusic.playback.MusicService.MusicBinder
import com.mochyy.mochymusic.link.isTelevision
import com.mochyy.mochymusic.playback.queues.Queue
import com.mochyy.mochymusic.utils.NotRecommended
import com.mochyy.mochymusic.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber

@Stable
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    val context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    parentScope: CoroutineScope,
) : Player.Listener {
    private companion object {
        private const val TAG = "PlayerConnection"
        private const val PLAYER_INIT_TIMEOUT_MS = 5000L
        private const val VideoLyricsSyncDelayMs = 1_500L
    }

    // its own job under the parent's, so dispose() can stop the pollers and flows this connection started
    val scope: CoroutineScope =
        CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    val service = binder.service
    private val playerReadinessFlow = service.isPlayerReady

    private fun getPlayerSafe(): ExoPlayer {
        return try {
            if (!playerReadinessFlow.value) {
                Timber.tag(TAG).w("Player accessed before service initialization complete; returning best-effort reference")
            }
            service.player
        } catch (e: UninitializedPropertyAccessException) {
            Timber.tag(TAG).e(e, "Fatal: player property accessed but not initialized")
            throw IllegalStateException("MusicService.player not initialized; possible race condition in service startup", e)
        }
    }

    val player: ExoPlayer
        get() = getPlayerSafe()

    private val isPlayerInitialized = MutableStateFlow(service.isPlayerReady.value)

    val playbackState: MutableStateFlow<Int>
    private val playWhenReady: MutableStateFlow<Boolean>
    val isPlaying: kotlinx.coroutines.flow.StateFlow<Boolean>

    init {
        Timber.tag(TAG).d("PlayerConnection init: playerReady=${playerReadinessFlow.value}")

        val initialState = try {
            val initialPlayer = getPlayerSafe()
            Triple(initialPlayer.playbackState, initialPlayer.playWhenReady,
                   initialPlayer.playWhenReady && initialPlayer.playbackState != STATE_ENDED)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection initialization, using defaults")
            Triple(Player.STATE_IDLE, false, false)
        }

        playbackState = MutableStateFlow(initialState.first)
        playWhenReady = MutableStateFlow(initialState.second)
        // a pause or play fade shows what you pressed straight away, not when the player catches up
        isPlaying = combine(playbackState, playWhenReady, service.transport.playIntent) { state, ready, intent ->
            (intent ?: ready) && state != STATE_ENDED
        }.stateIn(
            scope,
            SharingStarted.Lazily,
            initialState.third
        )

        scope.launch {
            playerReadinessFlow.collect { ready ->
                isPlayerInitialized.value = ready
                if (ready) {
                    Timber.tag(TAG).d("Service player initialization detected by PlayerConnection")
                }
            }
        }

        Timber.tag(TAG).d("PlayerConnection state flows initialized successfully")
    }

    val isEffectivelyPlaying = combine(
        isPlaying,
        service.castConnectionHandler?.isCasting ?: MutableStateFlow(false),
        service.castConnectionHandler?.castIsPlaying ?: MutableStateFlow(false)
    ) { localPlaying, isCasting, castPlaying ->
        if (isCasting) castPlaying else localPlaying
    }.stateIn(
        scope,
        SharingStarted.Lazily,
        player.playbackState != STATE_ENDED && player.playWhenReady
    )

    // the queue's own copy of the playing song, before the hand edits from its edit sheet go over it
    @Volatile private var queueMetadata: MediaMetadata? = player.currentMetadata
    val mediaMetadata = MutableStateFlow(queueMetadata?.let(SongEdits::applyTo))

    init {
        // a song edited while it's already in the queue shows the edit straight away
        scope.launch { SongEdits.all.collect { mediaMetadata.value = queueMetadata?.let(SongEdits::applyTo) } }
    }

    private fun showMetadata(metadata: MediaMetadata?) {
        queueMetadata = metadata
        mediaMetadata.value = metadata?.let(SongEdits::applyTo)
    }

    val currentSong =
        mediaMetadata.flatMapLatest {
            database.song(it?.id)
        }

    /**
     * How many ms the current song's lyrics are shifted. Positive shows each line sooner, so it is
     * added to the playback position wherever lyrics are timed.
     */
    val lyricsOffsetMs: StateFlow<Int> = currentSong
        .map { it?.song?.lyricsOffset ?: 0 }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    val currentLyrics = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.lyrics(mediaMetadata?.id)
    }

    init {
        // parse off the main thread, or the first frames of opening the player pay for it
        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            currentLyrics.map { it?.lyrics }.distinctUntilChanged().collect(LyricsUtils::warmParsedLyrics)
        }
        // a music video shows its lyrics straight away, then they move onto the video's own timing once it's been listened to
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            combine(mediaMetadata, currentLyrics) { metadata, lyrics -> metadata to lyrics }
                .distinctUntilChanged()
                .collectLatest { (metadata, lyrics) ->
                    if (metadata == null || lyrics == null) return@collectLatest
                    // a song skipped straight past never asks youtube anything
                    delay(VideoLyricsSyncDelayMs)
                    runCatching {
                        VideoLyricsSync.syncIfNeeded(context, database, metadata, lyrics) { albumSongId ->
                            service.matchSongToVideo(albumSongId, metadata.id)
                        }
                    }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else reportException(it) }
                }
        }
    }
    val currentFormat =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    /**
     * What [mediaId] is playing as, for the format badge: the service's facts (the decoder's
     * format, a module's codec name) with YouTube's record of its format filling in until the
     * decoder has it, unless a module plays it, whose stream that record doesn't describe.
     */
    fun streamFacts(mediaId: String): kotlinx.coroutines.flow.Flow<StreamFacts?> =
        combine(
            service.streamFacts.map { it[mediaId] }.distinctUntilChanged(),
            database.format(mediaId),
        ) { live, format ->
            val recorded = format?.takeIf { live?.fromModule != true }?.let(StreamFacts::of)
            when {
                live == null -> recorded
                recorded == null -> live
                else -> live.copy(codec = live.codec ?: recorded.codec, sampleRate = live.sampleRate ?: recorded.sampleRate)
            }
        }.distinctUntilChanged()

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val queueItems: kotlinx.coroutines.flow.StateFlow<List<com.mochyy.mochymusic.ui.player.models.QueueItemData>> = queueWindows.map { windows ->
        windows.map(::toQueueItem)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Lazily, emptyList())

    /**
     * The queue's items and the current song's position in them, published as one value from the
     * same player callback. queueItems and currentWindowIndex arrive separately (one goes through
     * a map on another dispatcher), so a reader pairing them saw the new index against the old
     * list for a frame whenever both changed at once - clearing history, toggling shuffle.
     */
    val queueSnapshot = MutableStateFlow(com.mochyy.mochymusic.ui.player.models.QueueState())
    private var snapshotWindows: List<Timeline.Window>? = null
    private var snapshotItems: List<com.mochyy.mochymusic.ui.player.models.QueueItemData> = emptyList()

    private fun toQueueItem(window: Timeline.Window): com.mochyy.mochymusic.ui.player.models.QueueItemData {
        val mediaItem = window.mediaItem
        return com.mochyy.mochymusic.ui.player.models.QueueItemData(
            uid = window.uid.hashCode(),
            mediaId = mediaItem.mediaId,
            title = mediaItem.mediaMetadata.title?.toString() ?: "",
            artist = mediaItem.mediaMetadata.artist?.toString() ?: "",
            artworkUri = mediaItem.mediaMetadata.artworkUri,
            duration = window.durationMs,
            isAutoplay = mediaItem.isAutoplay,
            isManual = mediaItem.isManualQueued,
        )
    }

    private fun publishQueueSnapshot() {
        val windows = queueWindows.value
        // A song change moves the index but not the list: only remap when the windows changed.
        if (windows !== snapshotWindows) {
            snapshotItems = windows.map(::toQueueItem)
            snapshotWindows = windows
        }
        queueSnapshot.value = com.mochyy.mochymusic.ui.player.models.QueueState(
            items = snapshotItems,
            currentIndex = currentWindowIndex.value,
            title = queueTitle.value.orEmpty(),
        )
    }

    // mainactivity turns this off while the app is hidden, the poll below rests then instead of recomposing the player for nobody
    val isUiVisible = MutableStateFlow(true)

    val progressState: kotlinx.coroutines.flow.StateFlow<com.mochyy.mochymusic.ui.player.models.ProgressState> =
        kotlinx.coroutines.flow.callbackFlow {
            while (true) {
                isUiVisible.first { it }
                try {
                    val p = attachedPlayer
                    if (p != null) {
                        val position = p.currentPosition.coerceAtLeast(0L)
                        val duration = p.duration.coerceAtLeast(0L)
                        trySend(
                            com.mochyy.mochymusic.ui.player.models.ProgressState(
                                position = position,
                                duration = duration,
                                percentage = if (duration > 0L) position.toFloat() / duration else 0f,
                            )
                        )
                    }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(66L)
            }
        }.distinctUntilChanged().stateIn(
            scope,
            SharingStarted.Lazily,
            com.mochyy.mochymusic.ui.player.models.ProgressState(),
        )

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val error = MutableStateFlow<PlaybackException?>(null)
    val isMuted = service.isMuted

    val waitingForNetworkConnection = service.waitingForNetworkConnection

    /** A failed stream counting down to its skip, for the seek bars */
    val streamFailure = service.streamFailure

    /** Crossmix has the next song playing over the end of this one */
    val isCrossmixing = service.crossmixMixing

    /** asks the player to open, then open its studio (the selector); from "Customize player" in settings */
    val studioRequests = kotlinx.coroutines.flow.MutableStateFlow(0L)

    fun requestPlayerStudio() {
        studioRequests.value = System.nanoTime()
    }

    /** Crossmix's last decision, for its test tools */
    val crossmixStatus = service.crossmixStatus

    fun testCrossmix() = service.testCrossmix()

    var onSkipPrevious: (() -> Unit)? = null
    var onSkipNext: (() -> Unit)? = null

    private var attachedPlayer: Player? = null

    init {
        try {

            scope.launch {
                service.playerFlow.collect { newPlayer ->
                    if (newPlayer != null && newPlayer != attachedPlayer) {
                        updateAttachedPlayer(newPlayer)
                    }
                }
            }

            if (attachedPlayer == null && service.isPlayerReady.value) {
                 updateAttachedPlayer(player)
            }

            Timber.tag(TAG).d("PlayerConnection flow observer registered")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize PlayerConnection listener or state")

            throw e
        }
    }

    private fun updateAttachedPlayer(newPlayer: Player) {
        attachedPlayer?.removeListener(this)
        attachedPlayer = newPlayer
        newPlayer.addListener(this)

        playbackState.value = newPlayer.playbackState
        playWhenReady.value = newPlayer.playWhenReady
        showMetadata(newPlayer.currentMetadata)
        queueTitle.value = service.queueTitle
        queueWindows.value = newPlayer.getQueueWindows()
        currentWindowIndex.value = newPlayer.getCurrentQueueIndex()
        currentMediaItemIndex.value = newPlayer.currentMediaItemIndex
        shuffleModeEnabled.value = newPlayer.shuffleModeEnabled
        repeatMode.value = newPlayer.repeatMode
        publishQueueSnapshot()

        Timber.tag(TAG).d("Attached to new player instance: $newPlayer")
    }

    // a device this phone steers takes the songs picked here; never on a tv, which steers nothing
    private val remote: com.mochyy.mochymusic.link.remote.RemoteControl? by lazy {
        if (context.isTelevision()) null
        else dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.mochyy.mochymusic.link.remote.RemoteControlEntryPoint::class.java,
        ).remoteControl()
    }

    fun playQueue(queue: Queue) {
        if (remote?.takeQueue(queue) == true) return
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("playQueue called before player ready; delegating to service")
        }
        try {
            service.playQueue(queue)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playQueue")
            throw e
        }
    }

    fun startRadioSeamlessly() {

        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player ready; delegating to service")
        }
        try {
            service.startRadioSeamlessly()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in startRadioSeamlessly")
            throw e
        }
    }

    fun setInfiniteQueueEnabled(enabled: Boolean) = service.setInfiniteQueueEnabled(enabled)

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        if (remote?.takeNext(items) == true) return
        try {
            service.playNext(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playNext")
            throw e
        }
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        if (remote?.takeQueueEnd(items) == true) return
        try {
            service.addToQueue(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in addToQueue")
            throw e
        }
        val first = items.firstOrNull() ?: return
        TopToaster.show(
            text = if (items.size == 1) "Added ${first.mediaMetadata.title ?: "song"} to Queue" else "Added ${items.size} songs to Queue",
            thumbnail = first.mediaMetadata.artworkUri?.toString(),
            iconRes = R.drawable.queue_music,
        )
    }

    // Queue page operations. Items are addressed by the uid the queue list was built from
    // (QueueItemData.uid) and looked up again at call time, so a timeline change between the
    // tap and the call can't land the action on the wrong song.

    private fun mediaIndexOf(uid: Int): Int {
        val timeline = player.currentTimeline
        val window = Timeline.Window()
        for (i in 0 until timeline.windowCount) {
            if (timeline.getWindow(i, window).uid.hashCode() == uid) return i
        }
        return C.INDEX_UNSET
    }

    /** Media item indices in the order they will play, shuffle included. */
    private fun playOrder(): MutableList<Int> {
        val timeline = player.currentTimeline
        val order = ArrayList<Int>(timeline.windowCount)
        if (timeline.isEmpty) return order
        val shuffle = player.shuffleModeEnabled
        var index = timeline.getFirstWindowIndex(shuffle)
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += index
            index = timeline.getNextWindowIndex(index, REPEAT_MODE_OFF, shuffle)
        }
        return order
    }

    // a tap in the queue fades or blends into the song like a skip does; the uid is looked up again when the song really changes
    fun playQueueItem(uid: Int) {
        if (mediaIndexOf(uid) == C.INDEX_UNSET) return
        try {
            service.transport.jumpTo { mediaIndexOf(uid) }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playQueueItem")
        }
    }

    /** Moves a song to play straight after [afterUid]. */
    fun moveQueueItem(uid: Int, afterUid: Int) {
        val from = mediaIndexOf(uid)
        val after = mediaIndexOf(afterUid)
        if (from == C.INDEX_UNSET || after == C.INDEX_UNSET || from == after) return
        try {
            if (!player.shuffleModeEnabled) {
                // moveMediaItem takes the final index, counted once the song is out of the way.
                val to = if (from < after) after else after + 1
                if (to != from) player.moveMediaItem(from, to)
            } else {
                // Shuffled, the play order is the shuffle order, not the item order: reorder that.
                val order = playOrder()
                order.remove(from)
                order.add(order.indexOf(after) + 1, from)
                player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.currentTimeMillis()))
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in moveQueueItem")
        }
    }

    /** Takes a song out of wherever it is in the queue and puts it next, as one the user queued. */
    fun playQueueItemNext(uid: Int) {
        val index = mediaIndexOf(uid)
        if (index == C.INDEX_UNSET || index == player.currentMediaItemIndex) return
        try {
            val item = player.getMediaItemAt(index).withAutoplay(false)
            player.removeMediaItem(index)
            service.playNext(listOf(item))
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playQueueItemNext")
        }
    }

    /** Removes songs from the queue. The one playing is never removed. */
    fun removeQueueItems(uids: Collection<Int>) {
        if (uids.isEmpty()) return
        val wanted = uids.toHashSet()
        val timeline = player.currentTimeline
        val window = Timeline.Window()
        val current = player.currentMediaItemIndex
        val indices = (0 until timeline.windowCount).filter {
            it != current && timeline.getWindow(it, window).uid.hashCode() in wanted
        }
        if (indices.isEmpty()) return
        // kept for the Undo, which puts each one back where it was
        val removed = indices.map { it to player.getMediaItemAt(it) }
        try {
            // Back to front in contiguous runs: one timeline change per run instead of per song,
            // and earlier indices stay valid while later ones go.
            var end = indices.size - 1
            while (end >= 0) {
                var start = end
                while (start > 0 && indices[start - 1] == indices[start] - 1) start--
                player.removeMediaItems(indices[start], indices[end] + 1)
                end = start - 1
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in removeQueueItems")
            return
        }
        val first = removed.first().second
        TopToaster.show(
            text = if (removed.size == 1) "Removed ${first.mediaMetadata.title ?: "song"} from queue" else "Removed ${removed.size} songs from queue",
            thumbnail = first.mediaMetadata.artworkUri?.toString(),
            iconRes = R.drawable.queue_music,
            actionLabel = "Undo",
            onAction = {
                // ascending, so each lands at its old index once the ones before it are back
                removed.forEach { (index, item) -> player.addMediaItem(index.coerceAtMost(player.mediaItemCount), item) }
            },
        )
    }

    // the song playing now stops coming back in autoplay, radios and home, and we skip past it right away
    fun stopRecommendingCurrent() {
        val metadata = player.currentMetadata ?: return
        NotRecommended.add(metadata.id)
        seekToNext()
        TopToaster.show(
            text = context.getString(R.string.not_recommended_done),
            thumbnail = metadata.thumbnailUrl,
            iconRes = R.drawable.thumb_down,
            actionLabel = context.getString(R.string.undo),
            onAction = { NotRecommended.remove(metadata.id) },
        )
    }

    fun toggleLike() {
        try {
            service.toggleLike()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLike")
        }
    }

    fun toggleMute() {
        service.toggleMute()
    }

    fun setMuted(muted: Boolean) {
        service.setMuted(muted)
    }

    fun toggleLibrary() {
        try {
            service.toggleLibrary()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLibrary")
        }
    }

    fun togglePlayPause() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                if (castHandler.castIsPlaying.value) {
                    castHandler.pause()
                } else {
                    castHandler.play()
                }
            } else {
                service.transport.togglePlayPause()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in togglePlayPause")
        }
    }

    fun play() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.play()
            } else {
                service.transport.play()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in play")
        }
    }

    fun pause() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.pause()
            } else {
                service.transport.pause()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in pause")
        }
    }

    fun seekTo(position: Long) {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.seekTo(position)
            } else {
                service.transport.cancel()
                player.seekTo(position)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekTo")
        }
    }

    fun seekToNext() {
        try {

            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToNext()
                return
            }
            service.transport.skipNext()
            onSkipNext?.invoke()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToNext")
        }
    }

    var onRestartSong: (() -> Unit)? = null

    // a swipe back: always the song before, never a restart, faded or blended like a skip
    fun skipToPreviousSong() {
        try {
            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToPrevious()
                return
            }
            service.transport.jumpTo { player.previousMediaItemIndex }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in skipToPreviousSong")
        }
    }

    fun seekToPrevious() {
        try {

            val castHandler = service.castConnectionHandler
            if (castHandler?.isCasting?.value == true) {
                castHandler.skipToPrevious()
                return
            }

            val restarts = player.currentPosition > 3000 || !player.hasPreviousMediaItem()
            service.transport.skipPrevious()
            if (restarts) onRestartSong?.invoke() else onSkipPrevious?.invoke()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToPrevious")
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        playWhenReady.value = newPlayWhenReady
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        showMetadata(mediaItem?.metadata)
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        publishQueueSnapshot()
        updateCanSkipPreviousAndNext()
    }

    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
        super.onTracksChanged(tracks)
        val format = player.audioFormat ?: return
        val mediaItem = player.currentMediaItem ?: return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val isLocal = database.song(mediaItem.mediaId).first()?.song?.isLocal == true
                if (isLocal) {
                    database.query {
                        upsert(
                            com.mochyy.mochymusic.db.entities.FormatEntity(
                                id = mediaItem.mediaId,
                                itag = -1,
                                mimeType = format.sampleMimeType?.split(";")?.get(0) ?: "audio/flac",
                                codecs = format.codecs ?: "",
                                bitrate = format.bitrate,
                                sampleRate = format.sampleRate,
                                contentLength = 0L,
                                loudnessDb = null,
                                perceptualLoudnessDb = null,
                                playbackUrl = null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error updating local format entity")
            }
        }
    }

    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
        super.onMediaMetadataChanged(mediaMetadata)
        val artworkData = mediaMetadata.artworkData ?: return
        val mediaItem = player.currentMediaItem ?: return

        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val songEntity = database.song(mediaItem.mediaId).first() ?: return@launch
                val thumbnailUrl = songEntity.song.thumbnailUrl
                // a cover file that was cleared away counts as none, so playing the song brings it back
                if (songEntity.song.isLocal &&
                    (thumbnailUrl == null || com.mochyy.mochymusic.importer.LocalArtwork.needsRepair(context, thumbnailUrl))
                ) {
                    val newThumbnailUrl = com.mochyy.mochymusic.importer.LocalArtwork.save(context, mediaItem.mediaId, artworkData)
                        ?: return@launch
                    database.query {
                        upsert(songEntity.song.copy(thumbnailUrl = newThumbnailUrl))
                    }
                    Timber.tag(TAG).d("Extracted local artwork to $newThumbnailUrl")

                    val currentMetadata = queueMetadata
                    if (currentMetadata?.id == mediaItem.mediaId) {
                        showMetadata(currentMetadata.copy(thumbnailUrl = newThumbnailUrl))
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error extracting local artwork")
            }
        }
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        publishQueueSnapshot()
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        publishQueueSnapshot()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty) {
            val window =
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                    !window.isLive ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive &&
                    window.isDynamic ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    val uiState: com.mochyy.mochymusic.ui.player.PlayerUiState by lazy { com.mochyy.mochymusic.ui.player.PlayerUiState(this) }

    fun dispose() {
        try {
            attachedPlayer?.removeListener(this)
            attachedPlayer = null
            scope.cancel()
            Timber.tag(TAG).d("PlayerConnection disposed successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection disposal")
        }
    }
}
