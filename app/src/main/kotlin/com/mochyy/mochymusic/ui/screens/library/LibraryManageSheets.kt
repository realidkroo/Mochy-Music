// LibraryManageSheets.kt
//
// "Manage imports", "Manage local" and "Manage cache": big tiles for the main actions and a row per
// other action, as in the mocks. Anything that takes music out of the library asks once more on a
// sheet pushed over this one.

package com.mochyy.mochymusic.ui.screens.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.importer.ImportSource
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetIcon
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.viewmodels.LibraryImportViewModel

/**
 * [sources] are the services that have brought music in, one "Delete ... imports" row each.
 * [onResyncAll] checks every imported playlist for new songs: a long tile under the two others.
 */
fun PopupSheetState.showManageImportsSheet(
    sources: List<ImportSource>,
    viewModel: LibraryImportViewModel,
    onResyncAll: () -> Unit,
) {
    var sheet: PopupSheetHandle? = null
    fun confirmThen(title: String, message: String, action: String, run: () -> Unit) =
        showConfirm(title, message, action) {
            run()
            sheet?.dismiss()
        }
    sheet = showWithHandle {
        val context = LocalContext.current
        ManageLayout(
            ManageAction(R.drawable.delete, stringResource(R.string.lib_manage_delete_all_imports)) {
                confirmThen(
                    title = context.getString(R.string.lib_manage_delete_all_imports_title),
                    message = context.getString(R.string.lib_manage_delete_all_imports_msg),
                    action = context.getString(R.string.lib_manage_delete_all_imports),
                    run = viewModel::deleteAllImports,
                )
            },
            ManageAction(R.drawable.undo, stringResource(R.string.lib_manage_undo_session)) {
                confirmThen(
                    title = context.getString(R.string.lib_manage_undo_last_title),
                    message = context.getString(R.string.lib_manage_undo_last_msg),
                    action = context.getString(R.string.lib_manage_undo_import),
                    run = viewModel::undoLatestImport,
                )
            },
            wide = ManageAction(R.drawable.sync, stringResource(R.string.lib_manage_resync_all)) {
                sheet?.dismiss()
                onResyncAll()
            },
            rows = sources.map { source ->
                ManageAction(R.drawable.delete, stringResource(R.string.lib_manage_delete_source, source.label)) {
                    confirmThen(
                        title = context.getString(R.string.lib_manage_delete_source_title, source.label),
                        message = context.getString(
                            if (source == ImportSource.YOUTUBE_MUSIC) R.string.lib_manage_delete_source_msg_youtube
                            else R.string.lib_manage_delete_source_msg,
                            source.label,
                        ),
                        action = context.getString(R.string.lib_manage_delete),
                        run = { viewModel.deleteImportsFrom(source) },
                    )
                }
            },
        )
    }
}

/**
 * [onRescan], [onAddFolder] and [onRemoveFromList] open their own sheets or pickers, so this one
 * closes first. Re-scan also brings back missing covers and reads the picked folders again.
 */
fun PopupSheetState.showManageLocalSheet(
    viewModel: LibraryImportViewModel,
    onRescan: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFromList: () -> Unit,
) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        val context = LocalContext.current
        ManageLayout(
            ManageAction(R.drawable.refresh, stringResource(R.string.lib_manage_rescan_local)) {
                sheet?.dismiss()
                onRescan()
            },
            ManageAction(R.drawable.delete, stringResource(R.string.lib_manage_delete_from_list)) {
                sheet?.dismiss()
                onRemoveFromList()
            },
            wide = ManageAction(R.drawable.library_add, stringResource(R.string.lib_manage_add_folder)) {
                sheet?.dismiss()
                onAddFolder()
            },
            rows = listOf(
                ManageAction(R.drawable.undo, stringResource(R.string.lib_manage_undo_session)) {
                    showConfirm(
                        title = context.getString(R.string.lib_manage_undo_local_title),
                        message = context.getString(R.string.lib_manage_undo_local_msg),
                        action = context.getString(R.string.lib_manage_undo_import),
                    ) {
                        viewModel.undoLatestLocalImport()
                        sheet?.dismiss()
                    }
                },
            ),
        )
    }
}

/**
 * [cachedSongs] are the songs "Remove songs" lists. Clearing goes further and also drops songs that
 * only got partly cached; downloads are kept apart and never touched.
 */
fun PopupSheetState.showManageCachedSheet(
    cachedSongs: List<Song>,
    onClearCache: () -> Unit,
    onRemove: (Set<String>) -> Unit,
) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle {
        val context = LocalContext.current
        ManageLayout(
            ManageAction(R.drawable.clear_all, stringResource(R.string.lib_manage_clear_cache)) {
                showConfirm(
                    title = context.getString(R.string.lib_manage_clear_cache_title),
                    message = context.getString(R.string.lib_manage_clear_cache_msg),
                    action = context.getString(R.string.lib_manage_clear_cache),
                ) {
                    onClearCache()
                    sheet?.dismiss()
                }
            },
            ManageAction(R.drawable.delete, stringResource(R.string.lib_manage_remove_songs)) {
                sheet?.dismiss()
                showRemoveCachedSheet(cachedSongs, onRemove)
            },
            rows = emptyList(),
        )
    }
}

/** One more look before music leaves the phone or the library, pushed over whatever asked. */
internal fun PopupSheetState.showConfirm(title: String, message: String, action: String, onConfirm: () -> Unit) {
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(
        buttonBar = {
            SheetPillButton(
                text = action,
                onClick = {
                    sheet?.dismiss()
                    onConfirm()
                },
            )
        },
    ) {
        Text(text = title, color = SearchColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(text = message, color = SearchColors.Secondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
    }
}

private class ManageAction(@DrawableRes val icon: Int, val label: String, val onClick: () -> Unit)

@Composable
private fun ManageLayout(first: ManageAction, second: ManageAction, wide: ManageAction? = null, rows: List<ManageAction>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ManageTile(first, Modifier.weight(1f))
            ManageTile(second, Modifier.weight(1f))
        }
        // a third big action spans the width under the two, rather than squeezing three into a row
        wide?.let { ManageTile(it, Modifier.fillMaxWidth()) }
        rows.forEach { ManageRow(it) }
    }
}

/** A tall tile: its icon in the top corner, the label at the bottom. */
@Composable
private fun ManageTile(action: ManageAction, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 92.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        ManageIcon(action.icon)
        Spacer(Modifier.height(14.dp))
        ManageLabel(action.label)
    }
}

@Composable
private fun ManageRow(action: ManageAction) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        ManageIcon(action.icon)
        Spacer(Modifier.width(14.dp))
        ManageLabel(action.label)
    }
}

/** The action's icon in plain white. It used to sit in a light circle; the sheets dropped those. */
@Composable
private fun ManageIcon(@DrawableRes icon: Int) {
    SheetIcon(icon, size = 22.dp)
}

@Composable
private fun ManageLabel(text: String) {
    Text(
        text = text,
        color = SearchColors.Primary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
