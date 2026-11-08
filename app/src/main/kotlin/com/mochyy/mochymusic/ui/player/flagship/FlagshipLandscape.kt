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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

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

// the like and the menu chevron, at the cover's bottom right; with the lyrics up they rise beside the title
private val ButtonSize = 34.dp
private val ButtonGap = 10.dp

// the landscape flagship, laid out from the concept: the cover down the left melting into its blurred copy, the controls on the right; the lyrics and the queue open in the right panel, and with the lyrics up the title and the seek bar move onto the cover
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
) {
    val connection = LocalPlayerConnection.current ?: return
    val track by connection.uiState.trackInfo.collectAsState()
    val carousel = LocalSongCarousel.current
    // on a tv the remote does the playing; the player is the cover and its lyrics, no buttons and no queue
    val onTv = com.mochyy.mochymusic.ui.utils.isTvLayout()
    val fadeShare = if (onTv) TvCoverFadeShare else CoverFadeShare
    // the tv's "flagship square": the whole cover, square and rounded, instead of one melting down the side
    val squareCover by com.mochyy.mochymusic.utils.rememberPreference(com.mochyy.mochymusic.constants.TvFlagshipSquareKey, defaultValue = false)
    // the tv's control bar takes over the title: the cover's own one glides down into it and fades
    val handedToBar = androidx.compose.animation.core.animateFloatAsState(
        if (onTv && com.mochyy.mochymusic.ui.player.TvPlayerRemote.controlsShown) 1f else 0f,
        androidx.compose.animation.core.tween(420),
        label = "handedToBar",
    )
    val direction = LocalLayoutDirection.current
    val sides = WindowInsets.navigationBarsIgnoringVisibility.union(WindowInsets.displayCutout).asPaddingValues()
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
    // the right panel's title and seek bar, until the lyrics have taken their place
    val panelInfoShown by remember(pages) { derivedStateOf { pages.lyricsProgress < 0.999f && pages.queueProgress < 0.999f } }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val coverWidth = minOf(maxHeight * CoverAspect, maxWidth * CoverWidthShare)
        val screenHeight = maxHeight

        // the playing song's cover, blurred once and laid over the whole player; it crossfades along the row as songs slide
        CarouselCrossfade(carousel = carousel, fallbackUrl = track.thumbnailUrl, modifier = Modifier.fillMaxSize()) { url ->
            BlurredCoverPicture(url = url, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
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

        LandscapeCover(
            coverWidth = coverWidth,
            thumbnailUrl = track.thumbnailUrl,
            interactive = interactive,
            lyricsProgress = lyricsProgress,
            fadeShare = fadeShare,
            fadeDownFrom = if (onTv && !squareCover) TvCoverFadeDownFrom else null,
            square = onTv && squareCover,
            band = !onTv,
            onTv = onTv,
        )
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

        // with the lyrics up: the title, the artist and the seek bar over the soft bottom of the cover
        if (lyricsMounted) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(coverWidth)
                    // on a tv the title stands alone at the cover's foot, lined up with the tv's own margins
                    .padding(
                        start = if (onTv) TvTitleInset else CoverGutter,
                        end = if (onTv) TvTitleInset else CoverGutter + ButtonSize * 2 + ButtonGap,
                        bottom = bottomInset + if (onTv) TvTitleInset else 14.dp,
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

        if (!onTv) CoverButtons(
            liked = track.liked,
            likeBurst = likeBurst,
            onChevron = onChevron,
            onChevronPlaced = onChevronPlaced,
            settled = { val p = lyricsProgress(); expanded() && (p <= 0f || p >= 1f) },
            // up beside the title once the lyrics are open, clear of the seek bar under it
            lift = { (lyricsProgress() * 52.dp.toPx()).roundToInt() },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = (coverWidth - CoverGutter - ButtonSize * 2 - ButtonGap).coerceAtLeast(0.dp), bottom = bottomInset + 18.dp),
        )

        if (lyricsMounted) {
            // lined up under the title: the phone's own gutter, or the tv's wider margin
            val sidePad = if (onTv) TvTitleInset - 8.dp else CoverGutter - 8.dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(coverWidth)
                    .padding(start = sidePad, end = sidePad, bottom = bottomInset)
                    .graphicsLayer { alpha = lyricsProgress() * (1f - handedToBar.value) },
            ) {
                Column { PlayerProgressSlider(active = sheetShowing) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = coverWidth, end = endInset)
                .padding(bottom = bottomInset),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (panelInfoShown) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp)
                            .graphicsLayer {
                                val p = maxOf(lyricsProgress(), queueProgress())
                                alpha = 1f - p
                                translationY = -p * 20.dp.toPx()
                            },
                    ) {
                        Box(modifier = Modifier.padding(horizontal = PanelGutter)) {
                            SongTitles(title = track.title, artist = track.artist, onClick = onSongInfo)
                        }
                        Spacer(Modifier.height(14.dp))
                        Column(modifier = Modifier.padding(horizontal = PanelGutter - 12.dp)) {
                            PlayerProgressSlider(active = sheetShowing && panelInfoShown)
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
                            .padding(top = 16.dp)
                            .graphicsLayer {
                                val p = queueProgress()
                                alpha = p
                                translationY = (1f - p) * 40.dp.toPx()
                            },
                    )
                }
            }
            if (!onTv) {
                transport(Modifier.padding(vertical = 6.dp))
                bottom(Modifier.padding(horizontal = 8.dp))
            }
        }
    }
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
    // no touch to drag the edge on a tv, so it melts away blurred instead of the flat alpha fade phones get
    onTv: Boolean = false,
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
                // recorded every time regardless of band, so the tv's edge blur below always has this frame's picture to read
                coverLayer.record { this@drawWithContent.drawContent() }
                drawLayer(coverLayer)
                // on a tv the edge melts away blurred instead (see CoverEdgeBlur); a flat alpha fade there left a hard cut
                // where the crisp cover met its blurred backdrop, since nothing bridged the two
                if (!onTv) {
                    val from = size.width * (1f - fadeShare)
                    // eased in and out, so the edge has no line where the fade starts or ends
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
                }
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
    if (onTv) {
        CoverEdgeBlur(coverWidth = coverWidth, fadeShare = fadeShare, coverLayer = coverLayer)
    }
    if (bandShown && band) {
        LyricsBand(coverWidth = coverWidth, coverLayer = coverLayer, lyricsProgress = lyricsProgress)
    }
}

