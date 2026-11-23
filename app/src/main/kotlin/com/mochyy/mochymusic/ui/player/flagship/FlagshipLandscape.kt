// flagship player laid out sideways: square or full edge-to-edge cover from the editor, same ambient backdrop, zero overlapping controls

package com.mochyy.mochymusic.ui.player.flagship

import android.graphics.Bitmap
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.PorterDuff
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size as CoilSize
import coil3.toBitmap
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.BlurQuality
import com.mochyy.mochymusic.constants.BlurStrengthPxKey
import com.mochyy.mochymusic.constants.PlayerBackgroundStyle
import com.mochyy.mochymusic.constants.PlayerCoverStyle
import com.mochyy.mochymusic.ui.component.BlurEffectCache
import com.mochyy.mochymusic.ui.component.DownscaledBlur
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.LocalBlurQuality
import com.mochyy.mochymusic.ui.component.drawGlassNode
import com.mochyy.mochymusic.ui.component.glassNodeHasContent
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.component.rememberCoverBlurAvailable
import com.mochyy.mochymusic.ui.component.rememberProgressiveBlur
import com.mochyy.mochymusic.ui.player.BackdropBlurTransformation
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
import com.mochyy.mochymusic.ui.player.ReflectionBlurPx
import com.mochyy.mochymusic.ui.player.ReflectionPx
import com.mochyy.mochymusic.ui.player.carouselText
import com.mochyy.mochymusic.ui.player.customize.CoverGradientBackdrop
import com.mochyy.mochymusic.ui.player.customize.DiscCoverStack
import com.mochyy.mochymusic.ui.player.customize.PlayerBackgroundContent
import com.mochyy.mochymusic.ui.player.customize.isDisc
import com.mochyy.mochymusic.ui.player.customize.rememberWarpShader
import com.mochyy.mochymusic.ui.player.letTouchesThroughWhenHidden
import com.mochyy.mochymusic.ui.player.likeButtonTarget
import com.mochyy.mochymusic.ui.player.rememberTextSlots
import com.mochyy.mochymusic.ui.player.studio.StudioAnchors
import com.mochyy.mochymusic.ui.player.studio.StudioParts
import com.mochyy.mochymusic.ui.player.studio.StudioPieces
import com.mochyy.mochymusic.ui.player.studio.studioAnchor
import com.mochyy.mochymusic.ui.player.studio.studioCoverLayer
import com.mochyy.mochymusic.ui.player.studio.studioMiddleLayer
import com.mochyy.mochymusic.ui.player.studio.studioPiece
import com.mochyy.mochymusic.ui.player.studio.studioTransportLayer
import com.mochyy.mochymusic.ui.player.styles.styleSongSwipe
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.roundToInt

// full cover fills up to this share of width and melts smoothly into its mirrored blurred copy on the right
internal const val LandscapeFullCoverWidthShare = 0.48f
internal const val LandscapeFullCoverAspect = 1.12f
private const val CoverFadeShare = 0.24f

// horizontal progressive frost across the cover-to-backdrop seam, matching portrait's CoverFrost radius
private val LandscapeFrostRadius = 40.dp
private const val LandscapeFrostLead = 0.16f
private const val LandscapeFrostOvershoot = 0.14f

// a tv is watched from across the room: the cover melts away over much more of its width, and at the bottom too
private const val TvCoverFadeShare = 0.45f
private const val TvCoverFadeDownFrom = 0.62f
private val TvTitleInset = 48.dp

// with lyrics or queue open on full cover, the cover's lower half softens under compact controls and fades past the seam
private const val LyricsBandShare = 0.54f
private const val LyricsBandWidthFactor = 1.10f
private const val LyricsOverlayHeightShare = 0.64f
private const val LyricsOverlayWidthFactor = 1.22f
private const val LyricsBandRadius = 30f
private const val LandscapeControlsIdleMs = 3_500L

// aligns title, playback seek bar, volume bar and card deck along one vertical rail (8dp bottom padding + 18dp BarInset)
private val PanelGutter = 26.dp
private val CoverGutter = 20.dp

// the like and the menu chevron, stacked cleanly in their own column at the bottom right
private val ButtonSize = 34.dp
private val ButtonGap = 10.dp
private val SideButtonsRoom = ButtonSize + 28.dp

// what runs down the left of the player on its side
private enum class CoverLook { SQUARE, FULL, DISC, RECORD, TV_SQUARE }

// the rounded square's resting size and corner radius, shared with the landscape cover editor
internal const val LandscapeSquareHeightShare = 0.72f
internal const val LandscapeSquareWidthShare = 0.36f
internal val LandscapeSquareShape = RoundedCornerShape(22.dp)
private val PanelGap = 28.dp

// the record is laid out by its box's width and runs 1.406 of it tall, so this box fits the record to the height
private const val RecordBoxPerHeight = 1f / 1.406f

// soft shade over the ambient backdrop so white text and icons stay crisp on bright covers
private val EvenShade = Color.Black.copy(alpha = 0.18f)
private val FullPanelShade = Color.Black.copy(alpha = 0.22f)
private const val GlareShade = 0.26f

// horizontal reflection mesh columns for the full-cover blurred mirror
private const val MirrorCols = 24
private const val MirrorStrength = 0.9f
private const val MirrorFadeEnd = 0.85f

// paced to the 30 hz ambient and warp shader step so landscape backdrops don't wake 120 hz screens unnecessarily
private const val LandscapeClockWakeMs = 30L

// shared landscape cover geometry so the player and the cover editor line up pixel for pixel
internal fun landscapeCoverBounds(
    style: PlayerCoverStyle,
    maxWidth: Dp,
    maxHeight: Dp,
    startInset: Dp,
): Rect {
    val w = maxWidth.value
    val h = maxHeight.value
    val s = startInset.value
    return if (style == PlayerCoverStyle.EDGE_TO_EDGE) {
        val coverW = minOf(h * LandscapeFullCoverAspect, w * LandscapeFullCoverWidthShare)
        Rect(0f, 0f, coverW, h)
    } else {
        val side = minOf(h * LandscapeSquareHeightShare, w * LandscapeSquareWidthShare)
        val x = s + maxOf(18f, (h - side) * 0.36f)
        val y = (h - side) / 2f
        Rect(x, y, x + side, y + side)
    }
}

