// custom "more option" chevron / bubble popup menu for module players: Stack, Pill, Card and Grid layouts with frosted glass, backdrop blur and dynamic conditions

package com.mochyy.mochymusic.ui.player.styles

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.extensions.toggleRepeatMode
import com.mochyy.mochymusic.modules.skin.SkinMenuLayout
import com.mochyy.mochymusic.modules.skin.SkinMenuStyle
import com.mochyy.mochymusic.ui.component.GlassPillBackground
import com.mochyy.mochymusic.ui.player.chevronMenuLabelStyle

private class BubbleEntry(
    val label: String,
    @DrawableRes val icon: Int,
    val active: Boolean = false,
    val picture: Any? = null,
    val accent: Color? = null,
    val onClick: () -> Unit,
)

/**
 * customizable chevron / bubble popup menu over a module player.
 * supports Stack, Pill, Card and Grid layouts, backdrop blur, per-item frosted glass, custom colors/strokes, and custom trigger art.
 */
@Composable
internal fun BoxScope.SkinBubbleMenu(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    context: SkinContext,
    style: SkinMenuStyle,
    end: Dp,
    edge: Dp,
    buttonSize: Dp,
    showButton: Boolean,
    extras: List<StyleMenuExtra>,
    atStart: Boolean,
    buttonTones: StyleTones,
    /** the player's own chevron art, size and shape from its menuButton; the menu style's trigger icon when it has none */
    chevron: ChevronArt? = null,
) {
    val atBottom = style.atBottom
    val itemBase = buttonSize
    @Suppress("NAME_SHADOWING")
    val buttonSize = chevron?.size ?: buttonSize
    val corner = when {
        atStart && atBottom -> Alignment.BottomStart
        atStart -> Alignment.TopStart
        atBottom -> Alignment.BottomEnd
        else -> Alignment.TopEnd
    }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(open) {
        reveal.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 420f))
    }

    val itemSize = style.size ?: (itemBase + 6.dp)
    val itemFill = style.fill.map(context::color)
    val activeFill = style.activeFill?.let(context::color) ?: Color.White.copy(alpha = 0.32f)
    val ink = style.ink?.let(context::color) ?: context.tones.ink
    val labelColor = style.labelColor?.let(context::color) ?: ink
    val labelFill = style.labelFill?.let(context::color)
    val strokeColor = style.strokeColor?.let(context::color) ?: Color.White.copy(alpha = 0.22f)
    val actions = context.actions
    val glass = context.glass

    // Flip only when crossing visibility threshold so opening/closing animates purely in draw/graphicsLayer.
    val menuVisible = remember(reveal) { androidx.compose.runtime.derivedStateOf { reveal.value > 0.001f } }
    if (menuVisible.value) {
        // backdrop dim + optional live backdrop blur over the player
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onOpenChange(false) } },
        ) {
            if (style.backdropBlur > 0.5f && glass != null) {
                val maxRadius = with(LocalDensity.current) { style.backdropBlur.dp.toPx() }
                GlassPillBackground(
                    state = glass,
                    blurRadius = { maxRadius * reveal.value.coerceIn(0f, 1f) },
                    tint = Color.Transparent,
                    tileMode = android.graphics.Shader.TileMode.CLAMP,
                    bleed = false,
                    fallbackBlur = true,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind { drawRect(Color.Black.copy(alpha = (style.dim * 0.55f) * reveal.value.coerceIn(0f, 1f))) },
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind { drawRect(Color.Black.copy(alpha = style.dim * reveal.value.coerceIn(0f, 1f))) },
                )
            }
        }

        val entries = rememberBubbleEntries(actions, extras, style.showDefaults, atBottom, onOpenChange)
        val anchorPad = Modifier
            .align(corner)
            .padding(
                start = if (atStart) (end - 3.dp).coerceAtLeast(8.dp) else 16.dp,
                end = if (atStart) 16.dp else (end - 3.dp).coerceAtLeast(8.dp),
                top = if (atBottom) 16.dp else edge + buttonSize + style.gap,
                bottom = if (atBottom) edge + buttonSize + style.gap else 16.dp,
            )

        when (style.layout) {
            SkinMenuLayout.Stack -> BubbleStackLayout(
                entries, style, reveal, anchorPad, atStart, atBottom, itemSize, itemFill, activeFill, ink, labelColor, labelFill, strokeColor, glass, edge, buttonSize,
            )
            SkinMenuLayout.Pill -> BubblePillLayout(
                entries, style, reveal, anchorPad, atStart, atBottom, itemSize, itemFill, activeFill, ink, labelColor, strokeColor, glass, edge, buttonSize,
            )
            SkinMenuLayout.Card -> BubbleCardLayout(
                entries, style, reveal, anchorPad, atStart, atBottom, itemFill, activeFill, ink, labelColor, strokeColor, glass,
            )
            SkinMenuLayout.Grid -> BubbleGridLayout(
                entries, style, reveal, anchorPad, atStart, atBottom, itemFill, activeFill, ink, labelColor, strokeColor, glass,
            )
        }
    }

    if (showButton) {
        val triggerArt = style.triggerIcon?.let(context.skin::resolve)
        // a menu style's trigger icon is a picture of its own, so it stays still unless the menuButton says otherwise
        val art = chevron?.takeIf { it.picture != null } ?: triggerArt?.let {
            ChevronArt(it, null, chevron?.size, chevron?.shape ?: CircleShape, chevron?.tint, chevron?.scale ?: 0.5f, turns = false, ring = chevron?.ring)
        } ?: chevron
        Box(
            modifier = Modifier
                .align(corner)
                .padding(
                    start = if (atStart) end else 0.dp,
                    end = if (atStart) 0.dp else end,
                    top = if (atBottom) 0.dp else edge,
                    bottom = if (atBottom) edge else 0.dp,
                ),
        ) {
            MenuChevron(open, buttonTones, buttonSize, pill = false, opensDown = !atBottom, onOpenChange = onOpenChange, art = art)
        }
    }
}

