// a section of the modules page: each module's card on a fixed slot, dragged by the lines beside it into order and swiped left to delete

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.ui.component.SwipeAction
import com.mochyy.mochymusic.ui.component.SwipeActionsBox
import com.mochyy.mochymusic.ui.component.SwipeRemoveColor
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import kotlin.math.roundToInt

private val RowGap = 12.dp
private val HandleWidth = 44.dp

@Composable
internal fun ModuleList(
    entries: List<ModuleEntry>,
    onOpen: (ModuleEntry) -> Unit,
    onRemove: (ModuleEntry) -> Unit,
    onReorder: (List<String>) -> Unit,
    // the module being updated right now, and what a tap on a row's update pill does
    updating: String? = null,
    onUpdate: (ModuleEntry) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // rows sit on fixed slots so they can slide; a big system font makes the slots taller
    val rowHeight = 104.dp + 28.dp * (density.fontScale - 1f).coerceAtLeast(0f)
    val slotPx = with(density) { (rowHeight + RowGap).toPx() }
    val byId = entries.associateBy { it.id }
    var order by remember { mutableStateOf(entries.map { it.id }) }
    var dragging by remember { mutableStateOf<String?>(null) }
    // a saved order or a new module coming in mustn't yank the list while a row is held
    LaunchedEffect(entries.map { it.id }) { if (dragging == null) order = entries.map { it.id } }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentOrder by rememberUpdatedState(order)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val shown = order.filter { it in byId }

    Box(Modifier.fillMaxWidth().height(rowHeight * shown.size + RowGap * (shown.size - 1).coerceAtLeast(0))) {
        shown.forEachIndexed { index, id ->
            val entry = byId.getValue(id)
            key(id) {
                val isDragged = dragging == id
                // one position per row: follows the finger while held, springs to its slot after
                val y = remember { Animatable(index * slotPx) }
                val held = remember { floatArrayOf(index * slotPx) }
                LaunchedEffect(isDragged, index) {
                    if (!isDragged) {
                        y.snapTo(held[0])
                        y.animateTo(index * slotPx, spring(dampingRatio = 0.8f, stiffness = 500f))
                    }
                }
                val lift by animateFloatAsState(if (isDragged) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "lift")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .offset {
                            if (isDragged) held[0] = index * slotPx + dragOffset
                            IntOffset(0, (if (isDragged) held[0] else y.value).roundToInt())
                        }
                        .graphicsLayer {
                            scaleX = 1f + 0.025f * lift
                            scaleY = 1f + 0.025f * lift
                        }
                        .fillMaxWidth()
                        .height(rowHeight),
                ) {
                    val removeLabel = stringResource(R.string.module_remove_short)
                    SwipeActionsBox(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        end = if (entry.removable) {
                            { SwipeAction(R.drawable.delete, SwipeRemoveColor, removeLabel) { onRemove(entry) } }
                        } else null,
                        enabled = !isDragged,
                        slab = false,
                    ) {
                        ModuleCard(entry, lifted = isDragged, updating = updating == id, onUpdate = { onUpdate(entry) }, onClick = { onOpen(entry) })
                    }
                    DragHandle(
                        Modifier
                            .width(HandleWidth)
                            .fillMaxHeight()
                            .pointerInput(Unit) { detectTapGestures {} }
                            .pointerInput(id) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = id
                                        dragOffset = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val list = currentOrder
                                        val from = list.indexOf(id)
                                        val to = (from + (dragOffset / slotPx).roundToInt()).coerceIn(0, list.size - 1)
                                        if (to != from) {
                                            order = list.toMutableList().apply { add(to, removeAt(from)) }
                                            dragOffset -= (to - from) * slotPx
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onDragEnd = {
                                        dragging = null
                                        dragOffset = 0f
                                        currentOnReorder(currentOrder)
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        dragOffset = 0f
                                        currentOnReorder(currentOrder)
                                    },
                                )
                            },
                    )
                }
            }
        }
    }
}

/** one module as the mock draws it: its icon, its name, how it's doing, and what it adds with its version in the corner */
@Composable
private fun ModuleCard(entry: ModuleEntry, lifted: Boolean, updating: Boolean, onUpdate: () -> Unit, onClick: () -> Unit) {
    val info = entry.module.info
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(26.dp))
            .background(if (lifted) Color(0xFF222225) else ModuleRowColor)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
    ) {
        ModuleLogo(entry.id, info.name, size = 56, art = entry.icon, corner = 0.24f)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        info.name, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 21.sp,
                        letterSpacing = (-0.6).sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(statusColor(entry), size = 7.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            moduleStatus(entry), fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (entry.update != null) UpdatePill(updating, onUpdate)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    featureSummary(entry.module.adds), fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Box(Modifier.padding(horizontal = 6.dp).size(width = 1.dp, height = 7.dp).background(LabColors.Secondary))
                Text(
                    stringResource(R.string.module_version, info.version), fontFamily = InterFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = Color.White, maxLines = 1,
                )
            }
        }
    }
}

// a newer copy is out: one tap brings it in right from the list
@Composable
private fun UpdatePill(updating: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .clickable(enabled = !updating, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        if (updating) {
            androidx.compose.material3.CircularProgressIndicator(color = Color.Black, strokeWidth = 1.5.dp, modifier = Modifier.size(11.dp))
        } else {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(R.drawable.download), null, tint = Color.Black, modifier = Modifier.size(12.dp),
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(if (updating) R.string.module_updating else R.string.module_update),
            fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black, maxLines = 1,
        )
    }
}

// the mock's three lines beside each card
@Composable
private fun DragHandle(modifier: Modifier) {
    Canvas(modifier) {
        val w = 22.dp.toPx()
        val x0 = size.width - w - 2.dp.toPx()
        val gap = 7.dp.toPx()
        val c = Color.White.copy(alpha = 0.55f)
        for (i in -1..1) {
            val y = size.height / 2 + i * gap
            drawLine(c, Offset(x0, y), Offset(x0 + w, y), 1.6.dp.toPx(), StrokeCap.Round)
        }
    }
}
