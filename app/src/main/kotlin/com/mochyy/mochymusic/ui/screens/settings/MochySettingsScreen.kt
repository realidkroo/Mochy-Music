// MochySettingsScreen.kt

package com.mochyy.mochymusic.ui.screens.settings

import com.mochyy.mochymusic.modules.browser.ModuleFeed
import com.mochyy.mochymusic.ui.screens.settings.modules.ModuleBrowserRoute
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
import androidx.compose.runtime.derivedStateOf
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
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.ui.screens.devices.showDeviceIntegration
import com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationsRoute
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

internal val BubbleColor = Color(0xFF1C1C1E)
private val SelectedBubbleColor = Color(0xFF3A3A3E)

// the header holds the mochy mark with the big "Settings" under it, taller than search's title-only header
private val SettingsTitleBlockHeight = 88.dp
private val SettingsMarkSize = 32.dp
internal val IconBgColor = Color(0xFF333338)

@Composable
fun MochySettingsScreen(navController: NavController, pane: SettingsPane? = null) {
    val focusManager = LocalFocusManager.current
    val sheets = com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState.current
    val devices = com.mochyy.mochymusic.ui.screens.devices.rememberDevicesLink()
    // on a wide screen the list stays put and its pages open beside it; device integration is a sheet over either
    val open: (String) -> Unit = { route ->
        when {
            route == com.mochyy.mochymusic.ui.screens.devices.DeviceIntegrationRoute -> sheets.showDeviceIntegration(devices)
            pane != null -> navController.openSettingsPage(route)
            else -> navController.navigate(route)
        }
    }
    val selected = pane?.selectedRoute
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

    val context = LocalContext.current
    // picked again each time the list opens, so a tip that was just followed stops showing
    val recommendations = remember(context) { pickRecommendations(context) }
    val results = remember(query.text, context) { searchSettings(context, query.text) }

    // Same latch-and-settle collapse as Library/Search: crossing the threshold always finishes
    // the morph, even after the user stops scrolling mid-gesture.
    val collapseProgress by rememberScrollCollapseProgress(scrollState)

    // Animated fade for content when search bar is tapped
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSearching) 0f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )
    // flips only at the ends of the fade, so the page isn't recomposed on every frame of it
    val showContent by remember { derivedStateOf { contentAlpha > 0.001f } }

    val customBg = ActiveSkin.appBackground?.let { bg ->
        bg.background.screens.isEmpty() || "mochy_settings" in bg.background.screens || "settings" in bg.background.screens
    } == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (customBg) Color.Transparent else Color.Black),
    ) {
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
                                        open(entry.route)
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
            if (showContent) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = contentAlpha }
                ) {
                    RecommendedCard(recommendations = recommendations, open = open)

                    // a build without the module feed (most forks) has no store to open
                    if (ModuleFeed.FeedUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        ModuleStoreCard(
                            onClick = { navController.navigate(ModuleBrowserRoute) },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
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
                            selected = selected == "audio_lab",
                            onClick = { open("audio_lab") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.settings_playback_category),
                            iconRes = R.drawable.play,
                            selected = selected == PlaybackSettingsRoute,
                            onClick = { open(PlaybackSettingsRoute) }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.settings_now_playing),
                            iconRes = R.drawable.lyrics,
                            selected = selected == NowPlayingSettingsRoute,
                            onClick = { open(NowPlayingSettingsRoute) }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.settings_app_behaviour),
                            iconRes = R.drawable.settings,
                            selected = selected == "general_settings",
                            onClick = { open("general_settings") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.settings_customisation_category),
                            iconRes = R.drawable.palette,
                            selected = selected == "customisation_settings",
                            onClick = { open("customisation_settings") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.storage),
                            iconRes = R.drawable.storage,
                            selected = selected == "storage_settings",
                            onClick = { open("storage_settings") }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.integrations),
                            iconRes = R.drawable.integration,
                            selected = selected == IntegrationsRoute,
                            onClick = { open(IntegrationsRoute) }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.devices_title),
                            iconRes = R.drawable.cast_connected,
                            selected = false,
                            onClick = { open(com.mochyy.mochymusic.ui.screens.devices.DeviceIntegrationRoute) }
                        )

                        CategoryBubble(
                            title = stringResource(R.string.settings_modules_category),
                            iconRes = R.drawable.library_add,
                            selected = selected == "modules",
                            onClick = { open("modules") }
                        )

                        // pages the modules that are on bring to settings, right under modules
                        com.mochyy.mochymusic.ui.screens.settings.modules.ModuleSettingsPages(open = open, selected = selected)
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
    // lit while its page is open beside the list on a wide screen
    selected: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) SelectedBubbleColor else BubbleColor)
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
