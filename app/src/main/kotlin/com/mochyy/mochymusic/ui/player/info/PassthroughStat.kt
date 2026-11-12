package com.mochyy.mochymusic.ui.player.info

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AudioPassMode
import com.mochyy.mochymusic.constants.AudioPassModeKey
import com.mochyy.mochymusic.constants.UsbPassthroughKey
import com.mochyy.mochymusic.playback.MusicService
import com.mochyy.mochymusic.playback.audio.AudioOutputs
import com.mochyy.mochymusic.playback.audio.pass.AudioPass
import com.mochyy.mochymusic.ui.screens.audiolab.androidName
import com.mochyy.mochymusic.ui.screens.audiolab.label
import com.mochyy.mochymusic.ui.screens.audiolab.statusLine
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference

// the output card's line on passthrough: straight to the dac, or why this song wasn't (issue 235 had no way to tell)
@Composable
fun rememberPassthroughStat(service: MusicService, device: AudioOutputs.Device?): Pair<String, String>? {
    val (on) = rememberPreference(UsbPassthroughKey, defaultValue = false)
    val (mode) = rememberEnumPreference(AudioPassModeKey, defaultValue = AudioPassMode.Default)
    val status by service.audioPass.status.collectAsState()
    val skip by service.audioPass.skip.collectAsState()
    val blockers by service.passBlockers.collectAsState()
    // off and nothing to pass to, there's nothing worth a line
    if (!on && device?.kind != AudioOutputs.Kind.USB) return null
    val resources = LocalContext.current.resources
    val value = when {
        !on -> stringResource(R.string.audiopass_stat_off)
        !mode.exclusive -> stringResource(R.string.audiopass_stat_song_rate)
        Build.VERSION.SDK_INT < mode.minSdk -> stringResource(R.string.audiopass_stat_old_android, androidName(mode.minSdk))
        blockers.isNotEmpty() -> stringResource(
            R.string.audiopass_stat_blocked,
            blockers.joinToString(", ") { resources.getString(it.label) },
        )
        status is AudioPass.Status.Playing || status is AudioPass.Status.Fell -> statusLine(status).orEmpty()
        else -> stringResource(skip?.message ?: R.string.audiopass_stat_unknown)
    }
    return stringResource(R.string.audiopass_stat_label) to value
}

private val AudioPass.Skip.message: Int
    get() = when (this) {
        AudioPass.Skip.Busy -> R.string.audiopass_stat_skip_busy
        AudioPass.Skip.Format -> R.string.audiopass_stat_skip_format
        AudioPass.Skip.Lossy -> R.string.audiopass_stat_skip_lossy
        AudioPass.Skip.FellBefore -> R.string.audiopass_stat_skip_fell
        AudioPass.Skip.NoDac -> R.string.audiopass_stat_skip_no_dac
        AudioPass.Skip.NoUsbAccess -> R.string.audiopass_stat_skip_no_access
        AudioPass.Skip.DacCantPlay -> R.string.audiopass_stat_skip_dac_cant
    }
