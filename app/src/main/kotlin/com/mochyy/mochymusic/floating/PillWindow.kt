// PillWindow.kt

package com.mochyy.mochymusic.floating

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.os.Build
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

// the cover rests this far from the side of the screen, and keeps this clear of the status and navigation bars
private val EdgeGap = 8.dp
private val BarGap = 6.dp

// how far past its resting spot a landing cover may swing before it bumps off the edge
private val EdgeGive = 5.dp

// the platform's own flag for a window that jumps straight to its new spot, set by hand before android 14 made it public
private const val PrivateFlagNoMoveAnimation = 0x00000040

// a fling carries the drop on by this much of a second, so a flick throws the pill to the far side
private const val FlingCarrySec = 0.14f
private const val MaxFlingDpPerSec = 1_800f

private val LandSpring = FlightSpring(stiffness = 420f, dampingRatio = 0.74f)
private val RoomSpring = FlightSpring(stiffness = 700f, dampingRatio = 1f)
private val MagnetSpring = FlightSpring(stiffness = 900f, dampingRatio = 0.72f)
private val CatchUpSpring = FlightSpring(stiffness = 1_400f, dampingRatio = 1f)
private val VanishSpring = FlightSpring(stiffness = 1_100f, dampingRatio = 1f)
private const val VanishMs = 220L

// the window goes this long after a drop even if an animation never reports back, so the pills' shade can't stay on screen
private const val VanishGiveUpMs = 900L

// what the window tells the service about
internal interface PillWindowListener {
    // end is true when the cover now sits on the right edge
    fun onDocked(end: Boolean)
    fun onDragStarted()

    // the cover reached its edge after a drop: the bubble pops back out
    fun onSettling()
    fun onMoved(x: Int, y: Int)
    fun onDropped(drop: PillDrop)
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
    private val flight = PillFlight { x, y -> moveCover(x, y) }

    val metrics = PillMetrics.forScreen(
        (screenSize().x / density).dp,
        widthScale = com.mochyy.mochymusic.modules.skin.ActiveSkin.floating?.floating?.widthScale ?: 1f,
    )
    private val margin = px(PillMetrics.Margin)
    private val cover = px(PillMetrics.Cover)

    // built once with the window, so picking the pill up never has to load a font or open a second window
    private val targets = PillDropTargets(context, cover)

    // the cover's top left in window coordinates; the window is always placed around it, whatever size it is
    private var coverX = 0f
    private var coverY = 0f
    private var dockEnd = true
    private var frameWidth = px(metrics.width(PillShape.PILL))
    private var frameHeight = px(metrics.height(PillShape.PILL, BaseSlots))
    private var slots = BaseSlots

    // the controls are open, so the bar under them takes slides for seeking
    private var open = false

    // the cover is flying home after a drop and the bubble is still tucked in, waiting for it to land
    private var landing = false

    // the flying cover has reached its edge, so the bubble can pop out as soon as the pill faces the right way
    private var arrived = false

    // the side a dropped cover is headed for, turned to once the bubble has tucked in and the window is just the cover
    private var turnTo: Boolean? = null

    // dropped on close or don't show again: touches are let be and the window is on its way out
    private var leaving = false
    private var vanishingTo: PillDrop? = null
    private val vanishAnyway = Runnable { finishVanish() }
    private var removed = false

    // while it's carried or flying the window covers the whole screen and the pill moves inside it by drawing;
    // moving the window itself every frame went through the window manager each time and stuttered
    private var roaming = false

