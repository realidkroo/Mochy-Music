package com.mochyy.mochymusic.ui.player.ipod

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// one screen of the ipod's menu tree, named the way its header shows it
@Immutable
sealed class IpodPage(val title: String, @StringRes val titleRes: Int? = null) {
    data object Main : IpodPage("Mochy")
    data object Music : IpodPage("", R.string.ps_ipod_music)
    data object NowPlaying : IpodPage("", R.string.ps_ipod_now_playing)
    data object CoverFlow : IpodPage("", R.string.ps_ipod_cover_flow)
    data object Playlists : IpodPage("", R.string.ps_ipod_playlists)
    data object Artists : IpodPage("", R.string.ps_ipod_artists)
    data object Albums : IpodPage("", R.string.ps_ipod_albums)
    data object Songs : IpodPage("", R.string.ps_ipod_songs)
    data object LikedSongs : IpodPage("", R.string.ps_ipod_liked_songs)
    data class Playlist(val id: String, val name: String) : IpodPage(name)
    data class Artist(val id: String, val name: String) : IpodPage(name)
    data class Album(val id: String, val name: String) : IpodPage(name)

    val isSidebar: Boolean get() = this == Main || this == Music

    /** the name in the app's language: the fixed pages have a string resource, the library's own pages a name */
    fun titleIn(context: Context): String = titleRes?.let(context::getString) ?: title
}

@Composable
internal fun IpodPage.displayTitle(): String = titleRes?.let { stringResource(it) } ?: title

// what picking a row does: open a page, play a song from the list, or shuffle everything
@Immutable
sealed interface IpodPick {
    class Open(val page: IpodPage) : IpodPick
    class Play(val index: Int) : IpodPick
    data object Shuffle : IpodPick
}

// one line in a menu, with a little cover and a second line on the library lists; pick is null for the "nothing here" line
@Immutable
class IpodRow(
    val key: String,
    val title: String,
    val pick: IpodPick?,
    val subtitle: String? = null,
    val art: String? = null,
    val roundArt: Boolean = false,
    @StringRes val titleRes: Int? = null,
) {
    val opensPage: Boolean get() = pick is IpodPick.Open
}

@Composable
internal fun IpodRow.displayTitle(): String = titleRes?.let { stringResource(it) } ?: title

// a page's lines, plus the songs behind them when it's a song list; art lists get taller rows
@Immutable
class IpodList(val rows: List<IpodRow>, val songs: List<Song> = emptyList(), val art: Boolean = false) {
    companion object {
        val Loading = IpodList(emptyList())
    }
}

// shown for the moment a list takes longer than the slide to read
val IpodLoadingList = IpodList(listOf(IpodRow("loading", "", null, titleRes = R.string.ps_ipod_loading)))

// the home sidebar: cover flow, the music menu and now playing
val IpodMainList = IpodList(
    listOf(
        IpodRow("coverflow", "", IpodPick.Open(IpodPage.CoverFlow), titleRes = R.string.ps_ipod_cover_flow),
        IpodRow("music", "", IpodPick.Open(IpodPage.Music), titleRes = R.string.ps_ipod_music),
        IpodRow("now", "", IpodPick.Open(IpodPage.NowPlaying), titleRes = R.string.ps_ipod_now_playing),
    )
)

// the music submenu, also shown in the left half of the screen beside the covers
val IpodMusicList = IpodList(
    listOf(
        IpodRow("playlists", "", IpodPick.Open(IpodPage.Playlists), titleRes = R.string.ps_ipod_playlists),
        IpodRow("artists", "", IpodPick.Open(IpodPage.Artists), titleRes = R.string.ps_ipod_artists),
        IpodRow("albums", "", IpodPick.Open(IpodPage.Albums), titleRes = R.string.ps_ipod_albums),
        IpodRow("songs", "", IpodPick.Open(IpodPage.Songs), titleRes = R.string.ps_ipod_songs),
        IpodRow("liked", "", IpodPick.Open(IpodPage.LikedSongs), titleRes = R.string.ps_ipod_liked_songs),
        IpodRow("shuffle", "", IpodPick.Shuffle, titleRes = R.string.ps_ipod_shuffle_songs),
        IpodRow("coverflow", "", IpodPick.Open(IpodPage.CoverFlow), titleRes = R.string.ps_ipod_cover_flow),
    )
)

// a page in the stack, keeping its own highlighted row so going back lands where you left it
@Stable
class IpodEntry(val page: IpodPage, val id: Long) {
    var list by mutableStateOf(
        when (page) {
            IpodPage.Main -> IpodMainList
            IpodPage.Music -> IpodMusicList
            else -> IpodList.Loading
        }
    )
    var selected by mutableIntStateOf(0)
    var first by mutableIntStateOf(0)

    // how many rows fit on the glass, told by the list as it's laid out
    var visibleRows = 7

    // moves the highlight, keeping it inside the visible window, false when it was already at the end
    fun move(steps: Int): Boolean {
        val count = list.rows.size
        if (count == 0) return false
        val target = (selected + steps).coerceIn(0, count - 1)
        if (target == selected) return false
        selected = target
        clamp()
        return true
    }

    // keeps the highlight on a real row and inside the window, after the list or the screen changed
    fun clamp() {
        val last = (list.rows.size - 1).coerceAtLeast(0)
        if (selected > last) selected = last
        val maxFirst = (list.rows.size - visibleRows).coerceAtLeast(0)
        first = first.coerceIn((selected - visibleRows + 1).coerceAtLeast(0), minOf(selected, maxFirst))
    }
}

// the stack of menu pages and the slide between them: the new page pushes the old one out, like the real thing
@Stable
class IpodNavigator(private val scope: CoroutineScope) {
    private var nextId = 0L
    val stack = mutableStateListOf(entry(IpodPage.Main), entry(IpodPage.NowPlaying))
    var leaving by mutableStateOf<IpodEntry?>(null)
        private set
    var forward by mutableStateOf(true)
        private set
    val slide = Animatable(1f)

    val top: IpodEntry get() = stack.last()

    // a list that's already loaded comes in full instead of filling up mid slide
    fun push(page: IpodPage, list: IpodList? = null): IpodEntry {
        val entry = entry(page)
        if (list != null) entry.list = list
        leaving = top
        stack.add(entry)
        forward = true
        play()
        return entry
    }

    // false at the main menu, where menu has nowhere left to go
    fun back(): Boolean {
        if (stack.size <= 1) return false
        leaving = stack.removeAt(stack.lastIndex)
        forward = false
        play()
        return true
    }

    // holding menu goes all the way back to the top in one slide
    fun backToMain() {
        if (stack.size <= 1) return
        leaving = top
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        forward = false
        play()
    }

    // a song just started, so its screen comes up wherever you picked it from
    fun showNowPlaying() {
        if (top.page != IpodPage.NowPlaying) push(IpodPage.NowPlaying)
    }

    private fun entry(page: IpodPage) = IpodEntry(page, nextId++)

    private fun play() {
        scope.launch {
            slide.snapTo(0f)
            // the new page is built and drawn once just off the glass before it moves, so the first frame of the slide isn't the heavy one
            repeat(PrepareFrames) { withFrameNanos { } }
            slide.animateTo(1f, tween(SlideMs, easing = SlideEasing))
            leaving = null
        }
    }
}

// ipod.js slides its views in 300 ms, eased so it starts quick and lands soft
private const val SlideMs = 300
private const val PrepareFrames = 2
private val SlideEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1f)
