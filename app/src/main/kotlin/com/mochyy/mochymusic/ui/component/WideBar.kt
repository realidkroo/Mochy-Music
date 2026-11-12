package com.mochyy.mochymusic.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.mochyy.mochymusic.LocalGlassState
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.extensions.toggleRepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import com.mochyy.mochymusic.ui.screens.Screens
import com.mochyy.mochymusic.ui.utils.stableSystemBars

// where the mini player's pill sits across the screen, and whether the wide bar's one glass already lies under it
@Immutable
class MiniPillFrame(
    val start: Dp,
    val end: Dp,
    // the wide bar draws the glass, the outline and the shadow under the mini player and the tabs alike
    val merged: Boolean,
    // the wide mini player has room for a previous button beside play and next
    val withPrevious: Boolean,
)

// the phone's mini player: the full width less 24dp each side, in its own glass
val PhoneMiniPill = MiniPillFrame(start = 24.dp, end = 24.dp, merged = false, withPrevious = false)

val LocalMiniPillFrame = staticCompositionLocalOf { PhoneMiniPill }

// each tab of the wide bar, as wide as the bar is tall, like the concept
private val WideTabWidth = 64.dp

// shuffle and repeat at the pill's right end, a little narrower than a tab so they read as the player's
private val WideExtraWidth = 46.dp
private const val WideExtraCount = 2

// the mini player's share of the wide bar, and the least it can shrink to on a narrow tablet
private val WideMiniWidth = 340.dp
private val WideMiniMinWidth = 240.dp

// the least room left beside the pill on either side
private val WideBarMargin = 24.dp

// the pill sits this far under the top of the bar's row, as on the phone, and the collapsed player lines up with it
val WideBarPillGap = 8.dp

// the wide bar: one pill centred along the bottom, the mini player on its left, the tabs, then shuffle and repeat
@Immutable
class WideBar(
    val pillStart: Dp,
    val miniWidth: Dp,
    val tabsWidth: Dp,
    // shuffle and repeat after the tabs, there only while something plays
    val extrasWidth: Dp,
    val screenWidth: Dp,
    // where the tabs sit on their own, centred, while nothing is playing
    val tabsAloneStart: Dp,
    // where the mini player sits on its own, centred, on a page without tabs
    val miniAloneStart: Dp,
) {
    val tabsStart: Dp get() = pillStart + miniWidth
    val extrasStart: Dp get() = tabsStart + tabsWidth
    val pillWidth: Dp get() = miniWidth + tabsWidth + extrasWidth

    val miniFrame = MiniPillFrame(
        start = pillStart,
        end = screenWidth - pillStart - miniWidth,
        merged = true,
        withPrevious = true,
    )

    val miniAloneFrame = MiniPillFrame(
        start = miniAloneStart,
        end = screenWidth - miniAloneStart - miniWidth,
        merged = false,
        withPrevious = true,
    )
}

// the wide bar's places for a screen this wide, kept clear of the side bars and the camera cutout
@Composable
fun rememberWideBar(screenWidth: Dp, tabCount: Int): WideBar {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val sides = WindowInsets.stableSystemBars.union(WindowInsets.displayCutout)
    val safeStart = with(density) { sides.getLeft(density, direction).toDp() }
    val safeEnd = with(density) { sides.getRight(density, direction).toDp() }
    return remember(screenWidth, tabCount, safeStart, safeEnd) {
        val room = screenWidth - safeStart - safeEnd
        val tabsWidth = WideTabWidth * tabCount + NavRowPadding * 2
        val extrasWidth = WideExtraWidth * WideExtraCount + NavRowPadding
        val miniWidth = WideMiniWidth
            .coerceAtMost(room - WideBarMargin * 2 - tabsWidth - extrasWidth)
            .coerceAtLeast(WideMiniMinWidth)
        WideBar(
            pillStart = safeStart + (room - miniWidth - tabsWidth - extrasWidth) / 2,
            miniWidth = miniWidth,
            tabsWidth = tabsWidth,
            extrasWidth = extrasWidth,
            screenWidth = screenWidth,
            tabsAloneStart = safeStart + (room - tabsWidth) / 2,
            miniAloneStart = safeStart + (room - miniWidth) / 2,
        )
    }
}

