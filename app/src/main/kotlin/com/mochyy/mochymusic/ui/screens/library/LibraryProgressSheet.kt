// LibraryProgressSheet.kt
//
// The progress sheet: the whole batch's bar, every song with its ring, and a button at the bottom:
// "Cancel all downloads" for downloads, "Create playlist" for local imports to keep them together. Opened from the Library's progress card, and right after a local
// import starts.

package com.mochyy.mochymusic.ui.screens.library

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.models.TransferBatch
import com.mochyy.mochymusic.models.TransferItem
import com.mochyy.mochymusic.models.TransferState
import com.mochyy.mochymusic.ui.component.CreatePlaylistDialog
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.viewmodels.LibraryImportViewModel
import kotlinx.coroutines.flow.StateFlow

/** Which job a progress card or sheet follows. */
enum class TransferKind(@StringRes val title: Int, @StringRes val idleText: Int) {
    Downloads(R.string.lib_transfer_download_title, R.string.lib_transfer_download_idle),
    LocalImport(R.string.lib_transfer_import_title, R.string.lib_transfer_import_idle),
}

internal fun LibraryImportViewModel.batchOf(kind: TransferKind): StateFlow<TransferBatch> = when (kind) {
    TransferKind.Downloads -> downloads
    TransferKind.LocalImport -> localImports
}

/** Shows the progress sheet for [kind] over whatever is showing. */
fun PopupSheetState.showTransferSheet(kind: TransferKind, viewModel: LibraryImportViewModel) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(
        buttonBar = {
            if (kind == TransferKind.Downloads) {
                CancelDownloadsButton(
                    batchFlow = viewModel.batchOf(kind),
                    onCancel = {
                        viewModel.cancelAllDownloads()
                        sheet?.dismiss()
                    },
                )
            } else {
                CreatePlaylistButton(
                    batchFlow = viewModel.batchOf(kind),
                    onCreated = { playlistId, songIds ->
                        viewModel.fillPlaylist(playlistId, songIds)
                        sheet?.dismiss()
                    },
                )
            }
        },
        // a finished import has been seen through; the next one starts with a clean sheet
        onClosed = { if (kind == TransferKind.LocalImport) viewModel.clearLocalImport() },
    ) {
        val batch by viewModel.batchOf(kind).collectAsState()
        TransferSheetContent(kind, batch)
    }
}

@Composable
private fun ColumnScope.TransferSheetContent(kind: TransferKind, batch: TransferBatch) {
    Text(
        text = stringResource(kind.title),
        color = SearchColors.Primary,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    )
    Spacer(Modifier.height(18.dp))

    val fraction = animateFloatAsState(batch.fraction, tween(350), label = "transferSheetFraction")
    ProgressBar(fraction)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (batch.isEmpty) "0" else stringResource(R.string.lib_transfer_n_of_m, batch.doneCount, batch.items.size),
            color = SearchColors.Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(batch.fraction * 100).toInt()}%",
            color = SearchColors.Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(18.dp))

    if (batch.isEmpty) {
        Text(
            text = stringResource(kind.idleText),
            color = SearchColors.Secondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(batch.items, key = { it.key }) { item -> TransferRow(item) }
    }
}

@Composable
private fun TransferRow(item: TransferItem) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        LibraryGridArtwork(url = item.artworkUrl, corner = 12.dp, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = SearchColors.Primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = if (item.state == TransferState.Failed) stringResource(R.string.lib_transfer_failed) else item.subtitle
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = SearchColors.Secondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        TransferRing(state = item.state, progress = item.progress)
    }
}

/** Stops the downloads still going. Greyed out once nothing is left to stop. */
@Composable
private fun CancelDownloadsButton(batchFlow: StateFlow<TransferBatch>, onCancel: () -> Unit) {
    val batch by batchFlow.collectAsState()
    SheetPillButton(
        text = stringResource(R.string.lib_transfer_cancel_all),
        enabled = batch.isRunning,
        onClick = onCancel,
    )
}

/**
 * Saves what came in as a playlist, through the app's own create sheet (pushed over this one).
 * Waits for the batch to finish, so the playlist gets every song and not the first few.
 */
@Composable
private fun CreatePlaylistButton(
    batchFlow: StateFlow<TransferBatch>,
    onCreated: (playlistId: String, songIds: List<String>) -> Unit,
) {
    val batch by batchFlow.collectAsState()
    var creating by remember { mutableStateOf(false) }
    val songIds = batch.finishedSongIds

    SheetPillButton(
        text = stringResource(R.string.lib_transfer_create_playlist),
        enabled = !batch.isRunning && songIds.isNotEmpty(),
        onClick = { creating = true },
    )
    if (creating) {
        val first = batch.items.firstOrNull { it.state == TransferState.Done }
        CreatePlaylistDialog(
            onDismiss = { creating = false },
            onPlaylistCreated = { playlistId -> onCreated(playlistId, songIds) },
            topSongId = first?.songId,
            topSongArtworkUrl = first?.artworkUrl,
        )
    }
}
