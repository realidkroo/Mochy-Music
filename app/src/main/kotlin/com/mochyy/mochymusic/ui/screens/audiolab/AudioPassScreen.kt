// dac's stuff, audio pass's page: the dac's or headphones' details, the output switches and the bit-perfect modes

package com.mochyy.mochymusic.ui.screens.audiolab

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AudioPassFixedVolumeKey
import com.mochyy.mochymusic.constants.AudioPassMode
import com.mochyy.mochymusic.constants.AudioPassModeKey
import com.mochyy.mochymusic.constants.HiFiResamplerKey
import com.mochyy.mochymusic.constants.HiResOutputKey
import com.mochyy.mochymusic.constants.PreferUsbDacKey
import com.mochyy.mochymusic.constants.UsbPassthroughKey
import com.mochyy.mochymusic.playback.audio.AudioOutputs
import com.mochyy.mochymusic.playback.audio.BluetoothCodec
import com.mochyy.mochymusic.playback.audio.BluetoothCodecWatcher
import com.mochyy.mochymusic.playback.audio.LdacQuality
import com.mochyy.mochymusic.playback.audio.UsbDacDetails
import com.mochyy.mochymusic.playback.audio.UsbDacReader
import com.mochyy.mochymusic.playback.audio.pass.AudioPass
import com.mochyy.mochymusic.playback.audio.pass.PassBlocker
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale

/** what the status card is about: whichever output plays now, with a plugged-in dac first when nothing else is */
private sealed interface Shown {
    data class Dac(val details: UsbDacDetails) : Shown
    data class Bluetooth(val state: BluetoothCodecWatcher.State) : Shown
    data object Wired : Shown
    data object Nothing : Shown
}

