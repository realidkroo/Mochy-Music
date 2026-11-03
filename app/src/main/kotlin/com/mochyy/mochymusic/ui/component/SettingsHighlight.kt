package com.mochyy.mochymusic.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * Carries "take me to this setting" from a settings search result to the page that owns it.
 *
 * Held here rather than as a navigation argument because the settings routes are registered
 * without arguments - `navigate("appearance_settings?highlight=x")` would simply fail to match.
 *
 * [pending] is claimed by the first matching row that composes, which then flashes and clears it,
 * so returning to the same page later does not re-trigger the highlight.
 */
object SettingsHighlight {
    /** Requested but not yet claimed by a row. */
    var pending: String? by mutableStateOf(null)

    /** Currently flashing. */
    var active: String? by mutableStateOf(null)
}

// a page's scroll and how much of its top a fixed header covers, so a highlighted row can bring itself just under it
class SettingsScroll(val state: ScrollState, val clearTop: Dp)

val LocalSettingsScroll = staticCompositionLocalOf<SettingsScroll?> { null }

// where a highlight target was laid out, kept off the snapshot so scrolling doesn't recompose anything
class SettingsPlacement {
    var coords: LayoutCoordinates? = null
}

// puts the target's top just under the header; bring-into-view only nudged it onto the screen's bottom edge, behind the bottom bar
suspend fun SettingsScroll.reveal(target: LayoutCoordinates, density: Density) {
    if (!target.isAttached) return
    val delta = target.positionInRoot().y - with(density) { clearTop.toPx() }
    state.animateScrollTo((state.value + delta).roundToInt().coerceIn(0, state.maxValue))
}
