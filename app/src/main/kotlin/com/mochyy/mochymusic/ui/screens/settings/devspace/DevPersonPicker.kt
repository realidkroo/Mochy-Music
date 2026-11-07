// the capsule beside the back button that switches between people on dev spaces, its pill follows the pager while you swipe

package com.mochyy.mochymusic.ui.screens.settings.devspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.mochyy.mochymusic.ui.component.pressBounce
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import com.mochyy.mochymusic.ui.component.RemoteFocus

internal val DevBarButtonFill = Color.White.copy(alpha = 0.22f)
private val PickerHeight = 36.dp

@Composable
internal fun DevPersonPicker(
    people: List<DevPerson>,
    pagerState: PagerState,
    expanded: Boolean,
    onTap: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments = remember(people.size) { mutableStateListOf(*Array(people.size) { Rect.Zero }) }
    val openness by animateFloatAsState(if (expanded) 1f else 0f, tween(260), label = "pickerOpen")
    val interaction = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.95f)
            .height(PickerHeight)
            .clip(CircleShape)
            .background(DevBarButtonFill)
            // after the padding, so it draws in the same space the people are placed in
            .padding(3.dp)
            .drawBehind {
                // the pager's position, fractional mid-swipe, picks the pill's place between two people
                val pos = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .coerceIn(0f, (people.size - 1).toFloat())
                val from = segments[floor(pos).toInt()]
                val to = segments[ceil(pos).toInt()]
                val t = pos - floor(pos)
                val left = lerp(from.left, to.left, t)
                val right = lerp(from.right, to.right, t)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.14f + 0.2f * openness),
                    topLeft = Offset(left, from.top),
                    size = Size(right - left, from.height),
                    cornerRadius = CornerRadius(from.height / 2f),
                )
            },
    ) {
        people.forEachIndexed { i, person ->
            if (i > 0) {
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .width(1.dp)
                        .height(14.dp)
                        .background(Color.White.copy(alpha = 0.55f)),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .onPlaced { segments[i] = Rect(it.positionInParent(), it.size.toSize()) }
                    .clip(CircleShape)
                    .clickable(interactionSource = interaction, indication = RemoteFocus) { onTap(i) }
                    .graphicsLayer {
                        // whoever you're not on fades back a little, following the swipe too
                        val distance = abs(pagerState.currentPage + pagerState.currentPageOffsetFraction - i)
                        alpha = 1f - 0.35f * distance.coerceIn(0f, 1f)
                    },
            ) {
                DevAvatar(person, Modifier.size(PickerHeight - 6.dp))
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandHorizontally(spring(dampingRatio = 0.78f, stiffness = 520f)) + fadeIn(tween(180, delayMillis = 60)),
                    exit = shrinkHorizontally(spring(dampingRatio = 1f, stiffness = 650f)) + fadeOut(tween(110)),
                ) {
                    Text(
                        person.name,
                        style = devText(13f, FontWeight.Bold, Color.White, -0.4f),
                        maxLines = 1,
                        modifier = Modifier.padding(start = 7.dp, end = 11.dp),
                    )
                }
            }
        }
    }
}
