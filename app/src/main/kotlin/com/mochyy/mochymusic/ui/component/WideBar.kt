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

// the mini player's share of the wide bar, and the least it can shrink to on a narrow tablet
private val WideMiniWidth = 340.dp
private val WideMiniMinWidth = 240.dp

// the least room left beside the pill on either side
private val WideBarMargin = 24.dp

// the pill sits this far under the top of the bar's row, as on the phone, and the collapsed player lines up with it
val WideBarPillGap = 8.dp

// the wide bar: one pill centred along the bottom, the mini player on its left and the tabs on its right
@Immutable
class WideBar(
    val pillStart: Dp,
    val miniWidth: Dp,
    val tabsWidth: Dp,
    val screenWidth: Dp,
    // where the tabs sit on their own, centred, while nothing is playing
    val tabsAloneStart: Dp,
    // where the mini player sits on its own, centred, on a page without tabs
    val miniAloneStart: Dp,
) {
    val tabsStart: Dp get() = pillStart + miniWidth
    val pillWidth: Dp get() = miniWidth + tabsWidth

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
        val miniWidth = WideMiniWidth
            .coerceAtMost(room - WideBarMargin * 2 - tabsWidth)
            .coerceAtLeast(WideMiniMinWidth)
        WideBar(
            pillStart = safeStart + (room - miniWidth - tabsWidth) / 2,
            miniWidth = miniWidth,
            tabsWidth = tabsWidth,
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
