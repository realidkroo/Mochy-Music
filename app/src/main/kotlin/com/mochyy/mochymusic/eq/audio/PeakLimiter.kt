package com.mochyy.mochymusic.eq.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.max

/** catches the equalizer's loudest peaks so a boost stays loud: looks ahead, eases down before full scale, holds longer than a bass cycle (rising between crests buzzes), one gain for all channels */
class PeakLimiter {

    var latencyFrames = 0
        private set

    private var channels = 0
    /** how long a dip is held and how long it takes to ease in: one frame more than the delay */
    private var window = 0
    private var holdFrames = 0
    private var releaseStep = 0.0

    private var delay = DoubleArray(0)
    private var delayPos = 0

    // the same delay as raw 16-bit pcm, oldest first, used instead of the doubles while resting
    private var pcmDelay = ByteArray(0)
    private var inPcm = false

    // the lowest gain asked for in the last window, kept as a queue that rises from front to back
    private var minGain = DoubleArray(0)
    private var minFrame = LongArray(0)
    private var minHead = 0
    private var minSize = 0

    // the held gains, averaged over the window so each dip becomes a smooth ramp
    private var held = DoubleArray(0)
    private var heldPos = 0
    private var heldSum = 0.0
    private var heldOnes = 0

    private var envelope = 1.0
    private var holdLeft = 0
    private var frame = 0L

    fun configure(sampleRate: Int, channels: Int) {
        this.channels = channels
        latencyFrames = (sampleRate * LookaheadSeconds).toInt().coerceAtLeast(1)
        window = latencyFrames + 1
        holdFrames = (sampleRate * HoldSeconds).toInt()
        releaseStep = 1.0 - exp(-1.0 / (ReleaseSeconds * sampleRate))
        delay = DoubleArray(latencyFrames * channels)
        pcmDelay = ByteArray(latencyFrames * channels * 2)
        minGain = DoubleArray(window)
        minFrame = LongArray(window)
        held = DoubleArray(window)
        reset()
    }

    fun reset() {
        delay.fill(0.0)
        delayPos = 0
        inPcm = false
        minHead = 0
        minSize = 0
        held.fill(1.0)
        heldPos = 0
        heldSum = window.toDouble()
        heldOnes = window
        envelope = 1.0
        holdLeft = 0
        frame = 0
    }

    /** nothing is being turned down, so all the limiter does right now is delay */
    val isResting: Boolean get() = envelope == 1.0 && heldOnes == window

    /** limits the first frames frames of buffer in place, what comes out is latencyFrames older */
    fun process(buffer: DoubleArray, frames: Int) {
        if (channels == 0) return
        if (inPcm) pcmToDoubles()
        // the usual case, nothing near full scale: skip the gain maths and only delay
        if (envelope == 1.0 && heldOnes == window && peak(buffer, frames) <= Ceiling) {
            delayOnly(buffer, frames)
            return
        }
        var i = 0
        for (f in 0 until frames) {
            var peak = 0.0
            for (c in 0 until channels) peak = max(peak, abs(buffer[i + c]))
            val needed = if (peak > Ceiling) Ceiling / peak else 1.0
            if (needed < 1.0) holdLeft = holdFrames else if (holdLeft > 0) holdLeft--
            // drop at once, hold, then recover slowly; the window below turns the drop into a ramp
            val recovered = envelope + (1.0 - envelope) * releaseStep
            val resting = when {
                holdLeft > 0 -> envelope
                recovered > 1.0 - Settled -> 1.0
                else -> recovered
            }
            envelope = minOf(needed, resting)

            val gain = smooth(lowestInWindow(envelope))
            for (c in 0 until channels) {
                val d = delayPos + c
                val late = delay[d]
                delay[d] = buffer[i + c]
                buffer[i + c] = late * gain
            }
            delayPos += channels
            if (delayPos == delay.size) delayPos = 0
            i += channels
            frame++
        }
    }

    private fun peak(buffer: DoubleArray, frames: Int): Double {
        var peak = 0.0
        for (i in 0 until frames * channels) peak = max(peak, abs(buffer[i]))
        return peak
    }

