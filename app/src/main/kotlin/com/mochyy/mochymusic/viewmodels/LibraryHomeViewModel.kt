// LibraryHomeViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.constants.AlbumSortType
import com.mochyy.mochymusic.constants.AlbumsFromLikedSongsKey
import com.mochyy.mochymusic.constants.ArtistSortType
import com.mochyy.mochymusic.constants.PlaylistSortType
import com.mochyy.mochymusic.constants.SongSortType
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Artist
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.db.entities.SpeedDialItem
import com.mochyy.mochymusic.importer.ImportProgress
import com.mochyy.mochymusic.importer.ImportSource
import com.mochyy.mochymusic.importer.MusicImportService
import com.mochyy.mochymusic.importer.groupLocalAlbums
import com.mochyy.mochymusic.playback.CachedSongs
import com.mochyy.mochymusic.utils.ArtistImageResolver
import com.mochyy.mochymusic.utils.dataStore
import com.music.innertube.YouTube
import com.music.innertube.models.YTItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale
import javax.inject.Inject

/**
 * Backs the Library home screen: the four category counts/covers, the pinned grid, and the
 * Recently added / Downloaded preview cards.
 *
 * Deliberately its own view model rather than reusing [HomeViewModel]'s `speedDialItems` — that
 * flow pads pinned items out to 27 with "keep listening" filler for the Home feed's speed-dial
 * carousel, which is exactly wrong here: the Library screen's Pinned section is only supposed to
 * show what was actually pinned, and hides entirely when that's empty.
 */
