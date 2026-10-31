package com.mochyy.mochymusic.ui.player.bedside

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.player.SongCarousel
import com.mochyy.mochymusic.ui.player.flagship.AnimatedPlayGlyph
import com.mochyy.mochymusic.ui.player.flagship.AnimatedSkipGlyph
import com.mochyy.mochymusic.ui.player.flagship.SkipCycleState
import com.mochyy.mochymusic.ui.player.models.TrackInfo
import com.mochyy.mochymusic.ui.player.styles.LocalStyleInteractive
import com.mochyy.mochymusic.ui.player.styles.whileStyleInteractive
import com.mochyy.mochymusic.utils.makeTimeString
import kotlinx.coroutines.launch

// the music tile's pages, top to bottom
internal const val MusicPages = 2

private val ThumbShape = RoundedCornerShape(8.dp)
private val CardShape = RoundedCornerShape(26.dp)
private val CardCoverShape = RoundedCornerShape(14.dp)
private val CardFill = Color(0xFF1C1C1E)
private val ButtonBox = 44.dp

// one page of the music tile, all drawn plain white inside a night tint layer that colours and dims it at rest
@Composable
internal fun MusicTilePage(
    page: Int,
    track: TrackInfo,
    carousel: SongCarousel?,
    progress: BedsideProgress,
    ink: BedsideInk,
    landscape: Boolean,
    padding: BedsideInsets,
    modifier: Modifier,
) {
    if (page == 0) CoverPage(track, carousel, progress, ink, landscape, padding, modifier)
    else CardPage(track, carousel, progress, ink, modifier)
}

// the cover filling the tile and fading into the black beside it, the song and its controls over the bottom of it
@Composable
private fun CoverPage(
    track: TrackInfo,
    carousel: SongCarousel?,
    progress: BedsideProgress,
    ink: BedsideInk,
    landscape: Boolean,
    padding: BedsideInsets,
    modifier: Modifier,
) {
    val painter = rememberCoverPainter(track.thumbnailUrl)
    Box(modifier.fillMaxSize().nightTint(ink)) {
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val under = Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f))
                    // sideways the tile fades into the clock beside it; upright the bottom fade already meets the tile under it
                    val beside = Brush.horizontalGradient(0.5f to Color.Transparent, 1f to Color.Black)
                    onDrawBehind {
                        drawCover(painter, ink.greyFilter())
                        drawRect(under)
                        if (landscape) drawRect(beside)
                    }
                },
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = padding.start + 30.dp, end = padding.end + 22.dp, bottom = padding.bottom + 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp)) {
                    SongSlots(carousel, track) { song, slot -> SmallCover(song.artwork, ink, slot.fillMaxSize()) }
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    SongSlots(carousel, track) { song, slot ->
                        Column(slot) {
                            BasicText(song.title, style = labelStyle(20.dp, FontWeight.Bold), color = WhiteInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(song.artist, style = labelStyle(13.dp), color = SoftWhiteInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                LikeButton(track.liked)
            }
            Spacer(Modifier.height(12.dp))
            SongTimes(progress)
            Spacer(Modifier.height(6.dp))
            ProgressLine(progress, { Color.White }, Modifier.fillMaxWidth().height(4.dp))
            Spacer(Modifier.height(10.dp))
            TileTransport(Modifier.align(Alignment.CenterHorizontally), gap = 36.dp)
        }
    }
}

// a dark card: now playing, the title, the cover with the buttons stacked beside it, and the song's line along the bottom
@Composable
private fun CardPage(track: TrackInfo, carousel: SongCarousel?, progress: BedsideProgress, ink: BedsideInk, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().nightTint(ink), contentAlignment = Alignment.Center) {
        val cover = minOf(maxHeight - 160.dp, maxWidth - 170.dp).coerceAtLeast(72.dp)
        val inner = cover + 16.dp + ButtonBox
        Column(
            Modifier
                .clip(CardShape)
                .background(CardFill)
                .padding(18.dp)
                .width(inner),
        ) {
            BasicText(stringResource(R.string.bedside_now_playing), style = labelStyle(11.dp, FontWeight.SemiBold), color = SoftWhiteInk)
            Box {
                SongSlots(carousel, track) { song, slot ->
                    BasicText(song.title, style = labelStyle(18.dp, FontWeight.Bold), color = WhiteInk, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = slot)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(cover)) {
                    SongSlots(carousel, track) { song, slot -> SmallCover(song.artwork, ink, slot.fillMaxSize(), CardCoverShape) }
                }
                Spacer(Modifier.width(16.dp))
                TileTransport(vertical = true, gap = 10.dp)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressTime(progress, total = false, size = 11.dp, colour = SoftWhiteInk)
                Spacer(Modifier.width(10.dp))
                ProgressLine(progress, { Color.White }, Modifier.weight(1f).height(4.dp))
                Spacer(Modifier.width(10.dp))
                ProgressTime(progress, total = true, size = 11.dp, colour = SoftWhiteInk)
            }
        }
    }
}

