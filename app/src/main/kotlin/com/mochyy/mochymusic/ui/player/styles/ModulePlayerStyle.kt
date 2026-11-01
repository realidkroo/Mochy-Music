// a look module's own player, drawn from its layout with the parts every style shares: the frame, the menu, the riding songs (modules/README.md)

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.LocalArtworkColor
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.CoverShape
import com.mochyy.mochymusic.modules.skin.ModuleSkin
import com.mochyy.mochymusic.modules.skin.ProgressStyle
import com.mochyy.mochymusic.modules.skin.SkinAlign
import com.mochyy.mochymusic.modules.skin.SkinBackground
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.modules.skin.SkinColor
import com.mochyy.mochymusic.modules.skin.SkinPlayer
import com.mochyy.mochymusic.modules.skin.SkinText
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.player.CarouselPictures
import com.mochyy.mochymusic.ui.player.SongCarousel
import com.mochyy.mochymusic.ui.player.customize.PlayerBackgroundContent
import com.mochyy.mochymusic.ui.player.flagship.AmbientBackdrop
import com.mochyy.mochymusic.ui.player.flagship.FollowPicture
import com.mochyy.mochymusic.ui.player.flagship.rememberAmbientClock
import com.mochyy.mochymusic.ui.player.flagship.rememberAmbientColors
import com.mochyy.mochymusic.utils.makeTimeString
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** what every block reads: the song, the clock and the colours, gathered once per song */
@Stable
private class SkinContext(
    val skin: ModuleSkin,
    val layout: SkinPlayer,
    val tones: StyleTones,
    val track: StyleTrack,
    val position: LivePosition,
    val lyricsPosition: LivePosition,
    val duration: Long,
    val carousel: SongCarousel?,
    val lyrics: StyleLyrics,
    val preview: Boolean,
    private val cover: Color,
    private val accent: Color,
) {
    fun color(color: SkinColor): Color = when (color) {
        is SkinColor.Fixed -> color.color
        SkinColor.Ink -> tones.ink
        SkinColor.InkSoft -> tones.inkSoft
        SkinColor.Played -> tones.played
        SkinColor.Remaining -> tones.remaining
        SkinColor.Chip -> tones.chip
        SkinColor.Cover -> cover
        SkinColor.Accent -> accent
    }
}

@Composable
internal fun ModulePlayerStyle(actions: StyleActions, modifier: Modifier, preview: Boolean) {
    // the look can go off a frame before the player falls back to the flagship; nothing is drawn for that frame
    val skin = ActiveSkin.current ?: return
    val layout = skin.player ?: return
    val tones = rememberStyleTones()
    val track = rememberStyleTrack()
    val position = rememberLivePosition(animated = !preview)
    val duration = rememberDurationMs()
    val carousel = rememberStyleCarousel(preview)
    // a layout without lyrics never fetches or parses any
    val wantsLyrics = remember(layout) { layout.blocks.any(::showsLyrics) }
    val lyrics = if (wantsLyrics) rememberStyleLyrics(readings = !preview) else NoLyrics
    val context = SkinContext(
        skin, layout, tones, track, position, position.withLyricsOffset(), duration, carousel, lyrics, preview,
        cover = LocalArtworkColor.current, accent = MaterialTheme.colorScheme.primary,
    )
    val background: (@Composable BoxScope.() -> Unit)? =
        if (layout.background == SkinBackground.Tones) null else { { SkinBackdrop(context) } }
    StyleFrame(PlayerStyle.MODULE, preview, modifier, tones, background) { landscape, menuOpen, setMenuOpen, rotated, setRotated ->
        val insets = styleInsets(landscape)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = layout.padding + insets.start,
                    end = layout.padding + insets.end,
                    top = insets.top + TopRoom,
                    bottom = insets.bottom + 24.dp,
                ),
            horizontalAlignment = layout.align.horizontal,
        ) {
            ColumnBlocks(layout.blocks, context)
        }
        StyleMenu(
            open = menuOpen, onOpenChange = setMenuOpen, tones = tones, actions = actions,
            rotated = rotated, onRotate = setRotated, atBottom = false,
            end = 22.dp + insets.end, edge = insets.top + 20.dp, buttonSize = MenuButton,
        )
    }
}

