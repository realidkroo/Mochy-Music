package com.mochyy.mochymusic.ui.player.styles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.util.VelocityTrackerAddPointsFix
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.PopupSheetHost
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.player.menu.LocalMenuSheetFloating

// compose reads a fling's speed off the raw screen touches, which don't turn with a sideways frame, so its lists flung the wrong way or not at all; the older way reads the turned touches, used while anything turned is up
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

// the player's menus and sheets over a style turned sideways: turned with it, each a floating card the right way up, the way the full video mode opens its own
@Composable
fun TurnedSheets(turned: Boolean, content: @Composable () -> Unit) {
    if (!turned) {
        content()
        return
    }
    TurnedFlingFix()
    // its own sheet host inside the turned frame, so a sheet one of these opens turns too
    val sheets = remember { PopupSheetState() }
    Box(modifier = Modifier.fillMaxSize().quarterTurn(true)) {
        PopupSheetHost(states = listOf(sheets)) {
            CompositionLocalProvider(
                LocalMenuState provides sheets,
                LocalMenuSheetFloating provides true,
            ) {
                content()
            }
        }
    }
}
