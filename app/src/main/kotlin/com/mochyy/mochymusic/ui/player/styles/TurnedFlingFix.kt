package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.util.VelocityTrackerAddPointsFix

// compose reads a fling's speed off the raw screen touches, which don't turn with the video mode's sideways frame, so its lists flung the wrong way; the older way reads the turned touches
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TurnedFlingFix(active: Boolean = true) {
    if (!active) return
    DisposableEffect(Unit) {
        TurnedFrames.open()
        onDispose { TurnedFrames.close() }
    }
}

// how many turned frames are up, so the fling goes back to normal only once the last of them leaves
@OptIn(ExperimentalComposeUiApi::class)
private object TurnedFrames {
    private var count = 0

    fun open() {
        if (count++ == 0) VelocityTrackerAddPointsFix = false
    }

    fun close() {
        if (--count == 0) VelocityTrackerAddPointsFix = true
    }
}
