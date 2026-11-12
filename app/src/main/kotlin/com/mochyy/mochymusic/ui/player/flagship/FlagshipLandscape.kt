package com.mochyy.mochymusic.ui.player.flagship

import android.graphics.Shader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.BlurQuality
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import com.mochyy.mochymusic.ui.component.DownscaledBlur
import com.mochyy.mochymusic.ui.component.LocalBlurQuality
import com.mochyy.mochymusic.ui.player.CarouselCrossfade
import com.mochyy.mochymusic.ui.player.CarouselPictures
import com.mochyy.mochymusic.ui.player.LikeBurstState
import com.mochyy.mochymusic.ui.player.LikeSlot
import com.mochyy.mochymusic.ui.player.LocalSongCarousel
import com.mochyy.mochymusic.ui.player.LyricsScreen
import com.mochyy.mochymusic.ui.player.PlayerPage
import com.mochyy.mochymusic.ui.player.PlayerPageTransition
import com.mochyy.mochymusic.ui.player.PlayerProgressSlider
import com.mochyy.mochymusic.ui.player.PressScaleActionButton
import com.mochyy.mochymusic.ui.player.QueueScreen
import com.mochyy.mochymusic.ui.player.carouselText
import com.mochyy.mochymusic.ui.player.likeButtonTarget
import com.mochyy.mochymusic.ui.player.rememberTextSlots
import com.mochyy.mochymusic.ui.player.styles.styleSongSwipe
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize

// the cover takes this much of the width beside the controls, and is at most this much wider than it is tall, like the concept
private const val CoverWidthShare = 0.55f
private const val CoverAspect = 1.2f

// the last part of the cover fades into its blurred copy behind, so it melts into the panel instead of ending in an edge
private const val CoverFadeShare = 0.16f

// a tv is watched from across the room: the cover melts away over much more of its width, and at the bottom too
private const val TvCoverFadeShare = 0.45f
private const val TvCoverFadeDownFrom = 0.62f
// the tv's title sits this far in from the cover's left and bottom edges, like the rest of the tv app
private val TvTitleInset = 48.dp

// with the lyrics up, the bottom of the cover goes soft under the title and the seek bar, the blur growing toward the edge
private const val LyricsBandShare = 0.46f
private const val LyricsBandRadius = 30f

// the panel beside the cover sits a little darker than the blurred cover, so white text always reads over it
private val PanelShade = Color.Black.copy(alpha = 0.3f)

private val PanelGutter = 26.dp
private val CoverGutter = 22.dp

// the like and the menu chevron, stacked at the right edge
private val ButtonSize = 34.dp
private val ButtonGap = 10.dp

// what runs down the left of the player on its side
private enum class CoverLook { SQUARE, FULL, RECORD, TV_SQUARE }

// the rounded square: this share of the height, never more than this share of the width
private const val SquareHeightShare = 0.74f
private const val SquareWidthShare = 0.42f
private val SquareShape = RoundedCornerShape(22.dp)

// the record is laid out by its box's width and runs 1.406 of it tall, so this box fits the record to the height
private const val RecordBoxPerHeight = 1f / 1.406f

// the gap between the square (or the record) and the panel beside it
private val PanelGap = 40.dp

// the like and the chevron stand at the right edge, stacked, this much room kept for them
private val SideButtonsRoom = ButtonSize + 30.dp

// with the square or the record the backdrop is one even shade, so the white words read anywhere on it
private val EvenShade = Color.Black.copy(alpha = 0.38f)

