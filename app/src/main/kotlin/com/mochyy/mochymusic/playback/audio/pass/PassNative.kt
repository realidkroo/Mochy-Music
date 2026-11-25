// PassNative.kt

package com.mochyy.mochymusic.playback.audio.pass

import java.nio.ByteBuffer

/** the jni half of audio pass (app/src/main/cpp); handles are only ever passed back here, and every native call throws IllegalStateException when the output is gone */
internal object PassNative {

    /** loads the library the first time a native output is wanted, so nobody else pays for it */
    val loaded: Boolean by lazy { runCatching { System.loadLibrary("mochypass") }.isSuccess }

    /** [driver] is [UsbDriver.native]: mochy's own usbdevfs calls or libusb */
    external fun usbOpen(fd: Int, plans: IntArray, bcdUsb: Int, driver: Int): Long
    external fun usbChosen(handle: Long): Int
    external fun usbCapacity(handle: Long): Int
    external fun usbWrite(handle: Long, buffer: ByteBuffer, offset: Int, length: Int): Int
    external fun usbPlay(handle: Long, playing: Boolean)
    external fun usbFlush(handle: Long)
    external fun usbPlayed(handle: Long): Long
    external fun usbControl(handle: Long, type: Int, request: Int, value: Int, index: Int, data: ByteArray): Int
    external fun usbClose(handle: Long)

    external fun exclusiveOpen(rate: Int, channels: Int, bits: Int, device: Int): Long
    external fun exclusiveDevice(handle: Long): Int
    external fun exclusiveCapacity(handle: Long): Int
    external fun exclusiveWrite(handle: Long, buffer: ByteBuffer, offset: Int, length: Int): Int
    external fun exclusivePlay(handle: Long, playing: Boolean)
    external fun exclusiveFlush(handle: Long)
    external fun exclusivePlayed(handle: Long): Long
    external fun exclusiveClose(handle: Long)
}
