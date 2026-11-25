// shared settings index and category catalog used by both MochySettingsScreen and custom module settings pages

package com.mochyy.mochymusic.ui.screens.settings

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.scrobble.ScrobbleSite
import com.mochyy.mochymusic.ui.screens.devices.DeviceIntegrationRoute
import com.mochyy.mochymusic.ui.screens.gesture.FontHighlightKey
import com.mochyy.mochymusic.ui.screens.settings.importsync.YouTubeSyncRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationsRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.scrobbleServiceRoute
import com.mochyy.mochymusic.ui.screens.settings.modules.ModuleBrowserRoute

internal data class SettingsEntry(
    val title: String,
    val page: String,
    val route: String,
    val keywords: String = "",
    val titleRes: Int? = null,
    val pageRes: Int? = null,
) {
    val highlightKey: String get() = title
}

internal data class SettingsCategoryDef(
    val id: String,
    val route: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
)

internal val SettingsCategoriesCatalog: List<SettingsCategoryDef> = listOf(
    SettingsCategoryDef("audio_lab", "audio_lab", R.string.settings_audio_lab_category, R.drawable.equalizer),
    SettingsCategoryDef("playback", PlaybackSettingsRoute, R.string.settings_playback_category, R.drawable.play),
    SettingsCategoryDef("now_playing", NowPlayingSettingsRoute, R.string.settings_now_playing, R.drawable.lyrics),
    SettingsCategoryDef("behaviour", "general_settings", R.string.settings_app_behaviour, R.drawable.settings),
    SettingsCategoryDef("customisation", "customisation_settings", R.string.settings_customisation_category, R.drawable.palette),
    SettingsCategoryDef("storage", "storage_settings", R.string.storage, R.drawable.storage),
    SettingsCategoryDef("integrations", IntegrationsRoute, R.string.integrations, R.drawable.integration),
    SettingsCategoryDef("devices", DeviceIntegrationRoute, R.string.devices_title, R.drawable.cast_connected),
    SettingsCategoryDef("modules", "modules", R.string.settings_modules_category, R.drawable.library_add),
    SettingsCategoryDef("module_store", ModuleBrowserRoute, R.string.settings_module_store, R.drawable.puzzle),
)

internal fun findSettingsCategory(raw: String): SettingsCategoryDef? {
    val clean = raw.trim().lowercase().replace('-', '_')
    val mapped = when (clean) {
        "audio", "audiolab", "audio_lab" -> "audio_lab"
        "playback", "playback_settings" -> "playback"
        "now_playing", "nowplaying", "now_playing_settings", "lyrics" -> "now_playing"
        "behaviour", "behavior", "general", "general_settings", "app_behaviour" -> "behaviour"
        "customisation", "customization", "appearance", "customisation_settings" -> "customisation"
        "storage", "storage_settings" -> "storage"
        "integrations" -> "integrations"
        "devices", "device_integration" -> "devices"
        "modules" -> "modules"
        "store", "module_store", "module_browser" -> "module_store"
        else -> clean
    }
    return SettingsCategoriesCatalog.firstOrNull { it.id == mapped || it.route == clean }
}

