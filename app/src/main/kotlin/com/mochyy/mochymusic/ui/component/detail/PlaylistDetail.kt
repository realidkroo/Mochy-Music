// PlaylistDetail.kt

package com.mochyy.mochymusic.ui.component.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalDetailAccentColor
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PlaylistStyle
import com.mochyy.mochymusic.constants.PlaylistStyleKey
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.ui.component.BlurDirection
import com.mochyy.mochymusic.ui.component.BoneColor
import com.mochyy.mochymusic.ui.component.Bone
import com.mochyy.mochymusic.ui.component.BonesHost
import com.mochyy.mochymusic.ui.component.GlassPillBackground
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.HomeCardCornerRadius
import com.mochyy.mochymusic.ui.component.ItemThumbnail
import com.mochyy.mochymusic.ui.component.ProgressiveGlassBackground
import com.mochyy.mochymusic.ui.component.RoundedHighlight
import com.mochyy.mochymusic.ui.component.ScrollAwayCover
import com.mochyy.mochymusic.ui.component.containerTransformCover
import com.mochyy.mochymusic.ui.component.containerTransformTarget
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.component.rememberElementBlurAvailable
import com.mochyy.mochymusic.ui.component.smoothstep
import com.mochyy.mochymusic.ui.player.BackdropBlurTransformation
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.theme.rememberCoverThemeColor
import com.mochyy.mochymusic.ui.utils.SoftBlurTransformation
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import com.music.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import com.mochyy.mochymusic.ui.utils.stableStatusBars
import com.mochyy.mochymusic.ui.utils.stableStatusBarsPadding
import com.mochyy.mochymusic.ui.component.RemoteFocus

// geometry and type from the Figma playlist frames (16:1162 open, 16:1354 scrolled). internal so
// the album, artist and generated pages line up on the same inset and type
internal val PlaylistInset = 36.dp
internal val PlaylistTracking = (-0.06).em
internal val PillContent = Color(0xFF2F2F2F)
internal val PillFill = Color.White.copy(alpha = 0.64f)
internal val TrackShape = RoundedCornerShape(9.dp)

// a pressed track lights a pill from one side of the screen to the other, like the swipe slab
private val TrackHighlight = RoundedHighlight(cornerRadius = 100.dp, spillX = (-10).dp)

internal val PlaylistTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    letterSpacing = PlaylistTracking,
)
internal val PlaylistCreatorStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    letterSpacing = (-0.04).em,
)
internal val PlaylistStatsStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.02).em,
)
internal val PillLabelStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    letterSpacing = PlaylistTracking,
)
internal val TrackTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 17.sp,
    letterSpacing = (-0.03).em,
)
internal val TrackSubtitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.5.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.02).em,
)
internal val SectionTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    letterSpacing = PlaylistTracking,
)
internal val ReceiptSmallStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 8.5.sp,
    letterSpacing = 0.04.em,
)
internal val ReceiptCenterStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    letterSpacing = 0.42.em,
)

/** who made a playlist. [artistId] lets the avatar be looked up when there's no url yet. */
data class CreatorUi(
    val name: String,
    val avatarUrl: String? = null,
    val artistId: String? = null,
)

data class FeaturedArtistUi(
    val id: String,
    val name: String,
    /** when the caller already has the photo (an artist page's shelves do), no lookup is made */
    val thumbnailUrl: String? = null,
)

/**
 * the artists a playlist leans on most, most frequent first. grouped by id alone: the same artist
 * can come back under two spellings, and a repeated id would crash the row's keys.
 */
fun <A> featuredArtistsOf(
    artists: List<A>,
    id: (A) -> String?,
    name: (A) -> String,
    limit: Int = 10,
): List<FeaturedArtistUi> =
    artists
        .mapNotNull { artist -> id(artist)?.let { it to name(artist) } }
        .groupBy({ it.first }, { it.second })
        .entries
        .sortedByDescending { it.value.size }
        .take(limit)
        .map { (artistId, names) -> FeaturedArtistUi(id = artistId, name = names.first()) }

// passes the scaffold's cover composable down to PlaylistHeader when classic or minimal style draws a framed cover
private val LocalPlaylistFramedCover = staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }

/** the cover band at the top of a playlist, shared by the header item and the backdrop behind it */
@Composable
fun playlistCoverHeight(): Dp = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

/**
 * shell every playlist screen sits in. it's what grows out of the tapped Home card, it takes its
 * colour from the bottom of the cover, draws the cover behind the list (riding up, zooming in and
 * going soft as you scroll, then fading out), and runs the top bar where a small copy of the cover
 * springs out of the back button once the header has scrolled away.
 */
