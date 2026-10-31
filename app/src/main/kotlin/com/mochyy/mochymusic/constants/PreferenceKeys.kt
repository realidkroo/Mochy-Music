// PreferenceKeys.kt

package com.mochyy.mochymusic.constants

import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset

import com.music.innertube.models.IpVersion

val IsFirstRunKey = booleanPreferencesKey("isFirstRun")
val EnableDynamicIconKey = booleanPreferencesKey("enableDynamicIcon")
val EnableHighRefreshRateKey = booleanPreferencesKey("enableHighRefreshRate")
val DynamicThemeKey = booleanPreferencesKey("dynamicTheme")
val SelectedThemeColorKey = intPreferencesKey("selectedThemeColor")
val DarkModeKey = stringPreferencesKey("darkMode")
val PureBlackKey = booleanPreferencesKey("pureBlack")
val PureBlackMiniPlayerKey = booleanPreferencesKey("pureBlackMiniPlayer")
val Enable26RecapKey = booleanPreferencesKey("enable26Recap")
/** Opens the monthly recap even before the 3rd or with fewer than 10 songs played (Experimental). */
val ForceMonthlyRecapKey = booleanPreferencesKey("forceMonthlyRecap")
/** Shows Crossmix's live status and a "Test on this song" button (Experimental). */
val CrossmixTestToolsKey = booleanPreferencesKey("crossmixTestTools")
val MiniPlayerOutlineKey = booleanPreferencesKey("miniPlayerOutline")
val DensityScaleKey = floatPreferencesKey("density_scale_factor")
val CustomDensityScaleKey = floatPreferencesKey("custom_density_scale_value")

enum class DensityScale(val value: Float, val label: String) {
    NATIVE(1.0f, "Native (100%)"),
    SLIGHTLY_COMPACT(0.85f, "Slightly Compact (85%)"),
    COMPACT(0.75f, "Compact (75%)"),
    VERY_COMPACT(0.65f, "Very Compact (65%)"),
    ULTRA_COMPACT(0.55f, "Ultra Compact (55%)");

    companion object {
        fun fromValue(value: Float): DensityScale = entries.find { it.value == value } ?: NATIVE
    }
}

val DefaultOpenTabKey = stringPreferencesKey("defaultOpenTab")
val SlimNavBarKey = booleanPreferencesKey("slimNavBar")
val GridItemsSizeKey = stringPreferencesKey("gridItemSize")
val SliderStyleKey = stringPreferencesKey("sliderStyle")
val SquigglySliderKey = booleanPreferencesKey("squigglySlider")
val SwipeToSongKey = booleanPreferencesKey("SwipeToSong")
val SwipeToRemoveSongKey = booleanPreferencesKey("SwipeToRemoveSong")
// what sliding a song does, per side (Settings > Customisation); the names are SongSwipeAction values
val SwipeRightActionKey = stringPreferencesKey("swipeRightAction")
val SwipeLeftActionKey = stringPreferencesKey("swipeLeftAction")

// the top layout of the home tab, picked in customisation settings
enum class HomeStyle(val displayName: String) {
    MOCHYY("Mochyy"),
    LISTEN_NOW("Listen now"),
    HOP_IN("Hop in"),
}

val HomeStyleKey = stringPreferencesKey("homeStyle")

// how playlist and album headers are laid out at the top of the page
enum class PlaylistStyle(val displayName: String) {
    FLOW("Flow"),
    CLASSIC("Classic"),
    MINIMAL("Minimal"),
}

val PlaylistStyleKey = stringPreferencesKey("playlistStyle")
val UseNewPlayerDesignKey= booleanPreferencesKey("useNewPlayerDesign")
val UseNewMiniPlayerDesignKey = booleanPreferencesKey("useNewMiniPlayerDesign")
val AppleMusicDarkChromeKey = booleanPreferencesKey("appleMusicDarkChrome")

val HidePlayerThumbnailKey = booleanPreferencesKey("hidePlayerThumbnail")
val ThumbnailCornerRadiusKey = floatPreferencesKey("thumbnailCornerRadius")
val CropAlbumArtKey = booleanPreferencesKey("cropAlbumArt")
val SeekExtraSeconds = booleanPreferencesKey("seekExtraSeconds")
val PauseOnMute = booleanPreferencesKey("pauseOnMute")
// keep playing when another app makes sound, and don't pause that app either
val MixWithOtherAppsKey = booleanPreferencesKey("mixWithOtherApps")
val ResumeOnBluetoothConnectKey = booleanPreferencesKey("resumeOnBluetoothConnect")
val StopPlaybackOnTaskRemovedKey = booleanPreferencesKey("stopPlaybackOnTaskRemoved")
val KeepScreenOn = booleanPreferencesKey("keepScreenOn")
// a swipe down anywhere on the open player closes it, not only from its top and cover (#121)
val PlayerSwipeDownAnywhereKey = booleanPreferencesKey("playerSwipeDownAnywhere")
val DeveloperModeKey = booleanPreferencesKey("developerMode")

enum class SliderStyle {
    DEFAULT,
    WAVY,
    SLIM
}

const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
val AppLanguageKey = stringPreferencesKey("appLanguage")
val ContentLanguageKey = stringPreferencesKey("contentLanguage")
val ContentCountryKey = stringPreferencesKey("contentCountry")
val SuggestionRegionKey = stringPreferencesKey("suggestionRegion")
val EnableKugouKey = booleanPreferencesKey("enableKugou")
val EnableLrcLibKey = booleanPreferencesKey("enableLrclib")
val EnableBetterLyricsKey = booleanPreferencesKey("enableBetterLyrics")
val EnableSimpMusicKey = booleanPreferencesKey("enableSimpMusic")
val EnableYouLyPlusKey = booleanPreferencesKey("enableYouLyPlus")
val EnablePaxsenixKey = booleanPreferencesKey("enablePaxsenix")
val EnableNetEaseKey = booleanPreferencesKey("enableNetEase")
val EnableQQMusicKey = booleanPreferencesKey("enableQQMusic")
val EnableDeezerKey = booleanPreferencesKey("enableDeezer")
val HideExplicitKey = booleanPreferencesKey("hideExplicit")
val HideVideoSongsKey = booleanPreferencesKey("hideVideoSongs")
val HideYoutubeShortsKey = booleanPreferencesKey("hideYoutubeShorts")
val ShowArtistDescriptionKey = booleanPreferencesKey("showArtistDescription")
val ShowArtistSubscriberCountKey = booleanPreferencesKey("showArtistSubscriberCount")
val ShowMonthlyListenersKey = booleanPreferencesKey("showMonthlyListeners")
val ShowArtistVideoKey = booleanPreferencesKey("showArtistVideo")
val ShowArtistBackgroundVideoKey = booleanPreferencesKey("showArtistBackgroundVideo")
val ProxyEnabledKey = booleanPreferencesKey("proxyEnabled")
val ProxyUrlKey = stringPreferencesKey("proxyUrl")
val ProxyTypeKey = stringPreferencesKey("proxyType")
val ProxyUsernameKey = stringPreferencesKey("proxyUsername")
val ProxyPasswordKey = stringPreferencesKey("proxyPassword")
val YtmSyncKey = booleanPreferencesKey("ytmSync")
val SelectedYtmPlaylistsKey = stringPreferencesKey("selectedYtmPlaylists")

