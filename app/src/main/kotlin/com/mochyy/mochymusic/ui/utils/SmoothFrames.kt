// SmoothFrames.kt

package com.mochyy.mochymusic.ui.utils

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

// a frame longer than this counts as this long, the same cap the song swipe uses
private const val MaxFrameNanos = 25_000_000L

// the time an animation sees only moves on by a capped step each frame, so a long frame slows it for a moment instead of skipping part of it
private class CappedFrameClock(private val real: MonotonicFrameClock) : MonotonicFrameClock {
    private var lastReal = 0L
    private var shown = 0L

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
        real.withFrameNanos { now ->
            shown = if (lastReal == 0L) now else shown + (now - lastReal).coerceIn(0L, MaxFrameNanos)
            lastReal = now
            onFrame(shown)
        }
}

// runs animations that should glide through a busy frame, like the tab pill while the new page is being built
suspend fun <T> smoothFrames(block: suspend CoroutineScope.() -> T): T {
    val real = coroutineContext[MonotonicFrameClock] ?: return coroutineScope(block)
    return withContext(CappedFrameClock(real), block)
}