@Composable
fun AudioPassScreen(navController: NavController) {
    val context = LocalContext.current
    val service = LocalPlayerConnection.current?.service
    val outputs = service?.audioOutputs
    val devices by remember(outputs) { outputs?.devices ?: MutableStateFlow(emptyList()) }.collectAsState()
    val blockers by remember(service) { service?.passBlockers ?: MutableStateFlow(emptySet()) }.collectAsState()
    val pass = service?.audioPass
    val status by remember(pass) { pass?.status ?: MutableStateFlow(AudioPass.Status.Off) }.collectAsState()
    val dacVolume by remember(pass) { pass?.dacVolume ?: MutableStateFlow(null) }.collectAsState()
    val usbPermitted by remember(pass) { pass?.usbPermitted ?: MutableStateFlow(null) }.collectAsState()
    val canRestore by remember(service) { service?.canRestorePassBlockers ?: MutableStateFlow(false) }.collectAsState(initial = false)

    // the dac's details come from binder calls, so they're read off the main thread whenever the outputs change
    var dac by remember { mutableStateOf<UsbDacDetails?>(null) }
    var mixerRate by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(devices) {
        val (details, rate) = withContext(Dispatchers.IO) { UsbDacReader.read(context) to UsbDacReader.mixerRate() }
        dac = details
        mixerRate = rate
    }
    val bluetooth = rememberBluetoothCodec()
    val codecState by bluetooth.state.collectAsState()

    val activeKind = devices.firstOrNull { it.isActive }?.kind
    val plugged = dac
    val shown = when {
        activeKind == AudioOutputs.Kind.BLUETOOTH -> Shown.Bluetooth(codecState)
        plugged != null -> Shown.Dac(plugged)
        activeKind == AudioOutputs.Kind.WIRED -> Shown.Wired
        else -> Shown.Nothing
    }

    AudioLabPage(
        title = LabTitle(
            stringResource(R.string.audiopass_title_first),
            stringResource(R.string.audiopass_title_second),
            firstSize = 64.sp,
            secondSize = 30.sp,
            secondDim = true,
            secondBadge = stringResource(R.string.audiopass_beta),
        ),
        onBack = { navController.navigateUp() },
        subtitle = subtitleFor(shown),
    ) {
        StatusCard(shown, mixerRate, onPermission = bluetooth::refresh)

        Spacer(Modifier.height(24.dp))
        LabSheetTitle(stringResource(R.string.audiopass_options))
        val (preferUsb, onPreferUsb) = rememberPreference(PreferUsbDacKey, defaultValue = false)
        val (hiRes, onHiRes) = rememberPreference(HiResOutputKey, defaultValue = false)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OptionTile(stringResource(R.string.audiopass_prefer_usb), preferUsb, onPreferUsb, Modifier.weight(1f).fillMaxHeight())
            OptionTile(stringResource(R.string.audiopass_hires), hiRes, onHiRes, Modifier.weight(1f).fillMaxHeight())
        }

        Spacer(Modifier.height(24.dp))
        LabSheetTitle(stringResource(R.string.audiopass_exclusive))
        val (resampler, onResampler) = rememberPreference(HiFiResamplerKey, defaultValue = false)
        LabCard(padding = 10.dp) {
            LabToggleRow(
                title = stringResource(R.string.audiopass_resampler),
                subtitle = stringResource(R.string.audiopass_resampler_desc),
                checked = resampler,
                onCheckedChange = onResampler,
            )
        }
        Spacer(Modifier.height(12.dp))
        val (passthrough, onPassthrough) = rememberPreference(UsbPassthroughKey, defaultValue = false)
        val (mode, onMode) = rememberEnumPreference(AudioPassModeKey, defaultValue = AudioPassMode.Default)
        // switching it on while something changes the sound offers to turn those off then and there
        val overlay = LocalZoomOutOverlayState.current
        val offerToClear = {
            if (blockers.isNotEmpty()) overlay.showPassBlockersOffer(blockers) { service?.clearPassBlockers() }
        }
        PassthroughCard(
            on = passthrough,
            onChange = { turnedOn ->
                onPassthrough(turnedOn)
                if (turnedOn && mode.exclusive) offerToClear()
            },
            mode = mode,
            onMode = { picked ->
                onMode(picked)
                if (passthrough && picked.exclusive && !mode.exclusive) offerToClear()
            },
            status = status.takeIf { mode.exclusive },
        )

        LabCollapsible(passthrough && mode == AudioPassMode.DIRECT_USB) {
            Spacer(Modifier.height(12.dp))
            DirectUsbCard(
                permitted = usbPermitted,
                volume = dacVolume,
                onAllow = { pass?.requestUsbPermission() },
                onVolume = { db -> pass?.setDacVolume(db) },
            )
        }
        LabCollapsible(passthrough && mode.exclusive) {
            Spacer(Modifier.height(12.dp))
            BlockersCard(
                active = blockers,
                lossyToo = mode == AudioPassMode.DIRECT_USB,
                canRestore = canRestore,
                onClear = { service?.clearPassBlockers() },
                onRestore = { service?.restorePassBlockers() },
            )
        }
    }
}

@Composable
private fun rememberBluetoothCodec(): BluetoothCodecWatcher {
    val context = LocalContext.current
    val watcher = remember { BluetoothCodecWatcher(context) }
    DisposableEffect(watcher) {
        watcher.start()
        onDispose { watcher.stop() }
    }
    return watcher
}

@Composable
private fun subtitleFor(shown: Shown): String = when (shown) {
    is Shown.Dac -> stringResource(R.string.audiopass_joined, shown.details.name, stringResource(R.string.audiopass_kind_usb))
    is Shown.Bluetooth -> stringResource(R.string.audiopass_status_bluetooth)
    Shown.Wired -> stringResource(R.string.audiopass_value_jack)
    Shown.Nothing -> stringResource(R.string.audiopass_kind_speaker)
}

// ---- status ------------------------------------------------------------------------------

