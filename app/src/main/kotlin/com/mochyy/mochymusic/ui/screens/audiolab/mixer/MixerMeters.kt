// the mixer's meters: read from the engine once a frame while a mixer page is on screen, drawn straight from these arrays so nothing recomposes

package com.mochyy.mochymusic.ui.screens.audiolab.mixer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mochyy.mochymusic.eq.mixer.MixerNative
import kotlinx.coroutines.isActive
import kotlin.math.log10
import kotlin.math.max

/** one bus's meter as drawn: the level falling at a steady rate, and the highest peak held for a moment */
class MixerMeterState {
    val levelL = FloatArray(Buses) { Floor }
    val levelR = FloatArray(Buses) { Floor }
    val holdL = FloatArray(Buses) { Floor }
    val holdR = FloatArray(Buses) { Floor }
    private val holdLeftL = FloatArray(Buses)
    private val holdLeftR = FloatArray(Buses)
    private val raw = FloatArray(MixerNative.PeakCount)

    /** momentary, short-term, integrated, range, true peak */
    val loudness = FloatArray(5) { None }
    var clipped = false
        private set

    // bumped every frame the meters moved; draw code reads it so only the draw phase runs again
    val tick = mutableLongStateOf(0L)

    // the short-term loudness of the last minute, newest last, for the loudness card's line
    val history = FloatArray(HistoryLength) { None }

    private var sinceLoudness = 0f
    private var sinceHistory = 0f

    fun step(seconds: Float) {
        MixerNative.peaks(raw)
        for (bus in 0 until Buses) {
            levelL[bus] = fall(levelL[bus], toDb(raw[bus * 2]), seconds)
            levelR[bus] = fall(levelR[bus], toDb(raw[bus * 2 + 1]), seconds)
            holdL[bus] = hold(holdL, holdLeftL, bus, levelL[bus], seconds)
            holdR[bus] = hold(holdR, holdLeftR, bus, levelR[bus], seconds)
        }
        if (raw[Buses * 2] > 0.5f) clipped = true
        sinceLoudness += seconds
        if (sinceLoudness >= 0.1f) {
            sinceLoudness = 0f
            MixerNative.loudness(loudness)
        }
        sinceHistory += seconds
        if (sinceHistory >= HistoryStep) {
            sinceHistory = 0f
            System.arraycopy(history, 1, history, 0, HistoryLength - 1)
            history[HistoryLength - 1] = loudness[1]
        }
        tick.longValue++
    }

    fun clearClip() {
        clipped = false
    }

    fun resetLoudness() {
        MixerNative.resetLoudness()
        loudness.fill(None)
        history.fill(None)
    }

    private fun fall(shown: Float, peak: Float, seconds: Float) = max(peak, max(Floor, shown - FallDbPerSecond * seconds))

    private fun hold(holds: FloatArray, left: FloatArray, bus: Int, level: Float, seconds: Float): Float {
        if (level >= holds[bus]) {
            left[bus] = HoldSeconds
            return level
        }
        left[bus] -= seconds
        return if (left[bus] > 0f) holds[bus] else max(level, holds[bus] - FallDbPerSecond * seconds)
    }

    private fun toDb(linear: Float) = if (linear > 1e-6f) max(Floor, 20f * log10(linear)) else Floor

    companion object {
        const val Buses = MixerNative.MaxBuses + 1
        const val Floor = -60f
        const val None = -200f
        const val HistoryLength = 120
        const val HistoryStep = 0.5f
        private const val FallDbPerSecond = 24f
        private const val HoldSeconds = 1.5f
    }
}

/** the meters, running only while [active] and the page is in front; the engine works loudness out only while someone reads it */
@Composable
fun rememberMixerMeters(active: Boolean): MixerMeterState {
    val state = remember { MixerMeterState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, lifecycle) {
        if (!active || !MixerNative.loaded) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            MixerNative.metering(true)
            try {
                var last = 0L
                while (isActive) {
                    withFrameNanos { now ->
                        val seconds = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.1f)
                        last = now
                        state.step(seconds)
                    }
                }
            } finally {
                MixerNative.metering(false)
            }
        }
    }
    return state
}