@Composable
fun PlaylistDetailScaffold(
    sharedElementKey: String?,
    coverUrl: String?,
    lazyListState: LazyListState,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    showCover: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    topBarOverride: (@Composable () -> Unit)? = null,
    /** picking songs or editing the order: the bar swaps its buttons but keeps its glass */
    modeBar: PlaylistModeBar? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    /**
     * how far the list scrolls before the top bar turns to glass. the cover band by default; a
     * page without a cover passes its shorter header so the bar frosts as the title slides under it
     */
    headerHeight: Dp? = null,
    /**
     * the generated pages (no cover of their own): the collapsed pill is a full capsule with a round
     * cover, like the Library's search capsule, and the theme colour sinks to black down the page
     */
    roundCapsule: Boolean = false,
    /**
     * a cover drawn in code instead of [coverUrl]'s picture (Liked songs). it fills the modifier
     * it's given; the backdrop and the small copy in the collapsed bar both draw it
     */
    cover: (@Composable (Modifier) -> Unit)? = null,
    /** the theme colour for a drawn [cover], which has no picture to sample one from */
    coverThemeColor: Color? = null,
    content: LazyListScope.() -> Unit,
) {
    // A bar that replaces the top bar (the list's own song search) sits over the list rather than
    // above it, and there's no cover band then to push the first rows down: the first matches were
    // drawn under the search field (#128). The list starts below the bar's measured height while
    // it's up, and starts from the top when it opens - it used to keep the scroll from before, half
    // way down a long playlist.
    val overrideBarHeightPx = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val searchBarUp = topBarOverride != null
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val overrideBarHeight = with(LocalDensity.current) { overrideBarHeightPx.intValue.toDp() }
    val listPadding = if (searchBarUp) {
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding() + overrideBarHeight,
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding(),
        )
    } else {
        contentPadding
    }
    LaunchedEffect(searchBarUp) {
        if (searchBarUp) lazyListState.scrollToItem(0)
    }
    // the cover stays up behind the search, gone soft as if scrolled part way, so the results read over it
    val searchSoftness by animateFloatAsState(
        targetValue = if (searchBarUp) SearchCoverSoftness else 0f,
        animationSpec = tween(420),
        label = "playlistSearchCover",
    )

    // the tapped card's cover comes first: the screen grows out of that image, so it has to be
    // there from the first frame, and keeping it means the cover never swaps mid-way
    val handedCover = remember(sharedElementKey) { containerTransformCover(sharedElementKey) }
    val coverUrl = handedCover ?: coverUrl
    val playlistStyle by rememberEnumPreference(PlaylistStyleKey, defaultValue = PlaylistStyle.FLOW)
    val effectiveShowCover = showCover && (roundCapsule || playlistStyle == PlaylistStyle.FLOW)
    val effectiveRoundCapsule = roundCapsule || (showCover && playlistStyle == PlaylistStyle.MINIMAL)
    val classicBackdrop = showCover && !roundCapsule && playlistStyle == PlaylistStyle.CLASSIC
    val framedCover: (@Composable (Modifier) -> Unit)? = remember(cover, coverUrl) {
        when {
            cover != null -> cover
            !coverUrl.isNullOrEmpty() -> { mod ->
                AsyncImage(
                    model = coverUrl.resize(544, 544),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = mod,
                )
            }
            else -> null
        }
    }

    val sampledThemeColor by rememberCoverThemeColor(if (cover != null) null else coverUrl)
    val themeColor = if (cover != null) coverThemeColor else sampledThemeColor
    val background = animateColorAsState(
        targetValue = themeColor ?: MaterialTheme.colorScheme.background,
        animationSpec = tween(600),
        label = "playlistTheme",
    )

    // the bottom nav scrim picks this up, so the whole screen reads as one colour. a page that
    // sinks to black hands it black, or the nav would sit in a band of the theme colour
    val accent = LocalDetailAccentColor.current
    SideEffect { accent.value = if (effectiveRoundCapsule) Color.Black else themeColor }
    DisposableEffect(Unit) { onDispose { accent.value = null } }

    val coverHeight = playlistCoverHeight()
    val classicHeaderHeight = playlistClassicHeaderHeight()
    val effectiveHeaderHeight = when {
        headerHeight != null -> headerHeight
        classicBackdrop -> classicHeaderHeight
        !effectiveShowCover -> playlistCompactHeaderHeight()
        else -> coverHeight
    }
    val collapseHeightPx = with(LocalDensity.current) { effectiveHeaderHeight.toPx() }
    val scrollProgress = remember(lazyListState, collapseHeightPx) {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (lazyListState.firstVisibleItemScrollOffset / collapseHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    // flips once the title has slid under the top bar; a soft spring with a little bounce on the
    // way in, a calmer one on the way out
    val collapsed by remember(scrollProgress) { derivedStateOf { scrollProgress.value > 0.55f } }
    val morph = remember { Animatable(0f) }
    LaunchedEffect(collapsed) {
        morph.animateTo(
            targetValue = if (collapsed) 1f else 0f,
            animationSpec = spring(dampingRatio = if (collapsed) 0.62f else 0.9f, stiffness = 360f),
        )
    }
    val glassState = remember { GlassState() }
    val scope = rememberCoroutineScope()
    // the Edit order pill is frosted even with the cover still showing, so the glass has to run
    // before the bar collapses
    val pillNeedsGlass = rememberUpdatedState(modeBar is PlaylistModeBar.EditOrder)

    CompositionLocalProvider(LocalPlaylistFramedCover provides framedCover) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .containerTransformTarget(sharedElementKey)
            .then(
                if (effectiveRoundCapsule) {
                    // pinned to the screen, not the list: the colour holds at the top and settles
                    // into black over the lower half, wherever the rows have scrolled to
                    Modifier.drawBehind {
                        drawRect(Color.Black)
                        drawRect(
                            Brush.verticalGradient(
                                0f to background.value,
                                0.34f to background.value.copy(alpha = 0.86f),
                                0.78f to background.value.copy(alpha = 0.18f),
                                1f to Color.Transparent,
                            )
                        )
                    }
                } else {
                    Modifier.drawBehind { drawRect(background.value) }
                }
            )
            .then(modifier)
    ) {
        // the frosted top bar samples the cover and the list together. it sits beside this box,
        // never inside it, so the capture can't end up containing the bar that draws it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { morph.value > 0.01f || pillNeedsGlass.value })
        ) {
            if (effectiveShowCover) {
                PlaylistCoverBackdrop(
                    coverUrl = coverUrl,
                    cover = cover,
                    coverHeight = coverHeight,
                    scrollProgress = { maxOf(scrollProgress.value, searchSoftness) },
                    background = background,
                )
            } else if (classicBackdrop) {
                PlaylistClassicBackdrop(
                    coverUrl = coverUrl,
                    cover = cover,
                    height = classicHeaderHeight,
                    scrollProgress = { maxOf(scrollProgress.value, searchSoftness) },
                    background = background,
                )
            }

            LazyColumn(
                state = lazyListState,
                contentPadding = listPadding,
                modifier = Modifier.fillMaxSize(),
                content = content,
            )
        }

        if (topBarOverride != null) {
            // measured, so the list starts under it (see listPadding)
            Box(
                modifier = Modifier.onSizeChanged { overrideBarHeightPx.intValue = it.height },
            ) {
                topBarOverride()
            }
        } else {
            PlaylistTopBar(
                glassState = glassState,
                morph = { morph.value },
                coverUrl = coverUrl,
                cover = cover,
                background = background,
                onBackClick = onBackClick,
                onBackLongClick = onBackLongClick,
                onMoreClick = onMoreClick,
                onMiniCoverClick = { scope.launch { lazyListState.animateScrollToItem(0) } },
                roundCapsule = effectiveRoundCapsule,
                mode = modeBar,
            )
        }

        overlay()
    }
    }
}

