// PillFlight.kt

package com.mochyy.mochymusic.floating

import android.view.Choreographer
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

// how stiff and how bouncy a flight is; under 1 it overshoots a little and settles back
internal class FlightSpring(val stiffness: Float, dampingRatio: Float) {
    val damping = 2f * dampingRatio * sqrt(stiffness)
}

// a little spring that carries the cover across the screen frame by frame, picking up a fling's speed so a throw keeps going smoothly
internal class PillFlight(private val onMove: (x: Float, y: Float) -> Unit) : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var goalX = 0f
    private var goalY = 0f
    private var spring = FlightSpring(500f, 1f)
    private var lastFrame = 0L
    private var onLanded: (() -> Unit)? = null

    // the screen's edge just past the goal: the cover reaches the goal, bumps the edge and bounces back off it
    private var wall = Float.NaN
    private var wallSide = 0f
    private var onHit: (() -> Unit)? = null

    var running = false
        private set

    // a flight already under way keeps its speed and only turns toward the new goal
    fun flyTo(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        spring: FlightSpring,
        velocityX: Float = 0f,
        velocityY: Float = 0f,
        wallX: Float = Float.NaN,
        onHit: (() -> Unit)? = null,
        onLanded: (() -> Unit)? = null,
    ) {
        if (!running) {
            x = fromX
            y = fromY
            vx = velocityX
            vy = velocityY
            lastFrame = 0L
            running = true
            choreographer.postFrameCallback(this)
        }
        goalX = toX
        goalY = toY
        this.spring = spring
        this.onLanded = onLanded
        this.onHit = onHit
        // only a cover coming from inside the screen bumps the edge; one starting past it just slides in
        wallSide = if (wallX.isNaN()) 0f else Math.signum(wallX - toX)
        wall = if (wallSide != 0f && (x - toX) * wallSide < 0f) wallX else Float.NaN
    }

    fun retarget(toX: Float, toY: Float) {
        goalX = toX
        goalY = toY
    }

    fun stop() {
        if (!running) return
        running = false
        onLanded = null
        onHit = null
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastFrame == 0L) 1f / 120f else ((frameTimeNanos - lastFrame) / 1_000_000_000f).coerceIn(0f, 1f / 30f)
        lastFrame = frameTimeNanos
        // small steps keep a stiff spring steady even when a frame comes late
        var left = dt
        while (left > 0f) {
            val h = min(left, StepSec)
            vx += (-spring.stiffness * (x - goalX) - spring.damping * vx) * h
            vy += (-spring.stiffness * (y - goalY) - spring.damping * vy) * h
            x += vx * h
            y += vy * h
            if (!wall.isNaN() && (x - wall) * wallSide > 0f) {
                x = wall
                if (vx * wallSide > 0f) vx = -vx * Bounce
            }
            left -= h
        }
        if (wallSide != 0f && (x - goalX) * wallSide >= 0f) hit()
        val landed = abs(goalX - x) < RestPx && abs(goalY - y) < RestPx && abs(vx) < RestSpeed && abs(vy) < RestSpeed
        if (!landed) {
            onMove(x, y)
            if (running) choreographer.postFrameCallback(this)
            return
        }
        running = false
        onMove(goalX, goalY)
        hit()
        val done = onLanded
        onLanded = null
        done?.invoke()
    }

    // the moment it reaches its edge, once per flight
    private fun hit() {
        val reached = onHit ?: return
        onHit = null
        reached()
    }

    private companion object {
        const val StepSec = 1f / 240f
        const val RestPx = 0.5f
        const val RestSpeed = 24f

        // how much speed the cover keeps when it bounces off the edge
        const val Bounce = 0.32f
    }
}
