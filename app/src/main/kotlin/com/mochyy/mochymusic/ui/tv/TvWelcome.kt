// TvWelcome.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.tv.PairTarget
import com.mochyy.mochymusic.link.tv.TvPairing
import com.mochyy.mochymusic.link.tv.TvProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// the light off her phone's screen, the blue of her hair
private val MascotGlow = Color(0xFF8FD0FF)

// the first thing every start shows: a quick "hii!" that pops up while the library opens behind it
@Composable
internal fun TvHello(onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(380, easing = TvEase))
        delay(520)
        alpha.animateTo(0f, tween(260))
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.tv_hii),
            style = TvText.Hero.copy(fontSize = 52.sp),
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha.value
                val scale = 0.86f + 0.14f * alpha.value
                scaleX = scale
                scaleY = scale
            },
        )
    }
}

// the welcome from the concept: the mark and the greeting on the left, the mascot holding up her phone on the right
@Composable
internal fun TvWelcome(onNext: () -> Unit) {
    val focus = remember { FocusRequester() }
    val seen = remember { HashSet<Any>() }
    val scope = rememberCoroutineScope()
    // a press sends everything off screen first, so it doesn't just cut to the next page
    val leave = remember { Animatable(0f) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .tvDots()
            .focusRequester(focus)
            // any button on the remote moves on, as the line on screen promises
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                if (leave.targetValue == 0f) {
                    scope.launch {
                        leave.animateTo(1f, tween(420, easing = TvEase))
                        onNext()
                    }
                }
                true
            }
            .focusable(),
    ) {
        TvMascot(
            leave = { leave.value },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxHeight(0.88f)
                .padding(end = 56.dp)
                .offset(y = 20.dp),
        )
        TvGreeting(
            seen = seen,
            leave = { leave.value },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 84.dp, bottom = 120.dp),
        )
    }
}

// she swings in from the right with a little overshoot, then floats and sways while her phone glows
@Composable
private fun TvMascot(leave: () -> Float, modifier: Modifier = Modifier) {
    // decoded off the main thread, and she only swings in once the picture is ready, so she never pops in halfway
    val painter = rememberAsyncImagePainter(R.drawable.tv_mascot)
    val arrive = remember { Animatable(0f) }
    LaunchedEffect(painter) {
        painter.state.first { it is AsyncImagePainter.State.Success || it is AsyncImagePainter.State.Error }
        delay(120)
        arrive.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 110f))
    }
    val idle = rememberInfiniteTransition(label = "mascotIdle")
    val bob by idle.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val sway by idle.animateFloat(-1f, 1f, infiniteRepeatable(tween(3700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val glow by idle.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.BottomEnd,
        modifier = modifier
            .graphicsLayer {
                val p = arrive.value
                val gone = leave()
                // she pivots from her feet, so the sway reads as a lean and not a wobble
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationX = (1f - p) * 240.dp.toPx() + gone * 280.dp.toPx()
                translationY = (1f - p) * 36.dp.toPx() + bob * 5.dp.toPx()
                rotationZ = (1f - p) * 8f + sway * 1.3f
                alpha = p.coerceIn(0f, 1f) * (1f - gone)
            }
            // the screen she's holding up gives off a soft light, read while drawing so it never recomposes
            .drawBehind {
                val center = Offset(size.width - size.height * 0.2f, size.height * 0.36f)
                val radius = size.height * 0.26f
                drawCircle(
                    Brush.radialGradient(listOf(MascotGlow.copy(alpha = 0.3f * glow), Color.Transparent), center, radius),
                    radius = radius,
                    center = center,
                )
            },
    )
}

// the mark spins into place, the greeting rises in a line at a time, and the hint keeps breathing until a press
@Composable
private fun TvGreeting(seen: MutableSet<Any>, leave: () -> Float, modifier: Modifier = Modifier) {
    val mark = remember { Animatable(0f) }
    LaunchedEffect(Unit) { mark.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 200f)) }
    val breathe = rememberInfiniteTransition(label = "hint")
    val hint by breathe.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "hintAlpha")
    Column(
        modifier.graphicsLayer {
            val gone = leave()
            translationX = -gone * 90.dp.toPx()
            alpha = 1f - gone
        },
    ) {
        Icon(
            painterResource(R.drawable.ic_mochy_mark),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    val p = mark.value
                    val scale = 0.3f + 0.7f * p
                    scaleX = scale
                    scaleY = scale
                    rotationZ = (1f - p) * -120f
                    alpha = p.coerceIn(0f, 1f)
                },
        )
        Spacer(Modifier.height(48.dp))
        Text(
            stringResource(R.string.tv_tap_anything),
            style = TvText.Item.copy(fontSize = 12.sp),
            modifier = Modifier.tvReveal("hint", seen, delayMillis = 640).graphicsLayer { alpha = hint },
        )
        Spacer(Modifier.height(6.dp))
        stringResource(R.string.tv_welcome).lines().forEachIndexed { index, line ->
            Text(
                line,
                style = TvText.Hero.copy(fontSize = 52.sp, lineHeight = 50.sp),
                modifier = Modifier.tvReveal("line$index", seen, delayMillis = 220 + index * 150),
            )
        }
    }
}

