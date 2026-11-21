// what moves over a module's cover or behind its player: the song's youtube video, its animated cover, or a loop of the module's own (docs/wiki, covers that move)

package com.mochyy.mochymusic.ui.player.styles

import android.net.ConnectivityManager
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.CanvasThumbnailAnimationKey
import com.mochyy.mochymusic.constants.CanvasWifiOnlyKey
import com.mochyy.mochymusic.constants.DisableVideoAutoplayKey
import com.mochyy.mochymusic.constants.PlayVideoBackgroundKey
import com.mochyy.mochymusic.modules.skin.CoverMedia
import com.mochyy.mochymusic.modules.skin.ModuleSkin
import com.mochyy.mochymusic.ui.player.CanvasArtworkPlaybackCache
import com.mochyy.mochymusic.ui.player.CanvasArtworkPlayer
import com.mochyy.mochymusic.ui.player.SongVideo
import com.mochyy.mochymusic.ui.player.SongVideoLookup
import com.mochyy.mochymusic.ui.player.YouTubeVideoBackground
import com.mochyy.mochymusic.ui.player.findCanvasCached
import com.mochyy.mochymusic.ui.player.rememberSongVideoLookup
import com.mochyy.mochymusic.utils.rememberDataSaverActive
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

// skipping through songs shouldn't ask every animated cover source about each one passed on the way
private const val AnimatedLookupSettleMs = 400L

/** what a cover's moving part plays right now */
private sealed interface MediaPick {
    data class Video(val video: SongVideo) : MediaPick
    /** a video loop: the song's animated cover or the module's own mp4, webm or hls */
    data class Clip(val url: String) : MediaPick
    /** the module's own gif or webp */
    data class Picture(val model: Any) : MediaPick
}

/** an animated cover lookup so far: the link once found, and whether the sources are still being asked */
private class AnimatedLookup(val url: String?, val searching: Boolean)

/** the first of [media] the song has, over the picture; [rides] slides it with the song on a swipe and [turn] spins it with a record-like cover */
@Composable
internal fun SkinCoverMedia(
    media: List<CoverMedia>,
    context: SkinContext,
    fit: Boolean,
    rides: Boolean,
    modifier: Modifier,
    turn: (() -> Float)? = null,
) {
    if (media.isEmpty() || context.preview) return
    val showing = rememberStyleShowing()
    val obscured = LocalAppContentObscured.current
    // the closed player keeps its layout composed, so the decoders only exist while it can be seen
    val shown by remember(showing) { derivedStateOf { showing() && !obscured.value } }
    if (!shown) return
    val connection = LocalPlayerConnection.current ?: return
    val track by connection.uiState.trackInfo.collectAsState()
    val metadata by connection.mediaMetadata.collectAsState()
    val songMetadata = metadata?.takeIf { it.id == track.mediaId }
    val songIsVideo = songMetadata?.isVideoSong == true
    val dataSaving = rememberDataSaverActive()
    val kinds = rememberMediaKinds(media, songIsVideo, dataSaving)
    val video = rememberSongVideoLookup(track.mediaId, track.title, track.artist, isVideoSong = songIsVideo, enabled = CoverMedia.Video in kinds)
    val durationSec = songMetadata?.duration?.takeIf { it > 0 }
    val animated = rememberAnimatedCover(track.mediaId, track.title, track.artist, durationSec, enabled = CoverMedia.Animated in kinds)
    val skin = context.skin
    val pick = remember(kinds, video, animated, skin, dataSaving) { pickMedia(kinds, video, animated, skin, dataSaving) } ?: return
    val playing by connection.isPlaying.collectAsState()

    // a fitted video sits on black, so the still picture doesn't peek round its edges
    var ready by remember { mutableStateOf(false) }
    val backing = animateFloatAsState(if (fit && ready) 1f else 0f, tween(300), label = "coverMediaBacking")
    val onReady: (Boolean) -> Unit = { ready = it }
    val carousel = context.carousel.takeIf { rides }
    val mediaId = track.mediaId
    Box(
        modifier
            .then(
                if (carousel == null) Modifier else Modifier.graphicsLayer {
                    val offset = carousel.offsetOfSong(mediaId)
                    // the song leaves the row for a moment as it changes, and its video mustn't sit over the next one's picture
                    alpha = if (offset == null || abs(offset) >= 1f) 0f else 1f
                    translationX = (offset ?: 0f) * size.width
                },
            )
            .drawBehind { if (backing.value > 0f) drawRect(Color.Black.copy(alpha = backing.value)) },
    ) {
        val inner = Modifier
            .fillMaxSize()
            .then(if (turn != null) Modifier.graphicsLayer { rotationZ = turn() } else Modifier)
        when (pick) {
            is MediaPick.Video -> YouTubeVideoBackground(
                video = pick.video,
                isPlaying = playing,
                glassState = null,
                onShowingChange = onReady,
                fit = { if (fit) 1f else 0f },
                modifier = inner,
            )
            // a loop plays while it can be seen, like mochy's own animated covers, song paused or not
            is MediaPick.Clip -> CanvasArtworkPlayer(
                primaryUrl = pick.url,
                fallbackUrl = null,
                isPlaying = true,
                onReadyChange = onReady,
                fit = fit,
                modifier = inner,
            )
            is MediaPick.Picture -> MovingPictureLayer(pick.model, if (fit) ContentScale.Fit else ContentScale.Crop, null, inner)
        }
    }
}

