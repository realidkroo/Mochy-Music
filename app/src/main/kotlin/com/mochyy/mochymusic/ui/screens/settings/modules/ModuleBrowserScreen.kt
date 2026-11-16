// the module browser: mochy's bar's module forums, searched and sorted by kind, each added with one tap through the usual add sheet

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.core.updater.DiscordUrl
import com.mochyy.mochymusic.modules.ModuleRegistry.LinkCheck
import com.mochyy.mochymusic.modules.browser.FeedKind
import com.mochyy.mochymusic.modules.browser.FeedModule
import com.mochyy.mochymusic.modules.browser.FeedResult
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.screens.settings.devspace.openDevUrl
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.stableStatusBars
import com.mochyy.mochymusic.viewmodels.BrowserFilter
import com.mochyy.mochymusic.viewmodels.ModuleBrowserViewModel
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import com.mochyy.mochymusic.viewmodels.matching
import kotlinx.coroutines.launch

const val ModuleBrowserRoute = "module_browser"

// the order the kinds are listed in when every kind is shown
private val SectionOrder = listOf(FeedKind.Look, FeedKind.Player, FeedKind.Source, FeedKind.Other)

@Composable
fun ModuleBrowserScreen(
    navController: NavController,
    viewModel: ModuleBrowserViewModel = hiltViewModel(),
    modules: ModulesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val overlay = LocalZoomOutOverlayState.current
    val scope = rememberCoroutineScope()
    val page by viewModel.page.collectAsState()
    val fetching by viewModel.fetching.collectAsState()
    val entries by modules.entries.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(BrowserFilter.All) }
    // the versions of what's added already, by id, so a card can say added or update
    val added = remember(entries) { entries.associate { it.id to it.module.info.version } }
    val top = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()

    fun signIn() {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, viewModel.signInUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.module_browser_no_browser), Toast.LENGTH_LONG).show()
        }
    }

    fun act(module: FeedModule) {
        if (module.locked) {
            Toast.makeText(context, context.getString(R.string.module_browser_locked_note, module.name), Toast.LENGTH_LONG).show()
            context.openDevUrl(module.postUrl)
            return
        }
        scope.launch {
            when (val result = viewModel.fetch(module)) {
                is LinkCheck.Ready -> overlay.showAddCheckedSheet(result, modules) { id -> navController.navigate(moduleRoute(id)) }
                LinkCheck.Unreachable -> Toast.makeText(context, context.getString(R.string.module_browser_fetch_failed, module.name), Toast.LENGTH_LONG).show()
                else -> linkProblem(context, result)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            }
        }
    }

    val current = page
    val person = when (current) {
        is FeedResult.Modules -> current.person
        is FeedResult.NotMember -> current.person
        else -> null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 12.dp, bottom = 140.dp),
    ) {
        item(key = "header", contentType = "header") {
            BrowserHeader(person, onBack = { navController.navigateUp() })
            Spacer(Modifier.height(18.dp))
            BrowserTitle()
            Spacer(Modifier.height(22.dp))
            BrowserSearch(query) { query = it }
            Spacer(Modifier.height(14.dp))
            BrowserChips(filter) { filter = it }
        }
        when (current) {
            null -> items(3, key = { "bone$it" }, contentType = { "bone" }) {
                Spacer(Modifier.height(14.dp))
                BrowserBoneCard()
            }
            is FeedResult.Modules -> moduleSections(
                modules = current.modules.matching(query, filter),
                filter = filter,
                added = added,
                fetching = fetching,
                onOpen = { context.openDevUrl(it.postUrl) },
                onAction = ::act,
            )
            FeedResult.SignedOut -> item(key = "signed-out", contentType = "note") {
                BrowserNote(
                    title = stringResource(R.string.module_browser_sign_in_title),
                    body = stringResource(R.string.module_browser_sign_in_body),
                    primary = stringResource(R.string.module_browser_sign_in),
                    onPrimary = ::signIn,
                )
            }
            is FeedResult.NotMember -> item(key = "not-member", contentType = "note") {
                val name = current.person?.name?.takeIf { it.isNotBlank() }
                BrowserNote(
                    title = stringResource(R.string.module_browser_join_title),
                    body = name?.let { stringResource(R.string.module_browser_join_body_named, it) } ?: stringResource(R.string.module_browser_join_body),
                    primary = stringResource(R.string.module_browser_join),
                    onPrimary = { context.openDevUrl(current.invite ?: DiscordUrl) },
                    secondary = stringResource(R.string.module_browser_check_again),
                    onSecondary = viewModel::reload,
                )
            }
            FeedResult.WarmingUp -> item(key = "warming", contentType = "note") {
                BrowserNote(null, stringResource(R.string.module_browser_warming), secondary = stringResource(R.string.module_browser_try_again), onSecondary = viewModel::reload)
            }
            FeedResult.Offline -> item(key = "offline", contentType = "note") {
                BrowserNote(null, stringResource(R.string.module_browser_offline), secondary = stringResource(R.string.module_browser_try_again), onSecondary = viewModel::reload)
            }
            FeedResult.NotSetUp -> item(key = "not-set-up", contentType = "note") {
                BrowserNote(null, stringResource(R.string.module_browser_not_set_up))
            }
        }
        if (person != null) {
            item(key = "sign-out", contentType = "footer") {
                Text(
                    stringResource(R.string.module_browser_sign_out),
                    color = Color.White.copy(alpha = 0.45f),
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 30.dp)
                        .clickable(onClick = viewModel::signOut)
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}

// every kind under its own heading when all are shown, otherwise the one kind picked under its name
private fun LazyListScope.moduleSections(
    modules: List<FeedModule>,
    filter: BrowserFilter,
    added: Map<String, String>,
    fetching: String?,
    onOpen: (FeedModule) -> Unit,
    onAction: (FeedModule) -> Unit,
) {
    if (modules.isEmpty()) {
        item(key = "nothing", contentType = "note") { BrowserNote(null, stringResource(R.string.module_browser_nothing)) }
        return
    }
    val sections = if (filter == BrowserFilter.All) SectionOrder.map { kind -> kind to modules.filter { it.kind == kind } } else listOf(null to modules)
    sections.forEach { (kind, list) ->
        if (list.isEmpty()) return@forEach
        item(key = "title-${kind ?: filter}", contentType = "title") {
            BrowserSectionTitle(stringResource(kind?.let(::sectionLabel) ?: filterSectionLabel(filter)))
        }
        items(list, key = { "module-${it.id}" }, contentType = { "module" }) { module ->
            BrowserModuleCard(
                module = module,
                action = actionFor(module, added),
                fetching = fetching == module.id,
                onOpen = { onOpen(module) },
                onAction = { onAction(module) },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun sectionLabel(kind: FeedKind): Int = when (kind) {
    FeedKind.Look -> R.string.module_browser_looks
    FeedKind.Player -> R.string.module_browser_players
    FeedKind.Source -> R.string.module_browser_sources
    FeedKind.Other -> R.string.module_browser_others
}

private fun filterSectionLabel(filter: BrowserFilter): Int = when (filter) {
    BrowserFilter.All, BrowserFilter.Looks -> R.string.module_browser_looks
    BrowserFilter.Players -> R.string.module_browser_players
    BrowserFilter.Sources -> R.string.module_browser_sources
    BrowserFilter.Paid -> R.string.module_browser_paid
}

// a zip look is added as "local." and its own id, made safe; a module with code keeps its id as it is
private fun actionFor(module: FeedModule, added: Map<String, String>): BrowserAction {
    if (module.locked) return BrowserAction.Locked
    val id = module.moduleId ?: return BrowserAction.Add
    val version = added["local." + id.lowercase().replace(Regex("[^a-z0-9._-]"), "-")] ?: added[id] ?: return BrowserAction.Add
    return if (module.version != null && module.version != version) BrowserAction.Update else BrowserAction.Added
}
