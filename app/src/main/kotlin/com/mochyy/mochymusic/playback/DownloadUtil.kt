// DownloadUtil.kt

package com.mochyy.mochymusic.playback

import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.mochyy.mochymusic.utils.followAppProxy
import com.music.innertube.YouTube
import com.mochyy.mochymusic.constants.AudioQuality
import com.mochyy.mochymusic.constants.DownloadQuality
import com.mochyy.mochymusic.constants.DownloadQualityKey
import com.mochyy.mochymusic.constants.IpVersionKey
import com.mochyy.mochymusic.constants.OfflineModeKey
import com.mochyy.mochymusic.module.TrackQuery
import com.mochyy.mochymusic.modules.SourceModules
import com.mochyy.mochymusic.utils.dataStore
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.FormatEntity
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.db.entities.SongEntity
import com.mochyy.mochymusic.lyrics.LyricsHelper
import com.mochyy.mochymusic.models.TransferBatch
import com.mochyy.mochymusic.models.TransferItem
import com.mochyy.mochymusic.models.TransferState
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.utils.OfflineMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.mochyy.mochymusic.di.DownloadCache
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.YTPlayerUtils
import com.mochyy.mochymusic.utils.enumPreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.time.LocalDateTime
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil
@Inject
constructor(
    @ApplicationContext context: Context,
    val database: MusicDatabase,
    val databaseProvider: DatabaseProvider,
    @DownloadCache val downloadCache: SimpleCache,
    private val sourceModules: SourceModules,
    private val lyricsHelper: LyricsHelper,
) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val downloadQuality by enumPreference(context, DownloadQualityKey, DownloadQuality.HIGH)
    private val ipVersion by enumPreference(context, IpVersionKey, IpVersion.AUTO)
    /** Each song's stream url and when it stops working (ms since 1970); read on the download threads. */
    private val songUrlCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    // the providers each song's download already failed from, so its next try goes further down the download order
    private val failedProviders = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

    /** Per-track stream request headers, so a cached url is replayed with its client's Origin. */
    private val songStreamHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    /** a module's file for each song being downloaded from one, looked up once since the download opens it more than once */
    private class ModuleDownload(val providerId: String, val url: String, val headers: Map<String, String>)

    private val moduleDownloads = java.util.concurrent.ConcurrentHashMap<String, ModuleDownload>()

    /** The YouTube format each download started in, so a resumed one can't carry on in another. */
    private val downloadItags = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    private val _batch = MutableStateFlow(TransferBatch())

    /**
     * The songs downloaded since the last time nothing was downloading, with how far each has got,
     * for the Library's progress card and sheet. A finished batch stays until the next download
     * starts, so the sheet can still save it as a playlist.
     */
    val batch: StateFlow<TransferBatch> = _batch.asStateFlow()

    // Straight from the network, never through the player's cache. The player caches the stream it
    // played, which is often another format than the download asks for (a lower quality, another
    // client's stream). A download that took the cached start from there and the rest from its own
    // stream was two files glued together: the song played for a few seconds, then went silent.
    private val dataSourceFactory =
        ResolvingDataSource.Factory(
            OkHttpDataSource.Factory(
                OkHttpClient.Builder()
                    .dns(object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> {
                            val addresses = Dns.SYSTEM.lookup(hostname)
                            return when (this@DownloadUtil.ipVersion) {
                                IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                                IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                                IpVersion.AUTO -> addresses
                            }
                        }
                    })
                    .followAppProxy()
                    .build(),
            ),
        ) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")

            moduleDownload(mediaId)?.let { found ->
                return@Factory dataSpec.buildUpon()
                    .setUri(found.url.toUri())
                    .setHttpRequestHeaders(dataSpec.httpRequestHeaders + found.headers)
                    .build()
            }

            // only while it still works. This used to keep the url's lifetime instead of when it ends,
            // and compare the wrong way round, so a dead url was reused for every retry of a download
            songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let {
                return@Factory dataSpec
                    .withUri(it.first.toUri())
                    .withRequestHeaders(dataSpec.httpRequestHeaders + songStreamHeaders[mediaId].orEmpty())
            }

            val playbackData = runBlocking(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = downloadQuality.youTubeQuality,
                    connectivityManager = connectivityManager,
                )
            }.getOrThrow()
            val format = playbackData.format

            // Picking up part way with a fresh url that gives another format would glue two files
            // together. Fail instead: the failed download is wiped (see forgetStream), so trying
            // again starts the whole song over in one format.
            val startedItag = downloadItags.put(mediaId, format.itag)
            if (dataSpec.position > 0 && startedItag != null && startedItag != format.itag) {
                throw java.io.IOException("Stream format changed part way through the download of $mediaId")
            }

            database.query {
                upsert(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";")[0],
                        codecs = format.mimeType.substringAfter("codecs=", "").removeSurrounding("\""),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        // some formats don't say; the real size is saved when the download finishes.
                        // Asserting it here failed those songs' downloads every time they were tried
                        contentLength = format.contentLength ?: 0L,
                        loudnessDb = playbackData.audioConfig?.loudnessDb,
                        perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb,
                        playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                    ),
                )

                val now = LocalDateTime.now()
                val existing = getSongByIdBlocking(mediaId)?.song

                val updatedSong = if (existing != null) {
                    if (existing.dateDownload == null) {
                        existing.copy(dateDownload = now)
                    } else {
                        existing
                    }
                } else {
                    SongEntity(
                        id = mediaId,
                        title = playbackData.videoDetails?.title ?: "Unknown",
                        duration = playbackData.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0,
                        thumbnailUrl = playbackData.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url?.resize(1200, 1200),
                        dateDownload = now,
                        isDownloaded = false
                    )
                }

                upsert(updatedSong)

                updatedSong.thumbnailUrl?.let { url ->
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                    SingletonImageLoader.get(context).enqueue(request)
                }
            }

            val streamUrl = playbackData.streamUrl.let {
                "${it}&range=0-${format.contentLength ?: 10000000}"
            }

            songUrlCache[mediaId] = streamUrl to System.currentTimeMillis() + playbackData.streamExpiresInSeconds * 1000L
            songStreamHeaders[mediaId] = playbackData.streamHeaders
            dataSpec
                .withUri(streamUrl.toUri())
                .withRequestHeaders(dataSpec.httpRequestHeaders + playbackData.streamHeaders)
        }

    val downloadNotificationHelper =
        DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

    @OptIn(DelicateCoroutinesApi::class)
    val downloadManager: DownloadManager =
        DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            Executor(Runnable::run)
        ).apply {
            maxParallelDownloads = 3
            addListener(
                object : DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        downloads.update { map ->
                            map.toMutableMap().apply {
                                set(download.request.id, download)
                            }
                        }

                        scope.launch {
                            when (download.state) {
                                Download.STATE_COMPLETED -> {
                                    database.updateDownloadedInfo(download.request.id, true, LocalDateTime.now())
                                    recordDownloadedLength(download)
                                    moduleDownloads.remove(download.request.id)
                                    failedProviders.remove(download.request.id)
                                    downloadItags.remove(download.request.id)
                                    saveLyrics(download.request.id)
                                }
                                Download.STATE_FAILED -> {
                                    database.updateDownloadedInfo(download.request.id, false, null)
                                    forgetStream(download.request.id)
                                    // kept by R8 in release builds, unlike Timber: the reason is the only clue
                                    android.util.Log.w(LOG_TAG, "Download of ${download.request.id} failed", finalException)
                                }
                                Download.STATE_STOPPED,
                                Download.STATE_REMOVING -> {
                                    database.updateDownloadedInfo(download.request.id, false, null)
                                }
                                else -> {
                                }
                            }
                        }
                    }
                }
            )
        }

    init {
        val result = mutableMapOf<String, Download>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        while (cursor.moveToNext()) {
            result[cursor.download.request.id] = cursor.download
        }
        downloads.value = result

        scope.launch { followBatches() }

        // Offline mode holds downloads where they are rather than failing them: they carry on
        // when it's turned off. Only then - resuming whenever offline mode reads off, at every
        // start included, would undo downloads paused for any other reason.
        scope.launch {
            var wasOffline = false
            context.dataStore.data
                .map { it[OfflineModeKey] ?: false }
                .distinctUntilChanged()
                .collect { offline ->
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        if (offline) {
                            downloadManager.pauseDownloads()
                        } else if (wasOffline) {
                            downloadManager.resumeDownloads()
                        }
                    }
                    wasOffline = offline
                }
        }
    }

    // a failed download retries with a fresh stream from the next provider down, and drops its bytes so two formats never get glued together
    private fun forgetStream(mediaId: String) {
        songUrlCache.remove(mediaId)
        songStreamHeaders.remove(mediaId)
        downloadItags.remove(mediaId)
        moduleDownloads.remove(mediaId)?.let { failed -> failedProviders.merge(mediaId, setOf(failed.providerId)) { a, b -> a + b } }
        runCatching { downloadCache.removeResource(mediaId) }
    }

    /** the first module in the download order with a whole file of [mediaId]; null when youtube comes first or nothing else has it */
    private fun moduleDownload(mediaId: String): ModuleDownload? {
        moduleDownloads[mediaId]?.let { return it }
        val song = runBlocking(Dispatchers.IO) { database.song(mediaId).first() } ?: return null
        val track = TrackQuery(
            title = song.song.title,
            artists = song.artists.map { it.name },
            album = song.song.albumName ?: song.album?.title,
            durationMs = song.song.duration.takeIf { it > 0 }?.times(1000L),
        )
        val route = runBlocking(Dispatchers.IO) { sourceModules.downloadRoute(track, failedProviders[mediaId].orEmpty()) }
        val (module, stream) = route as? SourceModules.Route.Module ?: return null
        val (mimeType, codecs) = savedFormatOf(stream.codec)

        database.query {
            // the length is filled in when the download finishes: see recordDownloadedLength
            upsert(
                FormatEntity(
                    id = mediaId,
                    itag = -1,
                    mimeType = mimeType,
                    codecs = codecs,
                    bitrate = 0,
                    sampleRate = stream.sampleRateHz,
                    contentLength = 0L,
                    loudnessDb = null,
                    perceptualLoudnessDb = null,
                    playbackUrl = null,
                ),
            )
            if (song.song.dateDownload == null) upsert(song.song.copy(dateDownload = LocalDateTime.now()))
        }
        return ModuleDownload(module.info.id, stream.url, stream.headers).also { moduleDownloads[mediaId] = it }
    }

    // the saved file as the format badges read it, a mime type and a codec, from the codec the module named
    private fun savedFormatOf(codec: String?): Pair<String, String> = when (codec?.uppercase()) {
        "FLAC" -> "audio/flac" to "flac"
        "ALAC" -> "audio/mp4" to "alac"
        "AAC", "HE-AAC" -> "audio/mp4" to "aac"
        "MP3" -> "audio/mpeg" to "mp3"
        "OPUS" -> "audio/ogg" to "opus"
        "E-AC-3", "DOLBY ATMOS" -> "audio/mp4" to "ec-3"
        else -> "audio/*" to codec?.lowercase().orEmpty()
    }

    /** One lyrics lookup at a time: an album finishing downloading would otherwise ask every provider at once per song. */
    private val lyricsLock = Mutex()

    /**
     * A downloaded song's lyrics, saved with it so they're there offline. Lyrics were only ever
     * fetched when a song played with lyrics on, so a download played offline had none. A song
     * that already has lyrics keeps them; one saved as "not found" is asked again.
     */
    private suspend fun saveLyrics(songId: String) {
        if (OfflineMode.isOn) return
        lyricsLock.withLock {
            val stored = database.lyrics(songId).first()
            if (stored != null && stored.lyrics != LyricsEntity.LYRICS_NOT_FOUND) return
            val song = database.song(songId).first() ?: return
            val found = runCatching { lyricsHelper.getLyrics(song.toMediaMetadata()) }.getOrNull() ?: return
            if (found.provider == LyricsHelper.NoConnectionProvider) return
            database.query { upsert(LyricsEntity(id = songId, lyrics = found.lyrics, provider = found.provider)) }
        }
    }

    /**
     * The downloaded file's real size into the song's format record. Playback checks the download
     * holds the whole song against that size, and a FLAC is several times the size YouTube's
     * stream was recorded at.
     */
    private suspend fun recordDownloadedLength(download: Download) {
        val length = download.contentLength.takeIf { it > 0 } ?: return
        val format = database.format(download.request.id).first() ?: return
        if (format.contentLength != length) database.query { upsert(format.copy(contentLength = length)) }
    }

    private val DownloadQuality.youTubeQuality: AudioQuality
        get() = when (this) {
            DownloadQuality.HIGH -> AudioQuality.HIGH
            DownloadQuality.MEDIUM -> AudioQuality.MEDIUM
            DownloadQuality.LOW -> AudioQuality.LOW
        }

    /**
     * Keeps [batch] up to date. The manager only reports a download starting, finishing or
     * failing, never how far along it is, so while anything downloads this asks twice a second.
     * Nothing is polled while idle: it waits for [downloads] to show a download under way.
     */
    private suspend fun followBatches() {
        while (true) {
            downloads.first { map -> map.values.any { it.isUnderWay } }
            val ids = LinkedHashSet<String>()
            val songs = HashMap<String, com.mochyy.mochymusic.db.entities.Song?>()
            while (true) {
                // the manager may only be read on the thread it was made on
                val running = kotlinx.coroutines.withContext(Dispatchers.Main) {
                    downloadManager.currentDownloads.filter { it.isUnderWay }
                }
                running.forEach { ids += it.request.id }
                val known = downloads.value
                // cancelled or deleted part way: it leaves the batch rather than showing as failed
                ids.retainAll { id -> known[id]?.state.let { it != null && it != Download.STATE_REMOVING } }
                ids.forEach { id -> if (id !in songs) songs[id] = database.song(id).first() }
                _batch.value = TransferBatch(
                    ids.map { id -> transferItem(id, running.firstOrNull { it.request.id == id } ?: known[id], songs[id]) },
                )
                if (running.isEmpty()) break
                delay(500)
            }
            // a batch that ended on an out-of-date entry in downloads can't spin this loop
            delay(1_000)
        }
    }

    private val Download.isUnderWay: Boolean
        get() = state == Download.STATE_QUEUED || state == Download.STATE_DOWNLOADING || state == Download.STATE_RESTARTING

    private fun transferItem(id: String, download: Download?, song: com.mochyy.mochymusic.db.entities.Song?): TransferItem =
        TransferItem(
            key = id,
            title = song?.song?.title
                ?: download?.request?.data?.takeIf { it.isNotEmpty() }?.decodeToString()
                ?: "Song",
            subtitle = song?.artists?.joinToString { it.name }?.ifBlank { null },
            artworkUrl = song?.song?.thumbnailUrl,
            state = when (download?.state) {
                Download.STATE_COMPLETED -> TransferState.Done
                Download.STATE_DOWNLOADING -> TransferState.Running
                Download.STATE_QUEUED, Download.STATE_RESTARTING -> TransferState.Waiting
                else -> TransferState.Failed
            },
            // unknown until the server says how big the file is
            progress = ((download?.percentDownloaded ?: 0f) / 100f).coerceIn(0f, 1f),
            songId = id,
        )

    fun getDownload(songId: String): Flow<Download?> =
        downloads.map { it[songId] }.distinctUntilChanged()

    fun release() {
        scope.cancel()
    }

    private companion object {
        const val LOG_TAG = "MochyDownload"
    }
}