// the backdrop is pinned behind the list, so it has to travel most of the way up on its own
private const val CoverFollow = 0.8f
// how far "scrolled" the cover looks behind the song search: soft and a little up, still there like the design
private const val SearchCoverSoftness = 0.42f
private const val CoverSoftBy = 0.55f

@Composable
private fun PlaylistCoverBackdrop(
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    coverHeight: Dp,
    scrollProgress: () -> Float,
    background: State<Color>,
) {
    val context = LocalContext.current
    // the card already holds the 544px copy in memory, so that shows instantly while the sharp one loads
    val request = remember(coverUrl) {
        ImageRequest.Builder(context)
            .data(coverUrl?.resize(1200, 1200))
            .placeholderMemoryCacheKey(coverUrl?.resize(544, 544))
            .build()
    }
    // before android 12 the whole frame can't blur, so the picture alone goes soft from a copy blurred once off the main thread
    val frameBlur = rememberElementBlurAvailable()
    val (elementBlur) = rememberPreference(com.mochyy.mochymusic.constants.ElementBlurKey, defaultValue = true)
    val softRequest = remember(coverUrl, frameBlur, elementBlur, cover != null) {
        // a drawn cover is soft already, and with element blur off the cover stays sharp
        if (frameBlur || !elementBlur || cover != null) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(coverUrl?.resize(544, 544))
                .size(192)
                .transformations(SoftBlurTransformation())
                .build()
        }
    }

    ScrollAwayCover(
        progress = scrollProgress,
        follow = -CoverFollow,
        modifier = Modifier
            .fillMaxWidth()
            .height(coverHeight),
    ) {
        if (cover != null) {
            cover(Modifier.fillMaxSize())
        } else {
            if (softRequest != null) {
                AsyncImage(
                    model = softRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        // skipped entirely until the scroll starts revealing it
                        .graphicsLayer { alpha = if (scrollProgress() > 0f) 1f else 0f },
                )
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (softRequest != null) {
                            Modifier.graphicsLayer {
                                alpha = 1f - smoothstep(0f, CoverSoftBy, scrollProgress())
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                        } else {
                            Modifier
                        }
                    ),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val bg = background.value
                    val top = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.34f),
                        1f to Color.Transparent,
                        endY = size.height * 0.24f,
                    )
                    val bottom = Brush.verticalGradient(
                        0.56f to Color.Transparent,
                        0.84f to bg.copy(alpha = 0.78f),
                        1f to bg,
                    )
                    onDrawBehind {
                        drawRect(top, size = Size(size.width, size.height * 0.24f))
                        drawRect(bottom)
                    }
                }
        )
    }
}

