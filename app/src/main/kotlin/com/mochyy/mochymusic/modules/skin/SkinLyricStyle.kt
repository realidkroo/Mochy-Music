// a module's own way of showing lyrics: how the sung, past and coming lines sit, how a line comes in, the words lighting up as they're sung, and its own shaders (docs/wiki, lyrics your way)

package com.mochyy.mochymusic.modules.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

@Immutable
data class SkinLyricStyle(
    val size: TextUnit,
    val weight: FontWeight,
    /** the words not sung yet */
    val color: SkinColor,
    /** the words already sung */
    val sung: SkinColor,
    val align: SkinAlign?,
    val gap: Dp,
    /** where the sung line rests, 0 the top of the block and 1 the bottom */
    val anchor: Float,
    val lines: Int,
    /** the pose of the sung line, the ones before it and the ones after it; a line between two blends them */
    val current: Map<PoseProp, Float>,
    val past: Map<PoseProp, Float>,
    val next: Map<PoseProp, Float>,
    /** how the lines move over to the next one */
    val transition: SkinTransition,
    /** keyframes a line plays when it starts being sung */
    val enter: SkinTimeline?,
    /** the sung colour runs across the words as they're sung, not the whole line at once */
    val sweep: Boolean,
    /** the module's shader painting the words, with the line's progress and place */
    val fill: SkinShaderSource?,
    /** the module's shader over the whole block, the drawn lyrics as its content */
    val effect: SkinShaderSource?,
    /** the scale on top of the player's, from the block or one of the module's settings */
    val scale: SkinSize,
    /** the line's translation under it, when the song has one */
    val translation: Boolean,
)
