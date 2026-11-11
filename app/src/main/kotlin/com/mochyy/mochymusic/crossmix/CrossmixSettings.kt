package com.mochyy.mochymusic.crossmix

import com.mochyy.mochymusic.constants.CrossmixLength
import com.mochyy.mochymusic.constants.CrossmixTempoMatch

/** the listener's choices for how crossmix mixes, read by the planner; the defaults are the planner left to itself */
data class CrossmixSettings(
    val length: CrossmixLength = CrossmixLength.MEDIUM,
    val tempoMatch: CrossmixTempoMatch = CrossmixTempoMatch.FULL,
    /** one style for every mix, or null for the one that fits the pair */
    val style: CrossmixStyle? = null,
    /** whether backspins, tape stops, risers and the like may be picked when the energy jumps */
    val stunts: Boolean = true,
) {
    companion object {
        val Default = CrossmixSettings()
    }
}
