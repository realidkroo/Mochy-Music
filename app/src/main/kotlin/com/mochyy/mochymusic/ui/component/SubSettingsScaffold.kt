// SubSettingsScaffold.kt

package com.mochyy.mochymusic.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.util.lerp
import coil3.compose.rememberAsyncImagePainter
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.screens.search.rememberScrollCollapseProgress
import kotlin.math.PI
import kotlin.math.sin
import com.mochyy.mochymusic.ui.utils.stableStatusBars

private val PagePadding = 20.dp
private val BackButtonSize = 40.dp

private val HeaderTopPadding = 16.dp

private val BackButtonCenterY = HeaderTopPadding + BackButtonSize / 2

private val ExpandedTitleTop = HeaderTopPadding + BackButtonSize + 12.dp

private val CollapsedTitleX = BackButtonSize + 14.dp

private val ExpandedHeaderHeight = 118.dp

private const val CollapsedTitleScale = 0.62f

private const val TitleMorphMaxBlurPx = 26f

private val OverlayEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private val SearchBarHeight = 52.dp

@Composable
fun SubSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    searchBar: (@Composable () -> Unit)? = null,
    /** the page this one sits under, written beside the back button ("‹ Audio Lab") */
    backLabel: String? = null,
    /** a small mark beside the title, like a beta tag; it fades as the title shrinks into the bar */
    titleBadge: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val glassState = remember { GlassState() }

    val headerFoundationColor = Color.Black

    // Latch-and-settle collapse, same as Library/Search: once the threshold is crossed the morph
    // always finishes smoothly, even after the user's finger lifts mid-scroll.
    val collapse = rememberScrollCollapseProgress(scrollState)
    // only ever read in layout, draw or a snapshot flow, so the 900 ms morph doesn't recompose the scaffold every frame
    val collapseProgress by collapse

    var titleHeightPx by remember { mutableIntStateOf(0) }

    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    val statusBarTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val totalHeaderHeight = statusBarTop + ExpandedHeaderHeight
    // a highlighted row lands a little under the collapsed header, where it's in plain view
    val settingsScroll = remember(scrollState, statusBarTop) {
        SettingsScroll(scrollState, statusBarTop + HeaderTopPadding + BackButtonSize + 20.dp)
    }

    var searchOverlayVisible by remember { mutableStateOf(false) }

    // The search pill is a single element that actually resizes (real layout constraints, not a
    // scaled graphicsLayer) and slides from its resting spot below the header to the icon slot at
    // top-right, so it reads as one shape warping into a circle rather than one thing fading out
    // while an unrelated icon fades in elsewhere. The two rects are plain layout constants - no
    // live position measurement feeding back into the content a RenderNode is capturing.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val expandedBarX = PagePadding
    val expandedBarY = totalHeaderHeight
    val expandedBarWidth = screenWidth - PagePadding * 2
    val collapsedBarX = screenWidth - PagePadding - BackButtonSize
    val collapsedBarY = statusBarTop + HeaderTopPadding

    LaunchedEffect(collapse) {
        snapshotFlow { collapse.value < 0.5f }.collect { opened -> if (opened) searchOverlayVisible = false }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()

                .glassRoot(glassState, isActive = { !scrollState.isScrollInProgress })
                .verticalScroll(scrollState)
                .padding(horizontal = PagePadding)
        ) {
            Spacer(modifier = Modifier.height(totalHeaderHeight))

            if (searchBar != null) {
                // Reserves the pill's resting space in the normal flow; the pill itself is drawn
                // as an overlay below so it can slide out over the header instead of being
                // clipped by the scrolling column.
                Spacer(modifier = Modifier.height(SearchBarHeight + 16.dp))
            }

            CompositionLocalProvider(LocalSettingsScroll provides settingsScroll) { content() }

            Spacer(modifier = Modifier.height(bottomInset.calculateBottomPadding() + 24.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeaderHeight)
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = collapseProgress }
        ) {
            ProgressiveGlassBackground(
                state = glassState,
                maxBlurRadius = { 40f * collapseProgress },
                foundationColor = headerFoundationColor.copy(alpha = 0.55f),
                direction = BlurDirection.BottomToTop,
                steps = 2,
                modifier = Modifier.fillMaxSize()
            )

            val progressive = rememberProgressiveBlur()
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val gradient = if (progressive) {
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.90f),
                                0.20f to Color.Black.copy(alpha = 0.50f),
                                0.45f to Color.Black.copy(alpha = 0.18f),
                                0.75f to Color.Transparent,
                            )
                        } else {
                            SolidColor(Color.Black.copy(alpha = FlatBarScrimAlpha))
                        }
                        onDrawBehind { drawRect(brush = gradient) }
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeaderHeight)
                .align(Alignment.TopCenter)
                .padding(top = statusBarTop, start = PagePadding, end = PagePadding)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = HeaderTopPadding)
                    .size(BackButtonSize)
                    .clip(CircleShape)
                    .background(Color(0xFF2C2C2E))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_ios),
                    contentDescription = stringResource(R.string.back_content_desc),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (backLabel != null) {
                // gives way to the title, which slides into this spot as the page collapses
                Text(
                    text = backLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(top = HeaderTopPadding, start = BackButtonSize + 10.dp)
                        .height(BackButtonSize)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .graphicsLayer { alpha = (1f - collapseProgress * 2.5f).coerceIn(0f, 1f) }
                        .clickable(onClick = onBack)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .onSizeChanged { titleHeightPx = it.height }
                    .graphicsLayer {

                        val p = collapseProgress

                        transformOrigin = TransformOrigin(0f, 0f)
                        val scale = lerp(1f, CollapsedTitleScale, p)
                        scaleX = scale
                        scaleY = scale

                        val collapsedTop =
                            BackButtonCenterY.toPx() - (titleHeightPx * CollapsedTitleScale) / 2f
                        translationX = lerp(0f, CollapsedTitleX.toPx(), p)
                        translationY = lerp(ExpandedTitleTop.toPx(), collapsedTop, p)

                        val blurPx = sin(p.coerceIn(0f, 1f) * PI.toFloat()) * TitleMorphMaxBlurPx
                        // DECAL is API 31. The argument is read before BlurEffectCache.get can
                        // return early, so naming it outside this check threw NoSuchFieldError on
                        // Android 11 and older the first time the header drew: every settings
                        // page crashed on opening. Below 31 there's no blur either way.
                        renderEffect = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            BlurEffectCache.element(blurPx, android.graphics.Shader.TileMode.DECAL)
                        } else {
                            null
                        }
                    },
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (titleBadge != null) {
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.graphicsLayer { alpha = (1f - collapseProgress * 2.5f).coerceIn(0f, 1f) }) { titleBadge() }
                }
            }
        }

        if (searchBar != null) {
            val iconTappable by remember { derivedStateOf { collapse.value > 0.6f } }

            // One element whose actual layout size and position are animated (a true warp, not a
            // scaled copy): the real search field fades out as it shrinks, an icon fades in on top
            // of it, and because both share the same shrinking frame they read as one shape
            // deforming into a circle rather than a new element appearing elsewhere.
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val p = collapse.value
                        val width = lerpDp(expandedBarWidth, BackButtonSize, p).roundToPx()
                        val height = lerpDp(SearchBarHeight, BackButtonSize, p).roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(width, height) {
                            placeable.place(
                                lerpDp(expandedBarX, collapsedBarX, p).roundToPx(),
                                lerpDp(expandedBarY, collapsedBarY, p).roundToPx(),
                            )
                        }
                    }
                    .graphicsLayer {
                        shape = RoundedCornerShape(lerpDp(26.dp, BackButtonSize / 2, collapse.value))
                        clip = true
                    }
                    .background(Color(0xFF2C2C2E))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - collapse.value / 0.6f).coerceIn(0f, 1f) }
                ) {
                    searchBar()
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ((collapse.value - 0.45f) / 0.55f).coerceIn(0f, 1f) }
                        .clip(CircleShape)
                        .clickable(enabled = iconTappable) {
                            searchOverlayVisible = !searchOverlayVisible
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = if (searchOverlayVisible) {
                            painterResource(R.drawable.close)
                        } else {
                            rememberAsyncImagePainter(model = R.raw.search)
                        },
                        contentDescription = if (searchOverlayVisible) "Hide search" else "Search",
                        tint = Color.White,
                        modifier = Modifier.size(if (searchOverlayVisible) 18.dp else 16.dp)
                    )
                }
            }

            BackHandler(enabled = searchOverlayVisible) { searchOverlayVisible = false }

            AnimatedVisibility(
                visible = searchOverlayVisible,
                enter = fadeIn(tween(260, easing = OverlayEasing)) +
                    slideInVertically(tween(260, easing = OverlayEasing)) { -it / 2 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 2 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(
                        top = statusBarTop + HeaderTopPadding + BackButtonSize + 10.dp,
                        start = PagePadding,
                        end = PagePadding,
                    ),
            ) {
                searchBar()
            }
        }
    }
}

@Composable
fun SubSettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
            .background(Color(0xFF1C1C1E))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = com.mochyy.mochymusic.ui.theme.InterFontFamily,
                    fontSize = 16.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_search_placeholder),
                            fontFamily = com.mochyy.mochymusic.ui.theme.InterFontFamily,
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )

            if (query.isNotEmpty()) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.settings_clear_search),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}
