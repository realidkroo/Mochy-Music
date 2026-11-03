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
        if (context.dataStore.get(YtPullHistoryKey, false)) {
            setLabel("Copying YouTube history…")
            val copied = pullHistory()
            "Synced. $copied ${if (copied == 1) "play" else "plays"} copied from YouTube history."
        } else {
            "Synced your likes, library and playlists."
        }
    }

    fun pullHistoryNow() = launchTask("Copying YouTube history…") {
        requireSignedIn()
        val copied = pullHistory()
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

    /** copies youtube music history into ours, youtube only gives day-ish buckets (today, last week, march 2026) so each play gets a time inside its bucket, a song already played in that bucket is skipped so pulling again never doubles up */
    private suspend fun pullHistory(): Int {
        // english section titles, whatever the app language, so the buckets can be read
        val page = YouTube.musicHistory(hlOverride = "en", maxRequests = HistoryPagesMax).getOrThrow()
        val today = LocalDate.now()
        val now = LocalDateTime.now()

        val existing = HashMap<String, MutableList<LocalDate>>()
        database.events().first().forEach { existing.getOrPut(it.event.songId) { mutableListOf() } += it.event.timestamp.toLocalDate() }

        var copied = 0
        var older = today.plusDays(1)
        page.sections.orEmpty().forEach { section ->
            // a title we can't read still gets its plays, placed just before the section above it, so nothing is dropped
            val bucket = bucketFor(section.title, today) ?: Bucket(older.minusDays(7).coerceAtMost(today), older.minusDays(1).coerceAtMost(today))
            if (bucket.from.isBefore(older)) older = bucket.from
            section.songs.forEachIndexed { index, song ->
                val dates = existing[song.id]
                if (dates != null && dates.any { !it.isBefore(bucket.from) && !it.isAfter(bucket.to) }) return@forEachIndexed

                // newest first within a bucket; never in the future, never before the bucket
                val timestamp = bucket.anchor(now).minusMinutes(index * 4L)
                    .coerceAtLeast(bucket.from.atStartOfDay())
                val playTimeMs = (song.duration ?: 0) * 1000L
                database.withTransaction {
                    insert(song.toMediaMetadata())
                    insert(Event(songId = song.id, timestamp = timestamp, playTime = playTimeMs))
                    if (playTimeMs > 0) incrementTotalPlayTime(song.id, playTimeMs)
                }
                existing.getOrPut(song.id) { mutableListOf() } += timestamp.toLocalDate()
                copied++
            }
        }
        return copied
    }

    private class Bucket(val from: LocalDate, val to: LocalDate) {
        fun anchor(now: LocalDateTime): LocalDateTime =
            if (to == now.toLocalDate()) now else to.atTime(21, 0)
    }

    private fun bucketFor(title: String, today: LocalDate): Bucket? {
        val thisMonday = today.with(DayOfWeek.MONDAY)
        return when (title.trim().lowercase(Locale.ENGLISH)) {
            "today" -> Bucket(today, today)
            "yesterday" -> today.minusDays(1).let { Bucket(it, it) }
            "this week" -> {
                val to = today.minusDays(2)
                if (to.isBefore(thisMonday)) Bucket(thisMonday, thisMonday) else Bucket(thisMonday, to)
            }
            "last week" -> Bucket(thisMonday.minusDays(7), thisMonday.minusDays(1))
            "this month" -> Bucket(today.withDayOfMonth(1), today)
            "last month" -> YearMonth.from(today).minusMonths(1).let { Bucket(it.atDay(1), it.atEndOfMonth()) }
            "this year" -> Bucket(today.withDayOfYear(1), today)
            "last year" -> today.minusYears(1).let { Bucket(it.withDayOfYear(1), it.withDayOfYear(it.lengthOfYear())) }
            else -> parseDay(title.trim(), today)?.let { Bucket(it, it) }
                ?: parseMonth(title.trim(), today)?.let { month -> Bucket(month.atDay(1), minOf(month.atEndOfMonth(), today)) }
                ?: title.trim().toIntOrNull()?.takeIf { it in 2000..today.year }?.let { year ->
                    Bucket(LocalDate.of(year, 1, 1), minOf(LocalDate.of(year, 12, 31), today))
                }
        }
    }

    // a weekday name is the last one before yesterday, and youtube sometimes writes a single date ("march 3", "mar 3, 2025")
    private fun parseDay(title: String, today: LocalDate): LocalDate? {
        DayOfWeek.entries.firstOrNull { it.name.equals(title, ignoreCase = true) }?.let { weekday ->
            var day = today.minusDays(2)
            while (day.dayOfWeek != weekday) day = day.minusDays(1)
            return day
        }
        for (pattern in DayPatterns) {
            runCatching { return LocalDate.parse(title, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)) }
        }
        for (pattern in DayNoYearPatterns) {
            runCatching {
                val monthDay = java.time.MonthDay.parse(title, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
                val thisYear = monthDay.atYear(today.year)
                return if (thisYear.isAfter(today)) thisYear.minusYears(1) else thisYear
            }
        }
        return null
    }

    private fun parseMonth(title: String, today: LocalDate): YearMonth? {
        runCatching { return YearMonth.parse(title, DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) }
        // a bare month name means the most recent one with that name
        return runCatching {
            val month = java.time.Month.valueOf(title.uppercase(Locale.ENGLISH))
            val thisYear = YearMonth.of(today.year, month)
            if (thisYear.isAfter(YearMonth.from(today))) thisYear.minusYears(1) else thisYear
        }.getOrNull()
    }

    private fun LocalDateTime.coerceAtLeast(min: LocalDateTime): LocalDateTime = if (isBefore(min)) min else this

    private fun LocalDate.coerceAtMost(max: LocalDate): LocalDate = if (isAfter(max)) max else this

    private companion object {
        // youtube hands the history out about a hundred plays per page, so this reaches a long way back
        const val HistoryPagesMax = 40
        val DayPatterns = listOf("MMMM d, yyyy", "MMM d, yyyy", "d MMMM yyyy", "d MMM yyyy")
        val DayNoYearPatterns = listOf("MMMM d", "MMM d", "d MMMM", "d MMM")
    }
}
