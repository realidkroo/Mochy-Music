// GestureScreen.kt

package com.mochyy.mochymusic.ui.screens.gesture

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerAwareWindowInsets
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.HomeStyle
import com.mochyy.mochymusic.constants.HomeStyleKey
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.constants.PlayerStyleKey
import com.mochyy.mochymusic.constants.PlaylistStyle
import com.mochyy.mochymusic.constants.PlaylistStyleKey
import com.mochyy.mochymusic.constants.SwipeLeftActionKey
import com.mochyy.mochymusic.constants.SwipeRightActionKey
import com.mochyy.mochymusic.constants.ThumbnailCornerRadiusKey
import com.mochyy.mochymusic.ui.component.DefaultSwipeLeft
import com.mochyy.mochymusic.ui.component.DefaultSwipeRight
import com.mochyy.mochymusic.ui.component.HomeCardCornerRadius
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState
import com.mochyy.mochymusic.ui.component.SheetIcon
import com.mochyy.mochymusic.ui.component.SheetOptionRow
import com.mochyy.mochymusic.ui.component.SongSwipeAction
import com.mochyy.mochymusic.ui.component.bleedHorizontally
import com.mochyy.mochymusic.ui.screens.audiolab.LabBackButton
import com.mochyy.mochymusic.ui.screens.audiolab.LabCard
import com.mochyy.mochymusic.ui.screens.audiolab.LabColors
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.constants.PlayerSwipeDownAnywhereKey
import com.mochyy.mochymusic.constants.PlayerSwipeUpFollowsKey
import com.mochyy.mochymusic.ui.screens.audiolab.LabToggleRow
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.ui.utils.stableStatusBars
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mochyy.mochymusic.ui.component.LocalSettingsScroll
import com.mochyy.mochymusic.ui.component.SettingsHighlight
import com.mochyy.mochymusic.ui.component.SettingsPlacement
import com.mochyy.mochymusic.ui.component.SettingsScroll
import com.mochyy.mochymusic.ui.component.reveal
import com.mochyy.mochymusic.ui.component.SubSettingsSearchBar
import com.mochyy.mochymusic.ui.screens.settings.BottomBarHighlightKey
import com.mochyy.mochymusic.ui.screens.settings.TopBarHighlightKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter

private val CardPadding = 20.dp

// past this the page stops widening, so a tablet reads like a wide phone
private val PageMaxWidth = 680.dp

private val PageTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 38.sp,
    letterSpacing = (-0.04).em,
)

@Composable
fun GestureScreen(navController: NavController) = CustomisationScreen(navController)

// the customize page's parts in order, with what a search or a settings result can find each one by
private enum class CustomiseSection(@StringRes val titleRes: Int, val keys: List<String>, val words: String) {
    ICONS(R.string.customise_icons, listOf("Icons", "App icon"), "icon icons app launcher home screen mochy outline anime material you white black dark transparent"),
    ROUNDING(R.string.customise_cover_rounding, listOf("Cover rounding"), "cover rounding corner radius square rounded art"),
    PLAYLIST(R.string.customise_playlist_styles, listOf("Playlist styles"), "playlist styles flow classic minimal header"),
    PLAYER(R.string.customise_player_styles, listOf("Player styles"), "player styles flagship turntable ipod poster timeline lyrics editor customize"),
    HOME(R.string.customise_home_styles, listOf("Home styles"), "home styles mochyy listen now hop in hero"),
    BLUR(R.string.appearance_blur, listOf("Blur", "Progressive blur", "Blur quality", "Blur strength"), "blur glass frosted progressive flat quality low high strength px"),
    ELEMENT_BLUR(R.string.customise_element_blur, listOf("Element blur"), "element blur lyrics lines cover zoom titles menus page swipes"),
    ANIMATION(R.string.appearance_animation, listOf("Animation", "Animation speed"), "animation speed motion transitions off faster slower"),
    LYRICS(R.string.appearance_lyrics_animation, listOf("Lyrics animation"), "lyrics animation style 1 2 boring plain shader glow wave"),
    TOP_BAR(R.string.customise_top_bar, listOf(TopBarHighlightKey), "top bar status bar player clock battery icons hide"),
    BOTTOM_BAR(R.string.customise_bottom_bar, listOf(BottomBarHighlightKey), "bottom bar navigation bar nav buttons gesture handle back home recents player hide"),
    GESTURE(R.string.customise_gesture, listOf("Gesture", "Swipe right", "Swipe left", "Swipe down anywhere to close"), "gesture swipe left right slide songs player close down");