// the landscape flagship: a rounded square (or the record, or the cover melting in from the left) beside the title, the seek bar,
// the buttons and the card deck; the lyrics and the queue open in the panel, the like and the chevron stand at the right edge
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlagshipLandscape(
    pages: PlayerPageTransition,
    // the controls follow the playing position only while the player shows
    sheetShowing: Boolean,
    // whether a touch on the player is the player's yet (it's still opening below that)
    interactive: () -> Boolean,
    // the sheet sits all the way open, so nothing on it is moving with the open or the close
    expanded: () -> Boolean,
    likeBurst: LikeBurstState,
    onChevron: () -> Unit,
    onChevronPlaced: (Rect) -> Unit,
    onSongInfo: () -> Unit,
    onSwitchLyricsSource: () -> Unit,
    // the style's own play buttons
    transport: @Composable (Modifier) -> Unit,
    // what sits under the play buttons: the volume and its pills, or the player's card deck
    bottom: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    // the Turntable: its record runs in from the left edge the way the poster's circle does, its arm still the seek bar
    turntable: Boolean = false,
) {
    val connection = LocalPlayerConnection.current ?: return
    val track by connection.uiState.trackInfo.collectAsState()
    val carousel = LocalSongCarousel.current
    // on a tv the remote does the playing; the player is the cover and its lyrics, no buttons and no queue
    val onTv = com.mochyy.mochymusic.ui.utils.isTvLayout()
    val fadeShare = if (onTv) TvCoverFadeShare else CoverFadeShare
    // the tv's "flagship square": the whole cover, square and rounded, instead of one melting down the side
    val tvSquare by com.mochyy.mochymusic.utils.rememberPreference(com.mochyy.mochymusic.constants.TvFlagshipSquareKey, defaultValue = false)
    // set in Settings > Customise > Player: the cover melting in from the left instead of the rounded square
    val fullCover by com.mochyy.mochymusic.utils.rememberPreference(com.mochyy.mochymusic.constants.LandscapeFullCoverKey, defaultValue = false)
    val look = when {
        onTv -> if (tvSquare) CoverLook.TV_SQUARE else CoverLook.FULL
        turntable -> CoverLook.RECORD
        fullCover -> CoverLook.FULL
        else -> CoverLook.SQUARE
    }
    // beside the square or the record the seek bar stays over the buttons; with the full cover it moves onto the cover under the lyrics
    val seekInPanel = !onTv && look != CoverLook.FULL
    // the tv's control bar takes over the title: the cover's own one glides down into it and fades
    val handedToBar = androidx.compose.animation.core.animateFloatAsState(
        if (onTv && com.mochyy.mochymusic.ui.player.TvPlayerRemote.controlsShown) 1f else 0f,
        androidx.compose.animation.core.tween(420),
        label = "handedToBar",
    )
    val direction = LocalLayoutDirection.current
    val sides = WindowInsets.navigationBarsIgnoringVisibility.union(WindowInsets.displayCutout).asPaddingValues()
    val startInset = sides.calculateStartPadding(direction)
    val endInset = sides.calculateEndPadding(direction)
    val bottomInset = sides.calculateBottomPadding()

    val lyricsProgress = remember(pages) { { pages.lyricsProgress.coerceIn(0f, 1f) } }
    val queueProgress = remember(pages) { { pages.queueProgress.coerceIn(0f, 1f) } }
    // each page is built only while it shows, or just before it opens
    val lyricsMounted by remember(pages) {
        derivedStateOf { pages.lyricsProgress > 0.001f || pages.preparing == PlayerPage.LYRICS }
    }
    val queueMounted by remember(pages, onTv) {
        derivedStateOf { !onTv && (pages.queueProgress > 0.001f || pages.preparing == PlayerPage.QUEUE) }
    }
    // the panel's title (and seek bar, beside the full cover), until a page has taken their place
    val panelInfoShown by remember(pages) { derivedStateOf { pages.lyricsProgress < 0.999f && pages.queueProgress < 0.999f } }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val squareSide = minOf(maxHeight * SquareHeightShare, maxWidth * SquareWidthShare)
        // as far in from the left as it is from the top, so the square sits in an even frame
        val squareStart = startInset + (maxHeight - squareSide) / 2
        val recordBox = maxHeight * RecordBoxPerHeight
        // where the panel starts: the full cover's right edge, or a gap past the square or the record
        val coverWidth = when (look) {
            CoverLook.FULL, CoverLook.TV_SQUARE -> minOf(maxHeight * CoverAspect, maxWidth * CoverWidthShare)
            CoverLook.SQUARE -> squareStart + squareSide + PanelGap
            CoverLook.RECORD -> startInset + recordBox + PanelGap / 2
        }

        // the playing song's cover, blurred once and laid over the whole player; it crossfades along the row as songs slide
        CarouselCrossfade(carousel = carousel, fallbackUrl = track.thumbnailUrl, modifier = Modifier.fillMaxSize()) { url ->
            BlurredCoverPicture(url = url, modifier = Modifier.fillMaxSize())
        }
        val evenShade = look == CoverLook.SQUARE || look == CoverLook.RECORD
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    if (evenShade) {
                        drawRect(EvenShade)
                        return@drawWithContent
                    }
                    val from = coverWidth.toPx() * (1f - fadeShare)
                    // one ramp across the whole width that holds the shade past the cover; a second flat rect on top doubled it into a hard line
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to PanelShade.copy(alpha = PanelShade.alpha * 0.4f),
                            1f to PanelShade,
                            startX = from,
                            endX = coverWidth.toPx(),
                        ),
                    )
                },
        )

        when (look) {
            CoverLook.SQUARE -> PhoneSquareCover(side = squareSide, start = squareStart, thumbnailUrl = track.thumbnailUrl, interactive = interactive)
            CoverLook.RECORD -> LandscapeRecord(width = recordBox, start = startInset, thumbnailUrl = track.thumbnailUrl, mediaId = track.mediaId, interactive = interactive, expanded = expanded, sheetShowing = sheetShowing)
            CoverLook.FULL, CoverLook.TV_SQUARE -> LandscapeCover(
                coverWidth = coverWidth,
                thumbnailUrl = track.thumbnailUrl,
                interactive = interactive,
                lyricsProgress = lyricsProgress,
                fadeShare = fadeShare,
                fadeDownFrom = if (onTv && look == CoverLook.FULL) TvCoverFadeDownFrom else null,
                square = look == CoverLook.TV_SQUARE,
                band = !onTv,
            )
        }
        if (onTv) {
            // a soft dark floor across the bottom, so the title always reads and the cover and lyrics sink into it
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                0.5f to Color.Transparent,
                                0.75f to Color.Black.copy(alpha = 0.45f),
                                1f to Color.Black.copy(alpha = 0.85f),
                            ),
                        )
                    },
            )
        }

        // beside the full cover, with the lyrics up: the title, the artist and the seek bar over the soft bottom of the cover
        if (lyricsMounted && !seekInPanel) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(coverWidth)
                    // on a tv the title stands alone at the cover's foot, lined up with the tv's own margins
                    .padding(
                        start = if (onTv) TvTitleInset else CoverGutter,
                        end = if (onTv) TvTitleInset else CoverGutter,
                        bottom = bottomInset + if (onTv) TvTitleInset else 66.dp,
                    )
                    .graphicsLayer {
                        val p = lyricsProgress()
                        val handed = handedToBar.value
                        alpha = p * (1f - handed)
                        translationY = (1f - p) * 24.dp.toPx() + handed * 30.dp.toPx()
                    },
            ) {
                SongTitles(title = track.title, artist = track.artist, onClick = onSongInfo)
            }
        }
        if (lyricsMounted && !onTv && !seekInPanel) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(coverWidth)
                    .padding(start = CoverGutter - 8.dp, end = CoverGutter - 8.dp, bottom = bottomInset)
                    .graphicsLayer { alpha = lyricsProgress() },
            ) {
                Column { PlayerProgressSlider(active = sheetShowing) }
            }
        }

        if (!onTv) CoverButtons(
            liked = track.liked,
            likeBurst = likeBurst,
            onChevron = onChevron,
            onChevronPlaced = onChevronPlaced,
            settled = expanded,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = endInset + 16.dp, bottom = bottomInset + 18.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = coverWidth, end = endInset + if (onTv) 0.dp else SideButtonsRoom)
                .padding(bottom = bottomInset),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (panelInfoShown) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // beside the square the title sits in the middle of the room over the seek bar, as the concept has it
                            .then(if (seekInPanel) Modifier.align(Alignment.CenterStart).padding(top = 18.dp) else Modifier.padding(top = 28.dp))
                            .graphicsLayer {
                                val p = maxOf(lyricsProgress(), queueProgress())
                                alpha = 1f - p
                                translationY = -p * 20.dp.toPx()
                            },
                    ) {
                        Box(modifier = Modifier.padding(horizontal = PanelGutter)) {
                            SongTitles(title = track.title, artist = track.artist, onClick = onSongInfo)
                        }
                        if (!seekInPanel) {
                            Spacer(Modifier.height(14.dp))
                            Column(modifier = Modifier.padding(horizontal = PanelGutter - 12.dp)) {
                                PlayerProgressSlider(active = sheetShowing && panelInfoShown)
                            }
                        }
                    }
                }
                if (lyricsMounted) {
                    LyricsScreen(
                        onClose = { pages.close() },
                        screenHeight = screenHeight,
                        contentBottomInset = 0.dp,
                        onSwitchSource = onSwitchLyricsSource,
                        showHeader = false,
                        modifier = Modifier
                            .fillMaxSize()
                            // clear of the handle at the top
                            .padding(top = if (onTv) 0.dp else 22.dp)
                            .graphicsLayer {
                                val p = lyricsProgress()
                                alpha = p
                                translationY = (1f - p) * 40.dp.toPx()
                            },
                    )
                }
                if (queueMounted) {
                    QueueScreen(
                        onClose = { pages.close() },
                        contentBottomInset = 0.dp,
                        controlsClearance = 0.dp,
                        likeBurst = likeBurst,
                        showHeader = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 26.dp)
                            .graphicsLayer {
                                val p = queueProgress()
                                alpha = p
                                translationY = (1f - p) * 40.dp.toPx()
                            },
                    )
                }
            }
            // beside the square or the record the seek bar stays put while the lyrics or the queue are open
            if (seekInPanel) {
                Column(modifier = Modifier.padding(horizontal = PanelGutter - 12.dp)) {
                    PlayerProgressSlider(active = sheetShowing)
                }
            }
            if (!onTv) {
                transport(Modifier.padding(vertical = 6.dp))
                bottom(Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

// the phone's rounded square, a little shadow under it, sliding along the row of songs under a sideways swipe
@Composable
private fun BoxScope.PhoneSquareCover(side: Dp, start: Dp, thumbnailUrl: String?, interactive: () -> Boolean) {
    val connection = LocalPlayerConnection.current
    val carousel = LocalSongCarousel.current
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .padding(start = start)
            .size(side)
            .shadow(24.dp, SquareShape)
            .clip(SquareShape)
            .styleSongSwipe(interactive, carousel) { connection?.player },
    ) {
        CarouselPictures(carousel = carousel, fallbackUrl = thumbnailUrl, modifier = Modifier.fillMaxSize()) { url, pictureModifier ->
            AsyncImage(
                model = url?.resize(900, 900),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = pictureModifier,
            )
        }
    }
}

// the Turntable on its side: the record, as tall as the screen, runs in from the left edge with its arm at its top right
@Composable
private fun BoxScope.LandscapeRecord(
    width: Dp,
    start: Dp,
    thumbnailUrl: String?,
    mediaId: String,
    interactive: () -> Boolean,
    expanded: () -> Boolean,
    sheetShowing: Boolean,
) {
    val connection = LocalPlayerConnection.current ?: return
    val carousel = LocalSongCarousel.current
    val transport by connection.uiState.transportState.collectAsState()
    // it only spins while the player is all the way open, the arm settles first
    val open by remember { derivedStateOf { expanded() } }
    com.mochyy.mochymusic.ui.player.customize.DiscCoverStack(
        style = com.mochyy.mochymusic.constants.PlayerCoverStyle.TURNTABLE,
        artworkUrl = thumbnailUrl,
        mediaId = mediaId,
        isPlaying = transport.isPlaying,
        spinActive = open && sheetShowing,
        editMode = false,
        carousel = carousel,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = start)
            .width(width)
            .fillMaxHeight()
            .styleSongSwipe(interactive, carousel) { connection.player },
    )
}

