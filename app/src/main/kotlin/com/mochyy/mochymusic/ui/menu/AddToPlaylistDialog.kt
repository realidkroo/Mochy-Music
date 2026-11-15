// AddToPlaylistDialog.kt

package com.mochyy.mochymusic.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.ui.component.CreatePlaylistDialog
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.PlaylistThumbnail
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.SheetSearchField
import com.mochyy.mochymusic.viewmodels.PlaylistsViewModel
import com.music.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale

/** How many playlists the "recents" section shows. */
private const val RecentCount = 4

/**
 * "Add to playlist", as a sheet (the first mock): a search field, the playlists used most
 * recently, all the others, and Create playlist pinned below. Shown through the app's popup
 * system while [isVisible] - over whatever menu opened it, which comes back forward behind it.
 *
 * @param onGetSong the songs to add, given the playlist they are going into.
 * @param topSongId when the songs are being added to a playlist made right here, the first of
 *   them with its [topSongArtworkUrl]: the new playlist's cover takes its colours from it.
 */
/** The top toast for songs just added to [playlist]: what went in, and Undo (not for a synced playlist, which YouTube has already been told about). */

/** [context] words the toast in the app language; callers that cannot give one get the English wording. */
internal fun announceAddedToPlaylist(
    database: com.mochyy.mochymusic.db.MusicDatabase,
    playlist: Playlist,
    ids: List<String>,
    context: android.content.Context? = null,
) {
    if (ids.isEmpty()) return
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        val first = database.song(ids.first()).firstOrNull()?.song
        val name = playlist.playlist.name
        com.mochyy.mochymusic.ui.component.TopToaster.show(
            text =
                if (context != null) {
                    if (ids.size == 1) {
                        context.getString(R.string.pc_added_one, first?.title ?: context.getString(R.string.pc_song_fallback), name)
                    } else {
                        context.getString(R.string.pc_added_many, ids.size, name)
                    }
                } else if (ids.size == 1) {
                    "Added ${first?.title ?: "song"} to $name"
                } else {
                    "Added ${ids.size} songs to $name"
                },
            thumbnail = first?.thumbnailUrl,
            iconRes = R.drawable.playlist_add,
            actionLabel = if (playlist.playlist.browseId == null) (context?.getString(R.string.undo) ?: "Undo") else null,
            onAction = { database.query { removeAddedSongs(playlist.id, ids, playlist.songCount) } },
        )
    }
}