    // launcher icons only switch on android 8+, where launchers draw adaptive icons
    val available: Boolean get() = this != ICONS || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O

    // every word typed has to show up in the section's english words, its names, or its title in the app's language
    fun matches(query: String, shownTitle: String): Boolean {
        val words = query.trim().lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val haystack = (this.words + " " + keys.joinToString(" ") + " " + shownTitle).lowercase()
        return words.all { it in haystack }
    }
}

// settings > customisation: cover rounding, style pickers, blur, animation, lyrics, the player's top and bottom bars and song swipe gestures
@Composable
fun CustomisationScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }
    val statusTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val titles = CustomiseSection.entries.associateWith { stringResource(it.titleRes) }
    val shown = CustomiseSection.entries.filter { it.available && it.matches(query, titles.getValue(it)) }
    val scroll = rememberScrollState()
    // nothing stays pinned over this page, so a highlighted section lands just under the status bar
    val settingsScroll = remember(scroll, statusTop) { SettingsScroll(scroll, statusTop + 16.dp) }

    // a plain black page like the mock: back button, the title, the search, then the cards
    CompositionLocalProvider(LocalSettingsScroll provides settingsScroll) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(scroll),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = PageMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = CustomisePageMargin)
                    .padding(top = statusTop + 18.dp, bottom = bottomInset + 32.dp),
            ) {
                LabBackButton(onBack = { navController.navigateUp() })
                Spacer(Modifier.height(14.dp))
                Text(text = stringResource(R.string.customise_title), style = PageTitleStyle, color = Color.White)
                Spacer(Modifier.height(16.dp))
                SubSettingsSearchBar(query = query, onQueryChange = { query = it })
                Spacer(Modifier.height(18.dp))

                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.customise_nothing_matches, query),
                        fontFamily = InterFontFamily,
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    )
                }
                shown.forEachIndexed { index, section ->
                    if (index > 0) SectionRule()
                    Box(Modifier.highlightTarget(section.keys)) {
                        CustomiseSectionContent(section)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomiseSectionContent(section: CustomiseSection) {
    val pagerBleed = Modifier.bleedHorizontally(CustomisePageMargin)
    when (section) {
        CustomiseSection.ICONS -> IconsSection()
        CustomiseSection.ROUNDING -> {
            val (coverRadiusPx, onCoverRadiusChange) = rememberPreference(ThumbnailCornerRadiusKey, defaultValue = DefaultCoverRadius)
            CoverRoundingSection(
                radiusPx = coverRadiusPx,
                onRadiusChange = { next ->
                    onCoverRadiusChange(next)
                    HomeCardCornerRadius = next.dp
                },
            )
        }
        CustomiseSection.PLAYLIST -> {
            val (coverRadiusPx) = rememberPreference(ThumbnailCornerRadiusKey, defaultValue = 25f)
            val (playlistStyle, onPlaylistStyleChange) = rememberEnumPreference(PlaylistStyleKey, defaultValue = PlaylistStyle.FLOW)
            StylePagerSection(
                category = stringResource(R.string.customise_playlist_styles),
                styles = PlaylistStyle.entries,
                selected = playlistStyle,
                nameOf = { it.displayName },
                onSelect = onPlaylistStyleChange,
                modifier = pagerBleed,
            ) { PlaylistStylePreview(style = it, coverRadius = coverRadiusPx.dp) }
        }
        CustomiseSection.PLAYER -> PlayerSection()
        CustomiseSection.HOME -> {
            val (coverRadiusPx) = rememberPreference(ThumbnailCornerRadiusKey, defaultValue = 25f)
            val (homeStyle, onHomeStyleChange) = rememberEnumPreference(HomeStyleKey, defaultValue = HomeStyle.MOCHYY)
            StylePagerSection(
                category = stringResource(R.string.customise_home_styles),
                styles = HomeStyle.entries,
                selected = homeStyle,
                nameOf = { it.displayName },
                onSelect = onHomeStyleChange,
                modifier = pagerBleed,
            ) { HomeStylePreview(style = it, coverRadius = coverRadiusPx.dp) }
        }
        CustomiseSection.BLUR -> BlurSection()
        CustomiseSection.ELEMENT_BLUR -> ElementBlurSection()
        CustomiseSection.ANIMATION -> AnimationSection()
        CustomiseSection.LYRICS -> LyricsStyleSection(modifier = pagerBleed)
        CustomiseSection.TOP_BAR -> TopBarSection()
        CustomiseSection.BOTTOM_BAR -> BottomBarSection()
        CustomiseSection.GESTURE -> GestureSection()
    }
}

// the player has its own editor, so its card opens that on the player itself
@Composable
private fun PlayerSection() {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val playerSheetState = LocalPlayerBottomSheetState.current
    val playerStyle by rememberEnumPreference(PlayerStyleKey, defaultValue = PlayerStyle.FLAGSHIP)
    val playFirst = stringResource(R.string.customise_play_first)
    PlayerStyleCard(
        selected = playerStyle,
        onOpenEditor = {
            val hasMedia = playerConnection?.player?.currentMediaItem != null
            if (hasMedia) {
                playerSheetState?.expandSoft()
                playerConnection?.requestPlayerStudio()
            } else {
                Toast.makeText(context, playFirst, Toast.LENGTH_SHORT).show()
            }
        },
    )
}

@Composable
private fun GestureSection() {
    val (right, setRight) = rememberEnumPreference(SwipeRightActionKey, defaultValue = DefaultSwipeRight)
    val (left, setLeft) = rememberEnumPreference(SwipeLeftActionKey, defaultValue = DefaultSwipeLeft)
    Column {
        Text(
            text = stringResource(R.string.customise_gesture),
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = (-0.05).em,
            ),
            color = Color.White,
        )
        Spacer(Modifier.height(14.dp))
        SwipeCard(
            title = stringResource(R.string.customise_swipe_right),
            subtitle = stringResource(R.string.customise_swipe_right_desc),
            toRight = true,
            action = right,
            onPick = setRight,
        )
        Spacer(Modifier.height(14.dp))
        SwipeCard(
            title = stringResource(R.string.customise_swipe_left),
            subtitle = stringResource(R.string.customise_swipe_left_desc),
            toRight = false,
            action = left,
            onPick = setLeft,
        )
        Spacer(Modifier.height(14.dp))
        PlayerGestureCard()
    }
}

// a section a search result or a related row points at scrolls into view and its outline flashes once
@Composable
private fun Modifier.highlightTarget(keys: List<String>): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val flash = remember { Animatable(0f) }
    val settingsScroll = LocalSettingsScroll.current
    val density = LocalDensity.current
    val placement = remember { SettingsPlacement() }
    LaunchedEffect(keys) {
        // watched, not keyed on: clearing the request restarted the effect and cut it off before it scrolled
        snapshotFlow { SettingsHighlight.pending }.filter { it in keys }.collect {
            SettingsHighlight.pending = null
            // let the page finish opening first, or it scrolls while still sliding in
            delay(320)
            val target = placement.coords
            if (settingsScroll != null && target != null) settingsScroll.reveal(target, density) else requester.bringIntoView()
            flash.animateTo(1f, tween(220))
            flash.animateTo(0f, tween(900))
        }
    }
    return this
        .bringIntoViewRequester(requester)
        .onPlaced { placement.coords = it }
        .drawWithContent {
            drawContent()
            val a = flash.value
            if (a > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.9f * a),
                    cornerRadius = CornerRadius(22.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
}

@Composable
private fun SectionRule() {
    HorizontalDivider(
        thickness = 1.dp,
        color = Color.White.copy(alpha = 0.1f),
        modifier = Modifier.padding(top = 18.dp, bottom = 20.dp),
    )
}

@Composable
private fun SwipeCard(
    title: String,
    subtitle: String,
    toRight: Boolean,
    action: SongSwipeAction,
    onPick: (SongSwipeAction) -> Unit,
) {
    val sheets = LocalMenuState.current
    LabCard(padding = CardPadding) {
        Text(
            title,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = (-0.02).em,
            color = LabColors.Primary,
        )
        Text(
            subtitle,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = LabColors.Secondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(20.dp))
        // the demo runs edge to edge, like the mock: the row slides off the card's side
        SwipePreview(action, toRight, Modifier.bleedHorizontally(CardPadding))
        Spacer(Modifier.height(20.dp))
        ActionDropdown(action) {
            sheets.show {
                ActionPicker(selected = action) { picked ->
                    onPick(picked)
                    sheets.dismiss()
                }
            }
        }
    }
}

@Composable
private fun ActionDropdown(action: SongSwipeAction, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(LabColors.Row)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        ActionOptionBadge(action = action)
        Crossfade(targetState = action, animationSpec = tween(180), modifier = Modifier.weight(1f), label = "swipeAction") { shown ->
            Text(
                shown.title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                letterSpacing = (-0.01).em,
                color = LabColors.Primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Icon(
            painter = painterResource(R.drawable.expand_more),
            contentDescription = null,
            tint = LabColors.Secondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ActionPicker(selected: SongSwipeAction, onPick: (SongSwipeAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        SongSwipeAction.entries.forEach { option ->
            SheetOptionRow(
                title = option.title,
                onClick = { onPick(option) },
                leading = { ActionOptionBadge(action = option) },
                trailing = {
                    if (option == selected) SheetIcon(R.drawable.check, size = 18.dp)
                },
            )
        }
    }
}

// colored circle badge so each gesture option shows its pill color in the selector and sheet
@Composable
private fun ActionOptionBadge(action: SongSwipeAction) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(action.color.copy(alpha = 0.22f)),
    ) {
        Icon(
            painter = painterResource(action.icon),
            contentDescription = null,
            tint = action.color,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * The open player's own gestures. Out of the box only its top strip and its cover close it with a
 * swipe down, so a crooked swipe over the controls can't throw it away; this brings back closing
 * it from anywhere for those who'd rather not reach for the top of a tall phone (#121).
 */
@Composable
private fun PlayerGestureCard() {
    val (anywhere, setAnywhere) = rememberPreference(PlayerSwipeDownAnywhereKey, defaultValue = false)
    val (follows, setFollows) = rememberPreference(PlayerSwipeUpFollowsKey, defaultValue = true)
    LabCard(padding = CardPadding) {
        Text(
            stringResource(R.string.customise_player_gestures),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = (-0.02).em,
            color = LabColors.Primary,
        )
        Text(
            stringResource(R.string.customise_player_gestures_desc),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = LabColors.Secondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(14.dp))
        LabToggleRow(
            title = stringResource(R.string.customise_swipe_down_anywhere),
            subtitle = stringResource(R.string.customise_swipe_down_anywhere_desc),
            checked = anywhere,
            onCheckedChange = setAnywhere,
        )
        LabToggleRow(
            title = stringResource(R.string.customise_swipe_up_follows),
            subtitle = stringResource(R.string.customise_swipe_up_follows_desc),
            checked = follows,
            onCheckedChange = setFollows,
        )
    }
}
