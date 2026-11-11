package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.CrossmixMode
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.screens.audiolab.LabCollapsible
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.screens.audiolab.LabStage
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlin.math.roundToInt

private val GridModes = listOf(CrossmixMode.AUTO_CROSSFADE, CrossmixMode.MANUAL_CROSSFADE, CrossmixMode.CROSSMIX)
private val CardGap = 10.dp
private val SmallCardHeight = 136.dp
private val BigStageHeight = 156.dp
private val MinStageHeight = 44.dp
private val CardPadding = 14.dp
private val CardShape = RoundedCornerShape(26.dp)
private val CardSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 360f)

// where a card is heading, read only in layout and draw so the move never recomposes the page
private class CardMotion(val x: State<Float>, val width: State<Float>, val row: State<Float>, val opened: State<Float>)

/** the three blends: the picked one opens out full width under the other two, which sit side by side as small cards; with nothing picked crossmix keeps the wide spot, closed */
@Composable
internal fun TransitionModeGrid(
    selected: CrossmixMode,
    modifier: Modifier = Modifier,
    card: @Composable (mode: CrossmixMode, opened: State<Float>) -> Unit,
) {
    val wide = if (selected in GridModes) selected else CrossmixMode.CROSSMIX
    val side = GridModes.filter { it != wide }
    val motions = GridModes.map { mode ->
        val isWide = mode == wide
        CardMotion(
            x = animateFloatAsState(if (isWide) 0f else side.indexOf(mode) * 0.5f, CardSpring, label = "cardX"),
            width = animateFloatAsState(if (isWide) 1f else 0.5f, CardSpring, label = "cardWidth"),
            row = animateFloatAsState(if (isWide) 1f else 0f, CardSpring, label = "cardRow"),
            opened = animateFloatAsState(if (mode == selected) 1f else 0f, CardSpring, label = "cardOpen"),
        )
    }

    Layout(
        content = { GridModes.forEachIndexed { i, mode -> card(mode, motions[i].opened) } },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val total = constraints.maxWidth
        val gap = CardGap.roundToPx()
        val rowStep = (SmallCardHeight + CardGap).toPx()
        val placeables = measurables.mapIndexed { i, measurable ->
            val width = (motions[i].width.value * (total + gap) - gap).roundToInt().coerceIn(1, total)
            measurable.measure(Constraints.fixedWidth(width))
        }
        val tops = motions.map { (it.row.value * rowStep).roundToInt() }
        val height = placeables.indices.maxOf { tops[it] + placeables[it].height }
        layout(total, height) {
            placeables.forEachIndexed { i, placeable ->
                // the card that's opening passes over the one closing
                placeable.place((motions[i].x.value * (total + gap)).roundToInt(), tops[i], zIndex = if (GridModes[i] == wide) 1f else 0f)
            }
        }
    }
}

/** one blend: its name, its picture, and its options under the picture while it's the picked one */
@Composable
internal fun TransitionModeCard(
    title: String,
    selected: Boolean,
    opened: State<Float>,
    onSelect: () -> Unit,
    picture: @Composable () -> Unit,
    options: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val selectedLabel = stringResource(R.string.transition_selected).uppercase()
    Layout(
        contents = listOf(
            { CardHeader(title, selectedLabel, opened) },
            { LabStage { picture() } },
            { Column { LabCollapsible(selected) { options() } } },
        ),
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.96f)
            .clip(CardShape)
            .background(LabColors.Card)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = RemoteFocus,
                enabled = !selected,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
    ) { (header, stage, extras), constraints ->
        val pad = CardPadding.roundToPx()
        val space = 10.dp.roundToPx()
        val inner = (constraints.maxWidth - pad * 2).coerceAtLeast(0)
        val headerPlaceable = header.first().measure(Constraints(maxWidth = inner))
        // small, the picture fills what the card has left; open, it grows into the big stage
        val smallStage = (SmallCardHeight.roundToPx() - pad * 2 - headerPlaceable.height - space).coerceAtLeast(MinStageHeight.roundToPx())
        val open = opened.value
        val stageHeight = (smallStage + (BigStageHeight.roundToPx() - smallStage) * open).roundToInt().coerceAtLeast(1)
        val stagePlaceable = stage.first().measure(Constraints.fixed(inner, stageHeight))
        val extrasPlaceable = extras.firstOrNull()?.measure(Constraints(maxWidth = inner))
        val stageTop = pad + headerPlaceable.height + space
        val height = stageTop + stageHeight + (extrasPlaceable?.height ?: 0) + pad
        layout(constraints.maxWidth, height) {
            headerPlaceable.place(pad, pad)
            stagePlaceable.place(pad, stageTop)
            extrasPlaceable?.place(pad, stageTop + stageHeight)
        }
    }
}

// the blend's name, with "selected" fading in at the other end as the card opens
@Composable
private fun CardHeader(title: String, selectedLabel: String, opened: State<Float>) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        Text(
            text = title,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = LabColors.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = selectedLabel,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            color = LabColors.Secondary,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { alpha = opened.value.coerceIn(0f, 1f) },
        )
    }
}
