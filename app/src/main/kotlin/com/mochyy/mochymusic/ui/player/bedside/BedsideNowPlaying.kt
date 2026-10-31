package com.mochyy.mochymusic.ui.player.bedside

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.ui.player.LocalSongCarousel
import com.mochyy.mochymusic.ui.player.SongCarousel
import com.mochyy.mochymusic.ui.player.carouselText
import com.mochyy.mochymusic.ui.player.models.TrackInfo
import com.mochyy.mochymusic.ui.player.rememberTextSlots
import com.mochyy.mochymusic.ui.player.styles.LocalStyleInteractive
import com.mochyy.mochymusic.ui.player.styles.StyleTones
import com.mochyy.mochymusic.ui.player.styles.TransportCircles
import com.mochyy.mochymusic.ui.player.styles.rememberStyleShowing
import com.mochyy.mochymusic.ui.player.styles.styleSongSwipe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val CoverPx = 1024
private val CoverShape = RoundedCornerShape(22.dp)
private val CoverBlank = Color.White.copy(alpha = 0.06f)
private const val ProgressTickMs = 1_000L

// what the bedside shows of one song
@Immutable
internal class BedsideSong(val title: String, val artist: String, val artwork: String?)

// the song half: its cover, title and artist, a thin line for how far in, and the buttons once it's touched
@Composable
internal fun BedsideNowPlaying(
    ink: BedsideInk,
    rest: BedsideRest,
    tones: StyleTones,
    time: BedsideTime,
    visible: () -> Boolean,
    preview: Boolean,
    modifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current
    val track = rememberPlayingTrack()
    val carousel = if (preview) null else LocalSongCarousel.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .styleSongSwipe(LocalStyleInteractive.current, carousel) { connection?.player },
    ) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            val pad = maxHeight * 0.12f
            val cover = minOf(maxHeight * 0.7f, maxWidth * 0.38f)
            val titleSize = maxHeight * 0.075f
            Row(Modifier.fillMaxSize().padding(horizontal = pad * 1.3f), verticalAlignment = Alignment.CenterVertically) {
                CoverSlots(carousel, track, ink, Modifier.size(cover))
                Spacer(Modifier.width(pad))
                SongDetails(carousel, track, ink, rest, tones, time, visible, titleSize, preview, Modifier.weight(1f))
            }
        } else {
            val pad = maxWidth * 0.09f
            val cover = minOf(maxWidth - pad * 2, maxHeight * 0.42f)
            val titleSize = maxWidth * 0.068f
            Column(Modifier.fillMaxSize().padding(horizontal = pad), verticalArrangement = Arrangement.Center) {
                CoverSlots(carousel, track, ink, Modifier.size(cover).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(pad))
                SongDetails(carousel, track, ink, rest, tones, time, visible, titleSize, preview, Modifier.fillMaxWidth())
            }
        }
    }
}