@Composable
private fun StatusCard(shown: Shown, mixerRate: Int?, onPermission: () -> Unit) {
    val status = when (shown) {
        is Shown.Dac -> if (shown.details.androidOutput) R.string.audiopass_status_connected else R.string.audiopass_status_driven
        is Shown.Bluetooth -> R.string.audiopass_status_bluetooth
        Shown.Wired -> R.string.audiopass_status_wired
        Shown.Nothing -> R.string.audiopass_status_none
    }
    LabCard(padding = 20.dp) {
        Text(
            stringResource(R.string.audiopass_status_label),
            fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            letterSpacing = (-0.02).em, color = Color(0xFF8E8E93),
        )
        Text(
            stringResource(status),
            fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp,
            letterSpacing = (-0.03).em, color = Color.White,
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(LabColors.StageLine))
        Spacer(Modifier.height(14.dp))
        when (shown) {
            is Shown.Dac -> InfoRows(dacRows(shown.details, mixerRate))
            is Shown.Bluetooth -> BluetoothDetails(shown.state, onPermission)
            Shown.Wired -> InfoRows(listOfNotNull(
                stringResource(R.string.audiopass_info_output) to stringResource(R.string.audiopass_value_jack),
                mixerRate?.let { stringResource(R.string.audiopass_info_mixer) to stringResource(R.string.audiopass_value_khz, khz(it)) },
            ))
            Shown.Nothing -> {
                Note(stringResource(R.string.audiopass_status_none_hint))
                mixerRate?.let {
                    Spacer(Modifier.height(12.dp))
                    InfoRows(listOf(stringResource(R.string.audiopass_info_mixer) to stringResource(R.string.audiopass_value_khz, khz(it))))
                }
            }
        }
    }
}

@Composable
private fun dacRows(dac: UsbDacDetails, mixerRate: Int?): List<Pair<String, String>> {
    val takes = listOfNotNull(
        if (dac.rates.isEmpty()) stringResource(R.string.audiopass_value_any_rate) else stringResource(R.string.audiopass_value_khz, rateSpan(dac.rates)),
        dac.bitDepths.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.audiopass_value_bits, it.joinToString("/")) },
    ).joinToString(" · ")
    val bitPerfect = when {
        dac.bitPerfect == null -> stringResource(R.string.audiopass_value_needs_14)
        dac.bitPerfect.isEmpty() -> stringResource(R.string.audiopass_value_not_offered)
        else -> listOf(
            stringResource(R.string.audiopass_value_khz, rateSpan(dac.bitPerfect.map { it.sampleRate })),
            stringResource(R.string.audiopass_value_bits, dac.bitPerfect.map { it.bits }.distinct().sorted().joinToString("/")),
        ).joinToString(" · ")
    }
    val id = if (dac.vendorId != null && dac.productId != null) String.format(Locale.ROOT, "%04x:%04x", dac.vendorId, dac.productId) else null
    return listOfNotNull(
        stringResource(R.string.audiopass_info_dac) to dac.name,
        dac.maker?.let { stringResource(R.string.audiopass_info_maker) to it },
        dac.uacVersion?.let {
            stringResource(R.string.audiopass_info_usb) to
                if (dac.asyncClock == true) stringResource(R.string.audiopass_value_uac_async, it) else stringResource(R.string.audiopass_value_uac, it)
        },
        stringResource(R.string.audiopass_info_takes) to takes,
        stringResource(R.string.audiopass_info_bitperfect) to bitPerfect,
        mixerRate?.takeIf { dac.androidOutput }?.let { stringResource(R.string.audiopass_info_mixer) to stringResource(R.string.audiopass_value_khz, khz(it)) },
        id?.let { stringResource(R.string.audiopass_info_id) to (dac.firmware?.let { fw -> stringResource(R.string.audiopass_value_firmware, id, fw) } ?: id) },
        dac.maxPowerMa?.takeIf { it > 0 }?.let { stringResource(R.string.audiopass_info_power) to stringResource(R.string.audiopass_value_power, it) },
        stringResource(R.string.audiopass_info_mic) to stringResource(if (dac.hasMic) R.string.audiopass_value_yes else R.string.audiopass_value_no),
    )
}

