// a look module's own player, drawn from its layout with the parts every style shares: the frame, the menu, the riding songs, sized to the screen it was made for (docs/wiki, player pages)

package com.mochyy.mochymusic.ui.player.styles

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.LocalArtworkColor
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.CoverShape
import com.mochyy.mochymusic.modules.skin.MenuSide
import com.mochyy.mochymusic.modules.skin.ModuleSkin
import com.mochyy.mochymusic.modules.skin.ModuleStyle
import com.mochyy.mochymusic.modules.skin.ProgressStyle
import com.mochyy.mochymusic.modules.skin.SkinAlign
import com.mochyy.mochymusic.modules.skin.SkinBackground
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.modules.skin.SkinColor
import com.mochyy.mochymusic.modules.skin.SkinCondition
import com.mochyy.mochymusic.modules.skin.SkinFlag
import com.mochyy.mochymusic.modules.skin.SkinPictureSource
import com.mochyy.mochymusic.modules.skin.SkinPlayer
import com.mochyy.mochymusic.modules.skin.SkinText
import com.mochyy.mochymusic.modules.skin.StackAlign
import com.mochyy.mochymusic.modules.skin.skinColor
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.player.CarouselPictures
import com.mochyy.mochymusic.ui.player.SongCarousel
import com.mochyy.mochymusic.ui.player.menu.SkinCustomiseSheet
import com.mochyy.mochymusic.utils.makeTimeString
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** the module style a selector card or an editor page draws, instead of the one picked for the player */
val LocalModuleStyle = staticCompositionLocalOf<ModuleStyle?> { null }

/** what every block reads: the song, the clocks, the colours, the module's settings and the player's own controls */
@Stable
internal class SkinContext(
    val style: ModuleStyle,
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
    val cover: Color,
    val accent: Color,
    val values: Map<String, String>,
    val pulse: SkinPulse,
    val actions: StyleActions,
    val openMenu: () -> Unit,
    val openCustomise: () -> Unit,
    /** the player's own state for the built-in @ conditions */
    val live: SkinLive,
    /** where event animations hear about songs, play, pause, likes and beats; null when nothing listens */
    val events: SkinEvents?,
    val landscape: Boolean,
    /** the captured backdrop a frosted box blurs, null when no box asks for one */
    val glass: GlassState?,
    /** the stream's words for {codec}, {kbps} and friends, null when no text names them */
    val audio: SkinAudio? = null,
    /** the live queue snapshot, null when nothing in the layout reads upcoming songs */
    val queue: SkinQueue? = null,
    /** where the lyrics are this frame, for the lyrics placeholders, clocks, events and shaders */
    val lyricClock: LyricClock,
    /** how much bigger than mochy's own dp the blocks are drawn */
    val scale: Float = 1f,
    /** the player editor, while it can come up over this player */
    val editor: ModuleEditorState? = null,
) {
    /** a size that stays mochy's own however the blocks are scaled, for what lines up with the chevron */
    fun fixed(size: Dp): Dp = size / scale

    fun color(color: SkinColor): Color = when (color) {
        is SkinColor.Fixed -> color.color
        SkinColor.Ink -> tones.ink
        SkinColor.InkSoft -> tones.inkSoft
        SkinColor.Played -> tones.played
        SkinColor.Remaining -> tones.remaining
        SkinColor.Chip -> tones.chip
        SkinColor.Cover -> cover
        SkinColor.Accent -> accent
        // a setting can't point at another setting, so this never loops
        is SkinColor.Setting -> skinColor(values[color.key])?.takeIf { it !is SkinColor.Setting }?.let(::color) ?: color(color.fallback)
    }

    /** whether a block's "when" holds for the module's settings and the player as they are now */
    fun holds(condition: SkinCondition?): Boolean {
        condition ?: return true
        val flag = if (condition.key.startsWith("@")) SkinFlag.of(condition.key) else null
        val value = if (flag != null) flagOn(flag).toString() else values[condition.key]?.trim()
        val matches = if (condition.value == null) {
            !value.isNullOrEmpty() && !value.equals("false", ignoreCase = true) && value != "0"
        } else {
            value.equals(condition.value, ignoreCase = true)
        }
        return matches != condition.negate
    }

    // reading these only where a condition names them keeps play and pause from recomposing blocks that don't care
    private fun flagOn(flag: SkinFlag): Boolean = when (flag) {
        SkinFlag.Playing -> live.playing
        SkinFlag.Paused -> !live.playing
        SkinFlag.Liked -> live.liked
        SkinFlag.Shuffle -> live.shuffle
        SkinFlag.Repeat -> live.repeat
        SkinFlag.RepeatOne -> live.repeatOne
        SkinFlag.Lyrics -> lyrics.lines.isNotEmpty()
        SkinFlag.Landscape -> landscape
        SkinFlag.Singing -> live.singing
        SkinFlag.HasNext -> queue?.hasNext == true
        SkinFlag.HasPrev -> queue?.hasPrev == true
        SkinFlag.Lossless -> audio?.isLossless == true
    }
}

