// the module browser: mochy's bar's modules from the feed, the search and the kind picked, and fetching a module's zip to add

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.constants.SimulateForkKey
import com.mochyy.mochymusic.modules.ModuleRegistry
import com.mochyy.mochymusic.modules.browser.FeedInstall
import com.mochyy.mochymusic.modules.browser.FeedModule
import com.mochyy.mochymusic.modules.browser.FeedResult
import com.mochyy.mochymusic.modules.browser.ModuleFeed
import com.mochyy.mochymusic.modules.browser.PackItem
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModuleBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val feed: ModuleFeed,
    private val registry: ModuleRegistry,
) : ViewModel() {

    private val _page = MutableStateFlow<FeedResult?>(null)

    /** what the feed said last, null while the first answer is on its way */
    val page: StateFlow<FeedResult?> = _page.asStateFlow()

    // the module whose zip is coming down, for its button's spinner
    private val _fetching = MutableStateFlow<String?>(null)
    val fetching: StateFlow<String?> = _fetching.asStateFlow()

    private var loadJob: Job? = null

    init {
        // signing in happens in the browser and lands here as a new session, which loads the list straight away
        viewModelScope.launch { feed.session.collect { reload() } }
        // flipping the fork preview switch reloads at once so a previously loaded store never stays on screen
        viewModelScope.launch {
            context.dataStore.data
                .map { it[SimulateForkKey] ?: false }
                .distinctUntilChanged()
                .drop(1)
                .collect { reload() }
        }
    }

    /** asks the feed again; what's on screen stays while it does, so a refresh never blanks the list */
    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { _page.value = feed.load() }
    }

    fun signInUri(): Uri? = feed.signInUri()

    fun signOut() {
        viewModelScope.launch { feed.signOut() }
    }

    /** the module checked for the add sheet before anything's added: its zip like one picked from the phone, or its link like a pasted one */
    suspend fun fetch(module: FeedModule): ModuleRegistry.LinkCheck {
        _fetching.value = module.id
        // a linked module is added from its link, so later updates come from there like any module added by link
        val link = module.link.takeIf { module.install == FeedInstall.Link }
        val result = if (link != null) registry.check(link) else checkZip(module.id)
        _fetching.value = null
        return result
    }

    /** one of a pack's modules, checked the same way; its spinner goes by [packKey] */
    suspend fun fetchItem(module: FeedModule, item: PackItem): ModuleRegistry.LinkCheck {
        _fetching.value = packKey(module, item)
        val link = item.link.takeIf { item.isLink }
        val result = if (link != null) registry.check(link) else checkZip(module.id, item.id)
        _fetching.value = null
        return result
    }

    private suspend fun checkZip(postId: String, item: String? = null): ModuleRegistry.LinkCheck {
        val file = feed.download(postId, item) ?: return ModuleRegistry.LinkCheck.Unreachable
        return registry.checkZip(Uri.fromFile(file))
    }
}

/** the modules a search and a category chip leave, in the order the feed gave them; a null category is all of them */
fun List<FeedModule>.matching(query: String, category: String?): List<FeedModule> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    // a paid forum's modules carry no paid tag of their own, so the paid chip goes by the module instead
    val paidChip = category != null && PaidWords.containsMatchIn(category)
    return filter { module ->
        val fits = category == null || category in module.tags || (paidChip && module.paid)
        val text = "${module.name} ${module.author.orEmpty()} ${module.blurb} ${module.tags.joinToString(" ")}".lowercase()
        fits && words.all { it in text }
    }
}

private val PaidWords = Regex("paid|premium", RegexOption.IGNORE_CASE)

/** what the spinner on one of a pack's modules goes by */
fun packKey(module: FeedModule, item: PackItem) = "${module.id}/${item.id}"