// Import & sync (Experimental)
/** New playlists start with "Sync with YouTube" switched on in the create dialog. */
val YtSyncNewPlaylistsKey = booleanPreferencesKey("ytSyncNewPlaylists")
/** Plays long enough to count go to the signed-in account's YouTube history. */
val YtReportPlaysKey = booleanPreferencesKey("ytReportPlays")
/** YouTube Music history is copied into Mochy's own listening history. */
val YtPullHistoryKey = booleanPreferencesKey("ytPullHistory")
/** Newest YouTube history entry already copied, so the next pull stops there. */
val YtHistoryLastSeenKey = stringPreferencesKey("ytHistoryLastSeen")
/** Whether YouTube sync keeps Liked music in step (onboarding's "What to sync" can leave it out). */
val YtSyncLikedSongsKey = booleanPreferencesKey("ytSyncLikedSongs")
/**
 * Set after the first full sync that marked what YouTube brought in. Syncs before that never
 * marked anything, so that one sync marks everything YouTube has, not just what is new.
 */
val YtSyncSourcesTaggedKey = booleanPreferencesKey("ytSyncSourcesTagged")
/** Songs and playlists that came from other services stay on this phone, nothing goes up to YouTube for them. */
val YtExcludeImportsKey = booleanPreferencesKey("ytExcludeImports")
/** Set by an undo: YouTube pulls wait until the user runs Sync now, so the undo isn't written back. */
val YtSyncHoldKey = booleanPreferencesKey("ytSyncHold")

/** The profile ID card's "USR#" number and join date (epoch day), fixed when onboarding finishes. */
val ProfileCardNumberKey = stringPreferencesKey("profileCardNumber")
val ProfileJoinedEpochDayKey = longPreferencesKey("profileJoinedEpochDay")

val AudioQualityKey = stringPreferencesKey("audioQuality")
val IpVersionKey = stringPreferencesKey("ipVersion")

enum class AudioQuality {
    /** Best available from whichever backend is active. */
    AUTO,

    // YouTube tiers
    LOW,
    MEDIUM,
    HIGH,

    // Monochrome (TIDAL-backed) tiers
    LOSSLESS,
    HI_RES_LOSSLESS,
    DOLBY_ATMOS;

    /** Tiers only the Monochrome backend can serve; YouTube has no equivalent. */
    val usesMonochrome: Boolean
        get() = this == LOSSLESS || this == HI_RES_LOSSLESS || this == DOLBY_ATMOS
}

/**
 * What songs are downloaded as, apart from what they stream as. Lossless is Monochrome's FLAC, for
 * the songs Monochrome has; the rest come from YouTube at High.
 */
val DownloadQualityKey = stringPreferencesKey("downloadQuality")

enum class DownloadQuality(val title: String, val description: String) {
    LOSSLESS("Lossless", "FLAC from Monochrome, CD quality or better. Songs it doesn't have download at High"),
    HIGH("High", "The best YouTube has: ~160 kbps Opus"),
    MEDIUM("Medium", "Around 128 kbps, half the space"),
    LOW("Low", "The smallest files, ~50–70 kbps"),
}

val AudioOffload = booleanPreferencesKey("enableOffload")

// where the sound goes: a plugged-in usb dac first, its own rate (or bit-perfect) on android 14+, 24-bit kept as float
val PreferUsbDacKey = booleanPreferencesKey("preferUsbDac")
val UsbPassthroughKey = booleanPreferencesKey("usbDacPassthrough")
val HiResOutputKey = booleanPreferencesKey("hiResFloatOutput")
val HiFiResamplerKey = booleanPreferencesKey("hiFiResampler")
val AudioPassModeKey = stringPreferencesKey("audioPassMode")
// lets the direct usb driver play a dac that has no volume of its own, at full scale
val AudioPassFixedVolumeKey = booleanPreferencesKey("audioPassFixedVolume")
// what "turn these off" on dac's stuff switched off, as json, until "turn these back on" puts it back
val AudioPassRestoreKey = stringPreferencesKey("audioPassRestore")

/** how the passthrough switch reaches the dac; minSdk is the android each one needs */
enum class AudioPassMode(val minSdk: Int) {
    SONG_RATE(34),
    BIT_PERFECT(34),
    DIRECT_USB(24),
    EXCLUSIVE_USB(27),
    EXCLUSIVE_DAC(27),
    ;

    /** whether songs go to an output only mochy plays to, instead of through android's mixer */
    val exclusive: Boolean get() = this != SONG_RATE

    companion object {
        /** android 14 brought the dac's own mixer; before it only mochy's own driver reaches the dac */
        val Default: AudioPassMode
            get() = if (android.os.Build.VERSION.SDK_INT >= 34) SONG_RATE else DIRECT_USB
    }
}

// which service the about sheet's "about" text comes from: "musixmatch" or "youtube"
val AboutSongSourceKey = stringPreferencesKey("aboutSongSource")

val PersistentQueueKey = booleanPreferencesKey("persistentQueue")
val PersistentShuffleAcrossQueuesKey = booleanPreferencesKey("persistentShuffleAcrossQueues")
val RememberShuffleAndRepeatKey = booleanPreferencesKey("rememberShuffleAndRepeat")
val ShuffleModeKey = booleanPreferencesKey("shuffleMode")
val SkipSilenceKey = booleanPreferencesKey("skipSilence")
val SkipSilenceInstantKey = booleanPreferencesKey("skipSilenceInstant")
val AudioNormalizationKey = booleanPreferencesKey("audioNormalization")
val AutoLoadMoreKey = booleanPreferencesKey("autoLoadMore")
val DisableLoadMoreWhenRepeatAllKey = booleanPreferencesKey("disableLoadMoreWhenRepeatAll")
val InfiniteQueueKey = booleanPreferencesKey("infiniteQueue")

val SetupCompletedKey = booleanPreferencesKey("setupCompleted")
val UsernameKey = stringPreferencesKey("username")
val ProfilePicUriKey = stringPreferencesKey("profilePicUri")
val AutoDownloadOnLikeKey = booleanPreferencesKey("autoDownloadOnLike")
val SimilarContent = booleanPreferencesKey("similarContent")
val DontSkipOnFailureKey = booleanPreferencesKey("dontSkipOnFailure")
/** Seconds a failed stream waits before skipping, 5..20 */
val StreamFailureTimeoutKey = intPreferencesKey("streamFailureTimeout")
val AutoSkipNextOnErrorKey = booleanPreferencesKey("autoSkipNextOnError")
val StopMusicOnTaskClearKey = booleanPreferencesKey("stopMusicOnTaskClear")
val ShufflePlaylistFirstKey = booleanPreferencesKey("shufflePlaylistFirst")
val PreventDuplicateTracksInQueueKey = booleanPreferencesKey("preventDuplicateTracksInQueue")
val CrossfadeEnabledKey = booleanPreferencesKey("crossfadeEnabled")
val CrossfadeDurationKey = floatPreferencesKey("crossfadeDuration")
val CrossfadeGaplessKey = booleanPreferencesKey("crossfadeGapless")
val CrossmixModeKey = stringPreferencesKey("crossmixMode")