@HiltViewModel
class LibraryHomeViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val importService: MusicImportService,
    private val cachedSongs: CachedSongs,
) : ViewModel() {

    val pinnedItems = database.speedDialDao.getAll()
        .map { pinned -> pinned.map { it.toYTItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val songs = database.songs(SongSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists = database.artists(ArtistSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // saved albums first, newest on top, then the ones your added songs come from, liked ones too when the old way is on
    @OptIn(ExperimentalCoroutinesApi::class)
    val albums = context.dataStore.data
        .map { it[AlbumsFromLikedSongsKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { withLiked ->
            combine(
                database.albumsLiked(AlbumSortType.CREATE_DATE, true),
                database.albumsFromLibrarySongs(withLiked),
            ) { saved, library ->
                (saved.sortedByDescending { it.album.bookmarkedAt } + library).distinctBy { it.id }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists = database.playlists(PlaylistSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val likedSongs = database.likedSongs(SongSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The import under way, if any - the banner at the top of the Library turns into its progress bar. */
    val importProgress: StateFlow<ImportProgress> = importService.progress

    /**
     * One card per service that has brought music in, each with its song count and newest covers.
     * In the order the services are listed in [ImportSource]. A source that only
     * stands for "some file" ([ImportSource.OTHER]) has no card: its songs are in the library and
     * that is all there is to say about them.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val importedSources: StateFlow<List<ImportedSourceCard>> = database.importedSourceCounts()
        .flatMapLatest { counts ->
            val listed = counts
                .mapNotNull { row ->
                    ImportSource.fromKey(row.source)?.takeIf { it.isListed }?.let { it to row.songCount }
                }
                .sortedBy { (source, _) -> source.ordinal }
            if (listed.isEmpty()) {
                flowOf(emptyList<ImportedSourceCard>())
            } else {
                combine(
                    listed.map { (source, count) ->
                        database.importedCovers(source.key).map { covers -> ImportedSourceCard(source, count, covers) }
                    }
                ) { cards -> cards.toList() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // how many albums each service's songs come from, with the newest few covers, for the imported tab
    val importedAlbums: StateFlow<Map<ImportSource, ImportedAlbums>> = database.importedAlbumRows()
        .map { rows ->
            rows.groupBy { it.source }
                .mapNotNull { (key, albums) ->
                    ImportSource.fromKey(key)?.let { it to ImportedAlbums(albums.size, albums.take(4).map { album -> album.thumbnailUrl }) }
                }
                .toMap()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The service each imported playlist came from, by playlist id, for the Imported tab's Playlists. */
    val playlistSources: StateFlow<Map<String, ImportSource>> = database.importedPlaylists()
        .map { rows -> rows.mapNotNull { row -> ImportSource.fromKey(row.source)?.let { row.playlistId to it } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Everything that plays from this device: songs downloaded for offline and audio files
     * imported from storage, newest first. This is what "Local" means everywhere in the Library,
     * so a song that is only in the library (or only liked) never shows up on the Local tab.
     */
    val local = LibraryShelf(
        source = database.onDeviceSongs(SongSortType.CREATE_DATE, true),
        libraryPlaylists = playlists,
        database = database,
        scope = viewModelScope,
    )

    /**
     * Songs the player kept whole from streaming, most recently played first. Their artists are
     * often not in the library, so this shelf fetches their pictures itself - only while the
     * Cached tab is open, since watching the cache means looking at it every couple of seconds.
     */
    val cached = LibraryShelf(
        source = cachedSongs.songs,
        libraryPlaylists = playlists,
        database = database,
        scope = viewModelScope,
        refreshArtists = ::refreshArtistImages,
    )

    fun shelf(kind: LibraryShelfKind): LibraryShelf = when (kind) {
        LibraryShelfKind.Local -> local
        LibraryShelfKind.Cached -> cached
    }

    init {
        // Imports made before library songs had their own place left them in a playlist, or out of
        // the Library entirely; put them where they belong (once - it finds nothing to do after).
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { importService.repairEarlierImports() }
                .onFailure { timber.log.Timber.w(it, "Repairing earlier imports failed") }
        }

        // a song's album is only stored once opened, so this makes sure every library song's album has a row to show
        viewModelScope.launch(Dispatchers.IO) {
            database.librarySongCount().distinctUntilChanged().collect { database.addMissingLibraryAlbums() }
        }

        // files imported before local albums existed get theirs here, after that it finds nothing to do
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { groupLocalAlbums(context, database) }
                .onFailure { timber.log.Timber.w(it, "Grouping local albums failed") }
        }

        // Local artists are included: a downloaded song that never went into the library has
        // artists the Library's list doesn't know about, and the Local tab shows them too.
        viewModelScope.launch(Dispatchers.IO) {
            combine(artists, local.artists) { library, local -> (library + local).distinctBy { it.id } }
                .collect { refreshArtistImages(it) }
        }
    }

    /**
     * Artist rows have no picture until something goes and fetches one: the artist table only
     * stores a thumbnail once it has been resolved, and nothing resolves it as a side effect of
     * reading the table. LibraryArtistsViewModel already did this, but the rebuilt Library
     * screens read from here instead, so without this every artist in the library rendered as a
     * placeholder icon forever.
     *
     * Refreshes anything missing a picture, or whose picture is over ten days old — artists
     * change their images and a permanently cached one goes stale.
     */
    private suspend fun refreshArtistImages(list: List<Artist>) {
        list.map { it.artist }
            .filter {
                it.thumbnailUrl == null ||
                    Duration.between(it.lastUpdateTime, LocalDateTime.now()) > Duration.ofDays(10)
            }
            .forEach { artist ->
                val preferred = ArtistImageResolver.resolveThumbnail(artist)
                YouTube.artist(artist.id)
                    .onSuccess { page ->
                        database.query { update(artist, page, preferred) }
                    }
                    .onFailure {
                        // The lookup failing doesn't mean we have nothing — the resolver
                        // may still have found an image locally, and dropping it here
                        // would leave the row blank for another ten days.
                        if (preferred != null) {
                            database.query {
                                update(
                                    artist.copy(
                                        thumbnailUrl = preferred,
                                        lastUpdateTime = LocalDateTime.now(),
                                    )
                                )
                            }
                        }
                    }
            }
    }

    /** Deletes these songs from the cache; they stream again the next time they play. */
    fun removeFromCache(songIds: Collection<String>) = cachedSongs.remove(songIds)

    fun clearCache() = cachedSongs.clear()

    fun unpin(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.speedDialDao.delete(id)
        }
    }

    fun pin(item: YTItem) {
        viewModelScope.launch(Dispatchers.IO) {
            database.pinToSpeedDial(SpeedDialItem.fromYTItem(item))
        }
    }
}

// a service's albums card on the imported tab
@androidx.compose.runtime.Immutable
data class ImportedAlbums(
    val count: Int,
    val covers: List<String?>,
)

/** What a service's card in the Library shows. */
@androidx.compose.runtime.Immutable
data class ImportedSourceCard(
    val source: ImportSource,
    val songCount: Int,
    val covers: List<String>,
)

/**
 * Downloads and imported files as one list, sorted as [sortType] says. They come from two queries
 * because they are two columns ([isDownloaded][com.mochyy.mochymusic.db.entities.SongEntity.isDownloaded]
 * and [isLocal][com.mochyy.mochymusic.db.entities.SongEntity.isLocal]); a download is dated by when
 * it finished downloading, an imported file by when it was imported.
 */
internal fun MusicDatabase.onDeviceSongs(sortType: SongSortType, descending: Boolean): Flow<List<Song>> =
    combine(
        downloadedSongs(SongSortType.CREATE_DATE, false),
        localSongs(SongSortType.CREATE_DATE, false),
    ) { downloaded, imported ->
        (downloaded + imported)
            .distinctBy { it.id }
            .sortedForSongSort(sortType, descending) { merged -> merged.sortedBy { it.song.dateDownload ?: it.song.inLibrary } }
    }.flowOn(Dispatchers.Default)

/**
 * A song list in the order the song sort asks for. What "date added" means depends on where the
 * songs came from, so [oldestFirst] puts them in date order.
 */
internal fun List<Song>.sortedForSongSort(
    sortType: SongSortType,
    descending: Boolean,
    oldestFirst: (List<Song>) -> List<Song>,
): List<Song> {
    val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    val sorted = when (sortType) {
        SongSortType.CREATE_DATE -> oldestFirst(this)
        SongSortType.NAME -> sortedWith(compareBy(collator) { it.song.title })
        SongSortType.ARTIST -> sortedWith(compareBy(collator) { song -> song.artists.joinToString("") { it.name } })
        SongSortType.PLAY_TIME -> sortedBy { it.song.totalPlayTime }
    }
    return if (descending) sorted.asReversed() else sorted
}