    private val params = WindowManager.LayoutParams(
        frameWidth,
        frameHeight,
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
        // window coordinates are the screen's, navigation bar included, so the full-screen window lines up with the resting one
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurDp = com.mochyy.mochymusic.modules.skin.ActiveSkin.floating?.floating?.blur ?: 0f
            if (blurDp > 0.5f && windowManager.isCrossWindowBlurEnabled) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = (blurDp * density).roundToInt().coerceIn(0, 80)
            }
        }
        title = "Floating lyrics"
        noMoveAnimation()
    }

    init {
        val screen = screenSize()
        // first time: the upper right of the screen, below the status bar
        dockEnd = savedX?.let { it + frameWidth / 2 > screen.x / 2 } ?: true
        coverX = restX(dockEnd)
        coverY = clampY((savedY ?: (screen.y / 6)) + margin.toFloat(), frameHeight)
        place(apply = false)
    }

    // false when the system refused the window, the permission was taken back most likely
    fun show(content: @Composable () -> Unit): Boolean {
        listener.onDocked(dockEnd)
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        compose.setContent(content)
        // the pills sit under the pill, so a cover dropped onto one shrinks away on top of it
        root.addView(targets.view, targets.layoutParams(0))
        root.addView(compose, FrameLayout.LayoutParams(frameWidth, frameHeight, Gravity.TOP or Gravity.START))
        owner.start()
        return runCatching { windowManager.addView(root, params) }
            .onFailure { owner.destroy() }
            .isSuccess
    }

    fun remove() {
        if (removed) return
        removed = true
        leaving = true
        landing = false
        vanishingTo = null
        root.removeCallbacks(vanishAnyway)
        flight.stop()
        targets.hideNow()
        root.release()
        compose.animate().cancel()
        owner.destroy()
        // hidden first and taken down after the next frame, so what's left on screen is an empty window, never the pills' shade
        root.visibility = View.INVISIBLE
        Choreographer.getInstance().postFrameCallback {
            root.post { runCatching { windowManager.removeView(root) } }
        }
    }

    // grows the window to hold both what's on screen and what's coming, away from the cover so nothing moves
    fun reserveFrame(shape: PillShape, slots: Int) {
        this.slots = slots
        open = shape == PillShape.OPEN
        resize(maxOf(frameWidth, px(metrics.width(shape))), maxOf(frameHeight, px(metrics.height(shape, slots))))
        if (!root.dragging && !flight.running && !leaving) makeRoom()
    }

    // fits the window to what's on screen once it has settled
    fun fitFrame(shape: PillShape, slots: Int) {
        this.slots = slots
        open = shape == PillShape.OPEN
        resize(px(metrics.width(shape)), px(metrics.height(shape, slots)))
        if (shape == PillShape.COVER) {
            turnIfReady()
            popWhenReady()
        }
    }

    private fun resize(width: Int, height: Int) {
        if (width == frameWidth && height == frameHeight) return
        frameWidth = width
        frameHeight = height
        place()
    }

    private fun moveCover(x: Float, y: Float) {
        coverX = x
        coverY = y
        place()
    }

    // the pill's frame around the cover: it hangs to the right of it when docked left, to the left when docked right
    private fun place(apply: Boolean = true) {
        val left = if (dockEnd) coverX + cover + margin - frameWidth else coverX - margin
        val top = coverY - margin
        val lp = compose.layoutParams
        if (lp != null && (lp.width != frameWidth || lp.height != frameHeight)) {
            lp.width = frameWidth
            lp.height = frameHeight
            compose.layoutParams = lp
        }
        if (roaming) {
            // inside the full-screen window only the drawing moves, which never waits on the window manager
            compose.translationX = left
            compose.translationY = top
            return
        }
        compose.translationX = 0f
        compose.translationY = 0f
        params.width = frameWidth
        params.height = frameHeight
        params.x = left.roundToInt()
        params.y = top.roundToInt()
        if (apply && root.isAttachedToWindow) runCatching { windowManager.updateViewLayout(root, params) }
    }

    // the window spreads over the whole screen with the pill drawn where it was, so nothing on screen moves as it does
    private fun roam() {
        if (roaming) return
        roaming = true
        val screen = screenSize()
        params.x = 0
        params.y = 0
        params.width = screen.x
        params.height = screen.y
        place()
        if (root.isAttachedToWindow) runCatching { windowManager.updateViewLayout(root, params) }
    }

    // back to a window just around the pill, once it has come to rest
    private fun land() {
        if (!roaming) return
        roaming = false
        targets.hideNow()
        place()
    }

    // the window only grows away from the cover; when that would run off the bottom the cover slides up first
    private fun makeRoom() {
        val toY = clampY(coverY, frameHeight)
        if (toY == coverY) return
        roam()
        flight.flyTo(coverX, coverY, coverX, toY, RoomSpring) {
            land()
            save()
        }
    }

    // a touch this close to the open bar is a seek, never a drag of the whole pill
    private fun onSeekBar(y: Float): Boolean =
        open && abs(y - compose.translationY - px(PillMetrics.barY(slots.toFloat()))) <= px(PillMetrics.SeekReach)

    // while the window covers the screen, only a touch on the pill itself counts
    private fun onPill(x: Float, y: Float): Boolean {
        val left = compose.translationX
        val top = compose.translationY
        return x >= left && x <= left + frameWidth && y >= top && y <= top + frameHeight
    }

    private fun isJustCover(): Boolean =
        frameWidth == px(metrics.width(PillShape.COVER)) && frameHeight == px(metrics.height(PillShape.COVER, slots))

    // let go: it flies at once toward the side it was headed for, carrying the fling's speed, bumps the edge, and the bubble pops out
    private fun settle(velocityX: Float, velocityY: Float) {
        val end = coverX + cover / 2f + velocityX * FlingCarrySec > screenSize().x / 2f
        turnTo = end
        turnIfReady()
        val goalY = clampY(coverY + velocityY * FlingCarrySec, px(metrics.height(PillShape.PILL, slots)))
        val goalX = restX(end)
        landing = true
        arrived = false
        flight.flyTo(
            coverX, coverY, goalX, goalY, LandSpring, velocityX, velocityY,
            wallX = goalX + if (end) px(EdgeGive) else -px(EdgeGive),
            onHit = {
                arrived = true
                popWhenReady()
            },
            onLanded = {
                arrived = true
                turnNow()
                popWhenReady()
                save()
                land()
            },
        )
    }

    // turning sides mirrors the pill, so it waits until the bubble has tucked in and the window is just the cover, where it moves nothing
    private fun turnIfReady() {
        val end = turnTo ?: return
        if (!isJustCover()) return
        turnTo = null
        if (end == dockEnd) return
        dockEnd = end
        listener.onDocked(end)
        place()
    }

    // landed before the bubble finished tucking in, which it has had the whole flight to do, so the window shrinks to the cover and turns
    private fun turnNow() {
        if (turnTo == null) return
        resize(px(metrics.width(PillShape.COVER)), px(metrics.height(PillShape.COVER, slots)))
        turnIfReady()
    }

    // the bubble pops back out once the cover is at its edge and the pill faces the right way
    private fun popWhenReady() {
        if (!landing || !arrived || turnTo != null) return
        landing = false
        arrived = false
        listener.onSettling()
    }

    // dropped on a pill: the cover is pulled into it and shrinks away, the pills drop after it, and only then does the window go
    private fun vanish(drop: PillDrop) {
        leaving = true
        vanishingTo = drop
        targets.swallow(drop)
        targets.centerOf(drop)?.let { center ->
            flight.flyTo(coverX, coverY, center.x - cover / 2f, center.y - cover / 2f, VanishSpring)
        }
        compose.pivotX = if (dockEnd) frameWidth - margin - cover / 2f else margin + cover / 2f
        compose.pivotY = margin + cover / 2f
        compose.animate()
            .scaleX(0.2f)
            .scaleY(0.2f)
            .alpha(0f)
            .setDuration(VanishMs)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { targets.hide(::finishVanish) }
            .start()
        root.postDelayed(vanishAnyway, VanishGiveUpMs)
    }

    // once, whichever comes first: the pills have dropped away, or they never said so in time
    private fun finishVanish() {
        val drop = vanishingTo ?: return
        vanishingTo = null
        root.removeCallbacks(vanishAnyway)
        targets.hideNow()
        listener.onDropped(drop)
    }

    // the position is kept as where the resting pill's window sat, so an older saved spot still picks the right side
    private fun save() {
        val x = if (dockEnd) coverX + cover + margin - px(metrics.pillWidth) else coverX - margin
        listener.onMoved(x.roundToInt(), (coverY - margin).roundToInt())
    }

    private fun restX(end: Boolean): Float =
        if (end) (screenSize().x - px(EdgeGap) - cover).toFloat() else px(EdgeGap).toFloat()

    // keeps everything below the cover's top, the bubble and the bar included, between the status bar and the navigation bar
    private fun clampY(y: Float, height: Int): Float {
        val bars = systemBars()
        val top = (bars.top + px(BarGap)).toFloat()
        val bottom = (screenSize().y - bars.bottom - px(BarGap) - (height - margin * 2)).toFloat()
        return if (bottom < top) top else y.coerceIn(top, bottom)
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

    private class Bars(val top: Int, val bottom: Int)

    // the status bar and navigation bar heights; an overlay can sit under both, so the pill is kept between them
    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun systemBars(): Bars {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Bars(insets.top, insets.bottom)
        }
        val resources = context.resources
        val status = resources.getIdentifier("status_bar_height", "dimen", "android")
        val navigation = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return Bars(
            top = if (status > 0) resources.getDimensionPixelSize(status) else px(24.dp),
            bottom = if (navigation > 0) resources.getDimensionPixelSize(navigation) else 0,
        )
    }

    private fun px(value: Dp): Int = (value.value * density).roundToInt()

    // takes a drag before compose sees it, from the raw screen position, so moving the window under the finger doesn't feed back into the drag
    private inner class DragFrame : FrameLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val maxFling = MaxFlingDpPerSec * density
        private var tracker: VelocityTracker? = null
        private var downX = 0f
        private var downY = 0f
        private var fromX = 0f
        private var fromY = 0f
        private var magnet: PillDrop? = null
        private var seeking = false

        // this whole gesture missed the pill, so none of it may pick the pill up later
        private var ignoring = false

        var dragging = false
            private set

        // the lifted cover's soft shadow reaches past the pill's frame while it's carried, so it isn't cut at the frame's edge
        init {
            clipChildren = false
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_OUTSIDE -> {
                    listener.onTouchedOutside()
                    return false
                }
                MotionEvent.ACTION_DOWN -> {
                    onScreen(event)
                    // a touch beside the pill while it flies home, or anything once it's leaving, is let be
                    ignoring = leaving || (roaming && !dragging && !onPill(event.x, event.y))
                    if (ignoring) return false
                    downX = event.rawX
                    downY = event.rawY
                    seeking = onSeekBar(event.y)
                    dragging = false
                    magnet = null
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain()
                    listener.onTouched()
                }
                MotionEvent.ACTION_MOVE -> if (!ignoring && !dragging && !seeking && hypot(event.rawX - downX, event.rawY - downY) > slop) startDrag(event)
            }
            if (ignoring) return false
            onScreen(event)
            track(event)
            if (!dragging) {
                val handled = super.dispatchTouchEvent(event)
                val lifted = event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
                // a plain tap never leaves the window spread over the screen once nothing is flying
                if (lifted && !flight.running && !landing) land()
                return handled
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> drag(fromX + event.rawX - downX, fromY + event.rawY - downY)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drop(event.actionMasked == MotionEvent.ACTION_UP)
            }
            return true
        }

        // while it roams the pill is placed in screen pixels, so touches are too, even the ones sent before the window finished spreading
        private fun onScreen(event: MotionEvent) {
            if (roaming) event.offsetLocation(event.rawX - event.x, event.rawY - event.y)
        }

        // the window moves under the finger, so the speed is measured on the screen instead
        private fun track(event: MotionEvent) {
            val onScreen = MotionEvent.obtain(event)
            onScreen.setLocation(event.rawX, event.rawY)
            tracker?.addMovement(onScreen)
            onScreen.recycle()
        }

        private fun startDrag(event: MotionEvent) {
            dragging = true
            // caught mid-flight it's held right where it is, and a plain tap lets it land
            flight.stop()
            fromX = coverX
            fromY = coverY
            downX = event.rawX
            downY = event.rawY
            // the cover's tap and hold are called off once it's a drag
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            // picked up again before it landed: it stays tucked for the new drag, and the next drop picks the side again
            turnTo = null
            landing = false
            arrived = false
            listener.onDragStarted()
            roam()
            targets.show(systemBars().bottom)
        }

        // the cover follows the finger, except near a pill where it's pulled onto it, and it catches back up after leaving one
        private fun drag(freeX: Float, freeY: Float) {
            val over = targets.hover(freeX + cover / 2f, freeY + cover / 2f)
            when {
                over != null -> if (over != magnet) {
                    magnet = over
                    targets.restSpot(over)?.let { spot ->
                        flight.flyTo(coverX, coverY, spot.x, spot.y, MagnetSpring)
                    }
                }
                magnet != null -> {
                    magnet = null
                    flight.flyTo(coverX, coverY, freeX, freeY, CatchUpSpring)
                }
                flight.running -> flight.retarget(freeX, freeY)
                else -> moveCover(freeX, freeY)
            }
        }

        private fun drop(released: Boolean) {
            dragging = false
            val speed = tracker?.let {
                it.computeCurrentVelocity(1_000, maxFling)
                PointF(it.xVelocity, it.yVelocity)
            } ?: PointF()
            tracker?.recycle()
            tracker = null
            val drop = magnet.takeIf { released }
            magnet = null
            flight.stop()
            if (drop != null) {
                vanish(drop)
                return
            }
            targets.hide()
            settle(speed.x, speed.y)
        }

        fun release() {
            tracker?.recycle()
            tracker = null
        }

        // a turned phone has a different screen, so the pill goes back to its edge on it
        override fun onConfigurationChanged(newConfig: Configuration?) {
            super.onConfigurationChanged(newConfig)
            if (dragging || leaving) return
            flight.stop()
            arrived = true
            turnNow()
            popWhenReady()
            land()
            coverX = restX(dockEnd)
            coverY = clampY(coverY, frameHeight)
            place()
        }
    }
}

// overlays have their own window type from android 8; before that the phone type stood in for it
internal fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
} else {
    @Suppress("DEPRECATION")
    WindowManager.LayoutParams.TYPE_PHONE
}

// an overlay that changes size and place at once slides there on its own, so the pill flew up and back on every pick-up and landing
private fun WindowManager.LayoutParams.noMoveAnimation() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        setCanPlayMoveAnimation(false)
        return
    }
    runCatching {
        val flags = WindowManager.LayoutParams::class.java.getField("privateFlags")
        flags.setInt(this, flags.getInt(this) or PrivateFlagNoMoveAnimation)
    }
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
