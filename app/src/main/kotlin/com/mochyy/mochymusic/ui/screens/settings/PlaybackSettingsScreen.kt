package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.DontSkipOnFailureKey
import com.mochyy.mochymusic.constants.MixWithOtherAppsKey
import com.mochyy.mochymusic.constants.PauseOnMute
import com.mochyy.mochymusic.constants.PersistentQueueKey
import com.mochyy.mochymusic.constants.PersistentShuffleAcrossQueuesKey
import com.mochyy.mochymusic.constants.ResumeOnBluetoothConnectKey
import com.mochyy.mochymusic.constants.SkipSilenceKey
import com.mochyy.mochymusic.constants.StopPlaybackOnTaskRemovedKey
import com.mochyy.mochymusic.constants.StreamFailureTimeoutKey
import com.mochyy.mochymusic.ui.component.DefaultPlayerSeekBarSlider
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.SubSettingsSearchBar
import com.mochyy.mochymusic.utils.rememberPreference

const val PlaybackSettingsRoute = "playback_settings"

// settings > playback: how music keeps going, shares the speaker, and what happens when a song won't load
@Composable
fun PlaybackSettingsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_playback_category),
        onBack = { navController.navigateUp() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        SettingsSection(stringResource(R.string.settings_section_listening), listeningItems().matching(query))
        SettingsSection(stringResource(R.string.settings_section_failures), failureItems().matching(query))
        SettingsSection(stringResource(R.string.settings_related), relatedItems(navController).matching(query))
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun listeningItems(): List<SettingsItem> {
    val (stopOnTaskRemoved, onStopOnTaskRemoved) = rememberPreference(StopPlaybackOnTaskRemovedKey, defaultValue = false)
    val (mixWithOtherApps, onMixWithOtherAppsChange) = rememberPreference(MixWithOtherAppsKey, defaultValue = false)
    val (resumeOnBluetooth, onResumeOnBluetoothChange) = rememberPreference(ResumeOnBluetoothConnectKey, defaultValue = false)
    val (pauseOnMute, onPauseOnMuteChange) = rememberPreference(PauseOnMute, defaultValue = false)
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(PersistentQueueKey, defaultValue = true)
    val (persistentShuffle, onPersistentShuffleChange) = rememberPreference(PersistentShuffleAcrossQueuesKey, defaultValue = true)
    val (skipSilence, onSkipSilenceChange) = rememberPreference(SkipSilenceKey, defaultValue = false)
    return listOf(
        switchItem(
            key = "Keep playing even when closed",
            title = stringResource(R.string.playback_keep_playing),
            description = stringResource(R.string.playback_keep_playing_desc),
            detail = stringResource(R.string.playback_keep_playing_detail),
            icon = painterResource(R.drawable.clear_all),
            checked = !stopOnTaskRemoved,
            onCheckedChange = { onStopOnTaskRemoved(!it) },
        ),
        switchItem(
            key = "Play alongside other apps",
            title = stringResource(R.string.playback_mix_other_apps),
            description = stringResource(R.string.playback_mix_other_apps_desc),
            detail = stringResource(R.string.playback_mix_other_apps_detail),
            icon = painterResource(R.drawable.volume_up),
            checked = mixWithOtherApps,
            onCheckedChange = onMixWithOtherAppsChange,
        ),
        switchItem(
            key = "Resume on Bluetooth connection",
            title = stringResource(R.string.playback_resume_bluetooth),
            description = stringResource(R.string.playback_resume_bluetooth_desc),
            icon = painterResource(R.drawable.bluetooth),
            checked = resumeOnBluetooth,
            onCheckedChange = onResumeOnBluetoothChange,
        ),
        switchItem(
            key = "Pause when muted",
            title = stringResource(R.string.pause_music_when_media_is_muted),
            description = stringResource(R.string.pause_music_when_muted_desc),
            icon = painterResource(R.drawable.volume_off),
            checked = pauseOnMute,
            onCheckedChange = onPauseOnMuteChange,
        ),
        switchItem(
            key = "Save Player's last state",
            title = stringResource(R.string.playback_save_state),
            description = stringResource(R.string.playback_save_state_desc),
            detail = stringResource(R.string.playback_save_state_detail),
            icon = painterResource(R.drawable.cached),
            checked = persistentQueue,
            onCheckedChange = onPersistentQueueChange,
        ),
        // shuffle stays on from one playlist to the next, like spotify, until it's turned off
        switchItem(
            key = "Keep shuffle on",
            title = stringResource(R.string.settings_persistent_shuffle),
            description = stringResource(R.string.settings_persistent_shuffle_desc),
            icon = painterResource(R.drawable.shuffle),
            checked = persistentShuffle,
            onCheckedChange = onPersistentShuffleChange,
        ),
        switchItem(
            key = "Skip Silence",
            title = stringResource(R.string.playback_skip_silence),
            description = stringResource(R.string.playback_skip_silence_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = skipSilence,
            onCheckedChange = onSkipSilenceChange,
        ),
    )
}

@Composable
private fun failureItems(): List<SettingsItem> {
    val (dontSkipOnFailure, onDontSkipOnFailureChange) = rememberPreference(DontSkipOnFailureKey, defaultValue = false)
    val (failureTimeout, onFailureTimeoutChange) = rememberPreference(StreamFailureTimeoutKey, defaultValue = 6)
    return listOf(
        switchItem(
            key = "Don't skip on failure",
            title = stringResource(R.string.settings_dont_skip_failure),
            description = stringResource(R.string.settings_dont_skip_failure_desc),
            detail = stringResource(R.string.settings_dont_skip_failure_detail),
            icon = painterResource(R.drawable.skip_next),
            checked = dontSkipOnFailure,
            onCheckedChange = onDontSkipOnFailureChange,
        ),
        SettingsItem(
            title = { ValueTitle(stringResource(R.string.settings_skip_timeout), stringResource(R.string.settings_seconds_short, failureTimeout)) },
            highlightKey = "Skip timeout failure",
            searchText = stringResource(R.string.settings_skip_timeout),
            description = {
                DefaultPlayerSeekBarSlider(
                    value = failureTimeout.toFloat(),
                    onValueChange = { onFailureTimeoutChange(it.toInt()) },
                    valueRange = 5f..20f,
                    steps = 14,
                    modifier = Modifier.padding(top = 8.dp),
                )
            },
            icon = painterResource(R.drawable.speed),
            iconShape = CircleShape,
            isVisible = !dontSkipOnFailure,
            isSubOption = true,
        ),
    )
}

// the sound side of playback lives in audio lab, these take people there
@Composable
private fun relatedItems(navController: NavController): List<SettingsItem> = listOf(
    linkItem(
        key = "Transition crossfade",
        title = stringResource(R.string.transition_title),
        description = stringResource(R.string.settings_transition_link_desc),
        icon = painterResource(R.drawable.linear_scale),
        onClick = { navController.navigate("crossmix_settings") },
    ),
    linkItem(
        key = "Audio Pass output usb dac",
        title = stringResource(R.string.audiopass_hub_title),
        description = stringResource(R.string.settings_audio_pass_desc),
        icon = rememberVectorPainter(Icons.Rounded.GraphicEq),
        onClick = { navController.navigate("audio_pass") },
    ),
)
