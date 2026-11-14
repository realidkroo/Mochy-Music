// the first onboarding page: the mochy girl showing off her phone over drifting welcomes, a tap anywhere moves on

package com.mochyy.mochymusic.ui.screens.setup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.screens.setup.onboarding.OnbTitleTracking
import com.mochyy.mochymusic.ui.screens.setup.welcome.GirlArt
import com.mochyy.mochymusic.ui.screens.setup.welcome.GirlMotion
import com.mochyy.mochymusic.ui.screens.setup.welcome.WelcomeColors
import com.mochyy.mochymusic.ui.screens.setup.welcome.WelcomeDots
import com.mochyy.mochymusic.ui.screens.setup.welcome.WelcomeGirl
import com.mochyy.mochymusic.ui.screens.setup.welcome.WelcomeWords
import com.mochyy.mochymusic.ui.utils.AppMotion
import com.mochyy.mochymusic.ui.utils.HighRefreshWhile
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.max
import kotlin.math.min

@Composable
fun WelcomeStep(isHiding: Boolean, onNext: () -> Unit) {
    val clock = rememberWelcomeClock()
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    val leave by animateFloatAsState(
        targetValue = if (isHiding) 1f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "welcomeLeave",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(WelcomeColors.Page)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.next),
                role = Role.Button,
                onClick = onNext,
            )
            // everything here moves every frame; bounded, it never makes the app behind the sheet repaint
            .damageBoundary(),
    ) {
        val layout = remember(maxWidth, maxHeight) { welcomeLayout(maxWidth, maxHeight) }
        val u = layout.unit

        WelcomeDots(layout.dots)
        WelcomeWords(clock.seconds, layout.wordsSize, layout.wordsTop)

        WelcomeGirl(
            motion = clock.girl,
            seconds = clock.seconds,
            modifier = Modifier
                .offset(x = layout.girlLeft, y = layout.girlTop)
                .size(layout.girlWidth, layout.girlHeight)
                .graphicsLayer {
                    translationY = leave * 56.dp.toPx()
                    alpha = 1f - leave
                },
        )

        // the bottom of the art melts into the page under the closing line
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(layout.fadeHeight)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to WelcomeColors.Page.copy(alpha = 0.86f),
                        1f to WelcomeColors.Page,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .padding(start = layout.titleStart, top = layout.titleTop)
                .graphicsLayer {
                    alpha = enter.value * (1f - leave)
                    translationY = (1f - enter.value) * 18.dp.toPx()
                },
        ) {
            Text(
                text = stringResource(R.string.welcome_to),
                color = Color(0xFFA7A7A7),
                fontSize = (26f * u).sp,
                lineHeight = (30f * u).sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = OnbTitleTracking,
            )
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = (58f * u).sp,
                lineHeight = (60f * u).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = OnbTitleTracking,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = layout.titleStart, end = 24.dp, bottom = 30.dp)
                .graphicsLayer { alpha = enter.value * (1f - leave) },
        ) {
            Text(
                text = stringResource(R.string.welcome_lets_listen),
                color = Color(0xFFD0D0D0),
                fontSize = (23f * u).sp,
                lineHeight = (27f * u).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = OnbTitleTracking,
            )
            Text(
                text = stringResource(R.string.welcome_tap_anywhere),
                color = Color(0xFF8C8C8C),
                fontSize = (14f * u).sp,
                lineHeight = (18f * u).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// the page's one clock: each frame it moves the girl, then the rings and words read the same time
private class WelcomeClock {
    val seconds = mutableFloatStateOf(0f)
    val girl = GirlMotion()
    var running by mutableStateOf(false)
        private set

    suspend fun run() {
        running = true
        try {
            var last = -1L
            var t = seconds.floatValue
            while (true) {
                withFrameNanos { now ->
                    // a stall only counts as a short step, so nothing leaps forward after a hitch
                    if (last >= 0L) t += ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                    girl.update(t)
                    seconds.floatValue = t
                }
            }
        } finally {
            running = false
        }
    }
}

// runs only while someone can see it: not under the player, not in the background, not with animations off
@Composable
private fun rememberWelcomeClock(): WelcomeClock {
    val clock = remember { WelcomeClock() }
    val obscured = LocalAppContentObscured.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(clock, obscured, lifecycle) {
        combine(snapshotFlow { !obscured.value && AppMotion.enabled }, lifecycle.currentStateFlow) { free, state ->
            free && state.isAtLeast(Lifecycle.State.STARTED)
        }
            .distinctUntilChanged()
            .collectLatest { visible -> if (visible) clock.run() }
    }
    // android 15+ drops draw-only animation to 60hz unless something asks, and this should glide at 120
    HighRefreshWhile { clock.running }
    return clock
}

// where everything sits, from one size unit so a tablet gets the same picture scaled up
private class WelcomeLayout(
    val unit: Float,
    val titleStart: Dp,
    val titleTop: Dp,
    val girlLeft: Dp,
    val girlTop: Dp,
    val girlWidth: Dp,
    val girlHeight: Dp,
    val wordsSize: Dp,
    val wordsTop: Dp,
    val dots: Dp,
    val fadeHeight: Dp,
)

// tall windows get the phone design; wide ones (tablets, foldables, landscape) put the girl on the right
private fun welcomeLayout(width: Dp, height: Dp): WelcomeLayout {
    val w = width.value
    val h = height.value
    val shape = GirlArt.FrameWidth / GirlArt.FrameHeight
    if (w / h < 0.9f) {
        val u = min(w / 393f, h / 820f).coerceIn(0.8f, 1.9f)
        val titleTop = max(64f, 76f * u)
        // big like a wallpaper: she spans the width and is cropped at the hips, under the title
        val girlTop = max(titleTop + 98f * u, h * 0.25f)
        val girlHeight = min(w * 1.08f / shape, (h - girlTop) * 1.12f)
        val girlWidth = girlHeight * shape
        return WelcomeLayout(
            unit = u,
            titleStart = max(24f, 36f * u).dp,
            titleTop = titleTop.dp,
            girlLeft = (w * 0.985f - girlWidth * GirlArt.FingertipsAcross).dp,
            girlTop = girlTop.dp,
            girlWidth = girlWidth.dp,
            girlHeight = girlHeight.dp,
            wordsSize = (128f * u).dp,
            wordsTop = (h * 0.235f).dp,
            dots = (17.5f * u).dp,
            fadeHeight = (h * 0.3f).dp,
        )
    }
    val u = min(w / 900f, h / 520f).coerceIn(0.7f, 1.8f)
    val girlWidth = min(h * 1.16f * shape, w * 0.62f)
    return WelcomeLayout(
        unit = u,
        titleStart = (48f * u).dp,
        titleTop = max(56f, h * 0.16f).dp,
        girlLeft = (w * 0.975f - girlWidth * GirlArt.FingertipsAcross).coerceAtLeast(w * 0.4f).dp,
        girlTop = max(8f, h * 0.02f).dp,
        girlWidth = girlWidth.dp,
        girlHeight = (girlWidth / shape).dp,
        wordsSize = (128f * u).dp,
        wordsTop = (h * 0.06f).dp,
        dots = (17.5f * u).dp,
        fadeHeight = (h * 0.26f).dp,
    )
}
