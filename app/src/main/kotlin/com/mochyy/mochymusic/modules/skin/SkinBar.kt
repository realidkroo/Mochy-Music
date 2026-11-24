// a look module's own nav bar: its tabs, their pictures and words, what lights the picked one, its own shader and how it moves (docs/wiki, your own nav bar)

package com.mochyy.mochymusic.modules.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

@Immutable
data class SkinBar(
    /** the tabs in order; empty keeps mochy's own four */
    val tabs: List<SkinBarTab>,
    val height: Dp,
    /** room between the bar and the screen's sides */
    val side: Dp,
    /** null keeps the bar a stadium */
    val corner: Dp?,
    /** mochy's frost, or the look's own glass, behind everything else */
    val glass: Boolean,
    /** colours over the glass, or instead of it */
    val fill: List<SkinColor>,
    val gradient: GradientKind,
    /** the module's own agsl drawn over the glass and the fill, under the tabs */
    val shader: SkinShaderSource?,
    val labels: BarLabels,
    val iconSize: Dp,
    /** the tabs' pictures and words while unlit, and while lit */
    val color: SkinColor?,
    val active: SkinColor?,
    val indicator: BarIndicator,
    /** a sideways drag along the bar picks the tab it's let go on */
    val drag: Boolean,
    /** keyframes a tab plays when it's pressed, when it lights up, and on a loop while it's lit */
    val press: SkinTimeline?,
    val select: SkinTimeline?,
    val idle: SkinTimeline?,
    val outline: Boolean,
    val shadow: Boolean,
    val size: SkinSize,
)

/** one tab: one of mochy's pages or one of the module's own, its pictures (unlit and lit) and its words */
@Immutable
data class SkinBarTab(val target: BarTarget, val icon: String?, val iconOn: String?, val label: String?)

@Immutable
sealed interface BarTarget {
    /** home, search, library or settings */
    data class Tab(val route: String) : BarTarget
    data class Page(val id: String) : BarTarget
    /** an allowlisted safe Mochy route (e.g. history, stats, mochy_settings, audio_lab, auto_playlist/liked) */
    data class Route(val route: String) : BarTarget
}

enum class BarLabels { Never, Selected, Always }

/** what marks the lit tab: mochy's pill, a dot or a line under it, a soft glow, or nothing when the shader draws it */
@Immutable
data class BarIndicator(val kind: IndicatorKind, val color: SkinColor?, val corner: Dp?)

enum class IndicatorKind { Pill, Dot, Line, Glow, None }