// "lets sync to your phone": a phone on the left showing where to go, the code and the steps on the right
@Composable
internal fun TvPairWelcome(pairing: TvPairing, onPaired: (TvProfile) -> Unit, onSkip: () -> Unit) {
    DisposableEffect(Unit) {
        pairing.open(PairTarget.NewProfile)
        onDispose { pairing.close() }
    }
    LaunchedEffect(Unit) { pairing.paired.collect { onPaired(it) } }
    val skipFocus = remember { FocusRequester() }
    val seen = remember { HashSet<Any>() }
    LaunchedEffect(Unit) { runCatching { skipFocus.requestFocus() } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            stringResource(R.string.tv_lets_sync),
            style = TvText.PageTitle.copy(fontSize = 24.sp, color = TvColors.Muted),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp).tvReveal("title", seen),
        )
        TvPhoneMock(
            tvName = pairing.code.value?.deviceName.orEmpty(),
            modifier = Modifier
                .padding(start = 94.dp, top = 140.dp)
                .width(maxWidth * 0.35f)
                .fillMaxHeight()
                .tvRiseIn(delayMillis = 120),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = maxWidth * 0.35f + 160.dp, top = 40.dp)
                .tvReveal("steps", seen, delayMillis = 260),
        ) {
            TvPairingSteps(pairing)
            Spacer(Modifier.height(28.dp))
            TvPillButton(stringResource(R.string.tv_skip_phone), onClick = onSkip, modifier = Modifier.focusRequester(skipFocus))
        }
    }
}

// a phone drawn from the concept's grey slab, with a tiny copy of mochy's tv page on it
@Composable
private fun TvPhoneMock(tvName: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(topStart = 52.dp, topEnd = 52.dp)).background(Color(0xFF1F1F1F))) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 30.dp)) {
            Text(stringResource(R.string.tv_link_title), style = TvText.Item.copy(fontSize = 16.sp))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.tv_link_found), style = TvText.Sub)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TvColors.CardRaised).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF3A3A3A)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_mochy_mark), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(tvName, style = TvText.Item)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(6) { Box(Modifier.size(width = 22.dp, height = 28.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF2E2E2E))) }
            }
        }
    }
}

// a person without a phone gets a name, typed with the tv's own keyboard
@Composable
internal fun TvNameEntry(onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(Modifier.tvPopIn(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.tv_whats_your_name), style = TvText.PageTitle.copy(color = TvColors.Muted))
            Spacer(Modifier.height(24.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                textStyle = TvText.PageTitle.copy(fontSize = 24.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(name) }),
                modifier = Modifier
                    .width(360.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E2E2E))
                    .focusRequester(focus)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(20.dp))
            TvPillButton(stringResource(R.string.tv_continue), onClick = { onDone(name) }, bright = true)
        }
    }
}
