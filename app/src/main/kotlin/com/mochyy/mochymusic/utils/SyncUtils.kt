// SyncUtils.kt

package com.mochyy.mochymusic.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.utils.completed
import com.music.innertube.utils.parseCookieString
import com.mochyy.mochymusic.constants.InnerTubeCookieKey
import com.mochyy.mochymusic.constants.LastFullSyncKey
import com.mochyy.mochymusic.constants.SYNC_COOLDOWN
import com.mochyy.mochymusic.constants.SelectedYtmPlaylistsKey
import com.mochyy.mochymusic.constants.YtExcludeImportsKey
import com.mochyy.mochymusic.constants.YtPullHistoryKey
import com.mochyy.mochymusic.constants.YtSyncHoldKey
import com.mochyy.mochymusic.constants.YtSyncLikedSongsKey
import com.mochyy.mochymusic.constants.YtSyncSourcesTaggedKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Event
import com.mochyy.mochymusic.importer.PlaylistAddedHere
import com.mochyy.mochymusic.scrobble.Scrobbler
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.ImportedPlaylist
import com.mochyy.mochymusic.db.entities.ImportedSong
import com.mochyy.mochymusic.db.entities.PlaylistEntity
import com.mochyy.mochymusic.db.entities.PlaylistSongMap
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.db.entities.SongEntity
import com.mochyy.mochymusic.extensions.collectLatest
import com.mochyy.mochymusic.extensions.isInternetConnected
import com.mochyy.mochymusic.extensions.isSyncEnabled
import com.mochyy.mochymusic.importer.ImportSource
import com.mochyy.mochymusic.importer.PendingYouTubeLikes
import com.mochyy.mochymusic.importer.SyncUndo
import com.mochyy.mochymusic.importer.YouTubeLikesMemory
import com.mochyy.mochymusic.models.toMediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncOperation {
    data object FullSync : SyncOperation()
    data object LikedSongs : SyncOperation()
    data object LibrarySongs : SyncOperation()
    data object UploadedSongs : SyncOperation()
    data object LikedAlbums : SyncOperation()
    data object UploadedAlbums : SyncOperation()
    data object ArtistsSubscriptions : SyncOperation()
    data object SavedPlaylists : SyncOperation()
    data object AutoSyncPlaylists : SyncOperation()
    data class SinglePlaylist(val browseId: String, val playlistId: String) : SyncOperation()
    data class LikeSong(val song: SongEntity) : SyncOperation()
    data class History(val maxRequests: Int = 8) : SyncOperation()
    data object CleanupDuplicates : SyncOperation()
    data object ClearAllSynced : SyncOperation()
}

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Error(val message: String) : SyncStatus()
    data object Completed : SyncStatus()
}

data class SyncState(
    val overallStatus: SyncStatus = SyncStatus.Idle,
    val likedSongs: SyncStatus = SyncStatus.Idle,
    val librarySongs: SyncStatus = SyncStatus.Idle,
    val uploadedSongs: SyncStatus = SyncStatus.Idle,
    val likedAlbums: SyncStatus = SyncStatus.Idle,
    val uploadedAlbums: SyncStatus = SyncStatus.Idle,
    val artists: SyncStatus = SyncStatus.Idle,
    val playlists: SyncStatus = SyncStatus.Idle,
    val currentOperation: String = ""
)

