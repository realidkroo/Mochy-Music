// a module player's live queue snapshot and custom upcoming-songs block, collected once and read only by the blocks that care (docs/wiki, blocks & player info)

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.skin.QueueTap
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.ui.player.models.QueueItemData
import com.mochyy.mochymusic.ui.player.models.QueueState
import com.mochyy.mochymusic.utils.makeTimeString

/** the queue snapshot held as snapshot state, so queue edits only redraw the blocks that read it */
@Stable
internal class SkinQueue(initial: QueueState = QueueState()) {
    var state by mutableStateOf(initial)
        internal set

    /** a song relative to the one playing now: 1 is next, -1 is previous */
    fun itemAt(offset: Int): QueueItemData? = state.items.getOrNull(state.currentIndex + offset)

    val hasNext: Boolean get() = state.currentIndex >= 0 && state.currentIndex + 1 < state.items.size

    val hasPrev: Boolean get() = state.currentIndex > 0 && state.currentIndex < state.items.size
}

/** null when nothing in the layout reads the queue, so plain players never listen to queue changes */
@Composable
internal fun rememberSkinQueue(needed: Boolean, preview: Boolean): SkinQueue? {
    if (!needed) return null
    if (preview) return remember { SkinQueue(PreviewQueue) }
    val connection = LocalPlayerConnection.current
    val queue = remember(connection) { SkinQueue(connection?.queueSnapshot?.value ?: QueueState()) }
    LaunchedEffect(connection) {
        connection?.queueSnapshot?.collect { queue.state = it }
    }
    return queue
}

/** upcoming songs straight inside a module player, with optional covers, numbers, lengths and tap-to-play */
@Composable
internal fun SkinQueueList(block: SkinBlock.QueueList, context: SkinContext, modifier: Modifier) {
    val state = context.queue?.state ?: QueueState()
    val start = (state.currentIndex + 1).coerceAtLeast(0)
    val upcoming = if (start < state.items.size) state.items.subList(start, (start + block.limit).coerceAtMost(state.items.size)) else emptyList()
    val titleColor = context.color(block.text.color)
    val artistColor = context.color(block.artistColor)
    if (upcoming.isEmpty()) {
        val note = block.empty ?: stringResource(R.string.skin_bar_nothing_next)
        if (note.isNotEmpty()) {
            Text(
                text = if (block.text.upper) note.uppercase() else note,
                color = artistColor,
                fontSize = block.text.size,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier.fillMaxWidth(),
            )
        }
        return
    }
    val connection = LocalPlayerConnection.current
    val interactive = LocalStyleInteractive.current
    val scroll = if (block.height != null && !context.preview) rememberScrollState() else null
    Column(
        verticalArrangement = Arrangement.spacedBy(block.gap),
        modifier = modifier
            .fillMaxWidth()
            .then(block.height?.let { Modifier.height(it) } ?: Modifier)
            .then(if (scroll != null) Modifier.verticalScroll(scroll) else Modifier),
    ) {
        upcoming.forEachIndexed { i, item ->
            val onTap: (() -> Unit)? = when (block.tap) {
                QueueTap.Play -> {
                    { if (!context.preview && interactive()) connection?.playQueueItem(item.uid) }
                }
                QueueTap.Queue -> {
                    { if (!context.preview && interactive()) context.actions.onQueue?.invoke() }
                }
                QueueTap.None -> null
            }
            QueueRow(
                item = item,
                index = i + 1,
                block = block,
                titleColor = titleColor,
                artistColor = artistColor,
                chipColor = context.tones.chip,
                onTap = onTap,
            )
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItemData,
    index: Int,
    block: SkinBlock.QueueList,
    titleColor: androidx.compose.ui.graphics.Color,
    artistColor: androidx.compose.ui.graphics.Color,
    chipColor: androidx.compose.ui.graphics.Color,
    onTap: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(block.corner)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier),
    ) {
        if (block.numbers) {
            Text(
                text = index.toString(),
                color = artistColor,
                fontSize = block.text.size * 0.85f,
                maxLines = 1,
                modifier = Modifier.width(22.dp),
            )
        }
        if (block.covers) {
            if (item.artworkUri != null) {
                AsyncImage(
                    model = item.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(block.coverSize).clip(shape),
                )
            } else {
                Box(Modifier.size(block.coverSize).clip(shape).background(chipColor))
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = if (block.text.upper) item.title.uppercase() else item.title,
                color = titleColor,
                fontSize = block.text.size,
                fontWeight = block.text.weight,
                maxLines = block.text.lines,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.artist.isNotBlank()) {
                Text(
                    text = if (block.text.upper) item.artist.uppercase() else item.artist,
                    color = artistColor,
                    fontSize = block.text.size * 0.85f,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (block.times && item.duration > 0L) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = makeTimeString(item.duration),
                color = artistColor,
                fontSize = block.text.size * 0.85f,
                maxLines = 1,
            )
        }
    }
}

// a small made-up queue for the style picker's still preview, so up-next cards aren't blank there
private val PreviewQueue = QueueState(
    items = listOf(
        QueueItemData(uid = 0, mediaId = "preview-0", title = "Now Playing", artist = "Mochy", artworkUri = null, duration = 214_000L),
        QueueItemData(uid = 1, mediaId = "preview-1", title = "Midnight Walk", artist = "Luna Tape", artworkUri = null, duration = 198_000L),
        QueueItemData(uid = 2, mediaId = "preview-2", title = "Rain on Glass", artist = "Soft Static", artworkUri = null, duration = 242_000L),
        QueueItemData(uid = 3, mediaId = "preview-3", title = "Late Train", artist = "Koto", artworkUri = null, duration = 185_000L),
    ),
    currentIndex = 0,
    title = "Night Drive",
)
