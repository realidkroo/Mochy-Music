// MixerNative.kt

package com.mochyy.mochymusic.eq.mixer

import java.nio.ByteBuffer

/** the jni half of the mixer (app/src/main/cpp/fx); engine handles only ever go back to their own player's playback thread */
internal object MixerNative {

    /** loads the library the first time the mixer is wanted, so nobody else pays for it */
    val loaded: Boolean by lazy { runCatching { System.loadLibrary("mochyfx") }.isSuccess }

    /** the master's id, after the sixteen mix buses; the same as model.h */
    const val Master = 16
    const val MaxBuses = 16
    const val MaxMeters = 8
    /** [peakL, peakR] per bus with the master last, then a clip flag */
    const val PeakCount = (MaxBuses + 1) * 2 + 1

    // focus values, the same as model.h; a lane is LaneFocus + its index
    const val FocusAll = 0
    const val FocusMid = 1
    const val FocusSide = 2
    const val FocusLeft = 3
    const val FocusRight = 4
    const val FocusLane = 10

    external fun create(rate: Int, channels: Int): Long
    external fun destroy(handle: Long)
    external fun process(handle: Long, input: ByteBuffer, inputOffset: Int, output: ByteBuffer, frames: Int): Boolean
    external fun reset(handle: Long)
    external fun bitstream(handle: Long, data: ByteBuffer, offset: Int, size: Int, timeUs: Long)

    external fun begin()
    external fun bus(id: Int, songIn: Boolean, focus: Int, sends: Int, master: Boolean)
    external fun slot(bus: Int, uid: Long, type: String, source: String, vars: Array<String>, revision: Int, values: FloatArray)
    external fun commit(enabled: Boolean)

    external fun busControl(id: Int, gainDb: Float, pan: Float, mute: Boolean, solo: Boolean)
    external fun sendLevel(from: Int, to: Int, level: Float)
    external fun params(uid: Long, values: FloatArray)
    external fun bypass(uid: Long, bypassed: Boolean)

    external fun metering(on: Boolean)
    external fun peaks(out: FloatArray)
    external fun loudness(out: FloatArray)
    external fun resetLoudness()
    external fun slotReadings(uid: Long, out: FloatArray): Boolean
    external fun slotProblem(uid: Long): String?
    external fun console(uid: Long): String
    external fun resume(uid: Long)

    external fun builtins(): String
    external fun loadLibrary(path: String): String?
    external fun checkScript(source: String): String
    external fun renderTail(type: String, source: String, vars: Array<String>, values: FloatArray, rate: Int, seconds: Float, points: Int): FloatArray
}