// the cover down the left, sliding along the row under a sideways swipe, its right edge melting into the blurred copy behind
@Composable
private fun BoxScope.LandscapeCover(
    coverWidth: Dp,
    thumbnailUrl: String?,
    interactive: () -> Boolean,
    lyricsProgress: () -> Float,
    fadeShare: Float,
    // where the cover starts fading towards its bottom, as a share of its height; null keeps the bottom whole
    fadeDownFrom: Float?,
    square: Boolean = false,
    // the blurred copy of the cover's foot under the title; a tv lays a dark floor there instead, a bright cover turned it white
    band: Boolean = true,
) {
    val connection = LocalPlayerConnection.current
    val carousel = LocalSongCarousel.current
    if (square) {
        SquareCover(coverWidth = coverWidth, thumbnailUrl = thumbnailUrl, interactive = interactive)
        return
    }
    // the cover is recorded once a frame it changes and drawn again, blurred, into the band under the lyrics' title
    val coverLayer = rememberGraphicsLayer()
    val bandShown by remember { derivedStateOf { lyricsProgress() > 0.001f } }
    Box(
        modifier = Modifier
            .width(coverWidth)
            .fillMaxHeight()
            .styleSongSwipe(interactive, carousel) { connection?.player }
            // its own layer, so the fade can cut the edge away without touching the backdrop behind
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                if (band) {
                    coverLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(coverLayer)
                } else {
                    drawContent()
                }
                val from = size.width * (1f - fadeShare)
                // eased in and out, so the edge has no line where the fade starts or ends, straight onto the
                // blurred backdrop behind it - a blurred band here only drew a second edge where its own blur
                // met the backdrop's different one
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.3f to Color.Black.copy(alpha = 0.1f),
                        0.65f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black,
                        startX = from,
                        endX = size.width,
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(from, 0f),
                    blendMode = BlendMode.DstOut,
                )
                if (fadeDownFrom != null) {
                    val top = size.height * fadeDownFrom
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color.Black.copy(alpha = 0.15f),
                            0.7f to Color.Black.copy(alpha = 0.6f),
                            1f to Color.Black,
                            startY = top,
                            endY = size.height,
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                        blendMode = BlendMode.DstOut,
                    )
                }
            },
    ) {
        CarouselPictures(carousel = carousel, fallbackUrl = thumbnailUrl, modifier = Modifier.fillMaxSize()) { url, pictureModifier ->
            AsyncImage(
                model = url?.resize(1200, 1200),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = pictureModifier,
            )
        }
    }
    if (bandShown && band) {
        LyricsBand(coverWidth = coverWidth, coverLayer = coverLayer, lyricsProgress = lyricsProgress)
    }
}

