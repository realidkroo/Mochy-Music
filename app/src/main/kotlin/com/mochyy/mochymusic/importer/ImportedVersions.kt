// ImportedVersions.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.models.toMediaMetadata
import com.music.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** swaps an imported song for another version (choose different version and refetch), the new song takes the old one's place in playlists, liked songs and the library */
object ImportedVersions {

    /** runs a swap to the end even when the menu or sheet that started it closes first */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** the track songId was imported as, null if no import brought it in, songs imported before tracks were remembered only have their own title and artist */
    suspend fun trackFor(context: Context, database: MusicDatabase, songId: String): TrackMatch? =
        withContext(Dispatchers.IO) {
            ImportMatches.get(context).forSong(songId)?.let { return@withContext it }
            if (songId !in database.importedSongIds()) return@withContext null
            val song = database.getSongById(songId) ?: return@withContext null
            val track = ImportedTrack(
                title = song.song.title,
                artist = song.artists.joinToString(", ") { it.name },
                album = null,
            )
            TrackMatch(
                key = track.trackKey(),
                title = track.title,
                artist = track.artist,
                source = ImportSource.OTHER.key,
                songId = songId,
            )
        }

    /** what a search turns up for match's track, best fit first */
    suspend fun candidates(match: TrackMatch): List<TrackMatcher.Candidate> =
        withContext(Dispatchers.IO) { TrackMatcher().candidates(match.track) }

    /** makes song match's track from now on: saved for later imports, and swapped in now */
    suspend fun choose(context: Context, database: MusicDatabase, match: TrackMatch, song: SongItem) {
        withContext(Dispatchers.IO) { ImportMatches.get(context).pin(match, song) }
        replace(context, database, match.songId, song)
    }

    /** refetch for an imported song: forgets a hand-picked version and searches again, returns the new song or null if nothing changed so the caller can say so */
    suspend fun refetch(context: Context, database: MusicDatabase, match: TrackMatch): SongItem? {
        val matches = ImportMatches.get(context)
        withContext(Dispatchers.IO) { matches.unpin(match) }
        val found = withContext(Dispatchers.IO) { TrackMatcher().match(match.track) } ?: return null
        if (found.id == match.songId) return null
        withContext(Dispatchers.IO) { matches.moveTo(match, found) }
        replace(context, database, match.songId, found)
        return found
    }

    private suspend fun replace(context: Context, database: MusicDatabase, oldId: String, song: SongItem) {
        if (oldId == song.id) return
        // read before the transaction: a room flow waited on inside one never answers
        val playlistMaps = database.playlistIdsContainingSongs(listOf(oldId))
            .map { id -> database.playlistSongs(id).first().map { it.map } }
        var likeMoved = false
        database.withTransaction {
            insert(song.toMediaMetadata())
            val old = getSongById(oldId)?.song
            val new = getSongById(song.id)?.song
            if (old != null && new != null) {
                update(
                    new.copy(
                        inLibrary = new.inLibrary ?: old.inLibrary,
                        addedByLike = if (new.inLibrary != null) new.addedByLike else old.addedByLike,
                        liked = new.liked || old.liked,
                        likedDate = new.likedDate ?: old.likedDate,
                    ),
                )
                // the wrong version leaves the library and liked songs it was only in for this track
                update(old.copy(inLibrary = null, liked = false, likedDate = null))
                likeMoved = old.liked
            }
            playlistMaps.forEach { maps ->
                val alreadyThere = maps.any { it.songId == song.id }
                maps.filter { it.songId == oldId }.forEach { map ->
                    if (alreadyThere) delete(map) else update(map.copy(songId = song.id))
                }
            }
            // the "imported from" records move with it, whichever services they're for
            openHelper.writableDatabase.apply {
                execSQL("UPDATE OR IGNORE imported_song SET songId = ? WHERE songId = ?", arrayOf(song.id, oldId))
                execSQL("DELETE FROM imported_song WHERE songId = ?", arrayOf(oldId))
            }
        }
        if (likeMoved) {
            PendingYouTubeLikes.remove(context, listOf(oldId))
            PendingYouTubeLikes.add(context, listOf(song.id))
        }
    }
}
