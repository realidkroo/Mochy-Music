package com.mochyy.mochymusic.ui.player.studio

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.PlayerBottomBarStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Where the player studio is: the player itself, the selector the player shrinks into, the
 * flagship's edit tiles, and the things a tile opens in place (the cover editor, the middle part,
 * the menu the middle part's chevron leads to, and the bottom bar's styles).
 */
enum class StudioStage { PLAYER, SELECTOR, EDIT, COVER, MIDDLE, MENU, BAR }

/** the sheets the editor opens over itself */
enum class StudioSheet { CONTROLS, SEEKBAR, END_TIME, MENU_SLOT, COVER_OPTIONS, DISC_LABEL, POSTER, IPOD }

/** frames the selector's cards get to be built before the player shrinks into them */
private const val PrepareFrames = 2
/** frames a newly picked style gets to be built (and drawn once) behind its card before it grows */
private const val HandoffFrames = 3

/** one smooth, slightly unhurried spring for every move in the studio, so it all flows as one */
internal val StudioSpring: AnimationSpec<Float> = spring(dampingRatio = 0.88f, stiffness = 210f)

/**
 * A value the studio animates. Drags write it directly (no coroutine, no mutex, so it tracks the
 * finger exactly); letting go settles it with [animateTo]. Writing it cancels any settle.
 */
@Stable
class Motion internal constructor(private val scope: CoroutineScope, initial: Float = 0f) {
    private val state = mutableFloatStateOf(initial)
    private var job: Job? = null

    var value: Float
        get() = state.floatValue
        set(v) {
            job?.cancel()
            job = null
            state.floatValue = v
        }

    val settling: Boolean get() = job?.isActive == true

    // holds it where it is, the way a finger catches a card that's still gliding
    fun stop() {
        job?.cancel()
        job = null
    }

    fun animateTo(target: Float, velocity: Float = 0f, spec: AnimationSpec<Float> = StudioSpring, onEnd: (() -> Unit)? = null) {
        job?.cancel()
        val from = state.floatValue
        if (abs(from - target) < 0.0005f && abs(velocity) < 0.01f) {
            state.floatValue = target
            onEnd?.invoke()
            return
        }
        job = scope.launch {
            animate(from, target, velocity, spec) { v, _ -> state.floatValue = v }
            onEnd?.invoke()
        }
    }
}

/**
 * Positions of the flagship's parts, measured where they sit on the player before the studio
 * moves them, relative to the player sheet (so the sheet shrinking into a card doesn't change
 * them). The studio maps these to where each part goes in the editor.
 */
@Stable
class StudioAnchors {
    private val pending = HashMap<MutableState<Rect>, LayoutCoordinates>()

    /** the player sheet's own coordinates, everything is measured against */
    var root: LayoutCoordinates? = null
        set(value) {
            field = value
            // parts placed before the sheet's root was known are measured now
            if (value != null && pending.isNotEmpty()) {
                val waiting = pending.toList()
                pending.clear()
                waiting.forEach { (slot, coordinates) -> capture(slot, coordinates) }
            }
        }
    val cover = mutableStateOf(Rect.Zero)
    val songInfo = mutableStateOf(Rect.Zero)
    val slider = mutableStateOf(Rect.Zero)
    val transport = mutableStateOf(Rect.Zero)
    val card = mutableStateOf(Rect.Zero)
    val sideButton = mutableStateOf(Rect.Zero)
    val chevron = mutableStateOf(Rect.Zero)
    val seekBar = mutableStateOf(Rect.Zero)
    val endTime = mutableStateOf(Rect.Zero)
    /** the bottom bar in its volume style (the card deck, its other style, is [card]) */
    val volumeBar = mutableStateOf(Rect.Zero)

    /** where the bottom bar sits on the player in [style] */
    fun bar(style: PlayerBottomBarStyle): MutableState<Rect> = when (style) {
        PlayerBottomBarStyle.STACK -> card
        PlayerBottomBarStyle.VOLUME -> volumeBar
    }

