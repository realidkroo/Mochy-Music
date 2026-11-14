// PillWindow.kt

package com.mochyy.mochymusic.floating

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mochyy.mochymusic.R
import kotlin.math.hypot
import kotlin.math.roundToInt

// a drop this close to the cross hides the pill
private val CloseReach = 72.dp
private val CloseSize = 56.dp
private val CloseFromBottom = 56.dp

// how long the pill takes to make room when the open controls would run off the screen
private const val MakeRoomMs = 180L

// what the window tells the service about
internal interface PillWindowListener {
    fun onMoved(x: Int, y: Int)
    fun onDismissed()
    fun onTouched()
    fun onTouchedOutside()
}

// the overlay window the pill lives in: its own lifecycle since no activity owns it, dragged by its raw touch so it never fights its own movement
internal class PillWindow(
    private val context: Context,
    savedX: Int?,
    savedY: Int?,
    private val listener: PillWindowListener,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val owner = OverlayOwner()
    private val root = DragFrame()
    private val compose = ComposeView(context)
    private var expandedWindow = false
    private var makingRoom: ValueAnimator? = null
    private var closeTarget: CloseTarget? = null

    val metrics = PillMetrics.forScreen((screenSize().x / density).dp)

    private val params = WindowManager.LayoutParams(
        px(metrics.collapsedWidth),
        px(metrics.collapsedHeight),
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // first time: the upper right of the screen, below the status bar
        val screen = screenSize()
        x = savedX ?: (screen.x - width + px(PillMetrics.Margin) - px(8.dp))
        y = savedY ?: (screen.y / 6)
        title = "Floating lyrics"
    }

    // false when the system refused the window, the permission was taken back most likely
    fun show(content: @Composable () -> Unit): Boolean {
        clampInto(params)
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        compose.setContent(content)
        root.addView(compose, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        owner.start()
        return runCatching { windowManager.addView(root, params) }
            .onFailure { owner.destroy() }
            .isSuccess
    }

    fun remove() {
        makingRoom?.cancel()
        closeTarget?.remove()
        closeTarget = null
        owner.destroy()
        runCatching { windowManager.removeView(root) }
    }

    // the open controls need a bigger window; it only grows right and down, and slides back in when that would leave the screen
    fun setExpanded(expanded: Boolean) {
        if (expanded == expandedWindow || !root.isAttachedToWindow) return
        expandedWindow = expanded
        params.width = px(if (expanded) metrics.expandedWidth else metrics.collapsedWidth)
        params.height = px(if (expanded) metrics.expandedHeight else metrics.collapsedHeight)
        val fromX = params.x
        val fromY = params.y
        clampInto(params)
        val toX = params.x
        val toY = params.y
        if (toX == fromX && toY == fromY) {
            update()
            return
        }
        params.x = fromX
        params.y = fromY
        update()
        makingRoom?.cancel()
        makingRoom = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MakeRoomMs
            addUpdateListener {
                val f = it.animatedValue as Float
                params.x = (fromX + (toX - fromX) * f).roundToInt()
                params.y = (fromY + (toY - fromY) * f).roundToInt()
                update()
            }
            start()
        }
    }

    private fun update() {
        if (root.isAttachedToWindow) runCatching { windowManager.updateViewLayout(root, params) }
    }

    // keeps the pill itself on screen and clear of the status bar; its see-through margin may hang off the edge
    private fun clampInto(lp: WindowManager.LayoutParams) {
        val screen = screenSize()
        val margin = px(PillMetrics.Margin)
        val top = topInset()
        lp.x = lp.x.coerceIn(-margin, (screen.x - lp.width + margin).coerceAtLeast(-margin))
        lp.y = lp.y.coerceIn(top - margin, (screen.y - lp.height + margin).coerceAtLeast(top - margin))
    }

    private fun screenSize(): Point = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = windowManager.currentWindowMetrics.bounds
        Point(bounds.width(), bounds.height())
    } else {
        Point().also {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(it)
        }
    }

    // the status bar's height; an overlay sits under it, so the pill is kept below it
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun topInset(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
            return insets.top
        }
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else px(24.dp)
    }

    private fun px(value: Dp): Int = (value.value * density).roundToInt()

    // takes a drag before compose sees it, from the raw screen position, so moving the window under the finger doesn't feed back into the drag
    private inner class DragFrame : FrameLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var fromX = 0
        private var fromY = 0
        private var dragging = false

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_OUTSIDE -> {
                    listener.onTouchedOutside()
                    return false
                }
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    fromX = params.x
                    fromY = params.y
                    dragging = false
                    makingRoom?.cancel()
                    listener.onTouched()
                }
                MotionEvent.ACTION_MOVE -> if (!dragging && hypot(event.rawX - downX, event.rawY - downY) > slop) {
                    dragging = true
                    // the cover's tap and hold are called off once it's a drag
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    closeTarget = CloseTarget().also { it.show() }
                }
            }
            if (!dragging) return super.dispatchTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    params.x = fromX + (event.rawX - downX).roundToInt()
                    params.y = fromY + (event.rawY - downY).roundToInt()
                    update()
                    closeTarget?.hover(event.rawX, event.rawY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    val dropped = event.actionMasked == MotionEvent.ACTION_UP && closeTarget?.hovering == true
                    closeTarget?.remove()
                    closeTarget = null
                    if (dropped) {
                        listener.onDismissed()
                    } else {
                        clampInto(params)
                        update()
                        listener.onMoved(params.x, params.y)
                    }
                }
            }
            return true
        }

        // a turned phone has a different screen, so the pill is brought back onto it
        override fun onConfigurationChanged(newConfig: Configuration?) {
            super.onConfigurationChanged(newConfig)
            clampInto(params)
            update()
        }
    }

    // the cross at the bottom of the screen that a dragged pill can be dropped on to hide it
    private inner class CloseTarget {
        private val size = px(CloseSize)
        private val view = ImageView(context).apply {
            setImageResource(R.drawable.close)
            setColorFilter(android.graphics.Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xE6202124.toInt())
            }
            alpha = 0f
            contentDescription = context.getString(R.string.floating_lyrics_hide)
        }
        private val lp = WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = px(CloseFromBottom)
        }
        var hovering = false
            private set

        fun show() {
            if (runCatching { windowManager.addView(view, lp) }.isFailure) return
            view.animate().alpha(1f).setDuration(150L).start()
        }

        fun hover(rawX: Float, rawY: Float) {
            if (!view.isAttachedToWindow) return
            val at = IntArray(2)
            view.getLocationOnScreen(at)
            val near = hypot(rawX - (at[0] + size / 2f), rawY - (at[1] + size / 2f)) < px(CloseReach)
            if (near == hovering) return
            hovering = near
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val scale = if (near) 1.2f else 1f
            view.animate().scaleX(scale).scaleY(scale).setDuration(120L).start()
        }

        fun remove() {
            if (!view.isAttachedToWindow) return
            view.animate().cancel()
            runCatching { windowManager.removeView(view) }
        }
    }
}

// overlays have their own window type from android 8; before that the phone type stood in for it
private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
} else {
    @Suppress("DEPRECATION")
    WindowManager.LayoutParams.TYPE_PHONE
}

// the lifecycle and saved state compose expects from a window, which an overlay has to bring itself
private class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        if (registry.currentState != Lifecycle.State.INITIALIZED) return
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (registry.currentState == Lifecycle.State.INITIALIZED || registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

