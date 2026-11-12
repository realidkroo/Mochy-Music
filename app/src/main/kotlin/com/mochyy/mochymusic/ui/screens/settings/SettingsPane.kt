package com.mochyy.mochymusic.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.screens.settings.devspace.DevPlaygroundRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.ImportSyncRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.IntegrationsRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.ScrobbleServiceRoute
import com.mochyy.mochymusic.ui.screens.settings.integrations.discord.DiscordRoute
import com.mochyy.mochymusic.ui.screens.settings.modules.ModulesRoute
import com.mochyy.mochymusic.ui.screens.settings.storage.StorageRoute
import com.mochyy.mochymusic.ui.theme.InterFontFamily

// the settings list's own route; on a wide screen it stays at the left while its pages open beside it
const val SettingsListRoute = "mochy_settings"

// the pages that open beside the list on a wide screen; anything else (an artist, the player's equalizer from home) takes the whole width
val SettingsPaneRoutes = setOf(
    SettingsListRoute,
    "audio_lab",
    "equalizer",
    "audio_fx",
    "audio_quality",
    "crossmix_settings",
    "general_settings",
    "customisation_settings",
    "gesture_settings",
    StorageRoute,
    IntegrationsRoute,
    DiscordRoute,
    ScrobbleServiceRoute,
    ProxySettingsRoute,
    ModulesRoute,
    "module/{id}",
    "cipher_settings",
    "experimental_settings",
    "playback_diagnostics",
    ImportSyncRoute,
    "import_providers",
    "import_tunemymusic",
    "import_progress",
    "youtube_sync",
    DevPlaygroundRoute,
)

// how wide the list is beside the open page: a phone's column on a phone on its side, a little more on a tablet
private fun settingsListWidth(windowWidth: androidx.compose.ui.unit.Dp) = (windowWidth * 0.38f).coerceIn(300.dp, 400.dp)

// the list on a wide screen: the page open beside it, so its bubble stays lit
@Immutable
class SettingsPane(val selectedRoute: String?)

// the list stays at the left while it's on the stack and the page in front is one of its own
fun showsSettingsPane(backStack: List<NavBackStackEntry>, currentRoute: String?): Boolean =
    currentRoute in SettingsPaneRoutes && backStack.any { it.destination.route == SettingsListRoute }

// the page the list opened, read off the stack so a page opened from inside it keeps its category lit
fun settingsPaneSelection(backStack: List<NavBackStackEntry>): String? {
    val listAt = backStack.indexOfLast { it.destination.route == SettingsListRoute }
    if (listAt < 0) return null
    return backStack.getOrNull(listAt + 1)?.destination?.route
}

// a page from the list opens beside it in place of the one that was open, so back goes to the list rather than through every page tried
fun NavController.openSettingsPage(route: String) {
    navigate(route) {
        popUpTo(SettingsListRoute) { inclusive = false }
        launchSingleTop = true
    }
}

// the list at the left of a wide screen, a hairline between it and the page beside it
@Composable
fun SettingsListPane(navController: NavController, selectedRoute: String?, modifier: Modifier = Modifier) {
    val windowWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    Box(modifier = modifier.width(settingsListWidth(windowWidth)).fillMaxHeight()) {
        MochySettingsScreen(navController = navController, pane = SettingsPane(selectedRoute))
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(0.8.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.1f)),
        )
    }
}

// the space beside the list before anything is picked
@Composable
fun SettingsPanePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mochy_mark),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(R.string.settings_pane_hint),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