// the small time over the song, its words, the line and the buttons, top to bottom
@Composable
private fun SongDetails(
    carousel: SongCarousel?,
    track: TrackInfo,
    ink: BedsideInk,
    rest: BedsideRest,
    tones: StyleTones,
    time: BedsideTime,
    visible: () -> Boolean,
    titleSize: Dp,
    preview: Boolean,
    modifier: Modifier,
) {
    Column(modifier) {
        BasicText("${time.hours}:${time.minutes}", style = labelStyle(titleSize * 0.62f, FontWeight.SemiBold), color = ink.soft)
        Spacer(Modifier.height(titleSize * 0.5f))
        Box {
            SongSlots(carousel, track) { song, slot ->
                Column(slot) {
                    BasicText(song.title, style = labelStyle(titleSize, FontWeight.Bold), color = ink.main, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(titleSize * 0.2f))
                    BasicText(song.artist, style = labelStyle(titleSize * 0.66f), color = ink.soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(titleSize * 0.9f))
        ProgressLine(rememberBedsideProgress(visible), ink::ink, Modifier.fillMaxWidth().height(4.dp))
        if (!preview) {
            Spacer(Modifier.height(titleSize * 0.9f))
            Box(Modifier.bedsideChrome(rest)) { TransportCircles(tones, size = 46.dp, spacing = 16.dp) }
        }
    }
}

@Composable
private fun CoverSlots(carousel: SongCarousel?, track: TrackInfo, ink: BedsideInk, modifier: Modifier) {
    Box(modifier) {
        SongSlots(carousel, track) { song, slot -> BedsideCover(song.artwork, ink, slot.fillMaxSize()) }
    }
}

// the playing song, and while a swipe slides the row the one coming in beside it, each with its own words and cover
@Composable
internal fun SongSlots(
    carousel: SongCarousel?,
    track: TrackInfo,
    content: @Composable (song: BedsideSong, slotModifier: Modifier) -> Unit,
) {
    if (carousel == null) {
        content(BedsideSong(track.title, track.artist, track.thumbnailUrl), Modifier)
        return
    }
    for (slot in rememberTextSlots(carousel)) {
        key(slot.uid) {
            val playing = slot.step == carousel.step
            val song = BedsideSong(
                title = slot.title.ifEmpty { if (playing) track.title else "" },
                artist = slot.artist.ifEmpty { if (playing) track.artist else "" },
                artwork = slot.artworkUrl ?: if (playing) track.thumbnailUrl else null,
            )
            content(song, Modifier.carouselText(carousel, slot))
        }
    }
}

@Composable
private fun BedsideCover(url: String?, ink: BedsideInk, modifier: Modifier) {
    val painter = rememberCoverPainter(url)
    Box(
        modifier
            .clip(CoverShape)
            .drawBehind {
                drawRect(CoverBlank)
                drawCover(painter, ink.coverFilter())
            },
    )
}

// how far into the song, read once a second and only while it can be seen, so a whole night of music costs next to nothing
@Stable
internal class BedsideProgress {
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    val fraction: Float get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

@Composable
internal fun rememberBedsideProgress(visible: () -> Boolean): BedsideProgress {
    val connection = LocalPlayerConnection.current
    val progress = remember { BedsideProgress() }
    val showing = rememberStyleShowing()
    val currentVisible by rememberUpdatedState(visible)
    LaunchedEffect(connection) {
        val ui = connection?.uiState ?: return@LaunchedEffect
        combine(
            ui.transportState.map { it.isPlaying }.distinctUntilChanged(),
            snapshotFlow { showing() && currentVisible() }.distinctUntilChanged(),
        ) { playing, shown -> playing to shown }
            .collectLatest { (playing, shown) ->
                if (!shown) return@collectLatest
                if (!playing) {
                    // paused, it only moves when someone seeks
                    ui.progressState.map { it.position to it.duration }.distinctUntilChanged().collect { (position, duration) ->
                        progress.positionMs = position
                        progress.durationMs = duration
                    }
                    return@collectLatest
                }
                while (true) {
                    val player = connection.player
                    progress.positionMs = player.currentPosition
                    progress.durationMs = player.duration.coerceAtLeast(0L)
                    delay(ProgressTickMs)
                }
            }
    }
    return progress
}

@Composable
internal fun ProgressLine(progress: BedsideProgress, colour: () -> Color, modifier: Modifier) {
    Box(
        modifier.drawBehind {
            val corner = CornerRadius(size.height / 2f)
            val ink = colour()
            drawRoundRect(ink.copy(alpha = ink.alpha * 0.22f), cornerRadius = corner)
            drawRoundRect(ink, size = size.copy(width = size.width * progress.fraction), cornerRadius = corner)
        },
    )
}

@Composable
internal fun rememberPlayingTrack(): TrackInfo {
    val connection = LocalPlayerConnection.current ?: return TrackInfo()
    val track by connection.uiState.trackInfo.collectAsState()
    return track
}

// the song's cover as a painter, so it can be drawn through the night look's filter without recomposing anything
@Composable
internal fun rememberCoverPainter(url: String?): Painter {
    val context = LocalContext.current
    val request = remember(url, context) {
        ImageRequest.Builder(context).data(url).size(CoverPx).crossfade(true).build()
    }
    return rememberAsyncImagePainter(request)
}

// draws the cover filling the box the way a crop would, through the night look's filter
internal fun DrawScope.drawCover(painter: Painter, filter: ColorFilter?) {
    val source = painter.intrinsicSize
    if (source.isUnspecified || source.width <= 0f || source.height <= 0f) {
        with(painter) { draw(size, colorFilter = filter) }
        return
    }
    val scale = maxOf(size.width / source.width, size.height / source.height)
    val drawn = Size(source.width * scale, source.height * scale)
    clipRect {
        translate((size.width - drawn.width) / 2f, (size.height - drawn.height) / 2f) {
            with(painter) { draw(drawn, colorFilter = filter) }
        }
    }
}
