// a look's own mini player: its blocks in a row beside mochy's cover, over its own ground in the pill, fading out as the player opens (docs/wiki, your own mini player)

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.modules.skin.ActiveMini
import com.mochyy.mochymusic.modules.skin.ModuleStyle
import com.mochyy.mochymusic.modules.skin.SkinBackground
import com.mochyy.mochymusic.ui.player.SongCarousel

/** the mini player's room: the pill's insets from the screen's sides, its height, and where the content starts after mochy's cover */
class MiniPillSpace(val start: Dp, val end: Dp, val height: Dp, val contentStart: Dp)

@Composable
fun ModuleMiniPlayer(mini: ActiveMini, carousel: SongCarousel?, progress: () -> Float, space: MiniPillSpace) {
    // gone by halfway open, the way mochy's own title goes, so the rest of the open never draws it
    val shown by remember(progress) { derivedStateOf { progress() < 0.5f } }
    if (!shown) return
    val showing = remember(progress) { { progress() < 0.5f } }
    CompositionLocalProvider(LocalStyleShowing provides showing) {
        MiniBody(mini, carousel, progress, space)
    }
}

@Composable
private fun MiniBody(mini: ActiveMini, carousel: SongCarousel?, progress: () -> Float, space: MiniPillSpace) {
    val style = remember(mini) { ModuleStyle("${mini.skin.moduleId}/mini", mini.skin, mini.layout, mini.layout.name, null, mini.values) }
    val actions = remember { StyleActions(onOtherMenu = {}, onEditPlayer = {}) }
    val context = rememberSkinContext(style, actions, preview = false, carousel = carousel, values = mini.values, openMenu = {}, openCustomise = {})
    val layout = mini.layout
    Box(
        Modifier
            .fillMaxWidth()
            .height(space.height)
            .graphicsLayer { alpha = (1f - progress() / 0.5f).coerceIn(0f, 1f) },
    ) {
        // the module's own ground over mochy's glass; a mini player that names none keeps the glass as it is
        if (layout.background != SkinBackground.Tones) {
            Box(Modifier.padding(start = space.start, end = space.end).fillMaxSize().clip(RoundedCornerShape(50))) {
                SkinBackdrop(context)
            }
        }
        PlayerFont(mini.skin.moduleId) {
            ScaledContent(context.scale) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = context.fixed(space.contentStart) + layout.padding, end = context.fixed(space.end + EndRoom) + layout.padding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RowBlocks(layout.blocks, context)
                }
            }
        }
    }
}

// room inside the pill's round end
private val EndRoom = 10.dp
