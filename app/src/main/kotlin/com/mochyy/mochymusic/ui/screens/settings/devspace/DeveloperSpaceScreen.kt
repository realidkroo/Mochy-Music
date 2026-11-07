// dev spaces: a pager with a page for each person who makes mochy, the repo numbers are fetched on every open

package com.mochyy.mochymusic.ui.screens.settings.devspace

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.BlurDirection
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.ProgressiveGlassBackground
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.utils.stableSystemBars
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.mochyy.mochymusic.ui.component.RemoteFocus

/** how long the picker stays open showing names after the last tap or swipe */
private const val PickerOpenMs = 2600L

@Composable
fun DeveloperSpaceScreen(navController: NavController, initialPage: Int = 0) {
    val stats by DevSpaceRepository.current.collectAsState()
    val loading by DevSpaceRepository.loading.collectAsState()

    // every open asks GitHub again; the playground page reuses what this fetched
    LaunchedEffect(Unit) { DevSpaceRepository.refresh(BuildConfig.BUILD_TIME_MILLIS, force = true) }

    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, DevPeople.lastIndex)) { DevPeople.size }
    val scrollStates = DevPeople.map { rememberScrollState() }
    val scope = rememberCoroutineScope()

    var pickerOpen by remember { mutableStateOf(false) }
    // swiping to someone else shows their name for a moment, like tapping the capsule would
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { pickerOpen = true }
    }
    LaunchedEffect(pickerOpen, pagerState.settledPage) {
        if (pickerOpen) {
            delay(PickerOpenMs)
            pickerOpen = false
        }
    }

    val density = LocalDensity.current
    val topInset = WindowInsets.stableSystemBars.asPaddingValues().calculateTopPadding()
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val bannerHeight = topInset + 118.dp
    // the bar frosts over as the page you're on scrolls its banner away
    val barRangePx = with(density) { 90.dp.toPx() }
    val barP by remember {
        derivedStateOf {
            val scrolled = scrollStates[pagerState.currentPage].value
            FastOutSlowInEasing.transform((scrolled / barRangePx).coerceIn(0f, 1f))
        }
    }
    val glassState = remember { GlassState() }
    val ready = stats != null || !loading

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // glassRoot wraps the content only, the glass bar is a sibling or it crashes (see docs/notes/settings.md)
        Box(
            Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { barP > 0f }),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val person = DevPeople[page]
                DevProfilePage(
                    person = person,
                    scrollState = scrollStates[page],
                    bannerHeight = bannerHeight,
                    modifier = Modifier.graphicsLayer {
                        // the page leaving sinks back a little and dims, so a swipe has some depth
                        val offset = abs(pagerState.currentPage - page + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                        val s = 1f - 0.06f * offset
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.45f * offset
                    },
                ) {
                    PlaygroundCard(stats = stats, settled = ready, onClick = { navController.navigate(DevPlaygroundRoute) })
                    Spacer(Modifier.height(maxOf(160.dp, bottomInset + 140.dp)))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + 72.dp)
                .graphicsLayer { alpha = barP },
        ) {
            if (barP > 0.01f) {
                ProgressiveGlassBackground(
                    state = glassState,
                    maxBlurRadius = { 50f * barP },
                    foundationColor = Color.Black.copy(alpha = 0.5f),
                    direction = BlurDirection.BottomToTop,
                    steps = 3,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val progressiveBar = com.mochyy.mochymusic.ui.component.rememberProgressiveBlur()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (progressiveBar) {
                            Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.7f), Color.Transparent))
                        } else {
                            androidx.compose.ui.graphics.SolidColor(Color.Black.copy(alpha = com.mochyy.mochymusic.ui.component.FlatBarScrimAlpha))
                        },
                    ),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = DevSideInset - 8.dp, top = topInset + 14.dp),
        ) {
            BackButton(onClick = { navController.navigateUp() })
            Spacer(Modifier.size(8.dp))
            DevPersonPicker(
                people = DevPeople,
                pagerState = pagerState,
                expanded = pickerOpen,
                onTap = { index ->
                    when {
                        index != pagerState.currentPage -> {
                            pickerOpen = true
                            scope.launch {
                                pagerState.animateScrollToPage(index, animationSpec = spring(dampingRatio = 0.86f, stiffness = 260f))
                            }
                        }
                        // a second tap on whoever you're on folds it away again
                        else -> pickerOpen = !pickerOpen
                    }
                },
            )
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pressBounce(interaction)
            .size(36.dp)
            .clip(CircleShape)
            .background(DevBarButtonFill)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick),
    ) {
        Icon(painterResource(R.drawable.chevron_leftpx), contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
