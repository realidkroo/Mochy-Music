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
import com.mochyy.mochymusic.ui.component.AppSwitch
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
import com.mochyy.mochymusic.constants.ShowStreamSourceToastKey
import com.mochyy.mochymusic.constants.ForceYtBackdropBlur1500Key
import com.mochyy.mochymusic.constants.HapticFocus
import com.mochyy.mochymusic.constants.HapticFocusKey
import com.mochyy.mochymusic.constants.HapticSensitivity
import com.mochyy.mochymusic.constants.MusicHapticsEnabledKey
import com.mochyy.mochymusic.constants.MusicHapticsSensitivityKey
import com.mochyy.mochymusic.constants.SetupCompletedKey
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalSettingsScreen(navController: NavController) {
    val (musicHapticsEnabled, onMusicHapticsEnabledChange) = rememberPreference(MusicHapticsEnabledKey, defaultValue = false)
    val (musicHapticsSensitivity, onMusicHapticsSensitivityChange) = rememberEnumPreference(MusicHapticsSensitivityKey, defaultValue = HapticSensitivity.MEDIUM)
    val (hapticFocus, onHapticFocusChange) = rememberEnumPreference(HapticFocusKey, defaultValue = HapticFocus.VIBE)
    val (showBigDiscStyles, onShowBigDiscStylesChange) = rememberPreference(
        com.mochyy.mochymusic.constants.ShowBigDiscStylesKey,
        defaultValue = false,
    )
    val (forceRecap, onForceRecapChange) = rememberPreference(
        com.mochyy.mochymusic.constants.ForceMonthlyRecapKey,
        defaultValue = false,
    )
    val (forceYtBackdropBlur1500, onForceYtBackdropBlur1500Change) = rememberPreference(
        ForceYtBackdropBlur1500Key,
        defaultValue = false,
    )

    val (crossmixTestTools, onCrossmixTestToolsChange) = rememberPreference(
        CrossmixTestToolsKey,
        defaultValue = false,
    )
    val crossmixTools = crossmixTestItems(isVisible = crossmixTestTools)
    val (sourceBanner, onSourceBannerChange) = rememberPreference(ShowStreamSourceToastKey, defaultValue = false)
    val (forcePlayback, onForcePlaybackChange) = rememberPreference(ForcePlaybackKey, defaultValue = false)
    val (streamBackend, onStreamBackendChange) = rememberEnumPreference(
        com.mochyy.mochymusic.constants.StreamBackendKey,
        defaultValue = com.mochyy.mochymusic.constants.StreamBackend.INNERTUBEX,
    )
    val streamBackendTitle = stringResource(R.string.experimental_stream_backend)
    // off by default: the whole app stays upright until the wide layout is ready
    val (landscapeUnlocked, onLandscapeUnlockedChange) = rememberPreference(
        com.mochyy.mochymusic.constants.LandscapeUnlockedKey,
        defaultValue = false,
    )
    val unlockLandscapeTitle = stringResource(R.string.experimental_unlock_landscape)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    SubSettingsScaffold(
        title = "Experimental",
        onBack = { navController.navigateUp() },
    ) {
            SettingsGroup(
                style = SettingsGroupStyle.Grouped,
                items = buildList {
                    add(
                        SettingsItem(
                            title = { Text(unlockLandscapeTitle) },
                            highlightKey = unlockLandscapeTitle,
                            descriptionText = stringResource(R.string.experimental_unlock_landscape_desc),
                            detail = stringResource(R.string.experimental_unlock_landscape_detail),
                            icon = painterResource(R.drawable.screen_rotation),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onLandscapeUnlockedChange(!landscapeUnlocked) },
                            trailingContent = {
                                AppSwitch(checked = landscapeUnlocked, onCheckedChange = onLandscapeUnlockedChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Import & sync") },
                            highlightKey = "Import & sync",
                            descriptionText = "Bring playlists from other services, sync with YouTube, export a backup",
                            icon = painterResource(R.drawable.sync),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { navController.navigate(com.mochyy.mochymusic.ui.screens.settings.importsync.ImportSyncRoute) }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Crossmix test tools") },
                            highlightKey = "Crossmix test tools",
                            descriptionText = "Live status and a jump-to-mix button",
                            detail = "Live status and a button that jumps to just before the next mix; also shown in Crossmix",
                            icon = painterResource(R.drawable.graphic_eq),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onCrossmixTestToolsChange(!crossmixTestTools) },
                            trailingContent = {
                                AppSwitch(
                                    checked = crossmixTestTools,
                                    onCheckedChange = onCrossmixTestToolsChange,
                                )
                            }
                        )
                    )
                    crossmixTools.forEach { add(it.copy(isSubOption = true)) }
                    add(
                        SettingsItem(
                            title = { Text("Show where songs play from") },
                            highlightKey = "Show where songs play from",
                            descriptionText = "Banner showing each song's source and quality",
                            detail = "A banner at the top names each song's source: Apple Music, Monochrome or YouTube, and its quality",
                            icon = painterResource(R.drawable.info),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onSourceBannerChange(!sourceBanner) },
                            trailingContent = {
                                AppSwitch(checked = sourceBanner, onCheckedChange = onSourceBannerChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text(streamBackendTitle) },
                            highlightKey = streamBackendTitle,
                            description = {
                                Text(
                                    stringResource(
                                        when (streamBackend) {
                                            com.mochyy.mochymusic.constants.StreamBackend.INNERTUBEX -> R.string.experimental_stream_backend_innertubex
                                            com.mochyy.mochymusic.constants.StreamBackend.CLASSIC -> R.string.experimental_stream_backend_classic
                                        }
                                    )
                                )
                            },
                            detail = stringResource(R.string.experimental_stream_backend_detail),
                            icon = painterResource(R.drawable.key),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = {
                                onStreamBackendChange(
                                    when (streamBackend) {
                                        com.mochyy.mochymusic.constants.StreamBackend.INNERTUBEX -> com.mochyy.mochymusic.constants.StreamBackend.CLASSIC
                                        com.mochyy.mochymusic.constants.StreamBackend.CLASSIC -> com.mochyy.mochymusic.constants.StreamBackend.INNERTUBEX
                                    }
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Force playback") },
                            highlightKey = "Force playback",
                            descriptionText = "If a provider fails, try the next one",
                            detail = "Never stop on a song: if one provider can't play it, the next in the provider order does; if none can, it's skipped. A banner says which",
                            icon = painterResource(R.drawable.play),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onForcePlaybackChange(!forcePlayback) },
                            trailingContent = {
                                AppSwitch(checked = forcePlayback, onCheckedChange = onForcePlaybackChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Force 1500 px YouTube blur") },
                            highlightKey = "Force 1500 px YouTube blur",
                            descriptionText = "Override the 120 px default; may increase GPU load",
                            icon = painterResource(R.drawable.tune),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onForceYtBackdropBlur1500Change(!forceYtBackdropBlur1500) },
                            trailingContent = {
                                AppSwitch(
                                    checked = forceYtBackdropBlur1500,
                                    onCheckedChange = onForceYtBackdropBlur1500Change,
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Force show monthly recap") },
                            highlightKey = "Force show monthly recap",
                            descriptionText = "Unlock the recap early",
                            detail = "Unlock the recap behind the Mochy card now, before the 3rd or 10 songs",
                            icon = painterResource(R.drawable.history),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onForceRecapChange(!forceRecap) },
                            trailingContent = {
                                AppSwitch(
                                    checked = forceRecap,
                                    onCheckedChange = onForceRecapChange,
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Big disc cover styles") },
                            highlightKey = "Big disc cover styles",
                            descriptionText = "Unfinished — oversized discs that bleed off screen",
                            icon = painterResource(R.drawable.album),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onShowBigDiscStylesChange(!showBigDiscStyles) },
                            trailingContent = {
                                AppSwitch(
                                    checked = showBigDiscStyles,
                                    onCheckedChange = onShowBigDiscStylesChange,
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Music haptics") },
                            highlightKey = "Music haptics",
                            descriptionText = "Vibrate to the beat — uses battery",
                            icon = painterResource(R.drawable.music_note),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { onMusicHapticsEnabledChange(!musicHapticsEnabled) },
                            trailingContent = {
                                AppSwitch(checked = musicHapticsEnabled, onCheckedChange = onMusicHapticsEnabledChange)
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Haptics sensitivity") },
                            description = {
                                Text(
                                    when (musicHapticsSensitivity) {
                                        HapticSensitivity.LOW -> "Low"
                                        HapticSensitivity.MEDIUM -> "Medium"
                                        HapticSensitivity.HIGH -> "High"
                                    }
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
                                    }
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Haptic focus") },
                            description = {
                                Text(
                                    when (hapticFocus) {
                                        HapticFocus.BALANCE -> "Balance"
                                        HapticFocus.VOCAL -> "Focus on vocal"
                                        HapticFocus.VIBE -> "Focus on vibe"
                                        HapticFocus.BASS -> "Focus on bass"
                                    }
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
                                    }
                                )
                            }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Cipher") },
                            highlightKey = "Cipher",
                            descriptionText = "Player script status, countdown and refresh",
                            icon = painterResource(R.drawable.key),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { navController.navigate("cipher_settings") }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Playback diagnostics") },
                            highlightKey = "Playback diagnostics",
                            descriptionText = "Which client served each track, and PoToken state",
                            icon = painterResource(R.drawable.info),
                            iconShape = androidx.compose.foundation.shape.CircleShape,
                            onClick = { navController.navigate("playback_diagnostics") }
                        )
                    )
                    add(
                        SettingsItem(
                            title = { Text("Repeat initial setup") },
                            highlightKey = "Repeat initial setup",
                            descriptionText = "Re-run the setup wizard",
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
                            }
                        )
                    )
                }
            )
    }
}
