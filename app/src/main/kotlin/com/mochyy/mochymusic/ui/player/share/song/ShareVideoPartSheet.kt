// ShareVideoPartSheet.kt

package com.mochyy.mochymusic.ui.player.share.song

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.player.share.ShareMuted
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// how many little frames line the strip
private const val StripFrames = 24

// the most of your own video a share uses; under a longer song it loops
internal const val OwnVideoLongestMs = 10_000L

// "which part?": your video playing on its own card, and its frames sliding under a window of up to ten seconds
internal fun PopupSheetState.showVideoPart(session: SongShareSession) {
    val popup = this
    show(buttonBar = { SheetPillButton(text = stringResource(R.string.song_share_ok), onClick = popup::dismiss) }) {
        VideoPartContent(session)
    }
}

@Composable
private fun VideoPartContent(session: SongShareSession) {
    val context = LocalContext.current
    val own = session.lastOwnVideo ?: return
    val frames by produceState(emptyList<ImageBitmap>(), own.uri) { value = videoFrames(context, own.uri, own.durationMs) }
    val height = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .height(height)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF2C2C2E)),
        ) {
            ShareVideoLayer(session, playing = session.onScreen, source = own)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.song_share_bg_which_part_title),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White,
        )
        Spacer(Modifier.height(16.dp))
        ShareTimeline(
            totalMs = own.durationMs,
            startMs = own.startMs,
            lengthMs = own.lengthMs,
            longestMs = OwnVideoLongestMs,
            onSettled = { start, length ->
                val moved = own.copy(
                    startMs = start.coerceIn(0L, (own.durationMs - length).coerceAtLeast(0L)),
                    lengthMs = length.coerceIn(1L, minOf(OwnVideoLongestMs, own.durationMs)),
                )
                session.lastOwnVideo = moved
                val dim = (session.background as? SongBackground.Video)?.dim ?: 0.85f
                session.setVideo(SongBackground.Video(moved, dim))
            },
            drawStrip = { timeToX -> drawVideoFrames(frames, own.durationMs, timeToX) },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.song_share_bg_max_seconds),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = ShareMuted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

// each little frame fills its share of the strip, cropped to the strip's height
private fun DrawScope.drawVideoFrames(frames: List<ImageBitmap>, durationMs: Long, timeToX: (Long) -> Float) {
    if (frames.isEmpty()) return
    val stripHeight = 44.dp.toPx()
    val top = (size.height - stripHeight) / 2f
    val step = durationMs / frames.size
    clipRect(top = top, bottom = top + stripHeight) {
        frames.forEachIndexed { i, frame ->
            val left = timeToX(i * step)
            val right = timeToX((i + 1) * step)
            if (right < 0f || left > size.width) return@forEachIndexed
            val width = (right - left).coerceAtLeast(1f)
            // the middle slice of the frame, as wide as its spot on the strip allows
            val sliceWidth = (frame.height * width / stripHeight).coerceAtMost(frame.width.toFloat()).toInt().coerceAtLeast(1)
            drawImage(
                frame,
                srcOffset = IntOffset((frame.width - sliceWidth) / 2, 0),
                srcSize = IntSize(sliceWidth, frame.height),
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(width.roundToInt(), stripHeight.roundToInt()),
            )
        }
    }
}

// small frames from across the video for the strip, read off the main thread
private suspend fun videoFrames(context: Context, uri: Uri, durationMs: Long): List<ImageBitmap> = withContext(Dispatchers.IO) {
    runCatching {
        MediaMetadataRetriever().run {
            try {
                setDataSource(context, uri)
                val step = (durationMs / StripFrames).coerceAtLeast(1L)
                (0 until StripFrames).mapNotNull { i ->
                    getFrameAtTime((i * step + step / 2) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
                        val scale = 96f / frame.height.coerceAtLeast(1)
                        Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt().coerceAtLeast(1), 96, true)
                            .also { if (it !== frame) frame.recycle() }
                            .asImageBitmap()
                    }
                }
            } finally {
                release()
            }
        }
    }.getOrDefault(emptyList())
}