// the landscape flagship: squared or full cover driven by the player editor, same ambient backdrop as portrait,
// with compact controls tucking cleanly under the cover when lyrics or queue open so nothing ever overlaps
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlagshipLandscape(
    pages: PlayerPageTransition,
    sheetShowing: Boolean,
    interactive: () -> Boolean,
    expanded: () -> Boolean,
    likeBurst: LikeBurstState,
    onChevron: () -> Unit,
    onChevronPlaced: (Rect) -> Unit,
    onSongInfo: () -> Unit,
    onSwitchLyricsSource: () -> Unit,
    transport: @Composable (Modifier) -> Unit,
    bottom: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    turntable: Boolean = false,
    coverStyle: PlayerCoverStyle = PlayerCoverStyle.SQUARED,
    backgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.AMBIENT,
    ambient: AmbientColors? = null,
    pureBlack: Boolean = false,
    editMode: Boolean = false,
    compactTransport: @Composable (Modifier) -> Unit = transport,
    studioParts: StudioParts? = null,
    studioAnchors: StudioAnchors? = null,
    studioPieces: StudioPieces? = null,
    onDoubleTapCover: ((Offset) -> Unit)? = null,
    bottomFlush: Boolean = false,
) {
    val connection = LocalPlayerConnection.current ?: return
    val track by connection.uiState.trackInfo.collectAsState()
    val transportState by connection.uiState.transportState.collectAsState()
    val carousel = LocalSongCarousel.current
    val onTv = com.mochyy.mochymusic.ui.utils.isTvLayout()
    val fadeShare = if (onTv) TvCoverFadeShare else CoverFadeShare
    val tvSquare by com.mochyy.mochymusic.utils.rememberPreference(com.mochyy.mochymusic.constants.TvFlagshipSquareKey, defaultValue = false)
    val look = when {
        onTv -> if (tvSquare) CoverLook.TV_SQUARE else CoverLook.FULL
        turntable -> CoverLook.RECORD
        coverStyle == PlayerCoverStyle.EDGE_TO_EDGE -> CoverLook.FULL
        coverStyle.isDisc -> CoverLook.DISC
        else -> CoverLook.SQUARE
    }
    val coverBlurAvailable = rememberCoverBlurAvailable()
    val useCoverFrost = look == CoverLook.FULL && !onTv && coverBlurAvailable
    val seamGlass = remember { GlassState() }

    val handedToBar = animateFloatAsState(
        if (onTv && com.mochyy.mochymusic.ui.player.TvPlayerRemote.controlsShown) 1f else 0f,
        tween(420),
        label = "handedToBar",
    )
    val direction = LocalLayoutDirection.current
    val sides = WindowInsets.navigationBarsIgnoringVisibility.union(WindowInsets.displayCutout).asPaddingValues()
    val startInset = sides.calculateStartPadding(direction)
    val endInset = sides.calculateEndPadding(direction)
    val bottomInset = sides.calculateBottomPadding()

    val lyricsProgress = remember(pages) { { pages.lyricsProgress.coerceIn(0f, 1f) } }
    val queueProgress = remember(pages) { { pages.queueProgress.coerceIn(0f, 1f) } }
    val pageProgress = remember(pages) { { maxOf(pages.lyricsProgress, pages.queueProgress).coerceIn(0f, 1f) } }
    val lyricsMounted by remember(pages) {
        derivedStateOf { pages.lyricsProgress > 0.001f || pages.preparing == PlayerPage.LYRICS }
    }
    val queueMounted by remember(pages, onTv) {
        derivedStateOf { !onTv && (pages.queueProgress > 0.001f || pages.preparing == PlayerPage.QUEUE) }
    }
    val pageMounted = lyricsMounted || queueMounted
    val panelInfoShown by remember(pages) { derivedStateOf { pages.lyricsProgress < 0.999f && pages.queueProgress < 0.999f } }
    val lyricsActive by remember(pages) { derivedStateOf { pages.page == PlayerPage.LYRICS } }

    // auto-retracting player controls on the landscape lyrics page: seekbar and transport tuck down while title & artist glide to bottom-left
    var controlsRetracted by remember { mutableStateOf(false) }
    var controlsTouching by remember { mutableStateOf(false) }
    var controlsInteractionTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lyricsActive) {
        controlsRetracted = false
        controlsInteractionTick++
    }
    LaunchedEffect(lyricsActive, controlsRetracted, controlsTouching, controlsInteractionTick, editMode) {
        if (lyricsActive && !controlsRetracted && !controlsTouching && !editMode) {
            delay(LandscapeControlsIdleMs)
            controlsRetracted = true
        }
    }
    val controlsRetract = animateFloatAsState(
        targetValue = if (lyricsActive && controlsRetracted && !editMode) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "landscapeControlsRetract",
    )
    val controlsActive by remember { derivedStateOf { controlsRetract.value < 0.99f } }
    val currentOnSongInfo by androidx.compose.runtime.rememberUpdatedState(onSongInfo)
    // tapping the cover returns from lyrics/queue back to cover view; on cover view it still opens song info
    val onCoverTap: () -> Unit = remember(pages) {
        { if (pages.page != PlayerPage.NONE) pages.close() else currentOnSongInfo() }
    }
    val onDiscCoverTap: () -> Unit = remember(pages) {
        { if (pages.page != PlayerPage.NONE) pages.close() }
    }

    // shared paced backdrop clock for ambient and cover-gradient styles
    val warpTime = remember { mutableFloatStateOf(0f) }
    val appObscured = LocalAppContentObscured.current
    val backdropRunning by remember(sheetShowing, editMode) {
        derivedStateOf { sheetShowing && !editMode && !appObscured.value }
    }
    LaunchedEffect(backdropRunning) {
        if (!backdropRunning) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withInfiniteAnimationFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                    warpTime.floatValue = (warpTime.floatValue + dt).mod(100f)
                }
                last = now
            }
            delay(LandscapeClockWakeMs)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth
        val squareSide = minOf(maxHeight * LandscapeSquareHeightShare, maxWidth * LandscapeSquareWidthShare)
        val squareStart = startInset + maxOf(18.dp, (maxHeight - squareSide) * 0.36f)
        val recordBox = maxHeight * RecordBoxPerHeight
        val leftColWidth = when (look) {
            CoverLook.FULL, CoverLook.TV_SQUARE -> minOf(maxHeight * LandscapeFullCoverAspect, maxWidth * LandscapeFullCoverWidthShare)
            CoverLook.SQUARE, CoverLook.DISC -> (squareStart + squareSide + PanelGap).coerceAtMost(maxWidth * 0.46f)
            CoverLook.RECORD -> startInset + recordBox + PanelGap / 2
        }

        val coverStudioModifier = Modifier
            .then(if (studioAnchors != null) Modifier.studioAnchor(studioAnchors) { it.cover } else Modifier)
            .then(Modifier.studioCoverLayer(studioParts))

        val showLeftCompactControls = !onTv && (look == CoverLook.SQUARE || look == CoverLook.DISC)
        val compactSide = minOf(
            maxHeight * 0.47f,
            (leftColWidth - startInset - 36.dp).coerceAtLeast(92.dp),
        )

        // backdrop and cover share one glassRoot when full-cover blur is enabled so progressive seam frost and lyrics frost sample them once
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (useCoverFrost) Modifier.glassRoot(seamGlass, isActive = { sheetShowing }) else Modifier),
        ) {
            LandscapeBackdrop(
                ambient = ambient,
                backgroundStyle = backgroundStyle,
                thumbnailUrl = track.thumbnailUrl,
                pureBlack = pureBlack,
                warpTime = { warpTime.floatValue },
                animate = backdropRunning,
                onTv = onTv,
                fullCover = look == CoverLook.FULL,
                coverWidth = leftColWidth,
                fadeShare = fadeShare,
            )

            when (look) {
                CoverLook.SQUARE -> PhoneSquareCover(
                    restSide = squareSide,
                    restStart = squareStart,
                    compactSide = compactSide,
                    leftColWidth = leftColWidth,
                    startInset = startInset,
                    pageProgress = if (showLeftCompactControls) pageProgress else { { 0f } },
                    thumbnailUrl = track.thumbnailUrl,
                    interactive = interactive,
                    editMode = editMode,
                    onTap = onCoverTap,
                    onDoubleTap = onDoubleTapCover,
                    studioModifier = coverStudioModifier,
                )
                CoverLook.DISC -> LandscapeDiscCover(
                    style = coverStyle,
                    restSide = squareSide,
                    restStart = squareStart,
                    compactSide = compactSide,
                    leftColWidth = leftColWidth,
                    startInset = startInset,
                    pageProgress = pageProgress,
                    thumbnailUrl = track.thumbnailUrl,
                    mediaId = track.mediaId,
                    isPlaying = transportState.isPlaying,
                    spinActive = expanded() && sheetShowing && !editMode,
                    editMode = editMode,
                    interactive = interactive,
                    onTap = onDiscCoverTap,
                    studioModifier = coverStudioModifier,
                )
                CoverLook.RECORD -> LandscapeRecord(
                    width = recordBox,
                    start = startInset,
                    thumbnailUrl = track.thumbnailUrl,
                    mediaId = track.mediaId,
                    interactive = interactive,
                    expanded = expanded,
                    sheetShowing = sheetShowing && !editMode,
                    studioModifier = coverStudioModifier,
                )
                CoverLook.FULL, CoverLook.TV_SQUARE -> LandscapeCover(
                    coverWidth = leftColWidth,
                    thumbnailUrl = track.thumbnailUrl,
                    interactive = interactive,
                    fadeShare = fadeShare,
                    fadeDownFrom = if (onTv && look == CoverLook.FULL) TvCoverFadeDownFrom else null,
                    square = look == CoverLook.TV_SQUARE,
                    editMode = editMode,
                    onTap = onCoverTap,
                    onDoubleTap = onDoubleTapCover,
                    studioModifier = coverStudioModifier,
                )
            }
        }

        // progressive horizontal frost across the cover-to-bg transition when blur is enabled, matching portrait's CoverFrost
        if (useCoverFrost) {
            LandscapeCoverFrost(
                glassState = seamGlass,
                coverWidth = leftColWidth,
                fadeShare = fadeShare,
                visible = { sheetShowing },
            )
        }

        // smooth edge-less frost and dark overlay under left-side controls/title while lyrics or queue are open
        if (pageMounted && look == CoverLook.FULL && !onTv) {
            LyricsBand(
                coverWidth = leftColWidth,
                glassState = if (useCoverFrost) seamGlass else null,
                pageProgress = pageProgress,
                retractProgress = { controlsRetract.value },
            )
        }

        if (onTv) {
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

        // squared/disc with lyrics or queue open: compact seek bar and transport tuck neatly under the smaller cover on the left
        if (pageMounted && showLeftCompactControls) {
            val leftControlsWidth = (leftColWidth - startInset - 22.dp).coerceAtLeast(140.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = startInset + 11.dp, bottom = bottomInset + 10.dp)
                    .width(leftControlsWidth)
                    .graphicsLayer {
                        val p = pageProgress()
                        alpha = p
                        translationY = (1f - p) * 18.dp.toPx()
                    },
            ) {
                PlayerProgressSlider(active = sheetShowing && !panelInfoShown, horizontalPadding = 8.dp)
                Spacer(Modifier.height(2.dp))
                compactTransport(Modifier.fillMaxWidth())
            }
        }

        // full cover with lyrics or queue open: title, seek bar and compact transport stack cleanly over the softened lower cover;
        // on lyrics, controls auto-retract downward while title & artist glide to the bottom-left over the dark overlay
        if (pageMounted && look == CoverLook.FULL) {
            if (onTv) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(leftColWidth)
                        .padding(start = TvTitleInset, end = TvTitleInset, bottom = bottomInset + TvTitleInset)
                        .graphicsLayer {
                            val p = lyricsProgress()
                            val handed = handedToBar.value
                            alpha = p * (1f - handed)
                            translationY = (1f - p) * 24.dp.toPx() + handed * 30.dp.toPx()
                        },
                ) {
                    SongTitles(title = track.title, artist = track.artist, onClick = onSongInfo)
                }
            } else {
                var controlsHeightPx by remember { mutableFloatStateOf(0f) }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(leftColWidth)
                        .padding(
                            start = startInset + CoverGutter,
                            end = CoverGutter + 10.dp,
                            bottom = bottomInset + 8.dp,
                        )
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                controlsTouching = true
                                controlsInteractionTick++
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                } while (event.changes.any { it.pressed })
                                controlsTouching = false
                                controlsInteractionTick++
                            }
                        }
                        .graphicsLayer {
                            val p = pageProgress()
                            alpha = p
                            translationY = (1f - p) * 20.dp.toPx()
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(1f)
                            .graphicsLayer {
                                val r = controlsRetract.value
                                translationY = r * (controlsHeightPx - 6.dp.toPx()).coerceAtLeast(0f)
                            },
                    ) {
                        SongTitles(
                            title = track.title,
                            artist = track.artist,
                            onClick = {
                                if (controlsRetracted) {
                                    controlsRetracted = false
                                    controlsInteractionTick++
                                } else {
                                    onSongInfo()
                                }
                            },
                            compact = true,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { controlsHeightPx = it.height.toFloat() }
                            .graphicsLayer {
                                val r = controlsRetract.value
                                alpha = (1f - r * 1.35f).coerceIn(0f, 1f)
                                translationY = r * (controlsHeightPx + 24.dp.toPx())
                                letTouchesThroughWhenHidden()
                            },
                    ) {
                        Spacer(Modifier.height(4.dp))
                        PlayerProgressSlider(
                            active = sheetShowing && !panelInfoShown && controlsActive,
                            horizontalPadding = 0.dp,
                        )
                        compactTransport(Modifier.fillMaxWidth())
                    }
                }
            }
        }

        // stacked like and chevron buttons in their own right-edge gutter so nothing can overlap them
        if (!onTv) CoverButtons(
            liked = track.liked,
            likeBurst = likeBurst,
            onChevron = onChevron,
            onChevronPlaced = onChevronPlaced,
            settled = expanded,
            studioAnchors = studioAnchors,
            studioPieces = studioPieces,
            editing = editMode,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = endInset + 14.dp, bottom = bottomInset + 16.dp),
        )

        // right panel: resting title + seek + transport, or full-height lyrics/queue, with the bottom deck/bar cleanly underneath
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = leftColWidth, end = endInset + if (onTv) 0.dp else SideButtonsRoom)
                .padding(bottom = if (bottomFlush) 0.dp else bottomInset),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (panelInfoShown) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 24.dp, bottom = 4.dp)
                            .graphicsLayer {
                                val p = pageProgress()
                                alpha = 1f - p
                                translationY = -p * 18.dp.toPx()
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PanelGutter)
                                .then(if (studioAnchors != null) Modifier.studioAnchor(studioAnchors) { it.songInfo } else Modifier)
                                .then(Modifier.studioMiddleLayer(studioParts) { it.songInfo }),
                        ) {
                            SongTitles(title = track.title, artist = track.artist, onClick = onSongInfo)
                        }
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (studioAnchors != null) Modifier.studioAnchor(studioAnchors) { it.slider } else Modifier)
                                .then(Modifier.studioMiddleLayer(studioParts) { it.slider }),
                        ) {
                            PlayerProgressSlider(
                                active = sheetShowing && panelInfoShown,
                                onSeekBarPlaced = { coords ->
                                    if (!editMode) studioAnchors?.capture(studioAnchors.seekBar, coords)
                                },
                                onEndTimePlaced = { coords ->
                                    if (!editMode) studioAnchors?.capture(studioAnchors.endTime, coords)
                                },
                                seekBarModifier = if (studioPieces != null) Modifier.studioPiece(studioPieces, StudioPieces.SEEK_BAR) else Modifier,
                                endTimeModifier = if (studioPieces != null) Modifier.studioPiece(studioPieces, StudioPieces.END_TIME) else Modifier,
                                horizontalPadding = PanelGutter,
                            )
                        }
                        if (!onTv) {
                            Spacer(Modifier.height(6.dp))
                            transport(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp)
                                    .then(if (studioAnchors != null) Modifier.studioAnchor(studioAnchors) { it.transport } else Modifier)
                                    .then(Modifier.studioTransportLayer(studioParts))
                                    .then(if (studioPieces != null) Modifier.studioPiece(studioPieces, StudioPieces.TRANSPORT) else Modifier),
                            )
                        }
                    }
                }
                if (lyricsMounted) {
                    LyricsScreen(
                        onClose = { pages.close() },
                        screenHeight = screenHeight,
                        contentBottomInset = 0.dp,
                        onImmersiveChange = { immersive ->
                            if (lyricsActive) {
                                if (immersive && !controlsTouching) {
                                    controlsRetracted = true
                                } else if (!immersive) {
                                    controlsRetracted = false
                                    controlsInteractionTick++
                                }
                            }
                        },
                        onSwitchSource = onSwitchLyricsSource,
                        showHeader = false,
                        onCoverBackdrop = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = if (onTv) 0.dp else 20.dp)
                            .graphicsLayer {
                                val p = lyricsProgress()
                                alpha = p
                                translationY = (1f - p) * 36.dp.toPx()
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
                            .padding(top = 20.dp)
                            .graphicsLayer {
                                val p = queueProgress()
                                alpha = p
                                translationY = (1f - p) * 36.dp.toPx()
                            },
                    )
                }
            }
            // turntable keeps its record full height on the left, so keep compact transport handy above the bottom bar while a page is up
            if (!onTv && look == CoverLook.RECORD && pageMounted) {
                compactTransport(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .graphicsLayer { alpha = pageProgress() },
                )
            }
            if (!onTv) {
                bottom(Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

// same flagship backdrop as portrait, plus a horizontal blurred cover mirror and seam veil for full cover mode
@Composable
private fun BoxScope.LandscapeBackdrop(
    ambient: AmbientColors?,
    backgroundStyle: PlayerBackgroundStyle,
    thumbnailUrl: String?,
    pureBlack: Boolean,
    warpTime: () -> Float,
    animate: Boolean,
    onTv: Boolean,
    fullCover: Boolean,
    coverWidth: Dp,
    fadeShare: Float,
) {
    val carousel = LocalSongCarousel.current
    if (onTv) {
        CarouselCrossfade(carousel = carousel, fallbackUrl = thumbnailUrl, modifier = Modifier.fillMaxSize()) { url ->
            BlurredCoverPicture(url = url, modifier = Modifier.fillMaxSize())
        }
    } else if (ambient != null) {
        AmbientBackdrop(colors = ambient, time = warpTime, modifier = Modifier.fillMaxSize())
    } else if (backgroundStyle != PlayerBackgroundStyle.COVER_GRADIENT) {
        PlayerBackgroundContent(
            style = backgroundStyle,
            thumbnailUrl = thumbnailUrl,
            pureBlack = pureBlack,
            modifier = Modifier.fillMaxSize(),
            animate = animate,
        )
    } else {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val bgWidth = maxWidth
            val bgHeight = maxHeight
            val warpShader = rememberWarpShader()
            CarouselCrossfade(carousel = carousel, fallbackUrl = thumbnailUrl, modifier = Modifier.fillMaxSize()) { url ->
                CoverGradientBackdrop(
                    thumbnailUrl = url,
                    width = bgWidth,
                    height = bgHeight,
                    animate = false,
                    shader = warpShader,
                    timeProvider = warpTime,
                )
            }
        }
    }

    // for full cover, copy and mirror the blurred cover across its right seam like portrait's CoverReflection + CoverVeil
    if (fullCover && !onTv && thumbnailUrl != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = (coverWidth * (1f - fadeShare)).coerceAtLeast(0.dp)),
        ) {
            CarouselPictures(carousel = carousel, fallbackUrl = thumbnailUrl, modifier = Modifier.fillMaxSize()) { url, pictureModifier ->
                HorizontalCoverReflection(
                    url = url,
                    coverWidth = coverWidth,
                    modifier = pictureModifier,
                )
            }
        }
    }

    // subtle colour-matched seam veil and glare guard so text and controls stay crisp on any cover
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val seam = ambient?.seam()
                val cw = coverWidth.toPx()
                val from = cw * (1f - fadeShare)
                val panelShadeBrush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.45f to FullPanelShade.copy(alpha = FullPanelShade.alpha * 0.5f),
                    1f to FullPanelShade,
                    startX = from,
                    endX = cw,
                )
                val seamVeilBrush = seam?.let { color ->
                    Brush.horizontalGradient(
                        0f to color.copy(alpha = 0f),
                        0.32f to color.copy(alpha = 0.25f),
                        1f to color.copy(alpha = 0.65f),
                        startX = from,
                        endX = size.width,
                    )
                }
                val glareBrush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = GlareShade),
                    startX = from,
                    endX = cw,
                )
                onDrawBehind {
                    if (!fullCover) {
                        drawRect(EvenShade)
                    } else {
                        seamVeilBrush?.let { drawRect(it) }
                        drawRect(panelShadeBrush)
                        val glare = ambient?.glare()?.coerceIn(0f, 1f) ?: 0f
                        if (glare > 0.004f) drawRect(glareBrush, alpha = glare)
                    }
                }
            },
    )
}

