package com.mochyy.mochymusic.ui.screens.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.CanvasThumbnailAnimationKey
import com.mochyy.mochymusic.constants.CanvasWifiOnlyKey
import com.mochyy.mochymusic.constants.DisableVideoAutoplayKey
import com.mochyy.mochymusic.constants.DontSkipOnFailureKey
import com.mochyy.mochymusic.constants.HiFiResamplerKey
import com.mochyy.mochymusic.constants.HiResOutputKey
import com.mochyy.mochymusic.constants.HideSettingsDescriptionsKey
import com.mochyy.mochymusic.constants.HideUpdateNoticeKey
import com.mochyy.mochymusic.constants.LocalSongAutoMetadataKey
import com.mochyy.mochymusic.constants.LyricsHighBloomKey
import com.mochyy.mochymusic.constants.LyricsMotionStyle
import com.mochyy.mochymusic.constants.LyricsMotionStyleKey
import com.mochyy.mochymusic.constants.MixWithOtherAppsKey
import com.mochyy.mochymusic.constants.OfflineModeKey
import com.mochyy.mochymusic.constants.PersistentQueueKey
import com.mochyy.mochymusic.constants.PlayVideoBackgroundKey
import com.mochyy.mochymusic.constants.PreferUsbDacKey
import com.mochyy.mochymusic.constants.SYSTEM_DEFAULT
import com.mochyy.mochymusic.constants.ShowFormatBadgeKey
import com.mochyy.mochymusic.constants.ShowFormatSampleRateKey
import com.mochyy.mochymusic.constants.SkipSilenceKey
import com.mochyy.mochymusic.constants.StopPlaybackOnTaskRemovedKey
import com.mochyy.mochymusic.constants.StreamFailureTimeoutKey
import com.mochyy.mochymusic.constants.SyncLyricsToVideoKey
import com.mochyy.mochymusic.constants.UsbPassthroughKey
import com.mochyy.mochymusic.constants.VideoQuality
import com.mochyy.mochymusic.constants.VideoQualityKey
import com.mochyy.mochymusic.constants.YouTubeCaptionTranslationKey
import com.mochyy.mochymusic.constants.YtReportPlaysKey
import com.mochyy.mochymusic.constants.YtVideoBackgroundLyricsSyncKey
import com.mochyy.mochymusic.core.updater.saveUpdateNotificationsSetting
import com.mochyy.mochymusic.i18n.AppLanguage
import com.mochyy.mochymusic.i18n.activityOrNull
import com.mochyy.mochymusic.playback.audio.AudioOutputs
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.DefaultPlayerSeekBarSlider
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsHighlight
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.SubSettingsSearchBar
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// settings > app behaviour: how the app and the player act, what used to be general, playback and most of appearance
@Composable
fun AppBehaviourSettingsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_app_behaviour),
        onBack = { navController.navigateUp() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        Section(stringResource(R.string.general_app_settings), generalItems().matching(query))
        Section(stringResource(R.string.settings_local_section), localItems().matching(query))
        Section(stringResource(R.string.settings_player_behaviour), playerBehaviourItems().matching(query))
        Section(stringResource(R.string.settings_on_the_player), playerLookItems().matching(query))
        Section(stringResource(R.string.playback_output), outputItems().matching(query))
        Section(stringResource(R.string.settings_other), otherItems().matching(query))
        Section(stringResource(R.string.settings_related), relatedItems(navController).matching(query))
        Spacer(Modifier.height(140.dp))
    }
}

// a section's title and its grouped rows, left out whole when the search hides every row
@Composable
private fun Section(title: String, items: List<SettingsItem>) {
    if (items.isEmpty()) return
    Text(
        text = title,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Color.White,
        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
    )
    SettingsGroup(style = SettingsGroupStyle.Grouped, items = items)
    Spacer(Modifier.height(18.dp))
}

