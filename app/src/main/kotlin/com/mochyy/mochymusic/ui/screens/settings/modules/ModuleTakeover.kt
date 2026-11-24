// a module page standing in for one of mochy's own: which one takes the page being opened, if any, and that page with a way back to mochy's (docs/wiki, taking over pages)

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.di.ModulesEntryPoint
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.Takeover
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.stableStatusBarsPadding
import dagger.hilt.android.EntryPointAccessors

/** the name a route goes by for a takeover: its path without arguments, so album/{albumId} is album and library/songs stays library/songs */
internal fun takeoverName(route: String): String =
    route.substringBefore('?').split('/').filter { it.isNotEmpty() && !it.startsWith("{") }.joinToString("/")

/** the app's nav controller, for a takeover page's buttons and rows; every page of the graph sits under it */
val LocalModuleNavController = staticCompositionLocalOf<NavController?> { null }

/** [original] as it is, or the module page that takes it over while its module is on and its condition holds */
@Composable
internal fun ModuleTakeover(route: String, entry: NavBackStackEntry, original: @Composable () -> Unit) {
    val name = remember(route) { takeoverName(route) }
    val navController = LocalModuleNavController.current
    val takeover = if (name !in AllowedTakeovers) null else pickTakeover(ActiveSkin.takeovers, name)
    // mochy's own page, once someone asked for it from the module's; kept while the page stays on the stack
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    if (takeover == null || showOriginal || navController == null) {
        original()
        return
    }
    TakeoverPage(takeover, name, entry, navController, onOriginal = { showOriginal = true }, original = original)
}

// the page's own name first, then the family it belongs to, the highest module whose condition holds winning
private fun pickTakeover(all: Map<String, List<Takeover>>, name: String): Takeover? {
    if (all.isEmpty()) return null
    val names = listOfNotNull(name, familyOf(name))
    return names.firstNotNullOfOrNull { key -> all[key]?.firstOrNull { it.page.condition?.holdsFor(it.values) != false } }
}

// one name for every kind of playlist page, so a module can take them all in one go
private fun familyOf(name: String): String? = when (name) {
    "local_playlist", "online_playlist", "auto_playlist", "top_playlist", "cache_playlist" -> "playlist"
    "search_input" -> "search"
    "mochy_settings" -> "settings_hub"
    else -> null
}

@Composable
private fun TakeoverPage(
    takeover: Takeover,
    name: String,
    entry: NavBackStackEntry,
    navController: NavController,
    onOriginal: () -> Unit,
    original: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // the registry straight from hilt, since the modules screen's view model starts update checks this page has no need for
    val registry = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, ModulesEntryPoint::class.java).moduleRegistry() }
    val entries by registry.entries.collectAsState()
    val module = entries.firstOrNull { it.id == takeover.skin.moduleId }
    if (module == null) {
        original()
        return
    }
    val subject = rememberPageSubject(name, entry)
    if (name == "mochy_settings") {
        // settings takeover always pins a native escape bar above the module page so Modules and native Settings can never be hidden
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            ModuleSafetyBanner(
                moduleName = module.module.info.name,
                onOpenModules = { navController.navigate("modules") },
                onOriginal = onOriginal,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ModulePage(takeover.page, module, takeover.skin, registry::setField, navController, subject, onOriginal = onOriginal, backLabel = null, insetTop = false)
            }
        }
    } else {
        ModulePage(takeover.page, module, takeover.skin, registry::setField, navController, subject, onOriginal = onOriginal, backLabel = null)
    }
}

/** un-hideable native safety strip shown above a custom Settings takeover */
@Composable
private fun ModuleSafetyBanner(
    moduleName: String,
    onOpenModules: () -> Unit,
    onOriginal: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141417))
            .stableStatusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(painterResource(R.drawable.puzzle), null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Text(
            stringResource(R.string.module_settings_styled_by, moduleName),
            fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SafetyChip(stringResource(R.string.module_settings_open_modules), onOpenModules)
        SafetyChip(stringResource(R.string.module_settings_original), onOriginal)
    }
}

@Composable
private fun SafetyChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .border(0.75.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color.White, maxLines = 1)
    }
}

// strict allowlist: login, backup, proxy, cipher, discord, scrobble, experimental, and module-management pages can NEVER be taken over
internal val AllowedTakeovers = setOf(
    "home", "history", "stats", "section_detail", "coming_soon",
    "search", "search_input", "explore", "mood_and_genres", "new_release",
    "library", "library/songs", "library/playlists", "library/added", "local_genre",
    "library/local/added", "library/local/albums", "library/local/artists", "library/local/playlists",
    "library/cached/added", "library/cached/albums", "library/cached/artists", "library/cached/playlists",
    "library/imported", "library/imported/playlists", "library/imported/albums",
    "album", "artist", "playlist", "local_playlist", "online_playlist", "auto_playlist", "top_playlist", "cache_playlist",
    "mochy_settings",
)
