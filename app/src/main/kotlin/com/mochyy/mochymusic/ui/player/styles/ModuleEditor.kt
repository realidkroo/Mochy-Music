// the player editor on a module player, the way the flagship's works: each part the module marks gets a tile, a tap opens it, a hold drags its slider, and the rest dims while one is open (docs/wiki, your own editor)

package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.modules.skin.SkinEditPart
import com.mochyy.mochymusic.modules.web.FieldRange
import com.mochyy.mochymusic.ui.player.studio.PartTileInk
import com.mochyy.mochymusic.ui.player.studio.drawTileOutline
import kotlin.math.abs
import kotlin.math.roundToInt

/** what the editor knows while it's up over a module player: which part is open, where each part is, and what a hold is dragging */
@Stable
class ModuleEditorState(
    /** how far the editor has come up, 0 to 1 */
    val progress: () -> Float,
    /** the editor is up over a module player */
    val active: () -> Boolean,
    /** a slider's ends, for a hold to drag between */
    private val rangeOf: (moduleId: String, key: String) -> FieldRange?,
    /** keeps a setting once a hold lets go */
    private val commit: (moduleId: String, key: String, value: String) -> Unit,
) {
    /** the part whose panel is open */
    var open by mutableStateOf<String?>(null)

    // each marked block's part and box in the player's own pixels, kept only while the editor is up
    private val pieces = mutableStateMapOf<Any, Pair<String, Rect>>()

    /** one box round every block of [part], null when none of them is on screen */
    fun rectOf(part: String): Rect? = pieces.values.filter { it.first == part }.map { it.second }.reduceOrNull { a, b ->
        Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
    }

    /** the parts that have a block on screen; the rest wait along the bottom of the editor */
    val shown: Set<String> get() = pieces.values.mapTo(HashSet()) { it.first }

    internal fun place(block: Any, part: String, rect: Rect) {
        val old = pieces[block]?.second
        val moved = old == null || abs(old.left - rect.left) > 0.5f || abs(old.top - rect.top) > 0.5f ||
            abs(old.right - rect.right) > 0.5f || abs(old.bottom - rect.bottom) > 0.5f
        if (moved) pieces[block] = part to rect
    }

    internal fun forget(block: Any) {
        pieces.remove(block)
    }

    /** settings shown at once while they're dragged or picked, until the module has the new value */
    val overrides = mutableStateMapOf<String, String>()

    /** the part a finger is holding */
    var held by mutableStateOf<String?>(null)

    internal var root: LayoutCoordinates? = null

    internal fun range(moduleId: String, key: String): FieldRange? = rangeOf(moduleId, key)

    internal fun keep(moduleId: String, key: String, value: String) {
        overrides[key] = value
        commit(moduleId, key, value)
    }

    /** everything back to rest when the editor goes */
    fun reset() {
        open = null
        held = null
        overrides.clear()
        pieces.clear()
    }
}

/** the editor over the module player, when one is up; the selector's cards and the editor's own previews have none */
val LocalModuleEditor = staticCompositionLocalOf<ModuleEditorState?> { null }

/** the player's frame, so every part's box is measured against the same corner */
internal fun Modifier.editRoot(editor: ModuleEditorState?): Modifier =
    if (editor == null) this else onPlaced { editor.root = it }

/** a block of [part]: measured only while the editor is up, from outside the block's own animation so a moving block keeps one tile */
@Composable
internal fun Modifier.editPart(part: String?, editor: ModuleEditorState?): Modifier {
    if (part == null || editor == null) return this
    val active by remember(editor) { derivedStateOf { editor.active() } }
    val block = remember { Any() }
    // a block that goes (a condition switched it off) takes its tile with it
    DisposableEffect(editor, block) { onDispose { editor.forget(block) } }
    if (!active) return this
    return onGloballyPositioned { coordinates ->
        val root = editor.root?.takeIf { it.isAttached } ?: return@onGloballyPositioned
        editor.place(block, part, root.localBoundingBoxOf(coordinates, clipBounds = false))
    }
}

