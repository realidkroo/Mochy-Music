// ControllingPill.kt

package com.mochyy.mochymusic.ui.screens.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.remote.RemoteTarget
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val PillSurface = Color(0xFF1C1C1F)

// "controlling living room tv": a small pill at the top left while this phone steers another device; tapping it opens the remote
@Composable
fun ControllingPill(modifier: Modifier = Modifier) {
    val link = rememberDevicesLink()
    val target by link.remote.target.collectAsState()
    val reachable by link.remote.reachable.collectAsState()
    // only the play state matters here, so the pill doesn't recompose on every position the device reports
    val playing by remember { link.remote.now.map { it?.playing == true }.distinctUntilChanged() }.collectAsState(initial = false)
    val menu = LocalMenuState.current
    // the name stays put while the pill slides away
    var shown by remember { mutableStateOf<RemoteTarget?>(null) }
    LaunchedEffect(target) { target?.let { shown = it } }

    AnimatedVisibility(
        visible = target != null,
        enter = fadeIn(tween(220)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { -it },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it },
        modifier = modifier,
    ) {
        val device = target ?: shown ?: return@AnimatedVisibility
        val interaction = remember { MutableInteractionSource() }
        Row(
            Modifier
                .pressBounce(interaction, pressedScale = 0.95f)
                .clip(CircleShape)
                .background(PillSurface)
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                .clickable(interactionSource = interaction, indication = RemoteFocus) { menu.showRemoteControl(link) }
                .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !reachable -> Color(0xFFFF8A80)
                            playing -> LiveGreen
                            else -> Color.White.copy(alpha = 0.6f)
                        },
                    ),
            )
            Spacer(Modifier.width(7.dp))
            Icon(device.kind.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                stringResource(R.string.remote_controlling, device.name),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
    }
}
