package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material3.Text
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
import com.mochyy.mochymusic.constants.CanvasBestQualityKey
import com.mochyy.mochymusic.constants.CanvasOrder
import com.mochyy.mochymusic.constants.CanvasOrderKey
import com.mochyy.mochymusic.constants.CanvasSource
import com.mochyy.mochymusic.constants.CanvasThumbnailAnimationKey
import com.mochyy.mochymusic.constants.CanvasWifiOnlyKey
import com.mochyy.mochymusic.constants.DisableVideoAutoplayKey
import com.mochyy.mochymusic.constants.LyricsHighBloomKey
import com.mochyy.mochymusic.constants.LyricsSoftLightKey
import com.mochyy.mochymusic.constants.LyricsMotionStyle
import com.mochyy.mochymusic.constants.LyricsMotionStyleKey
import com.mochyy.mochymusic.constants.LyricsRomanizeAsMainKey
import com.mochyy.mochymusic.constants.PlayVideoBackgroundKey
import com.mochyy.mochymusic.constants.ShowFormatBadgeKey
import com.mochyy.mochymusic.constants.ShowFormatSampleRateKey
import com.mochyy.mochymusic.constants.SpotifyCanvasKey
import com.mochyy.mochymusic.constants.StudioForVideosKey
import com.mochyy.mochymusic.constants.SyncLyricsToVideoKey
import com.mochyy.mochymusic.constants.VideoQuality
import com.mochyy.mochymusic.constants.VideoQualityKey
import com.mochyy.mochymusic.constants.YouTubeCaptionTranslationKey
import com.mochyy.mochymusic.constants.YtVideoBackgroundLyricsSyncKey
import com.mochyy.mochymusic.floating.rememberFloatingLyricsSwitch
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.SettingsHighlight
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.component.SubSettingsSearchBar
import com.mochyy.mochymusic.ui.player.SoftLightSupported
import com.mochyy.mochymusic.ui.screens.audiolab.LabSelect
import com.mochyy.mochymusic.ui.screens.settings.integrations.spotify.SpotifyRoute
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference

const val NowPlayingSettingsRoute = "now_playing_settings"

// what customize answers to when a search or the related row asks for its top bar card
const val TopBarHighlightKey = "Top Bar on player"

// the same for its bottom bar card
const val BottomBarHighlightKey = "Bottom Bar on player"

// settings > now playing: what the open player shows around the song, its badge, lyrics, videos and canvas
@Composable
fun NowPlayingSettingsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_now_playing),
        onBack = { navController.navigateUp() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        SettingsSection(stringResource(R.string.settings_on_the_player), badgeItems().matching(query))
        SettingsSection(stringResource(R.string.settings_section_lyrics), lyricsItems().matching(query))
        SettingsSection(stringResource(R.string.settings_section_videos), videoItems().matching(query))
        SettingsSection(stringResource(R.string.settings_section_canvas), canvasItems(navController).matching(query))
        SettingsSection(stringResource(R.string.settings_related), relatedItems(navController).matching(query))
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun badgeItems(): List<SettingsItem> {
    val (formatBadge, onFormatBadgeChange) = rememberPreference(ShowFormatBadgeKey, defaultValue = true)
    val (formatSampleRate, onFormatSampleRateChange) = rememberPreference(ShowFormatSampleRateKey, defaultValue = false)
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
    )
}

