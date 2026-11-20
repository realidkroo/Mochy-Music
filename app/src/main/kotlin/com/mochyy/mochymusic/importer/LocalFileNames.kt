// LocalFileNames.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

// what a file's name says about it: the song it holds, and whether it's music, lyrics or a picture
object LocalFileNames {
    enum class Kind { Audio, Lyrics, Image, Other }

    // what a file name says when the tags say nothing; artist is null when the name doesn't carry one
    data class Guess(val artist: String?, val title: String)

    private val AudioExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "alac", "aiff", "aif", "ape", "mka", "webm", "oga", "wv", "dsf", "dff", "amr", "3gp")

    // audio the player has no reader for: these came in fine, then never played
    private val UnplayableExtensions = setOf("wma", "aiff", "aif", "ape", "wv", "dsf", "dff")
    private val UnplayableMimeTypes = setOf(
        "audio/x-ms-wma", "audio/aiff", "audio/x-aiff", "audio/ape", "audio/x-ape", "audio/x-monkeys-audio",
        "audio/wavpack", "audio/x-wavpack", "audio/dsf", "audio/x-dsf", "audio/dff", "audio/x-dff", "audio/x-dsd",
    )

    // every lyrics file LyricsFileReader understands, from plain lrc to kugou's krc
    private val LyricsExtensions = setOf("lrc", "elrc", "lrcx", "ttml", "xml", "srt", "vtt", "ass", "ssa", "txt", "yrc", "qrc", "krc")

    private val ImageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "avif")

    // the names other players give a folder's shared picture, most likely first
    private val FolderCoverNames = listOf("cover", "folder", "front", "album", "albumart", "artwork", "albumartsmall")

    // "01 - ", "01. ", "1) " or a zero-padded "01 " in front of a ripped album's song
    private val TrackNumber = Regex("^(?:\\d{1,3}\\s*[.)_-]\\s*|0\\d\\s+)(?=\\S)")
    private val ArtistSeparator = Regex("\\s+[-–—]\\s+")
    private val Spaces = Regex("\\s+")

    fun kind(name: String, mime: String? = null): Kind {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            extension in LyricsExtensions -> Kind.Lyrics
            extension in AudioExtensions || mime?.startsWith("audio/") == true -> Kind.Audio
            extension in ImageExtensions || mime?.startsWith("image/") == true -> Kind.Image
            else -> Kind.Other
        }
    }

    // false for wma, aiff, ape, wavpack and dsd, which the player can't open, so they're left out of an import instead of failing on every play
    fun canPlay(name: String, mime: String? = null): Boolean {
        if (name.substringAfterLast('.', "").lowercase() in UnplayableExtensions) return false
        return mime?.lowercase() !in UnplayableMimeTypes
    }

    // "night drive.mp3" for any file mochy can open; null when nothing better than an id is known
    fun of(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path?.let { File(it).name }
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        if (queried != null) return queried
        // a folder's document id ends in the file's path, "primary:Music/night drive.mp3"; a bare number is a media id, not a name
        return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?.takeIf { '.' in it && it.substringBeforeLast('.').isNotBlank() }
    }

    fun withoutExtension(name: String): String =
        if (kind(name) != Kind.Other) name.substringBeforeLast('.') else name

    // "MILI - Not My Paradiso" as MILI and Not My Paradiso, the way mochy's export and most rips name songs
    fun guess(fileName: String): Guess {
        var base = withoutExtension(fileName).trim()
        // names downloaded from chats often swap every space for an underscore
        if (' ' !in base && '_' in base) base = base.replace('_', ' ')
        base = base.replace(TrackNumber, "").replace(Spaces, " ").trim()
        val parts = base.split(ArtistSeparator, limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            return Guess(artist = parts[0].trim(), title = parts[1].trim())
        }
        return Guess(artist = null, title = base)
    }

    // how a song and its lyrics or picture find each other: same name, whatever the case, the extension or underscores for spaces
    fun matchKey(fileName: String): String =
        withoutExtension(fileName).lowercase().replace('_', ' ').replace(Spaces, " ").trim()

    // where a picture ranks as the folder's shared cover, lower is better; null when its name isn't a cover name
    fun folderCoverRank(fileName: String): Int? =
        FolderCoverNames.indexOf(matchKey(fileName)).takeIf { it >= 0 }
}