// blurred copy of the full cover flipped horizontally across its right seam and eased out into the backdrop
@Composable
private fun HorizontalCoverReflection(
    url: String?,
    coverWidth: Dp,
    modifier: Modifier = Modifier,
) {
    if (url == null) return
    val context = LocalContext.current
    val request = remember(context, url) {
        ImageRequest.Builder(context)
            .data(url.resize(ReflectionPx, ReflectionPx))
            .allowHardware(false)
            .transformations(BackdropBlurTransformation(radiusPx = ReflectionBlurPx))
            .crossfade(false)
            .size(CoilSize(ReflectionPx, ReflectionPx))
            .build()
    }
    val picture by produceState<Bitmap?>(null, request) {
        value = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
    }
    val mesh = remember { HorizontalReflectionMesh() }
    Spacer(
        modifier = modifier.drawBehind {
            val bitmap = picture ?: return@drawBehind
            mesh.draw(this, bitmap, coverWidth.toPx())
        },
    )
}

// reused mesh arrays so the horizontal mirror allocates nothing per draw
private class HorizontalReflectionMesh {
    private val verts = FloatArray(2 * (MirrorCols + 1) * 2)
    private val colors = IntArray(2 * (MirrorCols + 1))
    private val paint = android.graphics.Paint().apply { isFilterBitmap = true }

