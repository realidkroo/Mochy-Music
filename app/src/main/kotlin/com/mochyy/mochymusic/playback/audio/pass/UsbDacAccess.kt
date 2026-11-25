// UsbDacAccess.kt

package com.mochyy.mochymusic.playback.audio.pass

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.mochyy.mochymusic.playback.audio.UsbDacReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** getting at the usb dac for the direct driver: android's permission, plug and unplug, and its descriptors read once per plug */
internal class UsbDacAccess(context: Context, private val onChanged: () -> Unit) {

    private val app = context.applicationContext
    val manager: UsbManager? = app.getSystemService(UsbManager::class.java)
    private val permissionAction = "${app.packageName}.AUDIO_PASS_USB"
    private val descriptors = HashMap<Int, UsbAudioDescriptors.Dac>()
    private var asked: Int? = null
    private var started = false

    /** told when a dac goes away, so a stream to it stops at once instead of at its next write */
    var onDetached: ((Int) -> Unit)? = null

    /** null with no dac plugged in, else whether android lets mochy open it */
    private val _permitted = MutableStateFlow<Boolean?>(null)
    val permitted: StateFlow<Boolean?> = _permitted.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> device?.let {
                    synchronized(this@UsbDacAccess) {
                        descriptors.remove(it.deviceId)
                        if (asked == it.deviceId) asked = null
                    }
                    onDetached?.invoke(it.deviceId)
                }
                permissionAction -> synchronized(this@UsbDacAccess) { asked = null }
            }
            refresh()
            onChanged()
        }
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter(permissionAction).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        runCatching { ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { app.unregisterReceiver(receiver) }
    }

    fun device(): UsbDevice? = manager?.let(UsbDacReader::audioDevice)

    fun hasPermission(device: UsbDevice): Boolean = runCatching { manager?.hasPermission(device) == true }.getOrDefault(false)

    fun refresh() {
        _permitted.value = device()?.let(::hasPermission)
    }

    /** shows android's "allow mochy to access" dialog, once per plug */
    @Synchronized
    fun requestPermission() {
        val usb = manager ?: return
        val device = device() ?: return
        if (hasPermission(device) || asked == device.deviceId) return
        asked = device.deviceId
        // android fills in the device and the answer, so the intent has to stay mutable; it's ours alone through the package
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = PendingIntent.getBroadcast(app, device.deviceId, Intent(permissionAction).setPackage(app.packageName), flags)
        runCatching { usb.requestPermission(device, intent) }.onFailure {
            asked = null
            Timber.tag("UsbDacAccess").w(it, "usb permission request failed")
        }
    }

    // the configuration the device runs in, asked straight from it (GET_CONFIGURATION); null leaves the parser on the first one
    private fun activeConfiguration(connection: android.hardware.usb.UsbDeviceConnection): Int? {
        val answer = ByteArray(1)
        return if (connection.controlTransfer(0x80, 0x08, 0, 0, answer, 1, 300) == 1) answer[0].toInt() and 0xff else null
    }

    /** the dac's settings and volume knob, read from its descriptors the first time they're needed after it's plugged in */
    @Synchronized
    fun dac(device: UsbDevice): UsbAudioDescriptors.Dac? {
        descriptors[device.deviceId]?.let { return it }
        val usb = manager ?: return null
        if (!hasPermission(device)) return null
        val connection = runCatching { usb.openDevice(device) }.getOrNull() ?: return null
        return try {
            connection.rawDescriptors?.let { raw -> UsbAudioDescriptors.parse(raw, activeConfiguration(connection)) }?.also { descriptors[device.deviceId] = it }
        } catch (error: Exception) {
            Timber.tag("UsbDacAccess").w(error, "couldn't read the dac's descriptors")
            null
        } finally {
            connection.close()
        }
    }
}
