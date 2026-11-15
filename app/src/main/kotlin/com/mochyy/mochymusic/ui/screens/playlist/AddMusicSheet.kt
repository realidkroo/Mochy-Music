// AddMusicSheet.kt

package com.mochyy.mochymusic.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.constants.HideExplicitKey
import com.mochyy.mochymusic.constants.HideVideoSongsKey
import com.mochyy.mochymusic.constants.SongSortType
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.SheetSearchField
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import com.music.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private enum class AddMusicTab { Suggested, Library }

/**
 * Pushes the "Add music" sheet for a playlist, as in the mock: a search over every song (your
 * library and YouTube), chips for suggestions and your library, and Done pinned below. It goes on
 * the sheet stack, so back closes only this sheet and you land on whatever was showing before.
 *
 * @param startInLibrary open on "In library": an empty playlist has nothing to base suggestions on.
 */
fun PopupSheetState.showAddMusicSheet(playlistId: String, startInLibrary: Boolean) {
    var sheet: PopupSheetHandle? = null
    val picks = AddMusicPicks()

    sheet = showWithHandle(
        buttonBar = {
            val database = LocalDatabase.current
            val scope = rememberCoroutineScope()
            val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
            val count = picks.songs.size
            SheetPillButton(
                text = when {
                    picks.saving -> "Adding…"
                    count == 0 -> "Done"
                    count == 1 -> "Add 1 song"
                    else -> "Add $count songs"
                },
                enabled = !picks.saving,
                onClick = {
                    if (count == 0) {
                        sheet?.dismiss()
                        return@SheetPillButton
                    }
                    picks.saving = true
                    // Nothing may close the sheet while the songs are going in.
                    sheet?.setLocked(true)
                    val chosen = picks.songs.toList()
                    scope.launch(Dispatchers.IO) {
                        try {
                            addPicksToPlaylist(database, playlistId, chosen, appContext)
                            withContext(Dispatchers.Main) { sheet?.dismiss() }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.tag("AddMusicSheet").e(e, "Adding songs to the playlist failed")
                            TopToaster.error("Couldn't add those songs. Try again.")
                            picks.saving = false
                            sheet?.setLocked(false)
                        }
                    }
                },
            )
        },
    ) {
        AddMusicContent(playlistId = playlistId, startInLibrary = startInLibrary, picks = picks)
    }
}

/**
 * The songs ticked so far. It lives as long as the sheet does, not as long as a list does, so
 * searching or switching chips never drops a pick.
 */
@Stable
internal class AddMusicPicks {
    /** In the order they were ticked, which is the order they go into the playlist. */
    val songs = mutableStateListOf<MediaMetadata>()
    val ids: Set<String> by derivedStateOf { songs.mapTo(HashSet()) { it.id } }
    var saving by mutableStateOf(false)

    fun toggle(id: String, song: () -> MediaMetadata) {
        val index = songs.indexOfFirst { it.id == id }
        if (index >= 0) songs.removeAt(index) else songs.add(song())
    }
}

