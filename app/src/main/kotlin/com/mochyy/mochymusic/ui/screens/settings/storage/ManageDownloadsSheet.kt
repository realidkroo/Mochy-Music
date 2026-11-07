// storage → manage downloads: every downloaded song with its size, suggestions of what to clear first, pick songs to remove them together

package com.mochyy.mochymusic.ui.screens.settings.storage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.ui.component.BoneColor
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetLabel
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.SheetSearchField
import com.mochyy.mochymusic.ui.component.detail.PlaylistSelectTick
import com.mochyy.mochymusic.ui.screens.library.showConfirm
import com.mochyy.mochymusic.ui.screens.playlist.SheetNote
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.viewmodels.DownloadedSong
import com.mochyy.mochymusic.viewmodels.StorageViewModel
import com.mochyy.mochymusic.ui.component.RemoteFocus

/** how many songs each suggestion shelf shows */
private const val ShelfSize = 4

/** fewer downloads than this and the suggestions would only repeat the full list */
private const val ShelvesFrom = 8

/** a download younger than this hasn't had a fair chance to be played yet */
private const val SettleMillis = 14L * 24 * 60 * 60 * 1000

private enum class DownloadSort(val label: String) {
    Size("size"),
    Name("name"),
    Recent("recently downloaded"),
    MostPlayed("most played"),
    LeastPlayed("least played");

    fun sort(songs: List<DownloadedSong>): List<DownloadedSong> = when (this) {
        Size -> songs.sortedByDescending { it.bytes }
        Name -> songs.sortedBy { it.song.song.title.lowercase() }
        Recent -> songs.sortedByDescending { it.downloadedAt }
        MostPlayed -> songs.sortedByDescending { it.song.song.totalPlayTime }
        LeastPlayed -> songs.sortedBy { it.song.song.totalPlayTime }
    }
}

/** the songs picked so far, it lives as long as the sheet so searching never drops a pick */
@Stable
private class DownloadPicks {
    val ids = mutableStateListOf<String>()

    fun toggle(id: String) {
        if (!ids.remove(id)) ids.add(id)
    }
}

fun PopupSheetState.showManageDownloadsSheet(viewModel: StorageViewModel) {
    val popup = this
    val picks = DownloadPicks()
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(
        buttonBar = {
            val downloads by viewModel.downloads.collectAsState()
            val chosen = downloads.orEmpty().filter { it.song.id in picks.ids }
            AnimatedContent(
                targetState = chosen.isEmpty(),
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)).togetherWith(fadeOut()) },
                label = "manageDownloadsButton",
            ) { nothingPicked ->
                SheetPillButton(
                    text = if (nothingPicked) {
                        "Done"
                    } else {
                        val n = chosen.size
                        "Remove ${if (n == 1) "1 song" else "$n songs"} · ${formatSize(chosen.sumOf { it.bytes })}"
                    },
                    onClick = {
                        if (chosen.isEmpty()) {
                            sheet?.dismiss()
                            return@SheetPillButton
                        }
                        val ids = chosen.map { it.song.id }
                        popup.showConfirm(
                            title = if (ids.size == 1) "Remove this download?" else "Remove ${ids.size} downloads?",
                            message = "They're deleted from this phone but stay in your library, and stream again " +
                                "when you play them.",
                            action = "Remove",
                        ) {
                            viewModel.removeDownloads(ids)
                            picks.ids.clear()
                        }
                    },
                )
            }
        },
    ) {
        ManageDownloadsContent(viewModel, picks)
    }
}

@Composable
private fun ManageDownloadsContent(viewModel: StorageViewModel, picks: DownloadPicks) {
    val downloads by viewModel.downloads.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(DownloadSort.Size) }
    val needle = query.trim()

    val all = downloads
    val sorted = remember(all, sort) { all?.let(sort::sort).orEmpty() }
    val matches = remember(all, needle) {
        if (needle.isEmpty()) emptyList() else all.orEmpty().filter { d ->
            d.song.song.title.contains(needle, ignoreCase = true) ||
                d.song.artists.any { it.name.contains(needle, ignoreCase = true) }
        }
    }
    val biggest = remember(all) { all.orEmpty().sortedByDescending { it.bytes }.take(ShelfSize) }
    val lessListened = remember(all) { lessListened(all.orEmpty()) }
    val totalBytes = remember(all) { all.orEmpty().sumOf { it.bytes } }

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        SheetSearchField(value = query, onValueChange = { query = it }, placeholder = "Search your downloads")
        Text(
            all?.let { "${it.size} ${if (it.size == 1) "song" else "songs"} · ${formatSize(totalBytes)}" } ?: " ",
            style = storageText(12f, FontWeight.Medium, StorageMuted, 0f),
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 10.dp),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))

        LazyColumn(
            contentPadding = PaddingValues(top = 14.dp, bottom = 12.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            when {
                all == null -> Unit
                all.isEmpty() -> item(key = "empty") { SheetNote("Nothing downloaded yet. Songs you download show up here.") }
                needle.isNotEmpty() -> {
                    if (matches.isEmpty()) {
                        item(key = "no-match") { SheetNote("No downloads match \"$needle\".") }
                    } else {
                        downloadRows("search", matches, picks)
                    }
                }
                else -> {
                    if (all.size >= ShelvesFrom) {
                        shelf("biggest", "Biggest", null, biggest, picks)
                        if (lessListened.isNotEmpty()) {
                            shelf("less", "Less listened", "Downloaded a while ago and hardly played", lessListened, picks)
                        }
                    }
                    item(key = "all-label") {
                        Column(Modifier.padding(bottom = 8.dp)) {
                            SheetLabel("All")
                            SortSwitch(sort) { sort = DownloadSort.entries[(sort.ordinal + 1) % DownloadSort.entries.size] }
                        }
                    }
                    downloadRows("all", sorted, picks)
                }
            }
        }
    }
}