// ---- layout ----------------------------------------------------------------------------------

@Composable
private fun ColumnScope.ColumnBlocks(blocks: List<SkinBlock>, context: SkinContext) {
    blocks.forEach { block ->
        when {
            block is SkinBlock.Space && block.grow -> Spacer(Modifier.weight(1f))
            block is SkinBlock.Space -> Spacer(Modifier.height(block.size))
            else -> Block(block, context, Modifier, inRow = false)
        }
    }
}

@Composable
private fun RowScope.RowBlocks(blocks: List<SkinBlock>, context: SkinContext) {
    blocks.forEach { block ->
        when {
            block is SkinBlock.Space && block.grow -> Spacer(Modifier.weight(1f))
            block is SkinBlock.Space -> Spacer(Modifier.width(block.size))
            // words, bars and groups share what's left of the row; covers, pictures and buttons keep their own size
            block.stretches -> Block(block, context, Modifier.weight(1f), inRow = true)
            else -> Block(block, context, Modifier, inRow = true)
        }
    }
}

@Composable
private fun Block(block: SkinBlock, context: SkinContext, modifier: Modifier, inRow: Boolean) {
    when (block) {
        is SkinBlock.Cover -> SkinCover(block, context, modifier)
        is SkinBlock.Title -> SongText(block.text, context, modifier, inRow) { it.title }
        is SkinBlock.Artist -> SongText(block.text, context, modifier, inRow) { it.artist }
        is SkinBlock.Album -> SongText(block.text, context, modifier, inRow) { it.album }
        is SkinBlock.Label -> TextLine(block.value, block.text, context, modifier.fillMaxWidth())
        is SkinBlock.Picture -> SkinPicture(block, context, modifier)
        is SkinBlock.Progress -> SkinProgress(block, context, modifier)
        is SkinBlock.Controls -> SkinControls(block, context, modifier)
        is SkinBlock.Lyrics -> SkinLyrics(block, context, modifier)
        is SkinBlock.Row -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(block.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) { RowBlocks(block.blocks, context) }
        is SkinBlock.Column -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(block.gap),
            horizontalAlignment = context.layout.align.horizontal,
        ) { ColumnBlocks(block.blocks, context) }
        // spaces are the column's and the row's to lay out
        is SkinBlock.Space -> Unit
    }
}

// ---- blocks ----------------------------------------------------------------------------------

