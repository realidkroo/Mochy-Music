// the module store: best from community up top, category chips that stick under the bar, a search that grows out of its circle, and every published module's card

package com.mochyy.mochymusic.ui.screens.settings.modules

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.ModuleStoreTermsKey
import com.mochyy.mochymusic.constants.UsernameKey
import com.mochyy.mochymusic.core.updater.DiscordUrl
import com.mochyy.mochymusic.modules.ModuleEntry
import com.mochyy.mochymusic.modules.ModuleRegistry.LinkCheck
import com.mochyy.mochymusic.modules.browser.FeedInstall
import com.mochyy.mochymusic.modules.browser.FeedModule
import com.mochyy.mochymusic.modules.browser.FeedResult
import com.mochyy.mochymusic.modules.browser.PackItem
import com.mochyy.mochymusic.ui.component.GlassState
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState
import com.mochyy.mochymusic.ui.component.glassRoot
import com.mochyy.mochymusic.ui.screens.settings.devspace.openDevUrl
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.ui.utils.stableStatusBars
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.ModuleBrowserViewModel
import com.mochyy.mochymusic.viewmodels.ModulesViewModel
import com.mochyy.mochymusic.viewmodels.matching
import kotlinx.coroutines.launch

const val ModuleBrowserRoute = "module_browser"

// on a tablet the store stays this wide in the middle, so cards don't stretch edge to edge
private val StoreMaxWidth = 1100.dp
// the next chart card peeks in from the right so it's clear there's more to swipe to
private val FeaturedPeek = 56.dp
private val FeaturedMaxWidth = 420.dp

@Composable
fun ModuleBrowserScreen(
    navController: NavController,
    viewModel: ModuleBrowserViewModel = hiltViewModel(),
    modules: ModulesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val overlay = LocalZoomOutOverlayState.current
    val page by viewModel.page.collectAsState()
    val fetching by viewModel.fetching.collectAsState()
    val entries by modules.entries.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var termsAccepted by rememberPreference(ModuleStoreTermsKey, 0)
    val (username) = rememberPreference(UsernameKey, "")
    val added = remember(entries) { addedModules(entries) }
    val act = rememberStoreAdd(viewModel, modules, navController)
    val open = remember(navController) { { module: FeedModule -> navController.navigate(moduleStoreRoute(module.id)) } }
    val list = rememberLazyListState()
    val glass = remember { GlassState() }
    val statusTop = WindowInsets.stableStatusBars.asPaddingValues().calculateTopPadding()
    val pageWidth = min(LocalConfiguration.current.screenWidthDp.dp, StoreMaxWidth)
    val columns = when {
        pageWidth >= 900.dp -> 3
        pageWidth >= 600.dp -> 2
        else -> 1
    }
    val store = page as? FeedResult.Modules
    val rows = remember(store, query, category, columns) { store?.modules?.matching(query, category).orEmpty().chunked(columns) }
    val chipsIndex = if (store?.chart.isNullOrEmpty()) 1 else 3
    val stick = rememberStick(list, chipsIndex, statusTop)
    val veil = rememberVeil(list)
    val searchShown = animateFloatAsState(if (searching) 1f else 0f, tween(300, easing = FastOutSlowInEasing), label = "search")
    val closeSearch = {
        query = ""
        searching = false
    }
    BackHandler(enabled = searching) { closeSearch() }

    fun signIn() {
        val uri = viewModel.signInUri() ?: return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.module_browser_no_browser), Toast.LENGTH_LONG).show()
        }
    }

    // the terms come before signing in, once per version of them; next agrees and goes on to discord
    fun welcome() {
        overlay.showStoreWelcome(
            name = username,
            onNext = {
                termsAccepted = StoreTermsVersion
                signIn()
            },
            onNotNow = { navController.navigateUp() },
        )
    }
    // only someone not signed in yet sees it; anyone already connected went through discord's own sign-in
    val needsTerms = termsAccepted < StoreTermsVersion && page == FeedResult.SignedOut
    LaunchedEffect(needsTerms) { if (needsTerms) welcome() }

    val person = when (val current = page) {
        is FeedResult.Modules -> current.person
        is FeedResult.NotMember -> current.person
        else -> null
    }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxSize().widthIn(max = StoreMaxWidth)) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().glassRoot(glass, isActive = { veil.value > 0f }),
                contentPadding = PaddingValues(top = storeContentTop(statusTop), bottom = 140.dp),
            ) {
                when (val current = page) {
                    null -> items(3, key = { "bone$it" }, contentType = { "bone" }) {
                        BrowserBoneCard(Modifier.padding(horizontal = StorePadding, vertical = 7.dp))
                    }
                    is FeedResult.Modules -> storeItems(
                        store = current,
                        rows = rows,
                        columns = columns,
                        featuredWidth = min(pageWidth - StorePadding - FeaturedPeek, FeaturedMaxWidth),
                        added = added,
                        fetching = fetching,
                        onOpen = open,
                        onAction = act,
                        titleAlpha = { 1f - searchShown.value * (1f - stick.value) },
                    )
                    else -> item(key = "note", contentType = "note") {
                        StoreMessage(current, onSignIn = { if (termsAccepted < StoreTermsVersion) welcome() else signIn() }, onRetry = viewModel::reload, onJoin = { link -> context.openDevUrl(link ?: DiscordUrl) })
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
            StoreChrome(
                statusTop = statusTop,
                person = person,
                glass = glass,
                list = list,
                chipsIndex = chipsIndex,
                veil = veil,
                stick = stick,
                searchShown = searchShown,
                showStore = store != null,
                categories = store?.categories.orEmpty(),
                category = category,
                onCategory = { category = it },
                searching = searching,
                query = query,
                onQuery = { query = it },
                onSearch = { searching = true },
                onCloseSearch = closeSearch,
                onBack = { navController.navigateUp() },
            )
        }
    }
}

