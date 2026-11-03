// DeviceOutputSheet.kt

package com.mochyy.mochymusic.ui.player.menu

import android.media.AudioManager
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.SettingsBluetooth
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.UsbPassthroughKey
import com.mochyy.mochymusic.playback.audio.AudioOutputs
import com.mochyy.mochymusic.ui.component.SheetIcon
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.flow.MutableStateFlow

/** "change playback": both volumes, then every output; picking one moves the song there, cast receivers play it themselves */
@Composable
fun DeviceOutputSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val service = playerConnection?.service
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    val deviceVolume = remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        )
    }
    val musicVolume = remember {
        mutableFloatStateOf(playerConnection?.player?.volume ?: 1f)
    }

    val devices by remember { service?.audioOutputs?.devices ?: MutableStateFlow(emptyList()) }.collectAsState()
    val cast = service?.castConnectionHandler
    val castTargets by remember { cast?.targets ?: MutableStateFlow(emptyList()) }.collectAsState()
    val castingTo by remember { cast?.castDeviceName ?: MutableStateFlow(null) }.collectAsState()
    val isCasting by remember { cast?.isCasting ?: MutableStateFlow(false) }.collectAsState()
    val isConnecting by remember { cast?.isConnecting ?: MutableStateFlow(false) }.collectAsState()
    // the receiver tapped last, so its row can say it's connecting (a tv launching its player takes a few seconds)
    var tappedTarget by remember { mutableStateOf<String?>(null) }
    val (passthrough) = rememberPreference(UsbPassthroughKey, false)
    val anyChosen = devices.any { it.isChosen }
    // larping, whatever is really playing turns into the level's dac, fed straight from mochy
    val larp = com.mochyy.mochymusic.ui.player.rememberLarpMode()
    val larping = larp != null
    val larpName = larp?.let { androidx.compose.ui.res.stringResource(it.dac) }.orEmpty()
    val larpDetail = larp?.let { com.mochyy.mochymusic.ui.player.larpDacDetail(it) }.orEmpty()

    // receivers are only looked for while this is open: discovery keeps the wifi radio busy
    DisposableEffect(cast) {
        cast?.startDiscovery()
        onDispose { cast?.stopDiscovery() }
    }

    MenuSheetSurface(onDismiss = onDismiss, halfDetent = 0.66f, fullDetent = 0.92f) { _ ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 28.dp)
        ) {
            SectionLabel("Change playback")

            VolumeCard(
                icon = R.drawable.volume_up,
                title = android.os.Build.MODEL ?: "this device",
                value = deviceVolume.floatValue,
                onValueChange = { next ->
                    deviceVolume.floatValue = next
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        (next * maxVolume).toInt(),
                        0,
                    )
                },
            )

            Spacer(modifier = Modifier.height(10.dp))

            VolumeCard(
                icon = R.drawable.music_note,
                title = "Music volume",
                value = musicVolume.floatValue,
                onValueChange = { next ->
                    musicVolume.floatValue = next
                    playerConnection?.player?.volume = next
                },
            )

            Spacer(modifier = Modifier.height(22.dp))
            SectionLabel("Play on")

            Column(modifier = Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeviceRow(
                    label = "Automatic",
                    detail = "Wherever Android sends it",
                    icon = Icons.Rounded.AutoMode,
                    selected = !anyChosen && !isCasting,
                    onClick = { service?.setPreferredAudioDevice(null) },
                )
                devices.forEach { device ->
                    val larpRow = larping && device.isActive && !isCasting
                    DeviceRow(
                        label = if (larpRow) larpName else deviceLabel(device),
                        detail = if (larpRow) larpDetail else deviceDetail(device, passthrough),
                        icon = if (larpRow) AudioOutputs.Kind.USB.icon() else device.kind.icon(),
                        selected = device.isChosen && !isCasting,
                        playing = device.isActive && !isCasting,
                        onClick = {
                            if (isCasting) cast?.disconnect()
                            service?.setPreferredAudioDevice(device.id)
                        },
                    )
                }
            }

            if (castTargets.isNotEmpty() || isCasting) {
                Spacer(modifier = Modifier.height(22.dp))
                SectionLabel("Other devices on your network")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isCasting) {
                        DeviceRow(
                            label = castingTo ?: "Cast device",
                            detail = "Playing here now · tap to stop casting",
                            icon = Icons.Rounded.CastConnected,
                            selected = true,
                            onClick = { cast?.disconnect() },
                        )
                    }
                    castTargets.filter { it.name != castingTo }.forEach { target ->
                        val connecting = isConnecting && tappedTarget == target.id
                        DeviceRow(
                            label = target.name,
                            detail = if (connecting) stringResource(R.string.cast_connecting) else target.detail ?: "Cast",
                            icon = Icons.Rounded.Cast,
                            onClick = {
                                tappedTarget = target.id
                                cast?.connectTo(target.id)
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            DeviceRow(
                label = "Pair a new device",
                detail = "Opens Bluetooth settings",
                icon = Icons.Rounded.SettingsBluetooth,
                onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
            )
        }
    }
}