/** downloads at least two weeks old, fewest plays first, no shelf if everything is newer than that */
private fun lessListened(all: List<DownloadedSong>): List<DownloadedSong> {
    val cutoff = System.currentTimeMillis() - SettleMillis
    return all
        .filter { it.downloadedAt in 1..cutoff }
        .sortedWith(compareBy<DownloadedSong> { it.song.song.totalPlayTime }.thenBy { it.downloadedAt })
        .take(ShelfSize)
}

private fun LazyListScope.shelf(
    key: String,
    title: String,
    subtitle: String?,
    songs: List<DownloadedSong>,
    picks: DownloadPicks,
) {
    item(key = "$key-label") {
        val allPicked by remember(songs) { derivedStateOf { songs.all { it.song.id in picks.ids } } }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Column(Modifier.weight(1f)) {
                SheetLabel(title)
                if (subtitle != null) Text(subtitle, style = storageText(12f, FontWeight.Medium, StorageMuted, -0.1f))
            }
            // a whole shelf in one go: it's what the suggestion is for
            Text(
                if (allPicked) "Unpick" else "Pick all",
                style = storageText(12f, FontWeight.SemiBold, Color.White, -0.2f),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable {
                        val ids = songs.map { it.song.id }
                        if (allPicked) picks.ids.removeAll(ids) else ids.filterNot { it in picks.ids }.forEach(picks.ids::add)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    downloadRows(key, songs, picks)
    item(key = "$key-gap") { Spacer(Modifier.height(18.dp)) }
}

private fun LazyListScope.downloadRows(prefix: String, songs: List<DownloadedSong>, picks: DownloadPicks) {
    items(songs, key = { "$prefix-${it.song.id}" }) { download ->
        DownloadRow(download, picked = download.song.id in picks.ids, picking = picks.ids.isNotEmpty()) {
            picks.toggle(download.song.id)
        }
    }
}

/** the sort switch, a tap moves to the next order and the word rolls over to the new one */
@Composable
private fun SortSwitch(sort: DownloadSort, onNext: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onNext)
            .padding(vertical = 2.dp),
    ) {
        Text("Sort by - ", style = storageText(12f, FontWeight.Medium, StorageMuted, 0f))
        AnimatedContent(
            targetState = sort,
            transitionSpec = { (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut()) },
            label = "downloadSort",
        ) { shown ->
            Text("${shown.label} ›", style = storageText(12f, FontWeight.SemiBold, Color.White.copy(alpha = 0.8f), 0f))
        }
    }
}

@Composable
private fun DownloadRow(download: DownloadedSong, picked: Boolean, picking: Boolean, onToggle: () -> Unit) {
    val song = download.song
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (picked) Color.White.copy(alpha = 0.07f) else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = RemoteFocus, onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        AsyncImage(
            model = song.song.thumbnailUrl?.resize(144, 144),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BoneColor),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                song.song.title,
                style = storageText(15f, FontWeight.SemiBold, tracking = -0.3f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artists.joinToString { it.name },
                style = storageText(12f, FontWeight.Medium, StorageMuted, -0.1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatSize(download.bytes), style = storageText(15f, FontWeight.Bold, tracking = -0.4f))
        // the ticks only show once something is picked, so the list stays calm until then
        AnimatedVisibility(
            visible = picking,
            enter = fadeIn() + scaleIn(initialScale = 0.5f),
            exit = fadeOut() + scaleOut(targetScale = 0.5f),
        ) {
            PlaylistSelectTick(checked = picked, modifier = Modifier.padding(start = 4.dp))
        }
        if (!picking) Spacer(Modifier.width(4.dp))
    }
}
