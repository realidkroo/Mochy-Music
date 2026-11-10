// LibraryAddMusicSheet.kt
//
// Picking songs in a sheet: "Add music" lists what a storage scan found on the phone (all ticked,
// so bringing everything in is one tap), "Remove from list" lists the files already imported and
// "Remove from cache" the cached songs (none ticked). All the same list with a search field and a
// checkbox per song.

package com.mochyy.mochymusic.ui.screens.library

import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.SheetSearchField
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.viewmodels.DeviceScan
import com.mochyy.mochymusic.viewmodels.LibraryImportViewModel
import com.mochyy.mochymusic.ui.component.RemoteFocus

/** A song in a picker sheet. */
@Immutable
private data class PickerSong(val key: String, val title: String, val subtitle: String?, val artworkUrl: String?)

/** The ticked songs, shared by the list and the sheet's button. */
@Stable
private class PickerSelection {
    var keys by mutableStateOf(emptySet<String>())

    /** Set once the list has had its starting ticks, so a re-scan doesn't undo the user's. */
    var started = false

    fun toggle(key: String) {
        keys = if (key in keys) keys - key else keys + key
    }
}

/**
 * Lists the songs on the phone that aren't in the library yet (the scan must already be running,
 * see [LibraryImportViewModel.scanDevice]). Adding them starts the import and hands over to
 * [onStarted], which shows its progress.
 */
fun PopupSheetState.showAddLocalMusicSheet(viewModel: LibraryImportViewModel, onStarted: () -> Unit) {
    val selection = PickerSelection()
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(
        buttonBar = {
            val scan by viewModel.scan.collectAsState()
            val count = selection.keys.size
            val stillRunning = stringResource(R.string.lib_import_still_running)
            SheetPillButton(
                text = if (count == 0) {
                    stringResource(R.string.lib_picker_done)
                } else {
                    pluralStringResource(R.plurals.lib_picker_add_songs, count, count)
                },
                onClick = {
                    val found = (scan as? DeviceScan.Found)?.songs.orEmpty()
                    val picked = found.filter { it.uri.toString() in selection.keys }
                    sheet?.dismiss()
                    if (picked.isEmpty()) return@SheetPillButton
                    if (!viewModel.importDeviceSongs(picked)) {
                        TopToaster.show(stillRunning, iconRes = R.drawable.library_music)
                    }
                    onStarted()
                },
            )
        },
    ) {
        val scan by viewModel.scan.collectAsState()
        val songs = remember(scan) {
            (scan as? DeviceScan.Found)?.songs?.map { PickerSong(it.uri.toString(), it.title, it.artist, it.artworkUri) }
        }
        LaunchedEffect(songs) {
            if (songs != null && !selection.started) {
                selection.keys = songs.mapTo(HashSet()) { it.key }
                selection.started = true
            }
        }
        SongPickerContent(
            title = stringResource(R.string.lib_picker_add_music),
            songs = songs,
            selection = selection,
            loadingText = stringResource(R.string.lib_picker_looking),
            emptyText = stringResource(R.string.lib_picker_all_in_library),
        )
    }
}

/** Lists the files imported from the phone, to take some back out of the library. */
fun PopupSheetState.showRemoveLocalSheet(localFiles: List<Song>, viewModel: LibraryImportViewModel) =
    showRemoveSongsSheet(
        songs = localFiles,
        title = R.string.lib_picker_remove_from_list,
        emptyText = R.string.lib_picker_no_local_files,
        hint = R.string.lib_picker_files_stay,
        onRemove = { viewModel.removeLocalSongs(it) },
    )

/** Lists the cached songs, to delete some from the cache. */
fun PopupSheetState.showRemoveCachedSheet(cachedSongs: List<Song>, onRemove: (Set<String>) -> Unit) =
    showRemoveSongsSheet(
        songs = cachedSongs,
        title = R.string.lib_picker_remove_from_cache,
        emptyText = R.string.lib_picker_nothing_cached,
        hint = R.string.lib_picker_cache_hint,
        onRemove = onRemove,
    )

