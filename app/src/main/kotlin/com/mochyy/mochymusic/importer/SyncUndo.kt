// SyncUndo.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
import com.mochyy.mochymusic.constants.YtSyncHoldKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.PlaylistSongMap
import com.mochyy.mochymusic.db.entities.SongEntity
import com.mochyy.mochymusic.utils.dataStore
import com.music.innertube.YouTube
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime

/** saves what the last full sync is about to change, so an undo can put it back */
object SyncUndo {
    private const val FILE_NAME = "yt_sync_undo.json"
    private const val LIKE_DELAY_MS = 150L
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Journal(
        val likedSongs: Map<String, String?>,
        val librarySongs: Map<String, String>,
        val playlists: Map<String, List<Entry>>,
        val pushedLikes: List<String> = emptyList(),
    )

    @Serializable
    private data class Entry(val songId: String, val setVideoId: String? = null)

    fun canUndo(context: Context): Boolean = synchronized(lock) { file(context).exists() }

    /** replaces the saved state with what the phone holds right now, before a sync touches it */
    suspend fun begin(context: Context, database: MusicDatabase) {
        // read bare columns instead of full song and playlist joins so auto-sync stays light on ram and sqlite
        val syncedIds = database.syncedPlaylistIds()
        val entriesByPlaylist = database.syncedPlaylistEntries().groupBy { it.playlistId }
        val journal = Journal(
            likedSongs = database.likedSongSnapshots().associate { it.id to it.likedDate?.toString() },
            librarySongs = database.librarySongSnapshots().associate { it.id to it.inLibrary.toString() },
            playlists = syncedIds.associateWith { id ->
                entriesByPlaylist[id].orEmpty().map { Entry(it.songId, it.setVideoId) }
            },
        )
        synchronized(lock) { write(context, journal) }
    }

    // the likes the phone had right before the last full sync, with their dates
    fun likedBefore(context: Context): Map<String, LocalDateTime?> {
        val journal = synchronized(lock) { read(context) } ?: return emptyMap()
        return journal.likedSongs.mapValues { (_, date) -> date?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } }
    }

    /** likes the sync sent up to YouTube, so an undo can take them off again */
    fun recordPushedLikes(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) {
            val journal = read(context) ?: return
            write(context, journal.copy(pushedLikes = journal.pushedLikes + ids))
        }
    }

    // puts likes, library and playlist songs back, then holds YouTube pulls; withYouTube also unlikes what the sync pushed
    suspend fun undo(context: Context, database: MusicDatabase, withYouTube: Boolean): Int {
        val journal = synchronized(lock) { read(context) } ?: return 0
        restoreLocal(database, journal)
        var failed = 0
        if (withYouTube) {
            journal.pushedLikes.forEach { id ->
                if (YouTube.likeVideo(id, false).isFailure) failed++
                delay(LIKE_DELAY_MS)
            }
        }
        synchronized(lock) { file(context).delete() }
        context.dataStore.edit { it[YtSyncHoldKey] = true }
        return failed
    }

    private suspend fun restoreLocal(database: MusicDatabase, journal: Journal) {
        val likeWrites = mutableListOf<SongEntity>()
        database.likedSongsByNameAsc().first().map { it.song }
            .filter { it.id !in journal.likedSongs }
            .forEach { likeWrites += it.copy(liked = false, likedDate = null) }
        for ((id, date) in journal.likedSongs) {
            val song = database.getSongById(id)?.song ?: continue
            val wanted = date?.let(LocalDateTime::parse)
            if (!song.liked || song.likedDate != wanted) likeWrites += song.copy(liked = true, likedDate = wanted)
        }

        val libraryWrites = mutableListOf<Pair<String, LocalDateTime?>>()
        database.songsByNameAsc().first().map { it.song }
            .filter { it.inLibrary != null && it.id !in journal.librarySongs }
            .forEach { libraryWrites += it.id to null }
        for ((id, date) in journal.librarySongs) {
            val song = database.getSongById(id)?.song ?: continue
            val wanted = LocalDateTime.parse(date)
            if (song.inLibrary != wanted) libraryWrites += id to wanted
        }

        val playlistRewrites = journal.playlists.mapNotNull { (playlistId, entries) ->
            if (database.playlist(playlistId).first() == null) return@mapNotNull null
            val now = database.playlistSongs(playlistId).first()
                .sortedBy { it.map.position }
                .map { Entry(it.map.songId, it.map.setVideoId) }
            val usable = entries.filter { database.getSongById(it.songId) != null }
            if (now == usable) null else playlistId to usable
        }

        database.withTransaction {
            likeWrites.forEach { update(it) }
            libraryWrites.forEach { (id, date) -> inLibrary(id, date) }
            playlistRewrites.forEach { (playlistId, entries) ->
                clearPlaylist(playlistId)
                entries.forEachIndexed { index, entry ->
                    insert(PlaylistSongMap(playlistId = playlistId, songId = entry.songId, position = index, setVideoId = entry.setVideoId))
                }
            }
        }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun read(context: Context): Journal? = runCatching {
        file(context).takeIf { it.exists() }?.readText()?.let { json.decodeFromString<Journal>(it) }
    }.getOrNull()

    private fun write(context: Context, journal: Journal) {
        runCatching { file(context).writeText(json.encodeToString(journal)) }
    }
}