private fun List<SettingsItem>.matching(query: String): List<SettingsItem> {
    if (query.isBlank()) return this
    return filter {
        it.highlightKey?.contains(query, ignoreCase = true) == true ||
            it.searchText?.contains(query, ignoreCase = true) == true ||
            it.descriptionText?.contains(query, ignoreCase = true) == true ||
            it.detail?.contains(query, ignoreCase = true) == true
    }
}

@Composable
private fun generalItems(): List<SettingsItem> {
    val context = LocalContext.current
    val (noticeHidden, onNoticeHidden) = rememberPreference(HideUpdateNoticeKey, defaultValue = false)
    val (offlineMode, onOfflineModeChange) = rememberPreference(OfflineModeKey, defaultValue = false)
    val (stopOnTaskRemoved, onStopOnTaskRemoved) = rememberPreference(StopPlaybackOnTaskRemovedKey, defaultValue = false)
    val (mixWithOtherApps, onMixWithOtherAppsChange) = rememberPreference(MixWithOtherAppsKey, defaultValue = false)
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(PersistentQueueKey, defaultValue = true)
    // the launch check's notification reads its own switch, so it follows this one too
    val setNoticeHidden = { hidden: Boolean ->
        onNoticeHidden(hidden)
        saveUpdateNotificationsSetting(context, !hidden)
    }
    return listOf(
        languageItem(),
        switchItem(
            key = "Disable App Updates notice",
            title = stringResource(R.string.settings_updates_notice),
            description = stringResource(R.string.settings_updates_notice_desc),
            icon = painterResource(R.drawable.update),
            checked = noticeHidden,
            onCheckedChange = setNoticeHidden,
        ),
        switchItem(
            key = "Offline mode",
            title = stringResource(R.string.settings_offline_mode),
            description = stringResource(R.string.settings_offline_mode_desc),
            icon = painterResource(R.drawable.download),
            checked = offlineMode,
            onCheckedChange = onOfflineModeChange,
        ),
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
            key = "Save Player's last state",
            title = stringResource(R.string.playback_save_state),
            description = stringResource(R.string.playback_save_state_desc),
            detail = stringResource(R.string.playback_save_state_detail),
            icon = painterResource(R.drawable.cached),
            checked = persistentQueue,
            onCheckedChange = onPersistentQueueChange,
        ),
    )
}

