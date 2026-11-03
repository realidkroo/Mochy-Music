package com.mochyy.mochymusic.ui.utils

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import com.mochyy.mochymusic.constants.EnableHighRefreshRateKey
import com.mochyy.mochymusic.utils.rememberPreference
import java.util.WeakHashMap

// asks android 15+ for the top refresh rate while active() holds; there a screen moving with no finger on it falls to 60hz (docs/notes/performance.md)
@Composable
fun HighRefreshWhile(active: () -> Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    val (enabled) = rememberPreference(EnableHighRefreshRateKey, defaultValue = true)
    if (!enabled) return
    val view = LocalView.current
    val currentActive by rememberUpdatedState(active)
    LaunchedEffect(view) {
        val voter = HighRateVoter.of(view) ?: return@LaunchedEffect
        var holding = false
        try {
            snapshotFlow { currentActive() }.collect { on ->
                if (on == holding) return@collect
                holding = on
                if (on) voter.acquire() else voter.release()
            }
        } finally {
            if (holding) voter.release()
        }
    }
}

// an invisible pixel that votes for the high rate each frame it's redrawn, the way compose votes for a layout that moved
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private class HighRateVoter(root: ViewGroup) {
    private val pixel = View(root.context).apply {
        // the screen's own top rate: the "high" category is only 90 on some 120 hz phones (redmi note 13 pro), and 90 on a 120 hz panel judders
        requestedFrameRate = root.display?.supportedModes?.maxOfOrNull { it.refreshRate }
            ?.takeIf { it > 0f } ?: View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var requests = 0
    private var ticking = false

    // a view only votes when it's drawn again, so it's dirtied on every frame while someone wants it
    private val tick = object : Runnable {
        override fun run() {
            if (requests == 0) {
                ticking = false
                return
            }
            pixel.invalidate()
            pixel.postOnAnimation(this)
        }
    }

    init {
        root.addView(pixel, ViewGroup.LayoutParams(1, 1))
    }

    fun acquire() {
        requests++
        if (!ticking) {
            ticking = true
            pixel.postOnAnimation(tick)
        }
    }

    fun release() {
        requests = (requests - 1).coerceAtLeast(0)
    }

    companion object {
        // one pixel per window, shared by everything that asks
        private val voters = WeakHashMap<View, HighRateVoter>()

        fun of(view: View): HighRateVoter? {
            val content = view.rootView.findViewById<ViewGroup>(android.R.id.content) ?: return null
            return voters.getOrPut(content) { HighRateVoter(content) }
        }
    }
}
