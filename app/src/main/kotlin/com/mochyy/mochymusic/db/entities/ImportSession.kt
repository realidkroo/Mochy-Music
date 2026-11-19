package com.mochyy.mochymusic.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * One run of an import: everything brought in from one service (or from the phone's storage) in
 * one go. Kept so "Undo import session" can take exactly that run back out, and nothing else.
 *
 * [source] is an `ImportSource.key`, or [DEVICE_SOURCE] for files imported from the phone.
 */
@Immutable
@Entity(tableName = "import_session")
data class ImportSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val startedAt: LocalDateTime,
) {
    companion object {
        /** The source of a session that imported audio files from the phone. */
        const val DEVICE_SOURCE = "device"
    }
}

/**
 * What one session did to one song. Undoing the session only reverses what it changed itself: a
 * song that was already in the library, or already liked, before the import stays that way.
 */
@Immutable
@Entity(
    tableName = "import_session_song",
    primaryKeys = ["sessionId", "songId"],
    indices = [Index(value = ["songId"])],
)
data class ImportSessionSong(
    val sessionId: Long,
    val songId: String,
    /** The import put the song in the library (or, for a file, created it). */
    val addedToLibrary: Boolean,
    /** The import liked the song. */
    val liked: Boolean,
)

// a song an import liked, with the newest import that did it, so a lost like can come back near where it was
data class ImportedLike(
    val songId: String,
    val importedAt: LocalDateTime,
)

/**
 * A playlist that came from a service, so the Library can count "Spotify playlists" and deleting
 * Spotify's imports can take its playlists with it. [sessionId] is null for a playlist that YouTube
 * sync brought in: it follows the account, so no import session owns it.
 *
 * [remoteId] and [remoteUrl] say where it lives on the service, so Re-sync can read it again.
 * Imports from before version 41 have neither; Re-sync then looks the playlist up by its name.
 */
@Immutable
@Entity(
    tableName = "imported_playlist",
    indices = [Index(value = ["source"])],
)
data class ImportedPlaylist(
    @PrimaryKey val playlistId: String,
    val source: String,
    val sessionId: Long?,
    val importedAt: LocalDateTime,
    /** The playlist's id on the service, the one its account lists it under. */
    val remoteId: String? = null,
    /** The share link it was read from, when it came in by link. */
    val remoteUrl: String? = null,
    val lastSyncedAt: LocalDateTime? = null,
    /** who made it on the service, so its page can credit them; imports from before version 45 have none */
    val authorName: String? = null,
    val authorAvatarUrl: String? = null,
    /** the author's page on the service */
    val authorUrl: String? = null,
)

/**
 * A song the service's playlist already had the last time it was read (see `trackKey`). Re-sync
 * only adds what isn't listed here, so songs the user took out of the local copy stay out, and
 * songs already matched aren't searched for again.
 */
@Immutable
@Entity(
    tableName = "imported_playlist_track",
    primaryKeys = ["playlistId", "trackKey"],
)
data class ImportedPlaylistTrack(
    val playlistId: String,
    val trackKey: String,
)
