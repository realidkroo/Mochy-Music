// YouTubeVideoLookup.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.YtVideoBackgroundLyricsSyncKey
import com.mochyy.mochymusic.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.mochyy.mochymusic.songedit.SongEdits
import com.mochyy.mochymusic.utils.VideoStreams
import com.mochyy.mochymusic.utils.YTPlayerUtils
import com.mochyy.mochymusic.utils.rememberPreference

data class OfficialMusicVideo(
    val videoId: String,
    val streams: VideoStreams,
)

// a song's video and where on it the song is right now; positionAt is null when the two simply run side by side
@Immutable
class SongVideo(
    val videoId: String,
    val streams: VideoStreams,
    val positionAt: ((songPositionMs: Long) -> Long)?,
)

object YouTubeVideoUrlCache {
    private const val maxSize = 64
    private val map = LinkedHashMap<String, OfficialMusicVideo?>(maxSize, 0.75f, true)

    // a miss stays a miss, but a found video whose links ran out has to be looked up again
    @Synchronized
    fun contains(mediaId: String): Boolean =
        map.containsKey(mediaId) && map[mediaId]?.streams?.isExpired != true

    @Synchronized
    fun get(mediaId: String): OfficialMusicVideo? = map[mediaId]

    @Synchronized
    fun put(mediaId: String, video: OfficialMusicVideo?) {
        if (mediaId.isBlank()) return
        map[mediaId] = video
        while (map.size > maxSize) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            } else {
                break
            }
        }
    }
}

suspend fun findOfficialMusicVideo(title: String, artist: String): OfficialMusicVideo? {
    val videoId = findOfficialMusicVideoId(title, artist) ?: return null
    val streams = YTPlayerUtils.resolveVideoStreams(videoId).getOrNull() ?: return null
    return OfficialMusicVideo(videoId = videoId, streams = streams)
}

// a track that is a video itself plays its own stream, which lines up with the audio exactly
suspend fun ownMusicVideo(mediaId: String): OfficialMusicVideo? =
    YTPlayerUtils.resolveVideoStreams(mediaId).getOrNull()?.let { OfficialMusicVideo(mediaId, it) }

// just the official video's id, for when its captions are wanted and not its picture; misses are kept too, so a song is searched once
suspend fun findOfficialMusicVideoId(title: String, artist: String): String? {
    if (title.isBlank() || artist.isBlank()) return null
    val key = "$title|$artist"
    if (OfficialVideoIdCache.contains(key)) return OfficialVideoIdCache.get(key)

    val results = YouTube.search(query = "$title $artist", filter = YouTube.SearchFilter.FILTER_VIDEO)
        .getOrElse { return null }.items

    val normalizedTitle = normalizeCanvasSongTitle(title)
    val normalizedArtist = normalizeCanvasArtistName(artist)

    val match = results
        .filterIsInstance<SongItem>()
        .filter { it.musicVideoType == MUSIC_VIDEO_TYPE_OMV }
        .firstOrNull { candidate ->
            val candidateTitle = normalizeCanvasSongTitle(candidate.title)
            val candidateArtist = normalizeCanvasArtistName(candidate.artists.joinToString { it.name })
            (candidateTitle.contains(normalizedTitle, ignoreCase = true) || normalizedTitle.contains(candidateTitle, ignoreCase = true)) &&
                (candidateArtist.contains(normalizedArtist, ignoreCase = true) || normalizedArtist.contains(candidateArtist, ignoreCase = true))
        }
    OfficialVideoIdCache.put(key, match?.id)
    return match?.id
}

private object OfficialVideoIdCache {
    private const val maxSize = 64
    private val map = LinkedHashMap<String, String?>(maxSize, 0.75f, true)

    @Synchronized
    fun contains(key: String): Boolean = map.containsKey(key)

    @Synchronized
    fun get(key: String): String? = map[key]

    @Synchronized
    fun put(key: String, videoId: String?) {
        map[key] = videoId
        if (map.size > maxSize) map.remove(map.keys.first())
    }
}

// a song's video once found, and whether it's still being looked for
@Immutable
class SongVideoLookup(val video: SongVideo?, val searching: Boolean)

// finds the video for a song (its own when it is a video, the official one otherwise) and lines the two up; null while looking or when there is none
@Composable
fun rememberSongVideo(
    mediaId: String,
    title: String,
    artist: String,
    isVideoSong: Boolean,
    enabled: Boolean,
): SongVideo? = rememberSongVideoLookup(mediaId, title, artist, isVideoSong, enabled).video