@Composable
private fun lyricsItems(): List<SettingsItem> {
    val (youtubeTranslations, onYoutubeTranslationsChange) = rememberPreference(YouTubeCaptionTranslationKey, defaultValue = true)
    val (syncLyricsToVideo, onSyncLyricsToVideoChange) = rememberPreference(SyncLyricsToVideoKey, defaultValue = true)
    val (romanizeAsMain, onRomanizeAsMainChange) = rememberPreference(LyricsRomanizeAsMainKey, defaultValue = false)
    val (highBloom, onHighBloomChange) = rememberPreference(LyricsHighBloomKey, defaultValue = true)
    val (softLight, onSoftLightChange) = rememberPreference(LyricsSoftLightKey, defaultValue = true)
    val (lyricsStyle) = rememberEnumPreference(LyricsMotionStyleKey, defaultValue = LyricsMotionStyle.MOTION)
    val (floatingLyrics, onFloatingLyricsChange) = rememberFloatingLyricsSwitch()
    return listOf(
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
            key = "Romanisation as the main line",
            title = stringResource(R.string.settings_romanize_as_main),
            description = stringResource(R.string.settings_romanize_as_main_desc),
            icon = painterResource(R.drawable.lyrics),
            checked = romanizeAsMain,
            onCheckedChange = onRomanizeAsMainChange,
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
        // the blend needs android 10, older phones never see it
        switchItem(
            key = "Tinted lyrics",
            title = stringResource(R.string.appearance_lyrics_soft_light),
            description = stringResource(R.string.appearance_lyrics_soft_light_desc),
            icon = painterResource(R.drawable.palette),
            checked = softLight,
            onCheckedChange = onSoftLightChange,
            visible = SoftLightSupported,
        ),
        switchItem(
            key = "Floating lyrics overlay bubble",
            title = stringResource(R.string.settings_floating_lyrics),
            description = stringResource(R.string.settings_floating_lyrics_desc),
            detail = stringResource(R.string.settings_floating_lyrics_detail),
            icon = painterResource(R.drawable.lyrics),
            checked = floatingLyrics,
            onCheckedChange = onFloatingLyricsChange,
        ),
    )
}

@Composable
private fun videoItems(): List<SettingsItem> {
    val (playVideoBackground, onPlayVideoBackgroundChange) = rememberPreference(PlayVideoBackgroundKey, defaultValue = false)
    val (lyricsSync, onLyricsSyncChange) = rememberPreference(YtVideoBackgroundLyricsSyncKey, defaultValue = true)
    val (disableVideoAutoplay, onDisableVideoAutoplayChange) = rememberPreference(DisableVideoAutoplayKey, defaultValue = false)
    val (studioForVideos, onStudioForVideosChange) = rememberPreference(StudioForVideosKey, defaultValue = false)
    return listOf(
        switchItem(
            key = "Always enable Youtube background",
            title = stringResource(R.string.appearance_youtube_background),
            description = stringResource(R.string.appearance_youtube_background_desc),
            icon = painterResource(R.drawable.slow_motion_video),
            checked = playVideoBackground,
            onCheckedChange = onPlayVideoBackgroundChange,
        ),
        // right under the background switch it belongs to, a sub row only joins the row just above it
        switchItem(
            key = "Timestamp matching",
            title = stringResource(R.string.appearance_timestamp_matching),
            description = stringResource(R.string.appearance_timestamp_matching_desc),
            icon = painterResource(R.drawable.lyrics),
            checked = lyricsSync,
            onCheckedChange = onLyricsSyncChange,
            subOptionOf = playVideoBackground,
        ),
        videoQualityItem(),
        switchItem(
            key = "Disable auto-playing YouTube video",
            title = stringResource(R.string.settings_disable_video_autoplay),
            description = stringResource(R.string.settings_disable_video_autoplay_desc),
            icon = painterResource(R.drawable.slow_motion_video),
            checked = disableVideoAutoplay,
            onCheckedChange = onDisableVideoAutoplayChange,
        ),
        switchItem(
            key = "Studio version for music videos",
            title = stringResource(R.string.settings_studio_for_videos),
            description = stringResource(R.string.settings_studio_for_videos_desc),
            icon = painterResource(R.drawable.music_note),
            checked = studioForVideos,
            onCheckedChange = onStudioForVideosChange,
        ),
    )
}

// how sharp youtube videos play, on the cover and in the full video mode
@Composable
private fun videoQualityItem(): SettingsItem {
    val (quality, onQuality) = rememberEnumPreference(VideoQualityKey, defaultValue = VideoQuality.AUTO)
    val overlay = LocalZoomOutOverlayState.current
    val title = stringResource(R.string.video_quality)
    return SettingsItem(
        title = { Text(title) },
        highlightKey = "Video quality",
        descriptionText = videoQualityName(quality),
        detail = stringResource(R.string.video_quality_settings_desc),
        searchText = title,
        icon = rememberVectorPainter(Icons.Rounded.HighQuality),
        iconShape = CircleShape,
        onClick = { overlay.showVideoQualitySheet(selected = quality, onSelect = onQuality) },
    )
}

