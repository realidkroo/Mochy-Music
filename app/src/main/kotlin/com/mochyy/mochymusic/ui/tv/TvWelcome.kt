// TvWelcome.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.tv.PairTarget
import com.mochyy.mochymusic.link.tv.TvPairing
import com.mochyy.mochymusic.link.tv.TvProfile

// the first thing every start shows: a quick "hii!" while the library opens behind it
@Composable
internal fun TvHello(onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(380))
        kotlinx.coroutines.delay(520)
        alpha.animateTo(0f, tween(260))
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.tv_hii),
            style = TvText.Hero.copy(fontSize = 52.sp),
            modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
        )
    }
}

// the welcome from the concept: the mark and the greeting on the left, the mascot holding up her phone on the right
@Composable
internal fun TvWelcome(onNext: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .tvDots()
            .focusRequester(focus)
            // any button on the remote moves on, as the line on screen promises
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    onNext()
                    true
                } else {
                    false
                }
            }
            .focusable(),
    ) {
        Image(
            painter = painterResource(R.drawable.tv_mascot),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomEnd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxHeight(0.9f)
                .padding(end = 40.dp)
                .offset(y = 20.dp),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 84.dp, bottom = 120.dp)) {
            Icon(painterResource(R.drawable.ic_mochy_mark), contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(48.dp))
            Text(stringResource(R.string.tv_tap_anything), style = TvText.Item.copy(fontSize = 12.sp))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.tv_welcome), style = TvText.Hero.copy(fontSize = 52.sp, lineHeight = 50.sp))
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
    LaunchedEffect(Unit) { runCatching { skipFocus.requestFocus() } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            stringResource(R.string.tv_lets_sync),
            style = TvText.PageTitle.copy(fontSize = 24.sp, color = TvColors.Muted),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp),
        )
        TvPhoneMock(
            tvName = pairing.code.value?.tvName.orEmpty(),
            modifier = Modifier
                .padding(start = 94.dp, top = 140.dp)
                .width(maxWidth * 0.35f)
                .fillMaxHeight(),
        )
        Column(Modifier.align(Alignment.CenterStart).padding(start = maxWidth * 0.35f + 160.dp, top = 40.dp)) {
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