@Composable
internal fun ModulePlayerStyle(actions: StyleActions, modifier: Modifier, preview: Boolean) {
    // the look can go off a frame before the player falls back to the flagship; nothing is drawn for that frame
    val style = LocalModuleStyle.current ?: ActiveSkin.style ?: return
    val skin = style.skin
    val layout = style.player
    var customiseOpen by remember { mutableStateOf(false) }
    val openMenu = remember { mutableStateOf({}) }
    // what someone is dragging in the editor shows at once, before it's kept
    val editor = LocalModuleEditor.current?.takeIf { !preview }
    val values = editor?.let { style.values + it.overrides } ?: style.values
    val context = rememberSkinContext(
        style, actions, preview, rememberStyleCarousel(preview), values,
        openMenu = { openMenu.value() }, openCustomise = { customiseOpen = true }, editor = editor,
    )
    val tones = context.tones
    val scale = context.scale
    val background: (@Composable BoxScope.() -> Unit)? =
        if (layout.background == SkinBackground.Tones) null else { { SkinBackdrop(context) } }
    StyleFrame(preview, modifier, tones, background) { landscape, menuOpen, setMenuOpen ->
        openMenu.value = { setMenuOpen(true) }
        // the corner every editor tile is measured from
        Box(Modifier.fillMaxSize().editRoot(editor))
        val insets = styleInsets(landscape)
        // over the playhead split there's no backdrop slot, so the layers go in here, still behind the blocks
        if (layout.background == SkinBackground.Tones && layout.layers.isNotEmpty()) SkinLayers(context)
        PlayerFont(skin.moduleId) {
            ScaledContent(scale) {
                SkinBody(context, insets)
                SkinHeader(context, insets)
            }
        }
        val button = layout.menuButton
        val buttonTones = remember(tones, button, context.cover, context.accent) {
            tones.copy(
                chip = button.fill?.let(context::color) ?: tones.chip,
                ink = button.color?.let(context::color) ?: tones.ink,
            )
        }
        val side = if (button.side == MenuSide.Start) insets.start else insets.end
        StyleMenu(
            open = menuOpen, onOpenChange = setMenuOpen, tones = tones, actions = actions,
            atBottom = false,
            end = 22.dp + side, edge = insets.top + 20.dp, buttonSize = MenuButton,
            showButton = button.side != MenuSide.Hidden,
            extras = rememberSkinMenuExtras(context),
            atStart = button.side == MenuSide.Start,
            buttonTones = buttonTones,
        )
        if (customiseOpen) SkinCustomiseSheet(onDismiss = { customiseOpen = false })
        // over everything, the chevron too, so while the editor is up only the tiles answer a finger
        ModuleEditTiles(context, editor)
    }
}

/** everything inside drawn [factor] times mochy's own size: every dp and sp, so a layout made for one screen fits another */
@Composable
internal fun ScaledContent(factor: Float, content: @Composable () -> Unit) {
    if (factor == 1f) {
        content()
        return
    }
    val density = LocalDensity.current
    val scaled = remember(density, factor) { Density(density.density * factor, density.fontScale) }
    CompositionLocalProvider(LocalDensity provides scaled, content = content)
}