@Composable
private fun canvasItems(navController: NavController): List<SettingsItem> {
    val (canvasEnabled, onCanvasEnabledChange) = rememberPreference(CanvasThumbnailAnimationKey, defaultValue = true)
    val (canvasWifiOnly, onCanvasWifiOnlyChange) = rememberPreference(CanvasWifiOnlyKey, defaultValue = true)
    val (canvasBest, onCanvasBestChange) = rememberPreference(CanvasBestQualityKey, defaultValue = true)
    val (canvasOrder, onCanvasOrderChange) = rememberEnumPreference(CanvasOrderKey, defaultValue = CanvasOrder.APPLE_TIDAL_SPOTIFY)
    val (spotifyCanvas) = rememberPreference(SpotifyCanvasKey, defaultValue = false)
    val canvasSourceNames = mapOf(
        CanvasSource.APPLE_MUSIC to stringResource(R.string.canvas_source_apple),
        CanvasSource.TIDAL to stringResource(R.string.canvas_source_tidal),
        CanvasSource.SPOTIFY to stringResource(R.string.canvas_source_spotify),
    )
    return listOf(
        switchItem(
            key = "Enable animated canvas",
            title = stringResource(R.string.appearance_canvas),
            description = stringResource(R.string.appearance_canvas_desc),
            icon = painterResource(R.drawable.sparks),
            checked = canvasEnabled,
            onCheckedChange = onCanvasEnabledChange,
        ),
        SettingsItem(
            title = { Text(stringResource(R.string.canvas_order)) },
            highlightKey = "Canvas order",
            searchText = stringResource(R.string.canvas_order),
            description = {
                LabSelect(
                    options = CanvasOrder.entries.map { order -> order.sources.joinToString(" → ") { canvasSourceNames.getValue(it) } },
                    selectedIndex = canvasOrder.ordinal,
                    onSelect = { onCanvasOrderChange(CanvasOrder.entries[it]) },
                    contentDescription = stringResource(R.string.canvas_order),
                    modifier = Modifier.padding(top = 8.dp),
                )
            },
            icon = painterResource(R.drawable.sparks),
            iconShape = CircleShape,
            isVisible = canvasEnabled,
            isSubOption = true,
        ),
        linkItem(
            key = "Spotify canvas",
            title = stringResource(R.string.spotify_canvas),
            description = stringResource(if (spotifyCanvas) R.string.spotify_canvas_row_on else R.string.spotify_canvas_row_off),
            icon = painterResource(R.drawable.sparks),
            onClick = { navController.navigate(SpotifyRoute) },
            visible = canvasEnabled,
            isSubOption = true,
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
        switchItem(
            key = "Sharpest animated covers",
            title = stringResource(R.string.canvas_best_quality),
            description = stringResource(R.string.canvas_best_quality_desc),
            icon = rememberVectorPainter(Icons.Rounded.HighQuality),
            checked = canvasBest,
            onCheckedChange = onCanvasBestChange,
            subOptionOf = canvasEnabled,
        ),
    )
}

// how the player looks lives in customize, these take people to the right card there
@Composable
private fun relatedItems(navController: NavController): List<SettingsItem> {
    val openCustomise = { key: String ->
        SettingsHighlight.pending = key
        navController.navigate("customisation_settings")
    }
    return listOf(
        linkItem(
            key = "Lyrics animation style",
            title = stringResource(R.string.appearance_lyrics_animation),
            description = stringResource(R.string.settings_lyrics_animation_link_desc),
            icon = painterResource(R.drawable.lyrics),
            onClick = { openCustomise("Lyrics animation") },
        ),
        linkItem(
            key = "Hide Top Bar status bar player",
            title = stringResource(R.string.settings_hide_top_bar),
            description = stringResource(R.string.settings_hide_top_bar_desc),
            icon = painterResource(R.drawable.tune),
            onClick = { openCustomise(TopBarHighlightKey) },
        ),
        linkItem(
            key = "Hide Bottom Bar navigation bar player",
            title = stringResource(R.string.settings_hide_bottom_bar),
            description = stringResource(R.string.settings_hide_bottom_bar_desc),
            icon = painterResource(R.drawable.tune),
            onClick = { openCustomise(BottomBarHighlightKey) },
        ),
    )
}