    fun draw(scope: DrawScope, bitmap: Bitmap, coverWidthPx: Float): Unit = with(scope) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f || coverWidthPx <= 0f) return
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val fit = max(coverWidthPx / bw, h / bh)
        val srcRight = (bw + coverWidthPx / fit) / 2f
        val srcTop = (bh - h / fit) / 2f

        for (row in 0..1) {
            val v = bh * row
            val y = (v - srcTop) * fit
            for (col in 0..MirrorCols) {
                val u = bw * col / MirrorCols
                // flipped horizontally: the cover's right edge starts at x = 0 and columns further left extend rightwards
                val x = (srcRight - u) * fit
                val t = (x / (w * MirrorFadeEnd)).coerceIn(0f, 1f)
                val eased = t * t * (3f - 2f * t)
                val a = (MirrorStrength * (1f - eased) * 255f).roundToInt().coerceIn(0, 255)
                val idx = row * (MirrorCols + 1) + col
                verts[idx * 2] = x
                verts[idx * 2 + 1] = y
                colors[idx] = android.graphics.Color.argb(a, 255, 255, 255)
            }
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val saved = native.save()
            native.clipRect(0f, 0f, w, h)
            native.drawBitmapMesh(bitmap, MirrorCols, 1, verts, 0, colors, 0, paint)
            native.restoreToCount(saved)
        }
    }
}

