// a module player's own lyrics page and queue page, opened in place of mochy's and made of player blocks like the player itself (docs/wiki, your own pages)

package com.mochyy.mochymusic.ui.player.styles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.ModuleStyle
import com.mochyy.mochymusic.modules.skin.SkinBackground
import com.mochyy.mochymusic.modules.skin.SkinPlayer
import com.mochyy.mochymusic.ui.utils.stableStatusBarsPadding

/** whether the module player on now asks for the white bar at the top of the player to go */
internal fun moduleHidesHandle(playerStyle: PlayerStyle): Boolean =
    playerStyle == PlayerStyle.MODULE && ActiveSkin.style?.player?.handle == false

/** the lyrics page over a style: the module player's own when it brings one, mochy's otherwise */
@Composable
internal fun StyleOrModuleLyricsPage(
    open: Boolean,
    onClose: () -> Unit,
    screenHeight: Dp,
    playerStyle: PlayerStyle,
    actions: StyleActions,
    onCoveredChange: (Boolean) -> Unit,
    onSwitchSource: () -> Unit,
) {
    val module = ActiveSkin.style?.takeIf { playerStyle == PlayerStyle.MODULE }
    val page = module?.player?.lyricsPage
    if (module != null && page != null) {
        ModulePlayerPage(open, onClose, page, "lyrics", module, actions, onCoveredChange)
    } else {
        StyleLyricsPage(open = open, onClose = onClose, screenHeight = screenHeight, onCoveredChange = onCoveredChange, onSwitchSource = onSwitchSource)
    }
}

/** the queue page over a style: the module player's own when it brings one, mochy's otherwise */
@Composable
internal fun StyleOrModuleQueuePage(
    open: Boolean,
    onClose: () -> Unit,
    playerStyle: PlayerStyle,
    actions: StyleActions,
    onCoveredChange: (Boolean) -> Unit,
) {
    val module = ActiveSkin.style?.takeIf { playerStyle == PlayerStyle.MODULE }
    val page = module?.player?.queuePage
    if (module != null && page != null) {
        ModulePlayerPage(open, onClose, page, "queue", module, actions, onCoveredChange)
    } else {
        StyleQueuePage(open = open, onClose = onClose, onCoveredChange = onCoveredChange)
    }
}

// rises over the player on its own backdrop; back or a close button in it goes back to the player
@Composable
private fun ModulePlayerPage(
    open: Boolean,
    onClose: () -> Unit,
    page: SkinPlayer,
    name: String,
    style: ModuleStyle,
    actions: StyleActions,
    onCoveredChange: (Boolean) -> Unit,
) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(open) {
        shown.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = if (open) 260f else 420f))
    }
    BackHandler(enabled = open, onBack = onClose)
    // a page that lets the player show through (a tones background) never covers it, so the player keeps drawing
    val opaque = page.background != SkinBackground.Tones
    val currentOnCovered by rememberUpdatedState(onCoveredChange)
    LaunchedEffect(opaque) {
        snapshotFlow { opaque && shown.value >= 0.999f }.collect { currentOnCovered(it) }
    }
    if (!open && shown.value < 0.001f) return

    val pageStyle = remember(style, page, name) { ModuleStyle("${style.key}#$name", style.skin, page, style.name, style.sub, style.values) }
    val pageActions = remember(actions, onClose) { actions.closingWith(onClose) }
    val context = rememberSkinContext(pageStyle, pageActions, preview = false, carousel = null, values = style.values, openMenu = {}, openCustomise = {})
    // room under the status bar for the player's white bar, unless the module took it away
    val top = if (style.player.handle) 40.dp else 12.dp
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = shown.value.coerceIn(0f, 1f)
                alpha = p
                translationY = (1f - p) * 48.dp.toPx()
                // each draw fades on its own instead of the whole page going through one buffer
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        SkinBackdrop(context)
        PlayerFont(style.skin.moduleId) {
            ScaledContent(context.scale) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (page.scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .stableStatusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = page.padding, end = page.padding, top = top, bottom = 16.dp),
                    horizontalAlignment = page.align.horizontal,
                ) {
                    ColumnBlocks(page.blocks, context, grow = !page.scroll)
                }
            }
        }
    }
}