@Composable
private fun ColumnScope.BluetoothDetails(state: BluetoothCodecWatcher.State, onPermission: () -> Unit) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermission() }
    when (state) {
        is BluetoothCodecWatcher.State.Known -> {
            InfoRows(bluetoothRows(state.device, state.codec))
            Spacer(Modifier.height(14.dp))
            Note(stringResource(if (state.codec.lossless) R.string.audiopass_bt_lossless else R.string.audiopass_bt_reencoded))
            val better = state.codec.better
            val ldac = state.codec.ldac?.takeIf { it != LdacQuality.HIGH }
            if (better != null || ldac != null) {
                Spacer(Modifier.height(10.dp))
                Note(
                    if (better != null) stringResource(R.string.audiopass_bt_better, better, state.codec.name)
                    else stringResource(R.string.audiopass_bt_ldac_low, ldacLabel(ldac!!)),
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
                val developerOn = remember { developerOptionsOn(context) }
                if (developerOn) {
                    PillButton(stringResource(R.string.audiopass_bt_open_developer)) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                } else {
                    Note(stringResource(R.string.audiopass_bt_developer_off))
                }
            }
        }
        is BluetoothCodecWatcher.State.NeedsPermission -> {
            InfoRows(listOf(stringResource(R.string.audiopass_info_headphones) to state.device))
            Spacer(Modifier.height(14.dp))
            Note(stringResource(R.string.audiopass_bt_permission_hint))
            Spacer(Modifier.height(10.dp))
            PillButton(stringResource(R.string.audiopass_bt_permission)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        is BluetoothCodecWatcher.State.Unknown -> {
            InfoRows(listOf(
                stringResource(R.string.audiopass_info_headphones) to state.device,
                stringResource(R.string.audiopass_info_codec) to stringResource(R.string.audiopass_value_unknown),
            ))
            Spacer(Modifier.height(14.dp))
            Note(stringResource(R.string.audiopass_bt_unknown_hint))
        }
        is BluetoothCodecWatcher.State.LeAudio -> {
            InfoRows(listOf(
                stringResource(R.string.audiopass_info_headphones) to state.device,
                stringResource(R.string.audiopass_info_codec) to stringResource(R.string.audiopass_value_le_audio),
            ))
            Spacer(Modifier.height(14.dp))
            Note(stringResource(R.string.audiopass_bt_reencoded))
        }
        BluetoothCodecWatcher.State.None -> Note(stringResource(R.string.audiopass_bt_reencoded))
    }
}

@Composable
private fun bluetoothRows(device: String, codec: BluetoothCodec): List<Pair<String, String>> = listOfNotNull(
    stringResource(R.string.audiopass_info_headphones) to device,
    stringResource(R.string.audiopass_info_codec) to listOfNotNull(
        codec.name,
        codec.sampleRate?.let { stringResource(R.string.audiopass_value_khz, khz(it)) },
        codec.bits?.let { stringResource(R.string.audiopass_value_bits, it.toString()) },
    ).joinToString(" · "),
    codec.ldac?.let { stringResource(R.string.audiopass_info_quality) to ldacLabel(it) },
    codec.alternatives.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.audiopass_info_also) to it.joinToString(", ") },
)

@Composable
private fun ldacLabel(quality: LdacQuality): String =
    quality.kbps?.let { stringResource(R.string.audiopass_value_kbps, it) } ?: stringResource(R.string.audiopass_value_adaptive)

private fun developerOptionsOn(context: Context): Boolean =
    Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

/** a key and its value per line, in the monospace the mocks use for raw facts */
@Composable
private fun InfoRows(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { (key, value) ->
            Row {
                Text(
                    key.uppercase(),
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 0.04.em,
                    color = LabColors.Faint, modifier = Modifier.width(104.dp),
                )
                Text(value, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = Color(0xFFD1D1D6))
            }
        }
    }
}

@Composable
private fun Note(text: String, color: Color = LabColors.Secondary) {
    Text(text, fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 16.sp, color = color)
}

@Composable
private fun PillButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) Color(0xFF3A3A3F) else LabColors.Chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            color = if (enabled) Color.White else LabColors.Faint,
        )
    }
}