@Composable
private fun AddMusicContent(playlistId: String, startInLibrary: Boolean, picks: AddMusicPicks) {
    val database = LocalDatabase.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(if (startInLibrary) AddMusicTab.Library else AddMusicTab.Suggested) }
    val needle = query.trim()

    // null until Room answers, so an empty set really means an empty playlist.
    val inPlaylist by remember(playlistId) { database.playlistSongIds(playlistId).map { it.toHashSet() } }
        .collectAsState(initial = null)
    val library by remember { database.songs(SongSortType.CREATE_DATE, descending = true) }
        .collectAsState(initial = null)
    val suggestions by produceState<List<SongItem>?>(initialValue = null, playlistId) {
        value = withContext(Dispatchers.IO) {
            loadPlaylistSuggestions(
                database = database,
                playlistId = playlistId,
                hideExplicit = context.dataStore.get(HideExplicitKey, false),
                hideVideos = context.dataStore.get(HideVideoSongsKey, false),
            )
        }
    }
    val search = rememberSongSearch(needle, library)

    // While searching, back clears the search and shows the chips' lists again; the next back
    // closes the sheet.
    BackHandler(enabled = query.isNotEmpty()) { query = "" }

    val listState = rememberLazyListState()
    LaunchedEffect(tab, needle) { listState.scrollToItem(0) }
    LaunchedEffect(listState) {
        // Scrolling the list means the typing is done: give the room back to the songs.
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) keyboard?.hide()
        }
    }

    val added = inPlaylist ?: emptySet()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Always the full sheet, so it doesn't jump in height as results come and go.
            .fillMaxHeight(),
    ) {
        Text(
            text = "Add music",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.03).em,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        SheetSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search all songs",
        )

        Spacer(Modifier.height(12.dp))

        // While searching neither chip is lit: the search covers everything. Tapping one clears it.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            AddMusicChip(
                text = "Recommended for this playlist",
                selected = needle.isEmpty() && tab == AddMusicTab.Suggested,
                onClick = {
                    query = ""
                    tab = AddMusicTab.Suggested
                },
            )
            AddMusicChip(
                text = "In library",
                selected = needle.isEmpty() && tab == AddMusicTab.Library,
                onClick = {
                    query = ""
                    tab = AddMusicTab.Library
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                needle.isNotEmpty() -> searchResults(search, added, picks)
                tab == AddMusicTab.Suggested -> suggestedSongs(suggestions, inPlaylist, picks)
                else -> librarySongs(library, added, picks)
            }
        }
    }
}
private fun LazyListScope.searchResults(
    results: SongSearchResults?,
    added: Set<String>,
    picks: AddMusicPicks,
) {
    if (results == null) {
        item(key = "search-bones") { PickRowBones() }
        return
    }

    if (results.library.isNotEmpty()) {
        item(key = "library-label") { SheetLabel("in your library", Modifier.padding(bottom = 6.dp)) }
        librarySongRows("library-hit", results.library, added, picks)
        item(key = "library-gap") { Spacer(Modifier.height(14.dp)) }
    }

    val online = results.online
    when {
        online == null -> {
            item(key = "online-label") { SheetLabel("all songs", Modifier.padding(bottom = 6.dp)) }
            item(key = "online-bones") { PickRowBones(rows = 4) }
        }
        online.isNotEmpty() -> {
            item(key = "online-label") { SheetLabel("all songs", Modifier.padding(bottom = 6.dp)) }
            onlineSongRows("online", online, added, picks)
        }
        results.onlineFailed -> item(key = "online-failed") {
            SheetNote("Couldn't search online. Check your connection and try again.")
        }
        results.library.isEmpty() -> item(key = "nothing") {
            SheetNote("Nothing found for “${results.query}”.")
        }
    }
}

private fun LazyListScope.suggestedSongs(
    suggestions: List<SongItem>?,
    inPlaylist: Set<String>?,
    picks: AddMusicPicks,
) {
    when {
        suggestions == null -> item(key = "suggested-bones") { PickRowBones() }
        suggestions.isNotEmpty() -> onlineSongRows("suggested", suggestions, inPlaylist.orEmpty(), picks)
        inPlaylist?.isEmpty() == true -> item(key = "suggested-empty") {
            SheetNote("Add a few songs first and we'll suggest more that fit.")
        }
        else -> item(key = "suggested-failed") {
            SheetNote("No suggestions right now. Check your connection, or search for a song above.")
        }
    }
}

private fun LazyListScope.librarySongs(library: List<Song>?, added: Set<String>, picks: AddMusicPicks) {
    when {
        library == null -> item(key = "library-bones") { PickRowBones() }
        library.isEmpty() -> item(key = "library-empty") {
            SheetNote("Your library is empty. Search above to find any song.")
        }
        else -> librarySongRows("library", library, added, picks)
    }
}

private fun LazyListScope.librarySongRows(
    keyPrefix: String,
    songs: List<Song>,
    added: Set<String>,
    picks: AddMusicPicks,
) {
    items(items = songs, key = { "$keyPrefix-${it.id}" }) { song ->
        PickRow(
            id = song.id,
            title = song.song.title,
            artists = song.artists.joinToString { it.name },
            thumbnailUrl = song.song.thumbnailUrl,
            inPlaylist = song.id in added,
            picks = picks,
            toMetadata = { song.toMediaMetadata() },
        )
    }
}

private fun LazyListScope.onlineSongRows(
    keyPrefix: String,
    songs: List<SongItem>,
    added: Set<String>,
    picks: AddMusicPicks,
) {
    items(items = songs, key = { "$keyPrefix-${it.id}" }) { song ->
        PickRow(
            id = song.id,
            title = song.title,
            artists = song.artists.joinToString { it.name },
            thumbnailUrl = song.thumbnail,
            inPlaylist = song.id in added,
            picks = picks,
            toMetadata = { song.toMediaMetadata() },
        )
    }
}
