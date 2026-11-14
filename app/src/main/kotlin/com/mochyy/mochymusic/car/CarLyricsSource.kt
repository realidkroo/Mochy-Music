// CarLyricsSource.kt

package com.mochyy.mochymusic.car

import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsHelper
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// one song's lyrics as the car uses them; plain lyrics have no timing, so they're only read, never followed
data class CarLyricLines(
    val mediaId: String,
    val lines: List<LyricsEntry>,
    val offsetMs: Int,
    val synced: Boolean,
) {
    // the line being sung at this place in the song, or -1 before the first line, after the last, or for plain lyrics
    fun lineAt(positionMs: Long): Int {
        if (!synced) return -1
        val index = LyricsUtils.findCurrentLineIndex(lines, positionMs + offsetMs)
        return if (index in lines.indices) index else -1
    }

    // the next line with words in it, so a gap marker never counts as "what's sung next"
    fun nextSungAfter(index: Int): LyricsEntry? = lines.drop(index + 1).firstOrNull { it.text.isNotBlank() }

    // a long instrumental stretch after a line, when the car goes back to the song's own title and cover until singing starts again
    fun isBreak(index: Int, positionMs: Long): Boolean {
        val position = positionMs + offsetMs
        val next = nextSungAfter(index)
        return position > LyricsUtils.lineEndMs(lines, index) + BreakAfterLineMs &&
            (next == null || next.time - position > BreakBeforeLineMs)
    }

    private companion object {
        const val BreakAfterLineMs = 1_500L
        const val BreakBeforeLineMs = 2_500L
    }
}

// finds a song's lyrics for the car and fetches them the way the player does when none are saved yet, since the car may be the first to ask
class CarLyricsSource(
    private val database: MusicDatabase,
    private val lyricsHelper: LyricsHelper,
) {
    suspend fun load(metadata: MediaMetadata): CarLyricLines? {
        val raw = withContext(Dispatchers.IO) { database.lyrics(metadata.id).first()?.lyrics } ?: fetch(metadata) ?: return null
        if (raw.isBlank() || raw == LyricsEntity.LYRICS_NOT_FOUND) return null
        val offset = withContext(Dispatchers.IO) { database.song(metadata.id).first()?.song?.lyricsOffset ?: 0 }
        return withContext(Dispatchers.Default) {
            // background vocals sit under the main line on the phone, the car has room for one line only
            val timed = LyricsUtils.parseLyricsCached(raw).filterNot { it.isBackground }
            when {
                timed.any { it.text.isNotBlank() } && !LyricsUtils.isUnsynced(timed) -> CarLyricLines(metadata.id, timed, offset, synced = true)
                else -> LyricsUtils.parsePlainLyrics(raw).takeIf { it.isNotEmpty() }?.let { CarLyricLines(metadata.id, it, offset, synced = false) }
            }
        }
    }

    // offline finds nothing because nothing was asked, so that isn't saved as "no lyrics" and the next try asks again; the floating lyrics fetch through here too
    suspend fun fetch(metadata: MediaMetadata): String? {
        val found = runCatching { lyricsHelper.getLyrics(metadata) }.getOrNull() ?: return null
        if (found.provider == LyricsHelper.NoConnectionProvider) return null
        database.query { upsert(LyricsEntity(id = metadata.id, lyrics = found.lyrics, provider = found.provider)) }
        return found.lyrics
    }
}