// ---- options -----------------------------------------------------------------------------

/** half-width card with its name over a switch, two to a row as in the mock */
@Composable
private fun OptionTile(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(LabColors.Card)
            .clickable { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(title, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp, color = Color.White)
        Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
        AppSwitch(checked = checked, onCheckedChange = onChange)
    }
}

// ---- passthrough -------------------------------------------------------------------------

@Composable
private fun PassthroughCard(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    mode: AudioPassMode,
    onMode: (AudioPassMode) -> Unit,
    status: AudioPass.Status?,
) {
    LabCard(padding = 10.dp) {
        LabToggleRow(
            title = stringResource(R.string.audiopass_passthrough),
            subtitle = stringResource(R.string.audiopass_passthrough_desc),
            checked = on,
            onCheckedChange = onChange,
        )
        LabCollapsible(on) {
            Spacer(Modifier.height(8.dp))
            LabPanel {
                Text(
                    stringResource(R.string.audiopass_modes).uppercase(),
                    fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.3.sp,
                    color = LabColors.Secondary,
                )
                AudioPassMode.entries.forEach { option ->
                    val available = Build.VERSION.SDK_INT >= option.minSdk
                    QualityRow(
                        title = stringResource(option.title),
                        description = if (available) stringResource(option.description) else stringResource(R.string.audiopass_mode_needs, androidName(option.minSdk)),
                        selected = option == mode,
                        enabled = available,
                    ) { onMode(option) }
                }
            }
            statusLine(status)?.let { line ->
                Spacer(Modifier.height(10.dp))
                Box(Modifier.padding(horizontal = 6.dp)) { Note(line, color = Color(0xFFD1D1D6)) }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** what the exclusive output is doing with the song playing now, in a sentence */
@Composable
internal fun statusLine(status: AudioPass.Status?): String? = when (status) {
    null, AudioPass.Status.Off -> null
    AudioPass.Status.Waiting -> stringResource(R.string.audiopass_now_waiting)
    is AudioPass.Status.Fell -> stringResource(R.string.audiopass_now_fell, khz(status.sampleRate), status.sourceBits.toString())
    is AudioPass.Status.Playing -> when {
        !status.exact -> stringResource(R.string.audiopass_now_inexact, status.device)
        status.lossless -> stringResource(R.string.audiopass_now_bitperfect, status.device, khz(status.sampleRate), status.bits.toString())
        else -> stringResource(R.string.audiopass_now_untouched, status.device, khz(status.sampleRate), status.bits.toString())
    }
}

// ---- direct usb --------------------------------------------------------------------------

@Composable
private fun DirectUsbCard(
    permitted: Boolean?,
    volume: AudioPass.DacVolume?,
    onAllow: () -> Unit,
    onVolume: (Double) -> Unit,
) {
    val (fixedVolume, onFixedVolume) = rememberPreference(AudioPassFixedVolumeKey, defaultValue = false)
    LabCard(padding = 10.dp) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            Text(
                stringResource(R.string.audiopass_usb_title),
                fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            when (permitted) {
                null -> Note(stringResource(R.string.audiopass_usb_no_dac))
                false -> {
                    Note(stringResource(R.string.audiopass_usb_permission_hint))
                    Spacer(Modifier.height(10.dp))
                    PillButton(stringResource(R.string.audiopass_usb_permission), onClick = onAllow)
                }
                true -> Unit
            }
        }
        if (volume != null) {
            Spacer(Modifier.height(6.dp))
            LabPanel {
                // the bar moves in steps the dac really has, read back after each change
                LabSlider(
                    label = stringResource(R.string.audiopass_dac_volume),
                    valueText = stringResource(R.string.audiopass_value_db, String.format(Locale.ROOT, "%.1f", volume.currentDb)),
                    value = AudioPass.levelOf(volume),
                    onValueChange = { level -> onVolume(AudioPass.dbAt(volume, level)) },
                    valueRange = 0f..1f,
                )
                Note(stringResource(R.string.audiopass_dac_volume_desc))
            }
        }
        LabToggleRow(
            title = stringResource(R.string.audiopass_fixed_volume),
            subtitle = stringResource(R.string.audiopass_fixed_volume_desc),
            checked = fixedVolume,
            onCheckedChange = onFixedVolume,
        )
    }
}

private val AudioPassMode.title: Int
    @StringRes get() = when (this) {
        AudioPassMode.SONG_RATE -> R.string.audiopass_mode_song_rate
        AudioPassMode.BIT_PERFECT -> R.string.audiopass_mode_bit_perfect
        AudioPassMode.DIRECT_USB -> R.string.audiopass_mode_direct_usb
        AudioPassMode.EXCLUSIVE_USB -> R.string.audiopass_mode_exclusive_usb
        AudioPassMode.EXCLUSIVE_DAC -> R.string.audiopass_mode_exclusive_dac
    }

private val AudioPassMode.description: Int
    @StringRes get() = when (this) {
        AudioPassMode.SONG_RATE -> R.string.audiopass_mode_song_rate_desc
        AudioPassMode.BIT_PERFECT -> R.string.audiopass_mode_bit_perfect_desc
        AudioPassMode.DIRECT_USB -> R.string.audiopass_mode_direct_usb_desc
        AudioPassMode.EXCLUSIVE_USB -> R.string.audiopass_mode_exclusive_usb_desc
        AudioPassMode.EXCLUSIVE_DAC -> R.string.audiopass_mode_exclusive_dac_desc
    }

internal fun androidName(sdk: Int): String = when (sdk) {
    27 -> "8.1"
    34 -> "14"
    else -> "7"
}

// ---- blockers ----------------------------------------------------------------------------

@Composable
private fun BlockersCard(active: Set<PassBlocker>, lossyToo: Boolean, canRestore: Boolean, onClear: () -> Unit, onRestore: () -> Unit) {
    LabCard(padding = 20.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                stringResource(if (active.isEmpty()) R.string.audiopass_blockers_clear_title else R.string.audiopass_blockers_title),
                fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp,
                color = Color.White, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            PillButton(stringResource(R.string.audiopass_blockers_button), enabled = active.isNotEmpty(), onClick = onClear)
        }
        Spacer(Modifier.height(12.dp))
        PassBlocker.entries.forEach { blocker ->
            val on = blocker in active
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) Color.White else LabColors.Faint))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(blocker.label),
                    fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = if (on) Color.White else LabColors.Faint,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Note(stringResource(if (lossyToo) R.string.audiopass_blocker_lossy_direct else R.string.audiopass_blocker_lossy))
        // what "turn these off" switched off, waiting to come back exactly as it was
        if (canRestore) {
            Spacer(Modifier.height(14.dp))
            PillButton(stringResource(R.string.audiopass_blockers_restore), onClick = onRestore)
        }
    }
}

internal val PassBlocker.label: Int
    @StringRes get() = when (this) {
        PassBlocker.EQUALIZER -> R.string.audiopass_blocker_equalizer
        PassBlocker.AUDIO_FX -> R.string.audiopass_blocker_fx
        PassBlocker.VOLUME -> R.string.audiopass_blocker_volume
        PassBlocker.NORMALIZATION -> R.string.audiopass_blocker_normalization
        PassBlocker.SPEED -> R.string.audiopass_blocker_speed
        PassBlocker.TRANSITIONS -> R.string.audiopass_blocker_transitions
        PassBlocker.SILENCE_OR_HAPTICS -> R.string.audiopass_blocker_silence
    }

// ---- numbers -----------------------------------------------------------------------------

internal fun khz(rate: Int): String =
    if (rate % 1000 == 0) "${rate / 1000}" else String.format(Locale.ROOT, "%.1f", rate / 1000.0)

/** "44.1–384", or one number when there's only one */
private fun rateSpan(rates: List<Int>): String {
    val low = rates.minOrNull() ?: return ""
    val high = rates.max()
    return if (low == high) khz(low) else "${khz(low)}–${khz(high)}"
}