    private fun delayOnly(buffer: DoubleArray, frames: Int) {
        var i = 0
        for (f in 0 until frames) {
            for (c in 0 until channels) {
                val d = delayPos + c
                val late = delay[d]
                delay[d] = buffer[i]
                buffer[i] = late
                i++
            }
            delayPos += channels
            if (delayPos == delay.size) delayPos = 0
        }
        frame += frames
        // every gain in the window was 1, so the queue has nothing worth keeping
        minSize = 0
    }

    /** the resting case straight on 16-bit pcm: the samples only go through the delay, so they're copied in blocks, not one by one */
    fun delayPcm(input: ByteBuffer, out: ByteBuffer) {
        if (!inPcm) doublesToPcm()
        val frameBytes = (2 * channels).coerceAtLeast(2)
        val bytes = (input.remaining() / frameBytes) * frameBytes
        if (bytes <= 0) {
            input.position(input.limit())
            return
        }
        val held = pcmDelay.size
        if (bytes >= held) {
            out.put(pcmDelay)
            val end = input.limit()
            input.limit(input.position() + bytes - held)
            out.put(input)
            input.limit(end)
            input.get(pcmDelay)
        } else {
            out.put(pcmDelay, 0, bytes)
            System.arraycopy(pcmDelay, bytes, pcmDelay, 0, held - bytes)
            input.get(pcmDelay, held - bytes, bytes)
        }
        frame += bytes / (2 * channels)
        minSize = 0
    }

    // what's in the delay comes out exactly as the double path would round it, so the switch can't be heard
    private fun doublesToPcm() {
        val pcm = ByteBuffer.wrap(pcmDelay).order(ByteOrder.nativeOrder())
        for (k in delay.indices) {
            var d = delayPos + k
            if (d >= delay.size) d -= delay.size
            pcm.putShort(toPcm(delay[d]))
        }
        inPcm = true
    }

    private fun pcmToDoubles() {
        val pcm = ByteBuffer.wrap(pcmDelay).order(ByteOrder.nativeOrder())
        for (k in delay.indices) delay[k] = pcm.getShort() / 32768.0
        delayPos = 0
        inPcm = false
    }

    /** the lowest of the last window envelopes, value is the newest */
    private fun lowestInWindow(value: Double): Double {
        while (minSize > 0 && minFrame[minHead] <= frame - window) {
            minHead = if (minHead + 1 == window) 0 else minHead + 1
            minSize--
        }
        // anything at the back that's no lower than the new value can never be the lowest again
        while (minSize > 0) {
            var back = minHead + minSize - 1
            if (back >= window) back -= window
            if (minGain[back] < value) break
            minSize--
        }
        var slot = minHead + minSize
        if (slot >= window) slot -= window
        minGain[slot] = value
        minFrame[slot] = frame
        minSize++
        return minGain[minHead]
    }

    /** the average of the last window held gains: each is at or below what the oldest frame in the delay needs, so the ramp lands in time and no peak gets through */
    private fun smooth(gain: Double): Double {
        val oldest = held[heldPos]
        if (oldest == 1.0) heldOnes--
        if (gain == 1.0) heldOnes++
        heldSum += gain - oldest
        held[heldPos] = gain
        heldPos = if (heldPos + 1 == window) 0 else heldPos + 1
        if (heldOnes == window) {
            // back to rest: start the sum clean so rounding never builds up
            heldSum = window.toDouble()
            return 1.0
        }
        return heldSum / window
    }

    companion object {
        private const val LookaheadSeconds = 0.005
        /** one cycle of a 40 hz bass note */
        private const val HoldSeconds = 0.025
        private const val ReleaseSeconds = 0.15
        private const val Ceiling = 1.0
        private const val Settled = 1e-6

        /** how much of a bass or mid boost the limiter takes without the song sounding squashed */
        private const val BoostRoomDb = 4.0

        /** how many db of boost at frequency the limiter takes alone: songs carry less treble than bass, so the room grows 3 db an octave above 1 khz */
        fun boostRoomDb(frequency: Double): Double =
            BoostRoomDb + 3.0 * max(0.0, log2(frequency / 1000.0))
    }
}