/** the tiles round the player's parts, over everything on it; while the editor is up nothing else on the player takes a touch */
@Composable
internal fun ModuleEditTiles(context: SkinContext, editor: ModuleEditorState?) {
    if (editor == null || context.preview) return
    val active by remember(editor) { derivedStateOf { editor.active() } }
    if (!active) return
    val parts = context.layout.edit
    val haptics = LocalHapticFeedback.current
    val moduleId = context.skin.moduleId
    // read when a hold starts; keying the gesture on it would cancel a drag the moment its own value changed
    val values by rememberUpdatedState(context.values)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val e = editor.progress()
            if (e <= 0.01f) return@Canvas
            val open = editor.open
            val openRect = open?.let(editor::rectOf)
            // with a part open the rest of the player steps back, so it's clear what's being changed
            if (openRect != null) {
                val hole = Path().apply { addRoundRect(RoundRect(openRect.inflate(6.dp.toPx()), CornerRadius(18.dp.toPx()))) }
                clipPath(hole, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.5f * e)) }
            }
            for (part in parts) {
                val rect = editor.rectOf(part.id) ?: continue
                val lit = part.id == open || part.id == editor.held
                if (open != null && !lit) continue
                drawTileOutline(rect.inflate(4.dp.toPx()), e * if (lit) 1f else 0.85f, PartTileInk)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(parts, moduleId) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        down.consume()
                        val part = partAt(parts, editor, down.position)
                        val hold = part?.hold?.let { key -> editor.range(moduleId, key)?.let { key to it } }
                        // let go first: a tap. moved away first: nothing. held long enough on a part with a slider: dragging it
                        val early = withTimeoutOrNull(if (hold != null) viewConfiguration.longPressTimeoutMillis else Long.MAX_VALUE) {
                            var outcome = 0
                            while (outcome == 0) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                outcome = when {
                                    change == null -> Cancelled
                                    !change.pressed -> Tapped
                                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> Cancelled
                                    else -> 0
                                }
                                change?.consume()
                            }
                            outcome
                        }
                        when {
                            early == Tapped -> editor.open = part?.id?.takeIf { it != editor.open }
                            early == null && hold != null && part != null ->
                                dragHold(editor, haptics, part, hold.first, hold.second, moduleId, values, down.position.y)
                        }
                    }
                },
        )
    }
}

// the smallest part under a finger, so a part inside another can still be reached
private fun partAt(parts: List<SkinEditPart>, editor: ModuleEditorState, at: Offset): SkinEditPart? =
    parts.mapNotNull { part -> editor.rectOf(part.id)?.takeIf { it.contains(at) }?.let { part to it } }
        .minByOrNull { (_, rect) -> rect.width * rect.height }?.first

// up makes it bigger and down smaller, the whole range over a long drag, with a tick at each end; kept once it's let go
private suspend fun AwaitPointerEventScope.dragHold(
    editor: ModuleEditorState,
    haptics: HapticFeedback,
    part: SkinEditPart,
    key: String,
    range: FieldRange,
    moduleId: String,
    values: Map<String, String>,
    startY: Float,
) {
    editor.held = part.id
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    val start = values[key]?.toFloatOrNull()?.coerceIn(range.start, range.end) ?: range.start
    val span = DragSpan.toPx()
    var value = start
    var atEnd = false
    try {
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: break
            change.consume()
            if (!change.pressed) break
            val raw = start + (startY - change.position.y) / span * (range.end - range.start)
            val stepped = range.step?.let { step -> range.start + ((raw - range.start) / step).roundToInt() * step } ?: raw
            value = stepped.coerceIn(range.start, range.end)
            val end = value <= range.start || value >= range.end
            if (end && !atEnd) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            atEnd = end
            editor.overrides[key] = formatSetting(value)
        }
    } finally {
        editor.held = null
        if (value != start) editor.keep(moduleId, key, formatSetting(value)) else editor.overrides.remove(key)
    }
}

/** 1.25 stays 1.25 and 2.0 reads as 2, the way the settings page writes a slider's value */
internal fun formatSetting(value: Float): String {
    val rounded = (value * 100f).roundToInt() / 100f
    return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
}

private val DragSpan = 260.dp
private const val Tapped = 1
private const val Cancelled = 2
