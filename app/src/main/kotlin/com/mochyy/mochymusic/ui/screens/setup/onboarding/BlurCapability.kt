// decides whether onboarding suggests turning blur off, by timing real frames on the hello page

package com.mochyy.mochymusic.ui.screens.setup.onboarding

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.mochyy.mochymusic.R
import java.util.Locale

object BlurCapability {

    enum class Verdict { Fine, Laggy, NotApplicable }

    /** frame timings measured on the hello page */
    data class Probe(
        val frames: Int,
        val slowFrames: Int,
        val averageFrameMs: Float,
        val refreshHz: Float,
    ) {
        val slowRatio: Float get() = if (frames == 0) 0f else slowFrames.toFloat() / frames
    }

    data class Result(val verdict: Verdict, val summary: String)

    /** chips confirmed to stutter under the app's glass */
    private val knownLaggy = listOf(
        "mt6781", // helio g96, redmi note 11 pro 4g, the reference laggy phone
    )

    /** chips confirmed fine even when the frame timing is borderline */
    private val knownFine = listOf(
        "mt6789", // helio g99, galaxy a24
        "t606", "ums9230", // unisoc t606, galaxy a03
        "sc9863", // unisoc sc9863a, galaxy a03 core
    )

    fun socName(): String {
        val parts = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Build.SOC_MANUFACTURER)
                add(Build.SOC_MODEL)
            }
            add(Build.HARDWARE)
            add(Build.BOARD)
        }
        return parts.filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ").lowercase(Locale.ROOT)
    }

    fun refreshRate(context: Context): Float {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        }
        return display?.refreshRate?.takeIf { it >= 30f } ?: 60f
    }

    fun decide(context: Context, probe: Probe?): Result {
        // below Android 12 the app draws its glass without a blur at all, so there's nothing to turn off
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return Result(Verdict.NotApplicable, context.getString(R.string.onb_blur_needs_android_12))
        }
        val soc = socName()
        val socLabel = displaySoc()
        val hz = probe?.refreshHz ?: refreshRate(context)
        val timing = probe?.let { context.getString(R.string.onb_blur_slow_frames, (it.slowRatio * 100).toInt(), hz.toInt()) } ?: "${hz.toInt()} Hz"

        val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
        if (lowRam) return Result(Verdict.Laggy, context.getString(R.string.onb_blur_low_memory, socLabel))
        if (knownLaggy.any { it in soc }) return Result(Verdict.Laggy, "$socLabel · $timing")

        val probeSaysLaggy = probe != null && probe.frames >= 20 && (
            probe.slowRatio > SLOW_RATIO_LIMIT ||
                probe.averageFrameMs > (1000f / probe.refreshHz) * AVERAGE_LIMIT
            )
        if (knownFine.any { it in soc }) {
            // only a really bad run overrides a phone known to cope
            val terrible = probe != null && probe.slowRatio > 0.5f
            return Result(if (terrible) Verdict.Laggy else Verdict.Fine, "$socLabel · $timing")
        }
        return Result(if (probeSaysLaggy) Verdict.Laggy else Verdict.Fine, "$socLabel · $timing")
    }

    private fun displaySoc(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val model = Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            val maker = Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
            if (model != null) return listOfNotNull(maker, model).joinToString(" ")
        }
        return Build.HARDWARE.ifBlank { Build.MODEL }
    }

    /** more than this share of late frames (over 1.5 refresh intervals) reads as stutter */
    private const val SLOW_RATIO_LIMIT = 0.2f

    /** or an average frame taking this much longer than one refresh interval */
    private const val AVERAGE_LIMIT = 1.3f
}

/** collects frame intervals, fed from a withFrameNanos loop on the hello page */
internal class FrameProbeRecorder(private val refreshHz: Float) {
    private var last = 0L
    private var started = 0L
    private var frames = 0
    private var slow = 0
    private var totalNanos = 0L

    fun onFrame(nanos: Long) {
        if (started == 0L) started = nanos
        // the first ~400 ms are composition, image decodes and shader compiles: not representative
        if (nanos - started < WARMUP_NANOS) {
            last = nanos
            return
        }
        // an interval counts only when both its frames are past the warm-up
        if (last != 0L && last - started >= WARMUP_NANOS) {
            val interval = nanos - last
            frames++
            totalNanos += interval
            if (interval > (1_000_000_000f / refreshHz) * 1.5f) slow++
        }
        last = nanos
    }

    fun result(): BlurCapability.Probe = BlurCapability.Probe(
        frames = frames,
        slowFrames = slow,
        averageFrameMs = if (frames == 0) 0f else totalNanos / frames / 1_000_000f,
        refreshHz = refreshHz,
    )

    private companion object {
        const val WARMUP_NANOS = 400_000_000L
    }
}
