// ShareSources.kt

package com.mochyy.mochymusic.ui.player.share.song

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.toBitmap
import com.mochyy.mochymusic.applecanvas.AppleMusicCanvasProvider
import com.mochyy.mochymusic.canvas.MonochromeApiCanvas
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.lyrics.LyricsHelper
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.playback.MusicService
import com.mochyy.mochymusic.ui.player.CanvasArtworkPlaybackCache
import com.mochyy.mochymusic.ui.player.lookUpVideo
import com.mochyy.mochymusic.ui.player.normalizeCanvasArtistName
import com.mochyy.mochymusic.ui.player.normalizeCanvasSongTitle
import com.mochyy.mochymusic.ui.player.share.coverPalette
import com.mochyy.mochymusic.ui.utils.SoftBlurTransformation
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.YTPlayerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// matching the song to its video by listening to both can take a while; the video still plays lined up as-is until then
private const val VideoMatchTimeoutMs = 20_000L

// the cover's colours, the cover itself sharp for the card and blurred for the clouds
internal suspend fun loadShareArt(context: Context, session: SongShareSession) {
    val url = session.song.thumbnailUrl
    session.palette = coverPalette(context, url)
    val cover = loadBitmap(context, url?.resize(1080, 1080), size = 1080)
    val coverBlur = loadBitmap(context, url?.resize(544, 544), size = 192, blur = SoftBlurTransformation())
    session.art = session.art.copy(cover = cover, coverBlur = coverBlur)
}

// your own picture, big enough for a 4k export, and a soft blurred copy
internal suspend fun loadPictureArt(context: Context, session: SongShareSession, uri: Uri) {
    if (session.art.pictureUri == uri && session.art.picture != null) return
    val sharp = loadBitmap(context, uri, size = 2160)
    val blurred = loadBitmap(context, uri, size = 220, blur = SoftBlurTransformation(width = 160, radius = 3))
    session.art = session.art.copy(pictureUri = uri, picture = sharp, pictureBlur = blurred)
}

private suspend fun loadBitmap(context: Context, data: Any?, size: Int, blur: SoftBlurTransformation? = null): ImageBitmap? {
    if (data == null) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            // kept in memory rather than on the gpu, so the software frames on old phones can draw it too
            val request = ImageRequest.Builder(context)
                .data(data)
                .size(size)
                .allowHardware(false)
                .apply { if (blur != null) transformations(blur) }
                .build()
            context.imageLoader.execute(request).image?.toBitmap()?.asImageBitmap()
        }.getOrNull()
    }
}

// the song's lyrics from the library, or fetched and kept there the same way the lyrics page does
internal suspend fun loadShareLyrics(session: SongShareSession, database: MusicDatabase, lyricsHelper: LyricsHelper?) {
    val id = session.song.id
    val stored = withContext(Dispatchers.IO) { database.lyrics(id).first() }
    val raw = stored?.lyrics ?: lyricsHelper?.let { helper ->
        val fetched = runCatching { helper.getLyrics(session.song) }.getOrNull()
        if (fetched != null && fetched.provider != LyricsHelper.NoConnectionProvider) {
            withContext(Dispatchers.IO) {
                database.query { upsert(LyricsEntity(id = id, lyrics = fetched.lyrics, provider = fetched.provider)) }
            }
        }
        fetched?.lyrics
    }
    loadLyricsOffset(session, database)
    if (raw.isNullOrBlank() || raw == LyricsEntity.LYRICS_NOT_FOUND) {
        session.lyrics = Fetched.Missing
        return
    }
    val lines = withContext(Dispatchers.Default) { runCatching { LyricsUtils.parseLyricsCached(raw) }.getOrNull() }
        ?.filter { it.text.isNotBlank() }
    session.lyrics = if (lines.isNullOrEmpty()) Fetched.Missing else Fetched.Found(lines)
}

// how far this song's lyrics were nudged on the lyrics page, so the share lines them up the same way
internal suspend fun loadLyricsOffset(session: SongShareSession, database: MusicDatabase) {
    session.lyricsOffsetMs = withContext(Dispatchers.IO) { database.song(session.song.id).first()?.song?.lyricsOffset?.toLong() ?: 0L }
}

// the album's looping cover from the same places the player finds it
internal suspend fun loadAnimatedCover(session: SongShareSession) {
    val song = session.song
    CanvasArtworkPlaybackCache.get(song.id)?.preferredAnimationUrl?.takeIf { it.isNotBlank() }?.let {
        session.animatedCover = Fetched.Found(it)
        return
    }
    val title = normalizeCanvasSongTitle(song.title)
    val artist = normalizeCanvasArtistName(session.share.artist)
    if (title.length < 2 || artist.length < 2) {
        session.animatedCover = Fetched.Missing
        return
    }
    val duration = song.duration.takeIf { it > 0 }
    val found = withContext(Dispatchers.IO) {
        runCatching {
            MonochromeApiCanvas.getBySongArtist(title, artist, null, duration)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                ?: AppleMusicCanvasProvider.getBySongArtist(title, artist, null, "us", duration)
                    ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
        }.getOrNull()
    }
    if (found != null) CanvasArtworkPlaybackCache.put(song.id, found)
    session.animatedCover = found?.preferredAnimationUrl?.let { Fetched.Found(it) } ?: Fetched.Missing
}

// the song's music video, its own picture when the song is a video; lined up as-is until matchMusicVideo has listened to both
internal suspend fun findMusicVideo(session: SongShareSession) {
    val song = session.song
    val found = runCatching { lookUpVideo(song.id, song.title, session.share.artist, song.isVideoSong) }.getOrNull()
    session.musicVideo = if (found == null) {
        Fetched.Missing
    } else {
        Fetched.Found(ShareMusicVideo(found.videoId, found.streams) { it })
    }
}

// lines the music video up with the song by listening to both; heavy, so it only runs once the music video is picked
internal suspend fun matchMusicVideo(session: SongShareSession, service: MusicService?) {
    val found = snapshotFlow { session.musicVideo }.first { it !is Fetched.Looking }.found ?: return
    if (found.videoId == session.song.id || service == null) return
    val map = withTimeoutOrNull(VideoMatchTimeoutMs) { runCatching { service.matchSongToVideo(session.song.id, found.videoId) }.getOrNull() }
        ?: return
    session.musicVideo = Fetched.Found(ShareMusicVideo(found.videoId, found.streams) { songMs -> map.videoMsAt(songMs) })
}

// links run out after a few hours; an export fetches fresh ones first if they have
internal suspend fun freshMusicVideo(video: ShareMusicVideo): ShareMusicVideo {
    if (!video.streams.isExpired) return video
    val streams = YTPlayerUtils.resolveVideoStreams(video.videoId).getOrNull() ?: return video
    return ShareMusicVideo(video.videoId, streams, video.videoMsAt)
}