/** The selected transition experience. Legacy crossfade keys remain the playback contract. */
enum class CrossmixMode {
    AUTO_CROSSFADE,
    OFF,
    MANUAL_CROSSFADE,
    CROSSMIX,
}

// crossmix is still being tuned, so it's greyed out and plays as auto crossfade; flip this to bring it back
const val CrossmixAvailable = false

/** the mode that actually plays, a saved crossmix falls back to auto crossfade while crossmix is in progress */
fun CrossmixMode.playable(): CrossmixMode =
    if (this == CrossmixMode.CROSSMIX && !CrossmixAvailable) CrossmixMode.AUTO_CROSSFADE else this

// pause, play and skip fades: on by default, 2 s long, no layered effect until one is picked
val TransportFadeEnabledKey = booleanPreferencesKey("transportFadeEnabled")
val TransportFadeSecondsKey = floatPreferencesKey("transportFadeSeconds")
val TransportEffectKey = stringPreferencesKey("transportEffect")

const val TransportFadeDefaultSeconds = 2f
const val TransportFadeMinSeconds = 0.5f
const val TransportFadeMaxSeconds = 10f

// the crossmix effect layered over a pause, play or skip fade
enum class TransportEffect(val label: String) {
    OFF("None"),
    ECHO("Echo"),
    REWIND("Rewind"),
    TAPE_STOP("Tape stop"),
    FAR_AWAY("Fade to far"),
    REVERB("Reverb wash"),
    FILTER("Filter sweep"),
}

val MaxImageCacheSizeKey = intPreferencesKey("maxImageCacheSize")
val MaxSongCacheSizeKey = intPreferencesKey("maxSongCacheSize")

val PauseListenHistoryKey = booleanPreferencesKey("pauseListenHistory")
val PauseSearchHistoryKey = booleanPreferencesKey("pauseSearchHistory")
val DisableScreenshotKey = booleanPreferencesKey("disableScreenshot")

// Discord: signed in on Discord's own page in the browser (OAuth), so Mochy only ever holds a
// token Discord gave it for this, never the account's password or login token
val DiscordAccessTokenKey = stringPreferencesKey("discordAccessToken")
val DiscordRefreshTokenKey = stringPreferencesKey("discordRefreshToken")
val DiscordTokenExpiresAtKey = longPreferencesKey("discordTokenExpiresAt")
/** the signed-in account's name, picture, banner and bio, as JSON, for the profile preview */
val DiscordProfileKey = stringPreferencesKey("discordProfile")
/** a login started in the browser, kept on disk in case Android closes Mochy meanwhile */
val DiscordLoginStateKey = stringPreferencesKey("discordLoginState")
val DiscordLoginVerifierKey = stringPreferencesKey("discordLoginVerifier")

val EnableDiscordRPCKey = booleanPreferencesKey("discordRPCEnable")
val DiscordActivityTypeKey = stringPreferencesKey("discordActivityType")
val DiscordRefreshSecondsKey = intPreferencesKey("discordPresenceRefreshSeconds")
val DiscordActivityNameKey = stringPreferencesKey("discordPresenceName")
val DiscordDetailsKey = stringPreferencesKey("discordPresenceDetails")
val DiscordStateKey = stringPreferencesKey("discordPresenceState")
val DiscordLargeTextKey = stringPreferencesKey("discordPresenceLargeText")
val DiscordSmallTextKey = stringPreferencesKey("discordPresenceSmallText")
val DiscordLargeImageKey = stringPreferencesKey("discordPresenceLargeImage")
val DiscordSmallImageKey = stringPreferencesKey("discordPresenceSmallImage")
val DiscordSpinSecondsKey = intPreferencesKey("discordPresenceSpinSeconds")
val DiscordSpinFpsKey = intPreferencesKey("discordPresenceSpinFps")
val DiscordButton1TextKey = stringPreferencesKey("discordPresenceButton1Text")
val DiscordButton1TargetKey = stringPreferencesKey("discordPresenceButton1Target")
val DiscordButton2TextKey = stringPreferencesKey("discordPresenceButton2Text")
val DiscordButton2TargetKey = stringPreferencesKey("discordPresenceButton2Target")

val EnableGoogleCastKey = booleanPreferencesKey("enableGoogleCast")

val LastFMSessionKey = stringPreferencesKey("lastfmSession")
val LastFMUsernameKey = stringPreferencesKey("lastfmUsername")
val EnableLastFMScrobblingKey = booleanPreferencesKey("lastfmScrobblingEnable")

val LibreFmSessionKey = stringPreferencesKey("librefmSession")
val LibreFmUsernameKey = stringPreferencesKey("librefmUsername")
val EnableLibreFmScrobblingKey = booleanPreferencesKey("librefmScrobblingEnable")

/** Now Playing and loves go to every signed-in scrobbling service, not only Last.fm. */
val LastFMUseNowPlaying = booleanPreferencesKey("lastfmUseNowPlaying")
val LastFMUseSendLikes = booleanPreferencesKey("lastfmUseSendLikes")

val ScrobbleDelayPercentKey = floatPreferencesKey("scrobbleDelayPercent")
val ScrobbleMinSongDurationKey = intPreferencesKey("scrobbleMinSongDuration")
val ScrobbleDelaySecondsKey = intPreferencesKey("scrobbleDelaySeconds")

val ChipSortTypeKey = stringPreferencesKey("chipSortType")
val SongSortTypeKey = stringPreferencesKey("songSortType")
val SongSortDescendingKey = booleanPreferencesKey("songSortDescending")
val PlaylistSongSortTypeKey = stringPreferencesKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = booleanPreferencesKey("playlistSongSortDescending")
val AutoPlaylistSongSortTypeKey = stringPreferencesKey("autoPlaylistSongSortType")
val AutoPlaylistSongSortDescendingKey = booleanPreferencesKey("autoPlaylistSongSortDescending")
val LikedSongSortTypeKey = stringPreferencesKey("likedSongSortType")
val LikedSongSortDescendingKey = booleanPreferencesKey("likedSongSortDescending")
val ArtistSortTypeKey = stringPreferencesKey("artistSortType")
val ArtistSortDescendingKey = booleanPreferencesKey("artistSortDescending")
val AlbumSortTypeKey = stringPreferencesKey("albumSortType")
val AlbumSortDescendingKey = booleanPreferencesKey("albumSortDescending")
val PlaylistSortTypeKey = stringPreferencesKey("playlistSortType")
val PlaylistSortDescendingKey = booleanPreferencesKey("playlistSortDescending")
val AddToPlaylistSortTypeKey = stringPreferencesKey("addToPlaylistSortType")
val AddToPlaylistSortDescendingKey = booleanPreferencesKey("addToPlaylistSortDescending")
val ArtistSongSortTypeKey = stringPreferencesKey("artistSongSortType")
val ArtistSongSortDescendingKey = booleanPreferencesKey("artistSongSortDescending")
val MixSortTypeKey = stringPreferencesKey("mixSortType")
val MixSortDescendingKey = booleanPreferencesKey("albumSortDescending")

