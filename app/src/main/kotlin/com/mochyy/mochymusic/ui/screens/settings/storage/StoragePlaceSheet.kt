// storage → choose where downloads or the cache are saved: this phone or any folder picked with the folder picker, then a move and one restart

package com.mochyy.mochymusic.ui.screens.settings.storage

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // a picked folder waiting on all files access before it can be moved to
    var waiting by remember { mutableStateOf<StoragePlace?>(null) }
    var problem by remember { mutableStateOf<Int?>(null) }

    fun accessAnswered() {
        val place = waiting ?: return
        if (viewModel.needsFolderAccess(place)) {
            problem = R.string.storage_place_no_access
        } else {
            waiting = null
            problem = null
            pick.picked = place
        }
    }
    val accessSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { accessAnswered() }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { accessAnswered() }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        scope.launch {
            val place = viewModel.pickedFolder(tree)
            problem = null
            when {
                place == null -> problem = R.string.storage_place_folder_unsupported
                viewModel.needsFolderAccess(place) -> waiting = place
                else -> {
                    waiting = null
                    pick.picked = place
                }
            }
        }
    }
    fun askForAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val own = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null))
            // some phones don't have the page for one app, only the list of them
            runCatching { accessSettings.launch(own) }
                .onFailure { runCatching { accessSettings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
        } else {
            storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        SheetLabel(stringResource(if (kind == StoredKind.Downloads) R.string.storage_place_sheet_downloads else R.string.storage_place_sheet_cache))
        Text(
            stringResource(if (kind == StoredKind.Downloads) R.string.storage_place_note_downloads else R.string.storage_place_note_cache),
            style = storageText(13f, FontWeight.Medium, StorageMuted, -0.1f),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        if (choice == null) {
            // the rows' height, so the sheet doesn't jump once the places are read
            Spacer(Modifier.height(140.dp))
            return@Column
        }
        // this phone, where it's saved now if that's somewhere else, and a folder just picked
        val phone = choice.places.first()
        val rows = listOfNotNull(phone, choice.current.takeIf { !it.isPhone }, pick.picked?.takeIf { it.root != choice.current.root && !it.isPhone })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { place ->
                PlaceRow(
                    place = place,
                    selected = (pick.picked ?: choice.current).root == place.root,
                    isCurrent = place.root == choice.current.root,
                    fits = place.root == choice.current.root || pick.fits(place),
                    onClick = { pick.picked = place },
                )
            }
            ChooseFolderRow(onClick = { folderPicker.launch(null) })
        }
        waiting?.let { place -> AccessCard(place.label, onAllow = ::askForAccess) }
        val failed = step is MoveStep.Failed
        val note = when {
            failed -> stringResource(R.string.storage_move_failed)
            problem != null -> stringResource(problem!!)
            choice.current.isPhone || choice.current.isFolder -> stringResource(R.string.storage_place_folder_note)
            else -> stringResource(R.string.storage_place_card_note)
        }
        Text(
            note,
            style = storageText(12f, FontWeight.Medium, if (failed || problem != null) StorageDanger else StorageMuted, -0.1f),
            modifier = Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp),
        )
    }
}

// the button that opens android's folder picker, in the rows' own style
@Composable
private fun ChooseFolderRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        PlaceIcon(rememberVectorPainter(Icons.Rounded.CreateNewFolder))
        Spacer(Modifier.width(14.dp))
        Text(stringResource(R.string.storage_place_choose_folder), style = storageText(16f, FontWeight.SemiBold, tracking = -0.3f))
    }
}

// why mochy needs all files access for the picked folder, and the way to give it
@Composable
private fun AccessCard(folder: String, onAllow: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface)
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.storage_place_access_needed, folder),
            style = storageText(13f, FontWeight.Medium, Color.White, -0.1f),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(42.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = RemoteFocus,
                    onClick = onAllow,
                ),
        ) {
            Text(stringResource(R.string.storage_place_allow_access), style = storageText(14f, FontWeight.SemiBold, Color.Black, -0.2f))
        }
    }
}

@Composable
private fun PlaceIcon(painter: Painter) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        Icon(painter, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
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
        PlaceIcon(
            when {
                place.isPhone -> painterResource(R.drawable.smartphone)
                place.isFolder -> rememberVectorPainter(Icons.Rounded.Folder)
                else -> painterResource(R.drawable.sd_card)
            },
        )
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
