package com.mochyy.mochymusic.ui.utils

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

// a phone on its side is always wider than this, a phone held upright never is, and every tablet is
private const val WideMinWidthDp = 600

// the regular layout stays for a phone held upright; on its side or on a tablet the app gets the wide one
@Composable
@ReadOnlyComposable
fun isWideLayout(): Boolean = LocalConfiguration.current.screenWidthDp >= WideMinWidthDp

// the window is wider than it is tall, so the player can put its cover beside the controls
@Composable
@ReadOnlyComposable
fun isLandscapeWindow(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp > configuration.screenHeightDp
}

// a tv, watched from across the room: a square cover stretched over the whole screen there is one huge blurry crop
@Composable
@ReadOnlyComposable
fun isTvLayout(): Boolean =
    (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

// a phone or tablet on its side: pages split into a left pane and a right one, sheets float as cards; the tv keeps its own
@Composable
@ReadOnlyComposable
fun isLandscapeLayout(): Boolean = isWideLayout() && isLandscapeWindow() && !isTvLayout()

// which way the window may turn: upright unless landscape is unlocked in Experimental, with any holds on top, the newest winning
object WindowTurning {
    private class Hold(val orientation: Int)

    private var base = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    private val holds = mutableListOf<Hold>()

    // Experimental > Unlock landscape; off keeps the whole app upright whichever way the phone is held
    fun setUnlocked(activity: Activity, unlocked: Boolean) {
        base = if (unlocked) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        apply(activity)
    }

    internal fun hold(activity: Activity, orientation: Int): Any {
        val hold = Hold(orientation)
        holds += hold
        apply(activity)
        return hold
    }

    internal fun release(activity: Activity, token: Any) {
        holds.remove(token)
        apply(activity)
    }

    private fun apply(activity: Activity) {
        activity.requestedOrientation = holds.lastOrNull()?.orientation ?: base
    }
}

// holds the window one way round while active (the recap upright, the video sideways), then hands it back to the setting
@Composable
fun HoldOrientation(orientation: Int, active: Boolean) {
    val activity = LocalActivity.current ?: return
    // a tv never turns, and asking it to only confuses its launcher
    if (isTvLayout()) return
    DisposableEffect(activity, orientation, active) {
        if (!active) return@DisposableEffect onDispose { }
        val token = WindowTurning.hold(activity, orientation)
        onDispose { WindowTurning.release(activity, token) }
    }
}