// the tv has no finger to drag a cutaway edge, so the last part of the cover blurs away instead of just fading flat;
// the blur grows into the edge the same way the lyrics band grows into the bottom, so nothing meets a hard line
private const val TvEdgeBlurRadius = 46f

@Composable
private fun BoxScope.CoverEdgeBlur(
    coverWidth: Dp,
    fadeShare: Float,
    coverLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
) {
    val mask = remember {
        Brush.horizontalGradient(0f to Color.Black, 0.7f to Color.Black, 1f to Color.Transparent)
    }
    val smooth = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
        LocalBlurQuality.current == BlurQuality.HIGH
    val from = coverWidth * (1f - fadeShare)
    DownscaledBlur(
        radius = { BlurEffectCache.effectiveRadius(TvEdgeBlurRadius) },
        tileMode = Shader.TileMode.CLAMP,
        mask = mask,
        growDown = if (smooth) true else null,
        growAcross = true,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = from)
            .width(coverWidth - from)
            .fillMaxHeight(),
    ) { _ ->
        translate(left = -from.toPx()) { drawLayer(coverLayer) }
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

// the like and the menu chevron, side by side; placed by an offset, so their rise never recomposes them
@Composable
private fun CoverButtons(
    liked: Boolean,
    likeBurst: LikeBurstState,
    onChevron: () -> Unit,
    onChevronPlaced: (Rect) -> Unit,
    // the chevron's spot is only handed on when it's holding still, not on every frame of its rise
    settled: () -> Boolean,
    lift: androidx.compose.ui.unit.Density.() -> Int,
    modifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current ?: return
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGap),
        modifier = modifier.offset { IntOffset(0, -lift()) },
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
