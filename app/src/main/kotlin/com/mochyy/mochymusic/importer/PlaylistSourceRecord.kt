// PlaylistSourceRecord.kt

package com.mochyy.mochymusic.importer

import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.ImportedPlaylist
import com.mochyy.mochymusic.db.entities.ImportedPlaylistTrack
import com.mochyy.mochymusic.db.entities.PlaylistEntity
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/** what mochy remembers about an imported playlist on its service: where it lives and every song it had last time, so reading it again only brings in what is new */
internal class PlaylistSourceRecord(private val database: MusicDatabase) {

    /** the copy an earlier import made of this playlist: same place on source, or else one with its name (old imports, mochy backups), a copy tied to a different playlist is never it */
    suspend fun existingCopy(source: ImportSource, name: String, origin: PlaylistOrigin?): PlaylistEntity? {
        origin?.remoteId
            ?.let { database.importedPlaylistByRemoteId(source.key, it) }
            ?.let { database.playlist(it.playlistId).first()?.playlist }
            ?.let { return it }
        return database.playlistsByNameAsc().first()
            .filter { it.playlist.name == name && it.playlist.isEditable }
            .firstOrNull { candidate ->
                val tied = database.getImportedPlaylist(candidate.id)
                origin?.remoteId == null || tied?.source != source.key || tied.remoteId == null
            }
            ?.playlist
    }

    /** the tracks playlistId hasn't had from the service before, each once, older playlists only have their songs so those are paired by title and artist */
    suspend fun newTracks(playlistId: String, tracks: List<ImportedTrack>): List<ImportedTrack> {
        val known = database.importedPlaylistTrackKeys(playlistId).toHashSet()
        if (known.isEmpty()) {
            database.playlistSongs(playlistId).first().forEach { entry ->
                val title = TrackMatcher.normalizeTitle(entry.song.song.title)
                known += youTubeKey(entry.song.id)
                entry.song.artists.forEach { known += "$title|${TrackMatcher.normalizeText(it.name)}" }
            }
        }
        val seen = HashSet<String>()
        return tracks.filter { track ->
            val key = track.trackKey()
            key !in known && seen.add(key)
        }
    }

    /** saves where the playlist lives on source, who made it there, and every song it has there now */
    suspend fun remember(source: ImportSource, playlistId: String, tracks: List<ImportedTrack>, origin: PlaylistOrigin?) {
        // one made here, or from another service, that an import topped up by name: nothing to re-sync with
        if (database.getImportedPlaylist(playlistId)?.source != source.key) return
        database.withTransaction {
            val author = origin?.author
            updateImportedPlaylistOrigin(
                playlistId, origin?.remoteId, origin?.remoteUrl, LocalDateTime.now(),
                author?.name, author?.avatarUrl, author?.profileUrl,
            )
            insertImportedPlaylistTracks(tracks.map { ImportedPlaylistTrack(playlistId, it.trackKey()) }.distinct())
        }
    }

    /** a youtube playlist saved as a copy from its own page, no import owned it so this makes it one re-sync can read again by youTubeId */
    suspend fun rememberYouTubeCopy(playlistId: String, youTubeId: String, songIds: List<String>) {
        val now = LocalDateTime.now()
        database.withTransaction {
            insert(
                ImportedPlaylist(
                    playlistId = playlistId,
                    source = ImportSource.YOUTUBE_MUSIC.key,
                    sessionId = null,
                    importedAt = now,
                    remoteId = youTubeId,
                    remoteUrl = "https://music.youtube.com/playlist?list=$youTubeId",
                    lastSyncedAt = now,
                ),
            )
            insertImportedPlaylistTracks(songIds.distinct().map { ImportedPlaylistTrack(playlistId, youTubeKey(it)) })
        }
    }
}

/** what re-sync knows a service's song by: exact for youtube songs, else cleaned title and first artist, which match whether read through a link or an account */
internal fun ImportedTrack.trackKey(): String {
    (videoId ?: ytItem?.id)?.let { return youTubeKey(it) }
    val firstArtist = TrackMatcher.splitArtists(artist).firstOrNull().orEmpty()
    return "${TrackMatcher.normalizeTitle(title)}|${TrackMatcher.normalizeText(firstArtist)}"
}

private fun youTubeKey(videoId: String) = "yt:$videoId"