// a little closer when the page opens, settling back as it lands
private const val ClassicBackdropOpenZoom = 1.12f
private const val ClassicBackdropScrollZoom = 0.22f

// the squared style's backdrop: its own cover blurred big behind the square, fading down into the page colour and zooming as it scrolls away
@Composable
private fun PlaylistClassicBackdrop(
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    height: Dp,
    scrollProgress: () -> Float,
    background: State<Color>,
) {
    val context = LocalContext.current
    // blurred once, tiny and off the main thread; stretched over the band it's a heavy blur for almost nothing, on every android version
    val request = remember(coverUrl) {
        ImageRequest.Builder(context)
            .data(coverUrl?.resize(544, 544))
            .size(96)
            .transformations(BackdropBlurTransformation(radiusPx = 6))
            .build()
    }
    val settle = remember { Animatable(ClassicBackdropOpenZoom) }
    LaunchedEffect(Unit) { settle.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                val p = scrollProgress()
                translationY = -p * size.height * CoverFollow
                val zoom = settle.value + p * ClassicBackdropScrollZoom
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0.5f, 0.35f)
            }
            .drawWithContent {
                drawContent()
                // fading it by laying the page's own colour over it keeps it one layer, no offscreen pass while scrolling
                val fade = smoothstep(0.2f, 0.85f, scrollProgress())
                if (fade > 0f) drawRect(background.value.copy(alpha = fade))
            },
    ) {
        if (cover != null) {
            cover(Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val bg = background.value
                    val top = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.3f),
                        1f to Color.Transparent,
                        endY = size.height * 0.25f,
                    )
                    // tinted a touch all the way down so the sharp square stands off it, then sinking into the page
                    val bottom = Brush.verticalGradient(
                        0f to bg.copy(alpha = 0.18f),
                        0.5f to bg.copy(alpha = 0.4f),
                        0.85f to bg.copy(alpha = 0.92f),
                        1f to bg,
                    )
                    onDrawBehind {
                        drawRect(bottom)
                        drawRect(top, size = Size(size.width, size.height * 0.25f))
                    }
                }
        )
    }
}

