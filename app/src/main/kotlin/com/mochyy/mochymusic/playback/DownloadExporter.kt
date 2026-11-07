// DownloadExporter.kt

package com.mochyy.mochymusic.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.offline.Download
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.DocumentTree
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

// the folder everything goes into, wherever the user points the export
private const val RootFolder = "mochy-preserve"

private const val CoverPx = 1200

private val SyncedLine = Regex("^\\s*\\[\\d+:\\d", RegexOption.MULTILINE)

// how the exported songs are sorted into folders
enum class ExportLayout { Playlists, Albums }

data class ExportChoice(val layout: ExportLayout, val covers: Boolean, val lyrics: Boolean)

sealed interface ExportStep {
    data object Idle : ExportStep
    data class Working(val done: Int, val total: Int) : ExportStep
    data class Done(val songs: Int, val skipped: Int) : ExportStep
    data class Failed(val message: String) : ExportStep
}

// copies downloaded songs out as plain files into a folder the user picked; where mochy keeps its own downloads never changes
@Singleton
class DownloadExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val downloadUtil: DownloadUtil,
) {
    // one song headed for one folder; [folderCover] is the picture that folder shares, when it has one
    private class Entry(val path: List<String>, val song: Song, val folderCover: String?)

    // app-wide so leaving the storage page doesn't stop an export halfway
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _step = MutableStateFlow<ExportStep>(ExportStep.Idle)
    val step: StateFlow<ExportStep> = _step.asStateFlow()

    fun start(tree: Uri, choice: ExportChoice) {
        if (_step.value is ExportStep.Working) return
        _step.value = ExportStep.Working(0, 0)
        scope.launch {
            runCatching { export(tree, choice) }
                .onSuccess { _step.value = it }
                .onFailure {
                    Log.e("DownloadExporter", "Export stopped", it)
                    _step.value = ExportStep.Failed(context.getString(R.string.storage_export_failed))
                }
        }
    }

    fun clear() {
        if (_step.value !is ExportStep.Working) _step.value = ExportStep.Idle
    }

    private suspend fun export(tree: Uri, choice: ExportChoice): ExportStep.Done {
        val ids = downloadUtil.downloads.value.values.filter { it.state == Download.STATE_COMPLETED }.map { it.request.id }
        val songs = if (ids.isEmpty()) emptyList() else database.getSongsByIds(ids)
        val entries = when (choice.layout) {
            ExportLayout.Playlists -> byPlaylist(songs)
            ExportLayout.Albums -> byAlbum(songs)
        }
        val documents = DocumentTree(context.contentResolver, tree)
        val root = documents.folder(documents.rootId, RootFolder)
        val folders = HashMap<List<String>, String>()
        val usedNames = HashMap<List<String>, MutableSet<String>>()
        val coveredFolders = HashSet<List<String>>()
        var skipped = 0

        entries.forEachIndexed { index, entry ->
            _step.value = ExportStep.Working(index, entries.size)
            val folder = folders.getOrPut(entry.path) {
                entry.path.fold(root) { parent, name -> documents.folder(parent, name) }
            }
            val audio = audioOf(entry.song.id)
            if (audio == null) {
                skipped++
                return@forEachIndexed
            }
            val base = uniqueName(usedNames.getOrPut(entry.path) { HashSet() }, baseName(entry.song))
            documents.write(folder, "$base.${audio.extension}") { out -> audio.copyTo(out) }

            if (choice.lyrics) lyricsOf(entry.song.id)?.let { (text, ext) ->
                documents.write(folder, "$base.$ext") { it.write(text.toByteArray()) }
            }
            if (choice.covers) {
                // an album folder shares one cover.jpg, a mixed folder gets a picture per song
                if (entry.folderCover != null) {
                    if (coveredFolders.add(entry.path)) writeCover(documents, folder, "cover.jpg", entry.folderCover)
                } else {
                    entry.song.song.thumbnailUrl?.let { writeCover(documents, folder, "$base.jpg", it) }
                }
            }
        }
        return ExportStep.Done(songs = entries.size - skipped, skipped = skipped)
    }

    // liked songs and each playlist get a folder under Playlist, everything in neither goes in Music
    private suspend fun byPlaylist(songs: List<Song>): List<Entry> {
        val byId = songs.associateBy { it.id }
        val out = ArrayList<Entry>()
        val placed = HashSet<String>()
        val playlistFolder = context.getString(R.string.storage_export_folder_playlists)

        val liked = songs.filter { it.song.liked }.sortedByDescending { it.song.likedDate }
        liked.forEach { out += Entry(listOf(playlistFolder, context.getString(R.string.storage_export_folder_liked)), it, null) }
        placed += liked.map { it.id }

        val playlistIds = if (byId.isEmpty()) emptyList() else database.playlistIdsContainingSongs(byId.keys.toList())
        val takenNames = HashSet<String>()
        for (playlistId in playlistIds) {
            val playlist = database.playlist(playlistId).first() ?: continue
            val name = uniqueName(takenNames, DocumentTree.safeName(playlist.playlist.name, playlistId))
            database.playlistSongs(playlistId).first()
                .mapNotNull { byId[it.map.songId] }
                .distinctBy { it.id }
                .forEach { song ->
                    out += Entry(listOf(playlistFolder, name), song, null)
                    placed += song.id
                }
        }

        val musicFolder = context.getString(R.string.storage_export_folder_music)
        songs.filter { it.id !in placed }.sortedBy { it.song.title.lowercase() }
            .forEach { out += Entry(listOf(musicFolder), it, null) }
        return out
    }

    // a folder per album under Albums with the album's cover, songs from no album go in Music
    private fun byAlbum(songs: List<Song>): List<Entry> {
        val albumsFolder = context.getString(R.string.storage_export_folder_albums)
        val musicFolder = context.getString(R.string.storage_export_folder_music)
        val (withAlbum, loose) = songs.partition { albumTitle(it) != null }
        val out = ArrayList<Entry>()
        withAlbum.groupBy { it.album?.id ?: albumTitle(it)!! }.values
            .sortedBy { albumTitle(it.first())!!.lowercase() }
            .forEach { albumSongs ->
                val first = albumSongs.first()
                val name = DocumentTree.safeName(albumTitle(first)!!, first.id)
                val cover = first.album?.thumbnailUrl ?: first.song.thumbnailUrl
                albumSongs.forEach { out += Entry(listOf(albumsFolder, name), it, cover) }
            }
        loose.sortedBy { it.song.title.lowercase() }.forEach { out += Entry(listOf(musicFolder), it, null) }
        return out
    }

    private fun albumTitle(song: Song): String? = (song.album?.title ?: song.song.albumName)?.takeIf { it.isNotBlank() }

    // "artist - title", the way most music players and file browsers expect a loose song
    private fun baseName(song: Song): String {
        val artist = song.artists.joinToString(", ") { it.name }
        val raw = if (artist.isBlank()) song.song.title else "$artist - ${song.song.title}"
        return DocumentTree.safeName(raw, song.id)
    }

    private fun uniqueName(taken: MutableSet<String>, name: String): String {
        var candidate = name
        var n = 2
        while (!taken.add(candidate.lowercase())) candidate = "$name ($n)".also { n++ }
        return candidate
    }

    // a downloaded song's bytes, straight from the pieces in the download cache, and what kind of file they make
    private class CachedAudio(private val spans: List<java.io.File>, val extension: String) {
        fun copyTo(out: OutputStream) = spans.forEach { span -> span.inputStream().use { it.copyTo(out, 256 * 1024) } }
    }

    // null when the download isn't whole, so a broken song is skipped rather than written out short
    private fun audioOf(mediaId: String): CachedAudio? {
        val cache = downloadUtil.downloadCache
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(mediaId))
        val spans = cache.getCachedSpans(mediaId).filter { it.isCached }.sortedBy { it.position }
        var expected = 0L
        for (span in spans) {
            if (span.position != expected) return null
            expected += span.length
        }
        if (spans.isEmpty() || (length > 0 && expected != length)) return null
        val files = spans.mapNotNull { it.file }
        if (files.size != spans.size) return null
        return CachedAudio(files, extensionOf(files.first()))
    }

    // read off the file's first bytes rather than the saved format, since a module download can carry any container
    private fun extensionOf(first: java.io.File): String {
        val head = ByteArray(12)
        val read = runCatching { first.inputStream().use { it.read(head) } }.getOrDefault(0)
        if (read < 4) return "m4a"
        fun at(offset: Int, text: String) = text.indices.all { head[offset + it] == text[it].code.toByte() }
        return when {
            at(0, "fLaC") -> "flac"
            at(0, "OggS") -> "ogg"
            at(0, "ID3") || (head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0) -> "mp3"
            at(0, "RIFF") -> "wav"
            head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() -> "webm"
            read >= 8 && at(4, "ftyp") -> "m4a"
            else -> "m4a"
        }
    }

    // timed lyrics go out as .lrc next to the song, which most players pick up; apple-style ones as .ttml and plain ones as .txt
    private suspend fun lyricsOf(songId: String): Pair<String, String>? {
        val text = database.lyrics(songId).first()?.lyrics ?: return null
        if (text.isBlank() || text == LyricsEntity.LYRICS_NOT_FOUND) return null
        val trimmed = text.trimStart()
        val ext = when {
            trimmed.startsWith("<") -> "ttml"
            SyncedLine.containsMatchIn(text) -> "lrc"
            else -> "txt"
        }
        return text to ext
    }

    private suspend fun writeCover(documents: DocumentTree, folder: String, name: String, url: String) {
        val request = ImageRequest.Builder(context)
            .data(url.resize(CoverPx, CoverPx))
            .allowHardware(false)
            .build()
        val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull() ?: return
        // a missing cover never stops the songs
        runCatching {
            documents.write(folder, name) { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw IOException("couldn't save $name")
            }
        }
    }
}
