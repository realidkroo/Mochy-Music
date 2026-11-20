// ImportHistory.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.ImportSession
import com.mochyy.mochymusic.db.entities.SongEntity
import com.mochyy.mochymusic.extensions.isSyncEnabled
import com.mochyy.mochymusic.utils.SyncUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** takes imports back out, each session only reverses what it changed itself so songs that were already liked or in the library stay that way */
@Singleton
class ImportHistory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
) {
    /** undoes the newest import from a service. null when there is none to undo */
    suspend fun undoLatestImport(): Int? = withContext(Dispatchers.IO) {
        database.importSessionsExcept(ImportSession.DEVICE_SOURCE).firstOrNull()?.let { undo(it) }
    }

    /** undoes the newest import of files from the phone. null when there is none to undo */
    suspend fun undoLatestLocalImport(): Int? = withContext(Dispatchers.IO) {
        database.importSessions(ImportSession.DEVICE_SOURCE).firstOrNull()?.let { undo(it) }
    }

    /** everything one service brought in. returns how many songs left the library */
    suspend fun deleteImportsFrom(source: ImportSource): Int = withContext(Dispatchers.IO) {
        database.importSessions(source.key).sumOf { undo(it) }
    }

    /** every import from every service; files from the phone are left alone */
    suspend fun deleteAllImports(): Int = withContext(Dispatchers.IO) {
        database.importSessionsExcept(ImportSession.DEVICE_SOURCE).sumOf { undo(it) }
    }

    /** takes files imported from the phone out of the library. the files themselves stay on the phone */
    suspend fun removeLocalSongs(songIds: Collection<String>) = withContext(Dispatchers.IO) {
        val songs = songIds.mapNotNull { database.getSongById(it)?.song }.filter { it.isLocal }
        if (songs.isEmpty()) return@withContext
        database.withTransaction {
            songs.forEach { deleteLocalSong(it) }
        }
        deleteOwnCopies(songs)
    }

    private suspend fun undo(session: ImportSession): Int {
        val rows = database.importSessionSongs(session.id)
        // read before the transaction: a flow query inside it would wait on the transaction itself
        val playlists = database.importSessionPlaylistIds(session.id)
            .mapNotNull { database.playlist(it).first()?.playlist }
        val isLocal = session.source == ImportSession.DEVICE_SOURCE

        val unliked = mutableListOf<SongEntity>()
        val removedFiles = mutableListOf<SongEntity>()
        var removed = 0
        database.withTransaction {
            for (row in rows) {
                val song = getSongById(row.songId)?.song ?: continue
                if (isLocal) {
                    // a file the session added has nothing to fall back to: it goes entirely
                    deleteLocalSong(song)
                    removedFiles += song
                    removed++
                    continue
                }
                val claimedElsewhere = otherSessionsWithSong(row.songId, session.id) > 0
                var updated = song
                if (row.liked && song.liked) updated = updated.copy(liked = false, likedDate = null)
                if (row.addedToLibrary && !claimedElsewhere && song.inLibrary != null) updated = updated.copy(inLibrary = null)
                if (updated != song) {
                    update(updated)
                    removed++
                }
                if (updated.liked != song.liked) unliked += updated
                if (otherSessionsFromSource(row.songId, session.source, session.id) == 0) {
                    deleteImportedSong(row.songId, session.source)
                }
            }
            playlists.forEach { playlist ->
                delete(playlist)
                deleteImportedPlaylist(playlist.id)
            }
            deleteImportSessionSongs(session.id)
            deleteImportSession(session.id)
        }

        deleteOwnCopies(removedFiles)
        // a like still waiting to go up to youtube shouldn't go now; one already there is taken back
        PendingYouTubeLikes.remove(context, unliked.map { it.id })
        if (context.isSyncEnabled()) unliked.forEach { syncUtils.likeSong(it) }
        return removed
    }

    /** removes a local song's row and everything hanging off it that has no foreign key to it */
    private fun MusicDatabase.deleteLocalSong(song: SongEntity) {
        delete(song)
        deleteFormat(song.id)
        deleteLyrics(song.id)
        deleteImportSessionSong(song.id)
    }

    /** what the removed songs left in app folders (their covers, a shared file's own copy) and the read access a picked file kept, all useless once they're gone */
    private suspend fun deleteOwnCopies(songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        val sharedRoot = File(context.filesDir, "shared_audio")
        // asked once for the whole batch, since removing hundreds of songs would otherwise ask hundreds of times
        val stillInLibrary = database.localUris().toHashSet()
        val held = runCatching { context.contentResolver.persistedUriPermissions.mapTo(HashSet()) { it.uri } }.getOrDefault(emptySet())
        for (song in songs) {
            runCatching { LocalArtwork.deleteIfUnused(context, database, song.thumbnailUrl) }
            val uri = song.localUri?.let(Uri::parse) ?: continue
            if (song.localUri in stillInLibrary) continue
            runCatching {
                uri.takeIf { it.scheme == "file" }?.path?.let { File(it) }
                    ?.takeIf { it.startsWith(sharedRoot) }
                    ?.parentFile?.deleteRecursively()
                // android only keeps around 500 single-file grants and drops the oldest past that, so a removed song gives its back
                if (uri in held) context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
