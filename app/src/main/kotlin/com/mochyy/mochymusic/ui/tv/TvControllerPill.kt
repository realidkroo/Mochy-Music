// TvControllerPill.kt

package com.mochyy.mochymusic.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.tv.TvControl

// "controlled from kei's pixel": a small pill under the logo while a phone steers the tv, so the room knows who's picking
@Composable
internal fun TvControllerPill(modifier: Modifier = Modifier) {
    val controller by TvControl.controller.collectAsState()
    // the name stays put while the pill fades out
    var shownName by remember { mutableStateOf("") }
    LaunchedEffect(controller) { controller?.let { shownName = it } }

    AnimatedVisibility(
        visible = controller != null,
        enter = fadeIn(tween(260)) + slideInHorizontally(tween(420, easing = TvEase)) { -it / 3 },
        exit = fadeOut(tween(220)) + slideOutHorizontally(tween(300)) { -it / 3 },
        modifier = modifier,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(TvColors.Pill)
                .border(1.dp, TvColors.PillEdge, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF5BD68A)))
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.smartphone), contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.tv_controlled_from, controller ?: shownName),
                style = TvText.Sub.copy(fontSize = 12.sp, color = Color.White),
                maxLines = 1,
            )
        }
    }
}