// search ids stay english so rows can find themselves, what's shown and matched follows the app's language
internal val LocalizedSettingsTitles = mapOf(
    "Customisation" to R.string.settings_customisation_category,
    "Blur" to R.string.appearance_blur,
    "Progressive blur" to R.string.appearance_progressive_blur,
    "Blur quality" to R.string.appearance_blur_quality,
    "Blur strength" to R.string.appearance_blur_strength,
    "Animation" to R.string.appearance_animation,
    "Lyrics animation" to R.string.appearance_lyrics_animation,
    "Larp mode" to R.string.larp_mode,
    "Disable App Updates notice" to R.string.settings_updates_notice,
    "Offline mode" to R.string.settings_offline_mode,
    "Data saver" to R.string.settings_data_saver,
    "Proxy" to R.string.proxy,
    "Albums from liked songs" to R.string.settings_albums_from_likes,
    "Keep playing even when closed" to R.string.playback_keep_playing,
    "Play alongside other apps" to R.string.playback_mix_other_apps,
    "Save Player's last state" to R.string.playback_save_state,
    "Local song auto metadata" to R.string.settings_local_auto_metadata,
    "Keep shuffle on" to R.string.settings_persistent_shuffle,
    "Disable auto-playing YouTube video" to R.string.settings_disable_video_autoplay,
    "Translations from YouTube" to R.string.settings_youtube_translations,
    "Sync lyrics to music videos" to R.string.settings_sync_lyrics_video,
    "Don't skip on failure" to R.string.settings_dont_skip_failure,
    "Skip timeout failure" to R.string.settings_skip_timeout,
    "Skip Silence" to R.string.playback_skip_silence,
    "Audio format badge" to R.string.appearance_format_badge,
    "Show sample rate" to R.string.appearance_sample_rate,
    "Always enable Youtube background" to R.string.appearance_youtube_background,
    "Enable animated canvas" to R.string.appearance_canvas,
    "High quality bloom" to R.string.appearance_bloom,
    "Tinted lyrics" to R.string.appearance_lyrics_soft_light,
    "Audio Pass" to R.string.audiopass_hub_title,
    "Prefer USB DAC" to R.string.playback_usb_prefer,
    "USB DAC passthrough" to R.string.playback_usb_passthrough,
    "Hi-res output" to R.string.playback_hires,
    "Hi-fi resampler" to R.string.playback_hifi,
    "Audio Lab" to R.string.settings_audio_lab_category,
    "Modules" to R.string.settings_modules_category,
    "Storage" to R.string.storage,
    "App behaviour" to R.string.settings_app_behaviour,
    "Playback" to R.string.settings_playback_category,
    "Now playing" to R.string.settings_now_playing,
    "Disable description on settings" to R.string.settings_hide_descriptions,
    "Cover rounding" to R.string.customise_cover_rounding,
    "Playlist styles" to R.string.customise_playlist_styles,
    "Player styles" to R.string.customise_player_styles,
    "Home styles" to R.string.customise_home_styles,
    "Element blur" to R.string.customise_element_blur,
    "Animation speed" to R.string.customise_animation_speed,
    "Top Bar on player" to R.string.customise_top_bar,
    "Gesture" to R.string.customise_gesture,
    "Swipe right" to R.string.customise_swipe_right,
    "Swipe left" to R.string.customise_swipe_left,
    "Swipe down anywhere to close" to R.string.customise_swipe_down_anywhere,
    "Open with your finger" to R.string.customise_swipe_up_follows,
)

internal val LocalizedSettingsPages = mapOf(
    "App behaviour" to R.string.settings_app_behaviour,
    "Playback" to R.string.settings_playback_category,
    "Now playing" to R.string.settings_now_playing,
    "Customisation" to R.string.settings_customisation_category,
    "Storage" to R.string.storage,
    "Audio Lab" to R.string.settings_audio_lab_category,
    "Modules" to R.string.settings_modules_category,
    "Integrations" to R.string.integrations,
)