// rounded square cover that glides cleanly into a smaller upper slot when lyrics or queue open
@Composable
private fun BoxScope.PhoneSquareCover(
    restSide: Dp,
    restStart: Dp,
    compactSide: Dp,
    leftColWidth: Dp,
    startInset: Dp,
    pageProgress: () -> Float,
    thumbnailUrl: String?,
    interactive: () -> Boolean,
    editMode: Boolean,
    onTap: () -> Unit,
    onDoubleTap: ((Offset) -> Unit)?,
    studioModifier: Modifier,
) {
    val connection = LocalPlayerConnection.current
    val carousel = LocalSongCarousel.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        Modifier
            .layout { measurable, constraints ->
                val p = pageProgress().coerceIn(0f, 1f)
                val fullW = constraints.maxWidth.toFloat()
                val fullH = constraints.maxHeight.toFloat()
                val rSide = restSide.toPx()
                val cSide = compactSide.toPx()
                val side = (rSide + (cSide - rSide) * p).roundToInt().coerceAtLeast(1)
                val restX = restStart.toPx()
                val restY = (fullH - rSide) / 2f
                val leftInnerStart = startInset.toPx() + 14.dp.toPx()
                val leftInnerWidth = (leftColWidth.toPx() - leftInnerStart - 14.dp.toPx()).coerceAtLeast(cSide)
                val compactX = leftInnerStart + (leftInnerWidth - cSide) / 2f
                val compactY = 20.dp.toPx()
                val x = (restX + (compactX - restX) * p).roundToInt()
                val y = (restY + (compactY - restY) * p).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(side, side))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(x.coerceIn(0, (fullW - side).roundToInt().coerceAtLeast(0)), y)
                }
            }
            .then(studioModifier)
            .shadow(22.dp, LandscapeSquareShape)
            .clip(LandscapeSquareShape)
            .onGloballyPositioned { coords[0] = it }
            .then(
                if (editMode) Modifier else Modifier.pointerInput(onTap, onDoubleTap) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { pos ->
                            val c = coords[0]
                            if (c != null && c.isAttached && onDoubleTap != null) onDoubleTap(c.localToRoot(pos))
                        },
                    )
                },
            )
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

