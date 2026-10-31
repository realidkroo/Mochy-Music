package com.mochyy.mochymusic.ui.player.video

import androidx.compose.foundation.Image
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.ui.player.flagship.AnimatedPlayGlyph
import com.mochyy.mochymusic.ui.player.flagship.AnimatedSkipGlyph
import com.mochyy.mochymusic.ui.player.flagship.SkipCycleState
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.player.PressScaleActionButton
import com.mochyy.mochymusic.ui.player.models.TrackInfo
import com.mochyy.mochymusic.ui.player.rememberMusicVolume
import com.mochyy.mochymusic.ui.player.styles.rememberDurationMs
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.ui.utils.stableStatusBars
import com.mochyy.mochymusic.utils.makeTimeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// the controls line up with the artwork on the left and the seek bar's end on the right
private val EdgeStart = 48.dp
private val EdgeEnd = 54.dp
private val TopRow = 32.dp

// the artwork: the song's cover beside its name with the controls up, a small faint watermark in the corner without them
private val ArtSize = 48.dp
private const val WatermarkScale = 28f / 48f
private const val WatermarkAlpha = 0.6f

// the play buttons, as far apart as the mock
private val PlaySize = 64.dp
private val SkipSize = 44.dp
private val TransportGap = 52.dp

// the seek bar, its touch strip, and the round chevron button above its end
private val SeekBottom = 14.dp
private val SeekTouch = 28.dp
private val SeekThickness = 5.dp
private val ChevronSize = 30.dp

// the volume line at the left edge
private val VolumeLength = 100.dp
private val VolumeTouch = 40.dp

// shown from the first frame of the fade in until the last of the fade out, so they can be pressed only while they can be seen
@Composable
private fun rememberControlsVisible(controls: () -> Float) =
    remember { derivedStateOf { controls() > 0.01f } }

@Composable
internal fun BoxScope.VideoTopBar(
    track: TrackInfo,
    frame: VideoFrame,
    state: VideoModeState,
    controls: () -> Float,
    onClose: () -> Unit,
    onSongInfo: () -> Unit,
) {
    val visible by rememberControlsVisible(controls)
    AsyncImage(
        model = track.thumbnailUrl?.resize(240, 240),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = EdgeStart, top = TopRow)
            .size(ArtSize)
            .graphicsLayer {
                val c = controls()
                val scale = WatermarkScale + (1f - WatermarkScale) * c
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                alpha = WatermarkAlpha + (1f - WatermarkAlpha) * c
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.12f)),
    )
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = EdgeStart + ArtSize + 14.dp, top = TopRow + 3.dp)
            .widthIn(max = (frame.width - EdgeStart - ArtSize - EdgeEnd - 70.dp).coerceAtLeast(80.dp))
            .graphicsLayer {
                val c = controls()
                alpha = c
                translationX = -(1f - c) * 12.dp.toPx()
            }
            // the title opens the song's info: its artists, album, about it and the stream it plays from
            .then(
                if (visible) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            state.touched()
                            if (state.controlsShown) onSongInfo() else state.toggleControls()
                        },
                    )
                } else Modifier
            ),
    ) {
        Text(
            text = track.title,
            color = Color.White,
            fontFamily = InterFontFamily,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.artist,
            color = Color.White.copy(alpha = 0.72f),
            fontFamily = InterFontFamily,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (visible) {
        val closeLabel = stringResource(R.string.video_mode_close_player)
        PressScaleActionButton(
            icon = R.drawable.close,
            tint = Color.White,
            containerColor = Color.White.copy(alpha = 0.22f),
            size = 30.dp,
            onClick = { if (state.controlsShown) onClose() else state.toggleControls() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = EdgeEnd, top = TopRow + 9.dp)
                .semantics { contentDescription = closeLabel }
                .graphicsLayer { alpha = controls() },
        )
    }
}

// previous, play or pause, next: in the middle of the whole screen, an open pane included
@Composable
internal fun BoxScope.VideoTransport(
    state: VideoModeState,
    controls: () -> Float,
    isPlaying: Boolean,
) {
    val visible by rememberControlsVisible(controls)
    if (!visible) return
    val connection = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()
    // the flagship's running triangles, each a step on with every press
    val previous = remember { SkipCycleState() }
    val next = remember { SkipCycleState() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(TransportGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                val c = controls()
                alpha = c
                val scale = 0.92f + 0.08f * c
                scaleX = scale
                scaleY = scale
            },
    ) {
        TransportButton(
            label = stringResource(R.string.previous),
            size = SkipSize,
            background = null,
            glyph = { AnimatedSkipGlyph(cycle = previous, backwards = true, color = Color.White, width = GlyphSize) },
        ) {
            state.touched()
            scope.launch { previous.step() }
            connection.seekToPrevious()
        }
        TransportButton(
            label = stringResource(if (isPlaying) R.string.pause else R.string.play),
            size = PlaySize,
            background = Color.Black.copy(alpha = 0.3f),
            glyph = { AnimatedPlayGlyph(playing = isPlaying, color = Color.White, height = GlyphSize) },
        ) {
            state.touched()
            connection.togglePlayPause()
        }
        TransportButton(
            label = stringResource(R.string.next),
            size = SkipSize,
            background = null,
            glyph = { AnimatedSkipGlyph(cycle = next, backwards = false, color = Color.White, width = GlyphSize) },
        ) {
            state.touched()
            scope.launch { next.step() }
            connection.seekToNext()
        }
    }
}

// the old 30dp icons' size: the play triangle about as tall, the skip pair about as wide
private val GlyphSize = 29.dp

@Composable
private fun TransportButton(
    label: String,
    size: Dp,
    background: Color?,
    glyph: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = label }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val scale = if (pressed) 0.88f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .then(if (background != null) Modifier.clip(CircleShape).background(background) else Modifier),
        ) {
            glyph()
        }
    }
}