internal val SettingsIndex = listOf(
    // Customisation
    SettingsEntry(FontHighlightKey, "Customisation", "customisation_settings", "font typeface ttf otf import system heading subheading global bold italic light customizer 폰트 글꼴", R.string.customise_font),
    SettingsEntry("Customisation", "Customisation", "customisation_settings", "customize cover rounding playlist player home styles gesture swipe blur animation lyrics"),
    SettingsEntry("Cover rounding", "Customisation", "customisation_settings", "square rounded corner radius px thumbnail art"),
    SettingsEntry("Playlist styles", "Customisation", "customisation_settings", "flow classic minimal header layout"),
    SettingsEntry("Player styles", "Customisation", "customisation_settings", "flagship turntable ipod poster timeline lyrics studio customize player cover"),
    SettingsEntry("Home styles", "Customisation", "customisation_settings", "mochyy listen now hop in hero layout grid"),
    SettingsEntry("Blur", "Customisation", "customisation_settings", "blur glass frosted performance lag"),
    SettingsEntry("Progressive blur", "Customisation", "customisation_settings", "gradient fade top bar hairline flat"),
    SettingsEntry("Blur quality", "Customisation", "customisation_settings", "low high glass performance"),
    SettingsEntry("Blur strength", "Customisation", "customisation_settings", "radius px glass amount"),
    SettingsEntry("Element blur", "Customisation", "customisation_settings", "lyrics line blur cover zoom title morph menu page wipe"),
    SettingsEntry("Animation", "Customisation", "customisation_settings", "transition motion off reduce disable"),
    SettingsEntry("Animation speed", "Customisation", "customisation_settings", "faster slower duration scale motion"),
    SettingsEntry("Lyrics animation", "Customisation", "customisation_settings", "lyrics style 1 2 boring plain shader glow wave agsl letters"),
    SettingsEntry("Top Bar on player", "Customisation", "customisation_settings", "status bar hide clock battery player top"),
    SettingsEntry("Gesture", "Customisation", "customisation_settings", "swipe slide song list left right queue library download play next playlist details"),
    SettingsEntry("Swipe right", "Customisation", "customisation_settings", "slide song list queue library download play next playlist details action"),
    SettingsEntry("Swipe left", "Customisation", "customisation_settings", "slide song list queue library download play next playlist details action"),
    SettingsEntry("Swipe down anywhere to close", "Customisation", "customisation_settings", "player close dismiss swipe down pull gesture anywhere"),
    SettingsEntry("Open with your finger", "Customisation", "customisation_settings", "player open swipe up mini player follow finger speed velocity gesture"),

    // App behaviour
    SettingsEntry("App behaviour", "App behaviour", "general_settings", "general app language updates network data library privacy"),
    SettingsEntry("Larp mode", "App behaviour", "general_settings", "larp 67 flac 768 khz wadax atlantis dac hi-res lossless bit-perfect flex"),
    SettingsEntry("App language", "App behaviour", "general_settings", "language bahasa locale device system indonesia english translate", R.string.app_language),
    SettingsEntry("Disable App Updates notice", "App behaviour", "general_settings", "update popup notification updater"),
    SettingsEntry("Disable description on settings", "App behaviour", "general_settings", "hide subtitle text rows titles only"),
    SettingsEntry("Offline mode", "App behaviour", "general_settings", "play downloaded cache offline"),
    SettingsEntry("Data saver", "App behaviour", "general_settings", "data saver mobile metered cellular bandwidth lower quality canvas video"),
    SettingsEntry("Proxy", "App behaviour", ProxySettingsRoute, "proxy socks http server vpn network region country blocked bot sign in"),
    SettingsEntry("Albums from liked songs", "App behaviour", "general_settings", "albums library liked songs old way apple music"),
    SettingsEntry("Local song auto metadata", "App behaviour", "general_settings", "match tags id3 covers lyrics"),
    SettingsEntry("Pause search history", "App behaviour", "general_settings", "search history pause private incognito privacy", R.string.pause_search_history),
    SettingsEntry("Count plays on YouTube views", "App behaviour", "general_settings", "youtube history plays views report count privacy", R.string.settings_youtube_plays),
    SettingsEntry("Override lyrics sorting", "App behaviour", "general_settings", "lyrics provider order sort priority duet word sync apple music source", R.string.settings_lyrics_order_override),

    // Playback
    SettingsEntry("Playback", "Playback", PlaybackSettingsRoute, "background bluetooth shuffle skip silence mute queue failure"),
    SettingsEntry("Keep playing even when closed", "Playback", PlaybackSettingsRoute, "background close stop playback"),
    SettingsEntry("Play alongside other apps", "Playback", PlaybackSettingsRoute, "audio focus mix pause other app video game sound together"),
    SettingsEntry("Resume on Bluetooth connection", "Playback", PlaybackSettingsRoute, "bluetooth headphones earbuds car connect resume auto play start", R.string.playback_resume_bluetooth),
    SettingsEntry("Pause when muted", "Playback", PlaybackSettingsRoute, "pause mute volume silent zero off automatic", R.string.pause_music_when_media_is_muted),
    SettingsEntry("Save Player's last state", "Playback", PlaybackSettingsRoute, "persistent queue restore track state"),
    SettingsEntry("Keep shuffle on", "Playback", PlaybackSettingsRoute, "shuffle persistent spotify random keep new playlist"),
    SettingsEntry("Skip Silence", "Playback", PlaybackSettingsRoute, "silence trim gap interval"),
    SettingsEntry("Don't skip on failure", "Playback", PlaybackSettingsRoute, "fail stream error stay skip"),
    SettingsEntry("Skip timeout failure", "Playback", PlaybackSettingsRoute, "skip timeout seconds failed stream"),

    // Now playing
    SettingsEntry("Now playing", "Now playing", NowPlayingSettingsRoute, "player lyrics video canvas badge format"),
    SettingsEntry("Audio format badge", "Now playing", NowPlayingSettingsRoute, "format codec opus aac flac badge title quality"),
    SettingsEntry("Show sample rate", "Now playing", NowPlayingSettingsRoute, "sample rate khz hz format badge quality"),
    SettingsEntry("Translations from YouTube", "Now playing", NowPlayingSettingsRoute, "captions translate lyrics language"),
    SettingsEntry("Sync lyrics to music videos", "Now playing", NowPlayingSettingsRoute, "lyrics video timing intro skit"),
    SettingsEntry("Romanisation as the main line", "Now playing", NowPlayingSettingsRoute, "romaji romanization pinyin latin lyrics japanese korean chinese", R.string.settings_romanize_as_main),
    SettingsEntry("High quality bloom", "Now playing", NowPlayingSettingsRoute, "lyrics bloom quality extra soft pass glow"),
    SettingsEntry("Tinted lyrics", "Now playing", NowPlayingSettingsRoute, "lyrics soft light blend tint colour color cover inactive grey gray lines"),
    SettingsEntry("Floating lyrics overlay bubble", "Now playing", NowPlayingSettingsRoute, "floating lyrics overlay bubble pill other apps draw over", R.string.settings_floating_lyrics),
    SettingsEntry("Always enable Youtube background", "Now playing", NowPlayingSettingsRoute, "youtube video background canvas"),
    SettingsEntry("Video quality", "Now playing", NowPlayingSettingsRoute, "video quality resolution 4k 2160p 1440p 1080p 720p 480p sharp", R.string.video_quality),
    SettingsEntry("Disable auto-playing YouTube video", "Now playing", NowPlayingSettingsRoute, "music video live autoplay cover"),
    SettingsEntry("Studio version for music videos", "Now playing", NowPlayingSettingsRoute, "music video mv omv audio studio album version skit sound effects official", R.string.settings_studio_for_videos),
    SettingsEntry("Enable animated canvas", "Now playing", NowPlayingSettingsRoute, "canvas animation spotify looping visual"),
    SettingsEntry("Canvas order", "Now playing", NowPlayingSettingsRoute, "canvas order apple music tidal spotify source priority", R.string.canvas_order),
    SettingsEntry("Canvas on Wi-Fi only", "Now playing", NowPlayingSettingsRoute, "canvas wifi mobile data", R.string.appearance_canvas_wifi),
    SettingsEntry("Sharpest animated covers", "Now playing", NowPlayingSettingsRoute, "canvas animated cover motion apple music quality hd 4k sharp resolution", R.string.canvas_best_quality),

    // Audio Lab
    SettingsEntry("Audio Lab", "Audio Lab", "audio_lab", "sound effects equalizer fx quality"),
    SettingsEntry("Transition", "Audio Lab", "crossmix_settings", "crossfade crossmix automix transition gapless blend"),
    SettingsEntry("Blend skips", "Audio Lab", "crossmix_settings", "skip next previous back crossfade seamless preload transition fade blend"),
    SettingsEntry("Equalizer", "Audio Lab", "equalizer", "eq bass treble band parametric graph autoeq headphones correction preset"),
    SettingsEntry("AutoEQ", "Audio Lab", "equalizer", "headphones earbuds correction harman database model"),
    SettingsEntry("Audio Quality", "Audio Lab", "audio_quality", "bitrate stream high low lossless hi-res flac monochrome"),
    SettingsEntry("Audio Pass", "Audio Lab", "audio_pass", "dac usb output headphones bluetooth codec bit perfect exclusive"),
    SettingsEntry("Prefer USB DAC", "Audio Lab", "audio_pass", "usb dac headphones output external audio"),
    SettingsEntry("USB DAC passthrough", "Audio Lab", "audio_pass", "bit perfect bitperfect passthrough sample rate dac usb resample"),
    SettingsEntry("Hi-res output", "Audio Lab", "audio_pass", "hi res 24 bit float precision lossless flac quality"),
    SettingsEntry("Hi-fi resampler", "Audio Lab", "audio_pass", "resample sample rate 44.1 48 khz sinc src quality filter"),
    SettingsEntry("Reverb", "Audio Lab", "audio_fx", "room hall echo space fx effect"),
    SettingsEntry("Loudness", "Audio Lab", "audio_fx", "boost louder compressor limiter night mode fx"),
    SettingsEntry("Audio Normalization", "Audio Lab", "audio_fx", "even out songs loudness volume gain replaygain normalize"),
    SettingsEntry("Spatial audio", "Audio Lab", "audio_fx", "crossfeed binaural hrtf virtual speakers 3d headphones"),
    SettingsEntry("Dolby Atmos", "Audio Lab", "audio_fx", "atmos spatial surround 3d immersive"),
    SettingsEntry("Playback speed", "Audio Lab", "audio_fx", "tempo pitch slowed nightcore preserve pitch"),
    SettingsEntry("Exponential volume", "Audio Lab", "audio_fx", "volume curve logarithmic limit"),
    SettingsEntry("Modules", "Modules", "modules", "apple music source module plugin extension stream subscription"),
    SettingsEntry("Apple Music", "Modules", "module/applemusic", "apple music itunes subscription stream aac"),
    SettingsEntry("Monochrome", "Modules", "module/monochrome", "monochrome lossless flac hi-res tidal"),
    SettingsEntry("Provider order", "Audio Lab", "audio_quality", "source order apple music monochrome youtube priority"),
    SettingsEntry("YouTube Music", "Audio Lab", "audio_quality", "youtube turn off disable provider only monochrome apple music"),

    // Integrations
    SettingsEntry("Discord", "Integrations", DiscordRoute, "discord rich presence rpc status activity listening playing profile behaviour pause clear"),
    SettingsEntry("Last.fm", "Integrations", scrobbleServiceRoute(ScrobbleSite.LASTFM), "lastfm scrobble scrobbling history stats account sign in"),
    SettingsEntry("Libre.fm", "Integrations", scrobbleServiceRoute(ScrobbleSite.LIBREFM), "librefm scrobble scrobbling history stats account sign in"),
    SettingsEntry("Now Playing", "Integrations", scrobbleServiceRoute(ScrobbleSite.LASTFM), "scrobble now playing status profile lastfm librefm"),
    SettingsEntry("Send likes", "Integrations", scrobbleServiceRoute(ScrobbleSite.LASTFM), "love loved songs lastfm librefm scrobble"),
    SettingsEntry("YouTube Music", "Integrations", YouTubeSyncRoute, "youtube ytm sync account sign in likes library playlists history"),

    // Storage
    SettingsEntry("Storage", "Storage", "storage_settings", "space disk size cache downloads clear free junk delete"),
    SettingsEntry("Music cache limit", "Storage", "storage_settings", "cache size limit space song stream"),
    SettingsEntry("Manage downloads", "Storage", "storage_settings", "downloads delete remove biggest offline space"),
    SettingsEntry("Remove all downloads", "Storage", "storage_settings", "delete clear downloads offline space"),

    // Experimental & devices
    SettingsEntry("Device integration", "Import & sync", DeviceIntegrationRoute, "tv television android tv google tv phone tablet car head unit pair link sync stream downloads big screen remote control devices"),
    SettingsEntry("Import & sync", "Experimental", "experimental_settings", "import spotify apple music deezer tidal tunemymusic csv youtube sync export backup transfer"),
    SettingsEntry("Big disc cover styles", "Experimental", "experimental_settings", "disc vinyl cover"),
    SettingsEntry("Music haptics", "Experimental", "experimental_settings", "vibration haptic"),
    SettingsEntry("Cipher", "Experimental", "experimental_settings", "player script signature refresh"),
    SettingsEntry("Playback diagnostics", "Experimental", "experimental_settings", "logs potoken stream client debug"),
    SettingsEntry("Repeat initial setup", "Experimental", "experimental_settings", "onboarding wizard setup"),
    SettingsEntry("Show where songs play from", "Experimental", "experimental_settings", "source banner toast apple music monochrome youtube"),
    SettingsEntry("Force playback", "Experimental", "experimental_settings", "fallback skip error stream failed youtube"),
)

internal fun searchSettings(context: Context, query: String): List<SettingsEntry> {
    val q = query.trim()
    if (q.isBlank()) return emptyList()
    return SettingsIndex.filter {
        it.title.contains(q, ignoreCase = true) ||
            (it.titleRes ?: LocalizedSettingsTitles[it.title])?.let { id -> context.getString(id).contains(q, ignoreCase = true) } == true ||
            (it.pageRes ?: LocalizedSettingsPages[it.page])?.let { id -> context.getString(id).contains(q, ignoreCase = true) } == true ||
            it.keywords.contains(q, ignoreCase = true) ||
            it.page.contains(q, ignoreCase = true)
    }
}
