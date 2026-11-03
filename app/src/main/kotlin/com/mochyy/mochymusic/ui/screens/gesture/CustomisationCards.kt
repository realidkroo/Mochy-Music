// CustomisationCards.kt

package com.mochyy.mochymusic.ui.screens.gesture

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PlayerStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// how far the customize page's cards sit in from the screen's sides, the same as every other settings page
internal val CustomisePageMargin = 20.dp

// the slider runs from a square corner to this many px
internal const val MaxCoverRadius = 45f

// how round covers are until someone changes it, and where the reset puts them back
internal const val DefaultCoverRadius = 25f

internal val CardShape = RoundedCornerShape(22.dp)
internal val SliderCardShape = RoundedCornerShape(20.dp)
private val RoundingCardHeight = 186.dp
internal val StyleCardHeight = 187.dp
internal val CardTextInset = 22.dp

// the cover corner in the rounding card: flush with its top right, this big
private val SwatchWidth = 146.dp
private val SwatchHeight = 135.dp
private val SwatchColor = Color(0xFFD9D9D9)

// the white arc sits this far off the corner, with straight ends this long
private val ArcGap = 19.dp
private val ArcTail = 12.dp

// the outline round the style that's in use
private val HighlightColor = Color.White.copy(alpha = 0.9f)

internal val CardLabelStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.03).em,
)

internal val CardValueStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.045).em,
    color = Color.White,
)

internal val SmallLabelStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 9.sp,
    letterSpacing = (-0.01).em,
)

internal val CaptionStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    letterSpacing = (-0.01).em,
)

// the card and slider that set how round covers are across the app
@Composable
fun CoverRoundingSection(
    radiusPx: Float,
    onRadiusChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var lastTick by remember { mutableIntStateOf((radiusPx / 5f).roundToInt()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RoundingCardHeight)
                .clip(CardShape)
                .background(LabColors.Card),
        ) {
            RoundingSwatch(radiusPx = radiusPx, modifier = Modifier.matchParentSize())
            CardTitle(
                title = stringResource(R.string.customise_cover_rounding),
                canReset = kotlin.math.abs(radiusPx - DefaultCoverRadius) >= 0.5f,
                onReset = { onRadiusChange(DefaultCoverRadius) },
            )
            BasicText(
                text = "${radiusPx.roundToInt()}px",
                style = CardValueStyle,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = CardTextInset, bottom = 24.dp),
            )
        }

        Spacer(Modifier.height(11.dp))

        CustomiseSliderCard(
            title = stringResource(R.string.customise_customize_it),
            startLabel = stringResource(R.string.customise_square),
            endLabel = stringResource(R.string.customise_more_rounded),
            fraction = radiusPx / MaxCoverRadius,
            onFractionChange = { f ->
                val next = f * MaxCoverRadius
                val tick = (next / 5f).roundToInt()
                // a tick every 5px, so the drag feels like it has notches
                if (tick != lastTick) {
                    lastTick = tick
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
                onRadiusChange(next)
            },
        )
    }
}

// a big card's title in its top left, with a small reset after it; the reset fades and stops while everything is at its default
@Composable
internal fun CardTitle(title: String, canReset: Boolean, onReset: (() -> Unit)?) {
    val haptic = LocalHapticFeedback.current
    Row(modifier = Modifier.padding(start = CardTextInset, top = CardTextInset)) {
        Text(text = title, style = CardLabelStyle, color = Color.White.copy(alpha = 0.9f))
        if (onReset != null) {
            Icon(
                painter = painterResource(R.drawable.refresh),
                contentDescription = stringResource(R.string.customise_reset),
                tint = Color.White.copy(alpha = if (canReset) 0.8f else 0.25f),
                modifier = Modifier
                    // lifted so the icon sits on the title's first line, not below it
                    .offset(x = 2.dp, y = (-9).dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canReset) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onReset()
                    }
                    .padding(8.dp),
            )
        }
    }
}

// the slim card under a big one: a title (and its value), a bare slider line, and what each end means
@Composable
internal fun CustomiseSliderCard(
    title: String,
    startLabel: String,
    endLabel: String,
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SliderCardShape)
            .background(LabColors.Card)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(start = CardTextInset, end = CardTextInset, top = 16.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = title, style = SliderTitleStyle, color = Color.White, modifier = Modifier.weight(1f))
            if (valueText != null) Text(text = valueText, style = SliderTitleStyle, color = Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(4.dp))
        BareSlider(fraction = fraction, onFractionChange = onFractionChange, enabled = enabled)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(startLabel, style = SmallLabelStyle, color = Color.White.copy(alpha = 0.5f))
            Text(endLabel, style = SmallLabelStyle, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

private val SliderTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
    letterSpacing = (-0.03).em,
)

