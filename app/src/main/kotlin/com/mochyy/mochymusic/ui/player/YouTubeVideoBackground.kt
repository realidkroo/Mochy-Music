// YouTubeVideoBackground.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.player.video.DriveYouTubeVideo
import com.mochyy.mochymusic.ui.player.video.VideoLoadingIndicator
import com.mochyy.mochymusic.ui.player.video.YouTubeVideoSurface
import com.mochyy.mochymusic.ui.player.video.rememberYouTubeVideoSession
import com.mochyy.mochymusic.ui.utils.rememberSettled

/** how long a song has to stay before its video starts */
private const val VideoSettleMs = 700L

// behind lyrics and under a blur, auto never needs more than this
private const val BackgroundAutoCap = 720

@Composable
fun YouTubeVideoBackground(
    video: SongVideo?,
    isPlaying: Boolean,
    // null when nothing mirrors the video, so its picture isn't recorded for nobody
    glassState: GlassState?,
    modifier: Modifier = Modifier,
    // the flagship's moving backdrop following the video's colours
    onFrameColors: ((List<androidx.compose.ui.graphics.Color>) -> Unit)? = null,
    // told when the video's picture is up over the cover, and when it's gone again
    onShowingChange: ((Boolean) -> Unit)? = null,
    // 0 zooms the video to fill, 1 shows all of it with room round it
    fit: () -> Float = { 0f },
) {
    if (video == null) return
    // a decoder for every song skipped past ran memory past a gigabyte and got the app killed, so the video waits for the song to stay
    val settledId = rememberSettled(video.videoId, VideoSettleMs) ?: return
    if (settledId != video.videoId) return
    val session = rememberYouTubeVideoSession(settledId)
    val currentOnShowing by rememberUpdatedState(onShowingChange)
    androidx.compose.runtime.LaunchedEffect(session) {
        androidx.compose.runtime.snapshotFlow { session.hasFrame }.collect { currentOnShowing?.invoke(it) }
    }
    androidx.compose.runtime.DisposableEffect(session) { onDispose { currentOnShowing?.invoke(false) } }
    val playing by rememberUpdatedState(isPlaying)
    DriveYouTubeVideo(session, video, isPlaying, autoCap = BackgroundAutoCap)
    Box(modifier = modifier) {
        YouTubeVideoSurface(
            session = session,
            isPlaying = isPlaying,
            glassState = glassState,
            glassActive = { playing },
            onFrameColors = onFrameColors,
            fit = fit,
            modifier = Modifier.fillMaxSize(),
        )
        // the cover shows until the first picture, so only a picture that froze mid-song gets a loader
        VideoLoadingIndicator(
            loading = session.hasFrame && session.isStalled,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