/** the cover in its shape, riding the row of songs, turning like a record while it plays when the layout asks */
@Composable
private fun SkinCover(block: SkinBlock.Cover, context: SkinContext, modifier: Modifier) {
    val shape = when (block.shape) {
        CoverShape.Circle -> CircleShape
        CoverShape.Square -> RectangleShape
        CoverShape.Rounded -> RoundedCornerShape(block.corner)
    }
    val turn = if (block.spin) rememberRecordTurn(context.preview) else null
    Box(
        modifier = modifier
            .fillMaxWidth(block.size)
            .aspectRatio(1f)
            // a turning cover redraws every frame, so it's kept to its own square and nothing else repaints with it
            .then(if (turn != null) Modifier.damageBoundary() else Modifier),
    ) {
        CarouselPictures(
            carousel = context.carousel,
            fallbackUrl = context.track.artwork,
            modifier = Modifier
                .fillMaxSize()
                .then(if (block.shadow) Modifier.shadow(18.dp, shape) else Modifier)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.08f)),
        ) { url, pictureModifier ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = pictureModifier.then(if (turn != null) Modifier.graphicsLayer { rotationZ = turn() } else Modifier),
            )
        }
        // the frame holds still over the turning cover, like a record player's rim
        block.frame?.let { frame ->
            AsyncImage(
                model = context.skin.resolve(frame),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** the song's title, artist or album, riding the row of songs the way the other styles' words do */
@Composable
private fun SongText(text: SkinText, context: SkinContext, modifier: Modifier, inRow: Boolean, pick: (StyleTrack) -> String) {
    Box(modifier.then(if (inRow) Modifier else Modifier.fillMaxWidth())) {
        StyleSongs(context.carousel, context.track, NoLyrics) { song, slotModifier ->
            TextLine(pick(song.track), text, context, slotModifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TextLine(value: String, text: SkinText, context: SkinContext, modifier: Modifier) {
    Text(
        text = if (text.upper) value.uppercase() else value,
        color = context.color(text.color),
        fontSize = text.size,
        fontWeight = text.weight,
        maxLines = text.lines,
        overflow = TextOverflow.Ellipsis,
        textAlign = (text.align ?: context.layout.align).textAlign,
        modifier = modifier,
    )
}

@Composable
private fun SkinPicture(block: SkinBlock.Picture, context: SkinContext, modifier: Modifier) {
    AsyncImage(
        model = context.skin.resolve(block.image),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .then(block.width?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth())
            .height(block.height)
            .clip(RoundedCornerShape(block.corner)),
    )
}

/** a bar the song plays along, drawn off the frame's clock so it never recomposes, tapped or dragged to seek */
@Composable
private fun SkinProgress(block: SkinBlock.Progress, context: SkinContext, modifier: Modifier) {
    val connection = LocalPlayerConnection.current
    val interactive = LocalStyleInteractive.current
    val played = context.color(block.color)
    val rest = context.color(block.track)
    val thickness = when (block.style) {
        ProgressStyle.Line -> 3.dp
        ProgressStyle.Thick -> 7.dp
        ProgressStyle.Pill -> 14.dp
    }
    // where a drag holds the bar, so it follows the finger instead of the song until it's let go
    val held = remember { mutableFloatStateOf(-1f) }
    val duration = context.duration
    val seekable = block.seek && !context.preview && connection != null
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(thickness + 20.dp)
                .then(
                    if (!seekable) Modifier else Modifier
                        .pointerInput(duration) {
                            whileStyleInteractive(interactive) {
                                detectTapGestures { offset ->
                                    connection?.player?.seekTo((offset.x / size.width * duration).toLong().coerceIn(0L, duration))
                                }
                            }
                        }
                        .pointerInput(duration) {
                            whileStyleInteractive(interactive) {
                                detectHorizontalDragGestures(
                                    onDragStart = { held.floatValue = (it.x / size.width).coerceIn(0f, 1f) },
                                    onDragEnd = {
                                        connection?.player?.seekTo((held.floatValue * duration).toLong())
                                        held.floatValue = -1f
                                    },
                                    onDragCancel = { held.floatValue = -1f },
                                ) { change, _ ->
                                    change.consume()
                                    held.floatValue = (change.position.x / size.width).coerceIn(0f, 1f)
                                }
                            }
                        }
                )
                .drawBehind {
                    val fraction = held.floatValue.takeIf { it >= 0f }
                        ?: (context.position.positionMs().toFloat() / duration).coerceIn(0f, 1f)
                    val stroke = thickness.toPx()
                    val y = size.height / 2f
                    val start = stroke / 2f
                    val end = size.width - stroke / 2f
                    drawLine(rest, Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
                    if (fraction > 0f) drawLine(played, Offset(start, y), Offset(start + (end - start) * fraction, y), stroke, StrokeCap.Round)
                },
        )
        if (block.times) {
            val style = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            val color = context.color(SkinColor.InkSoft)
            Row(Modifier.fillMaxWidth()) {
                PlayedTime(context.position, color, style, Modifier)
                Spacer(Modifier.weight(1f))
                Text(makeTimeString(duration), color = color, style = style, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SkinControls(block: SkinBlock.Controls, context: SkinContext, modifier: Modifier) {
    val fill = if (block.plain) Color.Transparent else block.color?.let(context::color) ?: context.tones.chip
    val icon = block.icon?.let(context::color) ?: context.tones.ink
    val tones = remember(context.tones, fill, icon) { context.tones.copy(chip = fill, ink = icon) }
    Box(modifier) {
        TransportCircles(tones = tones, size = block.size, spacing = block.size * 0.35f)
    }
}

@Composable
private fun SkinLyrics(block: SkinBlock.Lyrics, context: SkinContext, modifier: Modifier) {
    val color = context.color(block.color)
    Box(modifier.fillMaxWidth().height(block.height)) {
        // each song's words ride the row with it, and a song with no synced lyrics leaves the space empty
        StyleSongs(context.carousel, context.track, context.lyrics) { song, slotModifier ->
            if (song.lines.isNotEmpty()) {
                ExpressiveLyrics(
                    lines = song.lines,
                    position = context.lyricsPosition,
                    color = color,
                    baseSize = block.size,
                    modifier = Modifier.fillMaxSize().then(slotModifier),
                )
            }
        }
    }
}

// ---- background ------------------------------------------------------------------------------

@Composable
private fun BoxScope.SkinBackdrop(context: SkinContext) {
    val layout = context.layout
    when (val background = layout.background) {
        is SkinBackground.Player -> if (background.style == PlayerBackgroundStyle.AMBIENT) {
            AmbientSkinBackdrop(context)
        } else {
            PlayerBackgroundContent(style = background.style, thumbnailUrl = context.track.artwork, pureBlack = false, modifier = Modifier.fillMaxSize())
        }
        is SkinBackground.Fill -> {
            val colors = background.colors.map(context::color)
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (colors.size == 1) Modifier.background(colors.first()) else Modifier.background(Brush.verticalGradient(colors))),
            )
        }
        is SkinBackground.Picture -> AsyncImage(
            model = context.skin.resolve(background.image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        SkinBackground.Tones -> Unit
    }
    if (layout.dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = layout.dim)))
}

// the flagship's drifting colours, whose clock rests while the style can't be seen instead of running under the mini player
@Composable
private fun AmbientSkinBackdrop(context: SkinContext) {
    val showing = rememberStyleShowing()
    val obscured = LocalAppContentObscured.current
    val running by remember { derivedStateOf { !context.preview && showing() && !obscured.value } }
    val colors = rememberAmbientColors()
    FollowPicture(colors, context.track.artwork)
    AmbientBackdrop(colors = colors, time = rememberAmbientClock(running = running), modifier = Modifier.fillMaxSize())
}

// ---- helpers ---------------------------------------------------------------------------------

/** the cover's turn at a record's 33⅓ rpm, only while the song plays and the style can be seen */
@Composable
private fun rememberRecordTurn(preview: Boolean): () -> Float {
    val angle = remember { mutableFloatStateOf(0f) }
    val connection = LocalPlayerConnection.current
    val showing = rememberStyleShowing()
    val obscured = LocalAppContentObscured.current
    LaunchedEffect(connection, preview) {
        if (preview || connection == null) return@LaunchedEffect
        val playing = connection.uiState.transportState.map { it.isPlaying }.distinctUntilChanged()
        combine(playing, snapshotFlow { showing() && !obscured.value }) { on, shown -> on && shown }
            .distinctUntilChanged()
            .collectLatest { turning ->
                if (!turning) return@collectLatest
                var last = 0L
                while (true) {
                    withFrameNanos { now ->
                        if (last != 0L) angle.floatValue = (angle.floatValue + (now - last) / 1_000_000_000f * DegreesPerSecond) % 360f
                        last = now
                    }
                }
            }
    }
    return remember { { angle.floatValue } }
}

private fun showsLyrics(block: SkinBlock): Boolean = when (block) {
    is SkinBlock.Lyrics -> true
    is SkinBlock.Row -> block.blocks.any(::showsLyrics)
    is SkinBlock.Column -> block.blocks.any(::showsLyrics)
    else -> false
}

private val SkinBlock.stretches: Boolean
    get() = this is SkinBlock.Title || this is SkinBlock.Artist || this is SkinBlock.Album || this is SkinBlock.Label ||
        this is SkinBlock.Progress || this is SkinBlock.Lyrics || this is SkinBlock.Column

private val SkinAlign.horizontal: Alignment.Horizontal
    get() = when (this) {
        SkinAlign.Start -> Alignment.Start
        SkinAlign.Center -> Alignment.CenterHorizontally
        SkinAlign.End -> Alignment.End
    }

private val SkinAlign.textAlign: TextAlign
    get() = when (this) {
        SkinAlign.Start -> TextAlign.Start
        SkinAlign.Center -> TextAlign.Center
        SkinAlign.End -> TextAlign.End
    }

private val NoLyrics = StyleLyrics(null, emptyList())
// room at the top for the player's handle and the ••• button
private val TopRoom = 64.dp
private val MenuButton = 36.dp
// 33⅓ turns a minute
private const val DegreesPerSecond = 200f