@Composable
private fun PlaylistTopBar(
    glassState: GlassState,
    morph: () -> Float,
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    background: State<Color>,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    onMoreClick: (() -> Unit)?,
    onMiniCoverClick: () -> Unit,
    roundCapsule: Boolean = false,
    mode: PlaylistModeBar? = null,
) {
    val showChrome by remember { derivedStateOf { morph() > 0.01f } }
    val statusBarTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val barModifier = Modifier
        .fillMaxWidth()
        .stableStatusBarsPadding()
        .padding(top = 6.dp)
        .height(47.dp)
        .padding(start = 26.dp, end = 30.dp)

    Box(modifier = Modifier.fillMaxWidth()) {
        if (showChrome) {
            // the theme colour underneath keeps the sharp rows from showing through the frost
            ProgressiveGlassBackground(
                state = glassState,
                maxBlurRadius = { 34f * morph().coerceIn(0f, 1f) },
                tint = background.value.copy(alpha = 0.4f),
                foundationColor = background.value,
                direction = BlurDirection.BottomToTop,
                steps = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarTop + 96.dp)
                    .graphicsLayer { alpha = morph().coerceIn(0f, 1f) }
            )
        }

        // keyed on the kind of mode only: a new count or a new lambda updates the bar in place,
        // only going in or out of a mode plays the swap
        AnimatedContent(
            targetState = mode,
            contentKey = { it?.javaClass },
            transitionSpec = {
                (fadeIn(tween(180, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { -it / 3 })
                    .togetherWith(fadeOut(tween(110)) + slideOutVertically(tween(160)) { -it / 4 })
                    .using(SizeTransform(clip = false))
            },
            label = "playlistTopBarMode",
        ) { shownMode ->
            if (shownMode != null) {
                PlaylistModeBarRow(
                    mode = shownMode,
                    glassState = glassState,
                    coverUrl = coverUrl,
                    cover = cover,
                    tint = background,
                    modifier = barModifier,
                )
            } else {
                PlaylistNormalBarRow(
                    modifier = barModifier,
                    morph = morph,
                    glassState = glassState,
                    coverUrl = coverUrl,
                    cover = cover,
                    background = background,
                    onBackClick = onBackClick,
                    onBackLongClick = onBackLongClick,
                    onMoreClick = onMoreClick,
                    onMiniCoverClick = onMiniCoverClick,
                    roundCapsule = roundCapsule,
                )
            }
        }
    }
}

/** back (with the mini cover once collapsed) on the left, the dots on the right */
@Composable
private fun PlaylistNormalBarRow(
    modifier: Modifier,
    morph: () -> Float,
    glassState: GlassState,
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    background: State<Color>,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    onMoreClick: (() -> Unit)?,
    onMiniCoverClick: () -> Unit,
    roundCapsule: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        BackPill(
            morph = morph,
            glassState = glassState,
            coverUrl = coverUrl,
            cover = cover,
            tint = background,
            onBackClick = onBackClick,
            onBackLongClick = onBackLongClick,
            onMiniCoverClick = onMiniCoverClick,
            round = roundCapsule,
        )
        Spacer(Modifier.weight(1f))
        if (onMoreClick != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        val m = morph().coerceIn(0f, 1f)
                        drawCircle(Color.Black.copy(alpha = 0.28f * (1f - m)))
                        drawCircle(Color.White.copy(alpha = 0.18f * m))
                    }
                    .clickable(onClick = onMoreClick)
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = "Options",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * expanded it's only the back button. collapsed it's the 99x47 glass pill from the Figma, with a
 * small copy of the cover that grows out of the back button and settles beside it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackPill(
    morph: () -> Float,
    glassState: GlassState,
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    tint: State<Color>,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    onMiniCoverClick: () -> Unit,
    round: Boolean = false,
) {
    val density = LocalDensity.current
    // the round one is a true capsule, ends fully rounded like the Library's search field
    val pillShape = if (round) RoundedCornerShape(50) else RoundedCornerShape(17.dp)
    val miniShape = if (round) CircleShape else TrackShape

    Box(
        modifier = Modifier
            .size(width = 99.dp, height = 47.dp)
            .graphicsLayer { translationX = with(density) { 10.dp.toPx() } * morph() }
    ) {
        // the pill's size follows the spring in the layout pass, so it stretches out of the back
        // button (and overshoots a hair) without recomposing anything
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .layout { measurable, _ ->
                    val m = morph().coerceAtLeast(0f)
                    val width = lerp(30.dp, 99.dp, m).roundToPx()
                    val height = lerp(30.dp, 47.dp, m).roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) { placeable.place(0, 0) }
                }
                .graphicsLayer { alpha = morph().coerceIn(0f, 1f) }
        ) {
            // fully clear at morph 0, so the glass isn't composed (or recomposed by the theme colour tween) until the bar collapses
            val showPill by remember { derivedStateOf { morph() > 0f } }
            if (showPill) BackPillGlass(glassState = glassState, tint = tint, shape = pillShape)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { translationX = with(density) { 11.dp.toPx() } * morph() }
                .size(30.dp)
                .clip(CircleShape)
                .drawBehind {
                    val m = morph().coerceIn(0f, 1f)
                    drawCircle(Color.Black.copy(alpha = 0.28f + 0.17f * m))
                }
                .combinedClickable(onClick = onBackClick, onLongClick = onBackLongClick)
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back_ios),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(16.dp)
                    .offset(x = 2.dp),
            )
        }

        val showMini by remember { derivedStateOf { morph() > 0.02f } }
        if (showMini && (cover != null || !coverUrl.isNullOrEmpty())) {
            val miniModifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 56.dp)
                .graphicsLayer {
                    // starts tucked under the back button, small, and springs out to its slot;
                    // the spring overshoots a touch, so it lands with a little give
                    val m = morph()
                    translationX = with(density) { (-45).dp.toPx() } * (1f - m)
                    val s = 0.35f + 0.65f * m
                    scaleX = s
                    scaleY = s
                    alpha = m.coerceIn(0f, 1f)
                    shape = miniShape
                    clip = true
                }
                .size(37.dp)
                .clickable(onClick = onMiniCoverClick)
            if (cover != null) {
                cover(miniModifier)
            } else {
                AsyncImage(
                    model = coverUrl?.resize(240, 240),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = miniModifier,
                )
            }
        }
    }
}

/** the frosted fill and hairline of the collapsed back pill; the theme colour is read here so only this recomposes */
@Composable
private fun BoxScope.BackPillGlass(glassState: GlassState, tint: State<Color>, shape: Shape) {
    GlassPillBackground(
        state = glassState,
        blurRadius = { 28f },
        tint = Color.White.copy(alpha = 0.12f),
        foundationColor = tint.value,
        shape = shape,
        modifier = Modifier.matchParentSize()
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
    )
}

/**
 * title, who made it, a stats line, then Play / shuffle (sf) / add to library (rn).
 *
 * with a [description] (albums) the line under the creator is the description and the stats move
 * below it. [compact] is for the pages with no cover of their own: the block sits at the top under
 * the bar instead of at the foot of a cover band, with a bigger title, and [below] can hang a search
 * capsule under the buttons.
 */