/** everything a module layout's blocks read, for the player and the mini player alike: the song, its clocks, the music, the lyrics, the colours and the settings */
@Composable
internal fun rememberSkinContext(
    style: ModuleStyle,
    actions: StyleActions,
    preview: Boolean,
    carousel: SongCarousel?,
    values: Map<String, String>,
    openMenu: () -> Unit,
    openCustomise: () -> Unit,
    editor: ModuleEditorState? = null,
): SkinContext {
    val skin = style.skin
    val layout = style.player
    val tones = rememberStyleTones()
    val track = rememberStyleTrack()
    val position = rememberLivePosition(animated = !preview)
    val lyricsPosition = position.withLyricsOffset()
    val duration = rememberDurationMs()
    // a layout without lyrics never fetches or parses any, unless one of its shaders reads the sung line
    val shaderLyrics = skin.playerShaders.any { file -> ActiveSkin.shader(skin.moduleId, file)?.declared?.any { it in LyricUniformNames } == true }
    val lyrics = if (layout.lyrics || shaderLyrics) rememberStyleLyrics(readings = !preview) else NoLyrics
    val lyricClock = rememberLyricClock(lyrics.lines, lyricsPosition)
    // a still layout never starts the clock at all
    val pulse = rememberSkinPulse(needed = remember(layout) { layout.moves }, preview = preview, always = layout.alwaysMoves)
    val live = rememberSkinLive(layout.flags, preview, lyricClock)
    val events = rememberSkinEvents(layout.events, pulse, preview, lyricClock)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // a frosted box blurs the player's own backdrop, captured only when one asks and never in the small preview
    val glass = if (layout.frosted && !preview) remember { GlassState() } else null
    val audio = rememberSkinAudio(needed = layout.audio, preview = preview)
    val queue = rememberSkinQueue(needed = layout.queue, preview = preview)
    val scale = layout.size.factor(LocalConfiguration.current.screenWidthDp.toFloat(), values)
    return SkinContext(
        style, skin, layout, tones, track, position, lyricsPosition, duration, carousel, lyrics, preview,
        cover = LocalArtworkColor.current, accent = MaterialTheme.colorScheme.primary,
        values = values, pulse = pulse, actions = actions,
        openMenu = openMenu, openCustomise = openCustomise,
        live = live, events = events, landscape = landscape, glass = glass, audio = audio, queue = queue,
        lyricClock = lyricClock, scale = scale, editor = editor,
    )
}

// the look's font on everything the module draws, when its font stays on the player instead of the whole app
@Composable
internal fun PlayerFont(moduleId: String, content: @Composable () -> Unit) {
    val family = ActiveSkin.playerFont(moduleId)
    if (family == null) {
        content()
        return
    }
    androidx.compose.material3.ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = family), content)
}

// the blocks top to bottom, scrolling when the module asks and they don't fit
@Composable
private fun SkinBody(context: SkinContext, insets: StyleInsets) {
    val layout = context.layout
    val scroll = if (layout.scroll && !context.preview) rememberScrollState() else null
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (scroll != null) Modifier.verticalScroll(scroll) else Modifier)
            // the screen's edges and the room for the chevron stay where they are however big the blocks are
            .padding(
                start = layout.padding + context.fixed(insets.start),
                end = layout.padding + context.fixed(insets.end),
                top = context.fixed(insets.top + TopRoom),
                bottom = context.fixed(insets.bottom + 24.dp),
            ),
        horizontalAlignment = layout.align.horizontal,
    ) {
        // a scrolling column has no bottom to grow into, so its grow spaces stay closed
        ColumnBlocks(layout.blocks, context, grow = !layout.scroll)
    }
}

// the header row sits level with the chevron and keeps clear of it, so words like [liked] can share the top line
@Composable
private fun SkinHeader(context: SkinContext, insets: StyleInsets) {
    val layout = context.layout
    if (layout.header.isEmpty()) return
    val side = layout.menuButton.side
    val clear = context.fixed(MenuButton + 22.dp + 10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = context.fixed(insets.start) + if (side == MenuSide.Start) clear else layout.padding,
                end = context.fixed(insets.end) + if (side == MenuSide.End) clear else layout.padding,
                top = context.fixed(insets.top + 20.dp),
            )
            .height(context.fixed(MenuButton)),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowBlocks(layout.header, context)
    }
}

// ---- layout ----------------------------------------------------------------------------------

@Composable
internal fun ColumnScope.ColumnBlocks(blocks: List<SkinBlock>, context: SkinContext, grow: Boolean = true) {
    blocks.forEach { block ->
        // a block whose setting says no takes no room at all, its spaces included
        if (!context.holds((block as? SkinBlock.Styled)?.condition)) return@forEach
        val plain = block.plain
        when {
            plain is SkinBlock.Space && plain.grow && !grow -> Spacer(Modifier.height(plain.size))
            plain is SkinBlock.Space && plain.grow -> Spacer(Modifier.weight(1f))
            plain is SkinBlock.Space -> Spacer(Modifier.height(plain.size))
            else -> Block(block, context, Modifier, inRow = false)
        }
    }
}