@Composable
private fun rememberBubbleEntries(
    actions: StyleActions,
    extras: List<StyleMenuExtra>,
    showDefaults: Boolean,
    atBottom: Boolean,
    onOpenChange: (Boolean) -> Unit,
): List<BubbleEntry> {
    val connection = LocalPlayerConnection.current
    val transport = connection?.uiState?.transportState?.collectAsState()?.value
    val shuffleLabel = stringResource(R.string.shuffle)
    val repeatLabel = stringResource(if (transport?.repeatMode == Player.REPEAT_MODE_ONE) R.string.ps_style_repeat_one else R.string.repeat)
    val lyricsLabel = stringResource(R.string.lyrics)
    val queueLabel = stringResource(R.string.queue)
    val otherMenuLabel = stringResource(R.string.ps_action_other_menu)
    val editPlayerLabel = stringResource(R.string.menu_edit_player)
    val turnLabel = stringResource(if (actions.turn?.isTurned?.invoke() == true) R.string.player_turn_upright else R.string.player_rotate)

    val mappedExtras = extras.map { extra ->
        BubbleEntry(extra.label, extra.icon, extra.active, extra.picture, extra.accent) {
            if (!extra.keepsOpen) onOpenChange(false)
            extra.onClick()
        }
    }
    if (!showDefaults) {
        // always keep OtherMenu + EditPlayer reachable so a module can never lock users out of switching player styles
        return mappedExtras + listOf(
            BubbleEntry(otherMenuLabel, R.drawable.more_horiz) { onOpenChange(false); actions.onOtherMenu() },
            BubbleEntry(editPlayerLabel, R.drawable.tune) { onOpenChange(false); actions.onEditPlayer() },
        )
    }
    val defaults = buildList {
        actions.onLyrics?.let { open -> add(BubbleEntry(lyricsLabel, R.drawable.lyrics) { onOpenChange(false); open() }) }
        actions.onQueue?.let { open -> add(BubbleEntry(queueLabel, R.drawable.queue_music) { onOpenChange(false); open() }) }
        if (connection != null && transport != null) {
            add(
                BubbleEntry(shuffleLabel, R.drawable.shuffle, active = transport.shuffleModeEnabled) {
                    connection.player.shuffleModeEnabled = !connection.player.shuffleModeEnabled
                },
            )
            add(
                BubbleEntry(
                    repeatLabel,
                    if (transport.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
                    active = transport.repeatMode != Player.REPEAT_MODE_OFF,
                ) { connection.player.toggleRepeatMode() },
            )
        }
    }
    val tail = buildList {
        add(BubbleEntry(otherMenuLabel, R.drawable.more_horiz) { onOpenChange(false); actions.onOtherMenu() })
        actions.turn?.let { turn -> add(BubbleEntry(turnLabel, R.drawable.screen_rotation) { onOpenChange(false); turn.toggle() }) }
        add(BubbleEntry(editPlayerLabel, R.drawable.tune) { onOpenChange(false); actions.onEditPlayer() })
    }
    return if (atBottom && actions.turn?.isTurned?.invoke() == true) defaults + mappedExtras + tail else defaults + mappedExtras + tail
}

@Composable
private fun BubbleStackLayout(
    entries: List<BubbleEntry>,
    style: SkinMenuStyle,
    reveal: Animatable<Float, *>,
    anchorPad: Modifier,
    atStart: Boolean,
    atBottom: Boolean,
    itemSize: Dp,
    itemFill: List<Color>,
    activeFill: Color,
    ink: Color,
    labelColor: Color,
    labelFill: Color?,
    strokeColor: Color,
    glass: com.mochyy.mochymusic.ui.component.GlassState?,
    edge: Dp,
    buttonSize: Dp,
) {
    val roomForRows = LocalConfiguration.current.screenHeightDp.dp - edge - buttonSize - style.gap - 24.dp
    val bubbleShape = style.corner?.let { RoundedCornerShape(it) } ?: CircleShape
    Column(
        modifier = anchorPad
            .then(if ((itemSize + style.gap) * entries.size > roomForRows) Modifier.verticalScroll(rememberScrollState(), reverseScrolling = atBottom) else Modifier),
        horizontalAlignment = if (atStart) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(style.gap),
    ) {
        entries.forEachIndexed { index, entry ->
            val order = if (atBottom) entries.size - 1 - index else index
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.graphicsLayer {
                    val delay = (order * 0.08f).coerceAtMost(0.5f)
                    val p = ((reveal.value - delay) / (1f - delay)).coerceIn(0f, 1.2f)
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 24.dp.toPx() * if (atBottom) 1f else -1f
                    val s = 0.72f + 0.28f * p
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(if (atStart) 0f else 1f, 0.5f)
                },
            ) {
                val label = @Composable {
                    val textMod = if (labelFill != null) {
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(labelFill)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    } else Modifier
                    Text(
                        text = entry.label,
                        style = chevronMenuLabelStyle(labelColor, 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = textMod,
                    )
                }
                if (!atStart) label()
                BubbleSurface(
                    shape = bubbleShape,
                    fill = if (entry.active) listOf(entry.accent ?: activeFill) else itemFill.ifEmpty { listOf(Color.White.copy(alpha = 0.16f)) },
                    glassRadius = style.glass,
                    glass = glass,
                    stroke = style.stroke,
                    strokeColor = strokeColor,
                    modifier = Modifier.size(itemSize).clickable(onClick = entry.onClick),
                ) {
                    BubbleGlyph(entry, entry.accent?.takeIf { !entry.active } ?: ink, itemSize * 0.48f)
                }
                if (atStart) label()
            }
        }
    }
}

@Composable
private fun BubblePillLayout(
    entries: List<BubbleEntry>,
    style: SkinMenuStyle,
    reveal: Animatable<Float, *>,
    anchorPad: Modifier,
    atStart: Boolean,
    atBottom: Boolean,
    itemSize: Dp,
    itemFill: List<Color>,
    activeFill: Color,
    ink: Color,
    labelColor: Color,
    strokeColor: Color,
    glass: com.mochyy.mochymusic.ui.component.GlassState?,
    edge: Dp,
    buttonSize: Dp,
) {
    val roomForRows = LocalConfiguration.current.screenHeightDp.dp - edge - buttonSize - style.gap - 24.dp
    val pillShape = RoundedCornerShape(style.corner ?: 50.dp)
    Column(
        modifier = anchorPad
            .then(if ((itemSize + style.gap) * entries.size > roomForRows) Modifier.verticalScroll(rememberScrollState(), reverseScrolling = atBottom) else Modifier),
        horizontalAlignment = if (atStart) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(style.gap),
    ) {
        entries.forEachIndexed { index, entry ->
            val order = if (atBottom) entries.size - 1 - index else index
            BubbleSurface(
                shape = pillShape,
                fill = if (entry.active) listOf(entry.accent ?: activeFill) else itemFill.ifEmpty { listOf(Color(0xCC18181B)) },
                glassRadius = style.glass,
                glass = glass,
                stroke = style.stroke ?: 1.dp,
                strokeColor = strokeColor,
                modifier = Modifier
                    .graphicsLayer {
                        val delay = (order * 0.08f).coerceAtMost(0.5f)
                        val p = ((reveal.value - delay) / (1f - delay)).coerceIn(0f, 1.2f)
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * 20.dp.toPx() * if (atBottom) 1f else -1f
                        val s = 0.8f + 0.2f * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(if (atStart) 0f else 1f, 0.5f)
                    }
                    .clickable(onClick = entry.onClick),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (atStart) BubbleGlyph(entry, entry.accent?.takeIf { !entry.active } ?: ink, 18.dp)
                    Text(
                        text = entry.label,
                        color = labelColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!atStart) BubbleGlyph(entry, entry.accent?.takeIf { !entry.active } ?: ink, 18.dp)
                }
            }
        }
    }
}

