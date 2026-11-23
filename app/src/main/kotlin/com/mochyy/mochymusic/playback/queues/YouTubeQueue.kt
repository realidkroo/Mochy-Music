// YouTubeQueue.kt

package com.mochyy.mochymusic.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.NextResult
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.utils.NotRecommended
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private var continuation: String? = null
    private var failedPageLoads = 0

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            val seedId = preloadItem?.id ?: endpoint.videoId
            if (seedId != null && seedId.startsWith("LOCAL_")) {
                val localItem = preloadItem?.toMediaItem()
                return@withContext Queue.Status(
                    title = preloadItem?.title,
                    items = listOfNotNull(localItem),
                    mediaItemIndex = 0,
                )
            }

            var lastException: Throwable? = null

            for (attempt in 0..MAX_RETRIES) {
                try {
                    var nextResult = YouTube.next(endpoint, continuation).getOrThrow()

                    // A bare videoId endpoint (or a signed-out / unpersonalized session) often returns
                    // just the song itself — sometimes even with a non-null continuation that yields
                    // nothing similar. Always upgrade to the song's RDAMVM radio station when <= 1 item.
                    if (nextResult.items.size <= 1) {
                        radioEndpoint()?.let { radio ->
                            YouTube.next(radio).getOrNull()
                                ?.takeIf { it.items.size > nextResult.items.size }
                                ?.let { nextResult = it }
                        }
                    }

                    // Conversely, if RDAMVM itself came back with <= 1 song, try bare videoId (which can
                    // resolve via automixPreviewVideoRenderer) before falling back to related / artist songs.
                    if (nextResult.items.size <= 1 && isSongRadioEndpoint(endpoint) && seedId != null) {
                        YouTube.next(WatchEndpoint(videoId = seedId)).getOrNull()
                            ?.takeIf { it.items.size > nextResult.items.size }
                            ?.let { nextResult = it }
                    }

                    val mix = isMix()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    var items = nextResult.items.map { it.toMediaItem() }
                    var index = nextResult.currentIndexFor(seedId)

                    // If YouTube's radio/up-next still gave <= 1 track, populate similar songs from
                    // the Related tab (and as a last resort the artist's tracks) so radio never stalls.
                    if (mix && items.size <= 1 && seedId != null) {
                        val extra = fallbackSimilarItems(seedId, nextResult.relatedEndpoint)
                        if (extra.isNotEmpty()) {
                            val seed = items.firstOrNull() ?: preloadItem?.toMediaItem()
                            val combined = buildList {
                                if (seed != null) add(seed)
                                extra.forEach { candidate ->
                                    if (candidate.mediaId != seedId) add(candidate)
                                }
                            }.distinctBy { it.mediaId }
                            if (combined.size > items.size) {
                                items = combined
                                index = 0
                            }
                        }
                    }

                    if (!mix) {
                        return@withContext Queue.Status(title = nextResult.title, items = items, mediaItemIndex = index)
                    }
                    // the song you started from still plays even if you hid it, only what youtube adds around it goes
                    val hidden = NotRecommended.songs
                    return@withContext Queue.Status(
                        title = nextResult.title,
                        items = items.filterIndexed { i, item -> i == index || item.mediaId !in hidden },
                        mediaItemIndex = index - items.take(index).count { it.mediaId in hidden },
                    )
                } catch (e: Exception) {
                    lastException = e
                    // If RDAMVM failed, retry with bare videoId; if bare videoId failed, retry with RDAMVM.
                    if (isSongRadioEndpoint(endpoint) && seedId != null) {
                        endpoint = WatchEndpoint(videoId = seedId)
                    } else {
                        radioEndpoint()?.let { endpoint = it }
                    }
                    if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }

            // Final fallback: even if YouTube.next failed on all retries, try Related / Artist tracks
            // so Start Radio still queues similar songs around the selected track.
            if (seedId != null && isMix()) {
                val extra = fallbackSimilarItems(seedId, relatedEndpoint = null)
                val seed = preloadItem?.toMediaItem()
                if (extra.isNotEmpty() || seed != null) {
                    val hidden = NotRecommended.songs
                    val combined = buildList {
                        if (seed != null) add(seed)
                        extra.forEach { candidate ->
                            if (candidate.mediaId != seedId && candidate.mediaId !in hidden) add(candidate)
                        }
                    }.distinctBy { it.mediaId }
                    if (combined.isNotEmpty()) {
                        return@withContext Queue.Status(title = preloadItem?.title, items = combined, mediaItemIndex = 0)
                    }
                }
            }

            throw lastException ?: Exception("Failed to get initial status")
        }
    }

    private suspend fun fallbackSimilarItems(
        seedId: String,
        relatedEndpoint: com.music.innertube.models.BrowseEndpoint?,
    ): List<MediaItem> {
        val resolvedRelated = relatedEndpoint
            ?: runCatching { YouTube.next(WatchEndpoint(videoId = seedId)).getOrNull()?.relatedEndpoint }.getOrNull()
        val relatedSongs = resolvedRelated?.let { ep ->
            runCatching { YouTube.related(ep).getOrNull()?.songs.orEmpty().map { it.toMediaItem() } }
                .getOrDefault(emptyList())
        }.orEmpty()
        if (relatedSongs.isNotEmpty()) return relatedSongs

        val artistId = preloadItem?.artists?.firstNotNullOfOrNull { it.id } ?: return emptyList()
        return runCatching {
            YouTube.artist(artistId).getOrNull()?.sections.orEmpty()
                .flatMap { it.items }
                .filterIsInstance<SongItem>()
                .map { it.toMediaItem() }
                .distinctBy { it.mediaId }
        }.getOrDefault(emptyList())
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            val currentContinuation = continuation ?: return@withContext emptyList()
            var lastException: Throwable? = null

            for (attempt in 0..MAX_RETRIES) {
                try {
                    val nextResult = YouTube.next(endpoint, currentContinuation).getOrThrow()
                    endpoint = nextResult.endpoint
                    continuation = nextResult.continuation
                    failedPageLoads = 0
                    val items = nextResult.items.map { it.toMediaItem() }
                    return@withContext if (isMix()) items.filter { it.mediaId !in NotRecommended } else items
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
            // Only give up on the continuation after repeated failed loads, so one network
            // blip doesn't permanently end the queue.
            if (++failedPageLoads >= MAX_FAILED_PAGE_LOADS) {
                continuation = null
            }
            throw lastException ?: Exception("Failed to get next page")
        }
    }

    // a song on its own or a radio is youtube picking for you, curated (RDCLAK) and normal playlists and albums play as they are
    private fun isMix(): Boolean {
        val playlistId = endpoint.playlistId ?: return true
        return playlistId.startsWith("RD") && !playlistId.startsWith("RDCLAK")
    }

    private fun radioEndpoint(): WatchEndpoint? {
        val videoId = endpoint.videoId ?: preloadItem?.id ?: return null
        if (endpoint.playlistId != null) return null
        return radioWatchEndpoint(videoId)
    }

    private fun NextResult.currentIndexFor(videoId: String?): Int =
        currentIndex
            ?: videoId?.let { id -> items.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            ?: 0

    companion object {
        private const val MAX_RETRIES = 2
        private const val MAX_FAILED_PAGE_LOADS = 3
        private const val RETRY_DELAY_MS = 750L

        internal fun radioWatchEndpoint(videoId: String): WatchEndpoint =
            WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId")

        internal fun isSongRadioEndpoint(endpoint: WatchEndpoint): Boolean =
            endpoint.playlistId?.startsWith("RDAMVM") == true

        /**
         * Ensures a standalone song click (not from an album or playlist) starts the song's
         * YouTube Music radio mix (`RDAMVM<id>`) rather than a bare single-video endpoint.
         */
        fun forSong(endpoint: WatchEndpoint?, song: MediaMetadata): YouTubeQueue {
            val resolved = if (endpoint?.playlistId != null) endpoint else radioWatchEndpoint(song.id)
            return YouTubeQueue(resolved, song)
        }

        fun radio(videoId: String): YouTubeQueue =
            YouTubeQueue(radioWatchEndpoint(videoId))

        fun radio(song: MediaMetadata): YouTubeQueue {
            return YouTubeQueue(
                radioWatchEndpoint(song.id),
                song
            )
        }
    }
}