// the phone's volume as a thin line down the left edge: drag along it, it thickens under the finger
@Composable
internal fun BoxScope.VideoVolumeBar(state: VideoModeState, controls: () -> Float) {
    val visible by rememberControlsVisible(controls)
    if (!visible) return
    val volume = rememberMusicVolume(listening = true)
    var held by remember { mutableStateOf(false) }
    val label = stringResource(R.string.video_mode_volume)
    // the phone's top edge is this side now, and its camera hole sits halfway down it: the line stays clear of it
    val density = LocalDensity.current
    val cutout = with(density) { WindowInsets.stableStatusBars.getTop(this).toDp() }
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = (cutout - 6.dp).coerceAtLeast(2.dp))
            .size(VolumeTouch, VolumeLength + 24.dp)
            .semantics { contentDescription = label }
            .graphicsLayer { alpha = controls() }
            .pointerInput(volume) {
                val inset = 12.dp.toPx()
                fun level(y: Float) = (1f - (y - inset) / (size.height - inset * 2f)).coerceIn(0f, 1f)
                detectVerticalDragGestures(
                    onDragStart = {
                        held = true
                        state.touched()
                        volume.drag(level(it.y))
                    },
                    onDragEnd = {
                        held = false
                        volume.release()
                    },
                    onDragCancel = {
                        held = false
                        volume.release()
                    },
                ) { change, _ ->
                    change.consume()
                    state.touched()
                    volume.drag(level(change.position.y))
                }
            }
            .pointerInput(volume) {
                val inset = 12.dp.toPx()
                detectTapGestures { offset ->
                    state.touched()
                    volume.drag((1f - (offset.y - inset) / (size.height - inset * 2f)).coerceIn(0f, 1f))
                    volume.release()
                }
            }
            .drawBehind {
                val thickness = (if (held) 7.dp else 4.dp).toPx()
                val length = VolumeLength.toPx()
                val left = (size.width - thickness) / 2f
                val top = (size.height - length) / 2f
                val radius = CornerRadius(thickness / 2f)
                drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(left, top), Size(thickness, length), radius)
                val filled = length * volume.shown.coerceIn(0f, 1f)
                drawRoundRect(Color.White, Offset(left, top + length - filled), Size(thickness, filled), radius)
            },
    )
}