@Composable
internal fun RowScope.RowBlocks(blocks: List<SkinBlock>, context: SkinContext) {
    blocks.forEach { block ->
        if (!context.holds((block as? SkinBlock.Styled)?.condition)) return@forEach
        val plain = block.plain
        when {
            plain is SkinBlock.Space && plain.grow -> Spacer(Modifier.weight(1f))
            plain is SkinBlock.Space -> Spacer(Modifier.width(plain.size))
            // words, bars and groups share what's left of the row; covers, pictures and buttons keep their own size
            plain.stretches -> Block(block, context, Modifier.weight(1f), inRow = true)
            else -> Block(block, context, Modifier, inRow = true)
        }
    }
}

// a stack's blocks lie on top of each other, each placed by the stack's alignment and nudged by its own x and y
@Composable
private fun StackBlocks(block: SkinBlock.Stack, context: SkinContext, modifier: Modifier) {
    val align = when (block.align) {
        StackAlign.Center -> Alignment.Center
        StackAlign.Top -> Alignment.TopCenter
        StackAlign.Bottom -> Alignment.BottomCenter
        StackAlign.Start -> Alignment.CenterStart
        StackAlign.End -> Alignment.CenterEnd
    }
    Box(modifier.fillMaxWidth().then(block.height?.let { Modifier.height(it) } ?: Modifier), contentAlignment = align) {
        block.blocks.forEach { child ->
            if (!context.holds((child as? SkinBlock.Styled)?.condition) || child.plain is SkinBlock.Space) return@forEach
            Block(child, context, Modifier, inRow = false)
        }
    }
}