// the wide bar's one pill of glass, under the mini player and the tabs so the two read as one; with nothing playing it shrinks onto the tabs
@Composable
fun WideBarBackground(
    bar: WideBar,
    withPlayer: () -> Float,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val glassState = LocalGlassState.current ?: remember { GlassState() }
    val accentWash = rememberChromeAccentWash()
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .offset { IntOffset(lerp(bar.tabsAloneStart, bar.pillStart, withPlayer()).roundToPx(), WideBarPillGap.roundToPx()) }
                // the width is read while laying out, so the shrink onto the tabs never recomposes the bar
                .layout { measurable, _ ->
                    val width = lerp(bar.tabsWidth, bar.pillWidth, withPlayer()).roundToPx()
                    val height = NavBarHeight.roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) { placeable.place(0, 0) }
                }
                .dropShadow(NavBarShape, ChromeShadow)
                .clip(NavBarShape)
                .drawWithContent {
                    drawContent()
                    drawChromeOutline(Offset.Zero, size, cornerRadius = size.height / 2f)
                },
        ) {
            FrostedChromeGlass(
                state = glassState,
                pureBlack = pureBlack,
                accentWash = accentWash,
                modifier = Modifier.fillMaxSize(),
                surface = ChromeSurface.WideBar,
            )
        }
    }
}

// the wide bar's tabs, over its glass at the pill's right end, or centred on their own while nothing is playing
@Composable
fun WideBarTabs(
    bar: WideBar,
    withPlayer: () -> Float,
    navigationItems: List<Screens>,
    // from owningTab: the tab the page on screen belongs to
    selectedTabRoute: String,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // a second quick tap on Search: straight into typing
    onSearchDoubleTap: (() -> Unit)? = null,
) {
    val selectedIndex = navigationItems.indexOfFirst { it.route == selectedTabRoute }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .offset { IntOffset(lerp(bar.tabsAloneStart, bar.tabsStart, withPlayer()).roundToPx(), WideBarPillGap.roundToPx()) }
                .width(bar.tabsWidth)
                .height(NavBarHeight)
                .padding(horizontal = NavRowPadding)
                .navHighlight(selectedIndex, navigationItems.size),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationItems.forEach { screen ->
                NavTab(
                    screen = screen,
                    isSelected = screen.route == selectedTabRoute,
                    onItemClick = onItemClick,
                    onDoubleTap = onSearchDoubleTap.takeIf { screen == Screens.Search },
                )
            }
        }
    }
}

// shuffle and repeat at the pill's right end; they fade in with the player and slide in as the pill grows out of the tabs
@Composable
fun WideBarExtras(
    bar: WideBar,
    withPlayer: () -> Float,
    // with the player away the toggles aren't there at all: faded out they still sat on the last tabs and took their taps
    shown: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!shown) return
    val connection = LocalPlayerConnection.current ?: return
    // the two flows themselves, not the transport state, so a play or pause leaves the toggles alone
    val shuffle by connection.shuffleModeEnabled.collectAsState()
    val repeatMode by connection.repeatMode.collectAsState()
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .offset { IntOffset(lerp(bar.tabsAloneStart + bar.tabsWidth - bar.extrasWidth, bar.extrasStart, withPlayer()).roundToPx(), WideBarPillGap.roundToPx()) }
                .width(bar.extrasWidth)
                .height(NavBarHeight)
                .padding(end = NavRowPadding)
                .graphicsLayer { alpha = ((withPlayer() - 0.4f) / 0.6f).coerceIn(0f, 1f) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WideBarToggle(
                icon = R.drawable.shuffle,
                on = shuffle,
                label = stringResource(R.string.shuffle),
                onClick = { connection.player.shuffleModeEnabled = !connection.player.shuffleModeEnabled },
            )
            WideBarToggle(
                icon = if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
                on = repeatMode != Player.REPEAT_MODE_OFF,
                label = stringResource(R.string.repeat),
                onClick = { connection.player.toggleRepeatMode() },
            )
        }
    }
}

// one of the pill's player toggles: bright with a dot under it while on, dim while off
@Composable
private fun RowScope.WideBarToggle(icon: Int, on: Boolean, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val lit by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "wideBarToggle")
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .pressBounce(interaction, pressedScale = 0.85f)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier
                .size(21.dp)
                .graphicsLayer { alpha = 0.5f + 0.5f * lit },
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 15.dp)
                .size(4.dp)
                .graphicsLayer {
                    alpha = lit
                    scaleX = lit
                    scaleY = lit
                }
                .background(Color.White, CircleShape),
        )
    }
}