@Composable
fun PlaylistHeader(
    title: String,
    creators: List<CreatorUi>,
    stats: String,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean? = null,
    onSaveClick: () -> Unit = {},
    sortControl: (@Composable () -> Unit)? = null,
    description: String? = null,
    onCreatorClick: ((CreatorUi) -> Unit)? = null,
    compact: Boolean = false,
    below: (@Composable () -> Unit)? = null,
    // opens the list's own song search, from a round button beside shuffle
    onSearchClick: (() -> Unit)? = null,
) {
    val playlistStyle by rememberEnumPreference(PlaylistStyleKey, defaultValue = PlaylistStyle.FLOW)
    val framedCover = LocalPlaylistFramedCover.current
    val useMinimal = compact || playlistStyle == PlaylistStyle.MINIMAL
    val useClassic = !compact && playlistStyle == PlaylistStyle.CLASSIC

    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(
                start = PlaylistInset + 1.dp,
                end = PlaylistInset,
                bottom = 14.dp,
            )
        ) {
            if (useClassic && framedCover != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 22.dp),
                ) {
                    val shape = RoundedCornerShape(HomeCardCornerRadius)
                    Box(
                        modifier = Modifier
                            .size(playlistClassicCoverSize())
                            .clip(shape)
                            .background(BoneColor),
                    ) {
                        framedCover(Modifier.fillMaxSize())
                    }
                }
            }
            Text(
                text = title,
                style = if (useMinimal) PlaylistCompactTitleStyle else PlaylistTitleStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (creators.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                CreatorRow(creators, onCreatorClick)
            }
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                PlaylistDescription(description)
            }
            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(if (description.isNullOrBlank()) 3.dp else 6.dp))
                Text(
                    text = stats,
                    style = PlaylistStatsStyle,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayPill(isPlaying = isPlaying, onClick = onPlayClick)
                Spacer(Modifier.width(9.dp))
                CircleAction(
                    icon = R.drawable.shuffle,
                    contentDescription = "Shuffle",
                    onClick = onShuffleClick,
                    spinOnClick = true,
                )
                if (onSearchClick != null) {
                    Spacer(Modifier.width(4.dp))
                    CircleAction(
                        icon = R.drawable.search_outline,
                        contentDescription = stringResource(R.string.search_this_list),
                        onClick = onSearchClick,
                    )
                }
                if (isSaved != null) {
                    Spacer(Modifier.width(4.dp))
                    CircleAction(
                        icon = if (isSaved) R.drawable.library_add_check else R.drawable.library_add,
                        contentDescription = if (isSaved) "In your library" else "Add to library",
                        onClick = onSaveClick,
                    )
                }
            }
            if (sortControl != null) {
                Spacer(Modifier.height(12.dp))
                sortControl()
            }
        }
        if (below != null) {
            Spacer(Modifier.height(4.dp))
            below()
            Spacer(Modifier.height(10.dp))
        }
    }

    if (useMinimal || useClassic) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = playlistCompactHeaderTop())
        ) {
            body()
        }
    } else {
        val coverHeight = playlistCoverHeight()
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = coverHeight * 0.975f),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column { body() }
        }
    }
}

/** where a compact header's title starts: under the status bar and the back button's row */
@Composable
fun playlistCompactHeaderTop(): Dp =
    WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding() + 78.dp

/** a compact header's rough height, for [PlaylistDetailScaffold]'s headerHeight */
@Composable
fun playlistCompactHeaderHeight(): Dp = playlistCompactHeaderTop() + 120.dp

// the squared style's cover, most of the screen's width so it reads as the page's picture and not a thumbnail
@Composable
private fun playlistClassicCoverSize(): Dp = (LocalConfiguration.current.screenWidthDp * 0.72f).dp.coerceAtMost(340.dp)

// the squared style's header: the cover, the gap under it and the title block
@Composable
private fun playlistClassicHeaderHeight(): Dp = playlistCompactHeaderTop() + playlistClassicCoverSize() + 22.dp + 120.dp

internal val PlaylistCompactTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 36.sp,
    letterSpacing = PlaylistTracking,
)

/**
 * an album's description where the stats line used to be: two lines, and a tap opens the rest in
 * place. the height eases with the text so the rows below glide down instead of jumping.
 */
@Composable
internal fun PlaylistDescription(text: String, modifier: Modifier = Modifier) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        style = PlaylistStatsStyle.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        color = Color.White.copy(alpha = 0.74f),
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        modifier = modifier
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 420f))
            .clickable(
                enabled = overflows || expanded,
                interactionSource = null,
                indication = RemoteFocus,
            ) { expanded = !expanded },
    )
}

/** play and pause swap with a little pop, and the pill eases to the new label's width */
@Composable
internal fun PlayPill(isPlaying: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction)
            .height(33.dp)
            .clip(RoundedCornerShape(50))
            .background(PillFill)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(start = 21.dp, end = 17.dp)
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = { glyphSwap() },
            label = "playPillGlyph",
        ) { playing ->
            Icon(
                painter = painterResource(if (playing) R.drawable.ic_untitled_pause else R.drawable.ic_untitled_play),
                contentDescription = null,
                tint = PillContent,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = { labelSwap() },
            label = "playPillLabel",
        ) { playing ->
            Text(
                text = if (playing) "Pause" else "Play",
                style = PillLabelStyle,
                color = PillContent,
            )
        }
    }
}

