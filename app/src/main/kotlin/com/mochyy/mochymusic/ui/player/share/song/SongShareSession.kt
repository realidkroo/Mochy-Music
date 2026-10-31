// SongShareSession.kt

package com.mochyy.mochymusic.ui.player.share.song

import android.net.Uri
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import com.mochyy.mochymusic.constants.LyricsMotionStyle
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.ui.player.share.LyricsShareSession
import com.mochyy.mochymusic.ui.player.share.ShareBackground
import com.mochyy.mochymusic.ui.player.share.ShareSong
import com.mochyy.mochymusic.ui.player.share.ShareStyle
import com.mochyy.mochymusic.ui.player.share.contentColorOn
import com.mochyy.mochymusic.utils.VideoStreams

// how much of the song a share plays: 10 seconds to start with, anything from 2 to 40 with the timeline's arrows
internal const val ShareWindowMs = 10_000L
internal const val ShortestWindowMs = 2_000L
internal const val LongestWindowMs = 40_000L

// a lyrics video starts a breath before the first picked word and ends a breath after the last
private const val LeadInMs = 400L
private const val LeadOutMs = 600L

// how far the background can be zoomed in by pinching
internal const val ShareMaxZoom = 4f

internal enum class SongShareKind { MusicOnly, Lyrics }

internal sealed interface ShareVideoSource {
    // a clip from the phone: where the picked part starts, how long it runs, and how long the whole video is
    data class Own(val uri: Uri, val startMs: Long, val lengthMs: Long, val durationMs: Long) : ShareVideoSource

    // the song's music video, lined up with the song
    data object MusicVideo : ShareVideoSource

    // the album's looping animated cover
    data object AnimatedCover : ShareVideoSource
}

internal sealed interface SongBackground {
    data class Still(val background: ShareBackground) : SongBackground

    // dim fades the video toward black so the card stays readable on a bright one
    data class Video(val source: ShareVideoSource, val dim: Float = 0.85f) : SongBackground
}

// something fetched while the share is open: still looking, nothing found, or here it is
internal sealed interface Fetched<out T> {
    data object Looking : Fetched<Nothing>

    data object Missing : Fetched<Nothing>

    data class Found<T>(val value: T) : Fetched<T>
}

internal val <T> Fetched<T>.found: T? get() = (this as? Fetched.Found<T>)?.value

// the song's music video and where on it each moment of the song is
@Immutable
internal class ShareMusicVideo(val videoId: String, val streams: VideoStreams, val videoMsAt: (Long) -> Long)

// pictures drawn from memory, so an export frame never waits on an image load
@Immutable
internal data class ShareArt(
    val cover: ImageBitmap? = null,
    val coverBlur: ImageBitmap? = null,
    // your own picture, sharp and blurred, and which picture they are
    val pictureUri: Uri? = null,
    val picture: ImageBitmap? = null,
    val pictureBlur: ImageBitmap? = null,
)

// how the lyrics page is set up, read once so an export never waits for the settings to load
@Immutable
internal data class ShareLyricsLook(
    val motionStyle: LyricsMotionStyle = LyricsMotionStyle.MOTION,
    val highBloom: Boolean = true,
    val lineBlur: Boolean = true,
)

@Stable
internal class SongShareSession(val song: MediaMetadata) {
    val share = ShareSong(song.title, song.artists.joinToString { it.name }, song.thumbnailUrl)

    // youtube music by default; a song that only lives on this phone has no link to give
    val link: String? = if (song.localUri != null || song.id.startsWith("LOCAL_")) null else "https://music.youtube.com/watch?v=${song.id}"

    val durationMs: Long = song.duration.coerceAtLeast(0) * 1000L

    var kind by mutableStateOf(SongShareKind.MusicOnly)

    // the lyrics half: which lines are picked, card or cinematic, and how each word looks; null for music only
    var pick by mutableStateOf<LyricsShareSession?>(null)

    // the part of the song a video plays
    var windowStartMs by mutableLongStateOf(0L)
        private set
    var windowLengthMs by mutableLongStateOf(ShareWindowMs)
        private set
    val windowEndMs: Long get() = windowStartMs + windowLengthMs

    // set once the timeline has been moved by hand, so a late lyrics load doesn't pull the window somewhere else
    var windowTouched = false

    // a page with the picture on it is up and wants the song playing under it
    var previewWanted by mutableStateOf(false)

    // mochy is the app in front; leaving for instagram or anywhere else stops the song
    var onScreen by mutableStateOf(true)

    var background by mutableStateOf<SongBackground>(SongBackground.Still(ShareBackground.FromMusic))

    // the last still and the last video background, so switching between the two kinds finds each as you left it
    var lastStill by mutableStateOf<ShareBackground>(ShareBackground.FromMusic)
    var lastVideo by mutableStateOf<SongBackground.Video?>(null)

    // the last video picked from the phone, kept while you swipe to other backgrounds
    var lastOwnVideo by mutableStateOf<ShareVideoSource.Own?>(null)

    // a still background makes a picture; this makes it a video with the song playing instead
    var shareAsVideo by mutableStateOf(false)

    val hasVideoBackground: Boolean get() = background is SongBackground.Video
    val isVideo: Boolean get() = hasVideoBackground || shareAsVideo

