// LibraryCollection.kt
//
// The page behind All Songs, Albums, Artists and Playlists: the title with how much is there and how
// long it runs, Play / shuffle / search, a list or grid switch, and the A-Z rail down the side. Everything the list needs (sorting thousands of
// titles into letters, Japanese and all) is worked out off the main thread.

package com.mochyy.mochymusic.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.ui.component.BoneColor
import com.mochyy.mochymusic.ui.component.HomeCardHighlight
import com.mochyy.mochymusic.ui.component.containerTransformSource
import com.mochyy.mochymusic.ui.component.detail.PlaylistDetailScaffold
import com.mochyy.mochymusic.ui.component.detail.PlaylistHeader
import com.mochyy.mochymusic.ui.component.detail.PlaylistTrackBones
import com.mochyy.mochymusic.ui.component.detail.PlaylistTrackRow
import com.mochyy.mochymusic.ui.component.detail.TrackShape
import com.mochyy.mochymusic.ui.component.detail.playlistCompactHeaderHeight
import com.mochyy.mochymusic.ui.screens.playlist.GeneratedGroupHeader
import com.mochyy.mochymusic.ui.screens.playlist.GeneratedNoResults
import com.mochyy.mochymusic.ui.screens.playlist.GeneratedSearchCapsule
import com.mochyy.mochymusic.ui.utils.backToMain
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** one entry on a library page, as both the list and the grid draw it */
@Immutable
data class CollectionEntry(
    val id: String,
    val title: String,
    val subtitle: String?,
    val thumbnailUrl: String?,
    // songs only: marks the row while it plays and when it's on the phone
    val songId: String? = null,
    // albums and playlists open growing out of their cover
    val sharedElementKey: String? = null,
)

private const val GridColumns = 3
private val TileShape = RoundedCornerShape(14.dp)

private sealed interface CollectionRow<out T> {
    val key: String

    class Letter(val section: IndexSection) : CollectionRow<Nothing> {
        override val key = "h_${section.key}"
    }

    class Single<T>(val item: T, val entry: CollectionEntry) : CollectionRow<T> {
        override val key = "i_${entry.id}"
    }

    class Tiles<T>(override val key: String, val tiles: List<Pair<T, CollectionEntry>>) : CollectionRow<T>
}

// what the page shows, worked out off the main thread: rows in index order, the letters on the rail and where each starts
private class CollectionLayout<T>(
    val rows: List<CollectionRow<T>>,
    val ordered: List<T>,
    val sections: List<IndexSection>,
    val firstRowOf: Map<String, Int>,
    val count: Int,
    val totalSeconds: Int,
    // tints the page and sits in the collapsed pill, like the other pages without a cover of their own
    val coverUrl: String?,
)

private fun <T> layoutOf(
    items: List<T>,
    toEntry: (T) -> CollectionEntry,
    query: String,
    grid: Boolean,
    secondsOf: ((T) -> Int)?,
): CollectionLayout<T> {
    val pairs = items.map { it to toEntry(it) }
    val q = query.trim()
    val visible = if (q.isEmpty()) {
        pairs
    } else {
        pairs.filter { (_, e) -> e.title.contains(q, ignoreCase = true) || e.subtitle?.contains(q, ignoreCase = true) == true }
    }
    val sections = indexSections(visible) { it.second.title }
    val rows = ArrayList<CollectionRow<T>>(visible.size + sections.size)
    val firstRowOf = HashMap<String, Int>()
    sections.forEach { (section, entries) ->
        firstRowOf[section.key] = rows.size
        rows += CollectionRow.Letter(section)
        if (grid) {
            entries.chunked(GridColumns).forEachIndexed { i, chunk -> rows += CollectionRow.Tiles("t_${section.key}_$i", chunk) }
        } else {
            entries.forEach { (item, entry) -> rows += CollectionRow.Single(item, entry) }
        }
    }
    return CollectionLayout(
        rows = rows,
        ordered = sections.flatMap { (_, entries) -> entries.map { it.first } },
        sections = sections.map { it.first },
        firstRowOf = firstRowOf,
        count = items.size,
        totalSeconds = secondsOf?.let { seconds -> items.sumOf(seconds) } ?: 0,
        coverUrl = pairs.firstNotNullOfOrNull { it.second.thumbnailUrl?.takeIf(String::isNotEmpty) },
    )
}

