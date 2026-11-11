package com.mochyy.mochymusic.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.mochyy.mochymusic.playback.audio.pass.AudioPass
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

// what the media session drives (notification, lock screen, headset, car): its pause, play and skips get the same fades as the app's buttons
@UnstableApi
class TransportFadingPlayer(
    val exoPlayer: ExoPlayer,
    private val transport: TransportTransitions,
    private val pass: AudioPass? = null,
) : ForwardingPlayer(exoPlayer) {

    // the session's own listeners, told directly when the dac's volume comes or goes, since the player underneath never sees it
    private val listeners = CopyOnWriteArrayList<Player.Listener>()
    private val main = Handler(Looper.getMainLooper())

    // held here so it lives exactly as long as this player; audio pass only keeps it weakly
    private val onDacVolume: () -> Unit = { main.post(::announceDacVolume) }

    init {
        pass?.addVolumeListener(onDacVolume)
    }

    override fun play() = transport.play()

    override fun pause() = transport.pause()

    override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) transport.play() else transport.pause()

    override fun seekToNext() = transport.skipNext()

    override fun seekToNextMediaItem() = transport.skipNext()

    override fun seekToPrevious() = transport.skipPrevious()

    override fun seekToPreviousMediaItem() = transport.skipPrevious()

    override fun seekTo(positionMs: Long) {
        transport.cancel()
        super.seekTo(positionMs)
    }

    // a song picked from the queue on the lock screen or in the car fades or blends in like one picked in the app
    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        if (mediaItemIndex == exoPlayer.currentMediaItemIndex) {
            transport.cancel()
            super.seekToDefaultPosition(mediaItemIndex)
        } else {
            transport.jumpTo { mediaItemIndex }
        }
    }

    override fun stop() {
        transport.cancel()
        super.stop()
    }

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        listeners -= listener
    }

    // while mochy's own usb driver plays, android's music volume no longer reaches the dac: the volume keys and the system panel turn the dac's own knob instead
    private val dac: AudioPass.DacVolume? get() = pass?.dacVolume?.value

    override fun getDeviceInfo(): DeviceInfo = if (dac != null) DacDevice else super.getDeviceInfo()

    override fun getDeviceVolume(): Int = dac?.let { (AudioPass.levelOf(it) * AudioPass.VolumeSteps).roundToInt() } ?: super.getDeviceVolume()

    override fun isDeviceMuted(): Boolean = if (dac != null) false else super.isDeviceMuted()

    override fun setDeviceVolume(volume: Int, flags: Int) {
        if (dac != null) pass?.setDacLevel(volume.toFloat() / AudioPass.VolumeSteps) else super.setDeviceVolume(volume, flags)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun setDeviceVolume(volume: Int) {
        if (dac != null) pass?.setDacLevel(volume.toFloat() / AudioPass.VolumeSteps) else super.setDeviceVolume(volume)
    }

    override fun increaseDeviceVolume(flags: Int) {
        if (dac != null) pass?.stepDacVolume(up = true) else super.increaseDeviceVolume(flags)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun increaseDeviceVolume() {
        if (dac != null) pass?.stepDacVolume(up = true) else super.increaseDeviceVolume()
    }

    override fun decreaseDeviceVolume(flags: Int) {
        if (dac != null) pass?.stepDacVolume(up = false) else super.decreaseDeviceVolume(flags)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun decreaseDeviceVolume() {
        if (dac != null) pass?.stepDacVolume(up = false) else super.decreaseDeviceVolume()
    }

    override fun isCommandAvailable(command: Int): Boolean = (dac != null && command in DacCommands) || super.isCommandAvailable(command)

    override fun getAvailableCommands(): Player.Commands {
        val commands = super.getAvailableCommands()
        return if (dac != null) commands.buildUpon().addAll(*DacCommands.toIntArray()).build() else commands
    }

    private fun announceDacVolume() {
        val info = deviceInfo
        val volume = deviceVolume
        val commands = availableCommands
        listeners.forEach { listener ->
            listener.onDeviceInfoChanged(info)
            listener.onDeviceVolumeChanged(volume, false)
            listener.onAvailableCommandsChanged(commands)
        }
    }

    private companion object {
        val DacDevice: DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMinVolume(0).setMaxVolume(AudioPass.VolumeSteps).build()

        @Suppress("DEPRECATION")
        val DacCommands = listOf(
            Player.COMMAND_GET_DEVICE_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME,
            Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
            Player.COMMAND_ADJUST_DEVICE_VOLUME,
            Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
        )
    }
}
