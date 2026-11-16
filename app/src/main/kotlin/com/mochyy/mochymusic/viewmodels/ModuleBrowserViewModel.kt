// the module browser: mochy's bar's modules from the feed, the search and the kind picked, and fetching a module's zip to add

package com.mochyy.mochymusic.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.modules.ModuleRegistry
import com.mochyy.mochymusic.modules.browser.FeedKind
import com.mochyy.mochymusic.modules.browser.FeedModule
import com.mochyy.mochymusic.modules.browser.FeedResult
import com.mochyy.mochymusic.modules.browser.ModuleFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** which modules the chips show: every kind, one kind, or only the paid ones */
enum class BrowserFilter { All, Looks, Players, Sources, Paid }

@HiltViewModel
class ModuleBrowserViewModel @Inject constructor(
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
    }

    /** asks the feed again; what's on screen stays while it does, so a refresh never blanks the list */
    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { _page.value = feed.load() }
    }

    fun signInUri(): Uri = feed.signInUri()

    fun signOut() {
        viewModelScope.launch { feed.signOut() }
    }

    /** the module's zip, checked like one picked from the phone, for the add sheet to show before anything's added */
    suspend fun fetch(module: FeedModule): ModuleRegistry.LinkCheck {
        _fetching.value = module.id
        val file = feed.download(module.id)
        val result = if (file == null) ModuleRegistry.LinkCheck.Unreachable else registry.checkZip(Uri.fromFile(file))
        _fetching.value = null
        return result
    }
}

/** the modules a search and a chip leave, in the order the feed gave them */
fun List<FeedModule>.matching(query: String, filter: BrowserFilter): List<FeedModule> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    return filter { module ->
        val kindFits = when (filter) {
            BrowserFilter.All -> true
            BrowserFilter.Looks -> module.kind == FeedKind.Look
            BrowserFilter.Players -> module.kind == FeedKind.Player
            BrowserFilter.Sources -> module.kind == FeedKind.Source
            BrowserFilter.Paid -> module.paid
        }
        val text = "${module.name} ${module.author.orEmpty()} ${module.description}".lowercase()
        kindFits && words.all { it in text }
    }
}