/** the round buttons squish when pressed; shuffle gives its arrows a spin, and a changed icon pops in */
@Composable
internal fun CircleAction(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    spinOnClick: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pressBounce(interaction)
            .size(33.dp)
            .clip(CircleShape)
            .background(PillFill)
            .clickable(interactionSource = interaction, indication = RemoteFocus) {
                if (spinOnClick) {
                    scope.launch {
                        spin.snapTo(0f)
                        spin.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                    }
                }
                onClick()
            }
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = { glyphSwap() },
            label = "circleActionIcon",
        ) { res ->
            Icon(
                painter = painterResource(res),
                contentDescription = contentDescription,
                tint = PillContent,
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer { rotationZ = spin.value * 360f },
            )
        }
    }
}

/** an icon swap: the new one springs up from small while the old one shrinks away */
private fun <S> AnimatedContentTransitionScope<S>.glyphSwap(): ContentTransform =
    (scaleIn(initialScale = 0.4f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f)) + fadeIn(tween(120)))
        .togetherWith(scaleOut(targetScale = 0.4f, animationSpec = tween(110)) + fadeOut(tween(90)))
        .using(SizeTransform(clip = false))

/** a label swap: the new word rises in as the old one lifts out */
private fun <S> AnimatedContentTransitionScope<S>.labelSwap(): ContentTransform =
    (slideInVertically(spring(dampingRatio = 0.75f, stiffness = 600f)) { it / 2 } + fadeIn(tween(140)))
        .togetherWith(slideOutVertically(tween(120)) { -it / 2 } + fadeOut(tween(100)))
        .using(SizeTransform(clip = false))

/** creator avatars side by side, overlapping when there's more than one */
@Composable
private fun CreatorRow(creators: List<CreatorUi>, onClick: ((CreatorUi) -> Unit)? = null) {
    val shown = creators.take(3)
    val avatar = 16.dp
    val step = 11.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // the lead creator opens; an album by several artists leads with the first of them
        modifier = if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick(creators.first()) }
        } else {
            Modifier
        },
    ) {
        Box(modifier = Modifier.size(width = avatar + step * (shown.size - 1), height = avatar)) {
            shown.forEachIndexed { index, creator ->
                CreatorAvatar(
                    creator = creator,
                    size = avatar,
                    modifier = Modifier
                        .offset(x = step * index)
                        .zIndex((shown.size - index).toFloat())
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = when (creators.size) {
                1 -> creators[0].name
                2 -> "${creators[0].name} & ${creators[1].name}"
                else -> "${creators[0].name}, ${creators[1].name} & ${creators.size - 2} more"
            },
            style = PlaylistCreatorStyle,
            color = Color.White.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CreatorAvatar(creator: CreatorUi, size: Dp, modifier: Modifier = Modifier) {
    val looked = rememberArtistThumbnail(
        artistId = creator.artistId.takeIf { creator.avatarUrl == null },
        artistName = creator.name,
    )
    val url = creator.avatarUrl ?: looked.value
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.28f))
            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
    ) {
        if (url != null) {
            AsyncImage(
                model = url.resize(120, 120),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = creator.name.take(1).uppercase(),
                style = PlaylistStatsStyle.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

/** the "last added ⌄" label under the buttons; opens a small menu of sort orders */
@Composable
fun <T : Enum<T>> PlaylistSortControl(
    current: T,
    descending: Boolean,
    options: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onToggleDirection: () -> Unit,
    showDirection: (T) -> Boolean = { true },
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = label(current).lowercase(),
                style = PlaylistStatsStyle.copy(fontSize = 12.sp),
                color = Color.White.copy(alpha = 0.72f),
            )
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = if (showDirection(current) && !descending) 180f else 0f }
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color(0xFF1E1F23),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label(option),
                            style = TrackTitleStyle,
                            color = if (option == current) Color.White else Color.White.copy(alpha = 0.7f),
                        )
                    },
                    trailingIcon = if (option == current && showDirection(option)) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.expand_more),
                                contentDescription = if (descending) "Descending" else "Ascending",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = if (descending) 0f else 180f }
                            )
                        }
                    } else null,
                    onClick = {
                        if (option == current) onToggleDirection() else onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** a song row as drawn in the Figma playlist: 39dp cover, two lines, the dots on the right */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistTrackRow(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    /** an artist's row: a round photo instead of the rounded square */
    coverShape: androidx.compose.ui.graphics.Shape = TrackShape,
    /** applied to the cover itself, e.g. to open an album growing out of it */
    coverModifier: Modifier = Modifier,
    /** a song's id: marks the row when the song is downloaded or an imported file */
    songId: String? = null,
    /** picked while selecting: the row sits on a soft band */
    selected: Boolean = false,
    /** Optional like action; hidden when a selection-mode trailing control is present. */
    liked: Boolean? = null,
    onLikeClick: (() -> Unit)? = null,
) {
    val band by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "selectedBand")
    // Keep album rows as compact as the other tracks while giving the heart a 48dp tap target.
    val verticalPadding = if (trailing == null && liked != null && onLikeClick != null) 5.dp else 9.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .selectedRowBand { band }
            .combinedClickable(
                interactionSource = null,
                indication = TrackHighlight,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(start = PlaylistInset, end = 24.dp, top = verticalPadding, bottom = verticalPadding)
    ) {
        Box(
            modifier = Modifier
                .size(39.dp)
                .then(coverModifier)
                .clip(coverShape)
                .background(BoneColor)
        ) {
            ItemThumbnail(
                thumbnailUrl = thumbnailUrl,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = coverShape,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 11.dp)
        ) {
            Text(
                text = title,
                style = TrackTitleStyle,
                color = Color.White.copy(alpha = if (isActive) 1f else 0.94f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = TrackSubtitleStyle,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        com.mochyy.mochymusic.ui.component.OnDeviceMarks(songId, modifier = Modifier.padding(end = 2.dp))
        if (trailing == null && liked != null && onLikeClick != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onLikeClick),
            ) {
                Icon(
                    painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription = stringResource(if (liked) R.string.action_remove_like else R.string.action_like),
                    tint = if (liked) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMenuClick)
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = stringResource(R.string.more_options),
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** stand-in rows while the songs are still loading */
@Composable
fun PlaylistTrackBones(rows: Int = 7) {
    BonesHost(modifier = Modifier.fillMaxWidth()) {
        repeat(rows) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = PlaylistInset, top = 9.dp, bottom = 9.dp)
            ) {
                Bone(width = 39.dp, height = 39.dp, shape = TrackShape)
                Spacer(Modifier.width(11.dp))
                Column {
                    Bone(width = 78.dp, height = 13.dp)
                    Spacer(Modifier.height(4.dp))
                    Bone(width = 45.dp, height = 9.dp)
                }
            }
        }
    }
}

/** the receipt-style footer: N SONGS · E N D  O F  L I N E · N MINUTES between two rules */
@Composable
fun PlaylistEndOfLine(
    songCount: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlaylistInset)
            .padding(top = 18.dp, bottom = 6.dp)
    ) {
        ReceiptRule()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(25.dp)
        ) {
            Text(
                text = "$songCount ${if (songCount == 1) "SONG" else "SONGS"}",
                style = ReceiptSmallStyle,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "END OF LINE",
                style = ReceiptCenterStyle,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            val minutes = (totalSeconds + 30) / 60
            Text(
                text = "$minutes ${if (minutes == 1) "MINUTE" else "MINUTES"}",
                style = ReceiptSmallStyle,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        ReceiptRule()
    }
}

@Composable
private fun ReceiptRule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = Color.White.copy(alpha = 0.24f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 0.75.dp.toPx(),
                )
            }
    )
}

