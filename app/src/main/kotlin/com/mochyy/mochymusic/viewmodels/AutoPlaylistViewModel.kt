// AutoPlaylistViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.constants.HideExplicitKey
import com.mochyy.mochymusic.constants.HideVideoSongsKey
import com.mochyy.mochymusic.constants.LikedSongSortDescendingKey
import com.mochyy.mochymusic.constants.LikedSongSortType
import com.mochyy.mochymusic.constants.LikedSongSortTypeKey
import com.mochyy.mochymusic.constants.SongSortDescendingKey
import com.mochyy.mochymusic.constants.SongSortType
import com.mochyy.mochymusic.constants.SongSortTypeKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.ImportedSong
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.extensions.filterExplicit
import com.mochyy.mochymusic.extensions.filterVideoSongs
import com.mochyy.mochymusic.extensions.toEnum
import com.mochyy.mochymusic.importer.ImportSource
import com.mochyy.mochymusic.playback.CachedSongs
import com.mochyy.mochymusic.utils.SyncUtils
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Liked songs that came from one place; [source] null is the ones liked here in Mochy. */
data class LikedSourceSection(val source: ImportSource?, val songs: List<Song>)

data class AutoPlaylistSongs(
    val songs: List<Song>,
    /** Only for Liked songs in import order, when some were imported: [songs], split by service. */
    val sections: List<LikedSourceSection> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
    private val syncUtils: SyncUtils,
    private val cachedSongs: CachedSongs,
) : ViewModel() {
    val playlist = savedStateHandle.get<String>("playlist")!!

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private data class ListPrefs(
        val sortType: SongSortType,
        val descending: Boolean,
        val likedSortType: LikedSongSortType,
        val likedDescending: Boolean,
        val hideExplicit: Boolean,
        val hideVideoSongs: Boolean,
    )

    /** null until the first load */
    val content: StateFlow<AutoPlaylistSongs?> =
        context.dataStore.data
            .map {
                ListPrefs(
                    sortType = it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                    descending = it[SongSortDescendingKey] ?: true,
                    likedSortType = it[LikedSongSortTypeKey].toEnum(LikedSongSortType.CREATE_DATE),
                    likedDescending = it[LikedSongSortDescendingKey] ?: true,
                    hideExplicit = it[HideExplicitKey] ?: false,
                    hideVideoSongs = it[HideVideoSongsKey] ?: false,
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { prefs ->
                val visible: (List<Song>) -> List<Song> = {
                    it.filterExplicit(prefs.hideExplicit).filterVideoSongs(prefs.hideVideoSongs)
                }
                val (sortType, descending) = prefs.sortType to prefs.descending
                when (playlist) {
                    "liked" -> if (prefs.likedSortType == LikedSongSortType.IMPORT_ORDER) {
                        combine(
                            database.likedSongs(SongSortType.CREATE_DATE, prefs.likedDescending),
                            database.likedImportSources(),
                        ) { songs, imported -> bySource(visible(songs), imported) }
                    } else {
                        database.likedSongs(prefs.likedSortType.songSortType, prefs.likedDescending)
                            .map { AutoPlaylistSongs(visible(it)) }
                    }

                    "downloaded" -> database.downloadedSongs(sortType, descending)
                        .map { AutoPlaylistSongs(visible(it)) }

                    "uploaded" -> database.uploadedSongs(sortType, descending)
                        .map { AutoPlaylistSongs(visible(it)) }

                    // Local is everything on this device: downloads and imported files together
                    "local" -> database.onDeviceSongs(sortType, descending)
                        .map { AutoPlaylistSongs(visible(it)) }

                    // the Local page's Liked songs: only the liked songs that are on this device.
                    // Sorted like any song list - the page shows the song sort for it, not Liked's.
                    "local_liked" -> database.onDeviceSongs(sortType, descending)
                        .map { songs -> AutoPlaylistSongs(visible(songs.filter { it.song.liked })) }

                    // The Cached page and its liked songs: what the player kept whole from
                    // streaming. They come most recently played first, the cache's own "date added".
                    "cached" -> cachedSongs.songs
                        .map { songs -> AutoPlaylistSongs(visible(songs.sortedForSongSort(sortType, descending) { it.asReversed() })) }

                    "cached_liked" -> cachedSongs.songs
                        .map { songs ->
                            val liked = songs.filter { it.song.liked }
                            AutoPlaylistSongs(visible(liked.sortedForSongSort(sortType, descending) { it.asReversed() }))
                        }

                    "songs" -> database.songs(sortType, descending)
                        .map { AutoPlaylistSongs(visible(it)) }

                    "recently_added" -> database.songs(SongSortType.CREATE_DATE, true)
                        .map { AutoPlaylistSongs(visible(it)) }

                    else -> flowOf(AutoPlaylistSongs(emptyList()))
                }
            }
            .onEach { lastShown = playlist to it }
            .flowOn(Dispatchers.Default)
            // Stops a moment after the page stops watching: the Cached lists look at the player's
            // cache every couple of seconds, which shouldn't go on from the background. The last
            // list is kept, so coming back shows it at once.
            .stateIn(
                viewModelScope,
                kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
                lastShown?.takeIf { it.first == playlist }?.second,
            )

    /**
     * Liked songs by the service each was imported from, the ones liked here first and then the
     * services in the Library's order. Inside each, the order they were liked in: an imported like
     * keeps the date it was liked on the service, so that's the service's own order.
     */
    private fun bySource(songs: List<Song>, imported: List<ImportedSong>): AutoPlaylistSongs {
        // a song that came from two services goes with the one it came from first
        val sourceOf = imported
            .groupBy { it.songId }
            .mapValues { (_, rows) ->
                val first = rows.minWith(compareBy<ImportedSong>({ it.importedAt }, { ImportSource.fromKey(it.source)?.ordinal ?: Int.MAX_VALUE }))
                ImportSource.fromKey(first.source) ?: ImportSource.OTHER
            }
        val bySource = songs.groupBy { sourceOf[it.id] }
        // nothing imported: one plain list, no headings
        if (bySource.keys.all { it == null }) return AutoPlaylistSongs(songs)
        val sections = (listOf<ImportSource?>(null) + ImportSource.entries).mapNotNull { source ->
            bySource[source]?.let { LikedSourceSection(source, it) }
        }
        return AutoPlaylistSongs(songs = sections.flatMap { it.songs }, sections = sections)
    }

    private companion object {
        // the list the page showed last time, so opening it again has its songs from the first frame instead of popping in after the morph; one slot keeps the memory small
        @Volatile
        var lastShown: Pair<String, AutoPlaylistSongs>? = null
    }

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncUploadedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            when (playlist) {
                "liked" -> syncUtils.syncLikedSongsSuspend()
                "uploaded" -> syncUtils.syncUploadedSongsSuspend()
            }
            _isRefreshing.value = false
        }
    }
}

/** Import order lists by the date liked, then splits that by service. */
private val LikedSongSortType.songSortType: SongSortType
    get() = when (this) {
        LikedSongSortType.CREATE_DATE, LikedSongSortType.IMPORT_ORDER -> SongSortType.CREATE_DATE
        LikedSongSortType.NAME -> SongSortType.NAME
        LikedSongSortType.ARTIST -> SongSortType.ARTIST
        LikedSongSortType.PLAY_TIME -> SongSortType.PLAY_TIME
    }