@Composable
fun AddToPlaylistDialog(
    isVisible: Boolean,
    allowSyncing: Boolean = true,
    initialTextFieldValue: String? = null,
    onGetSong: suspend (Playlist) -> List<String>,
    onDismiss: () -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
    topSongId: String? = null,
    topSongArtworkUrl: String? = null,
) {
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext

    // Read through these: the sheet is composed by the popup host from lambdas made when it opened.
    val currentOnGetSong by rememberUpdatedState(onGetSong)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val playlists = viewModel.allPlaylists.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var showDuplicate by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var songIds by remember { mutableStateOf<List<String>?>(null) }
    var duplicates by remember { mutableStateOf(emptyList<String>()) }

    // This sheet's own handle, for closing exactly it: Create playlist opens over it.
    val sheet = remember { arrayOfNulls<PopupSheetHandle>(1) }

    val pick: (Playlist) -> Unit = { playlist ->
        selectedPlaylist = playlist
        scope.launch(Dispatchers.IO) {
            if (songIds == null) {
                songIds = currentOnGetSong(playlist)
            }
            val ids = songIds.orEmpty()
            duplicates = database.playlistDuplicates(playlist.id, ids)
            if (duplicates.isNotEmpty()) {
                showDuplicate = true
            } else {
                sheet[0]?.dismiss()
                currentOnDismiss()
                val browseId = playlist.playlist.browseId
                if (browseId != null) {
                    // a playlist that mirrors youtube keeps what's added here through its syncs, on top
                    com.mochyy.mochymusic.importer.PlaylistAddedHere
                        .add(context, playlist.id, ids)
                    database.addSongsToPlaylistTop(playlist, ids)
                } else {
                    database.addSongToPlaylist(playlist, ids)
                }
                announceAddedToPlaylist(database, playlist, ids, context)

                browseId?.let { ids.forEach { id -> YouTube.addToPlaylist(it, id) } }
            }
        }
    }

    if (isVisible) {
        DisposableEffect(menuState) {
            // Each opening starts from a clean pick: songs are asked for again.
            songIds = null
            var closed = false
            sheet[0] =
                menuState.showWithHandle(
                    buttonBar = {
                        SheetPillButton(
                            text = stringResource(R.string.pc_create_playlist),
                            onClick = { showCreate = true },
                        )
                    },
                    onClosed = {
                        closed = true
                        currentOnDismiss()
                    },
                ) {
                    AddToPlaylistContent(playlists = playlists, onPick = pick)
                }
            onDispose {
                if (!closed) sheet[0]?.dismiss()
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            initialTextFieldValue = initialTextFieldValue,
            allowSyncing = allowSyncing,
            topSongId = topSongId,
            topSongArtworkUrl = topSongArtworkUrl,
        )
    }

    if (showDuplicate) {
        DefaultDialog(
            title = { Text(stringResource(R.string.duplicates)) },
            buttons = {
                TextButton(
                    onClick = {
                        showDuplicate = false
                        sheet[0]?.dismiss()
                        currentOnDismiss()
                        val keep = songIds!!.filter { !duplicates.contains(it) }
                        database.transaction {
                            addSongToPlaylist(selectedPlaylist!!, keep)
                        }
                        announceAddedToPlaylist(database, selectedPlaylist!!, keep, context)
                    },
                ) {
                    Text(stringResource(R.string.skip_duplicates))
                }

                TextButton(
                    onClick = {
                        showDuplicate = false
                        sheet[0]?.dismiss()
                        currentOnDismiss()
                        database.transaction {
                            addSongToPlaylist(selectedPlaylist!!, songIds!!)
                        }
                        announceAddedToPlaylist(database, selectedPlaylist!!, songIds!!, context)
                    },
                ) {
                    Text(stringResource(R.string.add_anyway))
                }

                TextButton(
                    onClick = {
                        showDuplicate = false
                    },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showDuplicate = false
            },
        ) {
            Text(
                text =
                    if (duplicates.size == 1) {
                        stringResource(R.string.duplicates_description_single)
                    } else {
                        stringResource(R.string.duplicates_description_multiple, duplicates.size)
                    },
                textAlign = TextAlign.Start,
                modifier = Modifier.align(Alignment.Start),
            )
        }
    }
}

@Composable
private fun AddToPlaylistContent(
    playlists: State<List<Playlist>>,
    onPick: (Playlist) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val all = playlists.value

    // Recents: the playlists touched last. Only worth a section of their own once there are more
    // playlists than it would show; with few, one list is simpler.
    val sections =
        remember(all, query) {
            val needle = query.trim()
            if (needle.isNotEmpty()) {
                val hits = all.filter { it.playlist.name.contains(needle, ignoreCase = true) }
                Sections(recents = emptyList(), rest = hits, searching = true)
            } else if (all.size > RecentCount) {
                val recents =
                    all
                        .sortedByDescending { it.playlist.lastUpdateTime ?: it.playlist.createdAt ?: LocalDateTime.MIN }
                        .take(RecentCount)
                val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
                val rest = (all - recents.toSet()).sortedWith(compareBy(collator) { it.playlist.name })
                Sections(recents = recents, rest = rest, searching = false)
            } else {
                val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
                Sections(recents = emptyList(), rest = all.sortedWith(compareBy(collator) { it.playlist.name }), searching = false)
            }
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.pc_search_your_playlist),
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(bottom = 12.dp),
        ) {
            if (sections.recents.isNotEmpty()) {
                item(key = "recents-label") { SheetLabel(stringResource(R.string.sheet_label_recents), Modifier.padding(bottom = 6.dp)) }
                items(sections.recents, key = { "recent-${it.id}" }) { playlist ->
                    PlaylistPickRow(playlist = playlist, onClick = { onPick(playlist) })
                }
                item(key = "recents-gap") { Spacer(Modifier.height(14.dp)) }
            }

            if (sections.rest.isNotEmpty()) {
                item(key = "all-label") {
                    SheetLabel(
                        stringResource(if (sections.searching) R.string.sheet_label_results else R.string.sheet_label_all),
                        Modifier.padding(bottom = 6.dp),
                    )
                }
                items(sections.rest, key = { "all-${it.id}" }) { playlist ->
                    PlaylistPickRow(playlist = playlist, onClick = { onPick(playlist) })
                }
            } else if (sections.recents.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(if (sections.searching) R.string.pc_no_playlists_found else R.string.pc_no_playlists_yet),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                    )
                }
            }
        }
    }
}

private class Sections(
    val recents: List<Playlist>,
    val rest: List<Playlist>,
    val searching: Boolean,
)

/** A playlist as the mock draws it: a rounded square cover, its name, and under it the song count. */
@Composable
private fun PlaylistPickRow(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    val count =
        if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
            playlist.playlist.remoteSongCount
        } else {
            playlist.songCount
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = RemoteFocus,
                    onClick = onClick,
                ).padding(vertical = 6.dp),
    ) {
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = 52.dp,
            placeHolder = {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp),
                )
            },
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.playlist.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.n_song, count, count),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
