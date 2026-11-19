// ImportModels.kt

package com.mochyy.mochymusic.importer

import com.music.innertube.models.SongItem

data class ImportedTrack(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long? = null,
    /** exact youtube id when the source already knows it (mochy csv, youtube links) — no search needed */
    val videoId: String? = null,
    val thumbnailUrl: String? = null,
    /** full youtube item when the source is youtube itself, so nothing is lost converting back */
    val ytItem: SongItem? = null,
    /** when it was added to the source's list (liked, saved to the library), so imports keep that order */
    val addedAtMs: Long? = null,
    /** the track's own id on its service ("spotify:track:..."), which a hand-picked version is saved under */
    val sourceId: String? = null,
)

/** where imported music came from, kept on every imported song so the library can list imported from spotify and the youtube sync leaves these songs alone */
enum class ImportSource(val key: String, val label: String) {
    SPOTIFY("spotify", "Spotify"),
    APPLE_MUSIC("apple_music", "Apple Music"),
    TIDAL("tidal", "Tidal"),
    YOUTUBE_MUSIC("youtube_music", "YouTube Music"),
    DEEZER("deezer", "Deezer"),
    SOUNDCLOUD("soundcloud", "SoundCloud"),

    /** a file or a service with no name of its own (a tunemymusic csv, a mochy backup) */
    OTHER("other", "other sources");

    /** the sources the library gives a category of their own */
    val isListed: Boolean get() = this != OTHER

    companion object {
        fun fromKey(key: String?): ImportSource? = entries.firstOrNull { it.key == key }
    }
}

data class ParsedImport(
    val likedSongs: List<ImportedTrack>,
    val playlists: Map<String, List<ImportedTrack>>,
    /** songs that go straight into the library (an account's whole song library), in no playlist */
    val librarySongs: List<ImportedTrack> = emptyList(),
    /** things the user should know before importing, e.g. a source that only shares part of a playlist */
    val warnings: List<String> = emptyList(),
    /** which service this came from; null when that isn't known (a plain csv) */
    val provider: ImportSource? = null,
    /** each playlist's own cover on the source service, by its name in playlists */
    val playlistCovers: Map<String, String> = emptyMap(),
    /** where each playlist lives on the service, by its name in playlists, so it can be re-synced */
    val playlistOrigins: Map<String, PlaylistOrigin> = emptyMap(),
) {
    val totalSongs: Int get() = likedSongs.size + librarySongs.size + playlists.values.sumOf { it.size }
    val totalPlaylists: Int get() = playlists.size
}

/** where a playlist lives on its service. either is enough for re-sync to read it again */
data class PlaylistOrigin(
    /** its id on the service: what the service's account lists it under */
    val remoteId: String? = null,
    /** the share link it was read from */
    val remoteUrl: String? = null,
    /** whoever made it there, so the playlist page can still credit them after the import */
    val author: PlaylistAuthor? = null,
)

/** the person, curator or artist a playlist belongs to on its service */
data class PlaylistAuthor(
    val name: String,
    val avatarUrl: String? = null,
    /** their page on the service, opened when the name on the playlist page is tapped */
    val profileUrl: String? = null,
)

/** one re-sync run: the playlist being read now, and what the run has done so far */
data class ResyncSummary(
    val playlistName: String,
    /** how many playlists the run re-syncs (re-sync all), and how many are finished */
    val playlists: Int = 1,
    val done: Int = 0,
    /** new songs added over every playlist so far, and how many playlists got any */
    val added: Int = 0,
    val playlistsChanged: Int = 0,
    /** playlists that couldn't be read or added to */
    val failed: List<String> = emptyList(),
    /** something to know about how one was read, like a link that only shares part of the playlist */
    val note: String? = null,
)

/** a re-sync's read of a playlist: its songs on the service now, and where it was found */
data class ResyncRead(
    val tracks: List<ImportedTrack>,
    val origin: PlaylistOrigin,
    val note: String? = null,
)

/** one playlist for MusicImportService to re-sync, read runs inside the re-sync so the wait for the service already shows as progress */
class ResyncRequest(
    val playlistId: String,
    val playlistName: String,
    val source: ImportSource,
    val read: suspend () -> ResyncRead,
)

data class ImportOptions(
    /** also create the playlists (and likes) on the signed-in youtube account */
    val mirrorToYouTube: Boolean = false,
)

/** a track that couldn't be matched, with where it came from so the user can find it by hand */
data class UnmatchedTrack(
    val track: ImportedTrack,
    val playlistName: String?,
)

data class ImportProgress(
    val isRunning: Boolean = false,
    val totalTracks: Int = 0,
    val processedTracks: Int = 0,
    val matchedTracks: Int = 0,
    val currentLabel: String = "",
    val isDone: Boolean = false,
    val cancelled: Boolean = false,
    /** the service being imported from, for the "adding 12 of 340 tracks from spotify" banner */
    val source: ImportSource? = null,
    val playlistsCreated: Int = 0,
    val likedAdded: Int = 0,
    val unmatched: List<UnmatchedTrack> = emptyList(),
    /** playlists that imported locally but couldn't be created or filled on youtube */
    val youtubeFailures: List<String> = emptyList(),
    val error: String? = null,
    /** set while this run is a re-sync of one playlist rather than an import */
    val resync: ResyncSummary? = null,
)

/** a youtube song needs no matching: the id is exact, and the full item is kept */
internal fun SongItem.toImportedTrack() = ImportedTrack(
    title = title,
    artist = artists.joinToString(", ") { it.name },
    album = album?.name,
    durationMs = duration?.times(1000L),
    videoId = id,
    thumbnailUrl = thumbnail,
    ytItem = this,
)
