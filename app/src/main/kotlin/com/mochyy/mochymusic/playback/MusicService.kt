// MusicService.kt

@file:Suppress("DEPRECATION")

package com.mochyy.mochymusic.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.mochyy.mochymusic.MainActivity
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.AudioNormalizationKey
import com.mochyy.mochymusic.constants.AudioOffload
import com.mochyy.mochymusic.constants.AudioQualityKey
import com.mochyy.mochymusic.constants.AutoDownloadOnLikeKey
import com.mochyy.mochymusic.constants.AutoLoadMoreKey
import com.mochyy.mochymusic.constants.AutoSkipNextOnErrorKey
import com.mochyy.mochymusic.constants.DontSkipOnFailureKey
import com.mochyy.mochymusic.constants.StreamFailureTimeoutKey
import com.mochyy.mochymusic.constants.CrossfadeDurationKey
import com.mochyy.mochymusic.constants.CrossfadeEnabledKey
import com.mochyy.mochymusic.constants.TransportEffect
import com.mochyy.mochymusic.constants.TransportEffectKey
import com.mochyy.mochymusic.constants.TransportFadeDefaultSeconds
import com.mochyy.mochymusic.constants.TransportFadeEnabledKey
import com.mochyy.mochymusic.constants.TransportFadeMaxSeconds
import com.mochyy.mochymusic.constants.TransportFadeMinSeconds
import com.mochyy.mochymusic.constants.TransportFadeSecondsKey
import com.mochyy.mochymusic.constants.CrossfadeGaplessKey
import com.mochyy.mochymusic.constants.CrossmixMode
import com.mochyy.mochymusic.constants.CrossmixModeKey
import com.mochyy.mochymusic.constants.HiFiResamplerKey
import com.mochyy.mochymusic.constants.HiResOutputKey
import com.mochyy.mochymusic.constants.PreferUsbDacKey
import com.mochyy.mochymusic.constants.UsbPassthroughKey
import com.mochyy.mochymusic.constants.AudioPassMode
import com.mochyy.mochymusic.constants.AudioPassModeKey
import com.mochyy.mochymusic.constants.AudioPassFixedVolumeKey
import com.mochyy.mochymusic.constants.AudioPassRestoreKey
import com.mochyy.mochymusic.constants.playable
import com.mochyy.mochymusic.crossmix.CrossmixAnalysisRepository
import com.mochyy.mochymusic.constants.DisableLoadMoreWhenRepeatAllKey
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.mochyy.mochymusic.constants.HideExplicitKey
import com.mochyy.mochymusic.constants.HideVideoSongsKey
import com.mochyy.mochymusic.constants.HistoryDuration
import com.mochyy.mochymusic.constants.MediaSessionConstants.CommandToggleLike
import com.mochyy.mochymusic.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.mochyy.mochymusic.constants.MediaSessionConstants.CommandToggleShuffle
import com.mochyy.mochymusic.constants.MediaSessionConstants.CommandToggleStartRadio
import com.mochyy.mochymusic.constants.OfflineModeKey
import com.mochyy.mochymusic.constants.PauseListenHistoryKey
import com.mochyy.mochymusic.constants.MixWithOtherAppsKey
import com.mochyy.mochymusic.constants.PauseOnMute
import com.mochyy.mochymusic.constants.PersistentQueueKey
import com.mochyy.mochymusic.constants.InfiniteQueueKey
import com.mochyy.mochymusic.constants.PersistentShuffleAcrossQueuesKey
import com.mochyy.mochymusic.constants.PlayerVolumeKey
import com.mochyy.mochymusic.constants.RememberShuffleAndRepeatKey
import com.mochyy.mochymusic.constants.RepeatModeKey
import com.mochyy.mochymusic.constants.ResumeOnBluetoothConnectKey
import com.mochyy.mochymusic.constants.ScrobbleDelayPercentKey
import com.mochyy.mochymusic.constants.ScrobbleDelaySecondsKey
import com.mochyy.mochymusic.constants.ScrobbleMinSongDurationKey
import com.mochyy.mochymusic.constants.ShowLyricsKey
import com.mochyy.mochymusic.constants.ShuffleModeKey
import com.mochyy.mochymusic.constants.ShufflePlaylistFirstKey
import com.mochyy.mochymusic.constants.PreventDuplicateTracksInQueueKey
import com.mochyy.mochymusic.constants.SimilarContent
import com.mochyy.mochymusic.constants.SkipSilenceInstantKey
import com.mochyy.mochymusic.constants.SkipSilenceKey
import com.mochyy.mochymusic.constants.StopPlaybackOnTaskRemovedKey
import com.mochyy.mochymusic.constants.IpVersionKey
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Event
import com.mochyy.mochymusic.db.entities.FormatEntity
import com.mochyy.mochymusic.db.entities.LyricsEntity
import com.mochyy.mochymusic.db.entities.RelatedSongMap
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.di.DownloadCache
import com.mochyy.mochymusic.di.PlayerCache
import com.mochyy.mochymusic.eq.AudioFxService
import com.mochyy.mochymusic.eq.AudioLabStore
import com.mochyy.mochymusic.eq.EqualizerService
import com.mochyy.mochymusic.eq.audio.CustomEqualizerAudioProcessor
import com.mochyy.mochymusic.eq.audio.LoudnessAudioProcessor
import com.mochyy.mochymusic.eq.audio.ReverbAudioProcessor
import com.mochyy.mochymusic.eq.audio.SpatialAudioProcessor
import com.mochyy.mochymusic.extensions.SilentHandler
import com.mochyy.mochymusic.extensions.collect
import com.mochyy.mochymusic.extensions.collectLatest
import com.mochyy.mochymusic.extensions.currentMetadata
import com.mochyy.mochymusic.extensions.findNextMediaItemById
import com.mochyy.mochymusic.extensions.isAutoplay
import com.mochyy.mochymusic.extensions.withAutoplay
import com.mochyy.mochymusic.extensions.mediaItems
import com.mochyy.mochymusic.extensions.metadata
import com.mochyy.mochymusic.extensions.setOffloadEnabled
import com.mochyy.mochymusic.extensions.toEnum
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.extensions.toPersistQueue
import com.mochyy.mochymusic.extensions.toQueue
import com.mochyy.mochymusic.lyrics.LyricsHelper
import com.mochyy.mochymusic.models.PersistPlayerState
import com.mochyy.mochymusic.models.PersistQueue
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.playback.audio.SilenceDetectorAudioProcessor
import com.mochyy.mochymusic.playback.audio.HapticAudioProcessor
import com.mochyy.mochymusic.playback.audio.CrossmixClockSink
import com.mochyy.mochymusic.playback.audio.CrossmixTransitionAudioProcessor
import com.mochyy.mochymusic.playback.audio.pass.LabSound
import com.mochyy.mochymusic.playback.audio.pass.AudioPass
import com.mochyy.mochymusic.playback.audio.pass.PassRouterSink
import com.mochyy.mochymusic.playback.audio.pass.PassSink
import com.mochyy.mochymusic.playback.audio.pass.PassBlocker
import com.mochyy.mochymusic.playback.audio.pass.clearPassBlockers
import com.mochyy.mochymusic.playback.audio.pass.PassBlockersBefore
import com.mochyy.mochymusic.playback.audio.pass.restorePassBlockers
import com.mochyy.mochymusic.playback.audio.pass.passBlockersOf
import com.mochyy.mochymusic.constants.MusicHapticsEnabledKey
import com.mochyy.mochymusic.constants.MusicHapticsSensitivityKey
import com.mochyy.mochymusic.constants.HapticSensitivity
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import com.mochyy.mochymusic.playback.queues.EmptyQueue
import com.mochyy.mochymusic.playback.queues.Queue
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.playback.queues.filterExplicit
import com.mochyy.mochymusic.playback.queues.filterVideoSongs
import com.mochyy.mochymusic.utils.NotRecommended
import com.mochyy.mochymusic.utils.CoilBitmapLoader
import com.mochyy.mochymusic.utils.NetworkConnectivityObserver
import com.mochyy.mochymusic.scrobble.ScrobbleTimer
import com.mochyy.mochymusic.scrobble.Scrobbler
import com.mochyy.mochymusic.utils.SyncUtils
import com.mochyy.mochymusic.utils.YTPlayerUtils
import com.mochyy.mochymusic.constants.ShowStreamSourceToastKey
import com.mochyy.mochymusic.constants.YtReportPlaysKey
import com.mochyy.mochymusic.constants.ForcePlaybackKey
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import com.mochyy.mochymusic.utils.reportException
import com.mochyy.mochymusic.widget.MochyWidgetManager
import com.mochyy.mochymusic.widget.MusicWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

private const val INSTANT_SILENCE_SKIP_STEP_MS = 15_000L

/** A saved volume below this is taken as one saved while muted or mid-fade, not the user's own. */
private const val MinRestoredVolume = 0.02f
private const val INSTANT_SILENCE_SKIP_SETTLE_MS = 350L

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@androidx.annotation.OptIn(UnstableApi::class)
/** A stream that failed at [startedAt] (elapsedRealtime) and skips to the next track [timeoutMs] later. */
data class StreamFailure(val startedAt: Long, val timeoutMs: Long)

