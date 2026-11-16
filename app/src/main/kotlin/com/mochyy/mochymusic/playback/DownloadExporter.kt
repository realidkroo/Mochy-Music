// DownloadExporter.kt

package com.mochyy.mochymusic.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
import com.mochyy.mochymusic.songedit.LyricsFileReader
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.SequenceInputStream
import java.util.Collections
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
        val covers = CoverMemo()
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
            val lyrics = if (choice.lyrics) lyricsOf(entry.song.id) else null
            val cover = if (choice.covers) (entry.folderCover ?: entry.song.song.thumbnailUrl)?.let { covers.get(it) } else null
            writeAudio(documents, folder, base, audio, tagsOf(entry.song, lyrics?.first, cover))

            lyrics?.let { (text, ext) ->
                documents.write(folder, "$base.$ext") { it.write(text.toByteArray()) }
            }
            if (cover != null) {
                // an album folder shares one cover.jpg, a mixed folder gets a picture per song; a missing cover never stops the songs
                val name = if (entry.folderCover != null) "cover.jpg".takeIf { coveredFolders.add(entry.path) } else "$base.jpg"
                if (name != null) runCatching { documents.write(folder, name) { it.write(cover.jpeg) } }
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

    // the song's own audio, only youtube's webm opus moves into an .opus file since players skip .webm; opus and flac carry the song's tags
    private fun writeAudio(documents: DocumentTree, folder: String, base: String, audio: CachedAudio, tags: ExportTags) {
        if (audio.extension == "webm" && Build.VERSION.SDK_INT >= 29) {
            val temp = File.createTempFile("export", ".opus", context.cacheDir)
            try {
                if (OggOpusRemux.remux(audio.spans, temp)) {
                    documents.write(folder, "$base.opus") { out -> temp.inputStream().buffered(256 * 1024).use { ExportTagWriter.copyOpus(it, out, tags) } }
                    return
                }
            } finally {
                temp.delete()
            }
        }
        // lossless sources often send flac inside an mp4, which goes out as a real .flac instead of an .m4a
        if (audio.extension == "m4a") {
            Mp4FlacRemux.flacHeader(audio.spans)?.let { header ->
                val tagged = ExportTagWriter.retagFlacHeader(header, tags) ?: header
                documents.write(folder, "$base.flac") { out -> Mp4FlacRemux.write(audio.spans, tagged, out) }
                return
            }
        }
        documents.write(folder, "$base.${audio.extension}") { out ->
            when (audio.extension) {
                "opus" -> audio.open().use { ExportTagWriter.copyOpus(it, out, tags) }
                "flac" -> audio.open().use { ExportTagWriter.copyFlac(it, out, tags) }
                else -> audio.copyTo(out)
            }
        }
    }

    // a downloaded song's bytes, straight from the pieces in the download cache, and what kind of file they make
    private class CachedAudio(val spans: List<File>, val extension: String) {
        fun copyTo(out: OutputStream) = spans.forEach { span -> span.inputStream().use { it.copyTo(out, 256 * 1024) } }

        fun open() = SequenceInputStream(Collections.enumeration(spans.map { it.inputStream() })).buffered(256 * 1024)
    }

    // what goes into the file's own tags; ttml lyrics go in as lrc so other players can read them too
    private fun tagsOf(song: Song, lyrics: String?, cover: ExportCover?) = ExportTags(
        title = song.song.title,
        artist = song.artists.joinToString(", ") { it.name }.ifBlank { null },
        album = albumTitle(song),
        // an album sorts under its lead artist, so a guest on one song doesn't split it when it comes back in
        albumArtist = albumTitle(song)?.let { song.artists.firstOrNull()?.name },
        year = song.song.year ?: song.album?.year,
        lyrics = lyrics?.let { LyricsFileReader.parse(it.toByteArray()) ?: it },
        cover = cover,
    )

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
    private fun extensionOf(first: File): String {
        val head = ByteArray(64)
        val read = runCatching { first.inputStream().use { it.read(head) } }.getOrDefault(0)
        if (read < 4) return "m4a"
        fun at(offset: Int, text: String) = text.indices.all { head[offset + it] == text[it].code.toByte() }
        return when {
            at(0, "fLaC") -> "flac"
            // ogg holding opus is an .opus file, anything else in ogg stays .ogg
            at(0, "OggS") -> if (read >= 36 && (28..read - 8).any { at(it, "OpusHead") }) "opus" else "ogg"
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

    // an album's songs share one cover, so the last one loaded is kept instead of compressed again for every song
    private inner class CoverMemo {
        private var url: String? = null
        private var cover: ExportCover? = null

        suspend fun get(url: String): ExportCover? {
            if (url != this.url) {
                this.url = url
                cover = load(url)
            }
            return cover
        }

        private suspend fun load(url: String): ExportCover? {
            val request = ImageRequest.Builder(context)
                .data(url.resize(CoverPx, CoverPx))
                .allowHardware(false)
                .build()
            val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull() ?: return null
            val out = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) return null
            return ExportCover(out.toByteArray(), bitmap.width, bitmap.height)
        }
    }
}
