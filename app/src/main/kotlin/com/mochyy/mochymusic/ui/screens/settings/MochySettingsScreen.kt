// MochySettingsScreen.kt

package com.mochyy.mochymusic.ui.screens.settings

import com.mochyy.mochymusic.ui.component.navigateToTab
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.scrobble.ScrobbleSite
import com.mochyy.mochymusic.ui.screens.settings.importsync.YouTubeSyncRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationsRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.scrobbleServiceRoute
import com.mochyy.mochymusic.constants.ProfilePicUriKey
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.SettingsHighlight
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.screens.search.SearchAvatar
import com.mochyy.mochymusic.ui.screens.search.SearchField
import com.mochyy.mochymusic.ui.screens.search.SearchGlassTopBar
import com.mochyy.mochymusic.ui.screens.search.rememberScrollCollapseProgress
import com.mochyy.mochymusic.ui.screens.search.searchTopBarHeight
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberPreference

private data class SettingsEntry(
    val title: String,
    val page: String,
    val route: String,
    val keywords: String = "",
    val titleRes: Int? = null,
    val pageRes: Int? = null,
) {
    val highlightKey: String get() = title
}

// search ids stay english so rows can find themselves, what's shown and matched follows the app's language
private val LocalizedSettingsTitles = mapOf(
    "Customisation" to R.string.settings_customisation_category,
    "Blur" to R.string.appearance_blur,
    "Progressive blur" to R.string.appearance_progressive_blur,
    "Blur quality" to R.string.appearance_blur_quality,
    "Blur strength" to R.string.appearance_blur_strength,
    "Animation" to R.string.appearance_animation,
    "Lyrics animation" to R.string.appearance_lyrics_animation,
    "Disable App Updates notice" to R.string.settings_updates_notice,
    "Offline mode" to R.string.settings_offline_mode,
    "Keep playing even when closed" to R.string.playback_keep_playing,
    "Play alongside other apps" to R.string.playback_mix_other_apps,
    "Save Player's last state" to R.string.playback_save_state,
    "Local song auto metadata" to R.string.settings_local_auto_metadata,
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
    "Prefer USB DAC" to R.string.playback_usb_prefer,
    "USB DAC passthrough" to R.string.playback_usb_passthrough,
    "Hi-res output" to R.string.playback_hires,
    "Hi-fi resampler" to R.string.playback_hifi,
    "Audio Lab" to R.string.settings_audio_lab_category,
    "Modules" to R.string.settings_modules_category,
    "Storage" to R.string.storage,
    "App behaviour" to R.string.settings_app_behaviour,
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
)
private val LocalizedSettingsPages = mapOf(
    "App behaviour" to R.string.settings_app_behaviour,
    "Customisation" to R.string.settings_customisation_category,
    "Storage" to R.string.storage,
    "Audio Lab" to R.string.settings_audio_lab_category,
    "Modules" to R.string.settings_modules_category,
    "Integrations" to R.string.integrations,
)