// best from community when the chart has anything, then the categories title, the chips' slot, and the modules in rows of [columns]
private fun LazyListScope.storeItems(
    store: FeedResult.Modules,
    rows: List<List<FeedModule>>,
    columns: Int,
    featuredWidth: Dp,
    added: AddedModules,
    fetching: String?,
    onOpen: (FeedModule) -> Unit,
    onAction: (FeedModule) -> Unit,
    titleAlpha: () -> Float,
) {
    if (store.chart.isNotEmpty()) {
        item(key = "best-title", contentType = "title") {
            BrowserSectionTitle(stringResource(R.string.module_store_best), Modifier.padding(start = StorePadding, end = StorePadding, bottom = 16.dp))
        }
        item(key = "best", contentType = "best") {
            BestFromCommunity(store.chart, featuredWidth, added, fetching, onOpen, onAction)
        }
    }
    item(key = CategoriesKey, contentType = "title") {
        Box(
            Modifier.fillMaxWidth().height(StoreTitleHeight).padding(horizontal = StorePadding).graphicsLayer { alpha = titleAlpha() },
            contentAlignment = Alignment.CenterStart,
        ) {
            BrowserSectionTitle(stringResource(R.string.module_store_categories), Modifier.padding(end = StoreButton + 12.dp))
        }
    }
    // the chips themselves float over this gap, so they can stick under the bar once it reaches it
    item(key = ChipsKey, contentType = "chips") { Spacer(Modifier.height(StoreChipsHeight + 18.dp)) }
    if (rows.isEmpty()) {
        item(key = "nothing", contentType = "note") { BrowserNote(null, stringResource(R.string.module_browser_nothing), Modifier.padding(horizontal = StorePadding)) }
    }
    items(rows, key = { "row-${it.first().id}" }, contentType = { "row" }) { row ->
        Row(Modifier.padding(horizontal = StorePadding, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { module ->
                BrowserModuleCard(module, actionFor(module, added), fetching == module.id, onOpen, onAction, Modifier.weight(1f))
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun BestFromCommunity(chart: List<FeedModule>, cardWidth: Dp, added: AddedModules, fetching: String?, onOpen: (FeedModule) -> Unit, onAction: (FeedModule) -> Unit) {
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
        contentPadding = PaddingValues(start = StorePadding, end = StorePadding, bottom = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(chart, key = { it.id }, contentType = { "featured" }) { module ->
            FeaturedCard(module, actionFor(module, added), fetching == module.id, onOpen, onAction, Modifier.width(cardWidth))
        }
    }
}

// everything that isn't the store itself: signed out, not in the server, a fork, offline and the rest, each with what to do next
@Composable
private fun StoreMessage(page: FeedResult, onSignIn: () -> Unit, onRetry: () -> Unit, onJoin: (String?) -> Unit) {
    val padded = Modifier.padding(horizontal = StorePadding)
    when (page) {
        FeedResult.SignedOut -> BrowserNote(
            title = stringResource(R.string.module_browser_sign_in_title),
            body = stringResource(R.string.module_browser_sign_in_body),
            modifier = padded,
            primary = stringResource(R.string.module_browser_sign_in),
            onPrimary = onSignIn,
        )
        is FeedResult.NotMember -> {
            val name = page.person?.name?.takeIf { it.isNotBlank() }
            BrowserNote(
                title = stringResource(R.string.module_browser_join_title),
                body = name?.let { stringResource(R.string.module_browser_join_body_named, it) } ?: stringResource(R.string.module_browser_join_body),
                modifier = padded,
                primary = stringResource(R.string.module_browser_join),
                onPrimary = { onJoin(page.invite) },
                secondary = stringResource(R.string.module_browser_check_again),
                onSecondary = onRetry,
            )
        }
        FeedResult.Unofficial -> ForkNote(padded)
        FeedResult.WarmingUp -> BrowserNote(null, stringResource(R.string.module_browser_warming), padded, secondary = stringResource(R.string.module_browser_try_again), onSecondary = onRetry)
        FeedResult.Offline -> BrowserNote(null, stringResource(R.string.module_browser_offline), padded, secondary = stringResource(R.string.module_browser_try_again), onSecondary = onRetry)
        FeedResult.NotSetUp -> BrowserNote(null, stringResource(R.string.module_browser_not_set_up), padded)
        is FeedResult.Modules -> Unit
    }
}

/** what an add pill does: a paid module they don't have yet opens its post, a web one opens its page, anything else comes down and goes through the add sheet */
@Composable
internal fun rememberStoreAdd(viewModel: ModuleBrowserViewModel, modules: ModulesViewModel, navController: NavController): (FeedModule) -> Unit {
    val context = LocalContext.current
    val overlay = LocalZoomOutOverlayState.current
    val scope = rememberCoroutineScope()
    return remember(viewModel, modules, navController, overlay, context) {
        { module ->
            if (module.locked) {
                Toast.makeText(context, context.getString(R.string.module_browser_locked_note, module.name), Toast.LENGTH_LONG).show()
                context.openDevUrl(module.postUrl)
            } else if (module.install == FeedInstall.Web) {
                context.openDevUrl(module.link ?: module.postUrl)
            } else if (module.install == FeedInstall.Pack) {
                navController.navigate(moduleStoreRoute(module.id))
            } else {
                scope.launch {
                    when (val result = viewModel.fetch(module)) {
                        is LinkCheck.Ready -> overlay.showAddCheckedSheet(result, modules) { id -> navController.navigate(moduleRoute(id)) }
                        LinkCheck.Unreachable -> Toast.makeText(context, context.getString(R.string.module_browser_fetch_failed, module.name), Toast.LENGTH_LONG).show()
                        else -> linkProblem(context, result)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }
    }
}

/** the button on one of a pack's modules: it comes down and goes through the add sheet like any module */
@Composable
internal fun rememberPackAdd(viewModel: ModuleBrowserViewModel, modules: ModulesViewModel, navController: NavController): (FeedModule, PackItem) -> Unit {
    val context = LocalContext.current
    val overlay = LocalZoomOutOverlayState.current
    val scope = rememberCoroutineScope()
    return remember(viewModel, modules, navController, overlay, context) {
        { module, item ->
            if (module.locked) {
                Toast.makeText(context, context.getString(R.string.module_browser_locked_note, module.name), Toast.LENGTH_LONG).show()
                context.openDevUrl(module.postUrl)
            } else {
                scope.launch {
                    when (val result = viewModel.fetchItem(module, item)) {
                        is LinkCheck.Ready -> overlay.showAddCheckedSheet(result, modules) { id -> navController.navigate(moduleRoute(id)) }
                        LinkCheck.Unreachable -> Toast.makeText(context, context.getString(R.string.module_browser_fetch_failed, item.name), Toast.LENGTH_LONG).show()
                        else -> linkProblem(context, result)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    }
                }
            }
        }
    }
}

/** what's in already, so a pill can say added or update: each module's version and when its copy went in, which ones have a newer copy at their link, and which module each link became */
internal class AddedModules(
    val versions: Map<String, String>,
    val installedAt: Map<String, Long>,
    val updatable: Set<String>,
    val linkEntries: Map<String, String>,
) {
    /** the installed module a store module is, or null while it isn't in; a web page or a whole pack never is */
    fun entryFor(module: FeedModule): String? = when (module.install) {
        FeedInstall.Web, FeedInstall.Pack -> null
        FeedInstall.Link -> linkEntry(module.link)
        FeedInstall.Zip -> zipEntry(module.moduleId)
    }

    /** the installed module one of a pack's modules is */
    fun entryFor(item: PackItem): String? = if (item.isLink) linkEntry(item.link) else zipEntry(item.moduleId)

    private fun linkEntry(link: String?): String? = link?.let { linkEntries[linkKey(it)] }

    // a zip look is added as "local." and its own id, made safe; a module with code keeps its id as it is
    private fun zipEntry(moduleId: String?): String? =
        moduleId?.let { id -> listOf("local." + id.lowercase().replace(UnsafeIdChars, "-"), id).firstOrNull { it in versions } }
}

private val UnsafeIdChars = Regex("[^a-z0-9._-]")

internal fun addedModules(entries: List<ModuleEntry>) = AddedModules(
    versions = entries.associate { it.id to it.module.info.version },
    installedAt = entries.mapNotNull { entry -> entry.installedAt?.let { entry.id to it } }.toMap(),
    updatable = entries.filter { it.update != null }.map { it.id }.toSet(),
    linkEntries = entries.mapNotNull { entry -> entry.source?.link?.let { linkKey(it) to entry.id } }.toMap(),
)

// the same project however its link was typed: no scheme, www or trailing slash, and only owner/repo for github
private fun linkKey(link: String): String {
    val bare = link.trim().lowercase().substringAfter("://").removePrefix("www.").trimEnd('/')
    return if (bare.startsWith("github.com/")) bare.split('/').take(3).joinToString("/") else bare
}

internal fun actionFor(module: FeedModule, added: AddedModules): BrowserAction {
    if (module.locked) return BrowserAction.Locked
    return when (module.install) {
        FeedInstall.Web -> BrowserAction.Open
        // a pack's modules are added one by one from its page
        FeedInstall.Pack -> BrowserAction.View
        FeedInstall.Link -> linkedAction(added.entryFor(module), added)
        FeedInstall.Zip -> zipAction(added.entryFor(module), module.version, module.updated, added)
    }
}

/** the button for one of a pack's modules; a locked pack locks them all */
internal fun actionFor(module: FeedModule, item: PackItem, added: AddedModules): BrowserAction {
    if (module.locked) return BrowserAction.Locked
    val entry = added.entryFor(item)
    return if (item.isLink) linkedAction(entry, added) else zipAction(entry, item.version, module.updated, added)
}

// a linked module's newer copies are found by the app's own update check, at its link
private fun linkedAction(entry: String?, added: AddedModules): BrowserAction = when {
    entry == null -> BrowserAction.Add
    entry in added.updatable -> BrowserAction.Update
    else -> BrowserAction.Added
}

// a zip posted after this copy went in is newer even when its maker kept or left out the version; a different version says so too
private fun zipAction(entry: String?, version: String?, updated: Long?, added: AddedModules): BrowserAction {
    entry ?: return BrowserAction.Add
    val newerZip = updated != null && updated > (added.installedAt[entry] ?: Long.MAX_VALUE)
    val installed = added.versions[entry]
    val newerVersion = version != null && !installed.isNullOrBlank() && version != installed
    return if (newerZip || newerVersion) BrowserAction.Update else BrowserAction.Added
}
