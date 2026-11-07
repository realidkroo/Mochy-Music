// RecentSearches.kt

package com.mochyy.mochymusic.ui.screens.search

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.utils.getGroupedShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

// something opened from search, kept so it can be opened again from the search page with one tap
@Immutable
data class RecentSearch(
    val id: String,
    val kind: Kind,
    val title: String,
    // the artist, the album's artist or the playlist's maker
    val byline: String?,
    val thumbnailUrl: String?,
) {
    enum class Kind { Song, Album, Artist, Playlist }
}

// the songs, albums, artists and playlists people opened from search, newest first, like spotify's recent searches
object RecentSearches {
    private const val Prefs = "recent_searches"
    private const val ListKey = "items"
    private const val MaxKept = 30

    private val _items = MutableStateFlow<List<RecentSearch>?>(null)

    fun items(context: Context): StateFlow<List<RecentSearch>?> {
        if (_items.value == null) _items.value = read(context)
        return _items.asStateFlow()
    }

    fun add(context: Context, item: YTItem) {
        val entry = when (item) {
            is SongItem -> RecentSearch(item.id, RecentSearch.Kind.Song, item.title, item.artists.joinToString { it.name }, item.thumbnail)
            is AlbumItem -> RecentSearch(item.id, RecentSearch.Kind.Album, item.title, item.artists?.joinToString { it.name }, item.thumbnail)
            is ArtistItem -> RecentSearch(item.id, RecentSearch.Kind.Artist, item.title, null, item.thumbnail)
            is PlaylistItem -> RecentSearch(item.id, RecentSearch.Kind.Playlist, item.title, item.author?.name, item.thumbnail)
        }
        save(context, (listOf(entry) + current(context).filterNot { it.id == entry.id }).take(MaxKept))
    }

    fun remove(context: Context, id: String) = save(context, current(context).filterNot { it.id == id })

    fun clear(context: Context) = save(context, emptyList())

    private fun current(context: Context): List<RecentSearch> = _items.value ?: read(context)

    private fun save(context: Context, list: List<RecentSearch>) {
        _items.value = list
        val array = JSONArray(
            list.map {
                JSONObject().put("id", it.id).put("kind", it.kind.name).put("title", it.title)
                    .put("byline", it.byline.orEmpty()).put("thumb", it.thumbnailUrl.orEmpty())
            },
        )
        context.applicationContext.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit { putString(ListKey, array.toString()) }
    }

    private fun read(context: Context): List<RecentSearch> {
        val text = context.applicationContext.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getString(ListKey, null) ?: return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            RecentSearch(
                id = json.optString("id").ifBlank { return@mapNotNull null },
                kind = runCatching { RecentSearch.Kind.valueOf(json.optString("kind")) }.getOrNull() ?: return@mapNotNull null,
                title = json.optString("title"),
                byline = json.optString("byline").takeIf { it.isNotBlank() },
                thumbnailUrl = json.optString("thumb").takeIf { it.isNotBlank() },
            )
        }
    }
}

// the search page's "recent searches": a header with clear, then each one with its cover, what it is, and an x
fun LazyListScope.recentSearchItems(
    items: List<RecentSearch>,
    onOpen: (RecentSearch) -> Unit,
    onRemove: (RecentSearch) -> Unit,
    onClear: () -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "recent_searches_header") {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp).animateItem(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recent_searches),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text(stringResource(R.string.recent_searches_clear)) }
        }
    }
    itemsIndexed(items, key = { _, it -> "recent_${it.kind}_${it.id}" }, contentType = { _, _ -> "recent_search" }) { index, recent ->
        RecentSearchRow(
            recent = recent,
            shape = getGroupedShape(index, items.size),
            onOpen = { onOpen(recent) },
            onRemove = { onRemove(recent) },
            modifier = Modifier.animateItem(),
        )
    }
    item(key = "recent_searches_gap") { Spacer(Modifier.height(16.dp)) }
}

@androidx.compose.runtime.Composable
private fun RecentSearchRow(recent: RecentSearch, shape: Shape, onOpen: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onOpen)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = recent.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(if (recent.kind == RecentSearch.Kind.Artist) CircleShape else RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(recent.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val byline = recent.byline.orEmpty()
            Text(
                text = when (recent.kind) {
                    RecentSearch.Kind.Song -> stringResource(R.string.recent_search_song, byline)
                    RecentSearch.Kind.Album -> stringResource(R.string.recent_search_album, byline)
                    RecentSearch.Kind.Playlist -> stringResource(R.string.recent_search_playlist, byline)
                    RecentSearch.Kind.Artist -> stringResource(R.string.recent_search_artist)
                }.removeSuffix(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.recent_search_remove), modifier = Modifier.size(18.dp))
        }
    }
}