// the app's own language, apart from the phone's; android 13+ keeps it in the system's per-app setting too
@Composable
private fun languageItem(): SettingsItem {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = remember(context) { context.activityOrNull() }
    val language = remember(context) { AppLanguage.selection(context) }
    val languageName = if (language == SYSTEM_DEFAULT) stringResource(R.string.system_default) else appLanguageNativeName(language)
    val failure = stringResource(R.string.app_language_update_failed)
    val scope = rememberCoroutineScope()
    if (showDialog) {
        AppLanguageDialog(
            selected = language,
            onDismiss = { showDialog = false },
            onSelect = { tag ->
                showDialog = false
                if (tag != language && activity != null) {
                    scope.launch {
                        try {
                            AppLanguage.select(activity, tag)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
        )
    }
    return SettingsItem(
        title = { Text(stringResource(R.string.app_language)) },
        highlightKey = "App language",
        descriptionText = languageName,
        searchText = stringResource(R.string.app_language),
        icon = painterResource(R.drawable.translate),
        iconShape = CircleShape,
        enabled = activity != null,
        onClick = { showDialog = true },
    )
}

@Composable
private fun localItems(): List<SettingsItem> {
    val (autoMetadata, onAutoMetadataChange) = rememberPreference(LocalSongAutoMetadataKey, defaultValue = true)
    return listOf(
        switchItem(
            key = "Local song auto metadata",
            title = stringResource(R.string.settings_local_auto_metadata),
            description = stringResource(R.string.settings_local_auto_metadata_desc),
            icon = painterResource(R.drawable.cached),
            checked = autoMetadata,
            onCheckedChange = onAutoMetadataChange,
        ),
    )
}

@Composable
private fun playerBehaviourItems(): List<SettingsItem> {
    val (disableVideoAutoplay, onDisableVideoAutoplayChange) = rememberPreference(DisableVideoAutoplayKey, defaultValue = false)
    val (youtubeTranslations, onYoutubeTranslationsChange) = rememberPreference(YouTubeCaptionTranslationKey, defaultValue = true)
    val (syncLyricsToVideo, onSyncLyricsToVideoChange) = rememberPreference(SyncLyricsToVideoKey, defaultValue = true)
    val (dontSkipOnFailure, onDontSkipOnFailureChange) = rememberPreference(DontSkipOnFailureKey, defaultValue = false)
    val (failureTimeout, onFailureTimeoutChange) = rememberPreference(StreamFailureTimeoutKey, defaultValue = 6)
    val (skipSilence, onSkipSilenceChange) = rememberPreference(SkipSilenceKey, defaultValue = false)
    val (reportPlays, onReportPlaysChange) = rememberPreference(YtReportPlaysKey, defaultValue = true)
    return listOf(
        switchItem(
            key = "Disable auto-playing YouTube video",
            title = stringResource(R.string.settings_disable_video_autoplay),
            description = stringResource(R.string.settings_disable_video_autoplay_desc),
            icon = painterResource(R.drawable.slow_motion_video),
            checked = disableVideoAutoplay,
            onCheckedChange = onDisableVideoAutoplayChange,
        ),
        switchItem(
            key = "Translations from YouTube",
            title = stringResource(R.string.settings_youtube_translations),
            description = stringResource(R.string.settings_youtube_translations_desc),
            detail = stringResource(R.string.settings_youtube_translations_detail),
            icon = painterResource(R.drawable.translate),
            checked = youtubeTranslations,
            onCheckedChange = onYoutubeTranslationsChange,
        ),
        switchItem(
            key = "Sync lyrics to music videos",
            title = stringResource(R.string.settings_sync_lyrics_video),
            description = stringResource(R.string.settings_sync_lyrics_video_desc),
            detail = stringResource(R.string.settings_sync_lyrics_video_detail),
            icon = painterResource(R.drawable.lyrics),
            checked = syncLyricsToVideo,
            onCheckedChange = onSyncLyricsToVideoChange,
        ),
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
        switchItem(
            key = "Skip Silence",
            title = stringResource(R.string.playback_skip_silence),
            description = stringResource(R.string.playback_skip_silence_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = skipSilence,
            onCheckedChange = onSkipSilenceChange,
        ),
        // the same switch as sync's youtube history one, signed out it still counts the play
        switchItem(
            key = "Count plays on YouTube views",
            title = stringResource(R.string.settings_youtube_plays),
            description = stringResource(R.string.settings_youtube_plays_desc),
            detail = stringResource(R.string.settings_youtube_plays_detail),
            icon = painterResource(R.drawable.trending_up),
            checked = reportPlays,
            onCheckedChange = onReportPlaysChange,
        ),
    )
}

@Composable
private fun playerLookItems(): List<SettingsItem> {
    val (formatBadge, onFormatBadgeChange) = rememberPreference(ShowFormatBadgeKey, defaultValue = true)
    val (formatSampleRate, onFormatSampleRateChange) = rememberPreference(ShowFormatSampleRateKey, defaultValue = false)
    val (playVideoBackground, onPlayVideoBackgroundChange) = rememberPreference(PlayVideoBackgroundKey, defaultValue = false)
    val (lyricsSync, onLyricsSyncChange) = rememberPreference(YtVideoBackgroundLyricsSyncKey, defaultValue = true)
    val (canvasEnabled, onCanvasEnabledChange) = rememberPreference(CanvasThumbnailAnimationKey, defaultValue = true)
    val (canvasWifiOnly, onCanvasWifiOnlyChange) = rememberPreference(CanvasWifiOnlyKey, defaultValue = true)
    val (highBloom, onHighBloomChange) = rememberPreference(LyricsHighBloomKey, defaultValue = true)
    val (lyricsStyle) = rememberEnumPreference(LyricsMotionStyleKey, defaultValue = LyricsMotionStyle.MOTION)
    return listOf(
        switchItem(
            key = "Audio format badge",
            title = stringResource(R.string.appearance_format_badge),
            description = stringResource(R.string.appearance_format_badge_desc),
            icon = painterResource(R.drawable.graphic_eq),
            checked = formatBadge,
            onCheckedChange = onFormatBadgeChange,
        ),
        switchItem(
            key = "Show sample rate",
            title = stringResource(R.string.appearance_sample_rate),
            description = stringResource(R.string.appearance_sample_rate_desc),
            icon = painterResource(R.drawable.speed),
            checked = formatSampleRate,
            onCheckedChange = onFormatSampleRateChange,
            subOptionOf = formatBadge,
        ),
        switchItem(
            key = "Always enable Youtube background",
            title = stringResource(R.string.appearance_youtube_background),
            description = stringResource(R.string.appearance_youtube_background_desc),
            icon = painterResource(R.drawable.slow_motion_video),
            checked = playVideoBackground,
            onCheckedChange = onPlayVideoBackgroundChange,
        ),
        videoQualityItem(),
        switchItem(
            key = "Timestamp matching",
            title = stringResource(R.string.appearance_timestamp_matching),
            description = stringResource(R.string.appearance_timestamp_matching_desc),
            icon = painterResource(R.drawable.lyrics),
            checked = lyricsSync,
            onCheckedChange = onLyricsSyncChange,
            subOptionOf = playVideoBackground,
        ),
        switchItem(
            key = "Enable animated canvas",
            title = stringResource(R.string.appearance_canvas),
            description = stringResource(R.string.appearance_canvas_desc),
            icon = painterResource(R.drawable.sparks),
            checked = canvasEnabled,
            onCheckedChange = onCanvasEnabledChange,
        ),
        switchItem(
            key = "Canvas on Wi-Fi only",
            title = stringResource(R.string.appearance_canvas_wifi),
            description = stringResource(R.string.appearance_canvas_wifi_desc),
            icon = painterResource(R.drawable.wifi_proxy),
            checked = canvasWifiOnly,
            onCheckedChange = onCanvasWifiOnlyChange,
            subOptionOf = canvasEnabled,
        ),
        // only the shaded lyric styles have a bloom to make softer
        switchItem(
            key = "High quality bloom",
            title = stringResource(R.string.appearance_bloom),
            description = stringResource(R.string.appearance_bloom_desc),
            icon = painterResource(R.drawable.sparks),
            checked = highBloom,
            onCheckedChange = onHighBloomChange,
            visible = lyricsStyle.shaded,
        ),
    )
}

// how sharp youtube videos play, on the cover and in the full video mode
@Composable
private fun videoQualityItem(): SettingsItem {
    val (quality, onQuality) = rememberEnumPreference(VideoQualityKey, defaultValue = VideoQuality.AUTO)
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        VideoQualityDialog(
            selected = quality,
            onSelect = {
                onQuality(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
    val title = stringResource(R.string.video_quality)
    return SettingsItem(
        title = { Text(title) },
        highlightKey = "Video quality",
        descriptionText = videoQualityName(quality),
        detail = stringResource(R.string.video_quality_settings_desc),
        searchText = title,
        icon = rememberVectorPainter(Icons.Rounded.HighQuality),
        iconShape = CircleShape,
        onClick = { showDialog = true },
    )
}

@Composable
private fun outputItems(): List<SettingsItem> {
    val (preferUsbDac, onPreferUsbDacChange) = rememberPreference(PreferUsbDacKey, defaultValue = false)
    val (usbPassthrough, onUsbPassthroughChange) = rememberPreference(UsbPassthroughKey, defaultValue = false)
    val (hiResOutput, onHiResOutputChange) = rememberPreference(HiResOutputKey, defaultValue = false)
    val (hiFiResampler, onHiFiResamplerChange) = rememberPreference(HiFiResamplerKey, defaultValue = false)
    val outputs = LocalPlayerConnection.current?.service?.audioOutputs
    val devices by remember(outputs) { outputs?.devices ?: MutableStateFlow(emptyList()) }.collectAsState()
    val dacName = devices.firstOrNull { it.kind == AudioOutputs.Kind.USB }?.name?.ifBlank { "USB DAC" }
    return listOfNotNull(
        switchItem(
            key = "Prefer USB DAC",
            title = stringResource(R.string.playback_usb_prefer),
            description = dacName?.let { stringResource(R.string.playback_usb_connected, it) } ?: stringResource(R.string.playback_usb_prefer_desc),
            detail = stringResource(R.string.playback_usb_prefer_detail),
            icon = rememberVectorPainter(Icons.Rounded.Usb),
            checked = preferUsbDac,
            onCheckedChange = onPreferUsbDacChange,
        ),
        // the dac's own mixer settings only exist on android 14 and up, so older phones don't see the row at all
        switchItem(
            key = "USB DAC passthrough",
            title = stringResource(R.string.playback_usb_passthrough),
            description = stringResource(R.string.playback_usb_passthrough_desc),
            detail = stringResource(R.string.playback_usb_passthrough_detail),
            icon = rememberVectorPainter(Icons.Rounded.GraphicEq),
            checked = usbPassthrough,
            onCheckedChange = onUsbPassthroughChange,
        ).takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE },
        switchItem(
            key = "Hi-res output",
            title = stringResource(R.string.playback_hires),
            description = stringResource(R.string.playback_hires_desc),
            detail = stringResource(R.string.playback_hires_detail),
            icon = rememberVectorPainter(Icons.Rounded.HighQuality),
            checked = hiResOutput,
            onCheckedChange = onHiResOutputChange,
        ),
        switchItem(
            key = "Hi-fi resampler",
            title = stringResource(R.string.playback_hifi),
            description = stringResource(R.string.playback_hifi_desc),
            detail = stringResource(R.string.playback_hifi_detail),
            icon = rememberVectorPainter(Icons.Rounded.Waves),
            checked = hiFiResampler,
            onCheckedChange = onHiFiResamplerChange,
        ),
    )
}

@Composable
private fun otherItems(): List<SettingsItem> {
    val (hideDescriptions, onHideDescriptions) = rememberPreference(HideSettingsDescriptionsKey, defaultValue = false)
    return listOf(
        switchItem(
            key = "Disable description on settings",
            title = stringResource(R.string.settings_hide_descriptions),
            description = stringResource(R.string.settings_hide_descriptions_desc),
            icon = painterResource(R.drawable.info),
            checked = hideDescriptions,
            onCheckedChange = onHideDescriptions,
        ),
    )
}

// settings that live on another page but people look for here
@Composable
private fun relatedItems(navController: NavController): List<SettingsItem> = listOf(
    SettingsItem(
        title = { Text(stringResource(R.string.settings_hide_top_bar)) },
        highlightKey = "Hide Top Bar status bar player",
        descriptionText = stringResource(R.string.settings_hide_top_bar_desc),
        searchText = stringResource(R.string.settings_hide_top_bar),
        icon = painterResource(R.drawable.tune),
        iconShape = CircleShape,
        onClick = {
            SettingsHighlight.pending = TopBarHighlightKey
            navController.navigate("customisation_settings")
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        },
    ),
)

// what customize answers to when a search or the related row asks for its top bar card
const val TopBarHighlightKey = "Top Bar on player"

// a row title with its value on the right, for the rows with a slider under them
@Composable
private fun ValueTitle(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title)
        Text(
            text = value,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.70f),
        )
    }
}

// one switch row: key is the english name search results point at, subOptionOf tucks it under a parent that's on
private fun switchItem(
    key: String,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    detail: String? = null,
    subOptionOf: Boolean? = null,
    visible: Boolean = true,
) = SettingsItem(
    title = { Text(title) },
    highlightKey = key,
    searchText = title,
    descriptionText = description,
    detail = detail,
    icon = icon,
    iconShape = CircleShape,
    isVisible = visible && subOptionOf != false,
    isSubOption = subOptionOf != null,
    onClick = { onCheckedChange(!checked) },
    trailingContent = {
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = subOptionOf != false)
    },
)
