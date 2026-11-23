package com.mochyy.mochymusic.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.music.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "song",
    indices = [
        Index(
            value = ["albumId"]
        )
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: Int = -1,
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val year: Int? = null,
    val date: LocalDateTime? = null,
    val dateModified: LocalDateTime? = null,
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val totalPlayTime: Long = 0,
    val inLibrary: LocalDateTime? = null,
    val dateDownload: LocalDateTime? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    @ColumnInfo(defaultValue = "0")
    val lyricsOffset: Int = 0,
    @ColumnInfo(defaultValue = true.toString())
    val romanizeLyrics: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val isDownloaded: Boolean = false,
    @ColumnInfo(name = "isUploaded", defaultValue = false.toString())
    val isUploaded: Boolean = false,
    @ColumnInfo(name = "isVideo", defaultValue = false.toString())
    val isVideo: Boolean = false,
    @ColumnInfo(name = "localUri", defaultValue = "NULL")
    val localUri: String? = null,
    // in the library only because it was liked, so its album stays out of Albums unless the old way is on
    @ColumnInfo(defaultValue = "0")
    val addedByLike: Boolean = false,
    // a local file's genre tag, "" once read from a file that has none, null until it's been read
    val genre: String? = null,
) {
    fun localToggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
    )

    // Pure local state update: callers sync to YouTube through SyncUtils.likeSong so retries,
    // offline queueing, YouTubeLikesMemory and import-exclusion rules stay consistent (no double-firing).
    fun toggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
        inLibrary = if (!liked) inLibrary ?: LocalDateTime.now() else inLibrary,
        addedByLike = if (!liked && inLibrary == null) true else addedByLike,
    )

    // byLike is for the youtube sync, where a liked song lands in the library on its own
    fun toggleLibrary(syncToYouTube: Boolean = true, byLike: Boolean = false) = copy(
        liked = if (inLibrary == null) liked else false,
        inLibrary = if (inLibrary == null) LocalDateTime.now() else null,
        likedDate = if (inLibrary == null) likedDate else null,
        addedByLike = inLibrary == null && byLike,
    ).also {
        if (syncToYouTube) {
            CoroutineScope(Dispatchers.IO).launch {

                val addToLibrary = inLibrary == null
                YouTube.toggleSongLibrary(id, addToLibrary)
            }
        }
    }

    fun toggleUploaded() = copy(
        isUploaded = !isUploaded
    )
}