@Composable
fun rememberSongVideoLookup(
    mediaId: String,
    title: String,
    artist: String,
    isVideoSong: Boolean,
    enabled: Boolean,
): SongVideoLookup {
    // a video picked in "select video" (or none) changes what's looked up, so it keys everything below
    val edits by SongEdits.all.collectAsState()
    val pick = edits[mediaId]?.let { if (it.noVideo) NoVideoPick else it.videoId }
    // the first song takes its video straight from the cache, so the cover's video turning into the full video mode never gaps; later songs settle first
    val firstId = remember { mediaId }
    var found by remember(mediaId, pick) {
        mutableStateOf(if (enabled && mediaId == firstId) cachedVideo(mediaId, isVideoSong) else null)
    }
    var searching by remember(mediaId, pick) { mutableStateOf(enabled && found == null) }
    LaunchedEffect(mediaId, title, artist, enabled, isVideoSong, pick) {
        if (!enabled || mediaId.isBlank()) {
            found = null
            searching = false
            return@LaunchedEffect
        }
        if (found != null) {
            searching = false
            return@LaunchedEffect
        }
        searching = true
        // skipping through songs shouldn't look up, and then start a decoder for, every song passed on the way
        delay(LookupSettleMs)
        found = lookUpVideo(mediaId, title, artist, isVideoSong)
        searching = false
    }
    val video = found
    val positionAt = if (video != null) rememberVideoTimeline(mediaId, video.videoId) else null
    val songVideo = remember(video, positionAt) { video?.let { SongVideo(it.videoId, it.streams, positionAt) } }
    return remember(songVideo, searching) { SongVideoLookup(songVideo, searching) }
}

// how long a song has to stay before its video is looked up
private const val LookupSettleMs = 500L

private const val NoVideoPick = "none"

private fun videoCacheKey(mediaId: String, isVideoSong: Boolean): String {
    val picked = SongEdits.of(mediaId)?.videoId
    return when {
        picked != null -> "pick:$picked"
        isVideoSong -> "self:$mediaId"
        else -> mediaId
    }
}

private fun cachedVideo(mediaId: String, isVideoSong: Boolean): OfficialMusicVideo? {
    if (SongEdits.of(mediaId)?.noVideo == true) return null
    val key = videoCacheKey(mediaId, isVideoSong)
    return if (YouTubeVideoUrlCache.contains(key)) YouTubeVideoUrlCache.get(key) else null
}

// what the menus offer for a song's video: "wrong video?" once one was found, "find video" when the lookup came back empty or it was turned off
fun hasFoundVideo(mediaId: String, isVideoSong: Boolean): Boolean = cachedVideo(mediaId, isVideoSong) != null

// one lookup at a time: the mini player and the full video mode both ask for the playing song, and the second finds the first's answer cached
private val lookupLock = Mutex()

internal suspend fun lookUpVideo(mediaId: String, title: String, artist: String, isVideoSong: Boolean): OfficialMusicVideo? =
    lookupLock.withLock {
        val edit = SongEdits.of(mediaId)
        if (edit?.noVideo == true) return@withLock null
        val key = videoCacheKey(mediaId, isVideoSong)
        if (YouTubeVideoUrlCache.contains(key)) return@withLock YouTubeVideoUrlCache.get(key)
        val picked = edit?.videoId
        if (picked == null && !isVideoSong && (title.isBlank() || artist.isBlank())) return@withLock null
        val resolved = when {
            picked != null -> ownMusicVideo(picked)
            isVideoSong -> ownMusicVideo(mediaId)
            else -> findOfficialMusicVideo(title, artist)
        }
        YouTubeVideoUrlCache.put(key, resolved)
        resolved
    }

// where a separately found video is for each moment of the song: a first guess from its captions, then the exact answer from listening to both
@Composable
private fun rememberVideoTimeline(mediaId: String, videoId: String): ((Long) -> Long)? {
    val playerConnection = LocalPlayerConnection.current ?: return null
    // the track's own video is already in step with it
    if (videoId == mediaId) return null
    val lyricsSyncEnabled by rememberPreference(YtVideoBackgroundLyricsSyncKey, defaultValue = true)
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)

    var lyricTimeline by remember(mediaId) { mutableStateOf<LyricVideoTimeline?>(null) }
    LaunchedEffect(videoId, currentLyrics?.lyrics, lyricsSyncEnabled) {
        val lyricsRaw = currentLyrics?.lyrics?.takeIf { it.isNotBlank() && it != LYRICS_NOT_FOUND }
        if (!lyricsSyncEnabled || lyricsRaw == null) {
            lyricTimeline = null
            return@LaunchedEffect
        }
        val key = "$mediaId|$videoId|${lyricsRaw.hashCode()}"
        if (LyricVideoTimelineCache.contains(key)) {
            lyricTimeline = LyricVideoTimelineCache.get(key)
            return@LaunchedEffect
        }
        val timeline = try {
            buildLyricVideoTimeline(videoId, lyricsRaw)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        LyricVideoTimelineCache.put(key, timeline)
        lyricTimeline = timeline
    }

    var audioMap by remember(mediaId) { mutableStateOf<com.mochyy.mochymusic.lyrics.SongVideoMap?>(null) }
    LaunchedEffect(mediaId, videoId, lyricsSyncEnabled) {
        audioMap = if (lyricsSyncEnabled) playerConnection.service.matchSongToVideo(mediaId, videoId) else null
    }

    return remember(lyricTimeline, audioMap) {
        audioMap?.let { map -> { songMs: Long -> map.videoMsAt(songMs) } }
            ?: lyricTimeline?.let { timeline ->
                // the lyrics' own offset counts too: it's what puts the lyrics, and so the video, on the song
                { songMs: Long -> timeline.videoMsAt(songMs + playerConnection.lyricsOffsetMs.value) }
            }
    }
}
