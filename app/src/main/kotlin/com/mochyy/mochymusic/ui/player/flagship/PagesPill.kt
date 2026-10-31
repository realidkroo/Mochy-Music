package com.mochyy.mochymusic.ui.player.flagship

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.player.BehindDimPeak
import com.mochyy.mochymusic.ui.player.CardMorphState
import com.mochyy.mochymusic.ui.player.PlayerCardLook
import com.mochyy.mochymusic.ui.player.PlayerPage
import com.mochyy.mochymusic.ui.player.PlayerPageTransition
import com.mochyy.mochymusic.ui.player.cardRectOr
import com.mochyy.mochymusic.ui.player.expandingCardRect
import com.mochyy.mochymusic.ui.player.mixF
import com.mochyy.mochymusic.ui.player.smoothstepF
import com.mochyy.mochymusic.ui.player.studio.PlayerMenuAction
import kotlinx.coroutines.flow.collectLatest

// the page's clip starts as the pill's round ends and squares off as it fills the screen, no outline
internal val FlagshipPillLook = PlayerCardLook(PillHeight / 2, 0f, 0f, 0f, 0f, 0.dp, chevron = false)

internal val PillIconSize = 12.5.dp

// the half whose page is open, a touch brighter
private val PillLit = Color.White.copy(alpha = 0.2f)

// the button coming away from the pill runs on its own clock, the page's spring is far too quick at its start
private val PillAwaySpec = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)

// lyrics sits first in the pill, so it's on the left unless the layout runs right to left
internal fun pillHalfOnLeft(page: PlayerPage, layoutDirection: LayoutDirection): Boolean =
    (page == PlayerPage.LYRICS) == (layoutDirection == LayoutDirection.Ltr)

// one half of the pill: round on its outer side, its inner corners anywhere from square to round
internal fun Path.setPillHalf(rect: Rect, outer: Float, inner: Float, onLeft: Boolean) {
    reset()
    val round = CornerRadius(outer, outer)
    val cut = CornerRadius(inner, inner)
    addRoundRect(
        RoundRect(
            rect = rect,
            topLeft = if (onLeft) round else cut,
            topRight = if (onLeft) cut else round,
            bottomRight = if (onLeft) cut else round,
            bottomLeft = if (onLeft) round else cut,
        )
    )
}

// lyrics and queue in one pill; a page grows out of its half, and while it's open the other half sits alone at the bar's edge as a pill of its own
@Composable
internal fun PagesPill(
    isActive: (PlayerMenuAction) -> Boolean,
    onAction: (PlayerMenuAction) -> Unit,
    onPageFrom: (Rect) -> Unit,
    pages: PlayerPageTransition?,
) {
    // plain holders, not state: they're only read when a page starts to open
    val bounds = remember { arrayOf(Rect.Zero, Rect.Zero) }
    val currentOnPageFrom by rememberUpdatedState(onPageFrom)
    // how far the pill has come apart, counted from the moment a page starts to move
    val away = remember { Animatable(0f) }
    if (pages != null) {
        // a swipe or the menu can open a page too, so whichever page grows does it out of its own half
        LaunchedEffect(pages) {
            snapshotFlow { pages.linked }.collect { page ->
                val from = if (page == PlayerPage.NONE) Rect.Zero else bounds[page.pillIndex()]
                if (from != Rect.Zero) currentOnPageFrom(from)
            }
        }
        LaunchedEffect(pages) {
            snapshotFlow { pages.headerProgress > 0.0005f }.collectLatest { apart ->
                if (apart) away.animateTo(1f, PillAwaySpec) else away.snapTo(0f)
            }
        }
    }
    // how far apart the pill is: at 1 the half left behind is a round pill of its own, and it closes up as a page lands back in the bar
    fun split(): Float = if (pages == null) 0f else minOf(away.value, smoothstepF(pages.headerProgress / 0.25f))
    // how much of a half is in the pill: none while its page is open or growing out of it
    fun shown(page: PlayerPage): Float {
        if (pages == null) return 1f
        val progress = pages.headerProgress
        if (pages.isCardMorph(page) && progress > 0.0005f && progress < 0.9995f) return 0f
        return 1f - pages.progressOf(page).coerceIn(0f, 1f)
    }
    // with the queue open, lyrics is the one left and slides over into the queue's place at the bar's edge
    fun lyricsSlide(): Float = split() * (1f - shown(PlayerPage.QUEUE))
    // the lone pill at the edge opens the other page; lyrics' own place is empty then
    val openPage = pages?.page ?: PlayerPage.NONE
    val lyricsLit = isActive(PlayerMenuAction.LYRICS)
    val queueLit = isActive(PlayerMenuAction.QUEUE)
    val lyricsLabel = stringResource(R.string.flagship_lyrics)
    val queueLabel = stringResource(R.string.flagship_queue)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(PillWidth)
            .height(PillHeight)
            .drawWithCache {
                val lyricsPath = Path()
                val queuePath = Path()
                val lineWidth = 1.dp.toPx()
                val lineHeight = 15.5.dp.toPx()
                onDrawBehind {
                    val split = split()
                    val half = size.width / 2f
                    val inner = size.height / 2f * split
                    // lyrics is the start half, queue the end half that meets the bar's edge
                    val startOnLeft = layoutDirection == LayoutDirection.Ltr
                    val toEnd = if (startOnLeft) half else -half
                    val startRect = Rect(if (startOnLeft) 0f else half, 0f, if (startOnLeft) half else size.width, size.height)
                    val endRect = startRect.translate(toEnd, 0f)
                    pillHalf(lyricsPath, startRect.translate(toEnd * lyricsSlide(), 0f), startOnLeft, inner, shown(PlayerPage.LYRICS), lyricsLit)
                    pillHalf(queuePath, endRect, !startOnLeft, inner, shown(PlayerPage.QUEUE), queueLit)
                    val line = 0.38f * (1f - split)
                    if (line > 0.002f) {
                        drawRect(
                            color = Color.White.copy(alpha = line),
                            topLeft = Offset((size.width - lineWidth) / 2f, (size.height - lineHeight) / 2f),
                            size = Size(lineWidth, lineHeight),
                        )
                    }
                }
            },
    ) {
        PillHalf(
            painter = painterResource(R.drawable.flagship_lyrics),
            description = lyricsLabel,
            enabled = openPage == PlayerPage.NONE,
            shown = { shown(PlayerPage.LYRICS) },
            slide = ::lyricsSlide,
            onPlaced = { bounds[0] = it },
            onClick = {
                onPageFrom(bounds[0])
                onAction(PlayerMenuAction.LYRICS)
            },
        )
        PillHalf(
            painter = painterResource(R.drawable.flagship_queue),
            description = if (openPage == PlayerPage.QUEUE) lyricsLabel else queueLabel,
            enabled = true,
            shown = { shown(PlayerPage.QUEUE) },
            slide = { 0f },
            onPlaced = { bounds[1] = it },
            onClick = {
                // with the queue open this is lyrics sitting in the queue's place, a straight switch with no button to grow from
                if (openPage == PlayerPage.QUEUE) {
                    onAction(PlayerMenuAction.LYRICS)
                } else {
                    onPageFrom(bounds[1])
                    onAction(PlayerMenuAction.QUEUE)
                }
            },
        )
    }
}

