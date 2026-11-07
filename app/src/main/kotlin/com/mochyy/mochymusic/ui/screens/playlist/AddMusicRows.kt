// AddMusicRows.kt

package com.mochyy.mochymusic.ui.screens.playlist

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.ui.component.Bone
import com.mochyy.mochymusic.ui.component.BonesHost
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.ui.component.RemoteFocus

/*
 * The pieces the "Add music" sheet is drawn from: a song row with its tick box, the two chips
 * under the search, and the loading bones and notes that stand in for an empty list.
 */

/**
 * A song as the mock draws it: tick box, rounded cover, title and artists. A song that's already
 * in the playlist stays in the list, ticked and dimmed, so it's clear why it can't be picked.
 */
@Composable
internal fun PickRow(
    id: String,
    title: String,
    artists: String,
    thumbnailUrl: String?,
    inPlaylist: Boolean,
    picks: AddMusicPicks,
    toMetadata: () -> MediaMetadata,
) {
    // Only the row whose tick changed recomposes, not every row on screen.
    val picked by remember(id) { derivedStateOf { id in picks.ids } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (inPlaylist) 0.45f else 1f }
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                enabled = !inPlaylist,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = { picks.toggle(id, toMetadata) },
            )
            .padding(vertical = 4.dp),
    ) {
        PickBox(checked = inPlaylist || picked)
        Spacer(Modifier.width(14.dp))
        AsyncImage(
            model = thumbnailUrl?.resize(144, 144),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MenuRowSurface),
        )
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (inPlaylist) "In this playlist · $artists" else artists,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The mock's square tick box: soft grey when empty, white with a dark tick when picked. */
@Composable
private fun PickBox(checked: Boolean) {
    val fill by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pickBox",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            // Read in the draw pass, so the tick animating doesn't recompose the row.
            .drawBehind { drawRect(lerp(Color.White.copy(alpha = 0.2f), Color.White, fill)) },
    ) {
        Icon(
            painter = painterResource(R.drawable.check),
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer {
                    alpha = fill
                    scaleX = 0.6f + 0.4f * fill
                    scaleY = 0.6f + 0.4f * fill
                },
        )
    }
}

/** One of the two chips under the search: white when it's the list being shown, grey otherwise. */
@Composable
internal fun AddMusicChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) Color.White else MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** Grey stand-ins shaped like the rows while songs load, the same look as the mock. */
@Composable
internal fun PickRowBones(rows: Int = 6) {
    BonesHost(modifier = Modifier.fillMaxWidth()) {
        repeat(rows) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Bone(width = 18.dp, height = 18.dp, shape = RoundedCornerShape(5.dp))
                Spacer(Modifier.width(14.dp))
                Bone(width = 48.dp, height = 48.dp, shape = RoundedCornerShape(11.dp))
                Spacer(Modifier.width(13.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Bone(width = 104.dp, height = 12.dp)
                    Bone(width = 78.dp, height = 8.dp)
                }
            }
        }
    }
}

@Composable
internal fun SheetNote(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 28.dp),
    )
}