private val SettingsIndex = listOf(
    // Customisation
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

    // App behaviour
    SettingsEntry("App behaviour", "App behaviour", "general_settings", "general playback player behaviour"),
    SettingsEntry("App language", "App behaviour", "general_settings", "language bahasa locale device system indonesia english translate", R.string.app_language),
    SettingsEntry("Disable App Updates notice", "App behaviour", "general_settings", "update popup notification updater"),
    SettingsEntry("Offline mode", "App behaviour", "general_settings", "play downloaded cache offline"),
    SettingsEntry("Keep playing even when closed", "App behaviour", "general_settings", "background close stop playback"),
    SettingsEntry("Play alongside other apps", "App behaviour", "general_settings", "audio focus mix pause other app video game sound together"),
    SettingsEntry("Save Player's last state", "App behaviour", "general_settings", "persistent queue restore track state"),
    SettingsEntry("Local song auto metadata", "App behaviour", "general_settings", "match tags id3 covers lyrics"),
    SettingsEntry("Disable auto-playing YouTube video", "App behaviour", "general_settings", "music video live autoplay cover"),
    SettingsEntry("Translations from YouTube", "App behaviour", "general_settings", "captions translate lyrics language"),
    SettingsEntry("Sync lyrics to music videos", "App behaviour", "general_settings", "lyrics video timing intro skit"),
    SettingsEntry("Don't skip on failure", "App behaviour", "general_settings", "fail stream error stay skip"),
    SettingsEntry("Skip timeout failure", "App behaviour", "general_settings", "skip timeout seconds failed stream"),
    SettingsEntry("Skip Silence", "App behaviour", "general_settings", "silence trim gap interval"),
    SettingsEntry("Audio format badge", "App behaviour", "general_settings", "format codec opus aac flac badge title quality"),
    SettingsEntry("Show sample rate", "App behaviour", "general_settings", "sample rate khz hz format badge quality"),
    SettingsEntry("Always enable Youtube background", "App behaviour", "general_settings", "youtube video background canvas"),
    SettingsEntry("Enable animated canvas", "App behaviour", "general_settings", "canvas animation spotify looping visual"),
    SettingsEntry("High quality bloom", "App behaviour", "general_settings", "lyrics bloom quality extra soft pass glow"),
    SettingsEntry("Prefer USB DAC", "App behaviour", "general_settings", "usb dac headphones output external audio"),
    SettingsEntry("USB DAC passthrough", "App behaviour", "general_settings", "bit perfect bitperfect passthrough sample rate dac usb resample"),
    SettingsEntry("Hi-res output", "App behaviour", "general_settings", "hi res 24 bit float precision lossless flac quality"),
    SettingsEntry("Hi-fi resampler", "App behaviour", "general_settings", "resample sample rate 44.1 48 khz sinc src quality filter"),
    SettingsEntry("Disable description on settings", "App behaviour", "general_settings", "hide subtitle text rows titles only"),

    // Audio Lab
    SettingsEntry("Audio Lab", "Audio Lab", "audio_lab", "sound effects equalizer fx quality"),
    SettingsEntry("Crossfade", "Audio Lab", "crossmix_settings", "crossmix automix transition gapless blend"),
    SettingsEntry("Equalizer", "Audio Lab", "equalizer", "eq bass treble band parametric graph autoeq headphones correction preset"),
    SettingsEntry("AutoEQ", "Audio Lab", "equalizer", "headphones earbuds correction harman database model"),
    SettingsEntry("Audio Quality", "Audio Lab", "audio_quality", "bitrate stream high low lossless hi-res flac monochrome"),
    SettingsEntry("Reverb", "Audio Lab", "audio_fx", "room hall echo space fx effect"),
    SettingsEntry("Loudness", "Audio Lab", "audio_fx", "boost louder compressor limiter night mode fx"),
    SettingsEntry("Audio Normalization", "Audio Lab", "audio_fx", "even out songs loudness volume gain balance replaygain"),
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
    SettingsEntry("Discord", "Integrations", DiscordRoute, "discord rich presence rpc status activity listening playing profile"),
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

    // Experimental
    SettingsEntry("Import & sync", "Experimental", "experimental_settings", "import spotify apple music deezer tidal tunemymusic csv youtube sync export backup transfer"),
    SettingsEntry("Big disc cover styles", "Experimental", "experimental_settings", "disc vinyl cover"),
    SettingsEntry("Music haptics", "Experimental", "experimental_settings", "vibration haptic"),
    SettingsEntry("Cipher", "Experimental", "experimental_settings", "player script signature refresh"),
    SettingsEntry("Playback diagnostics", "Experimental", "experimental_settings", "logs potoken stream client debug"),
    SettingsEntry("Repeat initial setup", "Experimental", "experimental_settings", "onboarding wizard setup"),
    SettingsEntry("Show where songs play from", "Experimental", "experimental_settings", "source banner toast apple music monochrome youtube"),
    SettingsEntry("Force playback", "Experimental", "experimental_settings", "fallback skip error stream failed youtube"),
)

