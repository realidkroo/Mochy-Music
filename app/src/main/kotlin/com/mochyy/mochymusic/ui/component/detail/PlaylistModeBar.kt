// PlaylistModeBar.kt

package com.mochyy.mochymusic.ui.component.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.GlassPillBackground
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.ui.component.RemoteFocus

/**
 * what the playlist top bar shows instead of back and the dots while a song list is in a mode.
 * the screen keeps the state; the bar only draws it and reports taps.
 */
sealed interface PlaylistModeBar {
    /** picking songs after a long press. [onMore] is where the actions for the picked songs live */
    class Select(
        val count: Int,
        val allSelected: Boolean,
        val onToggleAll: () -> Unit,
        val onMore: () -> Unit,
        val onClose: () -> Unit,
    ) : PlaylistModeBar

    /** dragging songs into a new order. the tick locks the order again */
    class EditOrder(
        val onMore: (() -> Unit)?,
        val onDone: () -> Unit,
    ) : PlaylistModeBar
}

private val ModeButtonFill = Color.White.copy(alpha = 0.24f)
private val ModePillShape = RoundedCornerShape(17.dp)

/** the row inside the top bar while a mode is on. sits on the same frost as the normal bar */
@Composable
internal fun PlaylistModeBarRow(
    mode: PlaylistModeBar,
    glassState: GlassState,
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    tint: State<Color>,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        when (mode) {
            is PlaylistModeBar.Select -> {
                SelectTitle(count = mode.count, modifier = Modifier.padding(start = 10.dp))
                Spacer(Modifier.weight(1f))
                SelectAllPill(allSelected = mode.allSelected, onClick = mode.onToggleAll)
                Spacer(Modifier.width(8.dp))
                ModeCircleButton(
                    icon = R.drawable.more_horiz,
                    contentDescription = stringResource(R.string.pc_mode_actions),
                    enabled = mode.count > 0,
                    onClick = mode.onMore,
                )
                Spacer(Modifier.width(8.dp))
                ModeCircleButton(
                    icon = R.drawable.close,
                    contentDescription = stringResource(R.string.pc_mode_stop),
                    onClick = mode.onClose,
                )
            }

            is PlaylistModeBar.EditOrder -> {
                EditOrderPill(glassState = glassState, coverUrl = coverUrl, cover = cover, tint = tint)
                Spacer(Modifier.weight(1f))
                if (mode.onMore != null) {
                    ModeCircleButton(
                        icon = R.drawable.more_horiz,
                        contentDescription = stringResource(R.string.options),
                        onClick = mode.onMore,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                ModeCircleButton(
                    icon = R.drawable.check,
                    contentDescription = stringResource(R.string.done),
                    filled = true,
                    onClick = mode.onDone,
                )
            }
        }
    }
}

/** "Select" until something is picked, then the count, which rolls up or down as it changes */
@Composable
private fun SelectTitle(count: Int, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(spring(dampingRatio = 0.75f, stiffness = 600f)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(140)))
                .togetherWith(slideOutVertically(tween(120)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(100)))
                .using(SizeTransform(clip = false))
        },
        label = "selectTitle",
        modifier = modifier,
    ) { shown ->
        Text(
            text = if (shown == 0) stringResource(R.string.pc_mode_select) else stringResource(R.string.pc_mode_n_selected, shown),
            style = PlaylistTitleStyle,
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun SelectAllPill(allSelected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pressBounce(interaction)
            .height(30.dp)
            .clip(CircleShape)
            .background(ModeButtonFill)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        AnimatedContent(
            targetState = allSelected,
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.75f, stiffness = 600f)) { it / 2 } + fadeIn(tween(140)))
                    .togetherWith(slideOutVertically(tween(120)) { -it / 2 } + fadeOut(tween(100)))
                    .using(SizeTransform(clip = false))
            },
            label = "selectAllLabel",
        ) { all ->
            Text(
                text = stringResource(if (all) R.string.pc_mode_deselect_all else R.string.pc_mode_select_all),
                style = PillLabelStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

/**
 * a 30dp round button, the size of the dots on the normal bar. [filled] is the white one that
 * confirms, so the way out of a mode is the brightest thing up there.
 */
@Composable
private fun ModeCircleButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "modeButtonAlpha")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pressBounce(interaction)
            .graphicsLayer { this.alpha = alpha }
            .size(30.dp)
            .clip(CircleShape)
            .background(if (filled) PillFill else ModeButtonFill)
            .clickable(
                interactionSource = interaction,
                indication = RemoteFocus,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (filled) PillContent else Color.White,
            modifier = Modifier.size(if (filled) 17.dp else 18.dp),
        )
    }
}

/** the frosted fill and hairline of the edit pill; the theme colour is read here so only this recomposes */
@Composable
private fun BoxScope.EditOrderGlass(glassState: GlassState, tint: State<Color>) {
    GlassPillBackground(
        state = glassState,
        blurRadius = { 28f },
        tint = Color.White.copy(alpha = 0.12f),
        foundationColor = tint.value,
        shape = ModePillShape,
        modifier = Modifier.matchParentSize(),
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .border(1.dp, Color.White.copy(alpha = 0.12f), ModePillShape)
    )
}

/** the frosted pill from the mock: a small copy of the cover and what you're doing */
@Composable
private fun EditOrderPill(
    glassState: GlassState,
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    tint: State<Color>,
) {
    Box(modifier = Modifier.height(47.dp)) {
        EditOrderGlass(glassState = glassState, tint = tint)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 5.dp, end = 14.dp),
        ) {
            val miniModifier = Modifier
                .size(37.dp)
                .clip(TrackShape)
            when {
                cover != null -> cover(miniModifier)
                !coverUrl.isNullOrEmpty() -> AsyncImage(
                    model = coverUrl.resize(240, 240),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = miniModifier,
                )
            }
            Text(
                text = stringResource(R.string.pc_mode_edit_order),
                style = PillLabelStyle,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.padding(start = 9.dp),
            )
        }
    }
}

/**
 * the round tick a song row shows while picking. empty it's a thin ring; picked, a white disc
 * pops in with the check on it.
 */
@Composable
fun PlaylistSelectTick(checked: Boolean, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "selectTick",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(36.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .drawBehind {
                    val p = progress.coerceIn(0f, 1f)
                    val ring = 1.5.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f * (1f - p)),
                        radius = (size.minDimension - ring) / 2f,
                        style = Stroke(ring),
                    )
                    // the spring overshoots, so the disc swells a hair past its size and settles
                    drawCircle(color = Color.White, radius = size.minDimension / 2f * progress.coerceAtLeast(0f))
                },
        ) {
            Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = stringResource(if (checked) R.string.pc_mode_selected else R.string.pc_mode_not_selected),
                tint = PillContent,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        val p = progress.coerceAtLeast(0f)
                        alpha = p.coerceAtMost(1f)
                        scaleX = 0.4f + 0.6f * p
                        scaleY = 0.4f + 0.6f * p
                    },
            )
        }
    }
}

/** the soft band behind a picked row, so a long selection reads at a glance */
internal fun Modifier.selectedRowBand(progress: () -> Float): Modifier = drawBehind {
    val p = progress()
    if (p <= 0f) return@drawBehind
    val inset = 14.dp.toPx()
    drawRoundRect(
        color = Color.White.copy(alpha = 0.08f * p.coerceAtMost(1f)),
        topLeft = Offset(inset, 0f),
        size = Size(size.width - inset * 2f, size.height),
        cornerRadius = CornerRadius(14.dp.toPx()),
    )
}