// the seek bar across the whole screen, an open pane included, with the times over its ends and the chevron above its right end
@Composable
internal fun BoxScope.VideoSeekRow(
    state: VideoModeState,
    controls: () -> Float,
    positionProvider: () -> Long,
    onChevron: () -> Unit,
) {
    val visible by rememberControlsVisible(controls)
    if (!visible) return
    val connection = LocalPlayerConnection.current ?: return
    val durationMs = rememberDurationMs()
    // the times change once a second, so that's how often this recomposes
    val playedSeconds by remember(connection) {
        connection.progressState.map { it.position / 1000L }.distinctUntilChanged()
    }.collectAsState(initial = connection.progressState.value.position / 1000L)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shownMs = dragFraction?.let { (it * durationMs).toLong() } ?: playedSeconds * 1000L
    // moves with the music every frame while the controls are up; paused, it only has to catch a seek
    val fill = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(connection, durationMs) {
        while (true) {
            withFrameNanos { }
            fill.floatValue = (positionProvider().toFloat() / durationMs).coerceIn(0f, 1f)
            if (!connection.player.isPlaying) delay(200)
        }
    }
    val moreLabel = stringResource(R.string.more_options)

    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .padding(start = EdgeStart, end = EdgeEnd, bottom = SeekBottom)
            .graphicsLayer { alpha = controls() },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            PressScaleActionButton(
                icon = R.drawable.expand_less,
                tint = Color.White,
                containerColor = Color.White.copy(alpha = 0.22f),
                size = ChevronSize,
                onClick = {
                    state.touched()
                    if (state.controlsShown) onChevron() else state.toggleControls()
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .semantics { contentDescription = moreLabel },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TimeText(makeTimeString(shownMs))
            Spacer(modifier = Modifier.weight(1f))
            TimeText(makeTimeString(durationMs))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SeekTouch)
                .pointerInput(durationMs) {
                    fun fraction(x: Float): Float = (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    detectHorizontalDragGestures(
                        onDragStart = {
                            state.touched()
                            dragFraction = fraction(it.x)
                        },
                        onDragEnd = {
                            dragFraction?.let { connection.seekTo((it * durationMs).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        change.consume()
                        state.touched()
                        dragFraction = fraction(change.position.x)
                    }
                }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        state.touched()
                        connection.seekTo(((offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) * durationMs).toLong())
                    }
                }
                .drawBehind {
                    val thickness = (if (dragFraction != null) 7.dp else SeekThickness).toPx()
                    val top = (size.height - thickness) / 2f
                    val radius = CornerRadius(thickness / 2f)
                    drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(0f, top), Size(size.width, thickness), radius)
                    val played = dragFraction ?: fill.floatValue
                    drawRoundRect(Color.White, Offset(0f, top), Size(size.width * played, thickness), radius)
                },
        )
    }
}

@Composable
private fun TimeText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontFamily = InterFontFamily,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

// a double tap's jump: a soft round light on that side with how far it went, gone a moment later
@Composable
internal fun BoxScope.VideoSeekFlash(frame: VideoFrame, state: VideoModeState) {
    val flash = state.seekFlash
    // the last one stays drawn while it fades out
    val shown = remember { arrayOfNulls<SeekFlash>(1) }
    if (flash != null) shown[0] = flash
    val last = shown[0] ?: return
    val glow = remember { Animatable(0f) }
    LaunchedEffect(flash) {
        if (flash == null) return@LaunchedEffect
        glow.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
        delay(SeekFlashHoldMs)
        glow.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
        if (state.seekFlash === flash) state.seekFlash = null
    }
    val paneWidth = with(LocalDensity.current) { frame.paneWidth.toPx() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(if (last.forward) Alignment.CenterEnd else Alignment.CenterStart)
            .fillMaxHeight()
            .width(frame.width * 0.32f)
            .graphicsLayer {
                alpha = glow.value
                if (last.forward) translationX = -paneWidth * state.pane.value
            }
            .drawBehind {
                val radius = size.height * 0.95f
                val x = if (last.forward) size.width + radius * 0.35f else -radius * 0.35f
                drawCircle(Color.White.copy(alpha = 0.14f), radius = radius, center = Offset(x, size.height / 2f))
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.fast_forward),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier
                    .size(30.dp)
                    // there's no rewind icon, the forward one turned round is one
                    .graphicsLayer { if (!last.forward) scaleX = -1f },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pluralStringResource(R.plurals.video_mode_seek_seconds, last.seconds, last.seconds),
                color = Color.White,
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// how long a jump's light stays up before it fades
private const val SeekFlashHoldMs = 420L
