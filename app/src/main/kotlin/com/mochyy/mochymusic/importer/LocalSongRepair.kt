// LocalSongRepair.kt

package com.mochyy.mochymusic.importer

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.db.entities.SongArtistMap
import com.mochyy.mochymusic.lyrics.EmbeddedLyrics
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

// fixes local songs imported before file names were read: a title that was the file's link and no artist, and with a folder's files the lyrics and covers beside them
object LocalSongRepair {
    // raised when an update needs every saved folder read again for the lyrics and covers beside songs already in
    private const val FolderPass = 1
    private val FolderPassKey = intPreferencesKey("localSongFolderPass")

    // when the library opens: link titles are fixed every time, and once per FolderPass the saved folders are read so songs already in get what sits beside them
    suspend fun repairOnOpen(context: Context, database: MusicDatabase): Int {
        val due = (context.dataStore.data.first()[FolderPassKey] ?: 0) < FolderPass
        val beside = if (due) withContext(Dispatchers.IO) { LocalFolders.scanSaved(context).files } else emptyList()
        val fixed = repair(context, database, beside)
        if (due) context.dataStore.edit { it[FolderPassKey] = FolderPass }
        return fixed
    }

    // returns how many songs got something back; beside is a folder scan, so songs from it can pick up their lyrics and covers
    suspend fun repair(context: Context, database: MusicDatabase, beside: List<LocalFile> = emptyList()): Int =
        withContext(Dispatchers.IO) {
            val songs = database.localSongsByRowIdAsc().first()
            val index = if (beside.isEmpty()) null else FolderIndex(context, beside, withMediaStore = songs.any { cameFromMediaStore(it) })
            var fixed = 0
            for (song in songs) {
                val localUri = song.song.localUri ?: continue
                val uri = localUri.toUri()
                val repaired = try {
                    repairOne(context, database, song, uri, index?.find(context, uri))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Couldn't repair ${song.id}")
                    false
                }
                if (repaired) fixed++
            }
            fixed
        }

    private suspend fun repairOne(context: Context, database: MusicDatabase, song: Song, uri: Uri, beside: LocalFile?): Boolean {
        // only read the file again when its title came from the link and its name can be read now
        val tags = if (hasLinkTitle(song.song.title, uri) && LocalFileNames.of(context, uri) != null) extractAudioMetadata(context, uri) else null
        val title = tags?.title?.takeUnless { hasLinkTitle(it, uri) }
        val artist = tags?.artist?.takeIf { name -> name != UnknownLocalArtist && song.artists.all { it.name == UnknownLocalArtist } }
        val cover = beside?.cover?.takeIf { song.song.thumbnailUrl == null }
            ?.let { LocalSidecars.readCover(context, it) }
            ?.let { LocalArtwork.save(context, it) }
        val lyrics = beside?.lyrics?.takeIf { hasNoLyrics(database, song.id) }?.let { LocalSidecars.readLyrics(context, it) }
        // a tag like "A; B" that came in as one artist before tags were split gets its artists apart
        val combined = song.artists.singleOrNull()?.takeIf { artist == null && it.isLocal }
        val artists = when {
            artist != null -> LocalArtistNames.split(artist, null) { database.artistByName(it) != null }
            combined != null -> LocalArtistNames.split(combined.name, null) { database.artistByName(it) != null }.takeIf { it.size > 1 }
            else -> null
        }
        if (title == null && artists == null && cover == null && lyrics == null) return false

        database.withTransaction {
            update(song.song.copy(title = title ?: song.song.title, thumbnailUrl = cover ?: song.song.thumbnailUrl))
            if (artists != null) {
                songArtistMap(song.id).forEach { delete(it) }
                artists.forEachIndexed { position, name ->
                    val artistId = artistByName(name)?.id
                        ?: ArtistEntity.generateArtistId().also { insert(ArtistEntity(id = it, name = name, isLocal = true)) }
                    insert(SongArtistMap(songId = song.id, artistId = artistId, position = position))
                }
                // the old one (a joined name, or Unknown Artist) only goes once no other song or album names it
                song.artists.filter { it.isLocal && artistUses(it.id) == 0 }.forEach { delete(it) }
            }
            if (lyrics != null) upsert(LyricsEntity(id = song.id, lyrics = lyrics, provider = EmbeddedLyrics.Provider))
        }
        return true
    }

    // the title the old import took from the link: a media id like "1234" or a folder path like "primary:Music/song"
    private fun hasLinkTitle(title: String, uri: Uri): Boolean {
        val segment = uri.lastPathSegment ?: return false
        if (title != segment.substringBeforeLast('.')) return false
        return title.all(Char::isDigit) || '/' in title || ':' in title
    }

    // songs from the phone-wide scan or picked from the audio list carry a media link, which a folder's files only match through MediaStore
    private fun cameFromMediaStore(song: Song): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val authority = song.song.localUri?.toUri()?.authority ?: return false
        return authority == MediaStore.AUTHORITY || authority == "com.android.providers.media.documents"
    }

    // finds a song's file in a folder scan however the song came in: through that folder, picked on its own, or from the phone-wide scan
    private class FolderIndex(context: Context, files: List<LocalFile>, private val withMediaStore: Boolean) {
        private val byKey = HashMap<String, LocalFile>()

        init {
            for (file in files) {
                byKey[file.uri.toString()] = file
                LocalFolders.documentKey(file.uri)?.let { byKey[it] = file }
                if (withMediaStore) LocalFolders.mediaStoreUri(context, file.uri)?.let { byKey[it.toString()] = file }
            }
        }

        fun find(context: Context, uri: Uri): LocalFile? =
            byKey[uri.toString()]
                ?: LocalFolders.documentKey(uri)?.let { byKey[it] }
                ?: uri.takeIf { withMediaStore && it.authority != MediaStore.AUTHORITY }
                    ?.let { LocalFolders.mediaStoreUri(context, it) }
                    ?.let { byKey[it.toString()] }
    }

    private suspend fun hasNoLyrics(database: MusicDatabase, songId: String): Boolean {
        val saved = database.lyrics(songId).first()?.lyrics
        return saved.isNullOrBlank() || saved == LyricsEntity.LYRICS_NOT_FOUND
    }
}