val SongFilterKey = stringPreferencesKey("songFilter")
val ArtistFilterKey = stringPreferencesKey("artistFilter")
val AlbumFilterKey = stringPreferencesKey("albumFilter")

val LastLikeSongSyncKey = longPreferencesKey("last_like_song_sync")
val LastLibSongSyncKey = longPreferencesKey("last_library_song_sync")
val LastAlbumSyncKey = longPreferencesKey("last_album_sync")
val LastArtistSyncKey = longPreferencesKey("last_artist_sync")
val LastPlaylistSyncKey = longPreferencesKey("last_playlist_sync")
val LastFullSyncKey = longPreferencesKey("last_full_sync")

const val SYNC_COOLDOWN = 30 * 60L

val ArtistViewTypeKey = stringPreferencesKey("artistViewType")
val AlbumViewTypeKey = stringPreferencesKey("albumViewType")
val PlaylistViewTypeKey = stringPreferencesKey("playlistViewType")

val PlaylistEditLockKey = booleanPreferencesKey("playlistEditLock")
val QuickPicksKey = stringPreferencesKey("discover")
val PreferredLyricsProviderKey = stringPreferencesKey("lyricsProvider")
val LyricsProviderOrderKey = stringPreferencesKey("lyricsProviderOrder")
val QueueEditLockKey = booleanPreferencesKey("queueEditLock")
val ShowWrappedCardKey = booleanPreferencesKey("show_wrapped_card")
val WrappedSeenKey = booleanPreferencesKey("wrapped_seen")
val RandomizeHomeOrderKey = booleanPreferencesKey("randomizeHomeOrder")
val AlbumCanvasEnabledKey = booleanPreferencesKey("albumCanvasEnabled")

val UpdatePromptSnoozedAtKey = longPreferencesKey("update_prompt_snoozed_at")

val ShowStreamSourceToastKey = booleanPreferencesKey("show_stream_source_toast")
/** A song a source module or Monochrome can't play falls back to YouTube, and one nothing can play is skipped. */
val ForcePlaybackKey = booleanPreferencesKey("forcePlayback")

val ShowLikedPlaylistKey = booleanPreferencesKey("show_liked_playlist")
val ShowDownloadedPlaylistKey = booleanPreferencesKey("show_downloaded_playlist")
val ShowTopPlaylistKey = booleanPreferencesKey("show_top_playlist")
val ShowCachedPlaylistKey = booleanPreferencesKey("show_cached_playlist")
val LastPlayedLikedSongsTimeKey = longPreferencesKey("last_played_liked_songs_time")
val ShowUploadedPlaylistKey = booleanPreferencesKey("show_uploaded_playlist")
val ShowAudioQualityBadgeKey = booleanPreferencesKey("show_audio_quality_badge")
val ShowCommentButtonKey = booleanPreferencesKey("show_comment_button")
val ShowPlayerBottomCardKey = booleanPreferencesKey("showPlayerBottomCard")
val PlayVideoBackgroundKey = booleanPreferencesKey("playVideoBackground")
// a track that is a YouTube video (from a video row or the Videos search tab) shows that video in the player
val DisableVideoAutoplayKey = booleanPreferencesKey("disableVideoAutoplay")

// how sharp youtube videos play; auto drops to a lower picture when the connection slows down instead of freezing
enum class VideoQuality(val shortSide: Int) {
    AUTO(0),
    Q1080(1080),
    Q720(720),
    Q480(480),
    Q360(360),
    Q240(240),
    Q144(144),
}

val VideoQualityKey = stringPreferencesKey("videoQuality")

// the style the player goes back to when the full video mode is left
val VideoModeReturnStyleKey = stringPreferencesKey("videoModeReturnStyle")

// the full video mode shows the whole picture with room round it, instead of zooming it to fill the screen
val VideoModeWholeVideoKey = booleanPreferencesKey("videoModeWholeVideo")

val LocalSongAutoMetadataKey = booleanPreferencesKey("local_song_auto_metadata")

enum class LibraryViewType {
    LIST,
    GRID,
    ;

    fun toggle() =
        when (this) {
            LIST -> GRID
            GRID -> LIST
        }
}

enum class SongFilter {
    LIBRARY,
    LIKED,
    DOWNLOADED,
    UPLOADED
}

enum class ArtistFilter {
    LIBRARY,
    LIKED
}

enum class AlbumFilter {
    LIBRARY,
    LIKED,
    UPLOADED
}

enum class SongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class PlaylistSongSortType {
    CUSTOM,
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

/** Liked songs' own sort: the song sorts plus the order things were imported in, by service. */
enum class LikedSongSortType {
    CREATE_DATE,
    IMPORT_ORDER,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class AutoPlaylistSongSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    PLAY_TIME,
}

enum class ArtistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    PLAY_TIME,
}

enum class ArtistSongSortType {
    CREATE_DATE,
    NAME,
    PLAY_TIME,
}

enum class AlbumSortType {
    CREATE_DATE,
    NAME,
    ARTIST,
    YEAR,
    SONG_COUNT,
    LENGTH,
    PLAY_TIME,
}

enum class PlaylistSortType {
    CREATE_DATE,
    NAME,
    SONG_COUNT,
    LAST_UPDATED,
}

enum class MixSortType {
    CREATE_DATE,
    NAME,
    LAST_UPDATED,
}

enum class GridItemSize {
    BIG,
    SMALL,
}

enum class MyTopFilter {
    ALL_TIME,
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ;

