// LyricsProviderSortSheet.kt

package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.lyrics.LyricsProviderRegistry
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.SheetPillButton
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.screens.devices.SheetTitle
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// the lyrics sources top to bottom in the order they're trusted, dragged by their lines; done hands the new order back
fun PopupSheetState.showLyricsProviderSortSheet(order: List<String>, onDone: (List<String>) -> Unit) {
    // shared by the list and the pinned button, which compose apart
    val sorted = mutableStateOf(order)
    var sheet: PopupSheetHandle? = null
    sheet = showWithHandle(
        buttonBar = {
            SheetPillButton(
                text = stringResource(R.string.lyrics_sort_done),
                onClick = {
                    onDone(sorted.value)
                    sheet?.dismiss()
                },
            )
        },
    ) {
        SheetTitle(stringResource(R.string.lyrics_sort_title))
        Spacer(Modifier.height(16.dp))
        ProviderSortList(sorted, Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun ProviderSortList(sorted: MutableState<List<String>>, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val current = sorted.value
        val fromIndex = current.indexOf(from.key)
        val toIndex = current.indexOf(to.key)
        if (fromIndex < 0 || toIndex < 0) return@rememberReorderableLazyListState
        sorted.value = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(sorted.value, key = { it }, contentType = { "provider" }) { name ->
            ReorderableItem(reorderState, key = name) { isDragging ->
                ProviderRow(
                    name = LyricsProviderRegistry.getDisplayName(name),
                    isDragging = isDragging,
                    onPickUp = { haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                    onDrop = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                )
            }
        }
    }
}

// one source as the mock draws it: its name on a grey pill with the three lines to grab on the right
@Composable
private fun ReorderableCollectionItemScope.ProviderRow(
    name: String,
    isDragging: Boolean,
    onPickUp: () -> Unit,
    onDrop: () -> Unit,
) {
    val lift = animateFloatAsState(if (isDragging) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "lift")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .graphicsLayer {
                scaleX = 1f + 0.02f * lift.value
                scaleY = 1f + 0.02f * lift.value
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDragging) lerp(MenuRowSurface, Color.White, 0.1f) else MenuRowSurface)
            // holding anywhere on the row picks it up too, the lines are just the obvious place
            .longPressDraggableHandle(onDragStarted = { onPickUp() }, onDragStopped = { onDrop() })
            .padding(start = 16.dp),
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .draggableHandle(onDragStarted = { onPickUp() }, onDragStopped = { onDrop() }),
        ) {
            Icon(
                painter = painterResource(R.drawable.queue_drag_handle),
                contentDescription = null,
                tint = Color.White.copy(alpha = if (isDragging) 0.9f else 0.5f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
