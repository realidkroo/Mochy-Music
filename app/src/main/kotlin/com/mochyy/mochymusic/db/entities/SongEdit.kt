// SongEdit.kt

package com.mochyy.mochymusic.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mochyy.mochymusic.models.MediaMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// what someone changed on a song by hand in its edit sheet; a null field follows youtube, a set one survives every refetch and sync
@Immutable
@Entity(tableName = "song_edit")
data class SongEdit(
    @PrimaryKey val songId: String,
    val title: String? = null,
    // the picked artists as json, so a refetch can't swap them back
    val artists: String? = null,
    val album: String? = null,
    val year: Int? = null,
    // a provider's cover link, or a file:// picture the user picked
    val cover: String? = null,
    // the youtube video picked in "select video" for the cover video and the video mode
    val videoId: String? = null,
    // "none" in select video: this song never shows a video
    @ColumnInfo(defaultValue = "0")
    val noVideo: Boolean = false,
) {
    val artistList: List<EditedArtist>?
        get() = artists?.let(EditedArtist::decode)

    // the queue's copy of the song with these edits laid over it
    fun applyTo(metadata: MediaMetadata): MediaMetadata = metadata.copy(
        title = title ?: metadata.title,
        artists = artistList?.map { MediaMetadata.Artist(it.id, it.name) } ?: metadata.artists,
        // only renames an album the song already links to, a made up album id would open an empty page
        album = if (album != null && metadata.album != null) metadata.album.copy(title = album) else metadata.album,
        thumbnailUrl = cover ?: metadata.thumbnailUrl,
    )
}

@Immutable
@Serializable
data class EditedArtist(val id: String, val name: String, val thumbnail: String? = null) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(serializer())

        fun encode(artists: List<EditedArtist>): String = json.encodeToString(serializer, artists)

        // a broken row reads as no edit rather than taking the song's artists away
        fun decode(text: String): List<EditedArtist>? =
            runCatching { json.decodeFromString(serializer, text) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}
