// VideoLyricsSync.kt

package com.mochyy.mochymusic.lyrics

import android.content.Context
import android.util.LruCache
import com.mochyy.mochymusic.constants.SyncLyricsToVideoKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.utils.OfflineMode
import com.mochyy.mochymusic.utils.SongVersions
import com.mochyy.mochymusic.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale

// lyrics are timed to the album audio, so when the track is a music video they get moved onto the video: by listening to the album version against it, or through its captions
object VideoLyricsSync {

    // lyrics already looked at this session, so replaying a video doesn't ask youtube again
    private val checked by lazy { LruCache<String, Boolean>(64) }

    // matchAudio finds where an album song sits inside the playing video, by listening to both
    suspend fun syncIfNeeded(
        context: Context,
        database: MusicDatabase,
        metadata: MediaMetadata,
        entity: LyricsEntity,
        matchAudio: suspend (albumSongId: String) -> SongVideoMap?,
    ) {
        if (!metadata.isVideoSong || metadata.id != entity.id || OfflineMode.isOn) return
        val raw = entity.lyrics
        if (raw.isBlank() || raw == LyricsEntity.LYRICS_NOT_FOUND || entity.provider in VideoTimedProviders) return
        if (context.dataStore.data.first()[SyncLyricsToVideoKey] == false) return
        val key = "${entity.id}|${raw.hashCode()}"
        if (checked.get(key) != null) return
        // a shift set by hand (or by auto offset) already says where these lyrics belong
        if ((database.song(entity.id).first()?.song?.lyricsOffset ?: 0) != 0) return

        val lines = withContext(Dispatchers.Default) { LyricsUtils.parseLyrics(raw) }
        if (lines.size < 4 || LyricsUtils.isUnsynced(lines)) return

        // captions line the video up first without downloading a second audio stream; listening only runs when captions have nothing
        val albumSongId = SongVersions.songVersionId(metadata.title, metadata.artists.joinToString { it.name })
        val map = captionMap(entity.id, lines)
            ?: albumSongId?.let { matchAudio(it) }
        checked.put(key, true)
        android.util.Log.i("VideoLyricsSync", "${entity.id} against ${albumSongId ?: "its captions"}: ${map ?: "no match"}")
        if (map == null || !map.isShifted) return

        val retimed = withContext(Dispatchers.Default) { retime(raw, lines, map) } ?: return
        checked.put("${entity.id}|${retimed.hashCode()}", true)
        // the lyrics may have been swapped for another source while this ran
        if (database.lyrics(entity.id).first()?.lyrics != raw) return
        database.query { upsert(entity.copy(lyrics = retimed)) }
    }

    private suspend fun captionMap(videoId: String, lines: List<LyricsEntry>): SongVideoMap? {
        val captions = VideoCaptionSource.captions(videoId) ?: return null
        return VideoCaptionSource.align(captions, lines)?.alignment?.map
    }

    // every timestamp moved onto the video, a line's word timings moving with it; null when something would land past what lrc can write
    internal fun retime(raw: String, lines: List<LyricsEntry>, map: SongVideoMap): String? {
        // where each line lands, never before the line ahead of it (a part the video cuts out folds into the next line)
        val moved = HashMap<Long, Long>()
        var previous = 0L
        for (time in lines.map { it.time }.distinct().sorted()) {
            previous = maxOf(map.videoMsAt(time), previous)
            moved[time] = previous
        }
        if (previous >= MaxLrcMs) return null

        var lineShift = 0L
        return raw.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                LineTag.containsMatchIn(trimmed) -> {
                    val tags = StringBuilder()
                    var rest = trimmed
                    var first = true
                    while (true) {
                        val match = LineTag.find(rest) ?: break
                        val time = timeOf(match)
                        val newTime = moved[time] ?: (time + map.deltaAt(time)).coerceAtLeast(0L)
                        if (first) lineShift = newTime - time
                        first = false
                        tags.append('[').append(formatTime(newTime)).append(']')
                        rest = rest.substring(match.range.last + 1).trimStart()
                    }
                    tags.toString() + InlineTag.replace(rest) { "<${formatTime((timeOf(it) + lineShift).coerceAtLeast(0L))}>" }
                }
                trimmed.startsWith("<") && trimmed.endsWith(">") && !InlineTag.containsMatchIn(trimmed) ->
                    shiftWordData(trimmed, lineShift)
                else -> line
            }
        }
    }

    // the word timings line under a line ("<word:start:end|...>", in seconds), moved by that line's shift
    private fun shiftWordData(line: String, shiftMs: Long): String {
        val shiftSeconds = shiftMs / 1000.0
        val words = line.removeSurrounding("<", ">").split("|").joinToString("|") { word ->
            val parts = word.split(":")
            val start = parts.getOrNull(1)?.toDoubleOrNull()
            val end = parts.getOrNull(2)?.toDoubleOrNull()
            if (parts.size != 3 || start == null || end == null) return@joinToString word
            "${parts[0]}:${seconds(start + shiftSeconds)}:${seconds(end + shiftSeconds)}"
        }
        return "<$words>"
    }

    private fun seconds(value: Double): String = String.format(Locale.ROOT, "%.3f", value.coerceAtLeast(0.0))

    // read the way the lyrics parser reads it, so the moved times are found again by exact value
    private fun timeOf(match: MatchResult): Long {
        val (minutes, seconds, fraction) = match.destructured
        val millis = if (fraction.length == 2) fraction.toLong() * 10 else fraction.toLong()
        return minutes.toLong() * 60_000 + seconds.toLong() * 1000 + millis
    }

    private fun formatTime(ms: Long): String =
        String.format(Locale.ROOT, "%02d:%02d.%03d", ms / 60_000, ms / 1000 % 60, ms % 1000)

    private val LineTag = Regex("""^\[(\d{1,2}):(\d{2})\.(\d{2,3})\]""")
    private val InlineTag = Regex("""<(\d{1,2}):(\d{2})\.(\d{2,3})>""")

    // lyrics that came from this very video, or that someone typed in by hand
    private val VideoTimedProviders = setOf(
        YouTubeCaptionsLyricsProvider.name,
        YouTubeSubtitleLyricsProvider.name,
        com.mochyy.mochymusic.viewmodels.LyricsMenuViewModel.USER_EDITED,
    )

    // lrc minutes are two digits
    private const val MaxLrcMs = 100 * 60_000L
}