    fun toTimeMillis(): Long =
        when (this) {
            DAY ->
                LocalDateTime
                    .now()
                    .minusDays(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            WEEK ->
                LocalDateTime
                    .now()
                    .minusWeeks(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            MONTH ->
                LocalDateTime
                    .now()
                    .minusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            YEAR ->
                LocalDateTime
                    .now()
                    .minusMonths(12)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            ALL_TIME -> 0
        }
}

enum class QuickPicks {
    QUICK_PICKS,
    LAST_LISTEN,
}

enum class PreferredLyricsProvider {
    LRCLIB,
    KUGOU,
    BETTER_LYRICS,
    SIMPMUSIC,
    YOULYPLUS,
    PAXSENIX,
}

enum class PlayerButtonsStyle {
    DEFAULT,
    PRIMARY,
    TERTIARY
}

enum class PlayerBackgroundStyle {

    COVER_GRADIENT,

    SOLID,

    DARK_GRADIENT,

    APPLE_MUSIC,

    // the cover's colours drifting slowly, following a playing video or animated cover as it changes
    AMBIENT,
}

enum class PlayerCoverStyle {
    EDGE_TO_EDGE,
    SQUARED,
    DISC_SMALL_FULL,
    DISC_SMALL_LABEL,
    DISC_BIG_FULL,
    DISC_BIG_LABEL,
    DISC_ALBUM,
    /** the Turntable player's own: a big record and its arm. Not offered on the flagship. */
    TURNTABLE,
}

/**
 * Whole-player presets, picked in the player selector (hold the player, or "edit player").
 *
 * The flagship [DEFAULT] can be edited, [TURNTABLE] (the flagship around a record player) has
 * its disc label and [POSTER] its visualizer; the others are complete designs of their own,
 * each of which can be turned sideways from its menu.
 */
enum class PlayerStyle(val displayName: String, val selectorName: String) {
    // the flagship as it is now: frosted cover fade, moving cover colours, the new buttons and bottom bar
    FLAGSHIP("Flagship", "Flagship"),
    // the flagship before its layout refresh, kept as a variant under it in the selector
    CLASSIC("Classic", "Classic"),
    /** the flagship with a record player for its cover, whose arm is the seek bar */
    TURNTABLE("Turntable", "Turntable"),
    // the song's youtube video across the whole screen, turned sideways, with its lyrics in a pane
    VIDEO("Full video", "Full video"),
    // the ipod classic, click wheel and all, browsing the library from inside the player
    IPOD("iPod", "iPod"),
    POSTER("Poster", "Poster"),
    TIMELINE("Timeline", "Timeline"),
    LYRICS("Lyrics", "Lyrics stage");

    val editable: Boolean get() = this == FLAGSHIP || this == CLASSIC || this == TURNTABLE || this == POSTER || this == IPOD

    /** drawn by the flagship's own player (cover, controls, card), not a design of its own */
    val flagshipFamily: Boolean get() = this == FLAGSHIP || this == CLASSIC || this == TURNTABLE

    // the two flagships the full editor works on: cover, middle part, controls and bottom bar
    val hasFullEditor: Boolean get() = this == FLAGSHIP || this == CLASSIC

    companion object {
        /** the styles these replaced, so a stored choice lands on its nearest successor */
        fun fromStored(name: String?): PlayerStyle? = when (name) {
            "SIMPLE", "SIMPLE_COMPACT" -> TIMELINE
            "LYRICS_ABSTRACT" -> LYRICS
            // everyone on the old flagship moves to the new one once; picking classic later sticks
            "DEFAULT" -> FLAGSHIP
            else -> null
        }

        // the selector's columns, each look with its variants stacked under it, a swipe down away
        val selectorColumns: List<List<PlayerStyle>> = listOf(
            listOf(FLAGSHIP, CLASSIC),
            listOf(TURNTABLE),
            listOf(VIDEO),
            listOf(IPOD),
            listOf(POSTER),
            // the lyrics stage sits under the timeline, the way the classic sits under the flagship
            listOf(TIMELINE, LYRICS),
        )
    }
}

val PlayerStyleKey = stringPreferencesKey("playerStyle")

// the ipod player's body colours, ipod.js's three plus a pitch black one with a dark screen
enum class IpodTheme { SILVER, BLACK, U2, TRUE_DARK }

val IpodThemeKey = stringPreferencesKey("ipodTheme")

// the click wheel's own colour, or whatever the body's theme comes with
enum class IpodWheelColor { THEME, WHITE, BLACK, RED }

val IpodWheelColorKey = stringPreferencesKey("ipodWheelColor")

// the screen grows down to the wheel on tall phones instead of keeping the classic's shape
val IpodTallScreenKey = booleanPreferencesKey("ipodTallScreen")

// the soft shading round the edges of the body
val IpodShadowKey = booleanPreferencesKey("ipodShadow")

/** the Poster's bars round its cover, moving with the music; off until turned on in its editor */
val PosterVisualizerKey = booleanPreferencesKey("posterVisualizer")

/** whether [style] is shown turned sideways (its content rotated a quarter turn) */
fun playerStyleRotatedKey(style: PlayerStyle) = booleanPreferencesKey("playerStyleRotated_${style.name}")

/** what the timestamp at the right of the seek bar shows */
enum class PlayerEndTimestamp { END_TIME, TIME_LEFT }

val PlayerEndTimestampKey = stringPreferencesKey("playerEndTimestamp")

/** the four actions in the player's chevron menu, top to bottom, as action names joined by commas */
val PlayerMenuSlotsKey = stringPreferencesKey("playerMenuSlots")

/** the action of the round button beside the chevron (a like, out of the box) */
val PlayerSideButtonKey = stringPreferencesKey("playerSideButton")

/** what sits at the bottom of the player, under the play buttons */
enum class PlayerBottomBarStyle(val displayName: String) {
    /** the lyrics and queue cards, one behind the other */
    STACK("Stack"),
    /** the phone's volume, with the lyrics and queue buttons under it */
    VOLUME("Volume"),
}

val PlayerBottomBarStyleKey = stringPreferencesKey("playerBottomBarStyle")

// the new flagship keeps its own bottom bar, so it shows the volume bar it was designed with even where classic has the cards
val FlagshipBottomBarStyleKey = stringPreferencesKey("flagshipBottomBarStyle")

// the new flagship's own background, the moving cover colours out of the box
val FlagshipBackgroundStyleKey = stringPreferencesKey("flagshipBackgroundStyle")

// how the new flagship lays out its play buttons
enum class FlagshipTransportLayout {
    // back, play and next, wide apart with nothing round them
    THREE,
    // ten seconds back and forward on the outside, play in a white circle
    TEN_SECONDS,
}

val FlagshipTransportLayoutKey = stringPreferencesKey("flagshipTransportLayout")

/** how far the player's controls were held and dragged from their usual place in the editor, in dp (down is positive) */
val PlayerControlsShiftKey = floatPreferencesKey("playerControlsShift")

/** how much taller the player's card was pulled in the editor, in dp: room for readings and the next line */
val PlayerCardGrowthKey = floatPreferencesKey("playerCardGrowth")

/** what the disc's label shows */
enum class DiscLabelContent { CUSTOM, MUSIC_NAME, ARTIST_NAME, ALBUM_NAME, MUSIC_AND_ARTIST, NONE }

val DiscLabelContentKey = stringPreferencesKey("discLabelContent")

val SeekBarStyleKey = stringPreferencesKey("seekBarStyle")

val ButtonStyleKey = stringPreferencesKey("buttonStyle")

val PlayerCoverStyleKey = stringPreferencesKey("playerCoverStyle")

/**
 * Cover style to use when the user has not picked one.
 *
 * Edge-to-edge art leans on the backdrop blur behind it to separate the cover from the controls,
 * and that blur needs API 31. Below that the full-bleed art runs straight under the chrome with
 * nothing softening it, so squared reads far better there. This is only the *default* - an
 * explicit choice in Player customise still wins on every device.
 */
val DefaultPlayerCoverStyle: PlayerCoverStyle
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PlayerCoverStyle.EDGE_TO_EDGE
    } else {
        PlayerCoverStyle.SQUARED
    }

val ShowBigDiscStylesKey = booleanPreferencesKey("showBigDiscStyles")

val DiscRotatingAnimationKey = booleanPreferencesKey("discRotatingAnimation")

val DiscRealisticModeKey = booleanPreferencesKey("discRealisticMode")

val DiscNameKey = stringPreferencesKey("discName")

enum class PlayerTextContent {
    MUSIC_NAME,
    ALBUM_NAME,
    ARTIST_NAME,
    COMBINED,
}

val PlayerTitleSlotKey = stringPreferencesKey("playerTitleSlot")
val PlayerSubtitleSlotKey = stringPreferencesKey("playerSubtitleSlot")

val TopSize = stringPreferencesKey("topSize")
val HistoryDuration = floatPreferencesKey("historyDuration")

val PlayerButtonsStyleKey = stringPreferencesKey("player_buttons_style")
val PlayerBackgroundStyleKey = stringPreferencesKey("playerBackgroundStyle")
val MiniPlayerBackgroundStyleKey = stringPreferencesKey("miniPlayerBackgroundStyle")
val ShowLyricsKey = booleanPreferencesKey("showLyrics")
val SwipeLyricsKey = booleanPreferencesKey("swipeLyrics")
val EnableLyricsThumbnailPlayPauseKey = booleanPreferencesKey("enableLyricsThumbnailPlayPause")
val LyricsTextPositionKey = stringPreferencesKey("lyricsTextPosition")
val LyricsClickKey = booleanPreferencesKey("lyricsClick")

// the old lyrics shader switch, only read once to move people onto the shaderless lyric styles
val LyricsWaveAnimationKey = booleanPreferencesKey("lyricsWaveAnimation")

val LyricsHighBloomKey = booleanPreferencesKey("lyricsHighBloom")
val LyricsScrollKey = booleanPreferencesKey("lyricsScrollKey")
val LyricsRomanizeJapaneseKey = booleanPreferencesKey("lyricsRomanizeJapanese")
val LyricsRomanizeKoreanKey = booleanPreferencesKey("lyricsRomanizeKorean")
val LyricsRomanizeChineseKey = booleanPreferencesKey("lyricsRomanizeChinese")
val LyricsRomanizeRussianKey = booleanPreferencesKey("lyricsRomanizeRussian")
val LyricsRomanizeUkrainianKey = booleanPreferencesKey("lyricsRomanizeUkrainian")
val LyricsRomanizeSerbianKey = booleanPreferencesKey("lyricsRomanizeSerbian")
val LyricsRomanizeBulgarianKey = booleanPreferencesKey("lyricsRomanizeBulgarian")
val LyricsRomanizeBelarusianKey = booleanPreferencesKey("lyricsRomanizeBelarusian")
val LyricsRomanizeKyrgyzKey = booleanPreferencesKey("lyricsRomanizeKyrgyz")
val LyricsRomanizeMacedonianKey = booleanPreferencesKey("lyricsRomanizeMacedonian")
val LyricsRomanizeHindiKey = booleanPreferencesKey("lyricsRomanizeHindi")
val LyricsRomanizePunjabiKey = booleanPreferencesKey("lyricsRomanizePunjabi")
val LyricsRomanizeAsMainKey = booleanPreferencesKey("lyricsRomanizeAsMain")
val LyricsRomanizeCyrillicByLineKey = booleanPreferencesKey("lyricsRomanizeCyrillicByLine")
val TranslateLyricsKey = booleanPreferencesKey("translateLyrics")
val OpenRouterApiKey = stringPreferencesKey("openRouterApiKey")
val AiProviderKey = stringPreferencesKey("aiProvider")
val OpenRouterBaseUrlKey = stringPreferencesKey("openRouterBaseUrl")
val OpenRouterModelKey = stringPreferencesKey("openRouterModel")
val TranslateModeKey = stringPreferencesKey("translateMode")
val TranslateLanguageKey = stringPreferencesKey("translateLanguage")
/** Language the lyrics are translated FROM; "auto" lets the translator detect it. */
val TranslateSourceLanguageKey = stringPreferencesKey("translateSourceLanguage")
// a video's own uploaded translation is used as the lyrics translation when it has one (settings > general)
val YouTubeCaptionTranslationKey = booleanPreferencesKey("youtubeCaptionTranslation")
// a music video's lyrics are moved onto the video's own timing, found from its auto captions (settings > general)
val SyncLyricsToVideoKey = booleanPreferencesKey("syncLyricsToVideo")
val DeeplApiKey = stringPreferencesKey("deeplApiKey")
val DeeplFormalityKey = stringPreferencesKey("deeplFormality")
val LyricsGlowEffectKey = booleanPreferencesKey("lyricsGlowEffect")
val AppleMusicLyricsBlurKey = booleanPreferencesKey("appleMusicLyricsBlur")
val LyricsStandardBlurKey = booleanPreferencesKey("lyricsStandardBlur")

/**
 * How the active lyric line animates. A new key rather than [LyricsAnimationStyleKey]: that one
 * belonged to renderers that no longer exist and may still hold their old values on upgrade.
 */
val LyricsMotionStyleKey = stringPreferencesKey("lyricsMotionStyle")

// risesWords: words rise and lines cascade (style 1's motion), shaded: the agsl glow and wave run on top
enum class LyricsMotionStyle(
    val displayName: String,
    val description: String,
    val risesWords: Boolean,
    val shaded: Boolean,
) {
    /** Style 1: words rise into place as they're sung, held notes swell and glow, lines cascade. */
    MOTION("Style 1", "Words rise as they're sung, long notes swell, lines cascade", risesWords = true, shaded = true),

    /** Style 2: the original - a wave across the sung letters, the list scrolls as one piece. */
    LEGACY("Style 2", "The original wave across the sung letters", risesWords = false, shaded = true),

    // style 1 without the shader, for phones that struggle with it
    BORING("Boring", "Style 1's rise, without the glow", risesWords = true, shaded = false),

    // style 2 without the shader, just the colour sweeping along
    PLAIN("Plain", "Style 2's sweep, without the glow or the wave", risesWords = false, shaded = false);

    // the same motion with the shader switched off, used when moving people off the old shader switch
    val withoutShader: LyricsMotionStyle get() = if (risesWords) BORING else PLAIN
}

val LyricsAnimationStyleKey = stringPreferencesKey("lyricsAnimationStyle")
enum class LyricsAnimationStyle {
    NONE,
    FADE,
    GLOW,
    SLIDE,
    KARAOKE,
    APPLE,
    APPLE_V2,
    MOCHY_1,
    LYRICS_V2,
    METRO_LYRICS,
}

val LyricsTextSizeKey = floatPreferencesKey("lyricsTextSize")
val LyricsLineSpacingKey = floatPreferencesKey("lyricsLineSpacing")

val PlayerVolumeKey = floatPreferencesKey("playerVolume")
val RepeatModeKey = intPreferencesKey("repeatMode")

val SearchSourceKey = stringPreferencesKey("searchSource")
val SwipeThumbnailKey = booleanPreferencesKey("swipeThumbnail")
val RotatingThumbnailKey = booleanPreferencesKey("rotatingThumbnail")
val CanvasThumbnailAnimationKey = booleanPreferencesKey("canvasThumbnailAnimation")
val CanvasWifiOnlyKey = booleanPreferencesKey("canvasWifiOnly")
/** The player's format badge beside the title (OPUS, AAC, FLAC...) */
val ShowFormatBadgeKey = booleanPreferencesKey("showFormatBadge")
/** The badge also says the stream's sample rate */
val ShowFormatSampleRateKey = booleanPreferencesKey("showFormatSampleRate")
val SwipeSensitivityKey = floatPreferencesKey("swipeSensitivity")

enum class SearchSource {
    LOCAL,
    ONLINE,
    ;