private fun PlayerPage.pillIndex(): Int = if (this == PlayerPage.QUEUE) 1 else 0

// one half of the pages pill, small and faint while it's still coming back
private fun DrawScope.pillHalf(path: Path, rect: Rect, onLeft: Boolean, inner: Float, shown: Float, lit: Boolean) {
    if (shown <= 0.002f) return
    path.setPillHalf(rect, size.height / 2f, inner, onLeft)
    val grow = 0.7f + 0.3f * shown
    scale(grow, grow, rect.center) {
        drawPath(path, PillFill.copy(alpha = PillFill.alpha * shown))
        if (lit) drawPath(path, PillLit.copy(alpha = PillLit.alpha * shown))
    }
}

@Composable
private fun RowScope.PillHalf(
    painter: Painter,
    description: String,
    enabled: Boolean,
    shown: () -> Float,
    // how far toward the bar's edge it has slid, in half-pill widths
    slide: () -> Float,
    onPlaced: (Rect) -> Unit,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val toEnd = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .onGloballyPositioned { onPlaced(it.boundsInRoot()) }
            .clickable(enabled = enabled, interactionSource = source, indication = null, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .size(PillIconSize)
                // gone while its page is open or growing out of it, and along for the slide to the edge
                .graphicsLayer {
                    val s = shown()
                    alpha = s
                    scaleX = 0.7f + 0.3f * s
                    scaleY = 0.7f + 0.3f * s
                    translationX = toEnd * (PillWidth / 2).toPx() * slide()
                }
                .pressShrink(source),
        )
    }
}

// the lyrics or queue button pulling away from its pill and growing into its page, the icon zooming up as it fades
@Composable
internal fun FlagshipPillMorphSurface(
    transition: PlayerPageTransition,
    morph: CardMorphState,
    modifier: Modifier = Modifier,
) {
    val active by remember {
        derivedStateOf {
            transition.linked != PlayerPage.NONE &&
                transition.headerProgress > 0.0005f && transition.headerProgress < 0.9995f
        }
    }
    if (!active) return
    val lyricsIcon = painterResource(R.drawable.flagship_lyrics)
    val queueIcon = painterResource(R.drawable.flagship_queue)
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val path = Path()
                val iconPx = PillIconSize.toPx()
                onDrawBehind {
                    val page = transition.linked
                    val e = transition.headerProgress
                    val t = e.coerceIn(0f, 1f)
                    val dim = BehindDimPeak * kotlin.math.sin(Math.PI.toFloat() * t)
                    if (dim > 0.002f) drawRect(Color.Black.copy(alpha = dim))

                    val from = morph.cardRectOr(size.width, size.height, this)
                    val r = expandingCardRect(from, size.width, size.height, e)
                    // starts exactly as the half it left, then rounds off and fades as it fills the screen
                    val outer = mixF(from.height / 2f, 0f, t)
                    val fill = PillFill.alpha * (1f - smoothstepF(t / 0.55f))
                    if (fill > 0.002f) {
                        // its inner side, square where it touched the other half, rounds off as it pulls away
                        path.setPillHalf(r, outer, outer * smoothstepF(t / 0.3f), pillHalfOnLeft(page, layoutDirection))
                        drawPath(path, Color.White.copy(alpha = fill))
                    }

                    val iconAlpha = 1f - smoothstepF(t / 0.5f)
                    if (iconAlpha > 0.002f) {
                        val zoom = 1f + 1.8f * smoothstepF(t / 0.5f)
                        val icon = if (page == PlayerPage.QUEUE) queueIcon else lyricsIcon
                        translate(r.center.x - iconPx / 2f, r.center.y - iconPx / 2f) {
                            scale(zoom, zoom, Offset(iconPx / 2f, iconPx / 2f)) {
                                with(icon) { draw(Size(iconPx, iconPx), alpha = iconAlpha) }
                            }
                        }
                    }
                }
            },
    )
}