    /** the title row and the seek bar together: the editor's middle part */
    val middle: Rect
        get() {
            val a = songInfo.value
            val b = slider.value
            if (a == Rect.Zero) return b
            if (b == Rect.Zero) return a
            return Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
        }

    fun capture(slot: MutableState<Rect>, coordinates: LayoutCoordinates) {
        val base = root
        if (base == null) {
            pending[slot] = coordinates
            return
        }
        if (!base.isAttached || !coordinates.isAttached) return
        val rect = base.localBoundingBoxOf(coordinates, clipBounds = false)
        if (rect != slot.value) slot.value = rect
    }
}

/** measures the part this is on into [slot]. put it before any studio layer on the same part */
fun Modifier.studioAnchor(anchors: StudioAnchors?, slot: (StudioAnchors) -> MutableState<Rect>): Modifier =
    if (anchors == null) this else onGloballyPositioned { anchors.capture(slot(anchors), it) }

/**
 * The studio's state: which stage it's on and every animated value between them. One instance
 * lives with the player for as long as the player does.
 */
@Stable
class PlayerStudioState internal constructor(
    private val scope: CoroutineScope,
    // the selector's columns of style indexes, each look's variants under it; empty puts every style in a column of its own
    val columns: List<List<Int>> = emptyList(),
) {
    // which column a style's card is in, and how far down it
    fun columnOf(style: Int): Int = columns.indexOfFirst { style in it }.takeIf { it >= 0 } ?: style
    fun rowOf(style: Int): Int = columns.getOrNull(columnOf(style))?.indexOf(style)?.coerceAtLeast(0) ?: 0
    fun rowCount(column: Int): Int = columns.getOrNull(column)?.size ?: 1

    // the style index of the card at that column and row
    fun styleAt(column: Int, row: Int): Int =
        columns.getOrNull(column)?.let { it.getOrNull(row.coerceIn(0, it.lastIndex)) } ?: column

    private val rowMotions = HashMap<Int, Motion>()

    init {
        // made up front, so a card being drawn never has to make one
        columns.indices.forEach { rows(it) }
    }

    // each column's position among its variants, in cards; each keeps where it was left while the others are swiped through
    fun rows(column: Int): Motion = rowMotions.getOrPut(column) { Motion(scope) }

    // the column the live player's card is in, and its row there
    val liveColumn: Int get() = columnOf(livePage)
    val liveRow: Int get() = rowOf(livePage)

    var stage by mutableStateOf(StudioStage.PLAYER)
        private set

    /** the player shrunk into its selector card */
    val selector = Motion(scope)
    /** the flagship laid out as edit tiles */
    val edit = Motion(scope)
    /** the cover editor */
    val cover = Motion(scope)
    /** the cover editor scrolled down to the backgrounds (the player zoomed out into a box) */
    val backdrop = Motion(scope)
    /** the middle part pulled forward */
    val middle = Motion(scope)
    /** the menu grown out of the chevron */
    val menu = Motion(scope)
    /** the bottom bar pulled up to the middle of the screen, its styles beside it */
    val bar = Motion(scope)

    /** the selector's position across its columns, in cards */
    val page = Motion(scope)
    /**
     * 1 while a newly picked style's card still covers the live player, which is building that
     * style behind it; it fades to 0 as the player grows back out of the card
     */
    val handoff = Motion(scope)
    /** the card that is the live player (the style it's showing) */
    var livePage by mutableIntStateOf(0)

    /**
     * the cover editor's position among the cover styles, in styles. it moves freely while swiping
     * (and may cross several); the style under it is applied once it comes to rest, so a new swipe
     * can always start straight away from wherever it is
     */
    val coverPage = Motion(scope)
    /** the cover style applied (the live cover); the cover is drawn [coverCommitted] - [coverPage] screens aside */
    var coverCommitted by mutableIntStateOf(0)
    /** the same for the backgrounds */
    val backdropPage = Motion(scope)
    var backdropCommitted by mutableIntStateOf(0)
    /** the same for the bottom bar's styles */
    val barPage = Motion(scope)
    var barCommitted by mutableIntStateOf(0)

    /**
     * the player sheet is covered by the editor's still of it (the middle part's camera): it stops
     * drawing until the camera is back out
     */
    var sheetCovered by mutableStateOf(false)
        internal set

    var sheet by mutableStateOf<StudioSheet?>(null)
    /** which menu slot the menu-slot sheet is choosing for (0..3, or the side button) */
    var sheetSlot by mutableIntStateOf(0)

    val engaged: Boolean
        get() = stage != StudioStage.PLAYER || selector.value > 0.0005f || edit.value > 0.0005f

    /** the editor is up (or coming up): the player's own gestures and controls stand down */
    val editing: Boolean
        get() = stage >= StudioStage.EDIT || edit.value > 0.0005f

    /** the part whose camera move this stage is (the middle part and its menu, or the bottom bar) */
    val cameraPart: LiftedPart?
        get() = when (stage) {
            StudioStage.MIDDLE, StudioStage.MENU -> LiftedPart.MIDDLE
            StudioStage.BAR -> LiftedPart.BAR
            else -> null
        }

    /** how far [part]'s camera has moved in */
    fun camera(part: LiftedPart): Motion = when (part) {
        LiftedPart.MIDDLE -> middle
        LiftedPart.BAR -> bar
    }

    fun open(livePage: Int) {
        if (stage != StudioStage.PLAYER) return
        this.livePage = livePage
        page.value = liveColumn.toFloat()
        // every column opens on its main look, the live one on the variant that's playing
        rowMotions.forEach { (column, motion) -> motion.value = if (column == liveColumn) liveRow.toFloat() else 0f }
        rows(liveColumn).value = liveRow.toFloat()
        handoff.value = 0f
        stage = StudioStage.SELECTOR
        sheet = null
        // prepare, then animate: the cards are built before the player starts shrinking into its
        // own, so their first frames aren't the spring's
        scope.launch {
            repeat(PrepareFrames) { withFrameNanos { } }
            if (stage == StudioStage.SELECTOR) go(StudioStage.SELECTOR)
        }
    }

    /**
     * Back to the player on card [index] - a tap on a card, Set, or a swipe down. When it's another
     * style ([restyled]: the player has just been switched to it) the live player moves under that
     * card, which keeps covering it while the style is built behind it; only then does the player
     * grow back out, the card fading into it. Nothing heavy happens while anything moves.
     */
    fun settleOn(index: Int, restyled: Boolean, velocity: Float = 0f, after: () -> Unit = {}) {
        livePage = index
        if (!restyled) {
            go(StudioStage.PLAYER, velocity)
            after()
            return
        }
        handoff.value = 1f
        stage = StudioStage.PLAYER
        sheet = null
        // a tapped card at the side glides to the middle meanwhile
        page.animateTo(liveColumn.toFloat())
        rows(liveColumn).animateTo(liveRow.toFloat())
        scope.launch {
            repeat(HandoffFrames) { withFrameNanos { } }
            if (stage != StudioStage.PLAYER) return@launch
            go(StudioStage.PLAYER, velocity)
            after()
        }
    }

    // selectorVelocity carries a swipe down's speed into the cards sinking back into the player
    fun go(target: StudioStage, selectorVelocity: Float = 0f) {
        stage = target
        sheet = null
        when (target) {
            StudioStage.PLAYER -> {
                if (handoff.value > 0f) handoff.animateTo(0f, spec = androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                selector.animateTo(0f, velocity = selectorVelocity); edit.animateTo(0f); cover.animateTo(0f)
                backdrop.animateTo(0f); middle.animateTo(0f); menu.animateTo(0f); bar.animateTo(0f)
                page.animateTo(liveColumn.toFloat())
                rows(liveColumn).animateTo(liveRow.toFloat())
            }
            StudioStage.SELECTOR -> {
                handoff.value = 0f
                selector.animateTo(1f); edit.animateTo(0f); cover.animateTo(0f)
                backdrop.animateTo(0f); middle.animateTo(0f); menu.animateTo(0f); bar.animateTo(0f)
            }
            StudioStage.EDIT -> {
                // the editor works on the live card, so the cards line up on it even if something nudged them on the way in
                page.animateTo(liveColumn.toFloat())
                rows(liveColumn).animateTo(liveRow.toFloat())
                selector.animateTo(0f); edit.animateTo(1f); cover.animateTo(0f)
                backdrop.animateTo(0f); middle.animateTo(0f); menu.animateTo(0f); bar.animateTo(0f)
                coverPage.animateTo(coverCommitted.toFloat()); backdropPage.animateTo(backdropCommitted.toFloat())
                barPage.animateTo(barCommitted.toFloat())
            }
            StudioStage.COVER -> {
                coverPage.value = coverCommitted.toFloat()
                backdropPage.value = backdropCommitted.toFloat()
                edit.animateTo(1f)
                cover.animateTo(1f)
            }
            StudioStage.MIDDLE -> { edit.animateTo(1f); middle.animateTo(1f); menu.animateTo(0f) }
            StudioStage.MENU -> { menu.animateTo(1f) }
            StudioStage.BAR -> {
                barPage.value = barCommitted.toFloat()
                edit.animateTo(1f)
                bar.animateTo(1f)
            }
        }
    }

    /** one step back, the way the back button and the editor's back circle go */
    fun back() {
        when (stage) {
            StudioStage.PLAYER -> Unit
            StudioStage.SELECTOR -> go(StudioStage.PLAYER)
            StudioStage.EDIT -> go(StudioStage.SELECTOR)
            StudioStage.COVER -> if (backdrop.value > 0.5f) backdrop.animateTo(0f) else go(StudioStage.EDIT)
            StudioStage.MIDDLE -> go(StudioStage.EDIT)
            StudioStage.MENU -> go(StudioStage.MIDDLE)
            StudioStage.BAR -> go(StudioStage.EDIT)
        }
    }

    /** leaves at once, with no animation (the player closed under it) */
    fun reset() {
        stage = StudioStage.PLAYER
        sheet = null
        listOf(selector, edit, cover, backdrop, middle, menu, bar, handoff).forEach { it.value = 0f }
        page.value = liveColumn.toFloat()
        rows(liveColumn).value = liveRow.toFloat()
        coverPage.value = coverCommitted.toFloat()
        backdropPage.value = backdropCommitted.toFloat()
        barPage.value = barCommitted.toFloat()
    }

    fun openSheet(sheet: StudioSheet, slot: Int = 0) {
        sheetSlot = slot
        this.sheet = sheet
    }
}

@Composable
fun rememberPlayerStudioState(columns: List<List<Int>> = emptyList()): PlayerStudioState {
    val scope = rememberCoroutineScope()
    return remember(scope, columns) { PlayerStudioState(scope, columns) }
}

// ---- geometry ---------------------------------------------------------------------------

/** a uniform scale about a part's centre, then a move */
data class PartTransform(val scale: Float = 1f, val dx: Float = 0f, val dy: Float = 0f) {
    fun lerpTo(other: PartTransform, t: Float): PartTransform = PartTransform(
        scale + (other.scale - scale) * t,
        dx + (other.dx - dx) * t,
        dy + (other.dy - dy) * t,
    )

    /** where [rect] ends up */
    fun apply(rect: Rect): Rect {
        val cx = rect.center.x + dx
        val cy = rect.center.y + dy
        val hw = rect.width * scale / 2f
        val hh = rect.height * scale / 2f
        return Rect(cx - hw, cy - hh, cx + hw, cy + hh)
    }

    companion object {
        val Identity = PartTransform()

        /** moves and scales [from] onto [to]'s centre at [scale] */
        fun fit(from: Rect, to: Rect, scale: Float) = PartTransform(scale, to.center.x - from.center.x, to.center.y - from.center.y)
    }
}

/** the selector card's size against the screen, and where its centre sits */
internal const val CardScale = 0.72f
internal const val CardCenterY = 0.465f
internal val CardCorner: Dp = 34.dp
internal val CardGap: Dp = 18.dp

/** the box the player zooms out into for the backgrounds */
internal const val BoxScale = 0.6f
internal const val BoxCenterY = 0.43f
internal val BoxCorner: Dp = 30.dp

/**
 * Where the editor's tiles are: around the flagship's parts, right where they sit on the player.
 * Nothing moves or shrinks to make room - the tiles are drawn around the parts as they are.
 */
class TileLayout(
    val coverTile: Rect,
    val middleTile: Rect,
    val controlsTile: Rect,
    /** round the bottom bar; none when the player has no bar to edit */
    val barTile: Rect?,
    /** the full-bleed cover has no top edge to outline: its tile is just its sides, fading off */
    val fullCover: Boolean,
)

internal val TileMargin = 14.dp
internal val TilePadding = 10.dp
internal val TileCorner = 30.dp
internal val TileStroke = 3.5.dp

fun tileLayout(
    width: Float,
    height: Float,
    density: Density,
    anchors: StudioAnchors,
    fullCover: Boolean,
    barStyle: PlayerBottomBarStyle,
): TileLayout = with(density) {
    val margin = TileMargin.toPx()
    val pad = TilePadding.toPx()
    val gap = 8.dp.toPx()
    val cover = anchors.cover.value.takeIf { it.width > 0f } ?: Rect(margin, height * 0.1f, width - margin, height * 0.5f)
    val middle = anchors.middle.takeIf { it.width > 0f } ?: Rect(0f, height * 0.62f, width, height * 0.74f)
    val transport = anchors.transport.value.takeIf { it.width > 0f } ?: Rect(0f, height * 0.78f, width, height * 0.86f)
    val bar = anchors.bar(barStyle).value.takeIf { it.width > 0f }

    val coverTile = if (fullCover) {
        Rect(margin, 0f, width - margin, cover.bottom.coerceAtMost(middle.top - gap * 2))
    } else {
        Rect(cover.left - pad, cover.top - pad, cover.right + pad, cover.bottom + pad)
    }
    var middleTile = Rect(margin, middle.top - pad, width - margin, middle.bottom + pad)
    var controlsTile = Rect(margin, transport.top - pad, width - margin, transport.bottom + pad)
    // the card runs off the bottom of the screen, so its tile does too: no bottom edge to draw
    var barTile = bar?.let {
        val bottom = if (barStyle == PlayerBottomBarStyle.STACK) height + TileCorner.toPx() * 2f else it.bottom + pad
        Rect(margin, it.top - pad, width - margin, bottom)
    }
    // tiles that would touch share the space between them
    if (middleTile.bottom + gap > controlsTile.top) {
        val split = (middle.bottom + transport.top) / 2f
        middleTile = middleTile.copy(bottom = split - gap / 2f)
        controlsTile = controlsTile.copy(top = split + gap / 2f)
    }
    if (bar != null && barTile != null && controlsTile.bottom + gap > barTile.top) {
        val split = (transport.bottom + bar.top) / 2f
        controlsTile = controlsTile.copy(bottom = split - gap / 2f)
        barTile = barTile.copy(top = split + gap / 2f)
    }
    TileLayout(
        coverTile = coverTile,
        middleTile = middleTile,
        controlsTile = controlsTile,
        barTile = barTile,
        fullCover = fullCover,
    )
}