// the big cover corner tucked into the card's top right, with the white arc hugging its bend
@Composable
private fun RoundingSwatch(radiusPx: Float, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val r = radiusPx.coerceIn(0f, MaxCoverRadius).dp.toPx()
        val left = size.width - SwatchWidth.toPx()
        val bottom = SwatchHeight.toPx()
        // only the bottom left corner shows, the other three run off past the card's edges
        drawRoundRect(
            color = SwatchColor,
            topLeft = Offset(left, -r),
            size = Size(size.width - left + r, bottom + r),
            cornerRadius = CornerRadius(r, r),
        )

        val gap = ArcGap.toPx()
        val tail = ArcTail.toPx()
        val arcR = r + gap
        val x = left - gap
        val y = bottom + gap
        val path = Path().apply {
            moveTo(x, y - arcR - tail)
            lineTo(x, y - arcR)
            arcTo(
                rect = Rect(left = x, top = y - arcR * 2f, right = x + arcR * 2f, bottom = y),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false,
            )
            lineTo(x + arcR + tail, y)
        }
        drawPath(path = path, color = Color.White, style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

// a bare line like the mock: the bright part is how far along it is, tap or drag anywhere on it
@Composable
private fun BareSlider(fraction: Float, onFractionChange: (Float) -> Unit, enabled: Boolean) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val shown = fraction.coerceIn(0f, 1f)
    val currentShown by androidx.compose.runtime.rememberUpdatedState(shown)
    val currentOnChange by androidx.compose.runtime.rememberUpdatedState(onFractionChange)

    fun updateFromX(x: Float) {
        currentOnChange((x / widthPx).coerceIn(0f, 1f))
    }

    // the finger's own spot, so a slider that snaps to steps still moves when each move is smaller than a step
    var dragX by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        dragX += delta
        updateFromX(dragX)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) { if (enabled) detectTapGestures { offset -> updateFromX(offset.x) } }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStarted = { dragX = currentShown * widthPx },
            ),
    ) {
        val y = size.height / 2f
        val stroke = 3.dp.toPx()
        val start = stroke / 2f
        val end = size.width - stroke / 2f
        drawLine(Color.White.copy(alpha = 0.2f), Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(start, y), Offset(start + (end - start) * shown, y), stroke, StrokeCap.Round)
    }
}

// a swipeable row of style cards: swiping only looks around, tapping one puts it to use and outlines it
@Composable
fun <T> StylePagerSection(
    category: String,
    styles: List<T>,
    selected: T,
    nameOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable BoxScope.(T) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = styles.indexOf(selected).coerceAtLeast(0)) { styles.size }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            // the next card peeks in from the right so it's clear there's more
            contentPadding = PaddingValues(start = CustomisePageMargin, end = CustomisePageMargin + 4.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val style = styles[page]
            StyleCard(
                category = category,
                title = nameOf(style),
                highlighted = style == selected,
                onClick = {
                    if (style != selected) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(style)
                    }
                    if (pagerState.currentPage != page) scope.launch { pagerState.animateScrollToPage(page) }
                },
            ) { preview(style) }
        }
        StyleCaption(stringResource(R.string.customise_swipe_caption), Modifier.padding(start = CustomisePageMargin))
    }
}

// the player has an editor of its own, so its card shows the style in use and taps straight into that editor
@Composable
fun PlayerStyleCard(
    selected: PlayerStyle,
    onOpenEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StyleCard(
            category = stringResource(R.string.customise_player_styles),
            title = selected.displayName,
            highlighted = false,
            onClick = onOpenEditor,
        ) { PlayerStylePreview(style = selected) }
        StyleCaption(stringResource(R.string.customise_player_caption))
    }
}

@Composable
private fun StyleCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = CaptionStyle,
        color = Color.White.copy(alpha = 0.45f),
        modifier = modifier.padding(top = 10.dp),
    )
}

@Composable
private fun StyleCard(
    category: String,
    title: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    preview: @Composable BoxScope.() -> Unit,
) {
    val ring by animateColorAsState(
        targetValue = if (highlighted) HighlightColor else Color.Transparent,
        animationSpec = tween(220),
        label = "styleHighlight",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(StyleCardHeight)
            .clip(CardShape)
            .background(LabColors.Card)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        PreviewFrame(modifier = Modifier.align(Alignment.BottomEnd), content = preview)
        Text(
            text = category,
            style = CardLabelStyle,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(start = CardTextInset, top = CardTextInset),
        )
        // a long name shrinks to fit beside the preview instead of breaking mid-word
        BasicText(
            text = title,
            style = CardValueStyle,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 32.sp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = CardTextInset, bottom = 24.dp, end = PreviewWidth + 28.dp),
        )
        // drawn last so the outline runs over the preview's edge too
        Box(modifier = Modifier.matchParentSize().border(2.dp, ring, CardShape))
    }
}
