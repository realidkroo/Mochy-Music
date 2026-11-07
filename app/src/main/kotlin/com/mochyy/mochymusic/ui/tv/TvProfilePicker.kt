// TvProfilePicker.kt

package com.mochyy.mochymusic.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.tv.TvProfile
import com.mochyy.mochymusic.link.tv.TvProfiles

// "whos listening": every profile's circle, the last one used focused first, and a plus for someone new
@Composable
internal fun TvProfilePicker(onPick: (TvProfile) -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current
    val profiles by TvProfiles.profiles.collectAsState()
    val activeId by TvProfiles.activeId.collectAsState()
    var removing by remember { mutableStateOf<TvProfile?>(null) }
    val lastUsed = remember { FocusRequester() }
    val seen = remember { HashSet<Any>() }
    LaunchedEffect(Unit) { runCatching { lastUsed.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.tv_whos_listening), style = TvText.PageTitle.copy(fontSize = 40.sp, color = TvColors.Muted))
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                profiles.forEachIndexed { index, profile ->
                    val focusFirst = profile.id == activeId || (activeId == null && index == 0)
                    TvProfileCircle(
                        name = profile.name,
                        reveal = Modifier.tvReveal(profile.id, seen, delayMillis = index * 80),
                        modifier = if (focusFirst) Modifier.focusRequester(lastUsed) else Modifier,
                        onLongClick = { if (profile.id != activeId) removing = profile },
                        onClick = { onPick(profile) },
                    ) {
                        if (profile.avatarPath != null) {
                            AsyncImage(model = "file://${profile.avatarPath}", contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(profile.name.take(1).uppercase(), style = TvText.Hero.copy(fontSize = 48.sp, color = Color.Black))
                        }
                    }
                }
                TvProfileCircle(name = stringResource(R.string.tv_add_profile), reveal = Modifier.tvReveal("add", seen, delayMillis = profiles.size * 80), onClick = onAdd) {
                    Icon(painterResource(R.drawable.add), contentDescription = null, tint = Color.Black, modifier = Modifier.size(64.dp))
                }
            }
        }
        removing?.let { profile ->
            TvConfirm(
                text = stringResource(R.string.tv_remove_profile, profile.name),
                confirm = stringResource(R.string.tv_remove),
                onConfirm = {
                    TvProfiles.remove(context, profile.id)
                    removing = null
                },
                onDismiss = { removing = null },
            )
        }
    }
}

@Composable
private fun TvProfileCircle(
    name: String,
    reveal: Modifier,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(reveal, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier
                .size(128.dp)
                .tvClickable(shape = CircleShape, focusedScale = 1.08f, onLongClick = onLongClick, onClick = onClick)
                .clip(CircleShape)
                .background(Color(0xFFD9D9D9)),
            contentAlignment = Alignment.Center,
        ) { content() }
        Spacer(Modifier.height(12.dp))
        Text(name, style = TvText.Item.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(128.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// a yes-or-no in the middle of the screen, cancel focused first so a stray press never deletes anything
@Composable
internal fun TvConfirm(text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.tvPopIn().clip(RoundedCornerShape(26.dp)).background(TvColors.Card).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text, style = TvText.Item.copy(fontSize = 16.sp))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvPillButton(stringResource(R.string.tv_cancel), onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus))
                TvPillButton(confirm, onClick = onConfirm, bright = true)
            }
        }
    }
}
