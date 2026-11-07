package com.mochyy.mochymusic.ui.player.flagship

import android.graphics.Shader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
    val queueMounted by remember(pages) {
        derivedStateOf { pages.queueProgress > 0.001f || pages.preparing == PlayerPage.QUEUE }
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
                    val from = coverWidth.toPx() * (1f - CoverFadeShare)
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            1f to PanelShade,
                            startX = from,
                            endX = coverWidth.toPx(),
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(from, 0f),
                    )
                    drawRect(PanelShade, topLeft = androidx.compose.ui.geometry.Offset(coverWidth.toPx(), 0f))
                },
        )

        LandscapeCover(
            coverWidth = coverWidth,
            thumbnailUrl = track.thumbnailUrl,
            interactive = interactive,
            lyricsProgress = lyricsProgress,
        )

        // with the lyrics up: the title, the artist and the seek bar over the soft bottom of the cover
        if (lyricsMounted) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(coverWidth)
                    .padding(start = CoverGutter, end = CoverGutter + ButtonSize * 2 + ButtonGap, bottom = bottomInset + 14.dp)
                    .graphicsLayer {
                        val p = lyricsProgress()
                        alpha = p
                        translationY = (1f - p) * 24.dp.toPx()
                    },
            ) {
                SongTitles(title = track.title, artist = track.artist, onClick = onSongInfo)
            }
        }

        CoverButtons(
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
            transport(Modifier.padding(vertical = 6.dp))
            bottom(Modifier.padding(horizontal = 8.dp))
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
) {
    val connection = LocalPlayerConnection.current
    val carousel = LocalSongCarousel.current
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
                coverLayer.record { this@drawWithContent.drawContent() }
                drawLayer(coverLayer)
                val from = size.width * (1f - CoverFadeShare)
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = from, endX = size.width),
                    topLeft = androidx.compose.ui.geometry.Offset(from, 0f),
                    blendMode = BlendMode.DstOut,
                )
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
    if (bandShown) {
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