/** a module's gif or webp moving over its still first frame, only while the player can be seen, so it never ticks under the mini player */
@Composable
internal fun MovingPictureOverlay(model: Any, context: SkinContext, scale: ContentScale, tint: ColorFilter?, modifier: Modifier) {
    if (context.preview) return
    val showing = rememberStyleShowing()
    val obscured = LocalAppContentObscured.current
    val shown by remember(showing) { derivedStateOf { showing() && !obscured.value } }
    if (shown) MovingPictureLayer(model, scale, tint, modifier)
}

@Composable
private fun MovingPictureLayer(model: Any, scale: ContentScale, tint: ColorFilter?, modifier: Modifier) {
    val platform = LocalContext.current
    val request = remember(model) { movingPictureRequest(platform, model) }
    AsyncImage(model = request, contentDescription = null, contentScale = scale, colorFilter = tint, modifier = modifier)
}

// the module's list with auto worked out from the person's own settings, and whatever they turned off or data saver holds back taken out
@Composable
private fun rememberMediaKinds(media: List<CoverMedia>, songIsVideo: Boolean, dataSaving: Boolean): List<CoverMedia> {
    val videoCovers by rememberPreference(PlayVideoBackgroundKey, defaultValue = false)
    val animatedCovers by rememberPreference(CanvasThumbnailAnimationKey, defaultValue = true)
    val videoAutoplayOff by rememberPreference(DisableVideoAutoplayKey, defaultValue = false)
    return remember(media, songIsVideo, dataSaving, videoCovers, animatedCovers, videoAutoplayOff) {
        media
            .flatMap { item ->
                if (item != CoverMedia.Auto) listOf(item)
                else listOfNotNull(CoverMedia.Video.takeIf { videoCovers || (songIsVideo && !videoAutoplayOff) }, CoverMedia.Animated)
            }
            .filter { item ->
                when (item) {
                    CoverMedia.Video -> !dataSaving
                    CoverMedia.Animated -> animatedCovers && !dataSaving
                    else -> true
                }
            }
            .distinct()
    }
}

// the song's animated cover, from the cache at once or from the sources in the order picked in settings
@Composable
private fun rememberAnimatedCover(mediaId: String, title: String, artist: String, durationSec: Int?, enabled: Boolean): AnimatedLookup {
    val context = LocalContext.current
    val wifiOnly by rememberPreference(CanvasWifiOnlyKey, defaultValue = true)
    var found by remember(mediaId, enabled) {
        val cached = if (enabled) CanvasArtworkPlaybackCache.get(mediaId)?.preferredAnimationUrl else null
        mutableStateOf(AnimatedLookup(cached, searching = enabled && cached == null))
    }
    LaunchedEffect(mediaId, title, artist, enabled, wifiOnly) {
        if (!enabled || found.url != null) return@LaunchedEffect
        val metered = context.getSystemService<ConnectivityManager>()?.isActiveNetworkMetered == true
        if (mediaId.isBlank() || title.isBlank() || artist.isBlank() || (wifiOnly && metered)) {
            found = AnimatedLookup(null, searching = false)
            return@LaunchedEffect
        }
        delay(AnimatedLookupSettleMs)
        val url = findCanvasCached(context, mediaId, title, artist, durationSec)?.preferredAnimationUrl
        found = AnimatedLookup(url, searching = false)
    }
    return found
}

// the first on the list the song has; while one higher up is still being looked for nothing shows, so a lower one never flashes in first
private fun pickMedia(kinds: List<CoverMedia>, video: SongVideoLookup, animated: AnimatedLookup, skin: ModuleSkin, dataSaving: Boolean): MediaPick? {
    for (kind in kinds) {
        when (kind) {
            CoverMedia.Video -> {
                video.video?.let { return MediaPick.Video(it) }
                if (video.searching) return null
            }
            CoverMedia.Animated -> {
                animated.url?.let { return MediaPick.Clip(it) }
                if (animated.searching) return null
            }
            is CoverMedia.Loop -> loopPick(kind.path, skin, dataSaving)?.let { return it }
            CoverMedia.Auto -> Unit
        }
    }
    return null
}

// a loop in the zip always plays; one from a link waits for data saver to be off
private fun loopPick(path: String, skin: ModuleSkin, dataSaving: Boolean): MediaPick? = when (val found = skin.resolve(path)) {
    is File -> if (isMovingPicture(found)) MediaPick.Picture(found) else MediaPick.Clip(Uri.fromFile(found).toString())
    is String -> when {
        dataSaving -> null
        isMovingPicture(found) -> MediaPick.Picture(found)
        else -> MediaPick.Clip(found)
    }
    else -> null
}