@Singleton
class SyncUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val scrobbler: Scrobbler,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Timber.e(throwable, "Sync coroutine exception")
        }
    }

    private val syncJob = SupervisorJob()
    private val syncScope = CoroutineScope(Dispatchers.IO + syncJob + exceptionHandler)

    private val syncChannel = Channel<SyncOperation>(Channel.BUFFERED)
    private var processingJob: Job? = null

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val DB_OPERATION_DELAY_MS = 50L
        const val HISTORY_PAGES_AUTO = 6
        const val HISTORY_PAGES_FULL = 40
        private val DayFormatters = listOf("MMMM d, yyyy", "MMM d, yyyy", "d MMMM yyyy", "d MMM yyyy")
            .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
        private val DayNoYearFormatters = listOf("MMMM d", "MMM d", "d MMMM", "d MMM")
            .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
        private val MonthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    }

    init {
        startProcessingQueue()
    }

    private fun startProcessingQueue() {
        processingJob = syncScope.launch {
            for (operation in syncChannel) {
                try {
                    processOperation(operation)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Error processing sync operation: $operation")
                }
            }
        }
    }

    private suspend fun processOperation(operation: SyncOperation) {
        when (operation) {
            is SyncOperation.FullSync -> executeFullSync(manual = false)
            is SyncOperation.LikedSongs -> executeSyncLikedSongs()
            is SyncOperation.LibrarySongs -> executeSyncLibrarySongs()
            is SyncOperation.UploadedSongs -> executeSyncUploadedSongs()
            is SyncOperation.LikedAlbums -> executeSyncLikedAlbums()
            is SyncOperation.UploadedAlbums -> executeSyncUploadedAlbums()
            is SyncOperation.ArtistsSubscriptions -> executeSyncArtistsSubscriptions()
            is SyncOperation.SavedPlaylists -> executeSyncSavedPlaylists()
            is SyncOperation.AutoSyncPlaylists -> executeSyncAutoSyncPlaylists()
            is SyncOperation.SinglePlaylist -> executeSyncPlaylist(operation.browseId, operation.playlistId)
            is SyncOperation.LikeSong -> executeLikeSong(operation.song)
            is SyncOperation.History -> runCatching { pullYouTubeHistory(operation.maxRequests) }
                .onFailure { Timber.e(it, "Failed to sync YouTube history") }
            is SyncOperation.CleanupDuplicates -> executeCleanupDuplicatePlaylists()
            is SyncOperation.ClearAllSynced -> executeClearAllSyncedContent()
        }
    }

    private suspend fun isLoggedIn(): Boolean {
        return try {
            val cookie = context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .first()
            cookie?.let {
                val parsed = parseCookieString(it)
                "SAPISID" in parsed || "__Secure-3PAPISID" in parsed
            } ?: false
        } catch (e: Exception) {
            Timber.e(e, "Error checking login status")
            false
        }
    }

    /** Retries a Result-returning YouTube call with exponential backoff, unwrapping Result.failure so retries actually fire. */
    private suspend fun <T> withRetryResult(
        maxRetries: Int = MAX_RETRIES,
        initialDelay: Long = INITIAL_RETRY_DELAY_MS,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelay
        var lastError: Throwable? = null
        repeat(maxRetries) { attempt ->
            try {
                val result = block()
                if (result.isSuccess) return result
                lastError = result.exceptionOrNull()
                Timber.w(lastError, "Attempt ${attempt + 1}/$maxRetries failed")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Timber.w(e, "Attempt ${attempt + 1}/$maxRetries threw")
            }
            if (attempt < maxRetries - 1) {
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return Result.failure(lastError ?: Exception("Max retries exceeded"))
    }

    private fun updateState(update: SyncState.() -> SyncState) {
        _syncState.value = _syncState.value.update()
    }

    fun performFullSync() {
        syncScope.launch {
            syncChannel.send(SyncOperation.FullSync)
        }
    }

    suspend fun performFullSyncSuspend(manual: Boolean = false) {
        if (!isLoggedIn()) {
            Timber.w("Skipping full sync - user not logged in")
            return
        }
        executeFullSync(manual)
    }

    fun tryAutoSync() {
        syncScope.launch {
            if (!isLoggedIn()) {
                Timber.d("Skipping auto sync - user not logged in")
                return@launch
            }

            if (!context.isSyncEnabled() || !context.isInternetConnected()) {
                return@launch
            }

            val lastSync = context.dataStore.get(LastFullSyncKey, 0L)
            val currentTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            if (lastSync > 0 && (currentTime - lastSync) < SYNC_COOLDOWN) {
                return@launch
            }

            syncChannel.send(SyncOperation.FullSync)

            context.dataStore.edit { settings ->
                settings[LastFullSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            }
        }
    }

    fun runAllSyncs() {
        performFullSync()
    }

    fun likeSong(s: SongEntity) {
        syncScope.launch {
            syncChannel.send(SyncOperation.LikeSong(s))
        }
    }

    fun syncLikedSongs() {
        syncScope.launch {
            syncChannel.send(SyncOperation.LikedSongs)
        }
    }

    fun syncLibrarySongs() {
        syncScope.launch {
            syncChannel.send(SyncOperation.LibrarySongs)
        }
    }

    fun syncUploadedSongs() {
        syncScope.launch {
            syncChannel.send(SyncOperation.UploadedSongs)
        }
    }

    fun syncLikedAlbums() {
        syncScope.launch {
            syncChannel.send(SyncOperation.LikedAlbums)
        }
    }

    fun syncUploadedAlbums() {
        syncScope.launch {
            syncChannel.send(SyncOperation.UploadedAlbums)
        }
    }

    fun syncArtistsSubscriptions() {
        syncScope.launch {
            syncChannel.send(SyncOperation.ArtistsSubscriptions)
        }
    }

    fun syncSavedPlaylists() {
        syncScope.launch {
            syncChannel.send(SyncOperation.SavedPlaylists)
        }
    }

    fun syncAutoSyncPlaylists() {
        syncScope.launch {
            syncChannel.send(SyncOperation.AutoSyncPlaylists)
        }
    }

    fun syncPlaylist(browseId: String, playlistId: String) {
        syncScope.launch {
            executeSyncPlaylist(browseId, playlistId)
        }
    }

    fun syncAllAlbums() {
        syncScope.launch {
            syncChannel.send(SyncOperation.LikedAlbums)
            syncChannel.send(SyncOperation.UploadedAlbums)
        }
    }

    fun syncAllArtists() {
        syncScope.launch {
            syncChannel.send(SyncOperation.ArtistsSubscriptions)
        }
    }

    fun syncHistory(maxRequests: Int = HISTORY_PAGES_AUTO) {
        syncScope.launch {
            syncChannel.send(SyncOperation.History(maxRequests))
        }
    }

    fun cleanupDuplicatePlaylists() {
        syncScope.launch {
            syncChannel.send(SyncOperation.CleanupDuplicates)
        }
    }

    fun clearAllSyncedContent() {
        syncScope.launch {
            syncChannel.send(SyncOperation.ClearAllSynced)
        }
    }

    suspend fun syncLikedSongsSuspend() = executeSyncLikedSongs()
    suspend fun syncLibrarySongsSuspend() = executeSyncLibrarySongs()
    suspend fun syncUploadedSongsSuspend() = executeSyncUploadedSongs()
    suspend fun syncLikedAlbumsSuspend() = executeSyncLikedAlbums()
    suspend fun syncUploadedAlbumsSuspend() = executeSyncUploadedAlbums()
    suspend fun syncArtistsSubscriptionsSuspend() = executeSyncArtistsSubscriptions()
    suspend fun syncSavedPlaylistsSuspend() { executeSyncSavedPlaylists() }
    suspend fun syncAutoSyncPlaylistsSuspend() = executeSyncAutoSyncPlaylists()
    suspend fun cleanupDuplicatePlaylistsSuspend() = executeCleanupDuplicatePlaylists()
    suspend fun clearAllSyncedContentSuspend() = executeClearAllSyncedContent()

    suspend fun syncAllAlbumsSuspend() {
        executeSyncLikedAlbums()
        executeSyncUploadedAlbums()
    }

    suspend fun syncAllArtistsSuspend() {
        executeSyncArtistsSubscriptions()
    }

    private suspend fun executeFullSync(manual: Boolean) = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping full sync - user not logged in")
            return@withContext
        }
        if (!manual && context.dataStore.get(YtSyncHoldKey, false)) {
            Timber.d("Skipping auto sync - an undo is holding YouTube pulls")
            return@withContext
        }

        SyncUndo.begin(context, database)

        updateState { copy(overallStatus = SyncStatus.Syncing, currentOperation = "Starting full sync") }

        try {

            if (context.dataStore.get(YtSyncLikedSongsKey, true)) {
                executeSyncLikedSongs()
                delay(DB_OPERATION_DELAY_MS)
            }

            executeSyncLibrarySongs()
            delay(DB_OPERATION_DELAY_MS)

            executeSyncUploadedSongs()
            delay(DB_OPERATION_DELAY_MS)

            executeSyncLikedAlbums()
            delay(DB_OPERATION_DELAY_MS)

            executeSyncUploadedAlbums()
            delay(DB_OPERATION_DELAY_MS)

            executeSyncArtistsSubscriptions()
            delay(DB_OPERATION_DELAY_MS)

            val syncedBrowseIds = executeSyncSavedPlaylists()
            delay(DB_OPERATION_DELAY_MS)

            executeSyncAutoSyncPlaylists(skipBrowseIds = syncedBrowseIds)

            if (!manual && context.dataStore.get(YtPullHistoryKey, true)) {
                delay(DB_OPERATION_DELAY_MS)
                runCatching { pullYouTubeHistory(HISTORY_PAGES_AUTO) }
                    .onFailure { Timber.w(it, "Auto history pull failed") }
            }

            context.dataStore.edit {
                it[YtSyncSourcesTaggedKey] = true
                it[LastFullSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            }
            updateState { copy(overallStatus = SyncStatus.Completed, currentOperation = "") }
            Timber.d("Full sync completed successfully")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error during full sync")
            updateState { copy(overallStatus = SyncStatus.Error(e.message ?: "Unknown error"), currentOperation = "") }
        }
    }

    /** Songs imported from another service, when the user keeps those off YouTube. */
    private fun excludedFromYouTube(): Set<String> {
        if (!context.dataStore.get(YtExcludeImportsKey, false)) return emptySet()
        return database.importedSongIdsExcept(ImportSource.YOUTUBE_MUSIC.key).toSet()
    }

    /** See [YtSyncSourcesTaggedKey]: until one full sync has run, everything YouTube has is marked. */
    private fun sourcesTagged(): Boolean = context.dataStore.get(YtSyncSourcesTaggedKey, false)

    /** Marks a song as one YouTube brought in, so the Library lists it under YouTube Music. */
    private fun MusicDatabase.tagFromYouTube(songId: String, now: LocalDateTime) =
        insert(ImportedSong(songId = songId, source = ImportSource.YOUTUBE_MUSIC.key, importedAt = now))

    private suspend fun executeLikeSong(s: SongEntity) = withContext(Dispatchers.IO) {
        // before the YouTube check: a love on Last.fm or Libre.fm doesn't need a YouTube account
        val artist = database.song(s.id).firstOrNull()?.artists?.firstOrNull()?.name
        if (artist != null) scrobbler.love(artist, s.title, s.liked)

        if (s.isLocal) return@withContext
        if (s.id in excludedFromYouTube()) return@withContext

        if (!isLoggedIn()) {
            Timber.w("Skipping likeSong - user not logged in")
            return@withContext
        }

        // Persist desired state first so an offline tap or failed call is pushed on the next sync instead of being overwritten.
        PendingYouTubeLikes.set(context, s.id, s.liked)

        if (!context.isInternetConnected()) {
            Timber.d("Offline: queued ${if (s.liked) "like" else "unlike"} for ${s.id}")
            return@withContext
        }

        withRetryResult {
            YouTube.likeVideo(s.id, s.liked)
        }.onSuccess {
            PendingYouTubeLikes.remove(context, listOf(s.id))
            if (s.liked) {
                YouTubeLikesMemory.markSeen(context, s.id)
            } else {
                YouTubeLikesMemory.unmarkSeen(context, s.id)
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync like=${s.liked} for ${s.id}; will retry on next sync")
        }
    }

    private suspend fun executeSyncLikedSongs() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncLikedSongs - user not logged in")
            return@withContext
        }

        updateState { copy(likedSongs = SyncStatus.Syncing, currentOperation = "Syncing liked songs") }

        YouTubeLikesMemory.exclusive { pullLikedSongs() }
    }

    private suspend fun pullLikedSongs() {
        withRetryResult {
            YouTube.playlist("LM").completed()
        }.onSuccess { page ->
            try {
                val rawRemoteSongs = page.songs
                val rawRemoteIds = rawRemoteSongs.map { it.id }.toSet()
                val localSongs = database.likedSongsByNameAsc().first()
                val localIds = localSongs.map { it.id }.toSet()
                val excluded = excludedFromYouTube()

                // Flush any pending local likes/unlikes BEFORE reconciling YouTube's list so local changes always win.
                val pendingStates = PendingYouTubeLikes.readStates(context)
                val pushedLikes = mutableSetOf<String>()
                val keptLikes = mutableSetOf<String>()
                val pushedUnlikes = mutableSetOf<String>()
                val keptUnlikes = mutableSetOf<String>()

                if (pendingStates.isNotEmpty()) {
                    val donePending = mutableSetOf<String>()
                    for ((id, wantedLiked) in pendingStates) {
                        val isLikedLocally = id in localIds
                        if (wantedLiked) {
                            when {
                                !isLikedLocally || id in rawRemoteIds -> donePending += id
                                id in excluded -> {
                                    keptLikes += id
                                    donePending += id
                                }
                                else -> {
                                    if (YouTube.likeVideo(id, true).isSuccess) {
                                        pushedLikes += id
                                        donePending += id
                                    } else {
                                        keptLikes += id
                                    }
                                    delay(150)
                                }
                            }
                        } else {
                            when {
                                isLikedLocally || id !in rawRemoteIds -> donePending += id
                                id in excluded -> {
                                    keptUnlikes += id
                                    donePending += id
                                }
                                else -> {
                                    if (YouTube.likeVideo(id, false).isSuccess) {
                                        pushedUnlikes += id
                                        donePending += id
                                    } else {
                                        keptUnlikes += id
                                    }
                                    delay(150)
                                }
                            }
                        }
                    }
                    PendingYouTubeLikes.remove(context, donePending)
                    SyncUndo.recordPushedLikes(context, pushedLikes)
                }

                val suppressedUnlikes = pushedUnlikes + keptUnlikes
                val remoteSongs = rawRemoteSongs.filterNot { it.id in suppressedUnlikes }
                val remoteIds = remoteSongs.map { it.id }.toSet()
                val effectiveRemoteIds = remoteIds + pushedLikes

                unlikeDroppedOnYouTube(
                    localSongs = localSongs,
                    remoteIds = effectiveRemoteIds,
                    wholeList = page.songsContinuation == null,
                    keep = pushedLikes + keptLikes,
                )

                val now = LocalDateTime.now()
                val tagAll = !sourcesTagged()
                // batch existing lookups instead of opening a room flow per song
                val existingById = HashMap<String, Song>(remoteSongs.size)
                localSongs.forEach { existingById[it.id] = it }
                val missingIds = remoteSongs.map { it.id }.filter { it !in existingById }
                missingIds.chunked(500).forEach { chunk ->
                    database.getSongsByIds(chunk).forEach { existingById[it.id] = it }
                }
                // only rewrite likedDate timestamps when order actually changed, avoiding hundreds of no-op writes that trigger room invalidation storms
                val localOrder = localSongs
                    .filter { it.id in remoteIds }
                    .sortedByDescending { it.song.likedDate ?: LocalDateTime.MIN }
                    .map { it.id }
                val remoteOrder = remoteSongs.map { it.id }.filter { it in existingById && existingById[it]?.song?.liked == true }
                val orderChanged = localOrder != remoteOrder

                remoteSongs.withIndex().chunked(100).forEach { batch ->
                    val work = batch.filter { (_, song) ->
                        val dbSong = existingById[song.id]
                        tagAll ||
                            dbSong == null ||
                            !dbSong.song.liked ||
                            dbSong.song.likedDate == null ||
                            orderChanged ||
                            dbSong.song.isVideo != song.isVideoSong
                    }
                    if (work.isEmpty()) return@forEach
                    database.transaction {
                        work.forEach { (index, song) ->
                            try {
                                val dbSong = existingById[song.id]
                                val isVideoSong = song.isVideoSong
                                val timestamp = now.minusSeconds(index.toLong())
                                if (tagAll || dbSong == null || !dbSong.song.liked) tagFromYouTube(song.id, now)
                                if (dbSong == null) {
                                    insert(song.toMediaMetadata()) {
                                        it.copy(liked = true, likedDate = timestamp, isVideo = isVideoSong)
                                    }
                                } else {
                                    val nextLikedDate = if (!dbSong.song.liked || dbSong.song.likedDate == null || orderChanged) {
                                        timestamp
                                    } else {
                                        dbSong.song.likedDate
                                    }
                                    if (!dbSong.song.liked || dbSong.song.likedDate != nextLikedDate || dbSong.song.isVideo != isVideoSong) {
                                        update(dbSong.song.copy(liked = true, likedDate = nextLikedDate, isVideo = isVideoSong))
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to process song: ${song.id}")
                            }
                        }
                    }
                    delay(DB_OPERATION_DELAY_MS)
                }

                updateState { copy(likedSongs = SyncStatus.Completed) }
                Timber.d("Synced ${remoteSongs.size} liked songs (pushed +${pushedLikes.size}/-${pushedUnlikes.size})")
            } catch (e: Exception) {
                Timber.e(e, "Error processing liked songs")
                updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync liked songs after retries")
            updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    // the sync used to unlike every song missing from youtube's list, so one short list wiped hundreds of likes
    private suspend fun unlikeDroppedOnYouTube(localSongs: List<Song>, remoteIds: Set<String>, wholeList: Boolean, keep: Set<String>) {
        val seen = YouTubeLikesMemory.seen(context)
        val droppedIds = YouTubeLikesMemory.droppedOnYouTube(localSongs.map { it.id }, seen, remoteIds, wholeList, keep)
        if (droppedIds == null) {
            Timber.w("Liked songs list can't be trusted (${remoteIds.size} songs, whole list: $wholeList), keeping every like")
            YouTubeLikesMemory.rememberSeen(context, seen.orEmpty() + remoteIds)
            return
        }
        val dropped = localSongs.filter { it.id in droppedIds }
        YouTubeLikesMemory.recordRemoved(context, dropped.associate { it.id to it.song.likedDate })
        dropped.forEach { song ->
            try {
                database.update(song.song.localToggleLike())
                delay(DB_OPERATION_DELAY_MS)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update song: ${song.id}")
            }
        }
        YouTubeLikesMemory.rememberSeen(context, remoteIds)
    }

    private suspend fun executeSyncLibrarySongs() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncLibrarySongs - user not logged in")
            return@withContext
        }

        updateState { copy(librarySongs = SyncStatus.Syncing, currentOperation = "Syncing library songs") }

        withRetryResult {
            YouTube.library("FEmusic_liked_videos").completed()
        }.onSuccess { page ->
            try {
                val remoteSongs = page.items.filterIsInstance<SongItem>().reversed()
                val remoteIds = remoteSongs.map { it.id }.toSet()
                val localSongs = database.songsByNameAsc().first()
                // Music brought in by an import is in the library on purpose and is not in the
                // YouTube one: without this the sync would take each of those songs back out.
                // What sync itself brought in is left out of that, so it can still leave again;
                // a YouTube import run through an import session stays protected.
                val importedIds = (
                    database.importedSongIdsExcept(ImportSource.YOUTUBE_MUSIC.key) +
                        database.importSessionSongIds()
                    ).toSet()

                // a short list from youtube would take every song past the cut out of the library, so that sync only adds
                val wholeList = page.continuation == null
                if (!wholeList) Timber.w("Library songs came back short (${remoteIds.size}), keeping every song")
                // Files imported from the phone are never in the YouTube library either.
                val leaving = if (wholeList) localSongs.filterNot { it.song.isLocal || it.id in remoteIds || it.id in importedIds } else emptyList()
                leaving.forEach { song ->
                    try {
                        // syncToYouTube = false: we are applying YouTube's own state locally, not pushing back
                        database.update(song.song.toggleLibrary(syncToYouTube = false))
                        delay(DB_OPERATION_DELAY_MS)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to update song: ${song.id}")
                    }
                }

                val now = LocalDateTime.now()
                val tagAll = !sourcesTagged()
                val existingById = HashMap<String, Song>(remoteSongs.size)
                localSongs.forEach { existingById[it.id] = it }
                val missingIds = remoteSongs.map { it.id }.filter { it !in existingById }
                missingIds.chunked(500).forEach { chunk ->
                    database.getSongsByIds(chunk).forEach { existingById[it.id] = it }
                }
                // batch changes per chunk so unchanged songs don't open empty transactions or invalidate room observers
                remoteSongs.chunked(100).forEach { batch ->
                    val work = batch.filter { song ->
                        val dbSong = existingById[song.id]
                        tagAll || dbSong == null || dbSong.song.inLibrary == null
                    }
                    if (work.isEmpty()) return@forEach
                    database.transaction {
                        work.forEach { song ->
                            try {
                                val dbSong = existingById[song.id]
                                if (tagAll || dbSong == null || dbSong.song.inLibrary == null) tagFromYouTube(song.id, now)
                                if (dbSong == null) {
                                    insert(song.toMediaMetadata()) { it.toggleLibrary(syncToYouTube = false) }
                                } else if (dbSong.song.inLibrary == null) {
                                    // youtube puts every liked song in its library, those keep their album out like a like here does
                                    update(dbSong.song.toggleLibrary(syncToYouTube = false, byLike = dbSong.song.liked))
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to process song: ${song.id}")
                            }
                        }
                    }
                    delay(DB_OPERATION_DELAY_MS)
                }

                updateState { copy(librarySongs = SyncStatus.Completed) }
                Timber.d("Synced ${remoteSongs.size} library songs")
            } catch (e: Exception) {
                Timber.e(e, "Error processing library songs")
                updateState { copy(librarySongs = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync library songs after retries")
            updateState { copy(librarySongs = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncUploadedSongs() = withContext(Dispatchers.IO) {
        Timber.d("[UPLOAD_DEBUG] executeSyncUploadedSongs() started")
        if (!isLoggedIn()) {
            Timber.w("[UPLOAD_DEBUG] Skipping syncUploadedSongs - user not logged in")
            return@withContext
        }
        Timber.d("[UPLOAD_DEBUG] User is logged in, proceeding with sync")

        updateState { copy(uploadedSongs = SyncStatus.Syncing, currentOperation = "Syncing uploaded songs") }

        withRetryResult {
            YouTube.library("FEmusic_library_privately_owned_tracks", tabIndex = 1).completed()
        }.onSuccess { page ->
            try {
                val remoteSongs = page.items.filterIsInstance<SongItem>().reversed()
                val remoteIds = remoteSongs.map { it.id }.toSet()
                val localSongs = database.uploadedSongsByNameAsc().first()

                val songsToRemove = if (page.continuation == null) localSongs.filterNot { it.id in remoteIds } else emptyList()
                if (songsToRemove.isNotEmpty()) {
                    database.transaction {
                        songsToRemove.forEach { song ->
                            try {
                                update(song.song.toggleUploaded())
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to update uploaded song: ${song.id}")
                            }
                        }
                    }
                }

                val existingById = HashMap<String, Song>(remoteSongs.size)
                localSongs.forEach { existingById[it.id] = it }
                val missingIds = remoteSongs.map { it.id }.filter { it !in existingById }
                missingIds.chunked(500).forEach { chunk ->
                    database.getSongsByIds(chunk).forEach { existingById[it.id] = it }
                }
                remoteSongs.chunked(100).forEach { batch ->
                    val work = batch.filter { song ->
                        val dbSong = existingById[song.id]
                        dbSong == null || !dbSong.song.isUploaded
                    }
                    if (work.isEmpty()) return@forEach
                    database.transaction {
                        work.forEach { song ->
                            try {
                                val dbSong = existingById[song.id]
                                if (dbSong == null) {
                                    insert(song.toMediaMetadata()) { it.toggleUploaded() }
                                } else if (!dbSong.song.isUploaded) {
                                    update(dbSong.song.toggleUploaded())
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to process uploaded song: ${song.id}")
                            }
                        }
                    }
                    delay(DB_OPERATION_DELAY_MS)
                }

                updateState { copy(uploadedSongs = SyncStatus.Completed) }
                Timber.d("[UPLOAD_DEBUG] Synced ${remoteSongs.size} uploaded songs successfully")
            } catch (e: Exception) {
                Timber.e(e, "[UPLOAD_DEBUG] Error processing uploaded songs")
                updateState { copy(uploadedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "[UPLOAD_DEBUG] Failed to sync uploaded songs after retries")
            updateState { copy(uploadedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncLikedAlbums() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncLikedAlbums - user not logged in")
            return@withContext
        }

        updateState { copy(likedAlbums = SyncStatus.Syncing, currentOperation = "Syncing liked albums") }

        withRetryResult {
            YouTube.library("FEmusic_liked_albums").completed()
        }.onSuccess { page ->
            try {
                val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
                val remoteIds = remoteAlbums.map { it.id }.toSet()
                val localAlbums = database.albumsLikedByNameAsc().first()

                if (page.continuation == null) {
                    localAlbums.filterNot { it.id in remoteIds }.forEach { album ->
                        try {
                            database.update(album.album.copy(bookmarkedAt = null))
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update album: ${album.id}")
                        }
                    }
                }

                val localAlbumsById = localAlbums.associateBy { it.id }
                remoteAlbums.forEach { album ->
                    try {
                        // already bookmarked locally, skip the extra album network fetch on every sync
                        if (localAlbumsById[album.id]?.album?.bookmarkedAt != null) return@forEach
                        val dbAlbum = database.album(album.id).firstOrNull()
                        if (dbAlbum != null) {
                            if (dbAlbum.album.bookmarkedAt == null) {
                                database.update(dbAlbum.album.localToggleLike())
                            }
                        } else {
                            YouTube.album(album.browseId).onSuccess { albumPage ->
                                database.insert(albumPage)
                                database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                    database.update(newDbAlbum.album.localToggleLike())
                                }
                            }
                        }
                        delay(DB_OPERATION_DELAY_MS)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to process album: ${album.id}")
                    }
                }

                updateState { copy(likedAlbums = SyncStatus.Completed) }
                Timber.d("Synced ${remoteAlbums.size} liked albums")
            } catch (e: Exception) {
                Timber.e(e, "Error processing liked albums")
                updateState { copy(likedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync liked albums after retries")
            updateState { copy(likedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncUploadedAlbums() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncUploadedAlbums - user not logged in")
            return@withContext
        }

        updateState { copy(uploadedAlbums = SyncStatus.Syncing, currentOperation = "Syncing uploaded albums") }

        withRetryResult {
            YouTube.library("FEmusic_library_privately_owned_releases").completed()
        }.onSuccess { page ->
            try {
                val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
                val remoteIds = remoteAlbums.map { it.id }.toSet()
                val localAlbums = database.albumsUploadedByNameAsc().first()

                if (page.continuation == null) {
                    localAlbums.filterNot { it.id in remoteIds }.forEach { album ->
                        try {
                            database.update(album.album.toggleUploaded())
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update album: ${album.id}")
                        }
                    }
                }

                val localAlbumsById = localAlbums.associateBy { it.id }
                remoteAlbums.forEach { album ->
                    try {
                        // already uploaded locally, skip re-fetching its album page on every sync
                        if (localAlbumsById[album.id]?.album?.isUploaded == true) return@forEach
                        val dbAlbum = database.album(album.id).firstOrNull()
                        if (dbAlbum != null) {
                            if (!dbAlbum.album.isUploaded) {
                                database.update(dbAlbum.album.toggleUploaded())
                            }
                        } else {
                            YouTube.album(album.browseId).onSuccess { albumPage ->
                                database.insert(albumPage)
                                database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                    database.update(newDbAlbum.album.toggleUploaded())
                                }
                            }.onFailure { reportException(it) }
                        }
                        delay(DB_OPERATION_DELAY_MS)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to process album: ${album.id}")
                    }
                }

                updateState { copy(uploadedAlbums = SyncStatus.Completed) }
                Timber.d("Synced ${remoteAlbums.size} uploaded albums")
            } catch (e: Exception) {
                Timber.e(e, "Error processing uploaded albums")
                updateState { copy(uploadedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync uploaded albums after retries")
            updateState { copy(uploadedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncArtistsSubscriptions() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncArtistsSubscriptions - user not logged in")
            return@withContext
        }

        updateState { copy(artists = SyncStatus.Syncing, currentOperation = "Syncing artist subscriptions") }

        withRetryResult {
            YouTube.library("FEmusic_library_corpus_artists").completed()
        }.onSuccess { page ->
            try {
                val remoteArtists = page.items.filterIsInstance<ArtistItem>()
                val remoteIds = remoteArtists.map { it.id }.toSet()
                val localArtists = database.artistsBookmarkedByNameAsc().first()

                if (page.continuation == null) {
                    localArtists.filterNot { it.id in remoteIds }.forEach { artist ->
                        try {
                            database.update(artist.artist.copy(bookmarkedAt = null))
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to update artist: ${artist.id}")
                        }
                    }
                }

                val localArtistsById = localArtists.associateBy { it.id }
                val pendingArtists = mutableListOf<Pair<ArtistItem, Pair<ArtistEntity?, String?>>>()
                remoteArtists.forEach { artist ->
                    try {
                        val existing = localArtistsById[artist.id]?.artist ?: database.artist(artist.id).firstOrNull()?.artist
                        val needsChannelFetch = (existing == null || existing.channelId == null) && artist.channelId == null && artist.id.startsWith("UC")
                        val channelId = artist.channelId ?: existing?.channelId ?: if (needsChannelFetch) {
                            try {
                                YouTube.getChannelId(artist.id).takeIf { it.isNotEmpty() }
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                        val needsWrite = existing == null ||
                            existing.bookmarkedAt == null ||
                            (existing.channelId == null && channelId != null) ||
                            existing.name != artist.title ||
                            existing.thumbnailUrl != artist.thumbnail
                        if (needsWrite) pendingArtists.add(artist to (existing to channelId))
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to process artist: ${artist.id}")
                    }
                }
                if (pendingArtists.isNotEmpty()) {
                    database.transaction {
                        val now = LocalDateTime.now()
                        pendingArtists.forEach { (artist, pair) ->
                            val (existing, channelId) = pair
                            if (existing == null) {
                                insert(
                                    ArtistEntity(
                                        id = artist.id,
                                        name = artist.title,
                                        thumbnailUrl = artist.thumbnail,
                                        channelId = channelId,
                                        bookmarkedAt = now
                                    )
                                )
                            } else {
                                update(
                                    existing.copy(
                                        name = artist.title,
                                        thumbnailUrl = artist.thumbnail,
                                        channelId = channelId ?: existing.channelId,
                                        bookmarkedAt = existing.bookmarkedAt ?: now,
                                        lastUpdateTime = now
                                    )
                                )
                            }
                        }
                    }
                }

                updateState { copy(artists = SyncStatus.Completed) }
                Timber.d("Synced ${remoteArtists.size} artist subscriptions")
            } catch (e: Exception) {
                Timber.e(e, "Error processing artist subscriptions")
                updateState { copy(artists = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync artist subscriptions after retries")
            updateState { copy(artists = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncSavedPlaylists(): Set<String> = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncSavedPlaylists - user not logged in")
            return@withContext emptySet()
        }

        updateState { copy(playlists = SyncStatus.Syncing, currentOperation = "Syncing saved playlists") }
        val syncedBrowseIds = HashSet<String>()

        withRetryResult {
            YouTube.library("FEmusic_liked_playlists").completed()
        }.onSuccess { page ->
            try {
                val allRemotePlaylists = page.items.filterIsInstance<PlaylistItem>()
                    .filterNot { it.id == "LM" || it.id == "SE" }
                    .reversed()
                val remoteIds = allRemotePlaylists.map { it.id }.toSet()

                val localPlaylists = database.playlistsByNameAsc().first()

                // Onboarding's "What to sync" picks which saved playlists come in; ones already
                // linked (made here, or picked before) keep syncing. Nothing picked = all of them.
                val picked = context.dataStore.get(SelectedYtmPlaylistsKey, "")
                    .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                val linkedIds = localPlaylists.mapNotNull { it.playlist.browseId }.toSet()
                val remotePlaylists = if (picked.isEmpty()) allRemotePlaylists
                else allRemotePlaylists.filter { it.id in picked || it.id in linkedIds }

                // Only remove locally-bookmarked YouTube playlists when YouTube returned the complete list.
                if (page.continuation == null) {
                    localPlaylists.filterNot { it.playlist.browseId in remoteIds }
                        .filter { it.playlist.browseId != null && it.playlist.bookmarkedAt != null }
                        .forEach { playlist ->
                            try {
                                database.update(playlist.playlist.copy(bookmarkedAt = null))
                                PlaylistAddedHere.clearPlaylist(context, playlist.id)
                                delay(DB_OPERATION_DELAY_MS)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to update playlist: ${playlist.id}")
                            }
                        }
                }

                val tagAll = !sourcesTagged()
                for (playlist in remotePlaylists) {
                    try {
                        var playlistEntity = localPlaylists.find { it.playlist.browseId == playlist.id }?.playlist
                        val created = playlistEntity == null

                        if (playlistEntity == null) {
                            playlistEntity = PlaylistEntity(
                                name = playlist.title,
                                browseId = playlist.id,
                                thumbnailUrl = playlist.thumbnail,
                                isEditable = playlist.isEditable,
                                bookmarkedAt = LocalDateTime.now(),
                                remoteSongCount = playlist.songCountText?.let {
                                    Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                },
                                playEndpointParams = playlist.playEndpoint?.params,
                                shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                radioEndpointParams = playlist.radioEndpoint?.params
                            )
                            database.insert(playlistEntity)
                            Timber.d("syncSavedPlaylists: Created new playlist ${playlist.title} (${playlist.id})")
                        } else {
                            database.update(playlistEntity, playlist)
                            Timber.d("syncSavedPlaylists: Updated existing playlist ${playlist.title} (${playlist.id})")
                        }

                        if (created || tagAll) {
                            database.insert(
                                ImportedPlaylist(
                                    playlistId = playlistEntity.id,
                                    source = ImportSource.YOUTUBE_MUSIC.key,
                                    sessionId = null,
                                    importedAt = LocalDateTime.now(),
                                ),
                            )
                        }

                        executeSyncPlaylist(playlist.id, playlistEntity.id)
                        syncedBrowseIds.add(playlist.id)
                        delay(DB_OPERATION_DELAY_MS)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to sync playlist ${playlist.title}")
                    }
                }

                updateState { copy(playlists = SyncStatus.Completed) }
                Timber.d("Synced ${remotePlaylists.size} saved playlists")
            } catch (e: Exception) {
                Timber.e(e, "Error processing saved playlists")
                updateState { copy(playlists = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync saved playlists after retries")
            updateState { copy(playlists = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
        syncedBrowseIds
    }

    private suspend fun executeSyncAutoSyncPlaylists(skipBrowseIds: Set<String> = emptySet()) = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncAutoSyncPlaylists - user not logged in")
            return@withContext
        }

        try {
            // skip playlists just synced in executeSyncSavedPlaylists so full sync doesn't fetch them twice
            val autoSyncPlaylists = database.playlistsByNameAsc().first()
                .filter { it.playlist.isAutoSync && it.playlist.browseId != null && it.playlist.browseId !in skipBrowseIds }

            Timber.d("syncAutoSyncPlaylists: Found ${autoSyncPlaylists.size} playlists to sync")

            autoSyncPlaylists.forEach { playlist ->
                try {
                    executeSyncPlaylist(playlist.playlist.browseId!!, playlist.playlist.id)
                    delay(DB_OPERATION_DELAY_MS)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync playlist ${playlist.playlist.name}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error syncing auto-sync playlists")
        }
    }

    private suspend fun executeSyncPlaylist(browseId: String, playlistId: String) = withContext(Dispatchers.IO) {
        Timber.d("syncPlaylist: Starting sync for browseId=$browseId, playlistId=$playlistId")

        withRetryResult {
            YouTube.playlist(browseId).completed()
        }.onSuccess { page ->
            try {
                val remoteSongs = page.songs.map(SongItem::toMediaMetadata)
                Timber.d("syncPlaylist: Fetched ${remoteSongs.size} songs from remote")
                // a short list would cut every song past where paging stopped, so the playlist waits for a full read
                if (page.songsContinuation != null) {
                    Timber.w("syncPlaylist: Remote list came back short, leaving the playlist as it is")
                    return@onSuccess
                }

                val playlistEntity = database.playlist(playlistId).first()?.playlist
                val isEditable = playlistEntity?.isEditable == true || page.playlist.isEditable
                val loggedIn = isLoggedIn() && context.isInternetConnected()

                val localEntries = database.playlistSongs(playlistId).first()
                    .sortedBy { it.map.position }
                val localIds = localEntries.map { it.song.id }
                val localIdSet = localIds.toSet()
                val localFileIds = localEntries.filter { it.song.song.isLocal }.map { it.song.id }
                val localFileIdSet = localFileIds.toSet()

                val rawRemoteIds = remoteSongs.map { it.id }
                val rawRemoteIdSet = rawRemoteIds.toSet()

                val pendingAdds = PlaylistAddedHere.added(context, playlistId).filter { it in localIdSet }
                val pendingRemovals = PlaylistAddedHere.pendingRemovals(context, playlistId).filter { it !in localIdSet }.toSet()

                // 1. Push any pending local removals that YouTube still has.
                val pushedRemovals = mutableSetOf<String>()
                val failedRemovals = mutableSetOf<String>()
                if (loggedIn && isEditable && pendingRemovals.isNotEmpty()) {
                    for (song in remoteSongs) {
                        if (song.id !in pendingRemovals) continue
                        val svid = song.setVideoId
                        if (svid != null && YouTube.removeFromPlaylist(browseId, song.id, svid).isSuccess) {
                            pushedRemovals += song.id
                        } else {
                            failedRemovals += song.id
                        }
                        delay(100)
                    }
                } else {
                    failedRemovals += pendingRemovals.filter { it in rawRemoteIdSet }
                }

                // 2. Push any pending local additions that YouTube doesn't have yet.
                val toPushAdds = pendingAdds.filter { it !in rawRemoteIdSet && it !in localFileIdSet }
                val pushedAddSetVideoIds = HashMap<String, ArrayDeque<String>>()
                val pushedAdds = mutableSetOf<String>()
                if (loggedIn && isEditable && toPushAdds.isNotEmpty()) {
                    val pushResult = YouTube.addVideosToPlaylistWithSetVideoIds(browseId, toPushAdds)
                    if (pushResult.isSuccess) {
                        pushedAdds += toPushAdds
                        pushResult.getOrNull().orEmpty().forEach { (vid, svid) ->
                            pushedAddSetVideoIds.getOrPut(vid) { ArrayDeque() }.addLast(svid)
                        }
                    }
                }

                // 3. Reconcile final playlist order:
                // - Songs removed on YouTube (no longer in remoteSongs and not still pending add) are removed locally.
                // - Songs removed locally (pushed or still pending removal) are excluded from remoteSongs.
                // - Songs added locally that failed push or belong to a non-editable playlist stay on top until synced.
                // - Songs already present on YouTube are cleared from pendingAdds so future YouTube deletions remove them cleanly.
                val suppressedRemovals = pushedRemovals + failedRemovals
                val effectiveRemoteSongs = remoteSongs.filterNot { it.id in suppressedRemovals }
                val effectiveRemoteIds = effectiveRemoteSongs.map { it.id } + pushedAdds.filterNot { it in rawRemoteIdSet }
                val effectiveRemoteIdSet = effectiveRemoteIds.toSet()

                val stillPendingAdds = (pendingAdds.filterNot { it in effectiveRemoteIdSet } + localFileIds).distinct()
                val mergedIds = stillPendingAdds + effectiveRemoteIds.filter { it !in stillPendingAdds }

                PlaylistAddedHere.remember(
                    context = context,
                    playlistId = playlistId,
                    remoteIds = effectiveRemoteIds,
                    stillAdded = stillPendingAdds.filterNot { it in localFileIdSet },
                    stillRemoved = failedRemovals,
                )

                // Check whether either song order OR any missing setVideoId needs updating.
                val remoteSetVideoIdQueues = HashMap<String, ArrayDeque<String>>()
                effectiveRemoteSongs.forEach { song ->
                    song.setVideoId?.let { svid ->
                        remoteSetVideoIdQueues.getOrPut(song.id) { ArrayDeque() }.addLast(svid)
                    }
                }
                pushedAddSetVideoIds.forEach { (vid, q) ->
                    val dest = remoteSetVideoIdQueues.getOrPut(vid) { ArrayDeque() }
                    q.forEach { dest.addLast(it) }
                }

                val hasMissingSetVideoIds = localEntries.any { entry ->
                    entry.map.setVideoId == null && remoteSetVideoIdQueues[entry.song.id]?.isNotEmpty() == true
                }

                if (mergedIds == localIds && !hasMissingSetVideoIds) {
                    Timber.d("syncPlaylist: Local and remote are in sync, no changes needed")
                    return@onSuccess
                }

                Timber.d("syncPlaylist: Updating local playlist (remote=${effectiveRemoteIds.size}, pendingAdds=${stillPendingAdds.size}, local=${localIds.size})")

                val remoteById = effectiveRemoteSongs.associateBy { it.id }
                val localSetVideoIds = localEntries
                    .filter { it.map.setVideoId != null }
                    .groupBy({ it.song.id }, { it.map.setVideoId!! })
                    .mapValues { ArrayDeque(it.value) }
                val existingSongIds = mergedIds.chunked(500).flatMap { database.getSongsByIds(it) }.mapTo(HashSet()) { it.id }
                database.withTransaction {
                    database.clearPlaylist(playlistId)
                    mergedIds.forEachIndexed { idx, id ->
                        val song = remoteById[id]
                        if (song != null && existingSongIds.add(song.id)) {
                            database.insert(song)
                        }
                        val setVideoId = remoteSetVideoIdQueues[id]?.removeFirstOrNull()
                            ?: localSetVideoIds[id]?.removeFirstOrNull()
                        database.insert(
                            PlaylistSongMap(
                                songId = id,
                                playlistId = playlistId,
                                position = idx,
                                setVideoId = setVideoId,
                            )
                        )
                    }
                }
                Timber.d("syncPlaylist: Successfully synced playlist")
            } catch (e: Exception) {
                Timber.e(e, "Error processing playlist sync")
            }
        }.onFailure { e ->
            Timber.e(e, "syncPlaylist: Failed after retries")
        }
    }

    /**
     * Copies YouTube Music watch history into Mochy's local play history.
     * YouTube groups plays in date buckets ("Today", "Yesterday", "Last week", etc.), so each play
     * receives a timestamp inside its bucket and duplicates already recorded in that bucket are skipped.
     */
    suspend fun pullYouTubeHistory(maxRequests: Int = HISTORY_PAGES_FULL): Int = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) return@withContext 0
        // English section titles regardless of app language so date buckets can always be parsed
        val page = withRetryResult {
            YouTube.musicHistory(hlOverride = "en", maxRequests = maxRequests)
        }.getOrThrow()
        val today = LocalDate.now()
        val now = LocalDateTime.now()

        // read bare event rows without song joins so auto-sync doesn't pull the whole history graph into heap
        val existing = HashMap<String, MutableList<LocalDate>>()
        database.recapEvents(0L, Long.MAX_VALUE).forEach { row ->
            val date = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(row.timestamp), ZoneOffset.UTC).toLocalDate()
            existing.getOrPut(row.songId) { mutableListOf() } += date
        }

        val toInsert = mutableListOf<Triple<SongItem, LocalDateTime, Long>>()
        var older = today.plusDays(1)
        page.sections.orEmpty().forEach { section ->
            val bucket = historyBucketFor(section.title, today)
                ?: HistoryBucket(older.minusDays(7).coerceAtMost(today), older.minusDays(1).coerceAtMost(today))
            if (bucket.from.isBefore(older)) older = bucket.from
            section.songs.forEachIndexed { index, song ->
                val dates = existing[song.id]
                if (dates != null && dates.any { !it.isBefore(bucket.from) && !it.isAfter(bucket.to) }) return@forEachIndexed

                val timestamp = bucket.anchor(now).minusMinutes(index * 4L)
                    .coerceAtLeast(bucket.from.atStartOfDay())
                val playTimeMs = (song.duration ?: 0) * 1000L
                toInsert += Triple(song, timestamp, playTimeMs)
                existing.getOrPut(song.id) { mutableListOf() } += timestamp.toLocalDate()
            }
        }
        // batch history writes into chunks so room doesn't commit and invalidate once per song
        toInsert.chunked(100).forEach { chunk ->
            database.withTransaction {
                chunk.forEach { (song, timestamp, playTimeMs) ->
                    insert(song.toMediaMetadata())
                    insert(Event(songId = song.id, timestamp = timestamp, playTime = playTimeMs))
                    if (playTimeMs > 0) incrementTotalPlayTime(song.id, playTimeMs)
                }
            }
        }
        val copied = toInsert.size
        Timber.d("pullYouTubeHistory: Copied $copied new plays")
        copied
    }

    private class HistoryBucket(val from: LocalDate, val to: LocalDate) {
        fun anchor(now: LocalDateTime): LocalDateTime =
            if (to == now.toLocalDate()) now else to.atTime(21, 0)
    }

    private fun historyBucketFor(title: String, today: LocalDate): HistoryBucket? {
        val thisMonday = today.with(DayOfWeek.MONDAY)
        return when (title.trim().lowercase(Locale.ENGLISH)) {
            "today" -> HistoryBucket(today, today)
            "yesterday" -> today.minusDays(1).let { HistoryBucket(it, it) }
            "this week" -> {
                val to = today.minusDays(2)
                if (to.isBefore(thisMonday)) HistoryBucket(thisMonday, thisMonday) else HistoryBucket(thisMonday, to)
            }
            "last week" -> HistoryBucket(thisMonday.minusDays(7), thisMonday.minusDays(1))
            "this month" -> HistoryBucket(today.withDayOfMonth(1), today)
            "last month" -> YearMonth.from(today).minusMonths(1).let { HistoryBucket(it.atDay(1), it.atEndOfMonth()) }
            "this year" -> HistoryBucket(today.withDayOfYear(1), today)
            "last year" -> today.minusYears(1).let { HistoryBucket(it.withDayOfYear(1), it.withDayOfYear(it.lengthOfYear())) }
            else -> parseHistoryDay(title.trim(), today)?.let { HistoryBucket(it, it) }
                ?: parseHistoryMonth(title.trim(), today)?.let { month -> HistoryBucket(month.atDay(1), minOf(month.atEndOfMonth(), today)) }
                ?: title.trim().toIntOrNull()?.takeIf { it in 2000..today.year }?.let { year ->
                    HistoryBucket(LocalDate.of(year, 1, 1), minOf(LocalDate.of(year, 12, 31), today))
                }
        }
    }

    private fun parseHistoryDay(title: String, today: LocalDate): LocalDate? {
        DayOfWeek.entries.firstOrNull { it.name.equals(title, ignoreCase = true) }?.let { weekday ->
            var day = today.minusDays(2)
            while (day.dayOfWeek != weekday) day = day.minusDays(1)
            return day
        }
        for (formatter in DayFormatters) {
            runCatching { return LocalDate.parse(title, formatter) }
        }
        for (formatter in DayNoYearFormatters) {
            runCatching {
                val monthDay = java.time.MonthDay.parse(title, formatter)
                val thisYear = monthDay.atYear(today.year)
                return if (thisYear.isAfter(today)) thisYear.minusYears(1) else thisYear
            }
        }
        return null
    }

    private fun parseHistoryMonth(title: String, today: LocalDate): YearMonth? {
        runCatching { return YearMonth.parse(title, MonthYearFormatter) }
        return runCatching {
            val month = java.time.Month.valueOf(title.uppercase(Locale.ENGLISH))
            val thisYear = YearMonth.of(today.year, month)
            if (thisYear.isAfter(YearMonth.from(today))) thisYear.minusYears(1) else thisYear
        }.getOrNull()
    }

    private fun LocalDateTime.coerceAtLeast(min: LocalDateTime): LocalDateTime = if (isBefore(min)) min else this
    private fun LocalDate.coerceAtMost(max: LocalDate): LocalDate = if (isAfter(max)) max else this

    private suspend fun executeCleanupDuplicatePlaylists() = withContext(Dispatchers.IO) {
        try {
            val allPlaylists = database.playlistsByNameAsc().first()
            val browseIdGroups = allPlaylists
                .filter { it.playlist.browseId != null }
                .groupBy { it.playlist.browseId }

            for ((browseId, playlists) in browseIdGroups) {
                if (playlists.size > 1) {
                    Timber.w("Found ${playlists.size} duplicate playlists for browseId: $browseId")
                    val toKeep = playlists.maxByOrNull { it.songCount } ?: playlists.first()

                    playlists.filter { it.id != toKeep.id }.forEach { duplicate ->
                        try {
                            Timber.d("Removing duplicate playlist: ${duplicate.playlist.name} (${duplicate.id})")
                            database.clearPlaylist(duplicate.id)
                            database.delete(duplicate.playlist)
                            delay(DB_OPERATION_DELAY_MS)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to remove duplicate playlist: ${duplicate.id}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up duplicate playlists")
        }
    }

    private suspend fun executeClearAllSyncedContent() = withContext(Dispatchers.IO) {
        Timber.d("clearAllSyncedContent: Starting cleanup")

        updateState { copy(overallStatus = SyncStatus.Syncing, currentOperation = "Clearing synced content") }

        try {
            database.withTransaction {

                val likedSongs = database.likedSongsByNameAsc().first()
                likedSongs.forEach {
                    database.update(it.song.copy(liked = false, likedDate = null))
                }

                val librarySongs = database.songsByNameAsc().first()
                librarySongs.forEach {
                    if (it.song.inLibrary != null) {
                        database.update(it.song.copy(inLibrary = null))
                    }
                }

                val likedAlbums = database.albumsLikedByNameAsc().first()
                likedAlbums.forEach {
                    database.update(it.album.copy(bookmarkedAt = null))
                }

                val subscribedArtists = database.artistsBookmarkedByNameAsc().first()
                subscribedArtists.forEach {
                    database.update(it.artist.copy(bookmarkedAt = null))
                }

                val savedPlaylists = database.playlistsByNameAsc().first()
                savedPlaylists.forEach {
                    if (it.playlist.browseId != null) {
                        database.clearPlaylist(it.playlist.id)
                        database.delete(it.playlist)
                    }
                }

                val uploadedSongs = database.uploadedSongsByNameAsc().first()
                uploadedSongs.forEach {
                    database.update(it.song.copy(isUploaded = false))
                }

                val uploadedAlbums = database.albumsUploadedByCreateDateAsc().first()
                uploadedAlbums.forEach {
                    database.update(it.album.copy(isUploaded = false))
                }
            }

            context.dataStore.edit { settings ->
                settings[LastFullSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            }

            updateState { copy(overallStatus = SyncStatus.Completed, currentOperation = "") }
            Timber.d("clearAllSyncedContent: Cleanup completed successfully")
        } catch (e: Exception) {
            Timber.e(e, "clearAllSyncedContent: Error during cleanup")
            updateState { copy(overallStatus = SyncStatus.Error(e.message ?: "Unknown error"), currentOperation = "") }
        }
    }

    fun cancelAllSyncs() {
        processingJob?.cancel()
        startProcessingQueue()
        updateState { SyncState() }
    }
}
