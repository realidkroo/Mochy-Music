// storage → choose where downloads or the cache are saved: the phone or a memory card, then a move and one restart

package com.mochyy.mochymusic.ui.screens.settings.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.playback.MoveStep
import com.mochyy.mochymusic.playback.StoragePlace
import com.mochyy.mochymusic.playback.StoredKind
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.detail.PlaylistSelectTick
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.screens.library.showConfirm
import com.mochyy.mochymusic.viewmodels.PlaceChoice
import com.mochyy.mochymusic.viewmodels.StorageViewModel

// what the sheet and its button both need: the drives on offer once they're read, and the one tapped
@Stable
private class PlacePick {
    var choice by mutableStateOf<PlaceChoice?>(null)
    var picked by mutableStateOf<StoragePlace?>(null)

    // a drive that fits what's moving, with a little room to spare
    fun fits(place: StoragePlace): Boolean = place.free > (choice?.bytesToMove ?: 0L) + 50_000_000L
}

fun PopupSheetState.showStoragePlaceSheet(viewModel: StorageViewModel, kind: StoredKind) {
    val popup = this
    val pick = PlacePick()
    viewModel.clearMoveFailure()
    show(
        buttonBar = {
            val step by viewModel.moveStep.collectAsState()
            val choice = pick.choice
            val target = pick.picked
            val moving = step as? MoveStep.Working
            val canMove = moving == null && choice != null && target != null && target.root != choice.current.root && pick.fits(target)
            val title = stringResource(
                if (kind == StoredKind.Downloads) R.string.storage_place_move_downloads_title else R.string.storage_place_move_cache_title,
                target?.label.orEmpty(),
            )
            val message = if (kind == StoredKind.Downloads) {
                stringResource(R.string.storage_place_move_downloads_message, formatSize(choice?.bytesToMove ?: 0L))
            } else {
                stringResource(R.string.storage_place_move_cache_message)
            }
            val action = stringResource(R.string.storage_place_move_action)
            SheetPillButton(
                text = when {
                    moving != null -> stringResource(R.string.storage_moving, (moving.progress * 100).toInt())
                    else -> stringResource(R.string.storage_place_move)
                },
                enabled = canMove,
                onClick = {
                    val place = target ?: return@SheetPillButton
                    popup.showConfirm(title = title, message = message, action = action) { viewModel.move(kind, place) }
                },
            )
        },
    ) {
        PlaceSheetContent(viewModel, kind, pick)
    }
}

@Composable
private fun PlaceSheetContent(viewModel: StorageViewModel, kind: StoredKind, pick: PlacePick) {
    LaunchedEffect(kind) { if (pick.choice == null) pick.choice = viewModel.placeChoice(kind) }
    val step by viewModel.moveStep.collectAsState()
    val choice = pick.choice

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        SheetLabel(stringResource(if (kind == StoredKind.Downloads) R.string.storage_place_sheet_downloads else R.string.storage_place_sheet_cache))
        Text(
            stringResource(if (kind == StoredKind.Downloads) R.string.storage_place_note_downloads else R.string.storage_place_note_cache),
            style = storageText(13f, FontWeight.Medium, StorageMuted, -0.1f),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        if (choice == null) {
            // the rows' height, so the sheet doesn't jump once the drives are read
            Spacer(Modifier.height(140.dp))
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choice.places.forEach { place ->
                val selected = (pick.picked ?: choice.current).root == place.root
                PlaceRow(
                    place = place,
                    selected = selected,
                    isCurrent = place.root == choice.current.root,
                    fits = place.root == choice.current.root || pick.fits(place),
                    onClick = { pick.picked = place },
                )
            }
        }
        val note = when {
            step is MoveStep.Failed -> stringResource(R.string.storage_move_failed)
            choice.places.size == 1 -> stringResource(R.string.storage_place_only_phone)
            else -> stringResource(R.string.storage_place_card_note)
        }
        Text(
            note,
            style = storageText(12f, FontWeight.Medium, if (step is MoveStep.Failed) StorageDanger else StorageMuted, -0.1f),
            modifier = Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun PlaceRow(place: StoragePlace, selected: Boolean, isCurrent: Boolean, fits: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface)
            .border(1.5.dp, if (selected) Color.White.copy(alpha = 0.8f) else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(
                enabled = fits,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            Icon(
                painterResource(if (place.isPhone) R.drawable.smartphone else R.drawable.sd_card),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(place.label, style = storageText(16f, FontWeight.SemiBold, tracking = -0.3f), maxLines = 1)
            val detail = when {
                isCurrent -> stringResource(R.string.storage_place_here_now)
                !fits -> stringResource(R.string.storage_place_no_room)
                else -> stringResource(R.string.storage_place_free, formatSize(place.free), formatSize(place.total))
            }
            Text(detail, style = storageText(12f, FontWeight.Medium, if (!fits) StorageDanger else StorageMuted, 0f))
        }
        PlaylistSelectTick(checked = selected)
    }
}