/** header for the sections after the list, like "Featured Artist >" */
@Composable
fun PlaylistSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(start = PlaylistInset - 4.dp, end = PlaylistInset)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(text = title, style = SectionTitleStyle, color = Color.White, maxLines = 1)
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** big round artist photos under the list, most featured first */
@Composable
fun FeaturedArtistsRow(
    artists: List<FeaturedArtistUi>,
    onArtistClick: (FeaturedArtistUi) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Featured Artist",
    onTitleClick: (() -> Unit)? = null,
    showChevron: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PlaylistSectionTitle(title = title, onClick = onTitleClick, showChevron = showChevron)
        Spacer(Modifier.height(9.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PlaylistInset),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            items(items = artists, key = { it.id }) { artist ->
                val looked = rememberArtistThumbnail(artist.id.takeIf { artist.thumbnailUrl == null }, artist.name)
                val thumbnail = artist.thumbnailUrl ?: looked.value
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(95.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onArtistClick(artist) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(95.dp)
                            .clip(CircleShape)
                            .background(BoneColor)
                    ) {
                        val url = thumbnail
                        if (url != null) {
                            AsyncImage(
                                model = url.resize(300, 300),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.person),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(38.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = artist.name,
                        style = TrackTitleStyle.copy(fontSize = 13.sp),
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// artist photos already found this session, so re-opening a playlist doesn't hit the network again
private val artistThumbnailCache = ConcurrentHashMap<String, String>()

/** an artist's photo: memory, then the database, then YouTube (and remembered in the database) */
@Composable
fun rememberArtistThumbnail(artistId: String?, artistName: String): State<String?> {
    val database = LocalDatabase.current
    val state = remember(artistId) { mutableStateOf(artistId?.let { artistThumbnailCache[it] }) }
    LaunchedEffect(artistId) {
        val id = artistId ?: return@LaunchedEffect
        if (state.value != null) return@LaunchedEffect
        val url = withContext(Dispatchers.IO) {
            database.artist(id).firstOrNull()?.artist?.thumbnailUrl
                ?: YouTube.artist(id).getOrNull()?.artist?.thumbnail?.also { fetched ->
                    database.query {
                        insert(ArtistEntity(id = id, name = artistName, thumbnailUrl = fetched))
                    }
                }
        }
        if (url != null) {
            artistThumbnailCache[id] = url
            state.value = url
        }
    }
    return state
}