/**
 * the page itself. [onOpen] and [onPlay] get the items in the order shown, so a tap or Play queues
 * what's on screen. [pageKey] remembers this page's list or grid choice on its own.
 */
@Composable
fun <T> LibraryCollectionPage(
    title: String,
    pageKey: String,
    items: List<T>,
    toEntry: (T) -> CollectionEntry,
    countText: @Composable (Int) -> String,
    searchPlaceholder: String,
    onOpen: (item: T, ordered: List<T>) -> Unit,
    onMenu: (T) -> Unit,
    onPlay: (ordered: List<T>, shuffle: Boolean) -> Unit,
    navController: NavController,
    roundCovers: Boolean = false,
    secondsOf: ((T) -> Int)? = null,
    rowWrapper: @Composable (item: T, row: @Composable () -> Unit) -> Unit = { _, row -> row() },
) {
    val playerConnection = LocalPlayerConnection.current
    val idle = remember { MutableStateFlow<MediaMetadata?>(null) }
    // states, read inside the rows and the header only, so a play or pause never recomposes the page
    val nowPlaying = (playerConnection?.mediaMetadata ?: idle).collectAsState()
    val isPlaying = (playerConnection?.isEffectivelyPlaying ?: remember { MutableStateFlow(false) }).collectAsState()
    val queueTitle = (playerConnection?.queueTitle ?: remember { MutableStateFlow<String?>(null) }).collectAsState()

    var grid by rememberPreference(booleanPreferencesKey("libraryGrid_$pageKey"), defaultValue = false)
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val searchFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // a letter picked on the rail glides in just under the collapsed bar instead of hiding behind it
    val glide = rememberIndexGlide(listState, landing = indexLandingUnderBar())

    val layout by produceState<CollectionLayout<T>?>(null, items, query.text, grid) {
        value = withContext(Dispatchers.Default) { layoutOf(items, toEntry, query.text, grid, secondsOf) }
    }

    if (searching) {
        BackHandler {
            searching = false
            query = TextFieldValue()
        }
    }

    val shown = layout
    val stats = shown?.let {
        val count = countText(it.count)
        if (it.totalSeconds > 0) {
            stringResource(R.string.lib_stats_in_library_time, count, listeningTime(it.totalSeconds))
        } else {
            stringResource(R.string.lib_stats_in_library, count)
        }
    }.orEmpty()

    PlaylistDetailScaffold(
        sharedElementKey = null,
        coverUrl = shown?.coverUrl,
        lazyListState = listState,
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        showCover = false,
        roundCapsule = true,
        headerHeight = playlistCompactHeaderHeight(),
        overlay = {
            if (shown != null && shown.sections.size > 1 && query.text.isBlank()) {
                LibraryIndexRail(
                    sections = shown.sections,
                    onSectionSelected = { key ->
                        // +1 for the header above the rows
                        shown.firstRowOf[key]?.let { row -> glide.glideTo(row + 1) }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    ) {
        item(key = "collection_header", contentType = "header") {
            val thisQueue = queueTitle.value == title
            PlaylistHeader(
                title = title,
                creators = emptyList(),
                stats = stats,
                isPlaying = thisQueue && isPlaying.value,
                onPlayClick = {
                    if (thisQueue) playerConnection?.togglePlayPause() else shown?.let { onPlay(it.ordered, false) }
                },
                onShuffleClick = { shown?.let { onPlay(it.ordered, true) } },
                onSearchClick = {
                    searching = true
                    scope.launch {
                        listState.animateScrollToItem(0)
                        runCatching { searchFocus.requestFocus() }
                    }
                },
                actionsEnd = { ViewSwitch(grid = grid, onChange = { grid = it }) },
                below = if (searching) {
                    {
                        GeneratedSearchCapsule(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = searchPlaceholder,
                            focusRequester = searchFocus,
                        )
                    }
                } else {
                    null
                },
            )
        }

        if (shown == null) {
            item(key = "loading", contentType = "loading") { PlaylistTrackBones() }
            return@PlaylistDetailScaffold
        }
        if (shown.rows.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                if (query.text.isNotBlank()) GeneratedNoResults(query.text) else LibraryEmptyState()
            }
        }

        items(
            items = shown.rows,
            key = { it.key },
            contentType = { row ->
                when (row) {
                    is CollectionRow.Letter -> "letter"
                    is CollectionRow.Single -> "row"
                    is CollectionRow.Tiles -> "tiles"
                }
            },
        ) { row ->
            when (row) {
                is CollectionRow.Letter -> GeneratedGroupHeader(row.section.label)
                is CollectionRow.Single -> rowWrapper(row.item) {
                    val entry = row.entry
                    val active = entry.songId != null && entry.songId == nowPlaying.value?.id
                    PlaylistTrackRow(
                        thumbnailUrl = entry.thumbnailUrl,
                        title = entry.title,
                        subtitle = entry.subtitle.orEmpty(),
                        isActive = active,
                        isPlaying = active && isPlaying.value,
                        onClick = { onOpen(row.item, shown.ordered) },
                        onLongClick = { onMenu(row.item) },
                        onMenuClick = { onMenu(row.item) },
                        coverShape = if (roundCovers) CircleShape else TrackShape,
                        coverModifier = entry.sharedElementKey?.let { Modifier.containerTransformSource(it, cornerRadius = 9.dp) } ?: Modifier,
                        songId = entry.songId,
                    )
                }
                is CollectionRow.Tiles -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, bottom = 16.dp),
                ) {
                    row.tiles.forEach { (item, entry) ->
                        CollectionTile(
                            entry = entry,
                            round = roundCovers,
                            onClick = { onOpen(item, shown.ordered) },
                            onLongClick = { onMenu(item) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // the last row's tiles stay the size of a full row's
                    repeat(GridColumns - row.tiles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        item(key = "bottom_spacer", contentType = "spacer") { Spacer(Modifier.height(60.dp)) }
    }
}

/** "152h 42m" or "42m" */
@Composable
internal fun listeningTime(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) {
        stringResource(R.string.lib_time_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.lib_time_minutes, minutes)
    }
}

// the grid and list switch at the end of the buttons, the one showing a step brighter
@Composable
private fun ViewSwitch(grid: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(3.dp),
    ) {
        ViewSwitchButton(R.drawable.grid_view, stringResource(R.string.lib_view_grid), selected = grid) { onChange(true) }
        ViewSwitchButton(R.drawable.list, stringResource(R.string.lib_view_list), selected = !grid) { onChange(false) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewSwitchButton(icon: Int, description: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (selected) 0.22f else 0f))
            .combinedClickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = Color.White.copy(alpha = if (selected) 1f else 0.55f),
            modifier = Modifier.size(15.dp),
        )
    }
}

// a cover with its name under it, three to a row in the grid
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CollectionTile(
    entry: CollectionEntry,
    round: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = if (round) CircleShape else TileShape
    Column(
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
        modifier = modifier.combinedClickable(
            interactionSource = null,
            indication = HomeCardHighlight,
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(entry.sharedElementKey?.let { Modifier.containerTransformSource(it, cornerRadius = 14.dp) } ?: Modifier)
                .clip(shape)
                .background(BoneColor),
        ) {
            AsyncImage(
                model = entry.thumbnailUrl?.resize(300, 300),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.title,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.94f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        entry.subtitle?.let {
            Text(
                text = it,
                style = TextStyle(fontSize = 11.sp),
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
