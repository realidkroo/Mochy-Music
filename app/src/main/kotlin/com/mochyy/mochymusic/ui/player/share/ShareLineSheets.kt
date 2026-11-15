// ShareLineSheets.kt
//
// The first step of a lyrics share: picking the lines. The one you held is already picked, and
// flies in from the lyrics. Select hands over to the song share, which picks the style next. The
// pieces every share sheet uses live in ShareKit.kt.

package com.mochyy.mochymusic.ui.player.share

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.resize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.mochyy.mochymusic.ui.component.RemoteFocus

/** When a tap picks or drops several rows, each one waits this long per row from where the tap started. */
private const val SweepStepMs = 28L

// ---------------------------------------------------------------------------------------------
// Step 1: lines

internal fun PopupSheetState.showLinePicker(session: LyricsShareSession): PopupSheetHandle {
    val popup = this
    return showWithHandle(
        buttonBar = {
            SheetPillButton(
                text = stringResource(R.string.ps_share_select),
                enabled = session.lineCount > 0,
                onClick = { session.onLinesPicked(popup) },
            )
        },
    ) {
        LinePickerContent(session)
    }
}

@Composable
private fun LinePickerContent(session: LyricsShareSession) {
    LaunchedEffect(Unit) { session.choreography.playForward() }
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        ShareTitle(stringResource(R.string.share))
        PickedSummary(session)
        LazyColumn(
            state = session.choreography.listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            itemsIndexed(session.lines, key = { i, _ -> i }) { index, text ->
                LineRow(
                    session = session,
                    index = index,
                    text = text,
                    modifier = if (index == session.held) Modifier.onPlaced { session.flightTarget = it } else Modifier,
                )
            }
        }
    }
}

/** The cover, how many lines are picked, and how many words that is against each style's limit. */
@Composable
private fun PickedSummary(session: LyricsShareSession) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = session.song.thumbnailUrl?.resize(240, 240),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MenuRowSurface),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                AnimatedContent(
                    targetState = session.lineCount,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically { if (up) it else -it } + fadeIn()).togetherWith(slideOutVertically { if (up) -it else it } + fadeOut())
                    },
                    label = "pickedCount",
                ) { count ->
                    Text(
                        if (count == 0) {
                            stringResource(R.string.ps_share_pick_lines)
                        } else {
                            pluralStringResource(R.plurals.ps_share_lines_selected, count, count)
                        },
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = (-0.5).sp,
                        color = Color.White,
                    )
                }
                Text(
                    limitText(session),
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = ShareMuted,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        WordMeter(session)
    }
}

@Composable
private fun limitText(session: LyricsShareSession): String {
    val words = session.wordCount
    val card = ShareStyle.Card.maxWords
    return when {
        words == 0 -> stringResource(R.string.ps_share_tap_lines)
        session.atLimit -> stringResource(R.string.ps_share_words_max, words)
        words <= card -> stringResource(R.string.ps_share_words_fit_all, words, card)
        else -> stringResource(R.string.ps_share_words_cinematic, words, ShareWordLimit)
    }
}

/**
 * Two tracks, sized by how many words each holds: the card's part fills with the cover's colours,
 * the part only Cinematic holds fills outlined, the same way the rows past a card's limit look.
 */
@Composable
private fun WordMeter(session: LyricsShareSession) {
    val words by animateFloatAsState(session.wordCount.toFloat(), spring(dampingRatio = 0.8f, stiffness = 300f), label = "shareWords")
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .drawBehind {
                val gap = 4.dp.toPx()
                val card = ShareStyle.Card.maxWords.toFloat()
                val cardWidth = (size.width - gap) * card / ShareWordLimit
                val restLeft = cardWidth + gap
                val restWidth = size.width - restLeft
                val radius = CornerRadius(size.height / 2f)
                val brush = pickedBrush(session.palette, size.width)
                drawRoundRect(MenuRowSurface, size = Size(cardWidth, size.height), cornerRadius = radius)
                drawRoundRect(MenuRowSurface, topLeft = Offset(restLeft, 0f), size = Size(restWidth, size.height), cornerRadius = radius)
                val inCard = (words / card).coerceIn(0f, 1f)
                if (inCard > 0f) {
                    drawRoundRect(brush, size = Size((cardWidth * inCard).coerceAtLeast(size.height), size.height), cornerRadius = radius)
                }
                val past = ((words - card) / (ShareWordLimit - card)).coerceIn(0f, 1f)
                if (past > 0f) {
                    val line = 1.5.dp.toPx()
                    val width = (restWidth * past).coerceAtLeast(size.height)
                    drawRoundRect(brush, topLeft = Offset(restLeft, 0f), size = Size(width, size.height), cornerRadius = radius, alpha = 0.25f)
                    drawRoundRect(
                        brush,
                        topLeft = Offset(restLeft + line / 2f, line / 2f),
                        size = Size(width - line, size.height - line),
                        cornerRadius = radius,
                        style = Stroke(line),
                    )
                }
            },
    )
}

/**
 * One lyric line. Picking it fills it with the cover's colours; past a card's limit it's only
 * outlined; a line that can't fit at all is greyed out and shakes when tapped. When one tap picks
 * or drops several lines, they change one after another from where the tap started.
 */
@Composable
private fun LineRow(session: LyricsShareSession, index: Int, text: String, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val picked = session.isPicked(index)
    val pastCard = picked && session.pastCard(index)
    val blocked = !picked && !session.canPick(index)
    val fill = remember { Animatable(if (picked && !pastCard) 1f else 0f) }
    val outline = remember { Animatable(if (pastCard) 1f else 0f) }
    LaunchedEffect(picked, pastCard) {
        delay((abs(index - session.sweepFrom) * SweepStepMs).coerceAtMost(280L))
        launch { fill.animateTo(if (picked && !pastCard) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
        outline.animateTo(if (pastCard) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f))
    }
    val greyed by animateFloatAsState(if (blocked) 1f else 0f, tween(220), label = "lineGreyed")
    val shake = remember { Animatable(0f) }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .graphicsLayer {
                alpha = 1f - 0.65f * greyed
                translationX = shake.value
                with(session.choreography) { applyRowArrival(index) }
            }
            .drawBehind { drawShareRowLook(fill.value, outline.value, session.palette, ShareRowCorner.toPx()) }
            .clickable(interactionSource = interaction, indication = RemoteFocus) {
                if (session.toggle(index)) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.shake(shake)
                }
            }
            .padding(horizontal = ShareRowPadH, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = ShareRowTextStyle,
            modifier = Modifier.graphicsLayer { alpha = 0.7f + 0.3f * maxOf(fill.value, outline.value) },
        )
    }
}