@Composable
private fun BubbleCardLayout(
    entries: List<BubbleEntry>,
    style: SkinMenuStyle,
    reveal: Animatable<Float, *>,
    anchorPad: Modifier,
    atStart: Boolean,
    atBottom: Boolean,
    itemFill: List<Color>,
    activeFill: Color,
    ink: Color,
    labelColor: Color,
    strokeColor: Color,
    glass: com.mochyy.mochymusic.ui.component.GlassState?,
) {
    val cardShape = RoundedCornerShape(style.corner ?: 24.dp)
    BubbleSurface(
        shape = cardShape,
        fill = itemFill.ifEmpty { listOf(Color(0xE6141417)) },
        glassRadius = style.glass,
        glass = glass,
        stroke = style.stroke ?: 1.dp,
        strokeColor = strokeColor,
        modifier = anchorPad
            .widthIn(min = 210.dp, max = 270.dp)
            .graphicsLayer {
                val p = reveal.value.coerceIn(0f, 1.15f)
                alpha = p.coerceIn(0f, 1f)
                val s = 0.82f + 0.18f * p
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(if (atStart) 0f else 1f, if (atBottom) 1f else 0f)
            },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            entries.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (entry.active) (entry.accent ?: activeFill).copy(alpha = 0.28f) else Color.Transparent)
                        .clickable(onClick = entry.onClick)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BubbleGlyph(entry, entry.accent ?: ink, 20.dp)
                    Text(
                        text = entry.label,
                        color = labelColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleGridLayout(
    entries: List<BubbleEntry>,
    style: SkinMenuStyle,
    reveal: Animatable<Float, *>,
    anchorPad: Modifier,
    atStart: Boolean,
    atBottom: Boolean,
    itemFill: List<Color>,
    activeFill: Color,
    ink: Color,
    labelColor: Color,
    strokeColor: Color,
    glass: com.mochyy.mochymusic.ui.component.GlassState?,
) {
    val cardShape = RoundedCornerShape(style.corner ?: 24.dp)
    val cols = style.columns.coerceIn(2, 3)
    BubbleSurface(
        shape = cardShape,
        fill = itemFill.ifEmpty { listOf(Color(0xE6141417)) },
        glassRadius = style.glass,
        glass = glass,
        stroke = style.stroke ?: 1.dp,
        strokeColor = strokeColor,
        modifier = anchorPad
            .widthIn(min = 220.dp, max = 300.dp)
            .graphicsLayer {
                val p = reveal.value.coerceIn(0f, 1.15f)
                alpha = p.coerceIn(0f, 1f)
                val s = 0.82f + 0.18f * p
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(if (atStart) 0f else 1f, if (atBottom) 1f else 0f)
            },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entries.chunked(cols).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { entry ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (entry.active) (entry.accent ?: activeFill).copy(alpha = 0.34f) else Color.White.copy(alpha = 0.08f))
                                .clickable(onClick = entry.onClick)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        ) {
                            BubbleGlyph(entry, entry.accent ?: ink, 22.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = entry.label,
                                color = labelColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun BubbleSurface(
    shape: androidx.compose.ui.graphics.Shape,
    fill: List<Color>,
    glassRadius: Float?,
    glass: com.mochyy.mochymusic.ui.component.GlassState?,
    stroke: Dp?,
    strokeColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
        if (glassRadius != null && glass != null) {
            val px = with(LocalDensity.current) { glassRadius.dp.toPx() }
            GlassPillBackground(
                state = glass,
                blurRadius = { px },
                tint = fill.singleOrNull() ?: Color.Black.copy(alpha = 0.25f),
                tileMode = android.graphics.Shader.TileMode.CLAMP,
                bleed = true,
                fallbackBlur = true,
                modifier = Modifier.matchParentSize(),
            )
            if (fill.size > 1) Box(Modifier.matchParentSize().background(Brush.horizontalGradient(fill)))
        } else {
            val brush = if (fill.size <= 1) SolidColor(fill.firstOrNull() ?: Color(0xCC18181B)) else Brush.horizontalGradient(fill)
            Box(Modifier.matchParentSize().background(brush))
        }
        if (stroke != null) Box(Modifier.matchParentSize().border(stroke, strokeColor, shape))
        content()
    }
}

@Composable
private fun BubbleGlyph(entry: BubbleEntry, tint: Color, size: Dp) {
    if (entry.picture != null) {
        AsyncImage(
            model = entry.picture,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
    } else {
        Icon(
            painter = painterResource(entry.icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}