// the soft bottom of the cover under the lyrics' title: one pass whose blur grows toward the edge on high quality, a masked fixed blur below that
@Composable
private fun BoxScope.LyricsBand(
    coverWidth: Dp,
    coverLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    lyricsProgress: () -> Float,
) {
    val mask = remember {
        Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Black.copy(alpha = 0.85f), 1f to Color.Black)
    }
    val smooth = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
        LocalBlurQuality.current == BlurQuality.HIGH
    DownscaledBlur(
        radius = { BlurEffectCache.effectiveRadius(LyricsBandRadius) },
        tileMode = Shader.TileMode.CLAMP,
        mask = mask,
        growDown = if (smooth) true else null,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .width(coverWidth)
            .fillMaxHeight(LyricsBandShare)
            .graphicsLayer { alpha = lyricsProgress() },
    ) { fullSize ->
        translate(top = fullSize.height - coverLayer.size.height.toFloat()) { drawLayer(coverLayer) }
    }
}

// the title and the artist, riding the row of songs as it slides; a tap opens the song's details
@Composable
private fun SongTitles(title: String, artist: String, onClick: () -> Unit) {
    val carousel = LocalSongCarousel.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        if (carousel == null) {
            TitleLines(title, artist, Modifier)
        } else {
            for (slot in rememberTextSlots(carousel)) {
                androidx.compose.runtime.key(slot.uid) {
                    val playing = slot.step == carousel.step
                    TitleLines(
                        title = slot.title.ifEmpty { if (playing) title else "" },
                        artist = slot.artist.ifEmpty { if (playing) artist else "" },
                        modifier = Modifier.carouselText(carousel, slot),
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleLines(title: String, artist: String, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artist,
            color = Color.White.copy(alpha = 0.7f),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// the like over the menu chevron, stacked at the right edge
@Composable
private fun CoverButtons(
    liked: Boolean,
    likeBurst: LikeBurstState,
    onChevron: () -> Unit,
    onChevronPlaced: (Rect) -> Unit,
    // the chevron's spot is only handed on once the player holds still, not on every frame of the open
    settled: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current ?: return
    Column(
        verticalArrangement = Arrangement.spacedBy(ButtonGap),
        modifier = modifier,
    ) {
        PressScaleActionButton(
            icon = if (liked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
            tint = Color.White,
            containerColor = Color.White.copy(alpha = if (liked) 0.3f else 0.18f),
            size = ButtonSize,
            onClick = { likeBurst.likeFromButton(LikeSlot.PLAYER, liked, connection::toggleLike) },
            modifier = Modifier.likeButtonTarget(likeBurst, LikeSlot.PLAYER),
        )
        PressScaleActionButton(
            icon = R.drawable.expand_less,
            tint = Color.White,
            containerColor = Color.White.copy(alpha = 0.18f),
            size = ButtonSize,
            onClick = onChevron,
            modifier = Modifier.onGloballyPositioned { if (settled()) onChevronPlaced(it.boundsInRoot()) },
        )
    }
}

// the whole cover as a rounded square over the blurred copy, a little above the middle so the title fits under it
@Composable
private fun BoxScope.SquareCover(coverWidth: Dp, thumbnailUrl: String?, interactive: () -> Boolean) {
    val connection = LocalPlayerConnection.current
    val carousel = LocalSongCarousel.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.width(coverWidth).fillMaxHeight()) {
        val side = minOf(maxWidth * 0.74f, maxHeight * 0.62f)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = TvTitleInset, bottom = 70.dp)
                .size(side)
                .shadow(28.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .styleSongSwipe(interactive, carousel) { connection?.player },
        ) {
            CarouselPictures(carousel = carousel, fallbackUrl = thumbnailUrl, modifier = Modifier.fillMaxSize()) { url, pictureModifier ->
                AsyncImage(
                    model = url?.resize(900, 900),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = pictureModifier,
                )
            }
        }
    }
}