private data class RecommendedSetting(
    val titleRes: Int,
    val breadcrumbRes: Int,
    val iconRes: Int,
    val onClick: (NavController) -> Unit,
)

private val RecommendedOptions = listOf(
    RecommendedSetting(
        titleRes = R.string.settings_rec_blur,
        breadcrumbRes = R.string.settings_crumb_customisation,
        iconRes = R.drawable.contrast,
        onClick = { nav ->
            SettingsHighlight.pending = "Blur"
            nav.navigate("customisation_settings")
        }
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_crossfade,
        breadcrumbRes = R.string.settings_crumb_audio_lab,
        iconRes = R.drawable.linear_scale,
        onClick = { nav ->
            nav.navigate("crossmix_settings")
        }
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_equalizer,
        breadcrumbRes = R.string.settings_crumb_audio_lab,
        iconRes = R.drawable.equalizer,
        onClick = { nav ->
            nav.navigate("equalizer")
        }
    )
)

private val BubbleColor = Color(0xFF1C1C1E)

// the header holds the mochy mark with the big "Settings" under it, taller than search's title-only header
private val SettingsTitleBlockHeight = 88.dp
private val SettingsMarkSize = 32.dp
private val IconBgColor = Color(0xFF333338)

@Composable
fun MochySettingsScreen(navController: NavController) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val glassState = remember { GlassState() }

    val (profilePicUri) = rememberPreference(ProfilePicUriKey, "")

    var query by remember { mutableStateOf(TextFieldValue()) }
    var isSearchFocused by remember { mutableStateOf(false) }

    val isSearching = isSearchFocused || query.text.isNotBlank()

    BackHandler(enabled = isSearching) {
        query = TextFieldValue()
        isSearchFocused = false
        focusManager.clearFocus()
    }

    val recommendedItem = remember { RecommendedOptions.random() }

    val context = LocalContext.current
    val results = remember(query.text, context) {
        val q = query.text.trim()
        if (q.isBlank()) {
            emptyList()
        } else {
            SettingsIndex.filter {
                it.title.contains(q, ignoreCase = true) ||
                    (it.titleRes ?: LocalizedSettingsTitles[it.title])?.let { id -> context.getString(id).contains(q, ignoreCase = true) } == true ||
                    (it.pageRes ?: LocalizedSettingsPages[it.page])?.let { id -> context.getString(id).contains(q, ignoreCase = true) } == true ||
                    it.keywords.contains(q, ignoreCase = true) ||
                    it.page.contains(q, ignoreCase = true)
            }
        }
    }

    // Same latch-and-settle collapse as Library/Search: crossing the threshold always finishes
    // the morph, even after the user stops scrolling mid-gesture.
    val collapseProgress by rememberScrollCollapseProgress(scrollState)

    // Animated fade for content when search bar is tapped
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSearching) 0f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Scrollable content. glassRoot wraps only this box - never a sibling that also reads
        // glassState to draw the blur, such as SearchGlassTopBar below - or that sibling's draw
        // gets captured into its own backdrop RenderNode, which is a direct self-reference and
        // crashes RenderThread with a native stack overflow (see SearchScreen.kt for the same
        // split: glassRoot on the content box only, the glass top bar as a separate sibling).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { !scrollState.isScrollInProgress && collapseProgress > 0f }),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(searchTopBarHeight(titleBlockHeight = SettingsTitleBlockHeight)))

            // Search results view when user types
            AnimatedVisibility(
                visible = query.text.isNotBlank(),
                enter = fadeIn(tween(160, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(160, easing = FastOutSlowInEasing)) { 20 },
                exit = fadeOut(tween(120, easing = FastOutSlowInEasing))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (results.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_nothing_matches, query.text),
                            fontFamily = InterFontFamily,
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                        )
                    } else {
                        results.forEach { entry ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(BubbleColor)
                                    .clickable {
                                        SettingsHighlight.pending = entry.highlightKey
                                        navController.navigate(entry.route)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(IconBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.settings),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(11.dp))
                                    Column {
                                        Text(
                                            text = (entry.titleRes ?: LocalizedSettingsTitles[entry.title])?.let { stringResource(it) } ?: entry.title,
                                            fontFamily = InterFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = (entry.pageRes ?: LocalizedSettingsPages[entry.page])?.let { stringResource(it) } ?: entry.page,
                                            fontFamily = InterFontFamily,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.55f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Normal Settings Content (fades smoothly to black when search is focused)
            if (contentAlpha > 0.001f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = contentAlpha }
                ) {
                    // Recommended Item Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(BubbleColor)
                            .clickable { recommendedItem.onClick(navController) }
                            .padding(18.dp)
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.settings_recommended_item),
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(IconBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(recommendedItem.iconRes),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = stringResource(recommendedItem.titleRes),
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = stringResource(recommendedItem.breadcrumbRes),
                                        fontFamily = InterFontFamily,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.55f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(0.8.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Separate Bubble Pills for Categories (No outlines, individually bubble-wrapped)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // in the order of the design: sound first, then how the app acts and looks, then the rest
                        CategoryBubble(
                            title = stringResource(R.string.settings_audio_lab_category),
                            iconRes = R.drawable.equalizer,
                            onClick = { navController.navigate("audio_lab") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.settings_app_behaviour),
                            iconRes = R.drawable.settings,
                            onClick = { navController.navigate("general_settings") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.settings_customisation_category),
                            iconRes = R.drawable.palette,
                            onClick = { navController.navigate("customisation_settings") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.storage),
                            iconRes = R.drawable.storage,
                            onClick = { navController.navigate("storage_settings") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.integrations),
                            iconRes = R.drawable.integration,
                            onClick = { navController.navigate(IntegrationsRoute) }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.settings_modules_category),
                            iconRes = R.drawable.library_add,
                            onClick = { navController.navigate("modules") }
                        )
                    }
                }
            }

            val bottomInset = com.mochyy.mochymusic.LocalPlayerAwareWindowInsets.current.asPaddingValues()
            val bottomPadding = bottomInset.calculateBottomPadding()
            val bottomSpacerHeight = maxOf(340.dp, bottomPadding + 160.dp)

            // Headroom spacer for smooth scrolling past collapse threshold and bottom bar
            Spacer(modifier = Modifier.height(bottomSpacerHeight))
        }
        }

        // Top bar: Mochy mark + "Settings" title slide away on scroll (progressive glass blur
        // behind the gradient, matching Library/Search) while the search field glides up to sit
        // beside the profile avatar.
        SearchGlassTopBar(
            glassState = glassState,
            progressProvider = { collapseProgress },
            pureBlack = true,
            title = stringResource(R.string.settings),
            titleBlockHeight = SettingsTitleBlockHeight,
            titleContent = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mochy_mark),
                        contentDescription = "Mochy",
                        tint = Color.White,
                        modifier = Modifier.size(SettingsMarkSize),
                    )
                    Text(
                        text = stringResource(R.string.settings),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            },
            trailing = {
                // at rest it sits beside the mark like the design, and drops to the search field's middle as the header folds
                SearchAvatar(
                    modifier = Modifier.graphicsLayer {
                        val lift = (SettingsTitleBlockHeight / 2 - SettingsMarkSize / 2).toPx()
                        translationY = -(1f - collapseProgress) * lift
                    },
                    imageUrl = profilePicUri.ifBlank { null },
                    onClick = {
                        // back to the profile page these were opened from, not a second copy of it on top
                        if (!navController.popBackStack("settings", inclusive = false)) navController.navigateToTab("settings")
                    },
                )
            },
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = {},
                placeholder = stringResource(R.string.settings_search_placeholder),
                focused = isSearchFocused,
                onFocusChanged = { isSearchFocused = it },
                trailing = if (query.text.isNotEmpty()) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.settings_clear_search),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { query = TextFieldValue() },
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
internal fun CategoryBubble(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(BubbleColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = Color.White,
            )
        }
    }
}
