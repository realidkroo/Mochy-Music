package com.mochyy.mochymusic.ui.utils

import android.content.res.Configuration
import androidx.compose.runtime.Composable
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
