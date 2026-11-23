// IndexGlide.kt

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

// how far short of a far letter the list lands before gliding the rest of the way in
private val GlideLead = 220.dp

// no bounce: a list that swings past the letter and back reads as a mistake
private const val GlideStiffness = Spring.StiffnessMediumLow
private val GlideSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = GlideStiffness)

// the spring's own pace: carrying in more speed than this times the distance left would swing it past the letter
private val GlideRate = sqrt(GlideStiffness)

// where a picked letter rests on the playlist-style pages: just under the collapsed back pill
@Composable
fun indexLandingUnderBar(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp

// slides a list to the row a rail letter starts at instead of jumping there
class IndexGlide internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val landingPx: Float,
    private val leadPx: Float,
) {
    private var job: Job? = null
    private var speed = 0f

    // a letter on screen glides straight to its spot; a far one is jumped to just short of it, so the list never crawls through thousands of rows
    fun glideTo(index: Int) {
        // a scrub hands each new letter the speed of the last glide, so the list keeps flowing instead of stopping at every letter
        val carried = if (job?.isActive == true) speed else 0f
        job?.cancel()
        job = scope.launch { glide(index, carried) }
    }

    private suspend fun glide(index: Int, carried: Float) {
        // never above where the list's own padding starts, or the letter would hide under the page's title
        val landing = (landingPx + listState.layoutInfo.viewportStartOffset).coerceAtLeast(0f)
        val visible = offsetOf(index)
        if (visible == null) {
            val goingDown = index > listState.firstVisibleItemIndex
            listState.scrollToItem(index)
            val start = landing + if (goingDown) leadPx else -leadPx
            listState.scrollBy((offsetOf(index) ?: return) - start)
        }
        val distance = (offsetOf(index) ?: return) - landing
        if (abs(distance) < 1f) return
        val limit = abs(distance) * GlideRate
        val initial = if (carried * distance > 0f) carried.coerceIn(-limit, limit) else 0f
        listState.scroll {
            var moved = 0f
            animate(0f, distance, initial, GlideSpring) { value, velocity ->
                scrollBy(value - moved)
                moved = value
                speed = velocity
            }
        }
        speed = 0f
    }

    private fun offsetOf(index: Int): Float? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset?.toFloat()
}

// [landing] is how far below the top of the list's box a picked letter comes to rest
@Composable
fun rememberIndexGlide(listState: LazyListState, landing: Dp = 0.dp): IndexGlide {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val landingPx = with(density) { landing.toPx() }
    val leadPx = with(density) { GlideLead.toPx() }
    return remember(listState, scope, landingPx, leadPx) { IndexGlide(listState, scope, landingPx, leadPx) }
}