@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    @Inject
    lateinit var equalizerService: EqualizerService

    /** Audio Lab: owns the equalizer and Audio FX settings and pushes them to the processors live */
    @Inject
    lateinit var audioLab: AudioLabStore

    @Inject
    lateinit var audioFx: AudioFxService

    @Inject
    lateinit var scrobbler: Scrobbler

    /** the Discord status; it does nothing until the user signs in on the Discord page */
    @Inject
    lateinit var discordPresence: com.mochyy.mochymusic.discord.DiscordPresence

    /** source modules (Apple Music): asked for a stream before YouTube */
    @Inject
    lateinit var sourceModules: com.mochyy.mochymusic.modules.SourceModules

    @Inject
    lateinit var widgetManager: MochyWidgetManager

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var legacyFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var hasAudioFocus = false
    private var reentrantFocusGain = false
    private var mixWithOtherApps = false
    private var wasPlayingBeforeCall = false
    private val callWatcher by lazy { CallWatcher(this, ::onCallChanged) }
    private var wasPlayingBeforeVolumeMute = false
    private var isPausedByVolumeMute = false
    var preferredDeviceId: Int? = null
        private set

    /** the outputs, the one picked in the player, the usb dac's mixer, and whether 32-bit float may pass */
    val audioOutputs by lazy { com.mochyy.mochymusic.playback.audio.AudioOutputs(this) }

    /** the exclusive output every player shares: bit-perfect, aaudio exclusive and the direct usb driver */
    val audioPass by lazy { AudioPass(this, audioOutputs) }

    // what keeps songs off the exclusive output right now: audio pass lists them and the router reads them
    private val _passBlockers = MutableStateFlow<Set<PassBlocker>>(emptySet())
    val passBlockers: StateFlow<Set<PassBlocker>> = _passBlockers.asStateFlow()
    // each player's stats for nerds; weak like the spectra, so a released player takes its own along
    private val playerInspectors = java.util.WeakHashMap<Player, StreamInspector>()

    /** what [player]'s song really is on its way out, for stream info */
    fun inspectorOf(player: Player): StreamInspector? = playerInspectors[player]

    private var crossfadeEnabled = false
    private var crossfadeDuration = 5000f
    private var crossfadeGapless = true
    private var crossmixMode = CrossmixMode.OFF
    private var crossfadeTriggerJob: Job? = null

    private val secondaryPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).e(error, "Secondary player error")
            secondaryPlayer?.stop()
            secondaryPlayer?.clearMediaItems()
            // dropped without this, its decoders and audio track stayed alive with nothing to free them
            secondaryPlayer?.let { failed ->
                failed.removeListener(this)
                playerCrossmixProcessors.remove(failed)
                playerSilenceProcessors.remove(failed)
                unregisterEqualizer(failed)
                failed.release()
            }
            secondaryPlayer = null
        }
    }

    private var scope = CoroutineScope(Dispatchers.Main) + Job()

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)

    /** A stream that failed and is counting down to a skip; null when nothing has failed. */
    val streamFailure = MutableStateFlow<StreamFailure?>(null)
    private var failureJob: Job? = null
    private val isNetworkConnected = MutableStateFlow(false)

    private lateinit var audioQuality: com.mochyy.mochymusic.constants.AudioQuality
    private lateinit var ipVersion: IpVersion

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null

    // Bumped whenever the queue is replaced, so in-flight loads for an old queue don't append to the new one.
    private var queueGeneration = 0
    private var initialQueueLoading = false
    private var runwayJob: Job? = null

    // Radio source used to keep the queue going once the current queue runs out (infinite queue).
    private var infiniteSource: Queue? = null
    private val usedInfiniteSeeds = HashSet<String>()
    // Seeds whose radio couldn't be loaded (usually a network error), given another go on a retry.
    private val failedInfiniteSeeds = HashSet<String>()

    // When the runway gives up with the queue running dry, it tries again after a growing delay,
    // rather than leaving a single searched song with nothing after it until the next song change.
    private var runwayRetryJob: Job? = null
    private var runwayRetries = 0

    // The settings cache picks up a write a moment after it lands, so right after the toggle the
    // service goes by what was just set until the cache agrees.
    private var pendingInfiniteQueue: Boolean? = null

    // Lining up Autoplay ahead of time is a nicety, so after an attempt that found nothing (offline,
    // only local songs) it waits a while instead of retrying on every song change.
    private var autoplayPreviewRetryAt = 0L

    val currentMediaMetadata = MutableStateFlow<com.mochyy.mochymusic.models.MediaMetadata?>(null)
    private val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat =
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    lateinit var playerVolume: MutableStateFlow<Float>
    val isMuted = MutableStateFlow(false)

    fun toggleMute() {
        val newMutedState = !isMuted.value
        isMuted.value = newMutedState

        player.volume = if (newMutedState) 0f else playerVolume.value
    }

    fun setMuted(muted: Boolean) {
        isMuted.value = muted

        player.volume = if (muted) 0f else playerVolume.value
    }

    /** audio pass's "turn these off": every setting that keeps songs off the exclusive output goes back to neutral, remembering how it was */
    fun clearPassBlockers() {
        // audio lab and the volume are read before anything changes; the preferences inside the same edit that clears them
        val lab = audioLab
        val volume = playerVolume.value
        scope.launch {
            dataStore.edit { prefs ->
                prefs[AudioPassRestoreKey] = passJson.encodeToString(PassBlockersBefore.of(prefs, lab, volume))
                prefs.clearPassBlockers()
            }
        }
        audioLab.clearPassBlockers()
        playerVolume.value = 1f
    }

    /** "turn these back on": everything "turn these off" switched off goes back to how it was */
    fun restorePassBlockers() {
        scope.launch {
            var before: PassBlockersBefore? = null
            dataStore.edit { prefs ->
                before = prefs[AudioPassRestoreKey]?.let { runCatching { passJson.decodeFromString<PassBlockersBefore>(it) }.getOrNull() }
                before?.let(prefs::restorePassBlockers)
                prefs.remove(AudioPassRestoreKey)
            }
            before?.let {
                audioLab.restorePassBlockers(it)
                playerVolume.value = it.playerVolume
            }
        }
    }

    /** whether there's a "turn these back on" waiting */
    val canRestorePassBlockers: Flow<Boolean> by lazy { dataStore.data.map { it[AudioPassRestoreKey] != null }.distinctUntilChanged() }

    fun setPreferredAudioDevice(deviceId: Int?) {
        preferredDeviceId = deviceId
        audioOutputs.select(deviceId)
    }

    // every player follows the picked output: crossmix and crossfade make new ones, which used to fall back to the default
    private fun applyOutputRoute(device: AudioDeviceInfo?) {
        playerCrossmixProcessors.keys.forEach { (it as? ExoPlayer)?.setPreferredAudioDevice(device) }
        if (::player.isInitialized) player.setPreferredAudioDevice(device)
        secondaryPlayer?.setPreferredAudioDevice(device)
        fadingPlayer?.setPreferredAudioDevice(device)
    }

    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache
    private val resolvedStreamSources = java.util.concurrent.ConcurrentHashMap<String, String>()
    // Force playback: for each song, the providers that already failed on it, passed over next time
    private val failedSources = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
    // when each song's first failure above was, so they're forgotten after FailedSourceMemoryMs
    private val failedSourcesAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** the request headers each source module stream needs, by its cache key */
    private val moduleStreamHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
    // which provider each song was last handed to (a module id, or "youtube")
    private val resolvedSourceIds = java.util.concurrent.ConcurrentHashMap<String, String>()
    // the providers picked by hand in stream info, by song; they win over the order until they stop having it
    private val forcedRoutes = java.util.concurrent.ConcurrentHashMap<String, ForcedRoute>()
    private val _playingFrom = MutableStateFlow<Map<String, PlayingFrom>>(emptyMap())
    /** where each song played from, for stream info's provider row */
    val playingFrom = _playingFrom.asStateFlow()
    /** stream info's "force change": stops, asks the picked provider up to 20 times, then reloads from it */
    val providerSwitch by lazy { ProviderSwitch({ scope }, ProviderSwitchHost()) }
    /** what each song is playing as, by media id, for the player's format badge */
    val streamFacts = MutableStateFlow<Map<String, StreamFacts>>(emptyMap())
    /**
     * What this phone had no decoder for, by song ("No FLAC decoder available on this phone"),
     * and the provider whose stream it was: told with what plays instead once its format is
     * known, or as the reason when nothing does.
     */
    private data class DecoderNotice(val text: String, val from: String?)
    private val decoderNotices = java.util.concurrent.ConcurrentHashMap<String, DecoderNotice>()
    private var lastAnnouncedSourceMediaId: String? = null
    lateinit var player: ExoPlayer
        private set
    private var secondaryPlayer: ExoPlayer? = null
    private var fadingPlayer: ExoPlayer? = null
    private var isCrossfading = false
    private var crossfadeJob: Job? = null
    // the outgoing song keeps its own normalisation on its own audio session until it's let go;
    // one shared enhancer used to be handed the next song's gain mid-fade
    private var fadingLoudnessEnhancer: LoudnessEnhancer? = null
    private var loudnessEnhancerSessionId = C.AUDIO_SESSION_ID_UNSET
    // set while Crossmix replays the song-change hooks for the song it just handed over to
    private var promotingCrossmix = false
    private val crossmixAnalysisRepository by lazy {
        CrossmixAnalysisRepository(applicationContext) { createDataSourceFactory() }
    }
    private val crossmixDirector by lazy { CrossmixDirector(scope, CrossmixHost(), crossmixAnalysisRepository) }
    // fades and layered effects for pause, play and skips; the app's buttons and the media session both go through it
    val transport by lazy { TransportTransitions({ scope }, TransportHost()) }
    /** Crossmix is playing two songs together right now, for the player's "Mixing…" note */
    val crossmixMixing = MutableStateFlow(false)
    /** what Crossmix last decided, in a line, for its test tools in Experimental */
    val crossmixStatus = MutableStateFlow("")

    /** plans this song's Crossmix hand-over, then jumps to just before it: for trying mixes out */
    fun testCrossmix() = crossmixDirector.testMix()

    /** the stream each song was handed to the player as, when it wasn't the plain YouTube one */
    private val playingStreams = java.util.concurrent.ConcurrentHashMap<String, com.mochyy.mochymusic.playback.custom.CustomStreamResult>()

    // streams opened only to be listened to (a music video matched against its song); a failure there isn't the user's playback failing
    private val listeningOnlyStreams: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    // matches a song's recording against its music video's, for the video background and for a music video's lyrics
    private val videoAudioSync by lazy {
        com.mochyy.mochymusic.lyrics.VideoAudioSync({ createDataSourceFactory() }, listeningOnlyStreams)
    }

    // where songId's recording sits inside videoId's, found by listening to both; null when they don't share it
    suspend fun matchSongToVideo(songId: String, videoId: String): com.mochyy.mochymusic.lyrics.SongVideoMap? {
        val current = withContext(Dispatchers.Main) { player.currentMediaItem }
        fun sourceOf(id: String): com.mochyy.mochymusic.lyrics.VideoAudioSync.Source {
            if (current != null && current.mediaId == id) {
                val (uri, key) = lyricsAudioSource(current)
                return com.mochyy.mochymusic.lyrics.VideoAudioSync.Source(uri, key, listeningOnly = false)
            }
            return com.mochyy.mochymusic.lyrics.VideoAudioSync.Source(id.toUri(), id, listeningOnly = true)
        }
        return videoAudioSync.match(sourceOf(songId), sourceOf(videoId))
    }

    /** Lyrics auto offset; the lyrics offset sheet starts it and shows how it's going. */
    val lyricsAutoSync by lazy {
        com.mochyy.mochymusic.lyrics.LyricsAutoSync(
            dataSourceFactory = { createDataSourceFactory() },
            saveOffset = { songId, offsetMs -> database.query { updateLyricsOffset(songId, offsetMs) } },
            onUnwatchedResult = ::announceLyricsSync,
        )
    }

    /** Lines the playing song's lyrics up with its singing: see [com.mochyy.mochymusic.lyrics.LyricsAutoSync]. */
    fun autoSyncLyrics(lines: List<com.mochyy.mochymusic.lyrics.LyricsEntry>, currentOffsetMs: Int) {
        val item = player.currentMediaItem ?: return
        val (uri, key) = lyricsAudioSource(item)
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: player.currentMetadata?.duration?.times(1_000L) ?: 0L
        lyricsAutoSync.start(item.mediaId, uri, key, durationMs, lines, currentOffsetMs)
    }

    /**
     * What auto offset listens to: the very stream the player is playing, so the timing it finds
     * is the timing heard. A source module's stream that's a playlist of segments or locked (HLS,
     * DASH, DRM) can't be read as one file; then it's the song's YouTube stream, which nearly
     * always has the same timing.
     */
    private fun lyricsAudioSource(item: androidx.media3.common.MediaItem): Pair<android.net.Uri, String> {
        val stream = playingStreams[item.mediaId]
        if (stream != null && !stream.isHls && !stream.isDash && stream.widevineLicense == null && stream.decryptionKey == null) {
            return stream.streamUrl.toUri() to (stream.cacheKey ?: item.mediaId)
        }
        return (item.localConfiguration?.uri ?: item.mediaId.toUri()) to item.mediaId
    }

    /** A sync that finished with its sheet closed says how it went in the banner. */
    private fun announceLyricsSync(state: com.mochyy.mochymusic.lyrics.LyricsAutoSync.State) {
        val text = when (state) {
            is com.mochyy.mochymusic.lyrics.LyricsAutoSync.State.Synced -> "Lyrics synced to the singing"
            is com.mochyy.mochymusic.lyrics.LyricsAutoSync.State.Failed -> state.message
            else -> return
        }
        Handler(Looper.getMainLooper()).post { showBanner(text) }
    }

    private data class CrossfadeConfig(
        val mode: CrossmixMode,
        val enabled: Boolean,
        val durationSeconds: Float,
        val disableForGaplessAlbums: Boolean,
    )

    private lateinit var mediaSession: MediaLibrarySession

    private val playerInitialized = MutableStateFlow(false)
    val isPlayerReady: kotlinx.coroutines.flow.StateFlow<Boolean> = playerInitialized.asStateFlow()

    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow = _playerFlow.asStateFlow()

    private val playerSilenceProcessors = HashMap<Player, SilenceDetectorAudioProcessor>()
    private val playerReplayGain = HashMap<Player, com.mochyy.mochymusic.eq.audio.ReplayGainTags>()
    // each player's equaliser, so it's unregistered when that player goes (crossfades make many)
    private val playerEqProcessors = HashMap<Player, CustomEqualizerAudioProcessor>()
    private val playerCrossmixProcessors = HashMap<Player, CrossmixTransitionAudioProcessor>()
    // each player's ear for the visualizer; weak, so a player let go takes its own with it
    private val playerSpectra = java.util.WeakHashMap<Player, com.mochyy.mochymusic.playback.audio.AudioSpectrum>()

    /** what [player] is playing, as bands for the visualizer (the main thread's) */
    fun spectrumOf(player: Player): com.mochyy.mochymusic.playback.audio.AudioSpectrum? = playerSpectra[player]


    private val instantSilenceSkipEnabled = MutableStateFlow(false)

    private var isAudioEffectSessionOpened = false
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private lateinit var hapticAudioProcessor: HapticAudioProcessor

    /** When a play counts for Last.fm and Libre.fm; the scrobbler skips services that aren't signed in. */
    private val scrobbleTimer by lazy { ScrobbleTimer({ scope }, scrobbler) }

    // counts each youtube song people really listen to as a play on youtube, unless app behaviour turns it off
    private val playReporter by lazy { YouTubePlayReporter({ scope }, { player.currentPosition }) }

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())

    private var originalQueueSize: Int = 0

    private var consecutivePlaybackErr = 0
    private var retryJob: Job? = null
    private var retryCount = 0
    private var silenceSkipJob: Job? = null

    private val songUrlCache = HashMap<String, Pair<String, Long>>()

    /**
     * Headers that must accompany the stream request for each track, keyed by media id.
     * googlevideo checks Origin/Referer against the client that issued the url, so a cached
     * url replayed without them is rejected.
     */
    private val songStreamHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    private val bypassCacheForQualityChange = mutableSetOf<String>()

    private var currentMediaIdRetryCount = mutableMapOf<String, Int>()
    private val MAX_RETRY_PER_SONG = 3
    private val RETRY_DELAY_MS = 1000L
    private val STREAM_RETRY_INTERVAL_MS = 5000L

    private val recentlyFailedSongs = mutableSetOf<String>()
    private var failedSongsClearJob: Job? = null

    var castConnectionHandler: CastConnectionHandler? = null
        private set

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            val hasBluetooth = addedDevices?.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            } == true

            if (hasBluetooth) {
                if (dataStore.get(ResumeOnBluetoothConnectKey, false)) {
                    if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                        player.play()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        playerInitialized.value = false

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.music_player),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
            val pending = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.music_player))
                .setContentText("")
                .setSmallIcon(R.drawable.mochy_notification)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create foreground notification")
            reportException(e)
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
                .apply {
                    setSmallIcon(R.drawable.mochy_notification)
                },
        )
        audioOutputs.onRouteChanged = ::applyOutputRoute
        audioOutputs.preferUsb = dataStore.get(PreferUsbDacKey, false)
        audioOutputs.passthrough = dataStore.get(UsbPassthroughKey, false)
        audioOutputs.resampler = dataStore.get(HiFiResamplerKey, false)
        audioOutputs.start()
        audioPass.start()
        player = createExoPlayer()
        player.addListener(this@MusicService)
        observeOutputSettings()
        sleepTimer = SleepTimer(scope, player)
        player.addListener(sleepTimer)

        scope.launch {
            dataStore.data.map { it[MusicHapticsEnabledKey] ?: false }
                .distinctUntilChanged()
                .collect { enabled ->
                    hapticAudioProcessor.enabled = enabled
                }
        }

        scope.launch {
            dataStore.data.map { it[MusicHapticsSensitivityKey]?.toEnum(HapticSensitivity.MEDIUM) ?: HapticSensitivity.MEDIUM }
                .distinctUntilChanged()
                .collect { sensitivity ->
                    hapticAudioProcessor.sensitivity = sensitivity
                }
        }

        scope.launch {
            dataStore.data.map { it[com.mochyy.mochymusic.constants.HapticFocusKey]?.toEnum(com.mochyy.mochymusic.constants.HapticFocus.VIBE) ?: com.mochyy.mochymusic.constants.HapticFocus.VIBE }
                .distinctUntilChanged()
                .collect { focus ->
                    hapticAudioProcessor.focus = focus
                }
        }

        playerInitialized.value = true
        Timber.tag(TAG).d("Player successfully initialized")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupAudioFocusRequest()

        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, TransportFadingPlayer(player, transport, audioPass), mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)

        if (dataStore.get(RememberShuffleAndRepeatKey, true)) {
            player.shuffleModeEnabled = dataStore.get(ShuffleModeKey, false)
        }

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        audioQuality = dataStore.get(AudioQualityKey).toEnum(com.mochyy.mochymusic.constants.AudioQuality.AUTO)
        ipVersion = dataStore.get(IpVersionKey).toEnum(IpVersion.AUTO)
        // a saved volume of (next to) nothing is almost always left over from the old saving bug
        // (the live volume saved while muted or mid-fade, and copied back into this setting): it
        // started the app silent, so it starts at the default instead
        playerVolume = MutableStateFlow(
            dataStore.get(PlayerVolumeKey, 1f).coerceIn(0f, 1f).let { if (it < MinRestoredVolume) 1f else it },
        )

        initializeCast()

        // Audio FX's speed and pitch, on whichever player is playing. The equalizer and effects need
        // nothing here: the store hands them to the processors live, with no seek or reload.
        scope.launch {
            audioLab.playback.collect { settings ->
                val params = PlaybackParameters(settings.speed, settings.pitch)
                if (player.playbackParameters != params) player.playbackParameters = params
            }
        }

        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                isNetworkConnected.value = isConnected
                if (isConnected && waitingForNetworkConnection.value) {
                    triggerRetry()
                }

                // a status that didn't go through while offline goes now, not at the next song
                if (isConnected) discordPresence.nudge()
            }
        }

        var isFirstQualityEmit = true
        scope.launch {
            dataStore.data
                .map { it[AudioQualityKey]?.let { value ->
                    com.mochyy.mochymusic.constants.AudioQuality.entries.find { it.name == value }
                } ?: com.mochyy.mochymusic.constants.AudioQuality.AUTO }
                .distinctUntilChanged()
                .collect { newQuality ->
                    val oldQuality = audioQuality
                    audioQuality = newQuality

                    if (isFirstQualityEmit) {
                        isFirstQualityEmit = false
                        Timber.tag("MusicService").i("QUALITY INIT: $newQuality")
                        return@collect
                    }

                    Timber.tag("MusicService").i("QUALITY CHANGED: $oldQuality -> $newQuality")

                    val mediaId = player.currentMediaItem?.mediaId ?: return@collect
                    // a downloaded song plays its own file, and that download is the user's to keep:
                    // the new quality applies to what's streamed from here on
                    if (isFullyDownloaded(mediaId)) return@collect
                    val currentPosition = player.currentPosition
                    val wasPlaying = player.playWhenReady
                    val currentIndex = player.currentMediaItemIndex

                    Timber.tag("MusicService").i("RELOADING STREAM: $mediaId at position ${currentPosition}ms")

                    songUrlCache.remove(mediaId)
                    bypassCacheForQualityChange.add(mediaId)

                    // stop first: the loader holds the cached spans it's reading, and dropping them
                    // under it blocked the main thread
                    player.stop()
                    withContext(Dispatchers.IO) {
                        runCatching { playerCache.removeResource(mediaId) }
                            .onFailure { Timber.tag("MusicService").e(it, "Failed to clear cache for $mediaId") }
                    }
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()
                    player.playWhenReady = wasPlaying
                }
        }

        scope.launch {
            dataStore.data
                .map { it[IpVersionKey]?.toEnum(IpVersion.AUTO) ?: IpVersion.AUTO }
                .distinctUntilChanged()
                .collect { newIpVersion ->
                    val oldIpVersion = ipVersion
                    ipVersion = newIpVersion

                    if (isFirstQualityEmit) return@collect

                    Timber.tag("MusicService").i("IP VERSION CHANGED: $oldIpVersion -> $newIpVersion")

                    val mediaId = player.currentMediaItem?.mediaId ?: return@collect
                    val currentPosition = player.currentPosition
                    val currentIndex = player.currentMediaItemIndex
                    val wasPlaying = player.isPlaying

                    songUrlCache.remove(mediaId)

                    player.stop()
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()
                    if (wasPlaying) {
                        player.play()
                    }
                }
        }

        combine(playerVolume, isMuted) { volume, muted ->
            if (muted) 0f else volume
        }.collectLatest(scope) {
            // a running pause or skip fade sets the volume itself each frame, scaled by this
            if (!transport.isFading) player.volume = it
            if (crossmixDirector.isTransitioning) fadingPlayer?.volume = it
        }

        playerVolume.debounce(1000).collect(scope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        val labSound = combine(
            combine(audioLab.eq, audioLab.reverb, audioLab.loudness, ::Triple),
            audioLab.spatial,
            audioLab.playback,
            audioLab.volume,
        ) { (eq, reverb, loudness), spatial, playback, volume -> LabSound(eq, reverb, loudness, spatial, playback, volume) }
        combine(dataStore.data, labSound, playerVolume) { prefs, lab, volume -> passBlockersOf(prefs, lab, volume) }
            .distinctUntilChanged()
            .collect(scope) { _passBlockers.value = it }

        combine(dataStore.data, passBlockers) { prefs, blockers ->
            PassSettings(
                enabled = prefs[UsbPassthroughKey] ?: false,
                mode = prefs[AudioPassModeKey].toEnum(AudioPassMode.Default),
                blocked = blockers.isNotEmpty(),
                fixedVolume = prefs[AudioPassFixedVolumeKey] ?: false,
            )
        }.distinctUntilChanged().collect(scope) { audioPass.update(it.enabled, it.mode, it.blocked, it.fixedVolume) }

        currentSong.debounce(1000).collect(scope) { song ->
            updateNotification()
            updateWidgetUI(player.isPlaying)
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged(),
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(scope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null && database.lyrics(mediaMetadata.id)
                    .first() == null
            ) {
                val lyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
                // offline: nothing was found because nothing was asked; try again when online
                if (lyricsWithProvider.provider != com.mochyy.mochymusic.lyrics.LyricsHelper.NoConnectionProvider) {
                    database.query {
                        upsert(
                            LyricsEntity(
                                id = mediaMetadata.id,
                                lyrics = lyricsWithProvider.lyrics,
                                provider = lyricsWithProvider.provider,
                            ),
                        )
                    }
                }
            }
        }

        dataStore.data
            .map { prefs ->
                Triple(
                    prefs[TransportFadeEnabledKey] ?: true,
                    prefs[TransportFadeSecondsKey] ?: TransportFadeDefaultSeconds,
                    prefs[TransportEffectKey].toEnum(TransportEffect.OFF),
                )
            }
            .distinctUntilChanged()
            .collect(scope) { (enabled, seconds, effect) ->
                transport.enabled = enabled
                transport.lengthMs = seconds.coerceIn(TransportFadeMinSeconds, TransportFadeMaxSeconds) * 1_000f
                transport.effect = effect
            }

        dataStore.data
            .map { (it[SkipSilenceKey] ?: false) to (it[SkipSilenceInstantKey] ?: false) }
            .distinctUntilChanged()
            .collectLatest(scope) { (skipSilence, instantSkip) ->
                player.skipSilenceEnabled = skipSilence
                secondaryPlayer?.skipSilenceEnabled = skipSilence

                val enableInstant = skipSilence && instantSkip
                instantSilenceSkipEnabled.value = enableInstant

                playerSilenceProcessors.values.forEach { processor ->
                    processor.instantModeEnabled = enableInstant
                    if (!enableInstant) {
                        processor.resetTracking()
                    }
                }

                if (!enableInstant) {
                    silenceSkipJob?.cancel()
                }
            }

        dataStore.data
            .map { it[MixWithOtherAppsKey] ?: false }
            .distinctUntilChanged()
            .collect(scope) { mix ->
                mixWithOtherApps = mix
                if (mix) startMixingWithOtherApps() else stopMixingWithOtherApps()
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) ->
            audioFx.normalize = normalizeAudio
            setupLoudnessEnhancer()
        }

        // offload skips the app's own audio processing, so the equalizer and effects would go silent
        combine(
            dataStore.data.map { it[AudioOffload] ?: false },
            dataStore.data.map { it[CrossfadeEnabledKey] ?: false },
            audioLab.shapesSound,
        ) { offloadPref, crossfadeEnabled, shapesSound ->
             offloadPref && !crossfadeEnabled && !shapesSound
        }.distinctUntilChanged()
        .collectLatest(scope) { useOffload ->
             player.setOffloadEnabled(useOffload)
             secondaryPlayer?.setOffloadEnabled(useOffload)
        }

        // Discord needs the artist's picture (from the library) and the codec (from the decoder),
        // which both turn up a moment after the song starts
        currentSong.collect(scope) { publishPresence() }
        streamFacts.map { facts -> player.currentMediaItem?.mediaId?.let { facts[it] } }
            .distinctUntilChanged()
            .collect(scope) { publishPresence() }

        dataStore.data
            .map { prefs ->
                Triple(
                    prefs[ScrobbleDelayPercentKey] ?: ScrobbleTimer.DEFAULT_PLAYED_FRACTION,
                    prefs[ScrobbleMinSongDurationKey] ?: ScrobbleTimer.DEFAULT_MIN_SONG_SECONDS,
                    prefs[ScrobbleDelaySecondsKey] ?: ScrobbleTimer.DEFAULT_MAX_WAIT_SECONDS,
                )
            }
            .distinctUntilChanged()
            .collect(scope) { (playedFraction, minSongSeconds, maxWaitSeconds) ->
                scrobbleTimer.playedFraction = playedFraction
                scrobbleTimer.minSongSeconds = minSongSeconds
                scrobbleTimer.maxWaitSeconds = maxWaitSeconds
            }

        dataStore.data
            .map { prefs ->
                // offline mode keeps every play on the phone too
                val enabled = (prefs[YtReportPlaysKey] ?: true) && prefs[OfflineModeKey] != true
                enabled to ((prefs[HistoryDuration] ?: 30f) * 1000).toLong()
            }
            .distinctUntilChanged()
            .collect(scope) { (enabled, countAfterMs) ->
                playReporter.isEnabled = enabled
                playReporter.countAfterMs = countAfterMs
            }

        dataStore.data.map { prefs ->
            val legacyEnabled = prefs[CrossfadeEnabledKey] ?: false
            val mode = (
                prefs[CrossmixModeKey]
                    ?.let { value -> runCatching { CrossmixMode.valueOf(value) }.getOrNull() }
                    ?: if (legacyEnabled) CrossmixMode.MANUAL_CROSSFADE else CrossmixMode.AUTO_CROSSFADE
                ).playable()
            val enabled = mode != CrossmixMode.OFF
            val duration = when (mode) {
                CrossmixMode.AUTO_CROSSFADE -> 5f
                CrossmixMode.MANUAL_CROSSFADE -> (prefs[CrossfadeDurationKey] ?: 5f).coerceIn(1f, 30f)
                CrossmixMode.CROSSMIX -> (prefs[CrossfadeDurationKey] ?: 12f).coerceIn(8f, 20f)
                CrossmixMode.OFF -> prefs[CrossfadeDurationKey] ?: 5f
            }
            CrossfadeConfig(mode, enabled, duration, prefs[CrossfadeGaplessKey] ?: true)
        }
            .distinctUntilChanged()
            .collect(scope) { config ->
                crossmixMode = config.mode
                crossfadeEnabled = config.enabled
                crossfadeDuration = config.durationSeconds * 1000f
                crossfadeGapless = config.disableForGaplessAlbums
            }

        if (dataStore.get(PersistentQueueKey, true)) {
            val queueFile = filesDir.resolve(PERSISTENT_QUEUE_FILE)
            if (queueFile.exists()) {
                runCatching {
                    queueFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistQueue
                        }
                    }
                }.onSuccess { queue ->
                    runCatching {

                        val restoredQueue = queue.toQueue()

                        scope.launch {
                            playerInitialized.first { it }
                            if (isActive) {
                                playQueue(
                                    queue = restoredQueue,
                                    playWhenReady = false,
                                )
                            }
                        }
                    }.onFailure { error ->
                        Timber.tag(TAG).w(error, "Failed to restore persisted queue, clearing data")
                        clearPersistedQueueFiles()
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read persisted queue, clearing data")
                    clearPersistedQueueFiles()
                }
            }

            val automixFile = filesDir.resolve(PERSISTENT_AUTOMIX_FILE)
            if (automixFile.exists()) {
                runCatching {
                    automixFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistQueue
                        }
                    }
                }.onSuccess { queue ->
                    runCatching {
                        automixItems.value = queue.items.map { it.toMediaItem() }
                    }.onFailure { error ->
                        Timber.tag(TAG).w(error, "Failed to restore automix queue, clearing data")
                        clearPersistedQueueFiles()
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read automix queue, clearing data")
                    clearPersistedQueueFiles()
                }
            }

            val playerStateFile = filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE)
            if (playerStateFile.exists()) {
                runCatching {
                    playerStateFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistPlayerState
                        }
                    }
                }.onSuccess { playerState ->

                    scope.launch {
                        delay(1000)

                        // older saves held the player's live volume (0 if it was muted or fading
                        // when saved): only a real one is taken, and the slider's own saved value
                        // (PlayerVolumeKey, read at start) stands otherwise
                        if (playerState.volume >= MinRestoredVolume) playerVolume.value = playerState.volume

                        if (playerState.currentMediaItemIndex < player.mediaItemCount) {
                            player.seekTo(playerState.currentMediaItemIndex, playerState.currentPosition)
                        }
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read player state, clearing data")
                    clearPersistedQueueFiles()
                }
            }
        }

        scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (dataStore.get(PersistentQueueKey, true)) {
                    saveQueueToDisk()
                }
            }
        }

        scope.launch {
            while (isActive) {
                delay(10.seconds)
                if (dataStore.get(PersistentQueueKey, true) && player.isPlaying) {
                    saveQueueToDisk()
                }
            }
        }
    }

    private fun createExoPlayer(publish: Boolean = true): ExoPlayer {
        val eqProcessor = CustomEqualizerAudioProcessor()
        equalizerService.addAudioProcessor(eqProcessor)
        // this player's song's ReplayGain, read from its file's tags when its tracks change
        val replayGain = com.mochyy.mochymusic.eq.audio.ReplayGainTags()
        val fxProcessors = arrayOf<androidx.media3.common.audio.AudioProcessor>(
            SpatialAudioProcessor(audioFx),
            ReverbAudioProcessor(audioFx),
            LoudnessAudioProcessor(audioFx),
        )

        val silenceProcessor = SilenceDetectorAudioProcessor { handleLongSilenceDetected() }
        val crossmixProcessor = CrossmixTransitionAudioProcessor()
        val spectrum = com.mochyy.mochymusic.playback.audio.AudioSpectrum()
        val inspector = StreamInspector()

        val hapticVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        hapticAudioProcessor = HapticAudioProcessor { amplitude, isBassKick ->
            scope.launch(Dispatchers.Default) {
                delay(150)
                try {
                    if (hapticVibrator.hasVibrator()) {
                        val scale = (amplitude / 255f).coerceIn(0.01f, 1f)

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (isBassKick && hapticVibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {

                                val effect = VibrationEffect.startComposition()
                                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
                                    .compose()
                                hapticVibrator.vibrate(effect)
                            } else if (hapticVibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {

                                val effect = VibrationEffect.startComposition()
                                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale)
                                    .compose()
                                hapticVibrator.vibrate(effect)
                            } else if (hapticVibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
                                val effect = VibrationEffect.startComposition()
                                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale)
                                    .compose()
                                hapticVibrator.vibrate(effect)
                            } else if (hapticVibrator.hasAmplitudeControl()) {
                                hapticVibrator.vibrate(VibrationEffect.createOneShot(20, amplitude))
                            }
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hapticVibrator.hasAmplitudeControl()) {
                            hapticVibrator.vibrate(VibrationEffect.createOneShot(20, amplitude))
                        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && isBassKick) {
                            // Android 7 has no VibrationEffect, so it gets a plain buzz on the kick only.
                            // Newer phones without amplitude control stay silent, as they always have.
                            @Suppress("DEPRECATION")
                            hapticVibrator.vibrate(20)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing haptics")
                }
            }
        }

        runBlocking {
            val skipSilence = dataStore.get(SkipSilenceKey, false)
            val instantSkip = dataStore.get(SkipSilenceInstantKey, false)
            silenceProcessor.instantModeEnabled = skipSilence && instantSkip
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setRenderersFactory(createRenderersFactory(eqProcessor, fxProcessors, silenceProcessor, hapticAudioProcessor, crossmixProcessor, replayGain, spectrum, inspector))
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .setDeviceVolumeControlEnabled(true)
            // wake the playback loop when the audio buffer needs more, not every 10 ms: it was a third of a core in the background
            .experimentalSetDynamicSchedulingEnabled(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                replayGain.update(tracks)
                val mediaId = player.currentMediaItem?.mediaId
                val format = StreamFacts.selectedAudio(tracks)
                if (mediaId != null && format != null) recordDecodedFormat(mediaId, format)
                // audio that no decoder on this phone takes isn't an error to the player: the song
                // "plays" on in silence. Only the main player's song: a Crossmix's next one waits
                if (mediaId != null && player === this@MusicService.player &&
                    tracks.containsType(C.TRACK_TYPE_AUDIO) &&
                    !tracks.isTypeSupported(C.TRACK_TYPE_AUDIO, true)
                ) {
                    onUndecodableAudio(mediaId, tracks)
                } else if (mediaId != null && format != null && tracks.isTypeSupported(C.TRACK_TYPE_AUDIO, true)) {
                    announceDecoderNotice(mediaId)
                }
            }
        })
        playerReplayGain[player] = replayGain
        playerSilenceProcessors[player] = silenceProcessor
        playerCrossmixProcessors[player] = crossmixProcessor
        playerSpectra[player] = spectrum
        playerEqProcessors[player] = eqProcessor
        playerInspectors[player] = inspector
        player.addAnalyticsListener(inspector)
        player.setPreferredAudioDevice(audioOutputs.preferredDevice())

        player.apply {
                runBlocking {
                    val offload = dataStore.get(AudioOffload, false)
                    val crossfade = dataStore.get(CrossfadeEnabledKey, false)
                    setOffloadEnabled(if (crossfade) false else offload)
                    skipSilenceEnabled = dataStore.get(SkipSilenceKey, false)
                }
                addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))

            }
        if (publish) _playerFlow.value = player
        return player
    }

    // the usb dac switches, and when 32-bit float may pass: media3's float path skips every audio processor
    private fun observeOutputSettings() {
        dataStore.data
            .map { Triple(it[PreferUsbDacKey] ?: false, it[UsbPassthroughKey] ?: false, it[HiFiResamplerKey] ?: false) }
            .distinctUntilChanged()
            .collect(scope) { (preferUsb, passthrough, resampler) ->
                audioOutputs.preferUsb = preferUsb
                audioOutputs.passthrough = passthrough
                audioOutputs.resampler = resampler
            }
        val wantsFloat = dataStore.data.map { prefs ->
            (prefs[HiResOutputKey] ?: false) &&
                !(prefs[SkipSilenceKey] ?: false) &&
                !(prefs[MusicHapticsEnabledKey] ?: false) &&
                prefs[TransportEffectKey].toEnum(TransportEffect.OFF) == TransportEffect.OFF &&
                prefs[CrossmixModeKey]?.let { runCatching { CrossmixMode.valueOf(it) }.getOrNull() }?.playable() != CrossmixMode.CROSSMIX
        }
        combine(wantsFloat, audioLab.shapesSound, audioLab.playback) { wants, shapesSound, playback ->
            wants && !shapesSound && playback.speed == 1f && playback.pitch == 1f
        }.distinctUntilChanged().collect(scope) { audioOutputs.floatAllowed = it }
    }

    private fun setupAudioFocusRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .setAcceptsDelayedFocusGain(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            legacyFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                handleAudioFocusChange(focusChange)
            }
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        // a callback still on its way after mixing let go of focus, it would pause us for the other app
        if (mixWithOtherApps) return
        when (focusChange) {

            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                hasAudioFocus = true

                if (wasPlayingBeforeAudioFocusLoss && !player.isPlaying && !reentrantFocusGain) {
                    reentrantFocusGain = true
                    scope.launch {
                        delay(300)
                        if (hasAudioFocus && wasPlayingBeforeAudioFocusLoss && !player.isPlaying) {

                            if (castConnectionHandler?.isCasting?.value != true) {
                                player.play()
                            }
                            wasPlayingBeforeAudioFocusLoss = false
                        }
                        reentrantFocusGain = false
                    }
                }

                player.volume = if (isMuted.value) 0f else playerVolume.value
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                }
                abandonAudioFocus()
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                }
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.volume = if (isMuted.value) 0f else (playerVolume.value * 0.2f)
                }
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                hasAudioFocus = true
                player.volume = if (isMuted.value) 0f else playerVolume.value
                lastAudioFocusState = focusChange
            }
        }
    }

    /** the volume the playing song should be at now: the user's, nothing while muted, a fifth while ducked */
    private fun targetVolume(): Float = when {
        isMuted.value -> 0f
        !hasAudioFocus && lastAudioFocusState == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> playerVolume.value * 0.2f
        else -> playerVolume.value
    }

    private fun requestAudioFocus(): Boolean {
        // mixing never asks, holding focus is exactly what lets android pause or fade us for another app
        if (mixWithOtherApps) return true
        if (hasAudioFocus) return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest ?: return false
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                legacyFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        // granted straight back after a duck: no GAIN callback follows, and the player stayed
        // at a fifth of its volume for good
        if (hasAudioFocus && lastAudioFocusState == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            player.volume = if (isMuted.value) 0f else playerVolume.value
            lastAudioFocusState = AudioManager.AUDIOFOCUS_GAIN
        }
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        // let go even after a short loss, or the request stays queued and android hands focus back later
        if (!::audioManager.isInitialized) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            legacyFocusListener?.let { audioManager.abandonAudioFocus(it) }
        }
        hasAudioFocus = false
    }

    // holding focus while mixing would get us faded out on android 12+ the moment another app starts
    private fun startMixingWithOtherApps() {
        abandonAudioFocus()
        wasPlayingBeforeAudioFocusLoss = false
        if (lastAudioFocusState == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            player.volume = if (isMuted.value) 0f else playerVolume.value
        }
        lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
        callWatcher.start()
    }

    private fun stopMixingWithOtherApps() {
        callWatcher.stop()
        wasPlayingBeforeCall = false
        if (player.playWhenReady) requestAudioFocus()
    }

    // while mixing nothing else pauses us for a call, so do it here and pick the song back up after
    private fun onCallChanged(inCall: Boolean) {
        if (inCall) {
            wasPlayingBeforeCall = player.playWhenReady
            if (wasPlayingBeforeCall) player.pause()
            return
        }
        if (!wasPlayingBeforeCall) return
        wasPlayingBeforeCall = false
        if (castConnectionHandler?.isCasting?.value != true) player.play()
    }

    private fun clearPersistedQueueFiles() {
        runCatching { filesDir.resolve(PERSISTENT_QUEUE_FILE).delete() }
        runCatching { filesDir.resolve(PERSISTENT_AUTOMIX_FILE).delete() }
        runCatching { filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).delete() }
    }

    fun hasAudioFocusForPlayback(): Boolean {
        return hasAudioFocus
    }

    private fun waitOnNetworkError() {
        if (waitingForNetworkConnection.value) return

        if (retryCount >= MAX_RETRY_COUNT) {
            Timber.tag(TAG).w("Max retry count ($MAX_RETRY_COUNT) reached, stopping playback")
            stopOnError()
            retryCount = 0
            return
        }

        waitingForNetworkConnection.value = true
        com.mochyy.mochymusic.ui.component.TopToaster.error("Stream failed: no internet connection")

        retryJob?.cancel()
        retryJob = scope.launch {

            val delayMs = minOf(3000L * (1 shl retryCount), 30000L)
            Timber.tag(TAG).d("Waiting ${delayMs}ms before retry attempt ${retryCount + 1}/$MAX_RETRY_COUNT")
            delay(delayMs)

            if (isNetworkConnected.value && waitingForNetworkConnection.value) {
                retryCount++
                triggerRetry()
            }
        }
    }

    private fun triggerRetry() {
        waitingForNetworkConnection.value = false
        retryJob?.cancel()

        if (player.currentMediaItem != null) {

            if (retryCount > 3) {
                Timber.tag(TAG).d("Retry count > 3, attempting to refresh stream URL")
                val currentPosition = player.currentPosition
                player.seekTo(player.currentMediaItemIndex, currentPosition)
            }
            player.prepare()

        }
    }

    /** Moves past a stream that never came back, unless it has failed too many times in a row. */
    private fun skipAfterFailure() {
        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()

            if (castConnectionHandler?.isCasting?.value != true) {
                player.play()
            }
            return
        }

        player.pause()
        consecutivePlaybackErr = 0
    }

    /**
     * The song's audio is in a format no decoder on this phone takes (a FLAC on a phone without a
     * FLAC decoder, a rate or channel count beyond the phone's decoders). The player doesn't count
     * that as an error - the song plays on, in silence - so it's made one: a module's stream gives
     * way to the next provider (YouTube at the latest), and anything else fails like a song that
     * can't be played, saying why, and moves on.
     */
    private fun onUndecodableAudio(mediaId: String, tracks: androidx.media3.common.Tracks) {
        val audio = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }?.takeIf { it.length > 0 }?.getTrackFormat(0)
        val what = listOfNotNull(
            audio?.sampleMimeType?.substringAfter('/'),
            audio?.sampleRate?.takeIf { it > 0 }?.let { "$it Hz" },
            audio?.channelCount?.takeIf { it > 0 }?.let { "$it ch" },
        ).joinToString(" ").ifBlank { "unknown format" }
        Timber.tag(TAG).w("No decoder on this phone for $mediaId's audio ($what)")
        // not from inside the player's listener: the fallback stops and re-prepares the player
        scope.launch {
            if (player.currentMediaItem?.mediaId != mediaId) return@launch
            noteMissingDecoder(mediaId, StreamFacts.codecOf(audio?.sampleMimeType, audio?.codecs), audio?.sampleRate)
            if (forceFallBack(mediaId, announce = !decoderNotices.containsKey(mediaId))) return@launch
            lastPlaybackError = PlaybackException(
                "This phone can't decode this song's audio ($what)",
                null,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            )
            failStream()
        }
    }

    /** a player error that's this phone's decoders turning the song's format down */
    private fun isDecoderError(e: PlaybackException) = e.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
        e.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
        e.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
        e.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED

    /**
     * Remembers that [mediaId]'s stream from the provider it was handed to is [codec] this phone
     * can't decode, so the next stream's toast says why it's playing instead.
     */
    private fun noteMissingDecoder(mediaId: String, codec: String?, sampleRate: Int?, from: String? = resolvedSourceIds[mediaId]) {
        codec ?: return
        val rate = sampleRate?.takeIf { it > 0 }
        val text = when {
            !com.mochyy.mochymusic.modules.DeviceDecoders.canPlay(codec) -> "No $codec decoder available on this phone"
            rate != null && !com.mochyy.mochymusic.modules.DeviceDecoders.canPlay(codec, rate) ->
                "No $codec decoder for ${if (rate % 1000 == 0) "${rate / 1000}" else "%.1f".format(rate / 1000f)} kHz on this phone"
            else -> "This phone can't decode this $codec"
        }
        decoderNotices[mediaId] = DecoderNotice(text, from)
    }

    /**
     * "No FLAC decoder available on this phone · playing in OPUS on YouTube Music", once
     * [mediaId] plays from somewhere other than the stream this phone couldn't decode.
     */
    private fun announceDecoderNotice(mediaId: String) {
        val notice = decoderNotices.remove(mediaId) ?: return
        val source = resolvedSourceIds[mediaId] ?: return
        if (source == notice.from) return
        val codec = streamFacts.value[mediaId]?.codec?.let { " in $it" }.orEmpty()
        showBanner("${notice.text} · playing$codec on ${sourceModules.nameOf(source)}")
    }

    /** A failed stream the player has given up on: says so, and skips after the timeout unless that's turned off. */
    private fun failStream() {
        player.pause()
        announceStreamFailure()
        clearStreamFailure()
        if (dataStore.get(DontSkipOnFailureKey, false)) {
            // staying on it means trying it again every few seconds until it plays: loading it
            // again ends this job, and a failure starts the next wait
            failureJob = scope.launch {
                delay(STREAM_RETRY_INTERVAL_MS)
                Timber.tag(TAG).d("Retrying the failed stream")
                player.prepare()
                player.play()
            }
            return
        }
        if (player.nextMediaItemIndex == C.INDEX_UNSET) return

        val timeoutMs = dataStore.get(StreamFailureTimeoutKey, 6).coerceIn(5, 20) * 1000L
        streamFailure.value = StreamFailure(android.os.SystemClock.elapsedRealtime(), timeoutMs)
        failureJob = scope.launch {
            delay(timeoutMs)
            streamFailure.value = null
            Timber.tag(TAG).d("Stream failure timed out, skipping to next track")
            skipAfterFailure()
        }
    }

    private fun clearStreamFailure() {
        failureJob?.cancel()
        failureJob = null
        streamFailure.value = null
    }

    private fun stopOnError() = failStream()

    private var lastPlaybackError: PlaybackException? = null

    /** The top toast for a stream that couldn't be played, with a plain reason. */
    private fun announceStreamFailure() {
        val e = lastPlaybackError
        // no decoder for it here, and nowhere else had it: that's the whole reason
        val notice = player.currentMediaItem?.mediaId?.let { decoderNotices.remove(it) }
        if (notice != null && e != null && isDecoderError(e)) {
            com.mochyy.mochymusic.ui.component.TopToaster.error(notice.text)
            return
        }
        val reason = when {
            !isNetworkConnected.value || (e != null && isNetworkRelatedError(e)) -> "no internet connection"
            e != null && getHttpResponseCode(e) != null -> "server error ${getHttpResponseCode(e)}"
            e != null -> plainReason(e)
            else -> "unknown error"
        }
        com.mochyy.mochymusic.ui.component.TopToaster.error("Stream failed: $reason")
    }

    /** A readable reason from the root cause, since IO_UNSPECIFIED alone says nothing. */
    private fun plainReason(e: PlaybackException): String {
        val causes = generateSequence<Throwable>(e) { it.cause }.toList()
        val noProvider = causes.firstOrNull { it is com.mochyy.mochymusic.playback.custom.NoProviderException }
        return when {
            noProvider != null -> noProvider.message.orEmpty()
            causes.any { it is java.net.SocketTimeoutException } -> "connection timed out"
            causes.any { it is java.net.SocketException || it is java.io.EOFException } -> "connection interrupted"
            causes.any { it is javax.net.ssl.SSLException } -> "secure connection failed"
            causes.any { it is java.io.FileNotFoundException } -> "stream not found"
            causes.any { it is androidx.media3.datasource.cache.Cache.CacheException } -> "cache error"
            e.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "stream interrupted"
            isContainerParseError(e) -> "the audio file came back broken"
            else -> e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')
        }
    }

    private fun updateNotification() {
        mediaSession.setCustomLayout(
            listOf(
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            if (currentSong.value?.song?.liked ==
                                true
                            ) {
                                R.string.action_remove_like
                            } else {
                                R.string.action_like
                            },
                        ),
                    )
                    .setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.ic_heart else R.drawable.ic_heart_outline)
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> throw IllegalStateException()
                            },
                        ),
                    ).setIconResId(
                        when (player.repeatMode) {
                            // The player artwork is 95 x 77 dp; notifications need a 24 dp icon.
                            REPEAT_MODE_OFF -> R.drawable.notification_repeat
                            REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> throw IllegalStateException()
                        },
                    ).setSessionCommand(CommandToggleRepeatMode)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(CommandToggleShuffle)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.start_radio))
                    .setIconResId(R.drawable.radio)
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(currentSong.value != null)
                    .build(),
            ),
        )
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null
    ) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else {
                var updatedSong = song.song
                if (song.song.duration == -1) {
                    updatedSong = updatedSong.copy(duration = duration)
                }

                if (song.song.isVideo != mediaMetadata.isVideoSong) {
                    updatedSong = updatedSong.copy(isVideo = mediaMetadata.isVideoSong)
                }
                if (updatedSong != song.song) {
                    update(updatedSong)
                }
            }
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main) + Job()

        if (!playerInitialized.value) {
            Timber.tag(TAG).w("playQueue called before player initialization, queuing request")
            scope.launch {
                playerInitialized.first { it }
                playQueue(queue, playWhenReady)
            }
            return
        }

        currentQueue = queue
        queueTitle = null
        resetQueueRunway()
        val generation = queueGeneration
        initialQueueLoading = true
        val persistShuffleAcrossQueues = dataStore.get(PersistentShuffleAcrossQueuesKey, false)
        val previousShuffleEnabled = player.shuffleModeEnabled
        if (!persistShuffleAcrossQueues) {
            player.shuffleModeEnabled = false
        }

        originalQueueSize = 0
        if (queue.preloadItem != null) {
            player.setMediaItem(queue.preloadItem!!.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        scope.launch(SilentHandler) {
            try {
                loadInitialQueue(queue, generation, playWhenReady)
            } finally {
                if (generation == queueGeneration) {
                    initialQueueLoading = false
                    // Even if the initial load failed or came back short, make sure there's something next.
                    ensureQueueRunway()
                }
            }
        }
    }

    private suspend fun loadInitialQueue(
        queue: Queue,
        generation: Int,
        playWhenReady: Boolean,
    ) {
        val initialStatus =
            withContext(Dispatchers.IO) {
                queue.getInitialStatus()
                    .filterExplicit(dataStore.get(HideExplicitKey, false))
                    .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
            }
        if (generation != queueGeneration) return

        val preloadId = queue.preloadItem?.id
        // Only drop the result if the user has moved on from the preloaded song. Don't bail just because
        // the player is idle: the first stream for a fresh install often errors once and retries, and
        // bailing here used to leave the queue with only that one song.
        if (preloadId != null && player.currentMediaItem?.mediaId != preloadId) return

        if (initialStatus.title != null) {
            queueTitle = initialStatus.title
        }
        if (initialStatus.items.isEmpty()) return

        originalQueueSize = initialStatus.items.size
        if (preloadId != null) {
            val items = initialStatus.items
            val preloadIndex = items.indexOfFirst { it.mediaId == preloadId }
            if (preloadIndex >= 0) {
                player.addMediaItems(0, items.subList(0, preloadIndex))
                player.addMediaItems(items.subList(preloadIndex + 1, items.size))
            } else {
                // The result doesn't contain the playing song (e.g. it fell back to a radio mix), so
                // queue all of it after the current song instead of dropping a track.
                player.addMediaItems(items)
            }
        } else {
            player.setMediaItems(
                initialStatus.items,
                if (initialStatus.mediaItemIndex >
                    0
                ) {
                    initialStatus.mediaItemIndex
                } else {
                    0
                },
                initialStatus.position,
            )
            player.prepare()
            player.playWhenReady = playWhenReady
        }

        if (player.shuffleModeEnabled) {
            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
    }

    private fun resetQueueRunway() {
        queueGeneration++
        runwayJob?.cancel()
        runwayJob = null
        resetInfiniteSource()
    }

    /** Forgets the infinite queue's radio and seeds, so the next runway seeds afresh from the queue's tail. */
    private fun resetInfiniteSource() {
        runwayRetryJob?.cancel()
        runwayRetryJob = null
        runwayRetries = 0
        infiniteSource = null
        usedInfiniteSeeds.clear()
        failedInfiniteSeeds.clear()
        autoplayPreviewRetryAt = 0L
    }

    private fun isInfiniteQueueEnabled(): Boolean {
        // its songs come from YouTube radios
        if (com.mochyy.mochymusic.utils.OfflineMode.isOn) return false
        val stored = dataStore.get(InfiniteQueueKey, true)
        val pending = pendingInfiniteQueue ?: return stored
        if (pending == stored) pendingInfiniteQueue = null
        return pending
    }

    /**
     * Tries the runway again later after it came back with nothing while the queue is running dry,
     * e.g. the first request on a fresh install failed. Each retry waits twice as long as the last.
     */
    private fun scheduleRunwayRetry(generation: Int) {
        if (runwayRetries >= MAX_RUNWAY_RETRIES || com.mochyy.mochymusic.utils.OfflineMode.isOn) return
        val canGrow = (dataStore.get(AutoLoadMoreKey, true) && currentQueue.hasNextPage()) ||
            (isInfiniteQueueEnabled() && player.repeatMode == REPEAT_MODE_OFF)
        if (!canGrow) return
        val delayMs = RUNWAY_RETRY_BASE_MS shl runwayRetries
        runwayRetries++
        runwayRetryJob?.cancel()
        runwayRetryJob = scope.launch(SilentHandler) {
            delay(delayMs)
            if (generation != queueGeneration) return@launch
            usedInfiniteSeeds -= failedInfiniteSeeds
            failedInfiniteSeeds.clear()
            ensureQueueRunway()
        }
    }

    /**
     * Where the upcoming Autoplay songs start, or the end of the queue if there are none. Autoplay
     * songs are only ever appended, so they form the tail; anything the user queues goes in front.
     */
    private fun upcomingAutoplayStart(): Int {
        val count = player.mediaItemCount
        for (i in player.currentMediaItemIndex + 1 until count) {
            if (player.getMediaItemAt(i).isAutoplay) return i
        }
        return count
    }

    /**
     * Whether to line up Autoplay songs now, before the queue runs low, so the queue page can show
     * what plays after it. Only once the queue itself is finite: while it still has pages to load,
     * those come first and are what keeps it going.
     */
    private fun wantsAutoplayPreview(): Boolean {
        if (!isInfiniteQueueEnabled() || player.repeatMode != REPEAT_MODE_OFF) return false
        if (android.os.SystemClock.elapsedRealtime() < autoplayPreviewRetryAt) return false
        if (dataStore.get(AutoLoadMoreKey, true) && currentQueue.hasNextPage()) return false
        return upcomingAutoplayStart() == player.mediaItemCount
    }

    /** Drops the Autoplay songs still to come, e.g. when infinite queue is switched off. */
    private fun removeUpcomingAutoplay() {
        runwayJob?.cancel()
        runwayRetryJob?.cancel()
        val current = player.currentMediaItemIndex
        var end = player.mediaItemCount - 1
        // Back to front in runs, one timeline change per run rather than per song.
        while (end > current) {
            if (!player.getMediaItemAt(end).isAutoplay) {
                end--
                continue
            }
            var start = end
            while (start - 1 > current && player.getMediaItemAt(start - 1).isAutoplay) start--
            player.removeMediaItems(start, end + 1)
            end = start - 1
        }
    }

    /** How many items the player will play after the current one (respecting shuffle), capped at [limit]. */
    private fun upcomingItemCount(limit: Int): Int {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return 0
        var index = player.currentMediaItemIndex
        var count = 0
        while (count < limit) {
            index = timeline.getNextWindowIndex(index, REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET) break
            count++
        }
        return count
    }

    /**
     * Keeps a few songs queued ahead of the current one: first from the queue's own next pages, then, when
     * the queue has run dry and infinite queue is on, from a radio seeded by the songs at the end of the queue.
     * If playback already ended because nothing was left, it resumes on the newly added songs.
     */
    private fun ensureQueueRunway() {
        if (!playerInitialized.value) return
        if (initialQueueLoading || runwayJob?.isActive == true) return
        if (player.mediaItemCount == 0) return
        if (dataStore.get(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL) return
        val lowOnSongs = upcomingItemCount(QUEUE_RUNWAY_SIZE + 1) <= QUEUE_RUNWAY_SIZE
        val previewOnly = !lowOnSongs && wantsAutoplayPreview()
        if (!lowOnSongs && !previewOnly) return

        val generation = queueGeneration
        runwayJob = scope.launch(SilentHandler) {
            repeat(MAX_RUNWAY_ATTEMPTS) {
                val (candidates, fromInfinite) = loadMoreQueueItems() ?: run {
                    if (previewOnly) {
                        autoplayPreviewRetryAt = android.os.SystemClock.elapsedRealtime() + AUTOPLAY_PREVIEW_RETRY_MS
                    } else if (generation == queueGeneration) {
                        scheduleRunwayRetry(generation)
                    }
                    return@launch
                }
                if (generation != queueGeneration || player.mediaItemCount == 0) return@launch

                val newItems = if (fromInfinite) {
                    // Radio mixes repeat songs that are already queued (including their own seed).
                    val queued = HashSet<String>()
                    for (i in 0 until player.mediaItemCount) queued += player.getMediaItemAt(i).mediaId
                    candidates.filter { queued.add(it.mediaId) }.map { it.withAutoplay(true) }
                } else {
                    candidates
                }
                if (newItems.isEmpty()) return@repeat

                val endedWithNothingNext = player.playbackState == Player.STATE_ENDED
                // Autoplay goes on the end; the queue's own pages go in front of it, so the songs
                // the user picked still play before the similar ones.
                val insertAt = if (fromInfinite) player.mediaItemCount else upcomingAutoplayStart()
                player.addMediaItems(insertAt, newItems)
                runwayRetries = 0
                if (player.shuffleModeEnabled) {
                    // A new page of the queue is shuffled in itself; Autoplay keeps the radio's order.
                    placeAddedInShuffleOrder(insertAt until insertAt + newItems.size, shuffleThem = !fromInfinite)
                }
                if (endedWithNothingNext && player.playWhenReady) {
                    val nextIndex = player.nextMediaItemIndex
                    if (nextIndex != C.INDEX_UNSET) {
                        player.seekTo(nextIndex, 0)
                        player.prepare()
                        player.play()
                    }
                }
                // That may have been the queue's last page, which is when Autoplay lines up.
                if (!fromInfinite) scope.launch(SilentHandler) { ensureQueueRunway() }
                return@launch
            }
            if (previewOnly) {
                autoplayPreviewRetryAt = android.os.SystemClock.elapsedRealtime() + AUTOPLAY_PREVIEW_RETRY_MS
            } else if (generation == queueGeneration) {
                scheduleRunwayRetry(generation)
            }
        }
    }

    /** Returns the next batch of songs and whether they came from the infinite queue, or null if there's nothing to add. */
    private suspend fun loadMoreQueueItems(): Pair<List<MediaItem>, Boolean>? {
        // a queue's next page and the infinite queue are both online
        if (com.mochyy.mochymusic.utils.OfflineMode.isOn) return null
        val hideExplicit = dataStore.get(HideExplicitKey, false)
        val hideVideos = dataStore.get(HideVideoSongsKey, false)

        val queue = currentQueue
        if (dataStore.get(AutoLoadMoreKey, true) && queue.hasNextPage()) {
            val page = runCatching { withContext(Dispatchers.IO) { queue.nextPage() } }
                .onFailure { Timber.tag(TAG).w(it, "Failed to load next queue page") }
                .getOrDefault(emptyList())
                .filterExplicit(hideExplicit)
                .filterVideoSongs(hideVideos)
            if (page.isNotEmpty()) return page to false
        }

        if (!isInfiniteQueueEnabled() || player.repeatMode != REPEAT_MODE_OFF) return null

        val infiniteItems = loadInfiniteQueueItems()
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideos)
        return infiniteItems.takeIf { it.isNotEmpty() }?.let { it to true }
    }

    private suspend fun loadInfiniteQueueItems(): List<MediaItem> {
        // Only songs that aren't queued yet count: a radio that repeats the queue (or just its own
        // seed, which is all a signed-out radio sometimes returns) has to fall through to the next source.
        val queued = HashSet<String>()
        for (i in 0 until player.mediaItemCount) queued += player.getMediaItemAt(i).mediaId
        // the songs you said not to recommend again never get lined up
        val hidden = NotRecommended.songs
        fun List<MediaItem>.fresh() = filter { it.mediaId !in queued && it.mediaId !in hidden }

        infiniteSource?.takeIf { it.hasNextPage() }?.let { source ->
            runCatching { withContext(Dispatchers.IO) { source.nextPage() } }
                .onFailure { Timber.tag(TAG).w(it, "Failed to load next infinite queue page") }
                .getOrNull()
                ?.fresh()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }

        // Seed a fresh radio from the most recent online song we haven't seeded from yet, so the
        // queue keeps drifting forward instead of looping on the same mix.
        val seedItem = (player.mediaItemCount - 1 downTo 0)
            .asSequence()
            .map { player.getMediaItemAt(it) }
            .firstOrNull { !it.mediaId.startsWith("LOCAL_") && it.mediaId !in usedInfiniteSeeds && it.mediaId !in hidden }
            ?: return emptyList()
        val seed = seedItem.mediaId
        usedInfiniteSeeds += seed

        val radio = YouTubeQueue(WatchEndpoint(videoId = seed, playlistId = "RDAMVM$seed"))
        val radioItems = runCatching { withContext(Dispatchers.IO) { radio.getInitialStatus().items } }
            .onFailure { Timber.tag(TAG).w(it, "Failed to start infinite queue radio for $seed") }
            .getOrDefault(emptyList())
            .fresh()
        infiniteSource = radio
        if (radioItems.isNotEmpty()) return radioItems

        val relatedItems = runCatching {
            withContext(Dispatchers.IO) {
                val relatedEndpoint = YouTube.next(WatchEndpoint(videoId = seed)).getOrNull()?.relatedEndpoint
                    ?: return@withContext emptyList()
                YouTube.related(relatedEndpoint).getOrNull()?.songs.orEmpty().map { it.toMediaItem() }
            }
        }.getOrDefault(emptyList()).fresh()
        if (relatedItems.isNotEmpty()) return relatedItems

        // Last resort, for when YouTube gives a signed-out session neither a radio nor related
        // songs: the seed's artist's own songs.
        val artistItems = runCatching {
            withContext(Dispatchers.IO) {
                val artistId = seedItem.metadata?.artists?.firstNotNullOfOrNull { it.id }
                    ?: return@withContext emptyList()
                YouTube.artist(artistId).getOrNull()?.sections.orEmpty()
                    .flatMap { it.items }
                    .filterIsInstance<SongItem>()
                    .map { it.toMediaItem() }
            }
        }.getOrDefault(emptyList()).fresh().distinctBy { it.mediaId }
        if (artistItems.isNotEmpty()) return artistItems

        // Nothing came back for this seed: keep it out of this round, but let a retry use it again
        // (for a song played from search it's the only seed there is).
        failedInfiniteSeeds += seed
        return emptyList()
    }

    fun setInfiniteQueueEnabled(enabled: Boolean) {
        pendingInfiniteQueue = enabled
        scope.launch {
            dataStore.edit { settings ->
                settings[InfiniteQueueKey] = enabled
            }
        }
        if (enabled) {
            // Start over from the queue's tail: switching it off dropped the songs the old radio
            // lined up, and its seeds were already spent, so it would otherwise find nothing new.
            resetInfiniteSource()
            ensureQueueRunway()
        } else {
            removeUpcomingAutoplay()
        }
    }

    fun startRadioSeamlessly() {

        if (!playerInitialized.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player initialization")
            return
        }

        val currentMediaMetadata = player.currentMetadata ?: return

        val currentIndex = player.currentMediaItemIndex
        val currentMediaId = currentMediaMetadata.id

        resetQueueRunway()
        val generation = queueGeneration
        initialQueueLoading = true

        scope.launch(SilentHandler) {
            try {
                loadSeamlessRadio(currentIndex, currentMediaId, generation)
            } finally {
                if (generation == queueGeneration) {
                    initialQueueLoading = false
                    ensureQueueRunway()
                }
            }
        }
    }

    private suspend fun loadSeamlessRadio(
        currentIndex: Int,
        currentMediaId: String,
        generation: Int,
    ) {
        val radioQueue = YouTubeQueue(
            endpoint = WatchEndpoint(
                videoId = currentMediaId
            )
        )

        try {
            val initialStatus = withContext(Dispatchers.IO) {
                radioQueue.getInitialStatus()
                    .filterExplicit(dataStore.get(HideExplicitKey, false))
                    .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
            }
            if (generation != queueGeneration) return

            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }

            val radioItems = initialStatus.items.filter { item ->
                item.mediaId != currentMediaId && item.mediaId !in NotRecommended
            }

            if (radioItems.isNotEmpty()) {
                val itemCount = player.mediaItemCount

                if (itemCount > currentIndex + 1) {
                    player.removeMediaItems(currentIndex + 1, itemCount)
                }

                player.addMediaItems(currentIndex + 1, radioItems)
                if (player.shuffleModeEnabled) {
                    val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                    applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                }
            }

            currentQueue = radioQueue
        } catch (e: Exception) {

            try {
                val nextResult = withContext(Dispatchers.IO) {
                    YouTube.next(WatchEndpoint(videoId = currentMediaId)).getOrNull()
                }
                nextResult?.relatedEndpoint?.let { relatedEndpoint ->
                    val relatedPage = withContext(Dispatchers.IO) {
                        YouTube.related(relatedEndpoint).getOrNull()
                    }
                    if (generation != queueGeneration) return
                    relatedPage?.songs?.let { songs ->
                        val radioItems = songs
                            .filter { it.id != currentMediaId && it.id !in NotRecommended }
                            .map { it.toMediaItem() }
                            .filterExplicit(dataStore.get(HideExplicitKey, false))
                            .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))

                        if (radioItems.isNotEmpty()) {
                            val itemCount = player.mediaItemCount
                            if (itemCount > currentIndex + 1) {
                                player.removeMediaItems(currentIndex + 1, itemCount)
                            }
                            player.addMediaItems(currentIndex + 1, radioItems)
                            if (player.shuffleModeEnabled) {
                                val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                                applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                            }
                            // The old queue's pages no longer follow these songs; let infinite queue extend them.
                            currentQueue = EmptyQueue
                        }
                    }
                }
            } catch (_: Exception) {

            }
        }
    }

    fun getAutomixAlbum(albumId: String) {
        scope.launch(SilentHandler) {
            YouTube
                .album(albumId)
                .onSuccess {
                    getAutomix(it.album.playlistId)
                }
        }
    }

    fun getAutomix(playlistId: String) {
        if (dataStore.get(SimilarContent, true) &&
            !(dataStore.get(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL)) {
            scope.launch(SilentHandler) {
                try {

                    YouTube.next(WatchEndpoint(playlistId = playlistId))
                        .onSuccess { firstResult ->
                            YouTube.next(WatchEndpoint(playlistId = firstResult.endpoint.playlistId))
                                .onSuccess { secondResult ->
                                    automixItems.value = secondResult.items.map { song ->
                                        song.toMediaItem()
                                    }
                                }
                                .onFailure {

                                    if (firstResult.items.isNotEmpty()) {
                                        automixItems.value = firstResult.items.map { song ->
                                            song.toMediaItem()
                                        }
                                    }
                                }
                        }
                        .onFailure {

                            val currentSong = player.currentMetadata
                            if (currentSong != null) {

                                YouTube.next(WatchEndpoint(
                                    videoId = currentSong.id
                                )).onSuccess { radioResult ->
                                    val filteredItems = radioResult.items
                                        .filter { it.id != currentSong.id }
                                        .map { it.toMediaItem() }
                                    if (filteredItems.isNotEmpty()) {
                                        automixItems.value = filteredItems
                                    }
                                }.onFailure {

                                    YouTube.next(WatchEndpoint(videoId = currentSong.id)).getOrNull()?.relatedEndpoint?.let { relatedEndpoint ->
                                        YouTube.related(relatedEndpoint).onSuccess { relatedPage ->
                                            val relatedItems = relatedPage.songs
                                                .filter { it.id != currentSong.id }
                                                .map { it.toMediaItem() }
                                            if (relatedItems.isNotEmpty()) {
                                                automixItems.value = relatedItems

                                            }
                                        }
                                    }
                                }
                            }
                        }
                } catch (_: Exception) {

                }
            }
        }
    }

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        addToQueue(listOf(item))
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        playNext(listOf(item))
    }

    fun clearAutomix() {
        automixItems.value = emptyList()
    }

    fun playNext(items: List<MediaItem>) {

        if (player.mediaItemCount == 0 || player.playbackState == STATE_IDLE) {
            player.setMediaItems(items)
            player.prepare()

            if (castConnectionHandler?.isCasting?.value != true) {
                player.play()
            }
            return
        }

        if (dataStore.get(PreventDuplicateTracksInQueueKey, false)) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }

            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
            }
        }

        val insertIndex = player.currentMediaItemIndex + 1
        val shuffleEnabled = player.shuffleModeEnabled

        player.addMediaItems(insertIndex, items)
        player.prepare()

        if (shuffleEnabled) {

            val timeline = player.currentTimeline
            if (!timeline.isEmpty) {
                val size = timeline.windowCount
                val currentIndex = player.currentMediaItemIndex

                val newIndices = (insertIndex until (insertIndex + items.size)).toSet()

                val orderAfter = mutableListOf<Int>()
                var idx = currentIndex
                while (true) {
                    idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, true)
                    if (idx == C.INDEX_UNSET) break
                    if (idx != currentIndex) orderAfter.add(idx)
                }

                val prevList = mutableListOf<Int>()
                var pIdx = currentIndex
                while (true) {
                    pIdx = timeline.getPreviousWindowIndex(pIdx, Player.REPEAT_MODE_OFF, true)
                    if (pIdx == C.INDEX_UNSET) break
                    if (pIdx != currentIndex) prevList.add(pIdx)
                }
                prevList.reverse()

                // History stays in front of the current song. It used to be moved after the new
                // block, so everything already played came round again.
                val history = prevList.filter { it != currentIndex && it !in newIndices }
                val upcoming = orderAfter.filter { it != currentIndex && it !in newIndices }

                val nextBlock = (insertIndex until (insertIndex + items.size)).toList()
                val finalOrder = IntArray(size)
                var pos = 0
                history.forEach { if (pos < size) finalOrder[pos++] = it }
                if (pos < size) finalOrder[pos++] = currentIndex
                nextBlock.forEach { if (it in 0 until size && pos < size) finalOrder[pos++] = it }
                upcoming.forEach { if (pos < size) finalOrder[pos++] = it }

                if (pos < size) {
                    for (i in 0 until size) {
                        if (!finalOrder.contains(i)) {
                            finalOrder[pos++] = i
                            if (pos == size) break
                        }
                    }
                }

                player.setShuffleOrder(DefaultShuffleOrder(finalOrder, System.currentTimeMillis()))
            }
        }
    }

    fun addToQueue(items: List<MediaItem>) {

        if (dataStore.get(PreventDuplicateTracksInQueueKey, false)) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }

            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
            }
        }

        // The end of what the user queued, which is in front of any Autoplay songs.
        val insertAt = upcomingAutoplayStart()
        player.addMediaItems(insertAt, items)
        if (player.shuffleModeEnabled) {
            placeAddedInShuffleOrder(insertAt until insertAt + items.size, shuffleThem = false)
        }
        player.prepare()
    }

    /**
     * Fits songs just added while shuffled into the shuffle order without reshuffling the rest.
     * Reshuffling everything on each addition (as before) rearranged the whole upcoming queue
     * every time a page loaded or a song was queued, and threw History back into it. Now history
     * and what's lined up keep their order; the new songs go after the user's songs and before
     * Autoplay - or at the very end, if they are Autoplay.
     */
    private fun placeAddedInShuffleOrder(added: IntRange, shuffleThem: Boolean) {
        val timeline = player.currentTimeline
        if (timeline.isEmpty || added.isEmpty()) return
        val order = ArrayList<Int>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            if (index !in added) order += index
            index = timeline.getNextWindowIndex(index, REPEAT_MODE_OFF, true)
        }
        val block = added.toMutableList()
        if (shuffleThem) block.shuffle()
        val insertAt = if (player.getMediaItemAt(block.first()).isAutoplay) {
            order.size
        } else {
            val currentPos = order.indexOf(player.currentMediaItemIndex)
            (currentPos + 1 until order.size).firstOrNull { player.getMediaItemAt(order[it]).isAutoplay } ?: order.size
        }
        order.addAll(insertAt, block)
        if (order.size != timeline.windowCount) return
        player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.currentTimeMillis()))
    }

    fun toggleLibrary() {
        scope.launch {
            val songToToggle = currentSong.first()
            songToToggle?.let {
                val isInLibrary = it.song.inLibrary != null
                val token = if (isInLibrary) it.song.libraryRemoveToken else it.song.libraryAddToken

                token?.let { feedbackToken ->
                    YouTube.feedback(listOf(feedbackToken))
                }

                database.query {
                    update(it.song.toggleLibrary())
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun toggleLike() {
        scope.launch {
            val songToToggle = currentSong.first()
            songToToggle?.let {
                val song = it.song.toggleLike()
                database.query {
                    update(song)
                    syncUtils.likeSong(song)

                    if (dataStore.get(AutoDownloadOnLikeKey, false) && song.liked) {

                        val downloadRequest =
                            androidx.media3.exoplayer.offline.DownloadRequest
                                .Builder(song.id, song.id.toUri())
                                .setCustomCacheKey(song.id)
                                .setData(song.title.toByteArray())
                                .build()
                        androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                            this@MusicService,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false
                        )
                    }
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    private fun setupLoudnessEnhancer() {
        val audioSessionId = player.audioSessionId

        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            Timber.tag(TAG).w("setupLoudnessEnhancer: invalid audioSessionId ($audioSessionId), cannot create effect yet")
            return
        }

        if (loudnessEnhancer != null && loudnessEnhancerSessionId != audioSessionId) {
            // the player changed (a crossfade): the old enhancer stays with the outgoing song,
            // at its own gain, until that player is let go
            fadingLoudnessEnhancer?.runCatching { release() }
            fadingLoudnessEnhancer = loudnessEnhancer
            loudnessEnhancer = null
        }
        if (loudnessEnhancer == null) {
            try {
                loudnessEnhancerSessionId = audioSessionId
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                Timber.tag(TAG).d("LoudnessEnhancer created for sessionId=$audioSessionId")
            } catch (e: Exception) {
                reportException(e)
                loudnessEnhancer = null
                return
            }
        }

        scope.launch {
            try {
                val currentMediaId = withContext(Dispatchers.Main) {
                    player.currentMediaItem?.mediaId
                }

                val normalizeAudio = withContext(Dispatchers.IO) {
                    dataStore.data.map { it[AudioNormalizationKey] ?: true }.first()
                }

                // YouTube's loudness numbers describe YouTube's stream: a song from a module plays
                // another master, and evens out by its own ReplayGain in the sink instead
                val fromModule = currentMediaId != null &&
                    resolvedSourceIds[currentMediaId].let { it != null && it != com.mochyy.mochymusic.modules.SourceModules.YouTubeId }
                if (normalizeAudio && currentMediaId != null && !fromModule) {
                    val format = withContext(Dispatchers.IO) {
                        database.format(currentMediaId).first()
                    }

                    Timber.tag(TAG).d("Audio normalization enabled: $normalizeAudio")
                    Timber.tag(TAG).d("Format loudnessDb: ${format?.loudnessDb}, perceptualLoudnessDb: ${format?.perceptualLoudnessDb}")

                    val loudness = format?.loudnessDb ?: format?.perceptualLoudnessDb

                    withContext(Dispatchers.Main) {
                        if (loudness != null) {
                            val loudnessDb = loudness.toFloat()
                            val targetGain = (-loudnessDb * 100).toInt()
                            val clampedGain = targetGain.coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)

                            Timber.tag(TAG).d("Calculated raw normalization gain: $targetGain mB (from loudness: $loudnessDb)")

                            try {
                                loudnessEnhancer?.setTargetGain(clampedGain)
                                loudnessEnhancer?.enabled = true
                                Timber.tag(TAG).i("LoudnessEnhancer gain applied: $clampedGain mB")
                            } catch (e: Exception) {
                                Timber.tag(TAG).e(e, "Failed to apply loudness enhancement")
                                reportException(e)
                                releaseLoudnessEnhancer()
                            }
                        } else {
                            loudnessEnhancer?.enabled = false
                            Timber.tag(TAG).w("Normalization enabled but no loudness data available - no normalization applied")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loudnessEnhancer?.enabled = false
                        Timber.tag(TAG).d("setupLoudnessEnhancer: normalization disabled or mediaId unavailable")
                    }
                }
            } catch (e: Exception) {
                reportException(e)
                releaseLoudnessEnhancer()
            }
        }
    }

    private fun releaseLoudnessEnhancer() {
        try {
            loudnessEnhancer?.release()
            Timber.tag(TAG).d("LoudnessEnhancer released")
        } catch (e: Exception) {
            reportException(e)
            Timber.tag(TAG).e(e, "Error releasing LoudnessEnhancer: ${e.message}")
        } finally {
            loudnessEnhancer = null
        }
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        setupLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        releaseLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            },
        )
    }

    private var previousMediaItemIndex = C.INDEX_UNSET

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        clearStreamFailure()
        if (!promotingCrossmix) crossmixDirector.onItemChanged()

        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            val repeatMode = runBlocking { dataStore.get(RepeatModeKey, REPEAT_MODE_OFF) }
            if (repeatMode == REPEAT_MODE_ONE &&
                previousMediaItemIndex != C.INDEX_UNSET &&
                previousMediaItemIndex != player.currentMediaItemIndex) {

                player.seekTo(previousMediaItemIndex, 0)
            }
        }
        previousMediaItemIndex = player.currentMediaItemIndex

        mediaItem?.mediaId?.let { announceStreamSource(it) }

        setupLoudnessEnhancer()

        scrobbleTimer.onSongStop()
        playReporter.onSongStop()
        if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
            scrobbleTimer.onSongStart(player.currentMetadata, player.duration)
            playReporter.onSongStart(player.currentMetadata, player.duration)
        }

        if (castConnectionHandler?.isCasting?.value == true &&
            castConnectionHandler?.isSyncingFromCast != true &&
            mediaItem != null) {
            val metadata = mediaItem.metadata
            if (metadata != null) {

                val navigated = castConnectionHandler?.navigateToMediaIfInQueue(metadata.id) ?: false
                if (!navigated) {

                    castConnectionHandler?.loadMedia(metadata)
                }
            }
        }

        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            ensureQueueRunway()
        }

        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        // IDLE is where a failed stream sits; anything else means it's loading or playing again
        if (playbackState != Player.STATE_IDLE) clearStreamFailure()

        if (playbackState == Player.STATE_ENDED) {
            val repeatMode = runBlocking { dataStore.get(RepeatModeKey, REPEAT_MODE_OFF) }
            if (repeatMode == REPEAT_MODE_ALL && player.mediaItemCount > 0) {
                player.seekTo(0, 0)
                player.prepare()
                player.play()
            } else {
                // Ran out of songs (e.g. a page load failed earlier): try to extend and keep playing.
                ensureQueueRunway()
            }
        }

        if (dataStore.get(PersistentQueueKey, true) && !isSilenceSkipping) {
            saveQueueToDisk()
        }

        if (playbackState == Player.STATE_READY) {
            consecutivePlaybackErr = 0
            retryCount = 0
            waitingForNetworkConnection.value = false
            retryJob?.cancel()

            player.currentMediaItem?.mediaId?.let { mediaId ->
                resetRetryCount(mediaId)
                Timber.tag(TAG).d("Playback successful for $mediaId, reset retry count")
            }
            scheduleCrossfade()
        }

        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
            scrobbleTimer.onSongStop()
            playReporter.onSongStop()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {

        if (playWhenReady && castConnectionHandler?.isCasting?.value == true) {
            player.pause()
            return
        }

        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            if (playWhenReady) {
                isPausedByVolumeMute = false
            }

            if (!playWhenReady && !isPausedByVolumeMute) {
                wasPlayingBeforeVolumeMute = false
            }
        }

        if (playWhenReady) {
            setupLoudnessEnhancer()
        }
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            scheduleCrossfade()
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                val focusGranted = requestAudioFocus()
                if (focusGranted) {
                    openAudioEffectSession()
                }
            } else {
                closeAudioEffectSession()
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
        }

        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            updateWidgetUI(player.isPlaying)
            if (player.isPlaying) {
                startWidgetUpdates()
            } else {
                stopWidgetUpdates()
            }
        }

        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
            )
        ) {
            publishPresence()
        }

        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            scrobbleTimer.onPlayingChanged(player.isPlaying, player.currentMetadata, player.duration)
            playReporter.onPlayingChanged(player.isPlaying, player.currentMetadata, player.duration)
            // a pause is often the last thing before the app is closed, and the timed saves only
            // run every 30 seconds while paused: save the exact place now so it comes back there
            if (!player.isPlaying && player.playbackState == Player.STATE_READY && dataStore.get(PersistentQueueKey, true)) {
                saveQueueToDisk()
            }
        }

    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        if (shuffleModeEnabled) {

            if (player.mediaItemCount == 0) return

            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            val currentIndex = player.currentMediaItemIndex
            val totalCount = player.mediaItemCount

            applyShuffleOrder(currentIndex, totalCount, shufflePlaylistFirst, keepHistory = true)
        }

        if (dataStore.get(RememberShuffleAndRepeatKey, true)) {
            scope.launch {
                dataStore.edit { settings ->
                    settings[ShuffleModeKey] = shuffleModeEnabled
                }
            }
        }

        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }

        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    private fun applyShuffleOrder(
        currentIndex: Int,
        totalCount: Int,
        shufflePlaylistFirst: Boolean,
        /**
         * Songs before the current one already played: keep them, in order, as history rather than
         * shuffling them back in. True when shuffle is switched on mid-queue; false when a queue
         * starts shuffled partway in, where those songs haven't played yet.
         */
        keepHistory: Boolean = false,
    ) {
        if (totalCount == 0) return

        if (shufflePlaylistFirst && originalQueueSize > 0 && originalQueueSize < totalCount) {

            val originalIndices = (0 until originalQueueSize).filter { it != currentIndex }.toMutableList()
            val addedIndices = (originalQueueSize until totalCount).filter { it != currentIndex }.toMutableList()

            originalIndices.shuffle()
            addedIndices.shuffle()

            val shuffledIndices = IntArray(totalCount)
            var pos = 0
            shuffledIndices[pos++] = currentIndex

            if (currentIndex < originalQueueSize) {
                originalIndices.forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            } else {
                (0 until originalQueueSize).shuffled().forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            }
            player.setShuffleOrder(DefaultShuffleOrder(shapeShuffleOrder(shuffledIndices, currentIndex, keepHistory), System.currentTimeMillis()))
        } else {
            val shuffledIndices = IntArray(totalCount) { it }
            shuffledIndices.shuffle()

            val currentItemIndexInShuffled = shuffledIndices.indexOf(currentIndex)
            if (currentItemIndexInShuffled != -1) {
                val temp = shuffledIndices[0]
                shuffledIndices[0] = shuffledIndices[currentItemIndexInShuffled]
                shuffledIndices[currentItemIndexInShuffled] = temp
            }
            player.setShuffleOrder(DefaultShuffleOrder(shapeShuffleOrder(shuffledIndices, currentIndex, keepHistory), System.currentTimeMillis()))
        }
    }

    /** Autoplay to the back; with [keepHistory], the songs before [currentIndex] to the front, in order. */
    private fun shapeShuffleOrder(order: IntArray, currentIndex: Int, keepHistory: Boolean): IntArray {
        val shaped = autoplayLast(order)
        if (!keepHistory || currentIndex <= 0) return shaped
        val result = IntArray(shaped.size)
        var pos = 0
        for (i in 0 until currentIndex) result[pos++] = i
        for (i in shaped) if (i >= currentIndex) result[pos++] = i
        return result
    }

    /**
     * Shuffle mixes what the user queued, not Autoplay: those songs stay after everything else, in
     * the order the radio gave them. The song playing keeps its place at the front.
     */
    private fun autoplayLast(order: IntArray): IntArray {
        if (order.isEmpty()) return order
        val autoplay = (0 until player.mediaItemCount)
            .filter { it != order[0] && player.getMediaItemAt(it).isAutoplay }
        if (autoplay.isEmpty()) return order
        val isAutoplay = autoplay.toHashSet()
        val result = IntArray(order.size)
        var pos = 0
        for (index in order) if (index !in isAutoplay) result[pos++] = index
        for (index in autoplay) result[pos++] = index
        return result
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        // a faster song ends sooner: Discord's bar has to know
        publishPresence()
    }

    private fun getHttpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }

    private fun isExpiredUrlError(error: PlaybackException): Boolean {
        val responseCode = getHttpResponseCode(error)
        return responseCode == 403
    }

    private fun isRangeNotSatisfiableError(error: PlaybackException): Boolean {
        val responseCode = getHttpResponseCode(error)
        return responseCode == 416
    }

    private fun isPageReloadError(error: PlaybackException): Boolean {
        val errorMessage = error.message?.lowercase() ?: ""
        val causeMessage = error.cause?.message?.lowercase() ?: ""
        val innerCauseMessage = error.cause?.cause?.message?.lowercase() ?: ""

        val reloadKeywords = listOf(
            "page needs to be reloaded",
            "pagina deve essere ricaricata",
            "la pagina deve essere ricaricata",
            "page must be reloaded",
            "reload",
            "ricaricata"
        )

        return reloadKeywords.any { keyword ->
            errorMessage.contains(keyword) ||
            causeMessage.contains(keyword) ||
            innerCauseMessage.contains(keyword)
        }
    }

    private fun isNetworkRelatedError(error: PlaybackException): Boolean {

        if (isExpiredUrlError(error) || isRangeNotSatisfiableError(error) || isPageReloadError(error)) {
            return false
        }
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
                error.cause is java.net.ConnectException ||
                error.cause is java.net.UnknownHostException ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    }

    private fun isAudioRendererError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        if (!playerInitialized.value) {
            Timber.tag(TAG).e(error, "Player error occurred but player not initialized")
            return
        }

        // Offline mode refused a song that isn't on the device before anything went out. Nothing
        // to retry or recover - and the recovery below clears the song's cache, which is what
        // offline playback plays from - so straight on to the next song that is on the device.
        if (com.mochyy.mochymusic.utils.OfflineMode.isOn &&
            generateSequence<Throwable>(error) { it.cause }.any { it is com.music.innertube.OfflineModeException }
        ) {
            val next = nextOfflineIndex()
            if (next != C.INDEX_UNSET) {
                player.seekTo(next, C.TIME_UNSET)
                player.prepare()
                if (castConnectionHandler?.isCasting?.value != true) player.play()
            } else {
                player.pause()
                showBanner("Offline mode: nothing after this is on your device")
            }
            return
        }

        lastPlaybackError = error
        // every provider that's on was already asked: retrying would only ask them all again, so
        // straight on with the reason. Without a connection it waits for one like any other song.
        if (isNetworkConnected.value &&
            generateSequence<Throwable>(error) { it.cause }.any { it is com.mochyy.mochymusic.playback.custom.NoProviderException }
        ) {
            failStream()
            return
        }
        val mediaId = player.currentMediaItem?.mediaId
        Timber.tag(TAG).w(error, "Player error occurred for $mediaId: errorCode=${error.errorCode}, message=${error.message}, causes=${generateSequence<Throwable>(error) { it.cause }.joinToString(" <- ") { it.javaClass.simpleName + ":" + it.message }}")
        reportException(error)

        // a decoder turning the format down: the next provider's toast says so
        if (mediaId != null && isDecoderError(error)) {
            val format = (error as? androidx.media3.exoplayer.ExoPlaybackException)?.rendererFormat
            noteMissingDecoder(
                mediaId,
                StreamFacts.codecOf(format?.sampleMimeType, format?.codecs) ?: streamFacts.value[mediaId]?.codec,
                format?.sampleRate,
            )
        }
        // a module's copy that won't parse would fail the same way every time that module is asked
        if (mediaId != null && isContainerParseError(error)) dropModuleCache(mediaId)
        if (mediaId != null && forceFallBack(mediaId, announce = !decoderNotices.containsKey(mediaId))) return

        if (mediaId != null && hasExceededRetryLimit(mediaId)) {
            Timber.tag(TAG).w("Song $mediaId has exceeded retry limit, skipping")
            markSongAsFailed(mediaId)
            handleFinalFailure()
            return
        }

        if (mediaId != null) {
            performAggressiveCacheClear(mediaId)
        }

        when {
            isAudioRendererError(error) -> {
                Timber.tag(TAG).d("AudioTrack error detected (${error.errorCode}), performing safe recovery")
                handleAudioRendererError(mediaId)
                return
            }
            isRangeNotSatisfiableError(error) -> {
                Timber.tag(TAG).d("Range Not Satisfiable (416) detected, performing strict recovery")
                handleRangeNotSatisfiableError(mediaId)
                return
            }
            isPageReloadError(error) -> {
                Timber.tag(TAG).d("Page reload error detected, performing strict recovery")
                handlePageReloadError(mediaId)
                return
            }
            isExpiredUrlError(error) -> {
                Timber.tag(TAG).d("Expired URL (403) detected, refreshing stream URL")
                handleExpiredUrlError(mediaId)
                return
            }

            !isNetworkConnected.value || isNetworkRelatedError(error) -> {
                Timber.tag(TAG).d("Network-related error detected, waiting for connection")
                waitOnNetworkError()
                return
            }
        }

        // A file that won't parse is almost always two streams glued together in the cache, which
        // the clear above has just dropped: a fresh try plays. It used to give up on the spot.
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            isContainerParseError(error)) {
            Timber.tag(TAG).d("IO error detected (${error.errorCode}), attempting recovery")
            handleGenericIOError(mediaId)
            return
        }

        Timber.tag(TAG).d("Unrecoverable error, failing the stream")
        failStream()
    }

    private fun performAggressiveCacheClear(mediaId: String) {
        Timber.tag(TAG).d("Performing aggressive cache clear for $mediaId")

        songUrlCache.remove(mediaId)

        try {
            playerCache.removeResource(mediaId)
            Timber.tag(TAG).d("Cleared player cache for $mediaId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear player cache for $mediaId")
        }

        if (!mediaId.startsWith("LOCAL_")) {
            try {
                YTPlayerUtils.forceRefreshForVideo(mediaId)
                Timber.tag(TAG).d("Cleared decryption caches for $mediaId")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to clear decryption caches for $mediaId")
            }
        }
    }

    private fun isContainerParseError(error: PlaybackException) =
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED

    /** Every source module's cached copy of [mediaId], which lives under its own key. */
    private fun dropModuleCache(mediaId: String) {
        playerCache.keys
            .filter { it.startsWith(ModuleCacheKeyPrefix) && it.endsWith("|$mediaId") }
            .forEach { key -> runCatching { playerCache.removeResource(key) } }
    }

    private fun hasExceededRetryLimit(mediaId: String): Boolean {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        return currentRetries >= MAX_RETRY_PER_SONG
    }

    private fun incrementRetryCount(mediaId: String) {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        currentMediaIdRetryCount[mediaId] = currentRetries + 1
        Timber.tag(TAG).d("Retry count for $mediaId: ${currentRetries + 1}/$MAX_RETRY_PER_SONG")
    }

    private fun resetRetryCount(mediaId: String) {
        currentMediaIdRetryCount.remove(mediaId)
        recentlyFailedSongs.remove(mediaId)
    }

    private fun markSongAsFailed(mediaId: String) {
        recentlyFailedSongs.add(mediaId)
        currentMediaIdRetryCount.remove(mediaId)

        failedSongsClearJob?.cancel()
        failedSongsClearJob = scope.launch {
            delay(5 * 60 * 1000L)
            recentlyFailedSongs.clear()
            Timber.tag(TAG).d("Cleared recently failed songs list")
        }
    }

    private fun handleAudioRendererError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        // whether it was playing, read before the pause below wipes it: the recovery used to go
        // by the audio-focus flag instead, so it re-prepared the song and left it paused
        val resume = player.playWhenReady || wasPlayingBeforeAudioFocusLoss
        retryJob?.cancel()
        retryJob = scope.launch {
            try {

                player.pause()
                Timber.tag(TAG).d("Paused playback due to AudioTrack error")

                delay(RETRY_DELAY_MS * 3)

                if (!playerInitialized.value) {
                    Timber.tag(TAG).w("Player no longer initialized, aborting AudioTrack recovery")
                    return@launch
                }

                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {

                    val currentPosition = player.currentPosition
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()

                    Timber.tag(TAG).d("Retrying playback for $mediaId after AudioTrack error")

                    if (resume) {
                        delay(500)
                        if ((hasAudioFocus || requestAudioFocus()) && playerInitialized.value) {
                            if (castConnectionHandler?.isCasting?.value != true) {
                                player.play()
                            }
                        }
                    }
                } else {
                    Timber.tag(TAG).w("Invalid media item index during AudioTrack recovery")
                    handleFinalFailure()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error during AudioTrack error recovery")
                handleFinalFailure()
            }
        }
    }

    private fun handleRangeNotSatisfiableError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)
        // A song brought back after the app was closed asks its old, expired stream for the place
        // it stopped, which is what answers 416. A fresh stream at the same place plays, so the
        // first retry keeps it; going back to the start used to throw the restored progress away.
        // Only a song that keeps failing starts over.
        val fromStart = (currentMediaIdRetryCount[mediaId] ?: 0) > 1

        retryJob?.cancel()
        retryJob = scope.launch {

            performAggressiveCacheClear(mediaId)

            delay(RETRY_DELAY_MS)

            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, if (fromStart) 0 else player.currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after 416 error (${if (fromStart) "from the start" else "where it was"})")
        }
    }

    private fun handlePageReloadError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            Timber.tag(TAG).d("Handling page reload error for $mediaId")

            performAggressiveCacheClear(mediaId)

            delay(RETRY_DELAY_MS)

            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after page reload error")
        }
    }

    private fun handleExpiredUrlError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        songUrlCache.remove(mediaId)
        Timber.tag(TAG).d("Cleared cached URL for $mediaId")

        try {
            YTPlayerUtils.forceRefreshForVideo(mediaId)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear decryption caches")
        }

        retryJob?.cancel()
        retryJob = scope.launch {
            delay(RETRY_DELAY_MS)

            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after 403 error")
        }
    }

    private fun handleGenericIOError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob = scope.launch {
            performAggressiveCacheClear(mediaId)
            delay(RETRY_DELAY_MS)

            val currentPosition = player.currentPosition
            val currentIndex = player.currentMediaItemIndex
            player.seekTo(currentIndex, currentPosition)
            player.prepare()

            Timber.tag(TAG).d("Retrying playback for $mediaId after generic IO error")
        }
    }

    private fun handleFinalFailure() {
        player.currentMediaItem?.mediaId?.let { if (forceFallBack(it, includeYouTube = true)) return }
        Timber.tag(TAG).d("All recovery attempts exhausted, failing the stream")
        failStream()
    }

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
        super.onDeviceVolumeChanged(volume, muted)
        val pauseOnMute = dataStore.get(PauseOnMute, false)

        if ((volume == 0 || muted) && pauseOnMute) {
            if (player.isPlaying) {
                wasPlayingBeforeVolumeMute = true
                isPausedByVolumeMute = true
                player.pause()
            }
        } else if (volume > 0 && !muted && pauseOnMute) {
            if (wasPlayingBeforeVolumeMute && !player.isPlaying && castConnectionHandler?.isCasting?.value != true) {
                wasPlayingBeforeVolumeMute = false
                isPausedByVolumeMute = false
                player.play()
            }
        }
    }

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource
                    .Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(
                                OkHttpClient
                                    .Builder()
                                    .dns(object : Dns {
                                        override fun lookup(hostname: String): List<InetAddress> {
                                            val addresses = Dns.SYSTEM.lookup(hostname)
                                            return when (this@MusicService.ipVersion) {
                                                IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                                                IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                                                IpVersion.AUTO -> addresses
                                            }
                                        }
                                    })
                                    .proxy(YouTube.proxy)
                                    .proxyAuthenticator { _, response ->
                                        YouTube.proxyAuth?.let { auth ->
                                            response.request.newBuilder()
                                                .header("Proxy-Authorization", auth)
                                                .build()
                                        } ?: response.request
                                    }
                                    .build(),
                            ),
                        ),
                    ),
            ).setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    private var isSilenceSkipping = false

    private fun handleLongSilenceDetected() {
        if (!instantSilenceSkipEnabled.value) return
        if (silenceSkipJob?.isActive == true) return

        silenceSkipJob = scope.launch {

            delay(200)
            performInstantSilenceSkip()
        }
    }

    private suspend fun performInstantSilenceSkip() {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        if (duration <= INSTANT_SILENCE_SKIP_STEP_MS) return

        isSilenceSkipping = true
        try {
            var hops = 0
            val silenceProcessor = playerSilenceProcessors[player] ?: return
            while (coroutineContext.isActive && instantSilenceSkipEnabled.value && silenceProcessor.isCurrentlySilent()) {
                val current = player.currentPosition
                val target = (current + INSTANT_SILENCE_SKIP_STEP_MS).coerceAtMost(duration - 500)

                if (target <= current) break

                silenceProcessor.resetTracking()
                player.seekTo(target)
                hops++

                if (hops >= 80 || target >= duration - 500) break

                delay(INSTANT_SILENCE_SKIP_SETTLE_MS)
            }
            if (hops > 0) {
                Timber.tag(TAG).d("Silence skip: jumped $hops times")
            }
        } finally {
            isSilenceSkipping = false
        }
    }

    /** Tells the Discord status what's playing; it ignores this when Discord isn't signed in or is off. */
    private fun publishPresence() {
        val metadata = player.currentMetadata
        val state = player.playbackState
        if (metadata == null || state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
            discordPresence.update(null)
            return
        }
        // the library's record lags a moment behind a song change; use it only once it's this song
        val song = currentSong.value?.takeIf { it.id == metadata.id }
        val facts = streamFacts.value[metadata.id]
        discordPresence.update(
            com.mochyy.mochymusic.discord.NowPlaying(
                mediaId = metadata.id,
                title = metadata.title,
                artist = metadata.artists.joinToString { it.name },
                album = metadata.album?.title ?: song?.song?.albumName,
                artworkUrl = metadata.thumbnailUrl,
                artistArtworkUrl = song?.artists?.firstNotNullOfOrNull { it.thumbnailUrl },
                durationMs = player.duration.takeIf { it > 0 } ?: (metadata.duration * 1000L),
                positionMs = player.currentPosition,
                readAtMs = System.currentTimeMillis(),
                speed = player.playbackParameters.speed,
                // buffering with play pressed still counts as playing, so the status doesn't flicker
                isPlaying = player.playWhenReady,
                quality = facts?.describe(song?.format?.takeIf { !facts.fromModule }),
                isYouTube = song?.song?.isLocal != true && metadata.localUri == null,
            ),
        )
    }

    /**
     * How many bytes of this track the player still needs from [dataSpec.position].
     *
     * Metrolist's approach: prefer the length the player asked for, else derive it from the
     * stored content length. Guessing a chunk when neither is known makes a partially cached
     * track look complete, which is how replay used to break.
     */
    /**
     * Whether [cache] has every byte [dataSpec] asks for. The cache's own record of the file's size
     * comes first. The format row is rewritten each time the song streams, so a finished download
     * checked against another stream's size looked incomplete, and the song went online for the
     * rest: "connection failed" offline, or the wrong stream glued on.
     */
    private fun holdsWhole(cache: SimpleCache, mediaId: String, dataSpec: DataSpec): Boolean {
        val length = when {
            dataSpec.length >= 0 -> dataSpec.length
            else -> runCatching {
                androidx.media3.datasource.cache.ContentMetadata.getContentLength(cache.getContentMetadata(mediaId))
            }.getOrNull()?.takeIf { it > 0L }?.let { (it - dataSpec.position).coerceAtLeast(1L) }
                ?: requiredCachedLength(mediaId, dataSpec)
        }
        return cache.isCached(mediaId, dataSpec.position, length)
    }

    /**
     * YouTube can hand back a different file for the same song from one lookup to the next: Opus
     * on Wi-Fi, AAC on mobile data, or whatever another client offers. Both are cached under the
     * song's id, so the start of one got glued onto the rest of the other and the player gave up
     * with "parsing container malformed". A different itag or length means a different file, so
     * the old bytes go.
     */
    private fun dropStaleCachedBytes(mediaId: String, itag: Int, contentLength: Long?) {
        if (playerCache.getCachedSpans(mediaId).isEmpty()) return
        val freshLength = contentLength ?: 0L
        val cachedLength = runCatching {
            androidx.media3.datasource.cache.ContentMetadata.getContentLength(playerCache.getContentMetadata(mediaId))
        }.getOrDefault(C.LENGTH_UNSET.toLong())
        val previous = runCatching { runBlocking(Dispatchers.IO) { database.format(mediaId).first() } }.getOrNull()
        val sameFile = when {
            cachedLength > 0 && freshLength > 0 -> cachedLength == freshLength
            previous != null -> previous.itag == itag &&
                (previous.contentLength <= 0 || freshLength <= 0 || previous.contentLength == freshLength)
            // nothing to compare against: a bad splice is still caught by the parse-error retry
            else -> true
        }
        if (sameFile) return
        Timber.tag(TAG).i("Cached bytes for $mediaId are from another stream (itag ${previous?.itag} -> $itag), dropping them")
        runCatching { playerCache.removeResource(mediaId) }
            .onFailure { Timber.tag(TAG).w(it, "Couldn't drop stale cached bytes for $mediaId") }
    }

    private fun requiredCachedLength(mediaId: String, dataSpec: DataSpec): Long {
        if (dataSpec.length >= 0) return dataSpec.length
        val contentLength = runCatching {
            runBlocking(Dispatchers.IO) { database.format(mediaId).first()?.contentLength }
        }.getOrNull()?.takeIf { it > 0L }
        return contentLength?.let { (it - dataSpec.position).coerceAtLeast(1L) } ?: CHUNK_LENGTH
    }

    /**
     * Post a visible, named playback error. The media notification channel is IMPORTANCE_LOW and
     * silent by design, so failures posted there go unnoticed - errors get their own channel.
     */
    private fun notifyPlaybackError(title: String, message: String) {
        runCatching {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(
                        ERROR_CHANNEL_ID,
                        getString(R.string.playback_error_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
            }
            val notification = NotificationCompat.Builder(this, ERROR_CHANNEL_ID)
                .setSmallIcon(R.drawable.mochy_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .build()
            nm.notify(ERROR_NOTIFICATION_ID, notification)
        }.onFailure { Timber.tag(TAG).w(it, "Could not post playback error notification") }
    }

    // the song share's preview and video read the song through this, so they hit the same cache and get the same links playback does
    fun shareDataSourceFactory(): DataSource.Factory = createDataSourceFactory()

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            // a stream a source module or backend resolved to a real URL (an HLS playlist, given
            // as a data: URI when the module rewrote it, and its segments) carries no media id:
            // it's fetched as it is
            val mediaId = dataSpec.key ?: run {
                val scheme = dataSpec.uri.scheme
                if (scheme == "https" || scheme == "http" || scheme == "data") return@Factory dataSpec
                error("No media id")
            }

            // A source module's stream (Monochrome's FLAC file) goes out as the module gave it. It
            // used to carry the bare media id, so everything below took it for a YouTube song and
            // swapped in YouTube's stream - a cached YouTube url, or a fresh one - whenever the FLAC
            // wasn't already in the cache: the song played as Opus with a FLAC badge until the
            // decoder said otherwise.
            if (mediaId.startsWith(ModuleCacheKeyPrefix)) {
                // offline mode switched on mid-song: the rest of it isn't fetched either
                if (com.mochyy.mochymusic.utils.OfflineMode.isOn) throw com.music.innertube.OfflineModeException()
                return@Factory dataSpec.withRequestHeaders(
                    dataSpec.httpRequestHeaders + moduleStreamHeaders[mediaId].orEmpty()
                )
            }

            val shouldBypassCache = bypassCacheForQualityChange.contains(mediaId)

            val scheme = dataSpec.uri.scheme
            if (scheme == "content" || scheme == "file") {
                return@Factory dataSpec
            }

            if (!shouldBypassCache) {
                if (holdsWhole(downloadCache, mediaId, dataSpec) || holdsWhole(playerCache, mediaId, dataSpec)) {
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec
                }

                // Offline mode: what isn't on the device isn't fetched, not even from a url
                // already in hand. onPlayerError moves on to the next song that is.
                if (com.mochyy.mochymusic.utils.OfflineMode.isOn) throw com.music.innertube.OfflineModeException()

                songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }?.let { cached ->
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                    return@Factory dataSpec
                        .withUri(cached.first.toUri())
                        .withRequestHeaders(dataSpec.httpRequestHeaders + songStreamHeaders[mediaId].orEmpty())
                }
            } else {
                Timber.tag("MusicService").i("BYPASSING CACHE for $mediaId due to quality change")
            }

            if (com.mochyy.mochymusic.utils.OfflineMode.isOn) throw com.music.innertube.OfflineModeException()

            Timber.tag("MusicService").i("FETCHING STREAM: $mediaId | quality=$audioQuality")
            val playbackData = runBlocking(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                )
            }.getOrElse { throwable ->
                when (throwable) {
                    is PlaybackException -> throw throwable

                    is java.net.ConnectException, is java.net.UnknownHostException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        )
                    }

                    is java.net.SocketTimeoutException -> {
                        throw PlaybackException(
                            getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        )
                    }

                    else -> {
                        // Surface the reason rather than a silent stall; the media notification
                        // channel is silent by design so errors get their own.
                        if (mediaId !in listeningOnlyStreams) {
                            notifyPlaybackError(
                                getString(R.string.error_stream_unavailable_title),
                                throwable.message ?: getString(R.string.error_unknown),
                            )
                        }
                        throw PlaybackException(
                            getString(R.string.error_unknown),
                            throwable,
                            PlaybackException.ERROR_CODE_REMOTE_ERROR
                        )
                    }
                }
            }

            val nonNullPlayback = requireNotNull(playbackData) {
                getString(R.string.error_unknown)
            }
            run {
                val format = nonNullPlayback.format
                val loudnessDb = nonNullPlayback.audioConfig?.loudnessDb
                val perceptualLoudnessDb = nonNullPlayback.audioConfig?.perceptualLoudnessDb

                Timber.tag(TAG).d("Storing format for $mediaId with loudnessDb: $loudnessDb, perceptualLoudnessDb: $perceptualLoudnessDb")
                if (loudnessDb == null && perceptualLoudnessDb == null) {
                    Timber.tag(TAG).w("No loudness data available from YouTube for video: $mediaId")
                }

                // before the row below forgets which file the cached bytes came from
                dropStaleCachedBytes(mediaId, format.itag, format.contentLength)
                database.query {
                    upsert(
                        FormatEntity(
                            id = mediaId,
                            itag = format.itag,
                            mimeType = format.mimeType.split(";")[0],
                            codecs = format.mimeType.substringAfter("codecs=", "").removeSurrounding("\""),
                            bitrate = format.bitrate,
                            sampleRate = format.audioSampleRate,
                            contentLength = format.contentLength ?: 0L,
                            loudnessDb = loudnessDb,
                            perceptualLoudnessDb = perceptualLoudnessDb,
                            playbackUrl = nonNullPlayback.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                        )
                    )
                }
                playReporter.offerTracking(mediaId, nonNullPlayback.playbackTracking)
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, nonNullPlayback) }

                if (bypassCacheForQualityChange.remove(mediaId)) {
                    Timber.tag("MusicService").d("Cleared bypass cache flag for $mediaId after fresh fetch")
                }

                val streamUrl = nonNullPlayback.streamUrl

                songUrlCache[mediaId] =
                    streamUrl to System.currentTimeMillis() + (nonNullPlayback.streamExpiresInSeconds * 1000L)
                songStreamHeaders[mediaId] = nonNullPlayback.streamHeaders

                return@Factory dataSpec
                    .withUri(streamUrl.toUri())
                    .subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)
                    .withRequestHeaders(dataSpec.httpRequestHeaders + nonNullPlayback.streamHeaders)
            }
        }
    }

    private fun announceStreamSource(mediaId: String) {

        if (!dataStore.get(ShowStreamSourceToastKey, false)) return
        if (lastAnnouncedSourceMediaId == mediaId) return
        // its decoder toast names the source too, once the stream's format is known
        if (decoderNotices.containsKey(mediaId)) return
        val source = resolvedStreamSources[mediaId] ?: return
        lastAnnouncedSourceMediaId = mediaId
        showBanner("Playing from $source")
    }

    /**
     * The app's top banner (the one "Added to playlist" uses), with the song's cover. Only while
     * Mochy is on screen: one raised in the background would greet the user later, for a song
     * that's long gone.
     */
    private fun showBanner(text: String) {
        val visible = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        if (!visible) return
        com.mochyy.mochymusic.ui.component.TopToaster.show(
            text = text,
            thumbnail = player.currentMediaItem?.mediaMetadata?.artworkUri?.toString(),
        )
    }

    /**
     * A song that failed from one provider starts again from the next one in the order, where it
     * left off. A module's stream always gives way like this - a module this phone can't play (its
     * codec, its DRM, its server) used to leave the song retrying, then skipped, with YouTube never
     * asked. Force playback adds YouTube's own failures, which get YouTube's recoveries first (a
     * stale URL is refreshed, a cipher retried) and only come here once those are spent, via
     * [handleFinalFailure]. Without [announce] it says nothing: a decoder notice follows instead.
     */
    private fun forceFallBack(mediaId: String, includeYouTube: Boolean = false, announce: Boolean = true): Boolean {
        val failed = resolvedSourceIds[mediaId] ?: return false
        if (failed == com.mochyy.mochymusic.modules.SourceModules.YouTubeId &&
            (!includeYouTube || !dataStore.get(ForcePlaybackKey, false))
        ) {
            return false
        }
        recentFailures(mediaId)
        val skip = failedSources.getOrPut(mediaId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
        if (!skip.add(failed)) return false
        failedSourcesAt.putIfAbsent(mediaId, System.currentTimeMillis())
        val next = sourceModules.order.value.firstOrNull { it !in skip && sourceModules.isUsable(it) } ?: return false
        Timber.tag(TAG).i("Force playback: $mediaId failed from $failed, trying $next")
        android.util.Log.i("SourceModules", "Force playback: ${sourceModules.nameOf(failed)} failed on $mediaId, trying ${sourceModules.nameOf(next)}")
        resolvedStreamSources.remove(mediaId)
        songUrlCache.remove(mediaId)
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        val wasPlaying = player.playWhenReady
        if (announce) showBanner("${sourceModules.nameOf(failed)} couldn't play this · trying ${sourceModules.nameOf(next)}")
        player.stop()
        player.seekTo(index, position)
        player.prepare()
        player.playWhenReady = wasPlaying
        return true
    }

    private fun createMediaSourceFactory(): androidx.media3.exoplayer.source.MediaSource.Factory {
        val defaultFactory = DefaultMediaSourceFactory(
            createDataSourceFactory(),
            androidx.media3.extractor.DefaultExtractorsFactory(),
        )

        return object : androidx.media3.exoplayer.source.MediaSource.Factory by defaultFactory {
            override fun createMediaSource(mediaItem: androidx.media3.common.MediaItem): androidx.media3.exoplayer.source.MediaSource {
                return com.mochyy.mochymusic.playback.custom.DynamicResolvingMediaSource(
                    originalMediaItem = mediaItem,
                    mediaSourceFactoryProvider = { drmManager ->
                        val factory = DefaultMediaSourceFactory(
                            createDataSourceFactory(),
                            androidx.media3.extractor.DefaultExtractorsFactory(),
                        )
                        if (drmManager != null) {
                            factory.setDrmSessionManagerProvider { drmManager }
                        }
                        factory
                    },
                    fetcherAction = { mediaId ->
                        val forced = forcedRoutes.containsKey(mediaId)
                        ((if (forced) null else deviceStream(mediaItem)) ?: routeStream(mediaId)).also { stream ->
                            if (stream != null) playingStreams[mediaId] = stream else playingStreams.remove(mediaId)
                        }
                    },
                    onSourceResolved = { mediaId, source ->
                        resolvedStreamSources[mediaId] = source
                        Handler(Looper.getMainLooper()).post {
                            if (player.currentMediaItem?.mediaId == mediaId) {
                                announceStreamSource(mediaId)
                            }
                        }
                    }
                )
            }
        }
    }

    private fun createRenderersFactory(
        eqProcessor: CustomEqualizerAudioProcessor,
        fxProcessors: Array<androidx.media3.common.audio.AudioProcessor>,
        silenceProcessor: SilenceDetectorAudioProcessor,
        hapticProcessor: HapticAudioProcessor,
        crossmixProcessor: CrossmixTransitionAudioProcessor,
        replayGain: com.mochyy.mochymusic.eq.audio.ReplayGainTags,
        spectrum: com.mochyy.mochymusic.playback.audio.AudioSpectrum,
        inspector: StreamInspector,
    ) =
        object : DefaultRenderersFactory(this) {
            // the renderer below tells it which file each song comes from, so it can tell a lossless song from a lossy one
            private var router: PassRouterSink? = null

            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = CrossmixClockSink(PassRouterSink(com.mochyy.mochymusic.playback.audio.PrecisionSink(DefaultAudioSink
                .Builder(this@MusicService)
                // float is only ever let through by PrecisionSink, while nothing it would skip is on
                .setEnableFloatOutput(true)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(

                        // Audio Lab first (equalizer, then spatial, reverb, loudness), so Crossmix
                        // blends the two songs as they're heard
                        arrayOf(
                            eqProcessor,
                            *fxProcessors,
                            silenceProcessor,
                            hapticProcessor,
                            crossmixProcessor,
                        ),
                        SilenceSkippingAudioProcessor(2_000_000, 20_000, 256),
                        SonicAudioProcessor(),
                    ),
                ).build(), audioOutputs, inspector), PassSink(audioPass), audioPass).also { router = it }, crossmixProcessor, audioFx, replayGain, spectrum)

            // media3's own audio renderer, built the same way, that hands the router each song's file format before its decoder starts
            override fun buildAudioRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: AudioSink,
                eventHandler: Handler,
                eventListener: AudioRendererEventListener,
                out: ArrayList<Renderer>,
            ) {
                val songs = router
                out.add(object : MediaCodecAudioRenderer(
                    context, codecAdapterFactory, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, audioSink,
                ) {
                    override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
                        songs?.sourceFormat = formatHolder.format
                        return super.onInputFormatChanged(formatHolder)
                    }
                })
            }
        }.apply {
            // a phone's first-choice decoder failing to start (a vendor FLAC decoder on a 24-bit
            // file, say) moves on to its next one instead of failing the song
            setEnableDecoderFallback(true)
        }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val historyDurationMs = dataStore[HistoryDuration]?.times(1000f) ?: 30000f

        if (playbackStats.totalPlayTimeMs >= historyDurationMs &&
            !dataStore.get(PauseListenHistoryKey, false)
        ) {
            database.query {
                incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playbackStats.totalPlayTimeMs,
                        ),
                    )
                } catch (_: SQLException) {
                }
            }
        }
    }

    private fun saveQueueToDisk() {
        if (player.mediaItemCount == 0) {
            Timber.tag(TAG).d("Skipping queue save - no media items")
            return
        }

        try {

            val persistQueue = currentQueue.toPersistQueue(
                title = queueTitle,
                items = player.mediaItems.mapNotNull { it.metadata },
                mediaItemIndex = player.currentMediaItemIndex,
                position = player.currentPosition
            )

            val persistAutomix =
                PersistQueue(
                    title = "automix",
                    items = automixItems.value.mapNotNull { it.metadata },
                    mediaItemIndex = 0,
                    position = 0,
                )

            val persistPlayerState = PersistPlayerState(
                playWhenReady = player.playWhenReady,
                repeatMode = player.repeatMode,
                shuffleModeEnabled = player.shuffleModeEnabled,
                // the user's volume, not the player's live one: that's 0 while muted, a fifth while
                // ducked and anywhere at all mid-crossfade, and coming back to it next launch left
                // the app silent until the slider was touched
                volume = playerVolume.value,
                currentPosition = player.currentPosition,
                currentMediaItemIndex = player.currentMediaItemIndex,
                playbackState = player.playbackState
            )

            scope.launch(Dispatchers.IO) {
                runCatching {
                    filesDir.resolve(PERSISTENT_QUEUE_FILE).outputStream().use { fos ->
                        ObjectOutputStream(fos).use { oos ->
                            oos.writeObject(persistQueue)
                        }
                    }
                    Timber.tag(TAG).d("Queue saved successfully")
                }.onFailure {
                    Timber.tag(TAG).e(it, "Failed to save queue")
                    reportException(it)
                }

                runCatching {
                    filesDir.resolve(PERSISTENT_AUTOMIX_FILE).outputStream().use { fos ->
                        ObjectOutputStream(fos).use { oos ->
                            oos.writeObject(persistAutomix)
                        }
                    }
                    Timber.tag(TAG).d("Automix saved successfully")
                }.onFailure {
                    Timber.tag(TAG).e(it, "Failed to save automix")
                    reportException(it)
                }

                runCatching {
                    filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).outputStream().use { fos ->
                        ObjectOutputStream(fos).use { oos ->
                            oos.writeObject(persistPlayerState)
                        }
                    }
                    Timber.tag(TAG).d("Player state saved successfully")
                }.onFailure {
                    Timber.tag(TAG).e(it, "Failed to save player state")
                    reportException(it)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during queue save operation")
            reportException(e)
        }
    }

    override fun onDestroy() {
        isRunning = false

        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        audioOutputs.release()
        audioPass.release()
        castConnectionHandler?.release()
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
        discordPresence.update(null)
        // sends the last stretch of the song that was playing while the player can still say where it stopped
        playReporter.onSongStop()
        connectivityObserver.unregister()
        callWatcher.stop()
        abandonAudioFocus()
        releaseLoudnessEnhancer()
        crossmixDirector.release()
        fadingLoudnessEnhancer?.runCatching { release() }
        fadingLoudnessEnhancer = null
        mediaSession.release()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        playerSilenceProcessors.remove(player)
        playerReplayGain.remove(player)

        player.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (dataStore.get(StopPlaybackOnTaskRemovedKey, false)) {
            player.stop()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MusicWidgetReceiver.ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_LIKE -> {
                toggleLike()
            }
            MusicWidgetReceiver.ACTION_NEXT -> {
                player.seekToNext()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_PREVIOUS -> {
                player.seekToPrevious()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_UPDATE_WIDGET -> {
                updateWidgetUI(player.isPlaying)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateWidgetUI(isPlaying: Boolean) {
        scope.launch {
            try {
                val songData = currentSong.value
                val song = songData?.song
                val songTitle = song?.title ?: getString(R.string.no_song_playing)
                val artistName = songData?.artists?.joinToString(", ") { it.name } ?: getString(R.string.tap_to_open)
                val isLiked = songData?.song?.liked == true

                widgetManager.updateWidgets(
                    title = songTitle,
                    artist = artistName,
                    artworkUri = song?.thumbnailUrl,
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    duration = if (player.duration != C.TIME_UNSET) player.duration else 0,
                    currentPosition = player.currentPosition
                )
            } catch (e: Exception) {

            }
        }
    }

    private var widgetUpdateJob: Job? = null

    private fun startWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    updateWidgetUI(true)
                }
                // the widget's progress bar moves in whole seconds anyway; more often only cost
                // frames in the app
                delay(WidgetProgressIntervalMs)
            }
        }
    }

    private fun stopWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = null
    }

    private fun shareSong() {
        val songData = currentSong.value
        val songId = songData?.song?.id ?: return

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=$songId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(shareIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    suspend fun getStreamUrl(mediaId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val playbackData = YTPlayerUtils.playerResponseForPlayback(
                    videoId = mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                ).getOrNull()
                playbackData?.streamUrl
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to get stream URL for Cast")
                null
            }
        }
    }

    private fun initializeCast() {
        if (dataStore.get(com.mochyy.mochymusic.constants.EnableGoogleCastKey, true)) {
            try {
                castConnectionHandler = CastConnectionHandler(this, scope, this)
                castConnectionHandler?.initialize()
                timber.log.Timber.d("Google Cast initialized")
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to initialize Google Cast")
            }
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            crossmixDirector.onUserSeek()
            scheduleCrossfade()
        }
        // this comes before the song change itself, the last moment the old song's real end spot is known
        val sameSong = reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION &&
            oldPosition.mediaItemIndex == newPosition.mediaItemIndex
        playReporter.onJump(oldPosition.positionMs, newPosition.positionMs, sameSong)
    }

    private fun scheduleCrossfade() {
        // Crossmix plans its own hand-overs; it cancels itself when it's off or can't run
        crossmixDirector.schedule()
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        if (crossmixMode == CrossmixMode.CROSSMIX) return
        if (!crossfadeEnabled || player.duration == C.TIME_UNSET || player.duration <= crossfadeDuration) return
        if (crossfadeGapless && isNextItemGapless()) return
        if (!player.hasNextMediaItem() && player.repeatMode != REPEAT_MODE_ONE) return

        val duration = player.duration
        val triggerTime = duration - crossfadeDuration.toLong()
        val delayMs = triggerTime - player.currentPosition
        if (delayMs <= 0) return

        val targetMediaId = player.currentMediaItem?.mediaId

        crossfadeTriggerJob = scope.launch {
            delay(delayMs)
            if (isActive && player.isPlaying && player.currentMediaItem?.mediaId == targetMediaId && !sleepTimer.pauseWhenSongEnd) {
                startCrossfade()
            }
        }
    }

    private fun isNextItemGapless(): Boolean {
        val current = player.currentMediaItem?.mediaMetadata ?: return false
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return false
        val next = player.getMediaItemAt(nextIndex).mediaMetadata
        return current.albumTitle != null && current.albumTitle == next.albumTitle
    }

    /** the plain fixed-length crossfade (Auto and Manual): two equal-power volume ramps */
    private fun startCrossfade() {
        if (isCrossfading || crossmixDirector.isTransitioning) return

        val savedRepeatMode = runBlocking { dataStore.get(RepeatModeKey, REPEAT_MODE_OFF) }
        val savedShuffleEnabled = runBlocking { dataStore.get(ShuffleModeKey, false) }

        val targetIndex = if (savedRepeatMode == REPEAT_MODE_ONE) {
            player.currentMediaItemIndex
        } else {
            player.nextMediaItemIndex
        }
        if (targetIndex == C.INDEX_UNSET) return

        secondaryPlayer = createExoPlayer()
        val secPlayer = secondaryPlayer!!
        secPlayer.addListener(secondaryPlayerListener)

        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        secPlayer.setMediaItems(items)
        copyPlayOrder(from = player, to = secPlayer)
        secPlayer.playbackParameters = player.playbackParameters
        secPlayer.seekTo(targetIndex, 0L)
        secPlayer.volume = 0f
        secPlayer.repeatMode = savedRepeatMode
        secPlayer.shuffleModeEnabled = savedShuffleEnabled
        secPlayer.prepare()
        secPlayer.playWhenReady = true

        performCrossfadeSwap()
    }

    /**
     * Gives a player taking over the same shuffle order as the one handing over, so the queue the
     * user sees carries on as it was. Each crossfade used to reshuffle everything from the new
     * song, which brought songs that had already played back into the queue and changed what
     * "next song" showed after every song.
     */
    private fun copyPlayOrder(from: Player, to: ExoPlayer) {
        val timeline = from.currentTimeline
        if (timeline.isEmpty || timeline.windowCount != to.mediaItemCount) return
        val order = ArrayList<Int>(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += index
            index = timeline.getNextWindowIndex(index, REPEAT_MODE_OFF, true)
        }
        if (order.size != timeline.windowCount) return
        to.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.currentTimeMillis()))
    }

    private fun performCrossfadeSwap() {
        isCrossfading = true
        val nextPlayer = secondaryPlayer ?: return
        val currentPlayer = player

        fadingPlayer = currentPlayer
        player = nextPlayer
        _playerFlow.value = player
        secondaryPlayer = null

        fadingPlayer?.removeListener(this)
        fadingPlayer?.removeListener(sleepTimer)
        // The old player has the whole queue too. If the new song took a moment to load, the fade
        // waited while the old one finished its song and rolled on into the next one - the same
        // song the new player was starting - so it played twice over itself.
        fadingPlayer?.pauseAtEndOfMediaItems = true

        nextPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val out = fadingPlayer
                if (!isCrossfading || out == null) {
                    nextPlayer.removeListener(this)
                    return
                }
                // pause and resume both sides together; an old song that has played out stays put
                if (isPlaying) {
                    val finished = out.playbackState == Player.STATE_ENDED ||
                        (out.duration > 0 && out.currentPosition >= out.duration - 250)
                    if (!finished) out.play()
                } else {
                    out.pause()
                }
            }
        })

        nextPlayer.removeListener(secondaryPlayerListener)
        nextPlayer.addListener(this)
        nextPlayer.addListener(sleepTimer)

        sleepTimer.player = player

        try {
            (mediaSession as MediaSession).player = TransportFadingPlayer(player, transport, audioPass)
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to swap player in MediaSession")
        }

        crossfadeJob = scope.launch {
            val duration = crossfadeDuration.toLong()
            val steps = 20
            val stepTime = duration / steps
            val startVolume = try { fadingPlayer?.volume ?: 1f } catch(e:Exception) { 1f }

            for (i in 0..steps) {
                if (!isActive) break

                while (!player.isPlaying && isActive) {
                    delay(100)
                }

                val progress = i / steps.toFloat()
                val fadeIn = kotlin.math.sin(progress * Math.PI / 2.0).toFloat()
                val fadeOut = kotlin.math.cos(progress * Math.PI / 2.0).toFloat()

                try {
                    // the new song fades up to the volume it should be at now: started from the old
                    // one's, a fade begun while muted or ducked ended at 0 or a fifth, and stayed
                    player.volume = targetVolume() * fadeIn
                    fadingPlayer?.volume = startVolume * fadeOut
                } catch (e: Exception) { break }

                delay(stepTime)
            }

            try {
                fadingPlayer?.volume = 0f
                player.volume = targetVolume()
                cleanupCrossfade()
            } catch (e: Exception) { }
        }
    }

    private fun cleanupCrossfade() {
        fadingPlayer?.stop()
        fadingPlayer?.clearMediaItems()
        fadingPlayer?.release()
        fadingPlayer?.let(playerCrossmixProcessors::remove)
        fadingPlayer?.let(playerSilenceProcessors::remove)
        fadingPlayer?.let(::unregisterEqualizer)
        fadingPlayer = null
        fadingLoudnessEnhancer?.runCatching { release() }
        fadingLoudnessEnhancer = null
        isCrossfading = false
        sleepTimer.notifySongTransition()
    }

    private fun unregisterEqualizer(player: Player) {
        playerEqProcessors.remove(player)?.let { equalizerService.removeAudioProcessor(it) }
    }

    /**
     * The decoder's format for [mediaId]. A module's own codec name stands (it knows its encode
     * better than the container says: HE-AAC reads as plain AAC there); otherwise the decoder's
     * format is the whole story, YouTube's record filling in what it leaves out.
     */
    private fun recordDecodedFormat(mediaId: String, format: androidx.media3.common.Format) {
        val codec = StreamFacts.codecOf(format.sampleMimeType, format.codecs)
        val sampleRate = format.sampleRate.takeIf { it > 0 }
        streamFacts.update { all ->
            val old = all[mediaId]
            val merged = if (old?.fromModule == true) {
                old.copy(codec = old.codec ?: codec, sampleRate = sampleRate ?: old.sampleRate)
            } else StreamFacts(codec = codec, sampleRate = sampleRate)
            // HE-AAC's container can give its core's rate; the decoder plays it at twice that
            val facts = if (merged.codec == "HE-AAC" && (merged.sampleRate ?: 0) in 1..24_000) {
                merged.copy(sampleRate = merged.sampleRate!! * 2)
            } else merged
            if (facts == old) all else all + (mediaId to facts)
        }
    }

    /** [mediaId] goes to YouTube: whatever a module said about it last time no longer holds */
    private fun forgetModuleFacts(mediaId: String) {
        streamFacts.update { all -> if (all[mediaId]?.fromModule == true) all - mediaId else all }
    }

    /**
     * Where [mediaId] plays from: the first provider in the order (Settings → Audio Quality) that
     * is switched on, hasn't already failed on this song, and has it. Null hands it to YouTube.
     * With every provider spent, it throws, so the song is skipped rather than retried forever.
     */
    /**
     * The providers that failed on [mediaId] lately. They're forgotten after [FailedSourceMemoryMs]:
     * remembered for good, one slow answer from Monochrome sent the song to YouTube every time
     * after, until the app was closed.
     */
    private fun recentFailures(mediaId: String): Set<String> {
        val at = failedSourcesAt[mediaId]
        if (at != null && System.currentTimeMillis() - at > FailedSourceMemoryMs) {
            failedSources.remove(mediaId)
            failedSourcesAt.remove(mediaId)
        }
        return failedSources[mediaId].orEmpty()
    }

    /**
     * The song's own file, or its download, when it has one - so it never goes looking for a copy
     * online. Every song used to be routed through the source modules, which find songs by title
     * and artist: with a module ahead of YouTube in the order, an imported file or a downloaded
     * song played that module's online stream instead, and otherwise it was announced as playing
     * from YouTube Music.
     */
    private suspend fun deviceStream(mediaItem: androidx.media3.common.MediaItem): com.mochyy.mochymusic.playback.custom.CustomStreamResult? {
        val mediaId = mediaItem.mediaId
        val uri = mediaItem.localConfiguration?.uri
        val file = when {
            uri?.scheme == "content" || uri?.scheme == "file" -> uri.toString()
            // an imported file queued from somewhere that only had its id - a search result, a
            // pinned tile, a radio - would otherwise be looked up on YouTube by that id
            mediaId.startsWith("LOCAL_") -> database.song(mediaId).first()?.song?.localUri
            else -> null
        }
        if (file != null) {
            forgetModuleFacts(mediaId)
            notePlayingFrom(mediaId, PlayingFrom(PlayingFrom.Device, "This device"))
            // announced as "Playing from this device"
            return com.mochyy.mochymusic.playback.custom.CustomStreamResult(streamUrl = file, source = "this device")
        }
        if (isFullyDownloaded(mediaId)) {
            forgetModuleFacts(mediaId)
            notePlayingFrom(mediaId, PlayingFrom(PlayingFrom.Downloads, "Your downloads"))
            // the same item: the data source finds the download under its media id
            return com.mochyy.mochymusic.playback.custom.CustomStreamResult(
                streamUrl = uri?.toString() ?: mediaId,
                source = "your downloads",
            )
        }
        return null
    }

    private suspend fun routeStream(mediaId: String): com.mochyy.mochymusic.playback.custom.CustomStreamResult? {
        // offline: no provider is asked; the song plays if it's cached, and is skipped if not.
        // An imported file is never looked for online, even one whose file has gone missing.
        if (com.mochyy.mochymusic.utils.OfflineMode.isOn || mediaId.startsWith("LOCAL_")) return null
        forcedRoutes[mediaId]?.let { forced ->
            if (forced.providerId == com.mochyy.mochymusic.modules.SourceModules.YouTubeId) return youTubeRoute(mediaId)
            forcedModuleStream(mediaId, forced)?.let { return it }
            // the picked provider doesn't have it any more: the usual order takes over
            forcedRoutes.remove(mediaId)
        }
        val skip = recentFailures(mediaId)
        // a fresh lookup: a notice from one that never got to play no longer holds
        if (skip.isEmpty()) decoderNotices.remove(mediaId)
        // songs that only YouTube has (and the common case of YouTube first) need no lookup
        val first = sourceModules.order.value.firstOrNull { it !in skip && sourceModules.isUsable(it) }
        if (first == com.mochyy.mochymusic.modules.SourceModules.YouTubeId) return youTubeRoute(mediaId)
        val track = trackQueryOf(mediaId)
        val route = sourceModules.route(track, skip) { id, passed ->
            if (!decoderNotices.containsKey(mediaId)) noteMissingDecoder(mediaId, passed.codec?.uppercase(), passed.sampleRateHz, from = id)
        }
        return when (route) {
            is com.mochyy.mochymusic.modules.SourceModules.Route.Module -> moduleStream(mediaId, route.module, route.stream)
            com.mochyy.mochymusic.modules.SourceModules.Route.YouTube -> youTubeRoute(mediaId)
            com.mochyy.mochymusic.modules.SourceModules.Route.Exhausted -> {
                val youTube = com.mochyy.mochymusic.modules.SourceModules.YouTubeId
                when {
                    // YouTube is in the order after providers that don't have it: it plays there
                    youTube !in skip && sourceModules.isUsable(youTube) -> youTubeRoute(mediaId)
                    // the user turned YouTube off to hear only their other providers
                    !sourceModules.isEnabled(youTube) ->
                        throw com.mochyy.mochymusic.playback.custom.NoProviderException("none of your providers have it, and YouTube Music is off")
                    else -> throw com.mochyy.mochymusic.playback.custom.NoProviderException("every provider failed on it")
                }
            }
        }
    }

    /** null hands the song to YouTube, which is all YouTube needs */
    private fun youTubeRoute(mediaId: String): com.mochyy.mochymusic.playback.custom.CustomStreamResult? {
        val youTube = com.mochyy.mochymusic.modules.SourceModules.YouTubeId
        resolvedSourceIds[mediaId] = youTube
        forgetModuleFacts(mediaId)
        notePlayingFrom(mediaId, PlayingFrom(youTube, "YouTube Music"))
        return null
    }

    private fun moduleStream(
        mediaId: String,
        module: com.mochyy.mochymusic.module.SourceModule,
        stream: com.mochyy.mochymusic.module.SourceStream,
    ): com.mochyy.mochymusic.playback.custom.CustomStreamResult {
        resolvedSourceIds[mediaId] = module.info.id
        val cacheKey = "$ModuleCacheKeyPrefix${module.info.id}|$mediaId"
        moduleStreamHeaders[cacheKey] = stream.headers
        streamFacts.update { it + (mediaId to StreamFacts(codec = stream.codec, fromModule = true)) }
        notePlayingFrom(mediaId, PlayingFrom(module.info.id, module.info.name, stream.qualityLabel, stream.matched.id))
        Timber.tag("StreamFetch").i("$mediaId plays from ${module.info.name}: ${stream.matched.title} · ${stream.qualityLabel}")
        return com.mochyy.mochymusic.playback.custom.CustomStreamResult(
            streamUrl = stream.url,
            isHls = stream.format == com.mochyy.mochymusic.module.StreamFormat.Hls,
            isDash = stream.format == com.mochyy.mochymusic.module.StreamFormat.Dash,
            source = "${module.info.name} · ${stream.qualityLabel}",
            widevineLicense = (stream.drm as? com.mochyy.mochymusic.module.StreamDrm.Widevine)?.license,
            cacheKey = cacheKey,
        )
    }

    private fun notePlayingFrom(mediaId: String, from: PlayingFrom) {
        _playingFrom.update { if (it[mediaId] == from) it else it + (mediaId to from) }
    }

    /** the song as a module looks for it */
    private suspend fun trackQueryOf(mediaId: String): com.mochyy.mochymusic.module.TrackQuery? =
        database.song(mediaId).first()?.let {
            com.mochyy.mochymusic.module.TrackQuery(
                title = it.song.title,
                artists = it.artists.map { a -> a.name },
                album = it.song.albumName ?: it.album?.title,
                durationMs = it.song.duration.takeIf { d -> d > 0 }?.times(1000L),
            )
        }

    /** one ask of [module] for the song, passing over a stream this phone has no decoder for */
    private suspend fun resolveWith(module: com.mochyy.mochymusic.module.SourceModule, mediaId: String): com.mochyy.mochymusic.module.SourceStream? {
        val track = trackQueryOf(mediaId) ?: return null
        val result = module.resolve(track, sourceModules.picks(module.info.id))
        return (result as? com.mochyy.mochymusic.module.ResolveResult.Found)?.stream
            ?.takeIf { com.mochyy.mochymusic.modules.DeviceDecoders.canPlay(it) }
    }

    // the stream the switch already found is used once; a later replay asks the provider again
    private suspend fun forcedModuleStream(mediaId: String, forced: ForcedRoute): com.mochyy.mochymusic.playback.custom.CustomStreamResult? {
        val module = sourceModules.module(forced.providerId) ?: return null
        val stream = forced.freshStream?.also { forced.freshStream = null }
            ?: runCatching { resolveWith(module, mediaId) }.getOrNull()
            ?: return null
        return moduleStream(mediaId, module, stream)
    }

    private inner class ProviderSwitchHost : ProviderSwitch.Host {
        override fun currentMediaId(): String? = player.currentMediaItem?.mediaId?.takeIf { !it.startsWith("LOCAL_") }

        override fun holdPlayback(): Boolean {
            val wasPlaying = player.playWhenReady
            player.pause()
            return wasPlaying
        }

        override fun resumePlayback() = player.play()

        override suspend fun resolve(providerId: String, mediaId: String): ForcedRoute? = withContext(Dispatchers.IO) {
            if (providerId == com.mochyy.mochymusic.modules.SourceModules.YouTubeId) {
                YTPlayerUtils.playerResponseForPlayback(mediaId, null, audioQuality, connectivityManager).getOrNull()
                    ?: return@withContext null
                return@withContext ForcedRoute(providerId, null)
            }
            val module = sourceModules.module(providerId) ?: return@withContext null
            resolveWith(module, mediaId)?.let { ForcedRoute(providerId, it) }
        }

        override fun switchTo(mediaId: String, route: ForcedRoute, play: Boolean) {
            forcedRoutes[mediaId] = route
            failedSources.remove(mediaId)
            failedSourcesAt.remove(mediaId)
            val index = player.currentMediaItemIndex
            val position = player.currentPosition
            // stopping releases the song's source; preparing again fetches it, now from the picked provider
            player.stop()
            player.prepare()
            player.seekTo(index, position)
            player.playWhenReady = play
        }
    }

    /**
     * The next song in play order (shuffle and repeat-all included) that plays without a
     * connection, or [C.INDEX_UNSET] when none does.
     */
    private fun nextOfflineIndex(): Int {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return C.INDEX_UNSET
        // repeat-one would only ever give back the song that just failed
        val repeatMode = if (player.repeatMode == REPEAT_MODE_ONE) REPEAT_MODE_OFF else player.repeatMode
        var index = player.currentMediaItemIndex
        repeat(timeline.windowCount) {
            index = timeline.getNextWindowIndex(index, repeatMode, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET || index == player.currentMediaItemIndex) return C.INDEX_UNSET
            if (playsOffline(player.getMediaItemAt(index))) return index
        }
        return C.INDEX_UNSET
    }

    /** An imported file, a download, or a song the player has cached whole. */
    private fun playsOffline(item: androidx.media3.common.MediaItem): Boolean {
        val scheme = item.localConfiguration?.uri?.scheme
        return scheme == "content" || scheme == "file" || item.mediaId.startsWith("LOCAL_") ||
            isFullyDownloaded(item.mediaId) || isFullyCached(item.mediaId)
    }

    /** every byte of the song is in the player's own cache, from having played it */
    private fun isFullyCached(mediaId: String): Boolean = runCatching {
        val length = androidx.media3.datasource.cache.ContentMetadata.getContentLength(
            playerCache.getContentMetadata(mediaId)
        )
        length > 0 && playerCache.isCached(mediaId, 0, length)
    }.getOrDefault(false)

    /** every byte of the song is in the download cache */
    private fun isFullyDownloaded(mediaId: String): Boolean = runCatching {
        val length = androidx.media3.datasource.cache.ContentMetadata.getContentLength(
            downloadCache.getContentMetadata(mediaId)
        )
        length > 0 && downloadCache.isCached(mediaId, 0, length)
    }.getOrDefault(false)

    // what the pause, play and skip fades need from the service
    private inner class TransportHost : TransportTransitions.Host {
        override val player: ExoPlayer get() = this@MusicService.player

        override fun processorFor(player: ExoPlayer): CrossmixTransitionAudioProcessor? = playerCrossmixProcessors[player]

        override fun targetVolume(): Float = this@MusicService.targetVolume()

        // not while casting, mid-crossmix or mid-crossfade, which all drive the volume themselves
        override fun canFade(): Boolean =
            castConnectionHandler?.isCasting?.value != true && !crossmixDirector.isTransitioning && fadingPlayer == null

        override fun beatMsFor(mediaId: String?): Double? =
            mediaId?.let { crossmixAnalysisRepository.cached(it)?.bpm }?.takeIf { it in 40f..240f }?.let { 60_000.0 / it }
    }

    /** what the Crossmix director needs from the service: players, the session, the queue */
    private inner class CrossmixHost : CrossmixDirector.Host {
        override val currentPlayer: ExoPlayer get() = player

        override fun processorFor(player: Player): CrossmixTransitionAudioProcessor? = playerCrossmixProcessors[player]

        override fun createIncoming(index: Int, positionMs: Long, parameters: PlaybackParameters): ExoPlayer {
            val incoming = createExoPlayer(publish = false)
            val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
            incoming.setMediaItems(items)
            copyPlayOrder(from = player, to = incoming)
            incoming.repeatMode = player.repeatMode
            incoming.shuffleModeEnabled = player.shuffleModeEnabled
            // the tempo is set before anything plays: changing it later drains and resets the
            // audio chain, which would cut into the transition
            incoming.playbackParameters = parameters
            incoming.volume = if (isMuted.value) 0f else playerVolume.value
            incoming.seekTo(index, positionMs)
            incoming.playWhenReady = false
            // not prepared here: the director arms its renderer first, so not even the audio it
            // buffers while paused gets through untouched
            return incoming
        }

        override fun promote(incoming: ExoPlayer, outgoing: ExoPlayer) {
            fadingPlayer = outgoing
            player = incoming
            _playerFlow.value = incoming
            // set when it was made, seconds ago: a mute, an unmute or a slider move since then
            // never reached it, and the song carried on at the old volume (0, if it was muted)
            incoming.volume = targetVolume()

            outgoing.removeListener(this@MusicService)
            outgoing.removeListener(sleepTimer)
            // the outgoing player must never roll on into the next song itself
            outgoing.pauseAtEndOfMediaItems = true

            incoming.addListener(this@MusicService)
            incoming.addListener(sleepTimer)
            sleepTimer.player = incoming
            incoming.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val out = fadingPlayer
                    if (!crossmixDirector.isTransitioning || out == null) {
                        incoming.removeListener(this)
                        return
                    }
                    // pause and resume both sides together; an outgoing side that has played out stays put
                    if (isPlaying) {
                        val finished = out.playbackState == Player.STATE_ENDED ||
                            (out.duration > 0 && out.currentPosition >= out.duration - 250)
                        if (!finished) out.play()
                    } else {
                        out.pause()
                    }
                }
            })

            try {
                (mediaSession as MediaSession).player = TransportFadingPlayer(incoming, transport, audioPass)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to swap player in MediaSession")
            }

            // the hooks a song change runs (loudness, scrobbling, queue top-up, saving the queue)
            // for the song now playing, which this player reached by a seek before it was ours
            currentMediaMetadata.value = incoming.currentMetadata
            promotingCrossmix = true
            try {
                onMediaItemTransition(incoming.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            } finally {
                promotingCrossmix = false
            }
        }

        override fun release(outgoing: ExoPlayer) {
            runCatching {
                outgoing.stop()
                outgoing.clearMediaItems()
                outgoing.release()
            }
            playerCrossmixProcessors.remove(outgoing)
            playerSilenceProcessors.remove(outgoing)
            playerReplayGain.remove(outgoing)
            unregisterEqualizer(outgoing)
            if (fadingPlayer === outgoing) fadingPlayer = null
            fadingLoudnessEnhancer?.runCatching { release() }
            fadingLoudnessEnhancer = null
            sleepTimer.notifySongTransition()
        }

        override fun discard(incoming: ExoPlayer) {
            runCatching {
                incoming.stop()
                incoming.release()
            }
            playerCrossmixProcessors.remove(incoming)
            playerSilenceProcessors.remove(incoming)
            playerReplayGain.remove(incoming)
            unregisterEqualizer(incoming)
        }

        override fun crossmixBlocker(): String? = when {
            !crossfadeEnabled -> "Crossfade is off"
            crossmixMode != CrossmixMode.CROSSMIX -> "Crossfade is set to ${crossmixMode.name.lowercase()}, not Crossmix"
            isCrossfading -> "A plain crossfade is running"
            castConnectionHandler?.isCasting?.value == true -> "Casting: Crossmix waits until you're back"
            sleepTimer.pauseWhenSongEnd -> "The sleep timer stops after this song"
            else -> null
        }

        // with gapless albums on, songs from one album are checked for running into each other;
        // the director only plays them straight through when their audio really does
        override fun albumOf(index: Int): String? {
            if (!crossfadeGapless || index !in 0 until player.mediaItemCount) return null
            return player.getMediaItemAt(index).mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
        }

        override fun onStatus(status: String) {
            crossmixStatus.value = status
        }

        override fun nextIndex(): Int =
            if (player.repeatMode == REPEAT_MODE_ONE) player.currentMediaItemIndex else player.nextMediaItemIndex

        // the next song's lyrics usually aren't fetched yet: fetch them now (and keep them, as the
        // player would on opening it), since synced lines say exactly where its voice comes in
        override suspend fun lyrics(item: MediaItem): String? = withContext(Dispatchers.IO) {
            val stored = runCatching { database.lyrics(item.mediaId).first()?.lyrics }.getOrNull()
            if (stored != null) return@withContext stored.takeUnless { it == LyricsEntity.LYRICS_NOT_FOUND }
            val metadata = item.metadata ?: return@withContext null
            val found = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                runCatching { lyricsHelper.getLyrics(metadata) }.getOrNull()
            } ?: return@withContext null
            if (found.provider == com.mochyy.mochymusic.lyrics.LyricsHelper.NoConnectionProvider) return@withContext null
            runCatching {
                database.query { upsert(LyricsEntity(id = metadata.id, lyrics = found.lyrics, provider = found.provider)) }
            }
            found.lyrics.takeUnless { it == LyricsEntity.LYRICS_NOT_FOUND }
        }

        override fun baseParameters(): PlaybackParameters = player.playbackParameters

        override fun onMixingChanged(mixing: Boolean) {
            crossmixMixing.value = mixing
        }
    }

    companion object {
        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val YOUTUBE_PLAYLIST = "youtube_playlist"
        const val SEARCH = "search"
        const val SHUFFLE_ACTION = "__shuffle__"

        const val CHANNEL_ID = "music_channel_01"
        const val ERROR_CHANNEL_ID = "playback_errors_01"
        const val ERROR_NOTIFICATION_ID = 889
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L

        /**
         * Starts the cache key of a stream a source module resolved: "module|<module id>|<media id>".
         * Its own key keeps it apart from the song's YouTube stream, which is cached under the bare
         * media id - see the data source factory.
         */
        const val ModuleCacheKeyPrefix = "module|"

        /** how long a provider that failed on a song is passed over for it (Force playback) */
        const val FailedSourceMemoryMs = 10L * 60 * 1000
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        const val MAX_CONSECUTIVE_ERR = 5
        // Keep at least this many songs queued after the current one.
        private const val QUEUE_RUNWAY_SIZE = 5
        private const val MAX_RUNWAY_ATTEMPTS = 3
        private const val AUTOPLAY_PREVIEW_RETRY_MS = 60_000L
        private const val MAX_RUNWAY_RETRIES = 5
        private const val RUNWAY_RETRY_BASE_MS = 5_000L
        const val MAX_RETRY_COUNT = 10

        private const val MAX_GAIN_MB = 300
        private const val MIN_GAIN_MB = -1500

        private const val TAG = "MusicService"

        @Volatile
        var isRunning = false
            private set
    }
}

/** How often a placed home screen widget's progress is redrawn while playing. */
private const val WidgetProgressIntervalMs = 1_000L

// the passthrough switch, its mode and whether anything shapes the sound, compared as one so audio pass hears each change once
private data class PassSettings(val enabled: Boolean, val mode: AudioPassMode, val blocked: Boolean, val fixedVolume: Boolean)

// reads a restore point from an older version even if fields were added since
private val passJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