@Composable
private fun Block(block: SkinBlock, context: SkinContext, modifier: Modifier, inRow: Boolean) {
    when (block) {
        is SkinBlock.Styled -> {
            val motion = rememberBlockMotion(block, context)
            // measured before its own motion, so the editor's tile stays put while the block moves
            Block(block.inner, context, modifier.editPart(block.part, context.editor).skinStyled(block, context, motion), inRow)
        }
        is SkinBlock.Box -> SkinBox(block, context, modifier, inRow)
        is SkinBlock.Vector -> SkinVector(block, context, modifier)
        is SkinBlock.Stack -> StackBlocks(block, context, modifier)
        is SkinBlock.Visualizer -> SkinVisualizer(block, context, modifier)
        is SkinBlock.Button -> SkinButton(block, context, modifier)
        is SkinBlock.Shape -> SkinShape(block, context, modifier)
        is SkinBlock.Time -> SkinTime(block, context, modifier)
        is SkinBlock.Shader -> SkinShaderBlock(block, context, modifier)
        is SkinBlock.Surface -> SkinSurface(block, context, modifier)
        is SkinBlock.Cover -> SkinCover(block, context, modifier)
        is SkinBlock.Title -> SongText(block.text, context, modifier, inRow) { it.title }
        is SkinBlock.Artist -> SongText(block.text, context, modifier, inRow) { it.artist }
        is SkinBlock.Album -> SongText(block.text, context, modifier, inRow) { it.album }
        is SkinBlock.Label -> SkinLabel(block, context, modifier, inRow)
        is SkinBlock.Picture -> SkinPicture(block, context, modifier)
        is SkinBlock.Progress -> SkinProgress(block, context, modifier)
        is SkinBlock.Volume -> SkinVolume(block, context, modifier)
        is SkinBlock.Controls -> SkinControls(block, context, modifier)
        is SkinBlock.Lyrics -> SkinLyrics(block, context, modifier)
        is SkinBlock.BottomBar -> SkinBottomBar(block, context, modifier)
        is SkinBlock.QueueList -> SkinQueueList(block, context, modifier)
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

/** the cover in its shape and size, riding the row of songs, turning like a record while it plays when the layout asks, its video or animated cover over it */
@Composable
private fun SkinCover(block: SkinBlock.Cover, context: SkinContext, modifier: Modifier) {
    val shape = when (block.shape) {
        CoverShape.Circle -> CircleShape
        CoverShape.Square -> RectangleShape
        CoverShape.Rounded -> RoundedCornerShape(block.corner)
    }
    val turn = if (block.spin) rememberRecordTurn(context.preview, block.spinSpeed) else null
    Box(
        modifier = modifier
            .coverSize(block)
            // a turning cover redraws every frame, so it's kept to its own box and nothing else repaints with it
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
                contentScale = if (block.fit) ContentScale.Fit else ContentScale.Crop,
                modifier = pictureModifier.then(if (turn != null) Modifier.graphicsLayer { rotationZ = turn() } else Modifier),
            )
        }
        // a video plays every frame, so only its own box repaints with it
        if (block.media.isNotEmpty()) {
            SkinCoverMedia(
                media = block.media,
                context = context,
                fit = block.fit,
                rides = true,
                turn = turn,
                modifier = Modifier.matchParentSize().damageBoundary().clip(shape),
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

// fixed in dp when the layout gives a width or height, else its share of the width, always at its own ratio
private fun Modifier.coverSize(block: SkinBlock.Cover): Modifier {
    val fixedWidth = block.width
    val fixedHeight = block.height
    return when {
        fixedWidth != null && fixedHeight != null -> size(fixedWidth, fixedHeight)
        fixedWidth != null -> width(fixedWidth).aspectRatio(block.aspect)
        fixedHeight != null -> height(fixedHeight).aspectRatio(block.aspect, matchHeightConstraintsFirst = true)
        else -> fillMaxWidth(block.size).aspectRatio(block.aspect)
    }
}

/** the song's title, artist or album, riding the row of songs the way the other styles' words do */
@Composable
private fun SongText(text: SkinText, context: SkinContext, modifier: Modifier, inRow: Boolean, pick: (StyleTrack) -> String) {
    Box(modifier.then(if (inRow || text.wrap) Modifier else Modifier.fillMaxWidth())) {
        StyleSongs(context.carousel, context.track, NoLyrics) { song, slotModifier ->
            TextLine(pick(song.track), text, context, slotModifier.textWidth(text))
        }
    }
}

@Composable
internal fun TextLine(value: String, text: SkinText, context: SkinContext, modifier: Modifier) {
    Text(
        text = if (text.upper) value.uppercase() else value,
        color = context.color(text.color),
        fontSize = text.size,
        fontWeight = text.weight,
        maxLines = text.lines,
        overflow = TextOverflow.Ellipsis,
        textAlign = (text.align ?: context.layout.align).textAlign,
        letterSpacing = if (text.spacing != 0f) text.spacing.em else TextUnit.Unspecified,
        modifier = modifier,
    )
}

/** words fill their line, or hug themselves when the block asks to wrap */
internal fun Modifier.textWidth(text: SkinText): Modifier = if (text.wrap) this else fillMaxWidth()

/** the module's own words, with the song and its settings filled in; words naming the song ride the row of songs like the title */
@Composable
private fun SkinLabel(block: SkinBlock.Label, context: SkinContext, modifier: Modifier, inRow: Boolean) {
    val settled = if ("{$" in block.value) fillSettings(block.value, context.values) else block.value
    // the stream's words, the lyrics and the time are read here, so their changes only redraw this one label
    val heard = context.audio?.fill(settled) ?: settled
    val text = rememberPlayerFields(heard, context)
    if (!SongField.containsMatchIn(text)) {
        TextLine(text, block.text, context, modifier.textWidth(block.text))
        return
    }
    Box(modifier.then(if (inRow || block.text.wrap) Modifier else Modifier.fillMaxWidth())) {
        StyleSongs(context.carousel, context.track, NoLyrics) { song, slotModifier ->
            TextLine(fillSong(text, song.track), block.text, context, slotModifier.textWidth(block.text))
        }
    }
}

private fun fillSettings(text: String, values: Map<String, String>): String =
    SettingField.replace(text) { values[it.groupValues[1]].orEmpty() }

private fun fillSong(text: String, track: StyleTrack): String = text
    .replace("{title}", track.title, ignoreCase = true)
    .replace("{artist}", track.artist, ignoreCase = true)
    .replace("{album}", track.album, ignoreCase = true)

@Composable
private fun SkinPicture(block: SkinBlock.Picture, context: SkinContext, modifier: Modifier) {
    val model = when (val source = block.source) {
        is SkinPictureSource.Asset -> context.skin.resolve(source.path)
        SkinPictureSource.CurrentCover -> context.track.artwork ?: block.fallback?.let(context.skin::resolve)
        is SkinPictureSource.QueueCover -> context.queue?.itemAt(source.offset)?.artworkUri ?: block.fallback?.let(context.skin::resolve)
    }
    val box = modifier
        .then(block.width?.let { Modifier.width(it) } ?: Modifier.fillMaxWidth())
        .height(block.height)
        .clip(RoundedCornerShape(block.corner))
    if (model == null) {
        Box(box.background(context.tones.chip))
        return
    }
    val scale = if (block.fit) ContentScale.Fit else ContentScale.Crop
    val tint = block.tint?.let { ColorFilter.tint(context.color(it)) }
    if (block.source !is SkinPictureSource.Asset || !isMovingPicture(model)) {
        AsyncImage(model = model, contentDescription = null, contentScale = scale, colorFilter = tint, modifier = box)
        return
    }
    // the module's gif or webp: its first frame at once, the loop over it while the player is open
    Box(box) {
        AsyncImage(model = model, contentDescription = null, contentScale = scale, colorFilter = tint, modifier = Modifier.matchParentSize())
        MovingPictureOverlay(model, context, scale, tint, Modifier.matchParentSize())
    }
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
            val custom = block.custom
            if (song.lines.isNotEmpty() && custom != null) {
                SkinCustomLyrics(song.lines, custom, context, Modifier.fillMaxSize().then(slotModifier))
            } else if (song.lines.isNotEmpty() && block.flow) {
                SkinFlowLyrics(song.lines, block, context, Modifier.fillMaxSize().then(slotModifier))
            } else if (song.lines.isNotEmpty()) {
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

// ---- helpers ---------------------------------------------------------------------------------

/** the cover's turn at a record's 33⅓ rpm, only while the song plays and the style can be seen */
@Composable
private fun rememberRecordTurn(preview: Boolean, speed: Float): () -> Float {
    val angle = remember { mutableFloatStateOf(0f) }
    val connection = LocalPlayerConnection.current
    val showing = rememberStyleShowing()
    val obscured = LocalAppContentObscured.current
    LaunchedEffect(connection, preview, speed) {
        if (preview || connection == null) return@LaunchedEffect
        val playing = connection.uiState.transportState.map { it.isPlaying }.distinctUntilChanged()
        combine(playing, snapshotFlow { showing() && !obscured.value }) { on, shown -> on && shown }
            .distinctUntilChanged()
            .collectLatest { turning ->
                if (!turning) return@collectLatest
                var last = 0L
                while (true) {
                    withFrameNanos { now ->
                        if (last != 0L) angle.floatValue = (angle.floatValue + (now - last) / 1_000_000_000f * DegreesPerSecond * speed) % 360f
                        last = now
                    }
                }
            }
    }
    return remember { { angle.floatValue } }
}

/** the block under any styling, for deciding how it's laid out */
private val SkinBlock.plain: SkinBlock get() = if (this is SkinBlock.Styled) inner.plain else this

private val SkinBlock.stretches: Boolean
    get() = (this is SkinBlock.Title && !text.wrap) || (this is SkinBlock.Artist && !text.wrap) || (this is SkinBlock.Album && !text.wrap) ||
        (this is SkinBlock.Label && !text.wrap) || (this is SkinBlock.Time && !text.wrap) ||
        this is SkinBlock.Progress || this is SkinBlock.Volume || this is SkinBlock.Lyrics || this is SkinBlock.Column || this is SkinBlock.Stack ||
        this is SkinBlock.Visualizer || (this is SkinBlock.Shape && width == null) ||
        (this is SkinBlock.Shader && width == null) || (this is SkinBlock.Surface && width == null) ||
        (this is SkinBlock.Box && width == null) || this is SkinBlock.BottomBar || this is SkinBlock.QueueList

internal val SkinAlign.horizontal: Alignment.Horizontal
    get() = when (this) {
        SkinAlign.Start -> Alignment.Start
        SkinAlign.Center -> Alignment.CenterHorizontally
        SkinAlign.End -> Alignment.End
    }

internal val SkinAlign.textAlign: TextAlign
    get() = when (this) {
        SkinAlign.Start -> TextAlign.Start
        SkinAlign.Center -> TextAlign.Center
        SkinAlign.End -> TextAlign.End
    }

private val NoLyrics = StyleLyrics(null, emptyList())
// the uniforms that need the lyrics loaded even when no block shows them
private val LyricUniformNames = setOf("line", "lineCount", "lineProgress", "wordProgress", "singing")
// android's regex engine refuses a bare closing brace, so both are escaped or the player crashes on open
private val SongField = Regex("""\{(title|artist|album)\}""", RegexOption.IGNORE_CASE)
private val SettingField = Regex("""\{\$([A-Za-z0-9_.-]{1,60})\}""")
// room at the top for the player's handle and the ••• button
private val TopRoom = 64.dp
private val MenuButton = 36.dp
// 33⅓ turns a minute
private const val DegreesPerSecond = 200f
