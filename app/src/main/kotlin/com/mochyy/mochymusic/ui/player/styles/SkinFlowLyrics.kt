// a module player's small flowing lyrics: a few lines rolling up as the song goes, the sung one lit (docs/wiki, blocks)

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.mochyy.mochymusic.lyrics.LyricsEntry
import com.mochyy.mochymusic.lyrics.LyricsUtils
import com.mochyy.mochymusic.modules.skin.SkinBlock

@Composable
internal fun SkinFlowLyrics(lines: List<LyricsEntry>, block: SkinBlock.Lyrics, context: SkinContext, modifier: Modifier) {
    val position = context.lyricsPosition
    // the position moves every frame, but the list only hears about it when the sung line changes
    val current by remember(lines, position) { derivedStateOf { LyricsUtils.findCurrentLineIndex(lines, position.positionMs()) } }
    val state = rememberLazyListState()
    // the sung line sits second from the top, so the one just sung stays in view above it
    LaunchedEffect(current, lines) {
        val target = (current - LinesAbove).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        if (state.firstVisibleItemIndex == target && state.firstVisibleItemScrollOffset == 0) return@LaunchedEffect
        if (context.preview) state.scrollToItem(target) else state.animateScrollToItem(target)
    }
    val color = context.color(block.color)
    val lit = block.active?.let(context::color) ?: color
    val align = (block.align ?: context.layout.align).textAlign
    // lines that aren't sung keep the prefix's room, so a monospace column doesn't jump sideways
    val blank = remember(block.prefix) { " ".repeat(block.prefix.length) }
    LazyColumn(
        state = state,
        userScrollEnabled = false,
        contentPadding = PaddingValues(),
        modifier = modifier.fillMaxWidth().height(block.height),
    ) {
        itemsIndexed(lines, key = { index, _ -> index }, contentType = { _, _ -> 0 }) { index, line ->
            val sung = index == current
            Text(
                text = (if (sung) block.prefix else blank) + line.text.ifBlank { Instrumental },
                color = if (sung) lit else color,
                fontSize = block.size,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = align,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (sung) 1f else if (index < current) PastAlpha else NextAlpha },
            )
        }
    }
}

private const val LinesAbove = 1
private const val PastAlpha = 0.35f
private const val NextAlpha = 0.6f
private const val Instrumental = "♪"
