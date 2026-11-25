// UsbPass.kt

package com.mochyy.mochymusic.playback.audio.pass

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import java.nio.ByteBuffer

/** how the direct driver reaches the dac: its own kernel calls, or libusb for dacs that behave better with it */
enum class UsbDriver(val native: Int) { MOCHY(0), LIBUSB(1) }

/** the direct driver's output: android opens the dac, the native worker streams to it and android's own driver steps aside while it plays */
internal class UsbPass private constructor(
    private val connection: UsbDeviceConnection,
    private var handle: Long,
    val usbDeviceId: Int,
    override val deviceName: String,
    plan: UsbAudioDescriptors.Plan,
    unit: UsbAudioDescriptors.VolumeUnit?,
    rememberedDb: Double?,
) : PassTransport {

    override val sampleRate = plan.rate
    // what the player writes: a dac with more channels gets the song in its first ones, the rest silent, done natively
    override val channels = plan.sourceChannels

    // samples sit at the top of the slot, so a 24-bit dac in a 4-byte slot gets the song's bits unchanged
    override val bits = plan.subslot * 8
    val validBits = plan.bitResolution
    override val capacityFrames: Long = PassNative.usbCapacity(handle).toLong()

    private val transfer = UsbTransfer { type, request, value, index, data ->
        if (handle == 0L) -1 else PassNative.usbControl(handle, type, request, value, index, data)
    }

    /** the dac's own volume, turned down before the first sound; null when it has none */
    val volume: UsbDacVolume? = unit?.let { runCatching { UsbDacVolume.open(transfer, it) }.getOrNull() }?.also { v ->
        // the level picked last time comes back, but never louder than the dac's own knob was left
        rememberedDb?.let { db -> v.set(transfer, minOf(db, v.originalDb)) }
    }

    @Volatile private var unplugged = false

    /** called off the playback thread; usb control requests are safe beside the streaming worker */
    @Synchronized
    fun setVolume(db: Double): Double? = volume?.set(transfer, db)

    fun markUnplugged() {
        unplugged = true
    }

    override fun write(buffer: ByteBuffer): Int {
        val taken = call { PassNative.usbWrite(handle, buffer, buffer.position(), buffer.remaining()) }
        buffer.position(buffer.position() + taken)
        return taken
    }

    override fun play() = call { PassNative.usbPlay(handle, true) }

    override fun pause() = call { PassNative.usbPlay(handle, false) }

    override fun flush() = call { PassNative.usbFlush(handle) }

    override fun playedFrames(): Long = call { PassNative.usbPlayed(handle) }

    @Synchronized
    override fun release() {
        if (handle == 0L) return
        // the dac goes back as loud as it was found: its attenuator lives in the dac and would follow it into every other app
        if (!unplugged) runCatching { volume?.restore(transfer) }
        PassNative.usbClose(handle)
        handle = 0L
        runCatching { connection.close() }
    }

    private inline fun <T> call(block: () -> T): T {
        if (unplugged) throw PassFailure("the dac was unplugged")
        if (handle == 0L) throw PassFailure("the dac is closed")
        return try {
            block()
        } catch (error: IllegalStateException) {
            throw PassFailure(error.message ?: "the dac stopped", error)
        }
    }

    companion object {
        fun open(
            usb: UsbManager,
            device: UsbDevice,
            dac: UsbAudioDescriptors.Dac,
            rate: Int,
            channels: Int,
            sourceBits: Int,
            allowFixedVolume: Boolean,
            rememberedDb: Double?,
            driver: UsbDriver = UsbDriver.MOCHY,
        ): UsbPass {
            if (!PassNative.loaded) throw PassFailure("the native part didn't load")
            val plans = UsbAudioDescriptors.plans(dac, rate, channels, sourceBits).take(16)
            if (plans.isEmpty()) throw PassFailure("the dac has no setting for $rate Hz ${sourceBits}-bit")
            if (dac.volume == null && !allowFixedVolume) throw PassFailure("the dac has no volume of its own")
            val connection = usb.openDevice(device) ?: throw PassFailure("android wouldn't open the dac")
            val handle = try {
                PassNative.usbOpen(connection.fileDescriptor, encode(plans), dac.bcdUsb, driver.native)
            } catch (error: IllegalStateException) {
                connection.close()
                throw PassFailure(error.message ?: "the dac wouldn't start", error)
            }
            val plan = plans[PassNative.usbChosen(handle)]
            val name = device.productName?.trim()?.takeIf { it.isNotEmpty() } ?: "USB DAC"
            val pass = UsbPass(connection, handle, device.deviceId, name, plan, dac.volume, rememberedDb)
            if (pass.volume == null && !allowFixedVolume) {
                pass.release()
                throw PassFailure("the dac's volume couldn't be turned down first")
            }
            return pass
        }

        // the same order the native side reads them in, 26 ints a plan
        private fun encode(plans: List<UsbAudioDescriptors.Plan>): IntArray = plans.flatMap { p ->
            val i = p.implicit
            listOf(
                p.controlInterface, p.streamingInterface, p.alt, if (p.uac2) 1 else 0, p.clockId, p.selectorId, p.selectorPin,
                p.endpoint, p.endpointAttributes, p.maxPacket, p.interval, if (p.rateControl) 1 else 0,
                p.feedbackEndpoint, p.feedbackMaxPacket, p.rate, p.channels, p.subslot, p.bitResolution,
                p.sourceChannels, i?.streamingInterface ?: -1, i?.alt ?: 0, i?.endpoint ?: -1, i?.maxPacket ?: 0, i?.interval ?: 1, i?.frameBytes ?: 0,
                if (i?.rateControl == true) 1 else 0,
            )
        }.toIntArray()
    }
}