// disc cover styles rendered inside the same left-hand slot and gliding cleanly when lyrics/queue open
@Composable
private fun BoxScope.LandscapeDiscCover(
    style: PlayerCoverStyle,
    restSide: Dp,
    restStart: Dp,
    compactSide: Dp,
    leftColWidth: Dp,
    startInset: Dp,
    pageProgress: () -> Float,
    thumbnailUrl: String?,
    mediaId: String,
    isPlaying: Boolean,
    spinActive: Boolean,
    editMode: Boolean,
    interactive: () -> Boolean,
    onTap: () -> Unit,
    studioModifier: Modifier,
) {
    val connection = LocalPlayerConnection.current
    val carousel = LocalSongCarousel.current
    Box(
        Modifier
            .layout { measurable, constraints ->
                val p = pageProgress().coerceIn(0f, 1f)
                val fullH = constraints.maxHeight.toFloat()
                val rSide = restSide.toPx()
                val cSide = compactSide.toPx()
                val side = (rSide + (cSide - rSide) * p).roundToInt().coerceAtLeast(1)
                val restX = restStart.toPx()
                val restY = (fullH - rSide) / 2f
                val leftInnerStart = startInset.toPx() + 14.dp.toPx()
                val leftInnerWidth = (leftColWidth.toPx() - leftInnerStart - 14.dp.toPx()).coerceAtLeast(cSide)
                val compactX = leftInnerStart + (leftInnerWidth - cSide) / 2f
                val compactY = 20.dp.toPx()
                val x = (restX + (compactX - restX) * p).roundToInt()
                val y = (restY + (compactY - restY) * p).roundToInt()
                val placeable = measurable.measure(Constraints.fixed(side, side))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(x, y)
                }
            }
            .then(studioModifier)
            .then(
                if (editMode) Modifier else Modifier.pointerInput(onTap) {
                    detectTapGestures(onTap = { onTap() })
                },
            )
            .styleSongSwipe(interactive, carousel) { connection?.player },
    ) {
        DiscCoverStack(
            style = style,
            artworkUrl = thumbnailUrl,
            mediaId = mediaId,
            isPlaying = isPlaying,
            spinActive = spinActive,
            editMode = editMode,
            carousel = carousel,
            modifier = Modifier.fillMaxSize(),
        )
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
    studioModifier: Modifier,
) {
    val connection = LocalPlayerConnection.current ?: return
    val carousel = LocalSongCarousel.current
    val transport by connection.uiState.transportState.collectAsState()
    val open by remember { derivedStateOf { expanded() } }
    DiscCoverStack(
        style = PlayerCoverStyle.TURNTABLE,
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
            .then(studioModifier)
            .styleSongSwipe(interactive, carousel) { connection.player },
    )
}