    fun toggle() =
        when (this) {
            LOCAL -> ONLINE
            ONLINE -> LOCAL
        }
}

val VisitorDataKey = stringPreferencesKey("visitorData")
val DataSyncIdKey = stringPreferencesKey("dataSyncId")
val InnerTubeCookieKey = stringPreferencesKey("innerTubeCookie")
val AccountNameKey = stringPreferencesKey("accountName")
val AccountEmailKey = stringPreferencesKey("accountEmail")
val AccountChannelHandleKey = stringPreferencesKey("accountChannelHandle")
val UseLoginForBrowse = booleanPreferencesKey("useLoginForBrowse")

val LanguageCodeToName =
    mapOf(
        "af" to "Afrikaans",
        "az" to "Azərbaycan",
        "id" to "Bahasa Indonesia",
        "ms" to "Bahasa Malaysia",
        "ca" to "Català",
        "cs" to "Čeština",
        "da" to "Dansk",
        "de" to "Deutsch",
        "et" to "Eesti",
        "en-GB" to "English (UK)",
        "en" to "English (US)",
        "es" to "Español (España)",
        "es-419" to "Español (Latinoamérica)",
        "eu" to "Euskara",
        "fil" to "Filipino",
        "fr" to "Français",
        "fr-CA" to "Français (Canada)",
        "gl" to "Galego",
        "hr" to "Hrvatski",
        "zu" to "IsiZulu",
        "is" to "Íslenska",
        "it" to "Italiano",
        "sw" to "Kiswahili",
        "lt" to "Lietuvių",
        "hu" to "Magyar",
        "nl" to "Nederlands",
        "no" to "Norsk",
        "or" to "Odia",
        "uz" to "O‘zbe",
        "pl" to "Polski",
        "pt-PT" to "Português",
        "pt" to "Português (Brasil)",
        "ro" to "Română",
        "sq" to "Shqip",
        "sk" to "Slovenčina",
        "sl" to "Slovenščina",
        "fi" to "Suomi",
        "sv" to "Svenska",
        "bo" to "Tibetan བོད་སྐད།",
        "vi" to "Tiếng Việt",
        "tr" to "Türkçe",
        "bg" to "Български",
        "ky" to "Кыргызча",
        "kk" to "Қазақ Тілі",
        "mk" to "Македонски",
        "mn" to "Монгол",
        "ru" to "Русский",
        "sr" to "Српски",
        "uk" to "Українська",
        "el" to "Ελληνικά",
        "hy" to "Հայերեն",
        "iw" to "עברית",
        "ur" to "اردو",
        "ar" to "العربية",
        "fa" to "فارسی",
        "ne" to "नेपाली",
        "mr" to "मराठी",
        "hi" to "हिन्दी",
        "bn" to "বাংলা",
        "pa" to "ਪੰਜਾਬੀ",
        "gu" to "ગુજરાતી",
        "ta" to "தமிழ்",
        "te" to "తెలుగు",
        "kn" to "ಕನ್ನಡ",
        "ml" to "മലയാളം",
        "si" to "සිංහල",
        "th" to "ภาษาไทย",
        "lo" to "ລາວ",
        "my" to "ဗမာ",
        "ka" to "ქართული",
        "am" to "አማርኛ",
        "km" to "ខ្មែរ",
        "zh-CN" to "中文 (简体)",
        "zh-TW" to "中文 (繁體)",
        "zh-HK" to "中文 (香港)",
        "ja" to "日本語",
        "ko" to "한국어",
    )

val CountryCodeToName =
    mapOf(
        "DZ" to "Algeria",
        "AR" to "Argentina",
        "AU" to "Australia",
        "AT" to "Austria",
        "AZ" to "Azerbaijan",
        "BH" to "Bahrain",
        "BD" to "Bangladesh",
        "BY" to "Belarus",
        "BE" to "Belgium",
        "BO" to "Bolivia",
        "BA" to "Bosnia and Herzegovina",
        "BR" to "Brazil",
        "BG" to "Bulgaria",
        "KH" to "Cambodia",
        "CA" to "Canada",
        "CL" to "Chile",
        "HK" to "Hong Kong",
        "CO" to "Colombia",
        "CR" to "Costa Rica",
        "HR" to "Croatia",
        "CY" to "Cyprus",
        "CZ" to "Czech Republic",
        "DK" to "Denmark",
        "DO" to "Dominican Republic",
        "EC" to "Ecuador",
        "EG" to "Egypt",
        "SV" to "El Salvador",
        "EE" to "Estonia",
        "FI" to "Finland",
        "FR" to "France",
        "GE" to "Georgia",
        "DE" to "Germany",
        "GH" to "Ghana",
        "GR" to "Greece",
        "GT" to "Guatemala",
        "HN" to "Honduras",
        "HU" to "Hungary",
        "IS" to "Iceland",
        "IN" to "India",
        "ID" to "Indonesia",
        "IQ" to "Iraq",
        "IE" to "Ireland",
        "IL" to "Israel",
        "IT" to "Italy",
        "JM" to "Jamaica",
        "JP" to "Japan",
        "JO" to "Jordan",
        "KZ" to "Kazakhstan",
        "KE" to "Kenya",
        "KR" to "South Korea",
        "KW" to "Kuwait",
        "LA" to "Lao",
        "LV" to "Latvia",
        "LB" to "Lebanon",
        "LY" to "Libya",
        "LI" to "Liechtenstein",
        "LT" to "Lithuania",
        "LU" to "Luxembourg",
        "MK" to "Macedonia",
        "MY" to "Malaysia",
        "MT" to "Malta",
        "MX" to "Mexico",
        "ME" to "Montenegro",
        "MA" to "Morocco",
        "NP" to "Nepal",
        "NL" to "Netherlands",
        "NZ" to "New Zealand",
        "NI" to "Nicaragua",
        "NG" to "Nigeria",
        "NO" to "Norway",
        "OM" to "Oman",
        "PK" to "Pakistan",
        "PA" to "Panama",
        "PG" to "Papua New Guinea",
        "PY" to "Paraguay",
        "PE" to "Peru",
        "PH" to "Philippines",
        "PL" to "Poland",
        "PT" to "Portugal",
        "PR" to "Puerto Rico",
        "QA" to "Qatar",
        "RO" to "Romania",
        "RU" to "Russian Federation",
        "SA" to "Saudi Arabia",
        "SN" to "Senegal",
        "RS" to "Serbia",
        "SG" to "Singapore",
        "SK" to "Slovakia",
        "SI" to "Slovenia",
        "ZA" to "South Africa",
        "ES" to "Spain",
        "LK" to "Sri Lanka",
        "SE" to "Sweden",
        "CH" to "Switzerland",
        "TW" to "Taiwan",
        "TZ" to "Tanzania",
        "TH" to "Thailand",
        "TN" to "Tunisia",
        "TR" to "Turkey",
        "UG" to "Uganda",
        "UA" to "Ukraine",
        "AE" to "United Arab Emirates",
        "GB" to "United Kingdom",
        "US" to "United States",
        "UY" to "Uruguay",
        "VE" to "Venezuela (Bolivarian Republic)",
        "VN" to "Vietnam",
        "YE" to "Yemen",
        "ZW" to "Zimbabwe",
    )

val SuggestionRegionSlugToName =
    mapOf(
        "system" to "System Default",
        "us" to "Global (USA)",
        "in" to "India",
        "gb" to "United Kingdom",
        "ca" to "Canada",
        "au" to "Australia",
        "jp" to "Japan",
        "kr" to "South Korea",
        "de" to "Germany",
        "fr" to "France",
        "br" to "Brazil",
        "mx" to "Mexico",
        "ru" to "Russia",
        "it" to "Italy",
        "es" to "Spain",
        "nl" to "Netherlands",
        "se" to "Sweden",
        "no" to "Norway",
        "dk" to "Denmark",
        "fi" to "Finland",
        "pl" to "Poland",
        "tr" to "Turkey",
        "za" to "South Africa",
        "ng" to "Nigeria",
        "id" to "Indonesia",
        "my" to "Malaysia",
        "ph" to "Philippines",
        "th" to "Thailand",
        "vn" to "Vietnam",
        "tw" to "Taiwan",
        "hk" to "Hong Kong",
        "sg" to "Singapore",
        "ar" to "Argentina",
        "co" to "Colombia",
        "cl" to "Chile",
        "pe" to "Peru",
        "eg" to "Egypt",
        "sa" to "Saudi Arabia",
        "ae" to "United Arab Emirates",
        "il" to "Israel"
    )

val EnableCustomApiKey = booleanPreferencesKey("enableCustomApi")
val EnableMonochromeBackendKey = booleanPreferencesKey("enableMonochromeBackend")
val EnableSpatialAudioKey = booleanPreferencesKey("enableSpatialAudio")

val EnableMonochromePlaybackBackendKey = booleanPreferencesKey("enableMonochromePlaybackBackend")
val MonochromePlaybackApiUrlKey = stringPreferencesKey("monochromePlaybackApiUrl")
val MonochromePlaybackApiTokenKey = stringPreferencesKey("monochromePlaybackApiToken")

/**
 * Last Turnstile session JWT and its expiry, persisted so a solved challenge survives a restart.
 *
 * Without this the token lived only in memory, so every cold start paid a fresh WebView challenge
 * before Monochrome could play anything - which is what made it feel like it never loaded.
 */
val MonochromeTurnstileJwtKey = stringPreferencesKey("monochromeTurnstileJwt")
val MonochromeTurnstileJwtExpiryKey = longPreferencesKey("monochromeTurnstileJwtExpiry")

/** When Monochrome cannot resolve a track, fall back to YouTube instead of failing. */
val MonochromeFallbackToYouTubeKey = booleanPreferencesKey("monochromeFallbackToYouTube")
val EnableStreamDebugToastsKey = booleanPreferencesKey("enableStreamDebugToasts")

val AmazonMusicApiUrlKey = stringPreferencesKey("amazonMusicApiUrl")
val TidalToAsinUrlKey = stringPreferencesKey("tidalToAsinUrl")
val TurnstileSiteKeyKey = stringPreferencesKey("turnstileSiteKey")
val TurnstileBypassTokenKey = stringPreferencesKey("turnstileBypassToken")

val DeezerFallbackEnabledKey = booleanPreferencesKey("deezerFallbackEnabled")
val DeezerFallbackUrlKey = stringPreferencesKey("deezerFallbackUrl")

val ApiInstancesKey = stringPreferencesKey("apiInstances")
val EnableAmazonMusicBackendKey = booleanPreferencesKey("enable_amazon_music_backend")
val AmazonMusicInstancesKey = stringPreferencesKey("amazon_music_instances_list")
val StreamingInstancesKey = stringPreferencesKey("streamingInstances")
val QobuzInstancesKey = stringPreferencesKey("qobuzInstances")
val MonochromeInstancesKey = stringPreferencesKey("monochromeInstances")

val MusicHapticsEnabledKey = booleanPreferencesKey("musicHapticsEnabled")
val MusicHapticsSensitivityKey = stringPreferencesKey("musicHapticsSensitivity")
val HapticFocusKey = stringPreferencesKey("hapticFocus")
val HideAudioQualityBadgeKey = booleanPreferencesKey("hideAudioQualityBadge")

val DisableBlurKey = booleanPreferencesKey("disableBlur")

/**
 * How much work the glass blur does. Low is the lighter glass from before 8.1: the bottom bar
 * blurs only what's right under it with no grain on top, and glass draws its backdrop at a lower
 * resolution. High is the newer frosted glass.
 */
val BlurQualityKey = stringPreferencesKey("blurQuality")

enum class BlurQuality { LOW, HIGH }
val EnableProgressiveBlurKey = booleanPreferencesKey("enableProgressiveBlur")
// the animation switch in customize: off, nothing in the app moves (the old key name stayed so nobody's choice is lost)
val EnableElementBlurAnimatorKey = booleanPreferencesKey("enableElementBlurAnimator")
val BlurStrengthPxKey = floatPreferencesKey("blurStrengthPx")
// blur on things themselves (lyric lines, title morphs, menus, page wipes), apart from the glass blur
val ElementBlurKey = booleanPreferencesKey("elementBlur")
// how fast animations run on top of android's own animation speed, 1 means just follow android
val AnimationSpeedKey = floatPreferencesKey("animationSpeed")
// the phone's status bar over the open player, off hides it on every player style, the ipod too
val PlayerStatusBarKey = booleanPreferencesKey("playerStatusBar")
// out of the box the open player runs to the top of the screen with the status bar hidden
const val PlayerStatusBarDefault = false
// settings rows show only their titles, holding a row still shows what it does
val HideSettingsDescriptionsKey = booleanPreferencesKey("hideSettingsDescriptions")
// no update popup on home and no update notification, the updater page still shows what's new when opened
val HideUpdateNoticeKey = booleanPreferencesKey("hideUpdateNotice")
val AudioQualityBadgeDevKey = booleanPreferencesKey("audioQualityBadgeDev")
val YtVideoBackgroundLyricsSyncKey = booleanPreferencesKey("ytVideoBackgroundLyricsSync")
val ForceYtBackdropBlur1500Key = booleanPreferencesKey("forceYtBackdropBlur1500")

val OfflineModeKey = booleanPreferencesKey("offlineMode")
val DisableAiFilterKey = booleanPreferencesKey("disableAiFilter")
val SetupImportCompletedKey = booleanPreferencesKey("setupImportCompleted")

val LastKnownVersionNameKey = stringPreferencesKey("lastKnownVersionName")
val LastDonateShownTimeKey = longPreferencesKey("lastDonateShownTime")

// Donate auto-prompt (ui/screens/donate/DonatePrompt.kt)
val DonatePlaytimeMsKey = longPreferencesKey("donatePlaytimeMs")
val DonateFirstPromptShownKey = booleanPreferencesKey("donateFirstPromptShown")
val DonatePromptWindowStartKey = longPreferencesKey("donatePromptWindowStart")
val DonatePromptSlotsKey = stringPreferencesKey("donatePromptSlots")
val DonateOpenPromptAtKey = longPreferencesKey("donateOpenPromptAt")

// one-off lyrics cache clears (App.kt). raise LyricsCacheWipeGeneration to clear it again in a later release
val LyricsCacheWipeKey = intPreferencesKey("lyricsCacheWipe")
const val LyricsCacheWipeGeneration = 1