    // only a video share has a song to hear; a picture stays quiet and leaves the app's own music alone
    val previewPlays: Boolean get() = isVideo && previewWanted && onScreen

    // the background moved and zoomed by hand: zoom from 1, offsets as fractions of the picture
    var bgZoom by mutableFloatStateOf(1f)
    var bgOffsetX by mutableFloatStateOf(0f)
    var bgOffsetY by mutableFloatStateOf(0f)

    var palette by mutableStateOf<List<Color>>(emptyList())

    // null keeps the cover's own colour
    var cardColor by mutableStateOf<Color?>(null)
    var cardOpacity by mutableFloatStateOf(0.78f)
    var cardBlur by mutableFloatStateOf(0.6f)

    // where the card sits on the picture, as fractions of it, once a picture has been laid out
    var cardBounds by mutableStateOf<Rect?>(null)

    var lyrics by mutableStateOf<Fetched<List<LyricsEntry>>>(Fetched.Looking)
    var lyricsOffsetMs by mutableLongStateOf(0L)
    var lyricsLook by mutableStateOf(ShareLyricsLook())
    var musicVideo by mutableStateOf<Fetched<ShareMusicVideo>>(Fetched.Looking)
    var animatedCover by mutableStateOf<Fetched<String>>(Fetched.Looking)
    var art by mutableStateOf(ShareArt())

    // the song playing under the pages; null until the host has made it
    var preview: SongPreviewPlayer? = null

    // an invisible spot in the share's window where export frames are drawn
    var offscreenHost: ViewGroup? = null

    // the song position the pictures draw at, moved on every frame while a video picture is up
    val playhead = mutableLongStateOf(0L)
    val playheadMs: () -> Long = { playhead.longValue }

    // the lyrics have line timing, so they can flow with the song
    val lyricsSynced: Boolean
        get() = lyrics.found?.let { lines -> lines.size > 1 && lines.any { it.time > 0L } } == true

    val cinematic: Boolean get() = kind == SongShareKind.Lyrics && pick?.style == ShareStyle.Cinematic

    // music only and the lyrics card have a card; cinematic words sit straight on the background
    val hasCard: Boolean get() = !cinematic

    val cardTint: Color get() = cardColor ?: palette.firstOrNull() ?: Color(0xFF3A3A3C)

    // dark words on a light card, white everywhere else
    val cardInk: Color
        get() = if (cardOpacity > 0.5f && cardTint.luminance() > 0.55f) Color(0xFF111111) else Color.White

    // the cinematic words: dark on a light plain colour, white on everything else
    val wordsInk: Color
        get() = when (val current = background) {
            is SongBackground.Still -> contentColorOn(current.background)
            is SongBackground.Video -> Color.White
        }

    // the part of the song, kept inside the song and between 2 and 40 seconds long
    fun setWindow(startMs: Long, lengthMs: Long) {
        val longest = if (durationMs > 0L) minOf(LongestWindowMs, durationMs) else LongestWindowMs
        val length = lengthMs.coerceIn(minOf(ShortestWindowMs, longest), longest)
        val lastStart = if (durationMs > 0L) (durationMs - length).coerceAtLeast(0L) else Long.MAX_VALUE
        windowLengthMs = length
        windowStartMs = startMs.coerceIn(0L, lastStart)
        if (playhead.longValue !in windowStartMs..windowEndMs) playhead.longValue = windowStartMs
    }

    fun startAt(positionMs: Long) = setWindow(positionMs, windowLengthMs)

    // the picked lines' stretch of the song, so a lyrics video plays exactly them
    fun fitWindowToPick() {
        val pick = pick ?: return
        val range = pick.range ?: return
        val first = pick.entries.getOrNull(range.first) ?: return
        val last = pick.entries.getOrNull(range.last) ?: return
        val end = last.words?.lastOrNull()?.let { (it.endTime * 1000).toLong() }?.takeIf { it > last.time }
            ?: pick.entries.getOrNull(range.last + 1)?.time?.takeIf { it > last.time }
            ?: (last.time + 4_000L)
        // the lyrics run ahead of the song by the lyrics offset
        setWindow(first.time - lyricsOffsetMs - LeadInMs, end - first.time + LeadInMs + LeadOutMs)
    }

    // pan and zoom kept so the background always covers the whole picture
    fun moveBackground(zoomBy: Float, dx: Float, dy: Float) {
        val zoom = (bgZoom * zoomBy).coerceIn(1f, ShareMaxZoom)
        val reach = (zoom - 1f) / 2f
        bgZoom = zoom
        bgOffsetX = (bgOffsetX + dx).coerceIn(-reach, reach)
        bgOffsetY = (bgOffsetY + dy).coerceIn(-reach, reach)
    }

    fun setStill(background: ShareBackground) {
        lastStill = background
        this.background = SongBackground.Still(background)
    }

    fun setVideo(video: SongBackground.Video) {
        lastVideo = video
        background = video
    }

    // the song position for this frame: the preview's, or a loop of the window while there's no sound yet
    private var freeRunStart = Long.MIN_VALUE

    fun previewPositionMs(): Long {
        val now = AnimationUtils.currentAnimationTimeMillis()
        preview?.positionMs(now)?.let { return it }
        if (freeRunStart == Long.MIN_VALUE) freeRunStart = now
        return windowStartMs + (now - freeRunStart).mod(windowLengthMs)
    }
}
