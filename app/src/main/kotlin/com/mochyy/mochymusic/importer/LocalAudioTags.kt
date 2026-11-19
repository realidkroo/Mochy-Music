// LocalAudioTags.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.mochyy.mochymusic.R

// the artist a song gets when neither its tags nor its file name name one; local songs without one all share this artist
internal const val UnknownLocalArtist = "Unknown Artist"

internal data class LocalAudioMetadata(
    val title: String,
    val artist: String,
    val album: String?,
    val durationSeconds: Int,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val embeddedArtwork: ByteArray?,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
)

// reads a file's tags; a file without them gets its title and artist from its file name, never from the link android gave us
internal fun extractAudioMetadata(context: Context, uri: Uri): LocalAudioMetadata {
    val fromName = LocalFileNames.of(context, uri)?.let(LocalFileNames::guess)
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        fun tag(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }
        val tagTitle = tag(MediaMetadataRetriever.METADATA_KEY_TITLE)
        // the name's artist only counts when the name is about the same song as the tags
        val nameArtist = fromName?.artist?.takeIf { tagTitle == null || fromName.title.equals(tagTitle, ignoreCase = true) }
        val durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        // sample rate only has a key on android 12+
        val sampleRate = if (Build.VERSION.SDK_INT >= 31) {
            runCatching { tag(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() }.getOrNull()
        } else {
            null
        }
        // track and disc tags often read "3/12", the part before the slash is the number
        fun number(key: Int) = tag(key)?.substringBefore('/')?.trim()?.toIntOrNull()
        LocalAudioMetadata(
            title = tagTitle ?: fromName?.title ?: context.getString(R.string.unknown),
            artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: nameArtist
                ?: UnknownLocalArtist,
            album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
            durationSeconds = (durationMs / 1000).toInt(),
            mimeType = tag(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: context.contentResolver.getType(uri),
            bitrate = tag(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull(),
            sampleRate = sampleRate,
            embeddedArtwork = runCatching { retriever.embeddedPicture }.getOrNull(),
            albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
            trackNumber = number(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
            discNumber = number(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
            // opus and flac keep the year as DATE, which android reports under its own key
            year = (tag(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: tag(MediaMetadataRetriever.METADATA_KEY_DATE))?.take(4)?.toIntOrNull(),
            genre = cleanGenre(tag(MediaMetadataRetriever.METADATA_KEY_GENRE)),
        )
    } catch (_: Exception) {
        LocalAudioMetadata(
            title = fromName?.title ?: context.getString(R.string.unknown),
            artist = fromName?.artist ?: UnknownLocalArtist,
            album = null,
            durationSeconds = 0,
            mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull(),
            bitrate = null,
            sampleRate = null,
            embeddedArtwork = null,
        )
    } finally {
        retriever.release()
    }
}

// only the genre tag, for files that came in before genres were kept; null when the file won't open, so it's tried again later
internal fun readGenreTag(context: Context, uri: Uri): String? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        cleanGenre(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)).orEmpty()
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

// "Rock; Pop" or null-separated tags become "Rock; Pop", and old id3 number codes like "(17)" are dropped since they'd show as numbers
internal fun cleanGenre(raw: String?): String? {
    val genres = raw.orEmpty()
        .split(';', '\u0000')
        .map { it.trim().replace(Id3GenreCode, "").trim() }
        .filter { it.isNotEmpty() && it != "<unknown>" && !it.all(Char::isDigit) }
        .distinctBy { it.lowercase() }
    return genres.takeIf { it.isNotEmpty() }?.joinToString(GenreSeparator)
}

private val Id3GenreCode = Regex("^\\(\\d+\\)\\s*")

// how several genres sit in one song's genre column
internal const val GenreSeparator = "; "
