// SincResampler.kt

package com.mochyy.mochymusic.playback.audio

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt

/** polyphase kaiser-windowed sinc resampler: flat to ~20 khz, 120 db down past the lower nyquist, float in and out */
class SincResampler private constructor(
    val inRate: Int,
    val outRate: Int,
    val channels: Int,
    private val table: Table,
) {
    /** the filter's taps per output sample, which is what decides its steepness */
    val taps: Int get() = table.taps
    /** where the passband ends, in hz */
    val passbandHz: Double get() = table.passbandHz
    /** the lower rate's nyquist, where the stopband starts */
    val stopbandHz: Double get() = minOf(inRate, outRate) / 2.0

    // each channel's recent input, oldest first; the filter reads back taps - 1 samples from the newest
    private var history = Array(channels) { FloatArray(InitialCapacity) }
    private var stored = 0
    // absolute index of history[0], and of the input sample the next output centres on
    private var base = 0L
    private var nextInput = 0L
    private var phase = 0

    init {
        reset()
    }

    /** forgets everything heard, for a seek or a flush: the next sample starts on a quiet history */
    fun reset() {
        stored = table.taps - 1
        for (channel in history) channel.fill(0f, 0, stored)
        base = -stored.toLong()
        nextInput = 0L
        phase = 0
    }

    /** how many output frames at most [frames] more input can give, for sizing buffers */
    fun maxOutputFrames(frames: Int): Int = ((frames.toLong() + 2) * table.up / table.down + 2).toInt()

    /** resamples [frames] of [input] (one array per channel) into [output] and says how many frames it wrote */
    fun process(input: Array<FloatArray>, frames: Int, output: Array<FloatArray>): Int {
        append(input, frames)
        val taps = table.taps
        val up = table.up
        val down = table.down
        val coefficients = table.coefficients
        val last = base + stored - 1
        var written = 0
        while (nextInput <= last) {
            val newest = (nextInput - base).toInt()
            val offset = phase * taps
            for (c in 0 until channels) {
                val x = history[c]
                var acc = 0f
                var j = 0
                while (j < taps) {
                    acc += coefficients[offset + j] * x[newest - j]
                    j++
                }
                output[c][written] = acc
            }
            written++
            phase += down
            nextInput += phase / up
            phase %= up
        }
        // only the last taps - 1 samples before the next output are still needed
        val keepFrom = (nextInput - (taps - 1) - base).toInt().coerceIn(0, stored)
        if (keepFrom > 0) {
            for (channel in history) System.arraycopy(channel, keepFrom, channel, 0, stored - keepFrom)
            stored -= keepFrom
            base += keepFrom
        }
        return written
    }

    private fun append(input: Array<FloatArray>, frames: Int) {
        if (stored + frames > history[0].size) {
            val size = maxOf(stored + frames, history[0].size * 2)
            history = Array(channels) { c -> history[c].copyOf(size) }
        }
        for (c in 0 until channels) System.arraycopy(input[c], 0, history[c], stored, frames)
        stored += frames
    }

    /** the filter for one pair of rates, shared by every player that needs it */
    private class Table(val up: Int, val down: Int, val taps: Int, val passbandHz: Double, val coefficients: FloatArray)

    companion object {
        const val StopbandDb = 120.0
        // taps for a straight 1:1 band; going down in rate scales it up so the slope stays as steep
        private const val BaseTaps = 192
        private const val InitialCapacity = 8192
        // designed on the playback thread as a song opens: a strange rate pair would stutter, so android's mixer takes those
        private const val MaxCoefficients = 200_000

        private val tables = ConcurrentHashMap<Long, Table>()

        fun create(inRate: Int, outRate: Int, channels: Int): SincResampler? {
            if (inRate <= 0 || outRate <= 0 || inRate == outRate || channels <= 0) return null
            val key = inRate.toLong() shl 32 or outRate.toLong()
            val table = tables[key] ?: design(inRate, outRate)?.also { tables[key] = it } ?: return null
            return SincResampler(inRate, outRate, channels, table)
        }

        private fun design(inRate: Int, outRate: Int): Table? {
            val gcd = gcd(inRate, outRate)
            val up = outRate / gcd
            val down = inRate / gcd
            val taps = ceil(BaseTaps * maxOf(1.0, inRate.toDouble() / outRate)).toInt()
            val length = up.toLong() * taps
            if (length > MaxCoefficients) return null

            // kaiser's estimate: how wide the transition is for this length and attenuation, in hz at the input rate
            val transitionHz = (StopbandDb - 7.95) / (2.285 * 2 * PI * taps) * inRate
            val stopHz = minOf(inRate, outRate) / 2.0
            val passbandHz = stopHz - transitionHz
            val cutoff = (stopHz - transitionHz / 2) / (up.toDouble() * inRate)
            val beta = 0.1102 * (StopbandDb - 8.7)
            val i0Beta = besselI0(beta)
            val centre = (length - 1) / 2.0

            val coefficients = FloatArray(length.toInt())
            for (k in 0 until length.toInt()) {
                val t = k - centre
                val sinc = if (t == 0.0) 2 * cutoff else sin(2 * PI * cutoff * t) / (PI * t)
                val ratio = t / centre
                val window = besselI0(beta * sqrt((1 - ratio * ratio).coerceAtLeast(0.0))) / i0Beta
                // stored phase by phase, so one output reads one run of memory; times up for the zeros stuffed between inputs
                val p = k % up
                val j = k / up
                coefficients[p * taps + j] = (sinc * window * up).toFloat()
            }
            return Table(up, down, taps, passbandHz, coefficients)
        }

        private fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            val half = x / 2
            var k = 1
            while (k < 64) {
                term *= (half / k) * (half / k)
                sum += term
                if (term < sum * 1e-12) break
                k++
            }
            return sum
        }

        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}
