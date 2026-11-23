// a module page standing in for one of mochy's own: which one takes the page being opened, if any, and that page with a way back to mochy's (docs/wiki, taking over pages)

package com.mochyy.mochymusic.ui.screens.settings.modules

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.mochyy.mochymusic.di.ModulesEntryPoint
import com.mochyy.mochymusic.modules.skin.ActiveSkin
import com.mochyy.mochymusic.modules.skin.Takeover
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
    ModulePage(takeover.page, module, takeover.skin, registry::setField, navController, subject, onOriginal = onOriginal, backLabel = null)
}

// strict allowlist so no look module can ever hijack login, backup, settings, or module pages
private val AllowedTakeovers = setOf(
    "home", "history", "stats", "section_detail", "coming_soon",
    "search", "search_input", "explore", "mood_and_genres", "new_release",
    "library", "library/songs", "library/playlists", "library/added", "local_genre",
    "library/local/added", "library/local/albums", "library/local/artists", "library/local/playlists",
    "library/cached/added", "library/cached/albums", "library/cached/artists", "library/cached/playlists",
    "library/imported", "library/imported/playlists", "library/imported/albums",
    "album", "artist", "playlist", "local_playlist", "online_playlist", "auto_playlist", "top_playlist", "cache_playlist",
)
