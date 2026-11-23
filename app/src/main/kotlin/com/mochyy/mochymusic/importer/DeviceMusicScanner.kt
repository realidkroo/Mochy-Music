// DeviceMusicScanner.kt

package com.mochyy.mochymusic.importer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** a song found in the phone's storage or on a mounted memory card, before it is imported */
@Immutable
data class DeviceSong(
    val uri: Uri,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    /** the album picture android keeps for the file, if it has one */
    val artworkUri: String?,
    /** true when this song lives on a removable memory card rather than internal storage */
    val onSdCard: Boolean = false,
)

/** finds the music already on the phone and any mounted memory card, once the user lets mochy read its storage */
object DeviceMusicScanner {
    /** android 13 split audio out of the general storage permission */
    val permission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasAccess(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** every song on the phone and mounted SD cards, newest first, minus alreadyImported; keeps ringtones/alarms/notifications out while still catching SD card FLAC/Opus files whose IS_MUSIC flag is 0 */
    suspend fun scan(context: Context, alreadyImported: Set<String>): List<DeviceSong> = withContext(Dispatchers.IO) {
        if (!hasAccess(context)) return@withContext emptyList()
        val collections: List<Pair<Uri, Boolean>> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val volumes = runCatching { MediaStore.getExternalVolumeNames(context) }.getOrNull().orEmpty()
            if (volumes.isNotEmpty()) {
                // primary storage first, then every mounted removable SD card volume
                volumes.sortedBy { if (it == MediaStore.VOLUME_EXTERNAL_PRIMARY) 0 else 1 }
                    .map { vol -> MediaStore.Audio.Media.getContentUri(vol) to (vol != MediaStore.VOLUME_EXTERNAL_PRIMARY) }
            } else {
                listOf(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) to false)
            }
        } else {
            listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to false)
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
        )
        // MediaStore often leaves IS_MUSIC=0 on FLAC/Opus/WAV files copied onto SD cards outside /Music;
        // accept tracks marked music OR non-alert audio >= 15 seconds so SD card libraries are never hidden
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR (" +
            "${MediaStore.Audio.Media.DURATION} >= 15000 AND " +
            "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0)"
        val songs = ArrayList<DeviceSong>()
        val seenUris = HashSet<String>(alreadyImported)
        for ((collection, isCardVolume) in collections) {
            try {
                context.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC",
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val externalBase = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    while (cursor.moveToNext()) {
                        // wma, aiff and the like would come in and never play, so they aren't offered
                        if (!LocalFileNames.canPlay(cursor.getString(nameColumn).orEmpty(), cursor.getString(mimeColumn))) continue
                        val id = cursor.getLong(idColumn)
                        val uri = ContentUris.withAppendedId(collection, id)
                        // also skip if already imported under the generic external volume URI
                        val externalUri = ContentUris.withAppendedId(externalBase, id).toString()
                        if (externalUri in seenUris || !seenUris.add(uri.toString())) continue
                        songs += DeviceSong(
                            uri = uri,
                            title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: "Unknown song",
                            // android writes "<unknown>" when a file has no artist tag
                            artist = cursor.getString(artistColumn)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING },
                            durationMs = cursor.getLong(durationColumn),
                            artworkUri = ContentUris.withAppendedId(AlbumArtUri, cursor.getLong(albumColumn)).toString(),
                            onSdCard = isCardVolume,
                        )
                    }
                }
            } catch (e: Exception) {
                // a storage provider or unmounted card that refuses the query shouldn't take the library down with it
                Timber.w(e, "Scanning $collection for music failed")
            }
        }
        songs
    }

    private val AlbumArtUri: Uri = Uri.parse("content://media/external/audio/albumart")
}
