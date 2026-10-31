// PlayerLayoutTweaks.kt

package com.mochyy.mochymusic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.PlayerCardGrowthKey
import com.mochyy.mochymusic.constants.PlayerControlsShiftKey
import com.mochyy.mochymusic.ui.player.customize.CoverRegionFraction
import com.mochyy.mochymusic.ui.player.customize.MaxCoverRegionFraction
import com.mochyy.mochymusic.ui.player.customize.MinCoverRegionFraction
import com.mochyy.mochymusic.utils.rememberPreference

/** How much taller than usual the card can be pulled: room for a reading, a translation and the next line. */
internal val CardGrowthMax = 72.dp

/** The play buttons always keep this much room above the bottom bar, and below the seek bar. */
private val MinGapAboveBar = 12.dp

/** The part of the flagship a finger is holding in the editor. */
enum class HeldPart { CONTROLS, CARD }

// what the editor lets you hold and drag: the seek bar up and down (the buttons follow halfway, the cover zooms with it) and the card deck taller; read only while laying out or drawing
@Stable
class PlayerLayoutTweaks internal constructor() {
    /** where the controls were put, px below their usual place (negative is higher) */
    internal var shift by mutableFloatStateOf(0f)

    /** how much taller the card was pulled, px */
    internal var growth by mutableFloatStateOf(0f)

    /** the part a finger is holding right now, if any */
    var held by mutableStateOf<HeldPart?>(null)
        private set

    /** the part held last, so what marks it can fade out after it's let go */
    var lastHeld by mutableStateOf<HeldPart?>(null)
        private set

    // The limits, from the player's own layout (see setLimits).
    private var slack by mutableFloatStateOf(0f)
    private var highest by mutableFloatStateOf(0f)
    private var lowest by mutableFloatStateOf(0f)
    private var growthMax by mutableFloatStateOf(0f)

    /** the controls are the flagship's own: the Turntable lays out its own */
    var controlsMovable by mutableStateOf(false)
        private set

    /** the bottom bar is the card deck, and the flagship's controls sit on it */
    var cardGrowable by mutableStateOf(false)
        private set

    /** saves both values, in px; set by [rememberPlayerLayoutTweaks] */
    internal var save: (shift: Float, growth: Float) -> Unit = { _, _ -> }

    // where the parts may go: roomAboveBar is the gap either side of the centred play buttons, and the screen's height bounds the cover
    internal fun setLimits(
        roomAboveBar: Dp,
        screenHeightPx: Float,
        density: Density,
        controlsMovable: Boolean,
        cardGrowable: Boolean,
    ) = with(density) {
        // the buttons only move half as far, so each gap shrinks by half a move: the controls can go twice the spare room
        slack = ((roomAboveBar - MinGapAboveBar) * 2f).toPx().coerceAtLeast(0f)
        highest = (MinCoverRegionFraction - CoverRegionFraction) * screenHeightPx
        lowest = (MaxCoverRegionFraction - CoverRegionFraction) * screenHeightPx
        growthMax = CardGrowthMax.toPx()
        this@PlayerLayoutTweaks.controlsMovable = controlsMovable
        this@PlayerLayoutTweaks.cardGrowable = cardGrowable
    }

    /** how much taller the card is than usual, px */
    fun cardGrowth(): Float = if (cardGrowable) growth.coerceIn(0f, growthLimit()) else 0f

    /** where the controls are, px below their usual place: where they were put, but never into the card */
    fun controlsOffset(): Float = if (controlsMovable) shift.coerceIn(highest, lowestNow()) else 0f

    /** the card grows until the controls it pushes up would leave the cover at its smallest */
    private fun growthLimit(): Float = minOf(growthMax, slack - highest).coerceAtLeast(0f)

    /** the lowest the controls may sit right now: above the card, and the cover no bigger than its biggest */
    private fun lowestNow(): Float = minOf(lowest, slack - cardGrowth()).coerceAtLeast(highest)

    internal fun canMoveControls(up: Boolean): Boolean =
        if (up) controlsOffset() > highest + 0.5f else controlsOffset() < lowestNow() - 0.5f

    internal fun canGrowCard(taller: Boolean): Boolean =
        if (taller) cardGrowth() < growthLimit() - 0.5f else cardGrowth() > 0.5f

    /** picks [part] up, or puts down whatever was held (and saves where it went) */
    internal fun hold(part: HeldPart?) {
        val wasHeld = held != null
        held = part
        if (part != null) lastHeld = part
        if (part == null && wasHeld) save(shift, growth)
    }

    /** moves the controls with the finger; true when they're stopped at an end */
    internal fun dragControls(dy: Float): Boolean {
        val wanted = controlsOffset() + dy
        val placed = wanted.coerceIn(highest, lowestNow())
        shift = placed
        return placed != wanted
    }

    /** pulls the card taller (finger up) or shorter (finger down); true when it's stopped at an end */
    internal fun dragCard(dy: Float): Boolean {
        val wanted = cardGrowth() - dy
        val placed = wanted.coerceIn(0f, growthLimit())
        growth = placed
        return placed != wanted
    }
}

/** The flagship's held-and-dragged layout, kept in the settings. */
@Composable
fun rememberPlayerLayoutTweaks(): PlayerLayoutTweaks {
    val density = LocalDensity.current
    val (shiftDp, saveShiftDp) = rememberPreference(PlayerControlsShiftKey, 0f)
    val (growthDp, saveGrowthDp) = rememberPreference(PlayerCardGrowthKey, 0f)
    // seeded straight away, so the player opens laid out where it was left
    val tweaks = remember {
        PlayerLayoutTweaks().apply {
            shift = shiftDp * density.density
            growth = growthDp * density.density
        }
    }
    tweaks.save = { shift, growth ->
        saveShiftDp(shift / density.density)
        saveGrowthDp(growth / density.density)
    }
    // the settings arriving later (or changed elsewhere), unless a finger is moving the part
    LaunchedEffect(shiftDp, growthDp, density) {
        if (tweaks.held != null) return@LaunchedEffect
        tweaks.shift = shiftDp * density.density
        tweaks.growth = growthDp * density.density
    }
    return tweaks
}