// how far in and how long, either end of the line
@Composable
private fun SongTimes(progress: BedsideProgress) {
    Row(Modifier.fillMaxWidth()) {
        ProgressTime(progress, total = false, size = 12.dp, colour = WhiteInk)
        Spacer(Modifier.weight(1f))
        ProgressTime(progress, total = true, size = 12.dp, colour = WhiteInk)
    }
}

// one time on its own, so the ticking second only redraws this label and not the page around it
@Composable
private fun ProgressTime(progress: BedsideProgress, total: Boolean, size: Dp, colour: androidx.compose.ui.graphics.ColorProducer) {
    BasicText(
        makeTimeString(if (total) progress.durationMs else progress.positionMs),
        style = labelStyle(size, FontWeight.SemiBold),
        color = colour,
    )
}

// a cover inside a tint layer: it only loses its colour at rest, the layer does the rest
@Composable
private fun SmallCover(url: String?, ink: BedsideInk, modifier: Modifier, shape: RoundedCornerShape = ThumbShape) {
    val painter = rememberCoverPainter(url)
    Box(
        modifier
            .clip(shape)
            .drawBehind {
                drawRect(Color.White.copy(alpha = 0.12f))
                drawCover(painter, ink.greyFilter())
            },
    )
}

@Composable
private fun LikeButton(liked: Boolean) {
    val connection = LocalPlayerConnection.current ?: return
    val label = stringResource(R.string.bedside_like)
    TileButton(onClick = connection::toggleLike, modifier = Modifier.semantics { contentDescription = label }) {
        Icon(
            painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

// back, play and next as the flagship's running glyphs, no circles round them
@Composable
private fun TileTransport(modifier: Modifier = Modifier, vertical: Boolean = false, gap: Dp = 30.dp, glyph: Dp = 22.dp) {
    val connection = LocalPlayerConnection.current ?: return
    val transport by connection.uiState.transportState.collectAsState()
    val scope = rememberCoroutineScope()
    val previous = remember { SkipCycleState() }
    val next = remember { SkipCycleState() }
    val buttons: @Composable () -> Unit = {
        TileButton(
            enabled = transport.canSkipPrevious,
            onClick = {
                scope.launch { previous.step() }
                connection.seekToPrevious()
            },
        ) { AnimatedSkipGlyph(cycle = previous, backwards = true, color = Color.White, width = glyph * 1.2f) }
        TileButton(
            onClick = {
                if (transport.playbackState == Player.STATE_ENDED) {
                    connection.player.seekTo(0, 0)
                    connection.player.playWhenReady = true
                } else {
                    connection.togglePlayPause()
                }
            },
        ) { AnimatedPlayGlyph(playing = transport.isPlaying, color = Color.White, height = glyph) }
        TileButton(
            enabled = transport.canSkipNext,
            onClick = {
                scope.launch { next.step() }
                connection.seekToNext()
            },
        ) { AnimatedSkipGlyph(cycle = next, backwards = false, color = Color.White, width = glyph * 1.2f) }
    }
    if (vertical) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) { buttons() }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) { buttons() }
    }
}

// a plain button that dips when pressed and always takes the first tap
@Composable
private fun TileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val interactive = LocalStyleInteractive.current
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier = modifier
            .size(ButtonBox)
            .graphicsLayer {
                scaleX = press.value
                scaleY = press.value
                alpha = if (enabled) 1f else 0.4f
            }
            .pointerInput(enabled, interactive) {
                if (!enabled) return@pointerInput
                whileStyleInteractive(interactive) {
                    detectTapGestures(
                        onPress = {
                            scope.launch { press.animateTo(0.84f, spring(dampingRatio = 0.6f, stiffness = 900f)) }
                            tryAwaitRelease()
                            scope.launch { press.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f)) }
                        },
                        onTap = { currentOnClick() },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private val WhiteInk = androidx.compose.ui.graphics.ColorProducer { Color.White }
private val SoftWhiteInk = androidx.compose.ui.graphics.ColorProducer { Color.White.copy(alpha = 0.6f) }
