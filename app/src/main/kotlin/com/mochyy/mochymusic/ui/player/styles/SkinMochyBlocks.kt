// a module layout's scroll box, and mochy's own lyrics and queue dropped into it whole (docs/wiki, blocks)

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.modules.skin.SkinBlock
import com.mochyy.mochymusic.ui.player.LyricsScreen
import com.mochyy.mochymusic.ui.player.QueueScreen

/** a block that takes the room its column has left when it has no height of its own */
internal val SkinBlock.fillsColumn: Boolean
    get() = (this is SkinBlock.Scroll && height == null) || (this is SkinBlock.MochyLyrics && height == null) ||
        (this is SkinBlock.MochyQueue && height == null)

/** what a filling block gets inside a column that scrolls, where there's no "left over" to take */
internal val FillHeightWhenScrolling = 360.dp

// a scrolling list is never measured without an end, or it crashes; a row or a scrolling column around it can hand it one
private val MaxFill = 1200.dp

// blocks in a box of their own that scrolls; the player's swipe down still closes it from the top, and a sideways one keeps its swipes from changing the song
@Composable
internal fun SkinScroll(block: SkinBlock.Scroll, context: SkinContext, modifier: Modifier) {
    val state = rememberScrollState()
    val sized = modifier.fillMaxWidth().fillHeight(block.height).clipToBounds()
    if (block.row) {
        Row(
            modifier = sized.widthIn(max = MaxFill * 4).horizontalScroll(state, enabled = !context.preview),
            horizontalArrangement = Arrangement.spacedBy(block.gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            block.blocks.forEach { child ->
                if (!context.holds((child as? SkinBlock.Styled)?.condition)) return@forEach
                val plain = child.plain
                // a row that scrolls has no end for a growing space to push against, so every space is just its size
                if (plain is SkinBlock.Space) Spacer(Modifier.width(plain.size)) else Block(child, context, Modifier, inRow = true)
            }
        }
        return
    }
    Column(
        modifier = sized.verticalScroll(state, enabled = !context.preview),
        verticalArrangement = Arrangement.spacedBy(block.gap),
        horizontalAlignment = context.layout.align.horizontal,
    ) {
        ColumnBlocks(block.blocks, context, grow = false)
    }
}

// the lyrics on mochy's own lyrics page, flowing the way they do there; the small picker card skips them, they're far too much for it
@Composable
internal fun SkinMochyLyrics(block: SkinBlock.MochyLyrics, context: SkinContext, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().fillHeight(block.height).clipToBounds()) {
        if (context.preview) return@BoxWithConstraints
        LyricsScreen(
            onClose = {},
            screenHeight = maxHeight,
            contentBottomInset = 0.dp,
            showHeader = block.header,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// mochy's own queue with everything it does: drag to reorder, swipe a song away, its menus and its own buttons
@Composable
internal fun SkinMochyQueue(block: SkinBlock.MochyQueue, context: SkinContext, modifier: Modifier) {
    Box(modifier.fillMaxWidth().fillHeight(block.height).clipToBounds()) {
        if (context.preview) return@Box
        QueueScreen(
            onClose = {},
            contentBottomInset = 0.dp,
            controlsClearance = 0.dp,
            showHeader = block.header,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun Modifier.fillHeight(height: Dp?): Modifier = if (height != null) height(height) else heightIn(max = MaxFill)