// full edge-to-edge cover down the left, fading smoothly onto its blurred mirror and flagship backdrop on the right
@Composable
private fun BoxScope.LandscapeCover(
    coverWidth: Dp,
    thumbnailUrl: String?,
    interactive: () -> Boolean,
    fadeShare: Float,
    fadeDownFrom: Float?,
    square: Boolean = false,
    editMode: Boolean = false,
    onTap: () -> Unit = {},
    onDoubleTap: ((Offset) -> Unit)? = null,
    studioModifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current
    val carousel = LocalSongCarousel.current
    if (square) {
        SquareCover(coverWidth = coverWidth, thumbnailUrl = thumbnailUrl, interactive = interactive)
        return
    }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        modifier = Modifier
            .width(coverWidth)
            .fillMaxHeight()
            .then(studioModifier)
            .onGloballyPositioned { coords[0] = it }
            .then(
                if (editMode) Modifier else Modifier.pointerInput(onTap, onDoubleTap) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { pos ->
                            val c = coords[0]
                            if (c != null && c.isAttached && onDoubleTap != null) onDoubleTap(c.localToRoot(pos))
                        },
                    )
                },
            )
            .styleSongSwipe(interactive, carousel) { connection?.player }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val from = size.width * (1f - fadeShare)
                val rightFade = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.25f to Color.Black.copy(alpha = 0.08f),
                    0.55f to Color.Black.copy(alpha = 0.45f),
                    0.82f to Color.Black.copy(alpha = 0.85f),
                    1f to Color.Black,
                    startX = from,
                    endX = size.width,
                )
                val downFade = fadeDownFrom?.let { start ->
                    val top = size.height * start
                    top to Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Black.copy(alpha = 0.15f),
                        0.7f to Color.Black.copy(alpha = 0.6f),
                        1f to Color.Black,
                        startY = top,
                        endY = size.height,
                    )
                }
                onDrawWithContent {
                    drawContent()
                    drawRect(rightFade, topLeft = Offset(from, 0f), blendMode = BlendMode.DstOut)
                    downFade?.let { (top, brush) ->
                        drawRect(brush, topLeft = Offset(0f, top), blendMode = BlendMode.DstOut)
                    }
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
}

// progressive horizontal blur across the cover-to-bg transition when blur is enabled, matching portrait's CoverFrost
private val LandscapeFrostProgressiveMask = Brush.horizontalGradient(
    0f to Color.Transparent,
    0.06f to Color.Black,
    0.76f to Color.Black,
    0.84f to Color.Black.copy(alpha = 0.72f),
    0.92f to Color.Black.copy(alpha = 0.28f),
    1f to Color.Transparent,
)

private val LandscapeFrostFlatMask = Brush.horizontalGradient(
    *Array(9) { i ->
        val t = i / 8f
        t * 0.52f to Color.Black.copy(alpha = t * t * (3f - 2f * t))
    },
    0.76f to Color.Black,
    0.84f to Color.Black.copy(alpha = 0.72f),
    0.92f to Color.Black.copy(alpha = 0.28f),
    1f to Color.Transparent,
)

@Composable
private fun BoxScope.LandscapeCoverFrost(
    glassState: GlassState,
    coverWidth: Dp,
    fadeShare: Float,
    visible: () -> Boolean,
) {
    val drawn by remember { derivedStateOf { visible() } }
    if (!drawn) return
    val progressiveEnabled = rememberProgressiveBlur()
    val (blurStrengthPx) = rememberPreference(BlurStrengthPxKey, defaultValue = 25f)
    val smooth = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        LocalBlurQuality.current == BlurQuality.HIGH &&
        progressiveEnabled
    val radiusPx = with(LocalDensity.current) { LandscapeFrostRadius.toPx() }
    val frostStart = (coverWidth * (1f - fadeShare - LandscapeFrostLead)).coerceAtLeast(0.dp)
    val frostEnd = coverWidth * (1f + LandscapeFrostOvershoot)
    val frostWidth = (frostEnd - frostStart).coerceAtLeast(1.dp)
    val ramp = ((coverWidth - frostStart) / frostWidth).coerceIn(0.2f, 0.95f)
    var position by remember { mutableStateOf(Offset.Zero) }

    DownscaledBlur(
        radius = { BlurEffectCache.effectiveRadius(radiusPx * (blurStrengthPx / 25f)) },
        tileMode = Shader.TileMode.CLAMP,
        mask = if (smooth) LandscapeFrostProgressiveMask else LandscapeFrostFlatMask,
        growDown = if (smooth) true else null,
        growAcross = smooth,
        growRamp = ramp,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(frostStart.roundToPx(), 0) }
            .width(frostWidth)
            .fillMaxHeight()
            .onGloballyPositioned { position = it.positionInWindow() },
    ) { _ ->
        val node = glassState.renderNode
        if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && glassNodeHasContent(node)) {
            val relX = position.x - glassState.rootPosition.x
            val relY = position.y - glassState.rootPosition.y
            translate(left = -relX, top = -relY) {
                drawIntoCanvas { it.nativeCanvas.drawGlassNode(node) }
            }
        }
    }
}

// 2D smoothstep vertical + horizontal fade: eliminates both top and right seams in a single GPU pass with zero offscreen layers
private class SoftCornerFadeBrush(
    private val verticalStops: FloatArray,
    private val verticalAlphas: FloatArray,
    private val horizontalFadeStart: Float,
    private val tint: Color,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val vColors = IntArray(verticalAlphas.size) { i ->
            tint.copy(alpha = verticalAlphas[i]).toArgb()
        }
        val vertical = LinearGradient(0f, 0f, 0f, h, vColors, verticalStops, Shader.TileMode.CLAMP)
        val steps = 8
        val hPositions = FloatArray(steps + 2)
        val hColors = IntArray(steps + 2)
        hPositions[0] = 0f
        hColors[0] = android.graphics.Color.WHITE
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val eased = t * t * (3f - 2f * t)
            hPositions[i + 1] = horizontalFadeStart + (1f - horizontalFadeStart) * t
            hColors[i + 1] = android.graphics.Color.argb(((1f - eased) * 255f).roundToInt().coerceIn(0, 255), 255, 255, 255)
        }
        val horizontal = LinearGradient(0f, 0f, w, 0f, hColors, hPositions, Shader.TileMode.CLAMP)
        return ComposeShader(vertical, horizontal, PorterDuff.Mode.DST_IN)
    }
}

private fun smoothCornerFadeBrush(
    verticalStart: Float,
    maxAlpha: Float,
    horizontalFadeStart: Float,
    tint: Color = Color.Black,
): ShaderBrush {
    val steps = 8
    val hasPrefix = verticalStart > 0f
    val count = (if (hasPrefix) 1 else 0) + steps + 1
    val stops = FloatArray(count)
    val alphas = FloatArray(count)
    var idx = 0
    if (hasPrefix) {
        stops[idx] = 0f
        alphas[idx] = 0f
        idx++
    }
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val eased = t * t * (3f - 2f * t)
        stops[idx] = verticalStart + (1f - verticalStart) * t
        alphas[idx] = maxAlpha * eased
        idx++
    }
    return SoftCornerFadeBrush(stops, alphas, horizontalFadeStart, tint)
}

