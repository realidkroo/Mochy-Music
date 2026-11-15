// PlayerCoverLayout.kt

package com.mochyy.mochymusic.ui.player.customize

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.constants.PlayerCoverStyle

const val CoverRegionFraction = 0.63f

/**
 * How small and how big the cover's region can get when the controls under it are moved (held and
 * dragged in the player editor, or pushed up by a taller card).
 */
const val MinCoverRegionFraction = 0.42f
const val MaxCoverRegionFraction = 0.74f

private const val StageInsetDp = 24f

/** a boxed cover zoomed in still keeps this much room at each side */
private const val MinStageInsetDp = 12f

@Immutable
data class CoverArtBox(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

/** [CoverArtBox] in plain numbers, in whatever unit it was worked out in. */
class CoverBoxF(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * The cover's box, its region's bottom moved [offset] down (up when negative) from where it
 * usually is. Every size is in one unit, px or dp, and [unit] is 1dp in it.
 *
 * The edge-to-edge cover is the region itself, so it zooms the picture in and out. A boxed cover
 * scales with the region, from half its size up to a 12dp margin, and always fits under the
 * status bar. At offset 0 this is exactly the player's usual cover.
 */
fun coverArtBoxF(
    boxed: Boolean,
    width: Float,
    height: Float,
    statusTop: Float,
    unit: Float,
    offset: Float = 0f,
): CoverBoxF {
    val usualRegion = height * CoverRegionFraction
    val region = (usualRegion + offset).coerceIn(height * MinCoverRegionFraction, height * MaxCoverRegionFraction)
    if (!boxed) return CoverBoxF(0f, 0f, width, region)

    val top = statusTop + 16f * unit
    // ponytail: cap side to available height inside region; upgrade with two-pane layout when width allows
    val maxSide = (region - top - 8f * unit).coerceAtLeast(0f)
    val usualSide = (width - StageInsetDp * unit * 2f).coerceIn(0f, maxSide)
    var side = (usualSide * region / usualRegion).coerceAtMost(maxSide)
    if (offset > 0f) side = side.coerceAtMost(minOf(maxSide, maxOf(usualSide, width - MinStageInsetDp * unit * 2f)))
    if (offset < 0f) side = side.coerceAtMost(maxSide.coerceAtLeast(usualSide * 0.5f))
    val y = ((region - side) / 2f).coerceAtLeast(top)
    return CoverBoxF((width - side) / 2f, y, side, side)
}

fun coverArtBox(
    style: PlayerCoverStyle,
    maxWidth: Dp,
    maxHeight: Dp,
    statusBarTop: Dp,
    offset: Dp = 0.dp,
): CoverArtBox {
    val box = coverArtBoxF(style.usesBoxedStage, maxWidth.value, maxHeight.value, statusBarTop.value, 1f, offset.value)
    return CoverArtBox(x = box.x.dp, y = box.y.dp, width = box.width.dp, height = box.height.dp)
}
