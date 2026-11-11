// SongEditSaver.kt

package com.mochyy.mochymusic.songedit

import android.content.Context
import android.net.Uri
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.EditedArtist
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.db.entities.SongArtistMap
import com.mochyy.mochymusic.db.entities.SongEdit
import com.mochyy.mochymusic.playback.SongTuning
import com.mochyy.mochymusic.viewmodels.LyricsMenuViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

// the cover picked in the carousel: a provider's link, or a picture from the phone that still has to be copied in
sealed interface CoverPick {
    data class Link(val url: String) : CoverPick
    data class Picture(val uri: Uri) : CoverPick
}

// everything the edit sheet hands over when ok is pressed; a null means that part wasn't touched
data class SongEditDraft(
    val title: String,
    val cover: CoverPick?,
    val artists: List<EditedArtist>,
    val album: String,
    val year: Int?,
    val lyricsOffsetMs: Int,
    val speed: Float?,
    val eqPresetId: String?,
    val customLyrics: String?,
)

object SongEditSaver {
    // writes the edit into the song itself, so every list shows it, and into song_edit, so a refetch can't undo it
    suspend fun save(
        context: Context,
        database: MusicDatabase,
        tuning: SongTuning,
        songId: String,
        draft: SongEditDraft,
    ): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val song = database.getSongById(songId) ?: return@withContext false
        val entity = song.song
        val cover = when (val pick = draft.cover) {
            null -> null
            is CoverPick.Link -> pick.url
            // a picture that won't load leaves the cover as it was rather than failing the whole save
            is CoverPick.Picture -> SongCoverFiles.save(appContext, songId, pick.uri)
        }

        val before = database.songEditBlocking(songId)
        val title = draft.title.trim().ifBlank { entity.title }
        val album = draft.album.trim().ifBlank { null }
        val oldArtists = song.artists.map { it.id to it.name }
        val artistsChanged = draft.artists.isNotEmpty() && draft.artists.map { it.id to it.name } != oldArtists
        // a field is only locked once it differs from what youtube gave, or was locked already
        val edit = (before ?: SongEdit(songId)).copy(
            title = title.takeIf { it != entity.title || before?.title != null },
            artists = if (artistsChanged) EditedArtist.encode(draft.artists) else before?.artists,
            album = album.takeIf { it != entity.albumName || before?.album != null },
            year = draft.year.takeIf { it != entity.year || before?.year != null },
            cover = cover ?: before?.cover,
        )

        database.withTransaction {
            upsert(edit)
            update(
                entity.copy(
                    title = title,
                    thumbnailUrl = cover ?: entity.thumbnailUrl,
                    albumName = album,
                    year = draft.year,
                    lyricsOffset = draft.lyricsOffsetMs,
                ),
            )
            if (artistsChanged) replaceArtists(songId, draft.artists)
            draft.customLyrics?.let { upsert(LyricsEntity(id = songId, lyrics = it, provider = LyricsMenuViewModel.USER_EDITED)) }
        }

        // the picture that's the cover now stays, every older one of this song goes
        if (cover != null) SongCoverFiles.deleteStale(appContext, songId, cover)

        draft.speed?.let { speed ->
            val pitch = tuning.of(songId)?.pitch ?: 1f
            if (abs(speed - 1f) < 0.001f && abs(pitch - 1f) < 0.001f) tuning.clear(songId)
            else tuning.set(songId, SongTuning.Tuning(speed, pitch))
        }
        tuning.setEqPreset(songId, draft.eqPresetId)
        true
    }

    // the youtube video for the cover video and the video mode: a picked one, none at all, or both null to let mochy find it again
    suspend fun setVideo(database: MusicDatabase, songId: String, videoId: String?, noVideo: Boolean) =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                upsert((songEditBlocking(songId) ?: SongEdit(songId)).copy(videoId = videoId, noVideo = noVideo))
            }
        }

    private fun MusicDatabase.replaceArtists(songId: String, artists: List<EditedArtist>) {
        songArtistMap(songId).forEach { delete(it) }
        artists.forEachIndexed { index, artist ->
            // an artist already in the library keeps its own photo and details
            insert(
                ArtistEntity(
                    id = artist.id,
                    name = artist.name,
                    thumbnailUrl = artist.thumbnail,
                    channelId = artist.id.takeIf { it.startsWith("UC") },
                ),
            )
            insert(SongArtistMap(songId = songId, artistId = artist.id, position = index))
        }
    }
}
