package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.CrossmixTestToolsKey
import com.mochyy.mochymusic.constants.ForcePlaybackKey
import com.mochyy.mochymusic.constants.ForceYtBackdropBlur1500Key
import com.mochyy.mochymusic.constants.HapticFocus
import com.mochyy.mochymusic.constants.HapticFocusKey
import com.mochyy.mochymusic.constants.HapticSensitivity
import com.mochyy.mochymusic.constants.MusicHapticsEnabledKey
import com.mochyy.mochymusic.constants.MusicHapticsSensitivityKey
import com.mochyy.mochymusic.constants.SetupCompletedKey
import com.mochyy.mochymusic.constants.ShowStreamSourceToastKey
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalSettingsScreen(navController: NavController) {
    val (musicHapticsEnabled, onMusicHapticsEnabledChange) = rememberPreference(MusicHapticsEnabledKey, defaultValue = false)
    val (musicHapticsSensitivity, onMusicHapticsSensitivityChange) =
        rememberEnumPreference(
            MusicHapticsSensitivityKey,
            defaultValue = HapticSensitivity.MEDIUM,
        )
    val (hapticFocus, onHapticFocusChange) = rememberEnumPreference(HapticFocusKey, defaultValue = HapticFocus.VIBE)
    val (showBigDiscStyles, onShowBigDiscStylesChange) =
        rememberPreference(
            com.mochyy.mochymusic.constants.ShowBigDiscStylesKey,
            defaultValue = false,
        )
    val (forceRecap, onForceRecapChange) =
        rememberPreference(
            com.mochyy.mochymusic.constants.ForceMonthlyRecapKey,
            defaultValue = false,
        )
    val (forceYtBackdropBlur1500, onForceYtBackdropBlur1500Change) =
        rememberPreference(
            ForceYtBackdropBlur1500Key,
            defaultValue = false,
        )

    val (crossmixTestTools, onCrossmixTestToolsChange) =
        rememberPreference(
            CrossmixTestToolsKey,
            defaultValue = false,
        )
    val crossmixTools = crossmixTestItems(isVisible = crossmixTestTools)
    val (sourceBanner, onSourceBannerChange) = rememberPreference(ShowStreamSourceToastKey, defaultValue = false)
    val (forcePlayback, onForcePlaybackChange) = rememberPreference(ForcePlaybackKey, defaultValue = false)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    SubSettingsScaffold(
        title = stringResource(R.string.experimental_settings_title),
        onBack = { navController.navigateUp() },
    ) {
        SettingsGroup(
            style = SettingsGroupStyle.Grouped,
            items =
                buildList {
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.experimental_import_sync_title)) },
                            highlightKey = "Import & sync",
                            descriptionText = stringResource(R.string.experimental_import_sync_desc),
                            icon = painterResource(R.drawable.sync),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { navController.navigate(com.mochyy.mochymusic.ui.screens.settings.importsync.ImportSyncRoute) },
                        ),
                    )
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.experimental_crossmix_tools_title)) },
                            highlightKey = "Crossmix test tools",
                            descriptionText = stringResource(R.string.experimental_crossmix_tools_desc),
                            detail = stringResource(R.string.experimental_crossmix_tools_detail),
                            icon = painterResource(R.drawable.graphic_eq),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onCrossmixTestToolsChange(!crossmixTestTools) },
                            trailingContent = {
                                AppSwitch(
                                    checked = crossmixTestTools,
                                    onCheckedChange = onCrossmixTestToolsChange,
                                )
                            },
                        ),
                    )
                    crossmixTools.forEach { add(it.copy(isSubOption = true)) }
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.experimental_show_stream_source_title)) },
                            highlightKey = "Show where songs play from",
                            descriptionText = stringResource(R.string.experimental_show_stream_source_desc),
                            detail = stringResource(R.string.experimental_show_stream_source_detail),
                            icon = painterResource(R.drawable.info),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onSourceBannerChange(!sourceBanner) },
                            trailingContent = {
                                AppSwitch(checked = sourceBanner, onCheckedChange = onSourceBannerChange)
                            },
                        ),
                    )
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.experimental_force_playback_title)) },
                            highlightKey = "Force playback",
                            descriptionText = stringResource(R.string.experimental_force_playback_desc),
                            detail = stringResource(R.string.experimental_force_playback_detail),
                            icon = painterResource(R.drawable.play),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onForcePlaybackChange(!forcePlayback) },
                            trailingContent = {
                                AppSwitch(checked = forcePlayback, onCheckedChange = onForcePlaybackChange)
                            },
                        ),
                    )
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.experimental_force_yt_blur_title)) },
                            highlightKey = "Force 1500 px YouTube blur",
                            descriptionText = stringResource(R.string.experimental_force_yt_blur_desc),
                            icon = painterResource(R.drawable.tune),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onForceYtBackdropBlur1500Change(!forceYtBackdropBlur1500) },
                            trailingContent = {
                                AppSwitch(
                                    checked = forceYtBackdropBlur1500,
                                    onCheckedChange = onForceYtBackdropBlur1500Change,
                                )
                            },
                        ),
                    )
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.experimental_force_recap_title)) },
                            highlightKey = "Force show monthly recap",
                            descriptionText = stringResource(R.string.experimental_force_recap_desc),
                            detail = stringResource(R.string.experimental_force_recap_detail),
                            icon = painterResource(R.drawable.history),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onForceRecapChange(!forceRecap) },
                            trailingContent = {
                                AppSwitch(
                                    checked = forceRecap,
                                    onCheckedChange = onForceRecapChange,
                                )
                            },
                        ),
                    )
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.experimental_big_disc_title)) },
                            highlightKey = "Big disc cover styles",
                            descriptionText = stringResource(R.string.experimental_big_disc_desc),
                            icon = painterResource(R.drawable.album),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onShowBigDiscStylesChange(!showBigDiscStyles) },
                            trailingContent = {
                                AppSwitch(
                                    checked = showBigDiscStyles,
                                    onCheckedChange = onShowBigDiscStylesChange,
                                )
                            },
                        ),
                    )
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.experimental_music_haptics_title)) },
                            highlightKey = "Music haptics",
                            descriptionText = stringResource(R.string.experimental_music_haptics_desc),
                            icon = painterResource(R.drawable.music_note),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onMusicHapticsEnabledChange(!musicHapticsEnabled) },
                            trailingContent = {
                                AppSwitch(checked = musicHapticsEnabled, onCheckedChange = onMusicHapticsEnabledChange)
                            },
                        ),
                    )
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.experimental_haptic_sensitivity_title)) },
                            description = {
                                Text(
                                    when (musicHapticsSensitivity) {
                                        HapticSensitivity.LOW -> stringResource(R.string.experimental_haptic_sensitivity_low)
                                        HapticSensitivity.MEDIUM -> stringResource(R.string.experimental_haptic_sensitivity_medium)
                                        HapticSensitivity.HIGH -> stringResource(R.string.experimental_haptic_sensitivity_high)
                                    },
                                )
                            },
                            icon = painterResource(R.drawable.sliders),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            enabled = musicHapticsEnabled,
                            onClick = {
                                onMusicHapticsSensitivityChange(
                                    when (musicHapticsSensitivity) {
                                        HapticSensitivity.LOW -> HapticSensitivity.MEDIUM
                                        HapticSensitivity.MEDIUM -> HapticSensitivity.HIGH
                                        HapticSensitivity.HIGH -> HapticSensitivity.LOW
                                    },
                                )
                            },
                        ),
                    )
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.experimental_haptic_focus_title)) },
                            description = {
                                Text(
                                    when (hapticFocus) {
                                        HapticFocus.BALANCE -> stringResource(R.string.experimental_haptic_focus_balance)
                                        HapticFocus.VOCAL -> stringResource(R.string.experimental_haptic_focus_vocal)
                                        HapticFocus.VIBE -> stringResource(R.string.experimental_haptic_focus_vibe)
                                        HapticFocus.BASS -> stringResource(R.string.experimental_haptic_focus_bass)
                                    },
                                )
                            },
                            icon = painterResource(R.drawable.equalizer),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            enabled = musicHapticsEnabled,
                            onClick = {
                                onHapticFocusChange(
                                    when (hapticFocus) {
                                        HapticFocus.BALANCE -> HapticFocus.VOCAL
                                        HapticFocus.VOCAL -> HapticFocus.VIBE
                                        HapticFocus.VIBE -> HapticFocus.BASS
                                        HapticFocus.BASS -> HapticFocus.BALANCE
                                    },
                                )
                            },
                        ),
                    )
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.experimental_cipher_title)) },
                            highlightKey = "Cipher",
                            descriptionText = stringResource(R.string.experimental_cipher_desc),
                            icon = painterResource(R.drawable.key),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { navController.navigate("cipher_settings") },
                        ),
                    )
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.experimental_playback_diagnostics_title)) },
                            highlightKey = "Playback diagnostics",
                            descriptionText = stringResource(R.string.experimental_playback_diagnostics_desc),
                            icon = painterResource(R.drawable.info),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { navController.navigate("playback_diagnostics") },
                        ),
                    )
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.experimental_repeat_setup_title)) },
                            highlightKey = "Repeat initial setup",
                            descriptionText = stringResource(R.string.experimental_repeat_setup_desc),
                            icon = painterResource(R.drawable.restore),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = {
                                coroutineScope.launch {
                                    context.dataStore.updateData { prefs ->
                                        prefs.toMutablePreferences().apply {
                                            set(SetupCompletedKey, false)
                                        }
                                    }
                                }
                            },
                        ),
                    )
                },
        )
    }
}
