// LocalSidecars.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import android.net.Uri
import com.mochyy.mochymusic.importer.LocalFileNames.Kind
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.songedit.LyricsFileReader
import java.io.ByteArrayOutputStream

// a song file and what came with it: the lyrics and picture sharing its name, or the folder's cover
data class LocalFile(val uri: Uri, val name: String?, val lyrics: Uri? = null, val cover: Uri? = null)

// a file seen in a folder or a pick, before it's known what it belongs to
class NamedFile(val uri: Uri, val name: String, val kind: Kind)

// lyrics and covers kept next to songs the way other players and mochy's own export leave them
object LocalSidecars {
    // a cover past this is a photo someone left in the folder, not album art
    private const val MaxCoverBytes = 20 * 1024 * 1024

    // songs paired with the lyrics and pictures named like them; [picked] files also pair a lone lyrics file or picture whatever its name, since the user chose them together
    fun pair(files: List<NamedFile>, picked: Boolean): List<LocalFile> {
        val songs = files.filter { it.kind == Kind.Audio }
        if (songs.isEmpty()) return emptyList()
        val lyricsFiles = files.filter { it.kind == Kind.Lyrics }
        val images = files.filter { it.kind == Kind.Image }
        // song.lrc beats song.txt when both are there
        val lyricsByName = lyricsFiles.groupBy { LocalFileNames.matchKey(it.name) }
            .mapValues { (_, same) -> same.minBy { if (it.name.endsWith(".txt", ignoreCase = true)) 1 else 0 } }
        val imageByName = images.associateBy { LocalFileNames.matchKey(it.name) }
        val ownImages = songs.mapNotNull { imageByName[LocalFileNames.matchKey(it.name)] }.toSet()
        val sharedCover = images.mapNotNull { image -> LocalFileNames.folderCoverRank(image.name)?.let { image to it } }
            .minByOrNull { it.second }?.first
            ?: (images - ownImages).singleOrNull()?.takeIf { picked }
        val loneLyrics = lyricsFiles.singleOrNull()?.takeIf { picked && songs.size == 1 }
        return songs.map { song ->
            val key = LocalFileNames.matchKey(song.name)
            LocalFile(
                uri = song.uri,
                name = song.name,
                lyrics = (lyricsByName[key] ?: loneLyrics)?.uri,
                cover = (imageByName[key] ?: sharedCover)?.uri,
            )
        }
    }

    // the files someone picked, sorted into songs with their lyrics and pictures; audio the player can't open is left out and counted
    fun pairPicked(context: Context, uris: List<Uri>): FolderScan {
        var unplayable = 0
        val named = uris.mapNotNull { uri ->
            val name = LocalFileNames.of(context, uri).orEmpty()
            val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            val kind = LocalFileNames.kind(name, mime)
            if (kind == Kind.Audio && !LocalFileNames.canPlay(name, mime)) {
                unplayable++
                return@mapNotNull null
            }
            NamedFile(uri, name, kind)
        }
        return FolderScan(pair(named, picked = true), unplayable = unplayable)
    }

    // the file's own lyrics against the ones beside it: whichever is timed closer wins, the file beside it on a tie since someone put it there
    fun betterLyrics(embedded: String?, beside: String?): String? {
        if (embedded == null || beside == null) return beside ?: embedded
        return if (LyricsUtils.syncKind(embedded) > LyricsUtils.syncKind(beside)) embedded else beside
    }

    suspend fun readLyrics(context: Context, uri: Uri): String? = LyricsFileReader.read(context, uri)?.text

    fun readCover(context: Context, uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (out.size() + read > MaxCoverBytes) return@use null
                out.write(buffer, 0, read)
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        }
    }.getOrNull()
}
