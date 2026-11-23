// YouTubeSyncManager.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.YtExcludeImportsKey
import com.mochyy.mochymusic.constants.YtPullHistoryKey
import com.mochyy.mochymusic.constants.YtSyncHoldKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Event
import com.mochyy.mochymusic.db.entities.PlaylistEntity
import com.mochyy.mochymusic.extensions.isUserLoggedIn
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.utils.SyncUtils
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import com.music.innertube.YouTube
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class YtSyncState(
    val running: Boolean = false,
    val label: String = "",
    val message: String? = null,
    val canUndo: Boolean = false,
)

/** the manual youtube actions on the sync page; automatic library sync stays in SyncUtils */
@Singleton
class YouTubeSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> Timber.e(e, "YouTube sync task failed") }
    )
    private var job: Job? = null

    private val _state = MutableStateFlow(YtSyncState())
    val state: StateFlow<YtSyncState> = _state.asStateFlow()

    init {
        scope.launch { _state.value = _state.value.copy(canUndo = SyncUndo.canUndo(context)) }
    }

    fun syncNow() = launchTask("Syncing with YouTube…") {
        requireSignedIn()
        context.dataStore.edit { it[YtSyncHoldKey] = false }
        syncUtils.performFullSyncSuspend(manual = true)
        if (context.dataStore.get(YtPullHistoryKey, true)) {
            setLabel("Copying YouTube history…")
            val copied = syncUtils.pullYouTubeHistory(SyncUtils.HISTORY_PAGES_FULL)
            "Synced. $copied ${if (copied == 1) "play" else "plays"} copied from YouTube history."
        } else {
            "Synced your likes, library and playlists."
        }
    }

    fun pullHistoryNow() = launchTask("Copying YouTube history…") {
        requireSignedIn()
        val copied = syncUtils.pullYouTubeHistory(SyncUtils.HISTORY_PAGES_FULL)
        if (copied == 0) "Nothing new in your YouTube history." else "$copied ${if (copied == 1) "play" else "plays"} copied from YouTube history."
    }

    fun undoLastSync(withYouTube: Boolean) = launchTask("Undoing the last sync…") {
        if (!SyncUndo.canUndo(context)) return@launchTask context.getString(R.string.yt_sync_undo_nothing)
        if (withYouTube) requireSignedIn()
        val failed = SyncUndo.undo(context, database, withYouTube)
        when {
            failed > 0 -> context.resources.getQuantityString(R.plurals.yt_sync_undo_failed, failed, failed)
            withYouTube -> context.getString(R.string.yt_sync_undo_done_both)
            else -> context.getString(R.string.yt_sync_undo_done_phone)
        }
    }

    suspend fun lostLikesCount(): Int = LostLikes.count(context, database)

    fun bringBackLikes() = launchTask(context.getString(R.string.yt_sync_lost_likes_running)) {
        val back = LostLikes.bringBack(context, database)
        if (back == 0) context.getString(R.string.yt_sync_lost_likes_none_body)
        else context.resources.getQuantityString(R.plurals.yt_sync_lost_likes_done, back, back)
    }

    fun uploadLocalPlaylists() = launchTask("Creating playlists on YouTube…") {
        requireSignedIn()
        val keepOffYouTube = context.dataStore.get(YtExcludeImportsKey, false)
        val locals = database.playlistsByNameAsc().first().filter {
            it.playlist.browseId == null && it.playlist.isEditable && !it.playlist.isLocal &&
                it.playlist.id != PlaylistEntity.LIKED_PLAYLIST_ID && it.playlist.id != PlaylistEntity.DOWNLOADED_PLAYLIST_ID &&
                !(keepOffYouTube && database.getImportedPlaylist(it.playlist.id)?.source?.let { s -> s != ImportSource.YOUTUBE_MUSIC.key } == true)
        }
        if (locals.isEmpty()) return@launchTask "Every playlist is already on YouTube."
        var created = 0
        val failed = mutableListOf<String>()
        locals.forEachIndexed { index, playlist ->
            setLabel("Creating \"${playlist.playlist.name}\" (${index + 1}/${locals.size})")
            if (YouTubePlaylistMirror.mirror(database, playlist.playlist.id, playlist.playlist.name)) created++
            else failed += playlist.playlist.name
            delay(400)
        }
        buildString {
            append("Created $created ${if (created == 1) "playlist" else "playlists"} on YouTube.")
            if (failed.isNotEmpty()) append(" Failed: ${failed.joinToString(", ")}.")
        }
    }

    fun clearMessage() {
        if (!_state.value.running) _state.value = YtSyncState()
    }

    private class NotSignedInException : Exception("Sign in to YouTube first.")

    private fun requireSignedIn() {
        if (!context.isUserLoggedIn()) throw NotSignedInException()
    }

    private fun setLabel(label: String) {
        _state.value = _state.value.copy(label = label)
    }

    private fun launchTask(label: String, block: suspend () -> String) {
        if (job?.isActive == true) return
        _state.value = _state.value.copy(running = true, label = label, message = null)
        job = scope.launch {
            var message: String? = null
            try {
                message = block()
            } catch (e: CancellationException) {
                message = "Stopped."
                throw e
            } catch (e: NotSignedInException) {
                message = e.message
            } catch (e: Exception) {
                Timber.e(e, "YouTube sync task failed")
                message = "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _state.value = YtSyncState(running = false, message = message, canUndo = SyncUndo.canUndo(context))
            }
        }
    }
}