private fun PopupSheetState.showRemoveSongsSheet(
    songs: List<Song>,
    @StringRes title: Int,
    @StringRes emptyText: Int,
    @StringRes hint: Int,
    onRemove: (Set<String>) -> Unit,
) {
    val selection = PickerSelection()
    val pickerSongs = songs.map { PickerSong(it.id, it.song.title, it.artists.joinToString { a -> a.name }.ifBlank { null }, it.song.thumbnailUrl) }
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(
        buttonBar = {
            val count = selection.keys.size
            SheetPillButton(
                text = if (count == 0) {
                    stringResource(R.string.lib_picker_pick_to_remove)
                } else {
                    pluralStringResource(R.plurals.lib_picker_remove_songs, count, count)
                },
                enabled = count > 0,
                onClick = {
                    onRemove(selection.keys)
                    sheet?.dismiss()
                },
            )
        },
    ) {
        SongPickerContent(
            title = stringResource(title),
            songs = pickerSongs,
            selection = selection,
            loadingText = "",
            emptyText = stringResource(emptyText),
            hint = stringResource(hint),
        )
    }
}

/** Title, search, then the songs with their checkboxes. A null [songs] is still loading. */
@Composable
private fun ColumnScope.SongPickerContent(
    title: String,
    songs: List<PickerSong>?,
    selection: PickerSelection,
    loadingText: String,
    emptyText: String,
    hint: String? = null,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(songs, query) {
        val q = query.trim()
        if (q.isEmpty()) songs.orEmpty()
        else songs.orEmpty().filter { it.title.contains(q, ignoreCase = true) || it.subtitle?.contains(q, ignoreCase = true) == true }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            color = SearchColors.Primary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.weight(1f),
        )
        // with hundreds of songs, one tap to flip them all beats unticking each
        if (!songs.isNullOrEmpty()) {
            val allTicked = selection.keys.size == songs.size
            Text(
                text = stringResource(if (allTicked) R.string.lib_picker_select_none else R.string.lib_picker_select_all),
                color = SearchColors.Secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(role = Role.Button) {
                        selection.keys = if (allTicked) emptySet() else songs.mapTo(HashSet()) { it.key }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    if (hint != null) {
        Text(text = hint, color = SearchColors.Secondary, fontSize = 13.sp)
    }
    Spacer(Modifier.height(14.dp))
    SheetSearchField(value = query, onValueChange = { query = it }, placeholder = stringResource(R.string.search))
    Spacer(Modifier.height(14.dp))

    val message = when {
        songs == null -> loadingText
        songs.isEmpty() -> emptyText
        shown.isEmpty() -> stringResource(R.string.lib_picker_nothing_matches, query.trim())
        else -> null
    }
    if (message != null) {
        Text(
            text = message,
            color = SearchColors.Secondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(shown, key = { it.key }) { song ->
            PickerRow(song = song, checked = song.key in selection.keys, onToggle = { selection.toggle(song.key) })
        }
    }
}

@Composable
private fun PickerRow(song: PickerSong, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                role = Role.Checkbox,
                interactionSource = remember { MutableInteractionSource() },
                indication = RemoteFocus,
                onClick = onToggle,
            )
            .padding(vertical = 2.dp),
    ) {
        PickerCheckbox(checked)
        Spacer(Modifier.width(14.dp))
        LibraryGridArtwork(url = song.artworkUrl, corner = 12.dp, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = SearchColors.Primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.subtitle != null) {
                Text(
                    text = song.subtitle,
                    color = SearchColors.Secondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The mock's square tick box, the same as the playlist Add music sheet's: soft grey when empty,
 * white with a dark tick when ticked.
 */
@Composable
private fun PickerCheckbox(checked: Boolean) {
    val fill by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pickerCheckbox",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            // read in the draw pass, so the tick animating doesn't recompose the row
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