private fun deviceLabel(device: AudioOutputs.Device): String = device.name.ifBlank {
    when (device.kind) {
        AudioOutputs.Kind.PHONE -> "This phone"
        AudioOutputs.Kind.WIRED -> "Wired headphones"
        AudioOutputs.Kind.USB -> "USB audio"
        AudioOutputs.Kind.BLUETOOTH -> "Bluetooth device"
        AudioOutputs.Kind.HDMI -> "HDMI"
    }
}

// a dac says what it takes, so that's shown; the rest just say what they are
private fun deviceDetail(device: AudioOutputs.Device, passthrough: Boolean): String {
    val kind = when (device.kind) {
        AudioOutputs.Kind.PHONE -> "Speaker"
        AudioOutputs.Kind.WIRED -> "Headphone jack"
        AudioOutputs.Kind.USB -> "USB DAC"
        AudioOutputs.Kind.BLUETOOTH -> "Bluetooth"
        AudioOutputs.Kind.HDMI -> "HDMI"
    }
    val takes = listOfNotNull(
        device.maxSampleRate?.let { "up to ${it / 1000} kHz" },
        device.bitDepths.maxOrNull()?.let { "$it-bit" },
    ).takeIf { device.kind == AudioOutputs.Kind.USB }.orEmpty()
    val bypass = "passthrough on".takeIf {
        passthrough && device.kind == AudioOutputs.Kind.USB && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
    return (listOf(kind) + takes + listOfNotNull(bypass) + listOfNotNull("playing".takeIf { device.isActive })).joinToString(" · ")
}

private fun AudioOutputs.Kind.icon(): ImageVector = when (this) {
    AudioOutputs.Kind.PHONE -> Icons.Rounded.Smartphone
    AudioOutputs.Kind.WIRED -> Icons.Rounded.Headphones
    AudioOutputs.Kind.USB -> Icons.Rounded.Usb
    AudioOutputs.Kind.BLUETOOTH -> Icons.Rounded.Bluetooth
    AudioOutputs.Kind.HDMI -> Icons.Rounded.Tv
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color(0xFF8A8A8A),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun VolumeCard(
    icon: Int,
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MenuRowSurface)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetIcon(icon, tint = Color.White)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.volume_down),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
            LineSlider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun DeviceRow(
    label: String,
    detail: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    selected: Boolean = false,
    playing: Boolean = false,
) {
    val checkAlpha by animateFloatAsState(if (selected) 1f else 0f, label = "deviceCheck")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected || playing) Color.White.copy(alpha = 0.10f) else MenuRowSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                // a dac's line (kind, rates, bit depth, passthrough) runs long: it wraps instead of losing its end
                Text(
                    text = detail,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { alpha = checkAlpha }
                .clip(CircleShape)
                .background(Color.White),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
        }
    }
}