// cached top-level brushes so DownscaledBlur's RenderEffect cache and drawBehind reuse the exact same instances
private val LyricsBlurMaskBrush = smoothCornerFadeBrush(verticalStart = 0f, maxAlpha = 1f, horizontalFadeStart = 0.58f)
private val LyricsExpandedShadeBrush = smoothCornerFadeBrush(verticalStart = 0f, maxAlpha = 0.62f, horizontalFadeStart = 0.56f)
private val LyricsRetractedShadeBrush = smoothCornerFadeBrush(verticalStart = 0.48f, maxAlpha = 0.58f, horizontalFadeStart = 0.56f)

// soft bottom-left frost + edge-less dark gradient over the full cover while lyrics or queue are open;
// as controls retract, the frost fades out (zero GPU work when hidden) and the dark gradient lowers behind the title & artist
@Composable
private fun BoxScope.LyricsBand(
    coverWidth: Dp,
    glassState: GlassState?,
    pageProgress: () -> Float,
    retractProgress: () -> Float,
) {
    val blurShown by remember { derivedStateOf { glassState != null && pageProgress() > 0.001f && retractProgress() < 0.995f } }
    if (blurShown && glassState != null) {
        val smooth = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            LocalBlurQuality.current == BlurQuality.HIGH &&
            rememberProgressiveBlur()
        var position by remember { mutableStateOf(Offset.Zero) }
        DownscaledBlur(
            radius = { BlurEffectCache.effectiveRadius(LyricsBandRadius) },
            tileMode = Shader.TileMode.CLAMP,
            mask = LyricsBlurMaskBrush,
            growDown = if (smooth) true else null,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(coverWidth * LyricsBandWidthFactor)
                .fillMaxHeight(LyricsBandShare)
                .onGloballyPositioned { position = it.positionInWindow() }
                .graphicsLayer {
                    alpha = pageProgress().coerceIn(0f, 1f) * (1f - retractProgress().coerceIn(0f, 1f))
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        ) { _ ->
            val node = glassState.renderNode
            if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && glassNodeHasContent(node)) {
                val relX = position.x - glassState.rootPosition.x
                val relY = position.y - glassState.rootPosition.y
                translate(left = -relX, top = -relY) {
                    drawIntoCanvas { it.nativeCanvas.drawGlassNode(node) }
                }
            }
        }
    }
    Spacer(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .width(coverWidth * LyricsOverlayWidthFactor)
            .fillMaxHeight(LyricsOverlayHeightShare)
            .drawBehind {
                val p = pageProgress().coerceIn(0f, 1f)
                if (p <= 0.004f) return@drawBehind
                val r = retractProgress().coerceIn(0f, 1f)
                val expandedAlpha = p * (1f - r)
                if (expandedAlpha > 0.004f) drawRect(LyricsExpandedShadeBrush, alpha = expandedAlpha)
                val retractedAlpha = p * r
                if (retractedAlpha > 0.004f) drawRect(LyricsRetractedShadeBrush, alpha = retractedAlpha)
            },
    )
}

// the title and the artist, riding the row of songs as it slides; a tap opens the song's details
@Composable
private fun SongTitles(title: String, artist: String, onClick: () -> Unit, compact: Boolean = false) {
    val carousel = LocalSongCarousel.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        if (carousel == null) {
            TitleLines(title, artist, Modifier, compact)
        } else {
            for (slot in rememberTextSlots(carousel)) {
                androidx.compose.runtime.key(slot.uid) {
                    val playing = slot.step == carousel.step
                    TitleLines(
                        title = slot.title.ifEmpty { if (playing) title else "" },
                        artist = slot.artist.ifEmpty { if (playing) artist else "" },
                        modifier = Modifier.carouselText(carousel, slot),
                        compact = compact,
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleLines(title: String, artist: String, modifier: Modifier, compact: Boolean = false) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 17.sp else 22.sp,
            lineHeight = if (compact) 21.sp else 26.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artist,
            color = Color.White.copy(alpha = 0.72f),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 13.sp else 15.sp,
            lineHeight = if (compact) 16.sp else 19.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// the like over the menu chevron, stacked at the right edge with studio anchors wired
@Composable
private fun CoverButtons(
    liked: Boolean,
    likeBurst: LikeBurstState,
    onChevron: () -> Unit,
    onChevronPlaced: (Rect) -> Unit,
    settled: () -> Boolean,
    studioAnchors: StudioAnchors?,
    studioPieces: StudioPieces?,
    editing: Boolean,
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
            modifier = Modifier
                .likeButtonTarget(likeBurst, LikeSlot.PLAYER)
                .onGloballyPositioned { if (!editing) studioAnchors?.capture(studioAnchors.sideButton, it) }
                .then(if (studioPieces != null) Modifier.studioPiece(studioPieces, StudioPieces.SIDE) else Modifier),
        )
        PressScaleActionButton(
            icon = R.drawable.expand_less,
            tint = Color.White,
            containerColor = Color.White.copy(alpha = 0.18f),
            size = ButtonSize,
            onClick = onChevron,
            modifier = Modifier
                .onGloballyPositioned {
                    if (!editing) studioAnchors?.capture(studioAnchors.chevron, it)
                    if (settled()) onChevronPlaced(it.boundsInRoot())
                }
                .then(if (studioPieces != null) Modifier.studioPiece(studioPieces, StudioPieces.CHEVRON) else Modifier),
        )
    }
}

// the tv's whole cover as a rounded square over the blurred copy, a little above the middle so the title fits under it
@Composable
private fun BoxScope.SquareCover(coverWidth: Dp, thumbnailUrl: String?, interactive: () -> Boolean) {
    val connection = LocalPlayerConnection.current
    val carousel = LocalSongCarousel.current
    BoxWithConstraints(Modifier.width(coverWidth).fillMaxHeight()) {
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
