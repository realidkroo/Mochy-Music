// HeroCarousel.kt

package com.mochyy.mochymusic.ui.component

import android.content.Context
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applecanvas.AppleMusicCanvasProvider
import com.mochyy.mochymusic.canvas.CanvasArtwork
import com.mochyy.mochymusic.canvas.MonochromeApiCanvas
import com.mochyy.mochymusic.constants.CanvasThumbnailAnimationKey
import com.mochyy.mochymusic.constants.CanvasWifiOnlyKey
import com.mochyy.mochymusic.constants.DisableBlurKey
import com.mochyy.mochymusic.db.entities.LocalItem
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.models.SimilarRecommendation
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.mochyy.mochymusic.ui.player.CanvasArtworkPlaybackCache
import com.mochyy.mochymusic.ui.player.CanvasArtworkPlayer
import com.mochyy.mochymusic.ui.player.normalizeCanvasArtistName
import com.mochyy.mochymusic.ui.player.normalizeCanvasSongTitle
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.ui.utils.isLandscapeWindow
import com.mochyy.mochymusic.ui.utils.isTvLayout
import com.mochyy.mochymusic.ui.utils.isWideLayout
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.DailyDiscoverItem
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/** the hero takes this share of the screen height (527 of 874 in the Figma frame) */
const val HeroHeightFraction = 0.6f

// on its side the hero is the whole screen, like the tv: only the first row's title and the tops of its cards show under it
private const val LandscapeHeroHeightFraction = 0.86f

// on its side the title and the pill sit lower in the taller hero, just over the first row
private val LandscapeHeroTextBottom = 46.dp

// an upright tablet: each card takes this share of the hero's width, the next one peeking in after a gap
private const val PeekPageShare = 0.8f

// on a tv the hero is shorter, so the first row of music shows under it without scrolling
private const val TvHeroHeightFraction = 0.56f

// on a tv the cover sits square at the hero's right, this much of the hero's height, this far in from its edge
private const val TvCoverFraction = 0.72f
private val TvCoverEndInset = 56.dp
private val TvCoverShape = RoundedCornerShape(18.dp)
private val TvCoverShadow = androidx.compose.ui.graphics.shadow.Shadow(radius = 28.dp, color = Color.Black.copy(alpha = 0.55f), offset = androidx.compose.ui.unit.DpOffset(0.dp, 10.dp))
private val PeekPageGap = 14.dp

// the wide layout's cards sit side by side and slide; the phone's fill the hero and wipe
internal val LocalHeroPeek = androidx.compose.runtime.staticCompositionLocalOf { false }

// the hero's height for this window: Home's scroll-away and the bones use the same one
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun heroHeight(): androidx.compose.ui.unit.Dp {
    val fraction = when {
        isTvLayout() -> TvHeroHeightFraction
        isWideLayout() && isLandscapeWindow() -> LandscapeHeroHeightFraction
        else -> HeroHeightFraction
    }
    return (LocalConfiguration.current.screenHeightDp * fraction).dp
}

// a wide card: the hero's width less the gap, of which the card takes its share and the next card the rest
private object PeekPageSize : androidx.compose.foundation.pager.PageSize {
    override fun androidx.compose.ui.unit.Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int =
        ((availableSpace - pageSpacing) * PeekPageShare).roundToInt()
}

// the first section starts inside the hero's faded-out bottom rather than under it, which puts
// its title 61dp below the Play pill like the Figma (it was 68)
private val HeroSectionTuck = 7.dp

private val HeroSlideEasing = CubicBezierEasing(0.32f, 0f, 0.15f, 1f)
private const val HeroSlideMillis = 1150
private const val HeroAdvanceMillis = 5500L
private val HeroPillContent = Color(0xFF2F2F2F)
private val HeroPillFill = Color.White.copy(alpha = 0.64f)
private val HeroLabelColor = Color.White.copy(alpha = 0.69f)

// once the hero starts scrolling away its video holds still and the carousel stops turning, so
// the scroll's blur only ever sees a still frame
private const val HeroStillFrom = 0.08f

/** the small line above a hero title: [text], then a tiny [coverUrl] and the [subject] it's about */
@Immutable
data class HeroLabel(
    val text: String,
    val coverUrl: String? = null,
    val subject: String? = null,
    /** the subject is an artist: its tiny picture is round */
    val roundCover: Boolean = false,
)

sealed interface HeroCarouselItem {
    fun label(isPlaying: Boolean): HeroLabel
    val mainText: String
    val subText: String
    val thumbnailUrl: String?
    val mediaId: String?
    val songTitle: String?
    val artistName: String?
    val albumTitle: String?

    /** albums, artists and playlists open a page instead of playing straight away */
    val opensPage: Boolean get() = false

    fun onPlay(playerConnection: PlayerConnection, navController: NavController)
}

data class KeepListeningItem(val item: MediaMetadata, val isLastPlayed: Boolean = false) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = HeroLabel(
        when {
            isPlaying -> "Now playing"
            isLastPlayed -> "Continue"
            else -> "Recently played"
        }
    )
    override val mainText: String = item.title
    override val subText: String = item.artists.joinToString(", ") { it.name }
    override val thumbnailUrl: String? = item.thumbnailUrl
    override val mediaId: String? = item.id
    override val songTitle: String? = item.title
    override val artistName: String? = item.artists.joinToString { it.name }
    override val albumTitle: String? = item.album?.title

    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) {
        if (isLastPlayed) {
            playerConnection.play()
        } else {
            playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId = item.id), item))
        }
    }
}

/** why a recommendation is on the hero. it's picked per card, so neighbouring cards never say the same thing */
enum class DiscoverReason { YouMayAlsoLike, BecauseYouLike, MoreFrom, BecauseYouPlayed, InspiredBy }

/** a recommendation card, labelled with the song it grew out of (and that song's tiny cover) */
data class DiscoverItem(
    val item: DailyDiscoverItem,
    val reason: DiscoverReason = DiscoverReason.YouMayAlsoLike,
) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel {
        if (isPlaying) return HeroLabel("Now playing")
        val seed = item.seed
        val seedCover = seed.song.thumbnailUrl
        return when (reason) {
            DiscoverReason.YouMayAlsoLike -> HeroLabel("You may also like")
            DiscoverReason.BecauseYouLike -> HeroLabel("Because you like", seedCover, seed.song.title)
            DiscoverReason.MoreFrom -> HeroLabel(
                "More from",
                seedCover,
                sharedArtist(item) ?: seed.artists.firstOrNull()?.name ?: seed.song.title,
            )
            DiscoverReason.BecauseYouPlayed -> HeroLabel("Because you played", seedCover, seed.song.title)
            DiscoverReason.InspiredBy -> HeroLabel("Inspired by", seedCover, seed.song.title)
        }
    }
    override val mainText: String = item.recommendation.title
    override val subText: String = (item.recommendation as? SongItem)?.artists?.joinToString(", ") { it.name } ?: ""
    override val thumbnailUrl: String? = item.recommendation.thumbnail
    override val mediaId: String? = item.recommendation.id
    override val songTitle: String? = item.recommendation.title
    override val artistName: String? = (item.recommendation as? SongItem)?.artists?.joinToString { it.name } ?: ""
    override val albumTitle: String? = (item.recommendation as? SongItem)?.album?.name
    override val opensPage: Boolean = item.recommendation !is SongItem

    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) =
        playOrOpen(item.recommendation, playerConnection, navController)
}

private fun playOrOpen(rec: YTItem, playerConnection: PlayerConnection, navController: NavController) {
    when (rec) {
        is SongItem -> playerConnection.playQueue(
            YouTubeQueue(
                rec.endpoint ?: WatchEndpoint(videoId = rec.id),
                rec.toMediaMetadata()
            )
        )
        is AlbumItem -> navController.navigate("album/${rec.id}")
        is ArtistItem -> navController.navigate("artist/${rec.id}")
        is PlaylistItem -> navController.navigate("online_playlist/${rec.id}")
        else -> Unit
    }
}

/**
 * a pick from Home's related rows, worded by what it grew out of: "Because you listen to" an artist,
 * "Similar to" a song, "Because you played" an album. built from rows Home already loaded, so no fetch
 */
data class RelatedPickItem(
    val recommendation: YTItem,
    val reason: HeroLabel,
) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = if (isPlaying) HeroLabel("Now playing") else reason
    override val mainText: String = recommendation.title
    override val subText: String = (recommendation as? SongItem)?.artists?.joinToString(", ") { it.name } ?: ""
    override val thumbnailUrl: String? = recommendation.thumbnail
    override val mediaId: String? = recommendation.id
    override val songTitle: String? = recommendation.title
    override val artistName: String? = (recommendation as? SongItem)?.artists?.joinToString { it.name } ?: ""
    override val albumTitle: String? = (recommendation as? SongItem)?.album?.name
    override val opensPage: Boolean = recommendation !is SongItem

    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) =
        playOrOpen(recommendation, playerConnection, navController)
}

/** one of your own songs brought back with a reason of its own: "On repeat", "Back in rotation" */
data class ListeningPickItem(val item: MediaMetadata, val reason: String) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = HeroLabel(if (isPlaying) "Now playing" else reason)
    override val mainText: String = item.title
    override val subText: String = item.artists.joinToString(", ") { it.name }
    override val thumbnailUrl: String? = item.thumbnailUrl
    override val mediaId: String? = item.id
    override val songTitle: String? = item.title
    override val artistName: String? = item.artists.joinToString { it.name }
    override val albumTitle: String? = item.album?.title

    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) {
        playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId = item.id), item))
    }
}

// the hero's cards in order, shared by the phone's carousel and the tv's home so both put forward the same picks
internal fun heroCarouselItems(
    lastPlayedSong: MediaMetadata?,
    dailyDiscover: List<DailyDiscoverItem>?,
    extras: List<HeroCarouselItem>,
    playlistPicks: List<PlaylistPickItem>,
    keepListening: List<com.mochyy.mochymusic.db.entities.LocalItem>?,
    starterItems: List<HeroCarouselItem>,
    showGacha: Boolean,
): List<HeroCarouselItem> = buildList {
    lastPlayedSong?.let { add(KeepListeningItem(it, isLastPlayed = true)) }

    // the gacha comes straight after the first card, so the first auto-advance shows it;
    // buried behind the recommendations, most people never got that far
    if (showGacha) add(GachaItem)

    val discover = dailyDiscover?.take(4).orEmpty()
    val reasons = discoverReasons(discover)
    val discoverCards = discover.mapIndexed { index, item -> DiscoverItem(item, reasons[index]) }
    // a pick already on the hero (the song playing, a daily discover) isn't put forward twice
    val shown = HashSet<String>()
    lastPlayedSong?.id?.let(shown::add)
    interleave(discoverCards, extras).forEach { card ->
        val id = card.mediaId
        if (id == null || shown.add(id)) add(card)
    }

    // the user's own playlists between the recommendations, a couple at a time
    addAll(playlistPicks.take(2))

    val regular = count { it !is GachaItem }
    if (regular < 5) {
        keepListening?.filterIsInstance<com.mochyy.mochymusic.db.entities.Song>()
            ?.filter { song -> none { it.mediaId == song.id } }
            ?.take(5 - regular)
            ?.forEach { song ->
                add(KeepListeningItem(song.toMediaMetadata(), isLastPlayed = false))
            }
    }

    // nothing played yet: start from the imported playlists or the picked artists' songs
    if (none { it !is GachaItem }) addAll(starterItems.take(5))
}

/**
 * the extra cards, one per related row (its first song, else its first anything) plus a song on
 * repeat and a forgotten favourite. a row about an artist leads, since that's the most personal
 */
internal fun extraHeroPicks(
    similar: List<SimilarRecommendation>?,
    onRepeat: List<com.mochyy.mochymusic.db.entities.Song>?,
    forgotten: List<com.mochyy.mochymusic.db.entities.Song>?,
): List<HeroCarouselItem> = buildList {
    similar.orEmpty()
        .sortedBy { if (it.title is com.mochyy.mochymusic.db.entities.Artist) 0 else 1 }
        .take(4)
        .forEach { row ->
            val pick = row.items.firstOrNull { it is SongItem && !it.thumbnail.isNullOrEmpty() }
                ?: row.items.firstOrNull { !it.thumbnail.isNullOrEmpty() }
                ?: return@forEach
            val seed = row.title
            val reason = when (seed) {
                is com.mochyy.mochymusic.db.entities.Artist ->
                    HeroLabel("Because you listen to", seed.thumbnailUrl, seed.title, roundCover = true)
                is com.mochyy.mochymusic.db.entities.Album -> HeroLabel("Because you played", seed.thumbnailUrl, seed.title)
                else -> HeroLabel("Similar to", seed.thumbnailUrl, seed.title)
            }
            add(RelatedPickItem(pick, reason))
        }
    onRepeat?.firstOrNull { !it.thumbnailUrl.isNullOrEmpty() }?.let {
        add(ListeningPickItem(it.toMediaMetadata(), "On repeat"))
    }
    forgotten?.firstOrNull { !it.thumbnailUrl.isNullOrEmpty() }?.let {
        add(ListeningPickItem(it.toMediaMetadata(), "Back in rotation"))
    }
}

/** [a] and [b] taken in turns, so the extra kinds are spread through the hero instead of piled at the end */
private fun <T> interleave(a: List<T>, b: List<T>): List<T> = buildList(a.size + b.size) {
    for (i in 0 until maxOf(a.size, b.size)) {
        a.getOrNull(i)?.let { add(it) }
        b.getOrNull(i)?.let { add(it) }
    }
}

/** the "Gacha here! / Random plays" card (Figma 25:1508): roll a reel of covers for a random song */
data object GachaItem : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = HeroLabel("Gacha here!")
    override val mainText: String = "Random plays"
    override val subText: String = ""
    override val thumbnailUrl: String? = null
    override val mediaId: String? = null
    override val songTitle: String? = null
    override val artistName: String? = null
    override val albumTitle: String? = null
    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) = Unit
}

/**
 * One of the user's own playlists, put forward like any other recommendation; the pill plays it.
 * [play] does the loading, since the songs live in the database and this runs on a tap.
 */
class PlaylistPickItem(
    val id: String,
    val name: String,
    override val thumbnailUrl: String?,
    private val play: () -> Unit,
) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel = HeroLabel("From your playlists")
    override val mainText: String = name
    override val subText: String = ""
    override val mediaId: String? = null
    override val songTitle: String? = null
    override val artistName: String? = null
    override val albumTitle: String? = null

    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) = play()

    override fun equals(other: Any?): Boolean = other is PlaylistPickItem && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

/**
 * A brand-new install has nothing played yet, so the hero starts from what onboarding brought in:
 * the playlists just imported, or else the artists picked on "Let's build your home".
 */
data class StarterItem(
    val kind: Kind,
    val id: String,
    val title: String,
    val detail: String,
    override val thumbnailUrl: String?,
) : HeroCarouselItem {
    enum class Kind { Playlist, Artist }

    override fun label(isPlaying: Boolean): HeroLabel =
        HeroLabel(if (kind == Kind.Playlist) "From your import" else "Recommended from")
    override val mainText: String = title
    override val subText: String = ""
    override val mediaId: String? = null
    override val songTitle: String? = null
    override val artistName: String? = null
    override val albumTitle: String? = null
    override val opensPage: Boolean = true

    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) {
        navController.navigate(if (kind == Kind.Playlist) "local_playlist/$id" else "artist/$id")
    }
}

/** a new install's hero card: a song by one of the artists picked in onboarding; the pill plays it */
data class StarterSongItem(
    val song: SongItem,
    val pickedArtist: String,
    val pickedArtistThumbnail: String?,
) : HeroCarouselItem {
    override fun label(isPlaying: Boolean): HeroLabel =
        if (isPlaying) HeroLabel("Now playing")
        else HeroLabel("Recommended from", pickedArtistThumbnail, pickedArtist, roundCover = true)
    override val mainText: String = song.title
    override val subText: String = song.artists.joinToString(", ") { it.name }
    override val thumbnailUrl: String? = song.thumbnail
    override val mediaId: String? = song.id
    override val songTitle: String? = song.title
    override val artistName: String? = song.artists.joinToString { it.name }
    override val albumTitle: String? = song.album?.name

    override fun onPlay(playerConnection: PlayerConnection, navController: NavController) {
        playerConnection.playQueue(YouTubeQueue(song.endpoint ?: WatchEndpoint(videoId = song.id), song.toMediaMetadata()))
    }
}

/** an artist the recommendation shares with the song it came from, if there is one */
private fun sharedArtist(item: DailyDiscoverItem): String? {
    val rec = item.recommendation as? SongItem ?: return null
    return rec.artists.firstOrNull { artist ->
        item.seed.artists.any { seedArtist ->
            (artist.id != null && artist.id == seedArtist.id) || artist.name.equals(seedArtist.name, ignoreCase = true)
        }
    }?.name
}

/**
 * the wording for each recommendation: "You may also like" leads, then whatever is most specific
 * to that card (the same artist, a song you liked, a song you played), never the same phrase twice
 * in a row and no phrase more than twice in all.
 */
private fun discoverReasons(items: List<DailyDiscoverItem>): List<DiscoverReason> {
    val picked = ArrayList<DiscoverReason>(items.size)
    items.forEachIndexed { index, item ->
        val options = buildList {
            if (index == 0) add(DiscoverReason.YouMayAlsoLike)
            if (sharedArtist(item) != null) add(DiscoverReason.MoreFrom)
            if (item.seed.song.liked) add(DiscoverReason.BecauseYouLike)
            add(DiscoverReason.BecauseYouPlayed)
            add(DiscoverReason.InspiredBy)
            add(DiscoverReason.YouMayAlsoLike)
        }
        picked += options.firstOrNull { option ->
            option != picked.lastOrNull() && picked.count { it == option } < 2
        } ?: DiscoverReason.YouMayAlsoLike
    }
    return picked
}

@Composable
fun HeroCarousel(
    keepListening: List<LocalItem>?,
    lastPlayedSong: MediaMetadata?,
    dailyDiscover: List<DailyDiscoverItem>?,
    playerConnection: PlayerConnection,
    navController: NavController,
    heroScrollProgressProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    /** keep the bones up whatever has loaded (Home's launch intro is still playing) */
    hold: Boolean = false,
    gachaPool: List<MediaMetadata> = emptyList(),
    starterItems: List<HeroCarouselItem> = emptyList(),
    playlistPicks: List<PlaylistPickItem> = emptyList(),
    similarRecommendations: List<SimilarRecommendation>? = null,
    onRepeat: List<com.mochyy.mochymusic.db.entities.Song>? = null,
    forgottenFavorites: List<com.mochyy.mochymusic.db.entities.Song>? = null,
) {
    val showGacha = gachaPool.size >= GachaMinPool
    // only the first song of each list is used, so a reload that reshuffles the tails doesn't
    // rebuild the pager
    val extras = remember(
        similarRecommendations?.map { it.title.id to it.items.firstOrNull()?.id },
        onRepeat?.firstOrNull()?.id,
        forgottenFavorites?.firstOrNull()?.id,
    ) { extraHeroPicks(similarRecommendations, onRepeat, forgottenFavorites) }
    val carouselItems = remember(keepListening, lastPlayedSong, dailyDiscover, showGacha, starterItems, playlistPicks, extras) {
        heroCarouselItems(lastPlayedSong, dailyDiscover, extras, playlistPicks, keepListening, starterItems, showGacha)
    }

    val heroHeight = heroHeight()

    // scrolling away it does exactly what a playlist cover does: rides up, zooms, goes soft, fades.
    // the list already carries it up, so it only adds a little on top
    ScrollAwayCover(
        progress = heroScrollProgressProvider,
        follow = 0.2f,
        softFrom = HeroStillFrom,
        modifier = modifier
            .heroTuck()
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        // null while the first load is still out, so the bones get their reveal; an empty list
        // after loading means there's nothing at all to put forward yet
        RevealWhenLoaded(
            data = if (hold) null else carouselItems.takeIf { it.isNotEmpty() || !isLoading },
            modifier = Modifier.fillMaxSize(),
            bones = { HeroBones() },
        ) { items ->
            if (items.isEmpty()) {
                QuietHero(modifier = Modifier.fillMaxSize())
            } else {
                HeroPager(
                    items = items,
                    playerConnection = playerConnection,
                    navController = navController,
                    heroScrollProgressProvider = heroScrollProgressProvider,
                    gachaPool = gachaPool,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroPager(
    items: List<HeroCarouselItem>,
    playerConnection: PlayerConnection,
    navController: NavController,
    heroScrollProgressProvider: () -> Float,
    gachaPool: List<MediaMetadata>,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val nowPlaying by playerConnection.mediaMetadata.collectAsState()

    // with one card an infinite pager wraps onto itself and auto-advances against nothing,
    // so a single card is just a static hero
    val isSingleItem = items.size == 1
    val pagerState = rememberPagerState(
        initialPage = if (isSingleItem) 0 else (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % items.size),
        pageCount = { if (isSingleItem) 1 else Int.MAX_VALUE },
    )

    // the gacha lives up here rather than in its page, so a roll survives the page being swiped
    // out of composition and back
    val gacha = remember { GachaState() }
    val sounds = remember { GachaSounds(context.applicationContext) }
    DisposableEffect(sounds) { onDispose { sounds.release() } }

    // the reel's covers are picked once; they're only picked again (while it's idle) if there were
    // too few songs to fill it and more have turned up, so the covers never shuffle under your eyes
    LaunchedEffect(gachaPool) {
        if (gacha.phase != GachaPhase.Idle) return@LaunchedEffect
        val available = gachaPool.distinctBy { it.id }.size
        if (gacha.candidates.isEmpty() || (gacha.candidates.size < GachaCandidates && available > gacha.candidates.size)) {
            gacha.candidates = pickGachaCandidates(gachaPool)
        }
    }

    // every cover that passes the pointer ticks, like a case opening: a click, a buzz you can
    // feel, and the pointer knocked back a little
    LaunchedEffect(gacha) {
        var lastBuzz = 0L
        snapshotFlow { gacha.reel.value.roundToInt() }
            .drop(1)
            .collect {
                if (gacha.phase != GachaPhase.Rolling) return@collect
                sounds.tick()
                val now = SystemClock.uptimeMillis()
                if (now - lastBuzz >= 45L) {
                    lastBuzz = now
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
                launch {
                    gacha.pointerKick.snapTo(1f)
                    gacha.pointerKick.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 900f))
                }
            }
    }

    fun roll() {
        if (gacha.phase == GachaPhase.Rolling || gacha.candidates.size < 2) return
        scope.launch {
            gacha.phase = GachaPhase.Rolling
            sounds.prepare()
            try {
                gacha.candidates = refreshHiddenCandidates(gacha, gachaPool)
                val candidates = gacha.candidates
                val count = candidates.size
                val winner = Random.nextInt(count)
                // a random length every time, nine seconds at the most
                val duration = Random.nextInt(GachaMinRollMillis, GachaMaxRollMillis + 1)
                val start = gacha.reel.value.roundToInt()
                gacha.reel.snapTo(start.toFloat())
                // whole turns first (more of them on a longer roll), then on to the winner
                val spins = count * (2 + duration / 2500)
                val landing = start + spins + (winner - (start + spins)).mod(count)
                gacha.reel.animateTo(landing.toFloat(), tween(duration, easing = GachaRollEasing))

                val won = candidates[winner]
                gacha.result = won
                gacha.phase = GachaPhase.Landed
                sounds.win()
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                launch {
                    gacha.landPop.snapTo(1f)
                    gacha.landPop.animateTo(1.1f, tween(120))
                    gacha.landPop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 320f))
                }
                launch {
                    gacha.burst.snapTo(0.001f)
                    gacha.burst.animateTo(1f, tween(1100, easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)))
                    gacha.burst.snapTo(0f)
                }
                playerConnection.playQueue(YouTubeQueue.radio(won))
            } finally {
                if (gacha.phase == GachaPhase.Rolling) gacha.phase = GachaPhase.Idle
            }
        }
    }

    // roll() is a new local function on every recomposition; the gacha page gets one stable callback that calls the latest
    val latestRoll = androidx.compose.runtime.rememberUpdatedState { roll() }
    val onRoll = remember { { latestRoll.value() } }

    // a playing canvas repaints the hero, and both blur bands with it, on every video frame. while
    // Home is receding behind an opened playlist, or coming back from one, that lands on top of the
    // transition and makes it stutter, so the video holds still until Home has settled. coming back,
    // the player isn't even built until then: Home is composed fresh on the way back.
    val navTransition = LocalNavAnimatedContentScope.current?.transition
    // held as state and read only where it's used: read up here, the start of every page transition
    // recomposed the whole carousel and each page in it, right in the transition's heaviest frame
    val navSettledState = remember(navTransition) {
        derivedStateOf {
            navTransition == null ||
                (navTransition.currentState == EnterExitState.Visible && navTransition.targetState == EnterExitState.Visible)
        }
    }
    // the same goes for the gacha's drift and the auto-advance: Home sinking back behind a page, or
    // the player sliding up over it, keeps the app in one cached picture, and any of them moving
    // made that whole picture render again for every one of its frames
    val obscured = com.mochyy.mochymusic.LocalAppContentObscured.current
    val heroHeld = remember(obscured, navSettledState) { { !navSettledState.value || obscured.value } }

    if (!isSingleItem) {
        val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
        LaunchedEffect(pagerState, items.size, isDragged, gacha.phase) {
            if (isDragged) return@LaunchedEffect
            // restarts after every manual swipe, so the next auto-advance never lands right on top of it
            while (true) {
                val onGacha = items[pagerState.currentPage.mod(items.size)] is GachaItem
                // the gacha card waits for you: twice as long while it's idle, and once it has
                // rolled it stays until you swipe on
                if (onGacha && gacha.phase != GachaPhase.Idle) return@LaunchedEffect
                delay(if (onGacha) HeroAdvanceMillis * 2 else HeroAdvanceMillis)
                // scrolled away, there's nothing to show the next card to; mid-transition or under
                // the player nobody's looking either
                if (heroScrollProgressProvider() > HeroStillFrom || heroHeld()) continue
                pagerState.animateScrollToPage(
                    pagerState.currentPage + 1,
                    animationSpec = tween(HeroSlideMillis, easing = HeroSlideEasing),
                )
            }
        }

        // warm the next cover so a swipe never fades in from an empty page
        LaunchedEffect(pagerState.currentPage, items) {
            items[(pagerState.currentPage + 1).mod(items.size)].thumbnailUrl?.let { url ->
                context.imageLoader.enqueue(
                    ImageRequest.Builder(context).data(url.resize(1200, 1200)).build()
                )
            }
        }
    }

    val (disableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)
    val edgeBlur = !disableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var canvasAttached by remember { mutableStateOf(false) }
    LaunchedEffect(navSettledState) {
        snapshotFlow { navSettledState.value }.first { it }
        canvasAttached = true
    }

    // a swipe bows the wipe's edge round, an auto-advance keeps it straight. it's set as soon as a
    // finger lands and held through the fling until the pager settles, eased so grabbing a card
    // mid-advance bends the edge instead of snapping it
    var userSwipe by remember { mutableStateOf(false) }
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging) { if (dragging) userSwipe = true }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { if (!it) userSwipe = false }
    }
    val wipeCurve by animateFloatAsState(if (userSwipe) 1f else 0f, tween(260), label = "heroWipeCurve")

    // an upright tablet: cards side by side, the next one peeking in on the right; on its side each card fills the screen and wipes like the phone's
    val peek = isWideLayout() && !isLandscapeWindow()

    // the hero animates nearly all the time (video, gacha drift, the switch); bounded, that only
    // ever repaints the hero instead of the whole screen and every glass surface sampling it
    Box(modifier = Modifier.fillMaxSize().damageBoundary()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalHeroPeek provides peek) {
        HorizontalPager(
            state = pagerState,
            // no swiping off a reel that's still spinning
            userScrollEnabled = !isSingleItem && gacha.phase != GachaPhase.Rolling,
            pageSize = if (peek) PeekPageSize else androidx.compose.foundation.pager.PageSize.Fill,
            pageSpacing = if (peek) PeekPageGap else 0.dp,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page.mod(items.size)]
            if (item is GachaItem) {
                GachaPage(
                    state = gacha,
                    pageOffset = { pagerState.offsetOf(page) },
                    isResultPlaying = isPlaying && gacha.result != null && nowPlaying?.id == gacha.result?.id,
                    wipeCurve = { wipeCurve },
                    transitionBlur = edgeBlur,
                    held = heroHeld,
                    onShown = { sounds.prepare() },
                    onRoll = onRoll,
                )
            } else {
                HeroPage(
                    item = item,
                    pageOffset = { pagerState.offsetOf(page) },
                    isThisPlaying = isPlaying && nowPlaying?.id == item.mediaId,
                    wipeCurve = { wipeCurve },
                    isPagerMoving = { pagerState.isScrollInProgress },
                    canvasAttached = canvasAttached,
                    // a peeking card holds its canvas still, so only the one in front decodes video
                    canvasMayPlay = { navSettledState.value && (!peek || pagerState.settledPage == page) },
                    edgeBlur = edgeBlur,
                    heroScrollProgressProvider = heroScrollProgressProvider,
                    onPrimaryClick = {
                        if (isPlaying && nowPlaying?.id == item.mediaId) {
                            playerConnection.pause()
                        } else {
                            item.onPlay(playerConnection, navController)
                        }
                    },
                )
            }
        }
        }
    }
}

/** signed distance of [page] from the settled position: 0 on screen, +1 one page to the right */
private fun PagerState.offsetOf(page: Int): Float =
    (page - currentPage) - currentPageOffsetFraction

@Composable
private fun HeroPage(
    item: HeroCarouselItem,
    pageOffset: () -> Float,
    wipeCurve: () -> Float,
    isThisPlaying: Boolean,
    isPagerMoving: () -> Boolean,
    canvasAttached: Boolean,
    canvasMayPlay: () -> Boolean,
    edgeBlur: Boolean,
    heroScrollProgressProvider: () -> Float,
    onPrimaryClick: () -> Unit,
) {
    val peek = LocalHeroPeek.current
    val tv = isTvLayout()
    PageWipe(pageOffset = pageOffset, curve = wipeCurve, blur = edgeBlur, peek = peek) {
        HeroMedia(
            item = item,
            modifier = Modifier.pageMediaDrift(pageOffset, peek),
            edgeBlur = edgeBlur,
            isPagerMoving = isPagerMoving,
            canvasAttached = canvasAttached,
            canvasMayPlay = canvasMayPlay,
            heroScrollProgressProvider = heroScrollProgressProvider,
        )

        // per page and under the text: drawn once over the whole pager it would dim the titles too
        if (tv) TvHeroScrims() else HeroScrims()

        Box(
            modifier = Modifier
                .matchParentSize()
                .pageTextDrift(pageOffset, peek)
        ) {
            HeroText(
                label = item.label(isThisPlaying),
                title = if (item.subText.isNotEmpty()) "${item.mainText} - ${item.subText}" else item.mainText,
                pill = when {
                    item.opensPage -> HeroPillKind.View
                    isThisPlaying -> HeroPillKind.Pause
                    else -> HeroPillKind.Play
                },
                onPillClick = onPrimaryClick,
                // on a tv the words stop short of the cover at the right
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .then(if (tv) Modifier.padding(end = heroHeight() * TvCoverFraction + TvCoverEndInset) else Modifier)
            )
        }
    }
}

@Composable
private fun HeroMedia(
    item: HeroCarouselItem,
    modifier: Modifier = Modifier,
    edgeBlur: Boolean,
    isPagerMoving: () -> Boolean,
    canvasAttached: Boolean,
    canvasMayPlay: () -> Boolean,
    heroScrollProgressProvider: () -> Float,
) {
    val context = LocalContext.current
    val canvasEnabled by rememberPreference(CanvasThumbnailAnimationKey, defaultValue = true)
    val canvasWifiOnly by rememberPreference(CanvasWifiOnlyKey, defaultValue = true)
    var canvasArtwork by remember(item.mediaId) { mutableStateOf<CanvasArtwork?>(null) }

    LaunchedEffect(item.mediaId, canvasEnabled, canvasWifiOnly) {
        val id = item.mediaId ?: return@LaunchedEffect
        if (!canvasEnabled) return@LaunchedEffect

        val connectivityManager = context.getSystemService<android.net.ConnectivityManager>()
        if (canvasWifiOnly && connectivityManager?.isActiveNetworkMetered == true) return@LaunchedEffect
        // data saver keeps the cover still, a canvas is a video loop
        if (com.mochyy.mochymusic.utils.DataSaver.isActive(context)) return@LaunchedEffect

        CanvasArtworkPlaybackCache.get(id)?.let {
            canvasArtwork = it
            return@LaunchedEffect
        }

        val song = normalizeCanvasSongTitle(item.songTitle ?: "")
        val artist = normalizeCanvasArtistName(item.artistName ?: "")
        if (song.isBlank() || artist.isBlank()) return@LaunchedEffect

        val storefront = Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: "us"
        val fetched = withContext(Dispatchers.IO) {
            MonochromeApiCanvas.getBySongArtist(song, artist, item.albumTitle)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                ?: AppleMusicCanvasProvider.getBySongArtist(song, artist, item.albumTitle, storefront)
                    ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
        }
        if (fetched != null) {
            canvasArtwork = fetched
            CanvasArtworkPlaybackCache.put(id, fetched)
        }
    }

    // derived so scrolling only recomposes when the hero starts moving, not on every frame. the
    // video stops as soon as it does, so the scroll's blur is working from a still frame
    val heroAtRest by remember { derivedStateOf { heroScrollProgressProvider() < HeroStillFrom } }

    val imageRequest = remember(item.thumbnailUrl, context) {
        ImageRequest.Builder(context)
            .data(item.thumbnailUrl?.resize(1200, 1200))
            .crossfade(300)
            .build()
    }
    val mediaLayer = rememberGraphicsLayer()

    Box(modifier = modifier.fillMaxSize()) {
        // the page's media is recorded once and drawn again, blurred, into the edge bands below.
        // they're siblings of this box, never inside it, so the layer can't end up drawing itself.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (edgeBlur) {
                        Modifier.drawWithContent {
                            mediaLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(mediaLayer)
                        }
                    } else Modifier
                )
        ) {
            val art: @Composable (Modifier) -> Unit = { artModifier ->
                Box(artModifier) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (canvasAttached && canvasEnabled && canvasArtwork?.preferredAnimationUrl != null) {
                        CanvasArtworkPlayer(
                            primaryUrl = canvasArtwork?.preferredAnimationUrl,
                            fallbackUrl = null,
                            // also held while the open player covers Home: it kept decoding and drawing
                            // frames under it. It picks up where it was once Home shows again.
                            isPlaying = canvasMayPlay() && heroAtRest && !isPagerMoving() &&
                                !com.mochyy.mochymusic.LocalAppContentObscured.current.value,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            if (isTvLayout()) TvHeroArt(item, art) else art(Modifier.fillMaxSize())
        }

        // on a tv the words sit on the wash beside the cover, and a band there would only blur the cover's lower half
        if (edgeBlur && !isTvLayout()) {
            HeroBlurBand(mediaLayer, atTop = true, heightFraction = 0.46f, radius = 22f)
            HeroBlurBand(mediaLayer, atTop = false, heightFraction = 0.5f, radius = 30f)
        }
    }
}

// a square cover on a tv: the cover itself at the hero's right, over a soft wash of its own colours, instead of one huge crop across the screen
@Composable
private fun TvHeroArt(item: HeroCarouselItem, art: @Composable (Modifier) -> Unit) {
    val context = LocalContext.current
    // a small copy is plenty for a wash, and soft on its own where there's no blur (android 11 and older)
    val washRequest = remember(item.thumbnailUrl, context) {
        ImageRequest.Builder(context)
            .data(item.thumbnailUrl?.resize(96, 96))
            .size(96)
            .crossfade(300)
            .build()
    }
    val coverSize = heroHeight() * TvCoverFraction
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = washRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.6f
                    // decal, so the wash fades out at its edges instead of smearing them into a glow
                    renderEffect = androidx.compose.ui.graphics.BlurEffect(36.dp.toPx(), 36.dp.toPx(), androidx.compose.ui.graphics.TileMode.Decal)
                    clip = true
                },
        )
        art(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = TvCoverEndInset)
                .size(coverSize)
                .dropShadow(TvCoverShape, TvCoverShadow)
                .clip(TvCoverShape)
        )
    }
}

/**
 * progressive blur: the page's media drawn again, blurred, and faded out toward the middle of the
 * hero, so it's strongest right behind the top bar and behind the title and button.
 */
@Composable
private fun BoxScope.HeroBlurBand(
    mediaLayer: GraphicsLayer,
    atTop: Boolean,
    heightFraction: Float,
    radius: Float,
) {
    val mask = remember(atTop) {
        if (atTop) {
            Brush.verticalGradient(0f to Color.Black, 0.35f to Color.Black.copy(alpha = 0.85f), 1f to Color.Transparent)
        } else {
            Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Black.copy(alpha = 0.85f), 1f to Color.Black)
        }
    }
    // One layer per band, at the resolution DownscaledBlur picks for the radius: the mask runs in
    // the blur's own effect instead of an offscreen layer around it, and the media is blurred from
    // a half-size copy. Both bands redraw with every frame of the hero's video, so it adds up.
    // high quality on android 13+ grows the blur toward the edge in one pass; a faded fixed blur bloomed around bright parts
    val smooth = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
        LocalBlurQuality.current == com.mochyy.mochymusic.constants.BlurQuality.HIGH
    DownscaledBlur(
        radius = { BlurEffectCache.effectiveRadius(radius) },
        tileMode = Shader.TileMode.CLAMP,
        mask = mask,
        growDown = if (smooth) !atTop else null,
        modifier = Modifier
            .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(heightFraction),
    ) { fullSize ->
        val dy = if (atTop) 0f else fullSize.height - mediaLayer.size.height.toFloat()
        translate(top = dy) { drawLayer(mediaLayer) }
    }
}

/**
 * the Figma dim (the cover sits at 64% on black) and the top and bottom gradients, as one gradient.
 * black at a over black at b is black at 1 - (1 - a)(1 - b), and that's linear in each, so folding
 * the dim into the gradients' stops gives the same picture: one pass over the hero instead of three
 * (it's under every frame of the hero's video, the scroll-away and the switch)
 */
@Composable
private fun BoxScope.HeroScrims() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawWithCache {
                val topEnd = 0.463f
                val bottomStart = 0.533f
                val bottomEnd = 0.989f
                val scrim = Brush.verticalGradient(
                    0f to heroScrimBlack(0.86f),
                    0.475f * topEnd to heroScrimBlack(0.59f),
                    topEnd to heroScrimBlack(0f),
                    bottomStart to heroScrimBlack(0f),
                    bottomStart + 0.457f * (bottomEnd - bottomStart) to heroScrimBlack(0.59f),
                    bottomEnd to heroScrimBlack(1f),
                    1f to heroScrimBlack(1f),
                    startY = 0f,
                    endY = size.height,
                )
                onDrawBehind { drawRect(scrim) }
            }
    )
}

private const val HeroDim = 0.36f

// a tv's hero: dark behind the words at the left and into the rows below, the cover at the right left as bright as it is
@Composable
private fun BoxScope.TvHeroScrims() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawWithCache {
                val behindWords = Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.7f),
                    0.3f to Color.Black.copy(alpha = 0.45f),
                    0.58f to Color.Transparent,
                    endX = size.width,
                )
                val intoRows = Brush.verticalGradient(
                    0.8f to Color.Transparent,
                    1f to Color.Black,
                    endY = size.height,
                )
                onDrawBehind {
                    drawRect(behindWords)
                    drawRect(intoRows)
                }
            }
    )
}

/** black at [gradient], over the 36% dim */
private fun heroScrimBlack(gradient: Float): Color = Color.Black.copy(alpha = 1f - (1f - HeroDim) * (1f - gradient))

/** label, title and pill, sitting on the hero's bottom left like the Figma */
@Composable
private fun HeroText(
    label: HeroLabel,
    title: String,
    pill: HeroPillKind,
    onPillClick: () -> Unit,
    modifier: Modifier = Modifier,
    pillEnabled: Boolean = true,
) {
    val bottom = if (isWideLayout() && isLandscapeWindow() && !isTvLayout()) LandscapeHeroTextBottom else 64.dp
    Column(
        modifier = modifier.padding(start = HomeContentInset, end = HomeContentInset, bottom = bottom)
    ) {
        HeroLabelLine(label)
        AnimatedContent(
            targetState = title,
            transitionSpec = { heroLabelSwap() },
            label = "heroTitle",
        ) { text ->
            Text(
                text = text,
                style = HomeTitleStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(11.dp))
        HeroPill(kind = pill, onClick = onPillClick, enabled = pillEnabled)
    }
}

/** "Because you like [tiny cover] Song": the reason, then the song it grew out of */
@Composable
private fun HeroLabelLine(label: HeroLabel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label.text,
            style = HomeLabelStyle,
            color = HeroLabelColor,
            maxLines = 1,
        )
        if (label.coverUrl != null) {
            Spacer(Modifier.width(6.dp))
            AsyncImage(
                model = label.coverUrl.resize(120, 120),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(18.dp)
                    .clip(if (label.roundCover) CircleShape else RoundedCornerShape(5.dp))
                    .background(BoneColor),
            )
        }
        if (label.subject != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = label.subject,
                style = HomeLabelStyle,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

private enum class HeroPillKind { Play, Pause, View, Roll, Rolling }

/**
 * the 33dp light pill from the Figma: dark glyph and label on white at 64%. it squishes under the
 * finger, and when it changes (play to pause, roll to rolling) the new content pops in while the
 * pill eases to its new width.
 */
@Composable
private fun HeroPill(
    kind: HeroPillKind,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressBounce(interaction)
            .height(33.dp)
            .clip(RoundedCornerShape(50))
            .background(HeroPillFill)
            .clickable(interactionSource = interaction, indication = RemoteFocus, enabled = enabled, onClick = onClick)
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = { heroGlyphSwap() },
            label = "heroPill",
        ) { current ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(33.dp)
                    .padding(
                        start = when (current) {
                            HeroPillKind.Play, HeroPillKind.Pause -> 21.dp
                            HeroPillKind.View -> 17.dp
                            HeroPillKind.Roll, HeroPillKind.Rolling -> 11.dp
                        },
                        end = if (current == HeroPillKind.Roll || current == HeroPillKind.Rolling) 13.dp else 17.dp,
                    )
            ) {
                when (current) {
                    HeroPillKind.Play, HeroPillKind.Pause -> {
                        Icon(
                            painter = painterResource(
                                if (current == HeroPillKind.Pause) R.drawable.ic_untitled_pause else R.drawable.ic_untitled_play
                            ),
                            contentDescription = null,
                            tint = HeroPillContent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                    }
                    HeroPillKind.Roll, HeroPillKind.Rolling -> {
                        GachaDie(spinning = current == HeroPillKind.Rolling)
                        Spacer(Modifier.width(8.dp))
                    }
                    HeroPillKind.View -> Unit
                }
                Text(
                    text = when (current) {
                        HeroPillKind.Play -> "Play"
                        HeroPillKind.Pause -> "Pause"
                        HeroPillKind.View -> "View"
                        HeroPillKind.Roll -> "roll!!"
                        HeroPillKind.Rolling -> "rolling!!"
                    },
                    style = HomeLabelStyle,
                    color = HeroPillContent,
                )
                if (current == HeroPillKind.View) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        painter = painterResource(R.drawable.arrow_forward),
                        contentDescription = null,
                        tint = HeroPillContent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun <S> AnimatedContentTransitionScope<S>.heroGlyphSwap(): ContentTransform =
    (scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 650f)) + fadeIn(tween(130)))
        .togetherWith(scaleOut(targetScale = 0.6f, animationSpec = tween(110)) + fadeOut(tween(90)))
        .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.75f, stiffness = 520f) })

private fun <S> AnimatedContentTransitionScope<S>.heroLabelSwap(): ContentTransform =
    (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 3 } + fadeIn(tween(220)))
        .togetherWith(slideOutVertically(tween(160)) { -it / 3 } + fadeOut(tween(140)))
        .using(SizeTransform(clip = false))

/** drawn full height, but the list only makes room for all but the tuck */
private fun Modifier.heroTuck(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val tuck = HeroSectionTuck.roundToPx()
    layout(placeable.width, (placeable.height - tuck).coerceAtLeast(0)) {
        placeable.place(0, 0)
    }
}

/** the hero's loading look on its own, sized and placed exactly like [HeroCarousel] (Home's shell) */
@Composable
fun HeroCarouselBones(modifier: Modifier = Modifier) {
    val heroHeight = heroHeight()
    Box(
        modifier = modifier
            .heroTuck()
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        HeroBones()
    }
}

/**
 * Nothing to put forward at all: no history, no import, no picked artists' songs (offline on a
 * brand-new install, say). A quiet card where the label and title would be, never an empty hole.
 */
@Composable
private fun QuietHero(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent))
        )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = HomeContentInset, end = HomeContentInset, bottom = 64.dp)
        ) {
            Text(text = "Nothing here yet", style = HomeLabelStyle, color = HeroLabelColor, maxLines = 1)
            Text(
                text = "Play something and it shows up here",
                style = HomeTitleStyle,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** what the hero looks like while the first load is still out */
@Composable
private fun HeroBones() {
    BonesHost(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Brush.verticalGradient(listOf(BoneColor.copy(alpha = 0.12f), Color.Transparent)))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = HomeContentInset, bottom = 64.dp)
            ) {
                Bone(width = 72.dp, height = 14.dp)
                Spacer(Modifier.height(8.dp))
                Bone(width = 220.dp, height = 22.dp)
                Spacer(Modifier.height(13.dp))
                Bone(width = 93.dp, height = 33.dp, shape = RoundedCornerShape(50))
            }
        }
    }
}

// ---- the gacha card ---------------------------------------------------------------------------

// the reel's geometry, in Figma points on the 527pt tall hero (frame 25:1508); scaled to the real hero
private const val GachaDesignHeight = 527f
private const val GachaItemSize = 187f
private const val GachaFocusSize = 221f
private const val GachaPitch = 207f
private const val GachaFocusGap = 17f
private const val GachaCenterY = 230.5f
private const val GachaOverhang = 28f
private const val GachaCornerRadius = 25f
private const val GachaPointerX = 120f
private const val GachaPointerY = 236f
private const val GachaPointerWidth = 68f
private const val GachaPointerHeight = 75f

private const val GachaCandidates = 10
private const val GachaMinPool = 4
private const val GachaMinRollMillis = 3500
private const val GachaMaxRollMillis = 9000

// quick off the mark, then a long slow-down so the last few covers crawl past the pointer
private val GachaRollEasing = CubicBezierEasing(0.15f, 0.45f, 0.2f, 1f)

private const val IdleStepSeconds = 1f / 30f
private const val IdleWakeMillis = 28L

private const val TopShadeEnd = 0.44f
private const val BottomShadeStart = 0.56f

private val WashStops = floatArrayOf(0f, 0.5f, 1f)
private val WanderStops = floatArrayOf(0f, 0.5f, 1f)
private val GlowStops = floatArrayOf(0f, 0.45f, 1f)

/**
 * A gradient made once at unit size and placed by its local matrix; it's only made again when one
 * of its three colours changes, so a still card stops allocating shaders.
 */
private class UnitShader(private val make: (IntArray) -> android.graphics.Shader) {
    private var cached: android.graphics.Shader? = null
    private var c0 = 0
    private var c1 = 0
    private var c2 = 0

    fun shader(a: Int, b: Int, c: Int, matrix: android.graphics.Matrix): android.graphics.Shader {
        val current = cached
        val shader = if (current != null && a == c0 && b == c1 && c == c2) {
            current
        } else {
            make(intArrayOf(a, b, c)).also {
                cached = it
                c0 = a
                c1 = b
                c2 = c
            }
        }
        shader.setLocalMatrix(matrix)
        return shader
    }
}

/** Eases [from] toward [to], landing exactly once it's within a shade, so the colour stops changing. */
private fun settle(from: Color, to: Color, fraction: Float): Color {
    val next = lerp(from, to, fraction)
    return if (abs(next.red - to.red) < 0.002f && abs(next.green - to.green) < 0.002f &&
        abs(next.blue - to.blue) < 0.002f && abs(next.alpha - to.alpha) < 0.002f
    ) to else next
}

private val GachaPointerColor = Color(0xFFF0908D)
private val GachaPipColor = Color(0xFF3C3C3C)

/** the two colours the card takes from a cover: a lit one for the glow and a deep one for the wash */
@Immutable
private class GachaTint(val light: Color, val deep: Color)

// the Figma pink and lavender, until the covers' own colours are known
private val GachaDefaultTint = GachaTint(light = Color(0xFFDEA0AC), deep = Color(0xFF7C82B4))

/**
 * the lit and deep colours of a cover, from a tiny copy of it. the lit one is pushed bright and the
 * deep one dark, so even a grey cover gives the card something to glow with.
 */
private suspend fun gachaTint(context: Context, url: String): GachaTint? = withContext(Dispatchers.IO) {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(64)
        .allowHardware(false)
        .build()
    val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return@withContext null
    val palette = Palette.from(bitmap).maximumColorCount(12).generate()
    val lit = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch ?: return@withContext null
    val deep = palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.mutedSwatch ?: lit
    GachaTint(
        light = shiftBrightness(lit.rgb, minValue = 0.78f, maxValue = 1f, maxSaturation = 0.8f),
        deep = shiftBrightness(deep.rgb, minValue = 0.28f, maxValue = 0.5f, maxSaturation = 0.85f),
    )
}

private fun shiftBrightness(rgb: Int, minValue: Float, maxValue: Float, maxSaturation: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(rgb, hsv)
    hsv[1] = hsv[1].coerceAtMost(maxSaturation)
    hsv[2] = hsv[2].coerceIn(minValue, maxValue)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * a black shade as a gradient whose alpha follows [alphaAt] across many stops. the old few-stop
 * shades bent sharply where a stop sat, which read as a visible edge; an eased curve has no corner
 * to see, and the paint is dithered so it doesn't band either.
 */
private fun easedShade(height: Float, alphaAt: (Float) -> Float): android.graphics.LinearGradient {
    val steps = 24
    val positions = FloatArray(steps + 1) { it / steps.toFloat() }
    val colors = IntArray(steps + 1) { android.graphics.Color.argb((alphaAt(positions[it]) * 255f).roundToInt(), 0, 0, 0) }
    return android.graphics.LinearGradient(0f, 0f, 0f, height, colors, positions, Shader.TileMode.CLAMP)
}

/**
 * the gacha card's colour field in one pass: the wash from the deep colour up to the lit one, three
 * blooms drifting through it (the covers above and below the pointer, and a turn of the lit hue), the wandering light under the text
 * and the glow behind the big cover, all over a slow warp so the colours flow round each other.
 * One opaque fill instead of three blended gradients over the whole card, and dithered so the
 * long ramps don't band.
 */
private const val GachaMeshShader = """
uniform float2 size;
uniform float time;
uniform float2 washFrom;
uniform float2 washDir;
uniform float3 wander;
uniform float3 glow;
uniform float glowCore;
uniform float3 bloomA;
uniform float3 bloomB;
uniform float3 bloomC;
layout(color) uniform half4 washA;
layout(color) uniform half4 washB;
layout(color) uniform half4 washC;
layout(color) uniform half4 lit;
layout(color) uniform half4 accentA;
layout(color) uniform half4 accentB;
layout(color) uniform half4 accentC;

// a light falling from core at the centre to mid at knee, then out to nothing, darkening as it
// fades the way the old gradients' transparent-black outer stop did
float3 light(float3 col, float3 tint, float d, float core, float mid, float knee) {
    float dd = clamp(d, 0.0, 1.0);
    float a;
    float3 rgb;
    if (dd < knee) {
        a = mix(core, mid, dd / knee);
        rgb = tint;
    } else {
        float u = (dd - knee) / (1.0 - knee);
        a = mid * (1.0 - u);
        rgb = tint * (1.0 - u);
    }
    return col * (1.0 - a) + rgb * a;
}

half4 main(float2 p) {
    float2 uv = p / size;
    float2 q = p + float2(
        sin(uv.y * 4.2 + time * 0.38) + 0.5 * sin(uv.x * 6.3 - time * 0.21),
        cos(uv.x * 3.6 - time * 0.31) + 0.5 * cos(uv.y * 5.1 + time * 0.27)
    ) * size.x * 0.03;

    float s = clamp(dot(q - washFrom, washDir) / dot(washDir, washDir), 0.0, 1.0);
    float3 col = s < 0.5
        ? mix(float3(washA.rgb), float3(washB.rgb), s * 2.0)
        : mix(float3(washB.rgb), float3(washC.rgb), s * 2.0 - 1.0);

    float bc = 1.0 - smoothstep(0.0, 1.0, length(q - bloomC.xy) / bloomC.z);
    col = mix(col, float3(accentC.rgb), bc * 0.4);
    float ba = 1.0 - smoothstep(0.0, 1.0, length(q - bloomA.xy) / bloomA.z);
    col = mix(col, float3(accentA.rgb), ba * 0.62);
    float bb = 1.0 - smoothstep(0.0, 1.0, length(q - bloomB.xy) / bloomB.z);
    col = mix(col, float3(accentB.rgb), bb * 0.55);

    col = light(col, float3(lit.rgb), length(q - wander.xy) / wander.z, 0.32, 0.1, 0.5);
    col = light(col, float3(lit.rgb), length(p - glow.xy) / glow.z, glowCore, 0.32, 0.45);

    float n = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    col += (n - 0.5) / 255.0;
    return half4(half3(col), 1.0);
}
"""

@androidx.annotation.RequiresApi(33)
private fun createGachaMesh(): Any = createAgslShader(GachaMeshShader)

private val GachaHsv = FloatArray(3)

/** [color] turned round the colour wheel by [degrees], its saturation and value scaled */
private fun shiftHue(color: Color, degrees: Float, saturation: Float, value: Float): Int {
    android.graphics.Color.colorToHSV(color.toArgb(), GachaHsv)
    GachaHsv[0] = (GachaHsv[0] + degrees).mod(360f)
    GachaHsv[1] = (GachaHsv[1] * saturation).coerceIn(0f, 0.9f)
    GachaHsv[2] = (GachaHsv[2] * value).coerceIn(0f, 1f)
    return android.graphics.Color.HSVToColor(GachaHsv)
}

@androidx.annotation.RequiresApi(33)
private fun drawGachaMesh(
    canvas: android.graphics.Canvas,
    shader: Any,
    paint: android.graphics.Paint,
    width: Float,
    height: Float,
    lit: Color,
    deep: Color,
    above: Color,
    below: Color,
    washFromX: Float,
    washDirX: Float,
    washDirY: Float,
    wanderX: Float,
    wanderY: Float,
    wanderR: Float,
    glowX: Float,
    glowY: Float,
    glowR: Float,
    glowCore: Float,
    time: Float,
) {
    shader.setAgslUniform("size", width, height)
    shader.setAgslUniform("time", time)
    shader.setAgslUniform("washFrom", washFromX, 0f)
    shader.setAgslUniform("washDir", washDirX, washDirY)
    shader.setAgslUniform("wander", wanderX, wanderY, wanderR)
    shader.setAgslUniform("glow", glowX, glowY, glowR)
    shader.setAgslUniform("glowCore", glowCore)
    // the blooms keep to the middle band of the card, the part the top and bottom shading leaves
    shader.setAgslUniform(
        "bloomA",
        width * (0.3f + 0.16f * sin(time * 0.17f + 0.7f)),
        height * (0.4f + 0.08f * kotlin.math.cos(time * 0.23f)),
        width * 0.68f,
    )
    shader.setAgslUniform(
        "bloomB",
        width * (0.72f + 0.12f * sin(time * 0.13f + 2.1f)),
        height * (0.64f + 0.07f * sin(time * 0.29f + 0.4f)),
        width * 0.72f,
    )
    shader.setAgslUniform(
        "bloomC",
        width * (0.55f + 0.2f * sin(time * 0.11f + 4.2f)),
        height * (0.24f + 0.06f * kotlin.math.cos(time * 0.19f + 1.1f)),
        width * 0.6f,
    )
    shader.setAgslColorUniform("washA", lerp(deep, lit, 0.55f).toArgb())
    shader.setAgslColorUniform("washB", lerp(deep, lit, 0.22f).toArgb())
    shader.setAgslColorUniform("washC", deep.toArgb())
    shader.setAgslColorUniform("lit", lit.toArgb())
    // the neighbouring covers' colours, lifted to sit with the lit one; a cover that's the same hue
    // as the one under the pointer gets turned a little round the wheel, so there's always a second
    // and third colour in the field
    shader.setAgslColorUniform("accentA", shiftHue(above, 26f, 1.05f, 0.95f))
    shader.setAgslColorUniform("accentB", shiftHue(below, -22f, 1.1f, 1.45f))
    shader.setAgslColorUniform("accentC", shiftHue(lit, 48f, 1f, 0.85f))
    paint.shader = shader.asAgslPaintShader()
    canvas.drawRect(0f, 0f, width, height, paint)
}

private enum class GachaPhase { Idle, Rolling, Landed }

@Stable
private class GachaState {
    var candidates by mutableStateOf<List<MediaMetadata>>(emptyList())
    var phase by mutableStateOf(GachaPhase.Idle)
    var result by mutableStateOf<MediaMetadata?>(null)

    /** reel position in covers: the cover at `round(reel) mod count` sits under the pointer */
    val reel = Animatable(0f)
    val pointerKick = Animatable(0f)
    val landPop = Animatable(1f)

    /** a ring and a flare from the winning cover; runs 0 to 1 once per win */
    val burst = Animatable(0f)

    /** song id -> the colours of its cover */
    val tints = mutableStateMapOf<String, GachaTint>()
}

private fun pickGachaCandidates(pool: List<MediaMetadata>): List<MediaMetadata> =
    pool.distinctBy { it.id }.shuffled().take(GachaCandidates)

/** swaps the covers that are out of sight for fresh ones, so every roll has something new on it */
private fun refreshHiddenCandidates(state: GachaState, pool: List<MediaMetadata>): List<MediaMetadata> {
    val current = state.candidates
    if (current.isEmpty()) return pickGachaCandidates(pool)
    val spare = pool
        .distinctBy { it.id }
        .filter { song -> current.none { it.id == song.id } }
        .shuffled()
        .iterator()
    val reel = state.reel.value
    return current.mapIndexed { index, song ->
        if (abs(reelDistance(index, reel, current.size)) > 2.5f && spare.hasNext()) spare.next() else song
    }
}

/** where cover [index] sits relative to the pointer, in slots: 0 under it, +1 the one below */
private fun reelDistance(index: Int, reel: Float, count: Int): Float {
    var d = (reel - index) % count
    if (d < -count / 2f) d += count
    if (d >= count / 2f) d -= count
    return d
}

/** the reel's sounds: a dry click per cover and a chime for the winner. made on first use, kept for the hero's life */
private class GachaSounds(private val context: Context) {
    private var pool: SoundPool? = null
    private var tickSound = 0
    private var winSound = 0
    private var lastTick = 0L

    fun prepare() {
        if (pool != null) return
        val created = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        tickSound = created.load(context, R.raw.gacha_tick, 1)
        winSound = created.load(context, R.raw.gacha_win, 1)
        pool = created
    }

    fun tick() {
        // at full speed the covers pass faster than clicks can sound apart; it becomes a rattle
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 28L) return
        lastTick = now
        pool?.play(tickSound, 0.5f, 0.5f, 1, 0, 0.94f + Random.nextFloat() * 0.12f)
    }

    fun win() {
        pool?.play(winSound, 0.75f, 0.75f, 2, 0, 1f)
    }

    fun release() {
        pool?.release()
        pool = null
    }
}

/**
 * the gacha card: a column of covers running down the right edge with the coral pointer aimed at
 * the big one in the middle. roll (or tap that cover) and the reel spins like a slot machine drum,
 * ticking past the pointer, slows right down, and lands on a song that starts playing.
 *
 * the card is coloured by whichever cover is under the pointer: its colours wash through the
 * background as the reel turns, a glow behind the big cover breathes while it waits and flares with
 * the speed, and a win sends a ring out from the winner. it all runs in the draw phase off a frame
 * clock that only ticks while the card is on screen.
 */
@Composable
private fun GachaPage(
    state: GachaState,
    pageOffset: () -> Float,
    isResultPlaying: Boolean,
    wipeCurve: () -> Float,
    transitionBlur: Boolean,
    /** Home is mid-transition or under the player: the card holds still */
    held: () -> Boolean,
    onShown: () -> Unit,
    onRoll: () -> Unit,
) {
    val context = LocalContext.current
    val rolling = state.phase == GachaPhase.Rolling
    val result = state.result

    // the sounds load when the card is first seen, so the very first roll already clicks
    LaunchedEffect(Unit) { onShown() }

    // every cover's colours, worked out once from a tiny copy
    val candidates = state.candidates
    LaunchedEffect(candidates) {
        candidates.forEach { song ->
            if (song.id in state.tints) return@forEach
            val url = song.thumbnailUrl ?: return@forEach
            gachaTint(context, url)?.let { state.tints[song.id] = it }
        }
    }

    // read only while drawing, so a new frame redraws the card without recomposing it
    val light = remember { mutableStateOf(GachaDefaultTint.light) }
    val deep = remember { mutableStateOf(GachaDefaultTint.deep) }
    // the covers just above and just below the pointer: their colours drift through the field too,
    // so it shifts colour as the reel turns instead of being one cover's wash
    val above = remember { mutableStateOf(GachaDefaultTint.light) }
    val below = remember { mutableStateOf(GachaDefaultTint.deep) }
    val clock = remember { mutableFloatStateOf(0f) }
    val speed = remember { mutableFloatStateOf(0f) }

    // nor does one hidden under the player or behind a page that's opening
    val currentHeld by androidx.compose.runtime.rememberUpdatedState(held)
    LaunchedEffect(state) {
        var lastNanos = 0L
        var lastReel = state.reel.value
        var idleTime = 0f
        var wasBusy = false
        while (isActive) {
            // the pager keeps its neighbours composed; a card off to the side doesn't need frames
            if (abs(pageOffset()) >= 0.999f || currentHeld()) {
                lastNanos = 0L
                snapshotFlow { abs(pageOffset()) < 0.999f && !currentHeld() }.first { it }
                continue
            }
            withFrameNanos { now ->
                val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
                lastNanos = now
                val reel = state.reel.value
                val count = state.candidates.size
                // covers per second, smoothed so the stretch and flare ease in and out with the spin
                val instant = abs(reel - lastReel) / dt.coerceAtLeast(1e-3f)
                speed.floatValue += (instant - speed.floatValue) * (1f - exp(-dt / 0.12f))
                lastReel = reel

                // the colours under the pointer, blended between the two covers either side of it.
                // the background trails them a little, so a fast spin washes rather than flashes
                var aboveTarget = GachaDefaultTint.light
                var belowTarget = GachaDefaultTint.deep
                val target = if (count == 0) {
                    GachaDefaultTint
                } else {
                    val base = floor(reel).toInt()
                    val between = reel - base
                    fun tintAt(offset: Int) = state.tints[state.candidates[(base + offset).mod(count)].id]
                    val from = tintAt(0) ?: GachaDefaultTint
                    val to = tintAt(1) ?: from
                    aboveTarget = lerp((tintAt(-1) ?: from).light, from.light, between)
                    belowTarget = lerp(to.deep, (tintAt(2) ?: to).deep, between)
                    GachaTint(lerp(from.light, to.light, between), lerp(from.deep, to.deep, between))
                }
                val busy = speed.floatValue > 0.05f || state.burst.value > 0f
                // at rest the slow sway and breathing only need ~30 steps a second: a step is
                // under 2px of a soft gradient, too small to see, at a quarter of 120 Hz's redraws
                idleTime += dt
                if (busy || idleTime >= IdleStepSeconds) {
                    val step = if (busy) dt else idleTime
                    idleTime = 0f
                    val follow = 1f - exp(-step / 0.22f)
                    light.value = settle(light.value, target.light, follow)
                    deep.value = settle(deep.value, target.deep, follow)
                    above.value = settle(above.value, aboveTarget, follow)
                    below.value = settle(below.value, belowTarget, follow)
                    clock.floatValue += step
                }
                wasBusy = busy
            }
            // idle, there's no need to wake on every vsync just to wait out the step
            if (!wasBusy) delay(IdleWakeMillis)
        }
    }

    val peek = LocalHeroPeek.current
    PageWipe(pageOffset = pageOffset, curve = wipeCurve, blur = transitionBlur, peek = peek) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().pageMediaDrift(pageOffset, peek)) {
            val unit = maxHeight / GachaDesignHeight
            val focus = unit * GachaFocusSize
            val coverShape = RoundedCornerShape(unit * GachaCornerRadius)
            val count = candidates.size

            // the wash, the glows and the win ring. Its own layer, so its per-frame redraw doesn't
            // re-record the reel or the shading. On Android 13+ the whole field is one shader pass
            // (see GachaMeshShader); below that the gradients are built once at unit size and only
            // re-coloured when the colours actually move; position and size change through each
            // shader's matrix, so an idle card allocates nothing per frame.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val focusPx = focus.toPx()
                        val overhangPx = (unit * GachaOverhang).toPx()
                        val glowX = w + overhangPx - focusPx / 2f
                        val glowY = (unit * GachaCenterY).toPx()
                        val mesh: Any? = if (Build.VERSION.SDK_INT >= 33) createGachaMesh() else null
                        val meshPaint = android.graphics.Paint()
                        val washPaint = android.graphics.Paint().apply { isDither = true }
                        val wanderPaint = android.graphics.Paint().apply { isDither = true }
                        val glowPaint = android.graphics.Paint().apply { isDither = true }
                        val ringPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            style = android.graphics.Paint.Style.STROKE
                        }
                        val wash = UnitShader { colors -> android.graphics.LinearGradient(0f, 0f, 1f, 0f, colors, WashStops, Shader.TileMode.CLAMP) }
                        val wander = UnitShader { colors -> android.graphics.RadialGradient(0f, 0f, 1f, colors, WanderStops, Shader.TileMode.CLAMP) }
                        val glow = UnitShader { colors -> android.graphics.RadialGradient(0f, 0f, 1f, colors, GlowStops, Shader.TileMode.CLAMP) }
                        val matrix = android.graphics.Matrix()
                        val values = FloatArray(9)
                        onDrawBehind {
                            val lit = light.value
                            val dark = deep.value
                            val t = clock.floatValue
                            val rush = (speed.floatValue / 30f).coerceIn(0f, 1f)
                            val win = state.burst.value
                            drawIntoCanvas { canvas ->
                                val native = canvas.nativeCanvas
                                // the deep colour fills the card and lightens towards the top right,
                                // the light swaying slowly so the card never sits still
                                val sway = sin(t * 0.35f) * w * 0.12f
                                val x0 = w * 0.85f + sway
                                val dx = (w * 0.15f - sway) - x0
                                val dy = h
                                // a second light wandering on the left, under the text
                                val wanderX = w * (0.18f + 0.08f * sin(t * 0.23f))
                                val wanderY = h * (0.55f + 0.1f * sin(t * 0.31f + 1.3f))
                                val wanderR = w * 0.7f
                                // the glow behind the big cover: breathing at rest, swelling with the
                                // spin, flaring on a win
                                val breathe = 0.05f * sin(t * 1.4f)
                                val flare = if (win > 0f) sin(win * PI.toFloat()) * 0.55f else 0f
                                val glowR = focusPx * (1.05f + breathe + rush * 0.35f + flare)
                                val glowY2 = glowY + sin(t * 0.8f) * focusPx * 0.04f
                                val glowCore = (0.75f + rush * 0.2f).coerceAtMost(1f)

                                if (mesh != null && Build.VERSION.SDK_INT >= 33) {
                                    drawGachaMesh(
                                        canvas = native,
                                        shader = mesh,
                                        paint = meshPaint,
                                        width = w,
                                        height = h,
                                        lit = lit,
                                        deep = dark,
                                        above = above.value,
                                        below = below.value,
                                        washFromX = x0,
                                        washDirX = dx,
                                        washDirY = dy,
                                        wanderX = wanderX,
                                        wanderY = wanderY,
                                        wanderR = wanderR,
                                        glowX = glowX,
                                        glowY = glowY2,
                                        glowR = glowR,
                                        glowCore = glowCore,
                                        time = t,
                                    )
                                } else {
                                    // a similarity transform keeps the unit gradient's bands at right angles
                                    values[0] = dx; values[1] = -dy; values[2] = x0
                                    values[3] = dy; values[4] = dx; values[5] = 0f
                                    values[6] = 0f; values[7] = 0f; values[8] = 1f
                                    matrix.setValues(values)
                                    washPaint.shader = wash.shader(
                                        lerp(dark, lit, 0.55f).toArgb(),
                                        lerp(dark, lit, 0.22f).toArgb(),
                                        dark.toArgb(),
                                        matrix,
                                    )
                                    native.drawRect(0f, 0f, w, h, washPaint)

                                    matrix.setScale(wanderR, wanderR)
                                    matrix.postTranslate(wanderX, wanderY)
                                    wanderPaint.shader = wander.shader(
                                        lit.copy(alpha = 0.32f).toArgb(),
                                        lit.copy(alpha = 0.1f).toArgb(),
                                        Color.Transparent.toArgb(),
                                        matrix,
                                    )
                                    native.drawCircle(wanderX, wanderY, wanderR, wanderPaint)

                                    matrix.setScale(glowR, glowR)
                                    matrix.postTranslate(glowX, glowY2)
                                    glowPaint.shader = glow.shader(
                                        lit.copy(alpha = glowCore).toArgb(),
                                        lit.copy(alpha = 0.32f).toArgb(),
                                        Color.Transparent.toArgb(),
                                        matrix,
                                    )
                                    native.drawCircle(glowX, glowY2, glowR, glowPaint)
                                }

                                // the win: a ring that leaves the winner and fades as it grows
                                if (win > 0f && win < 1f) {
                                    ringPaint.color = lerp(lit, Color.White, 0.5f).copy(alpha = (1f - win) * 0.9f).toArgb()
                                    ringPaint.strokeWidth = (1f - win) * 10.dp.toPx() + 1.dp.toPx()
                                    native.drawCircle(glowX, glowY, focusPx * (0.55f + win * 1.1f), ringPaint)
                                }
                            }
                        }
                    }
            )

            // white until a cover loads, as a painter rather than a background behind it: one draw
            // per cover means a fading cover can simply draw at lower alpha, instead of being
            // rendered offscreen and composited every frame (each one a full tile flush on Mali)
            val blankCover = remember { ColorPainter(Color.White) }
            candidates.forEachIndexed { index, song ->
                key(song.id) {
                    AsyncImage(
                        model = song.thumbnailUrl?.resize(544, 544),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = blankCover,
                        error = blankCover,
                        fallback = blankCover,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = unit * GachaOverhang, y = unit * GachaCenterY - focus / 2)
                            .size(focus)
                            .graphicsLayer {
                                val d = reelDistance(index, state.reel.value, count)
                                val near = (1f - abs(d)).coerceIn(0f, 1f)
                                val landed = if (near > 0.5f) state.landPop.value else 1f
                                val scale = (GachaItemSize + (GachaFocusSize - GachaItemSize) * near) / GachaFocusSize * landed
                                // a fast reel stretches its covers a touch along the spin
                                val stretch = 1f + (speed.floatValue / 120f).coerceAtMost(0.1f)
                                scaleX = scale
                                scaleY = scale * stretch
                                // anchored on the right edge, like the Figma, so the covers grow leftwards
                                transformOrigin = TransformOrigin(1f, 0.5f)
                                val slots = d * GachaPitch + GachaFocusGap * d.coerceIn(-1f, 1f)
                                translationY = slots * unit.toPx()
                                // a drum: covers tip away as they leave the middle
                                rotationX = (-d * 18f).coerceIn(-50f, 50f)
                                cameraDistance = 14f * density
                                // fade out towards the ends instead of popping out of sight
                                alpha = (2.7f - abs(d)).coerceIn(0f, 1f)
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                shape = coverShape
                                clip = true
                            },
                    )
                }
            }

            // the hero's own top and bottom shading, over the reel. Never changes, so it's recorded
            // once in its own layer. Each shade is clear beyond 44% of the height, so it's only
            // drawn over its own part: under half the area of two full-height fills
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
                    .drawWithCache {
                        val h = size.height
                        val shadePaint = android.graphics.Paint().apply { isDither = true }
                        val top = easedShade(h) { y -> 0.85f * (1f - smoothstep(0f, TopShadeEnd, y)).let { it * it } }
                        val bottom = easedShade(h) { y -> smoothstep(BottomShadeStart, 1f, y) }
                        onDrawBehind {
                            drawIntoCanvas { canvas ->
                                shadePaint.shader = top
                                canvas.nativeCanvas.drawRect(0f, 0f, size.width, h * TopShadeEnd, shadePaint)
                                shadePaint.shader = bottom
                                canvas.nativeCanvas.drawRect(0f, h * BottomShadeStart, size.width, h, shadePaint)
                            }
                        }
                    }
            )

            // the pointer, knocked back a little by every cover that ticks past it
            Icon(
                painter = painterResource(R.drawable.ic_untitled_play),
                contentDescription = null,
                tint = GachaPointerColor,
                modifier = Modifier
                    .offset(
                        x = unit * (GachaPointerX - GachaPointerWidth / 2f),
                        y = unit * (GachaPointerY - GachaPointerHeight / 2f),
                    )
                    .size(width = unit * GachaPointerWidth, height = unit * GachaPointerHeight)
                    .graphicsLayer {
                        val kick = state.pointerKick.value
                        translationX = -kick * 7.dp.toPx()
                        rotationZ = -kick * 9f
                        // a little pulse when it lands
                        val win = state.burst.value
                        val pop = if (win > 0f) 1f + 0.18f * sin(win * PI.toFloat()) else 1f
                        scaleX = pop
                        scaleY = pop
                    }
            )

            // the big cover is a roll button too
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = unit * GachaOverhang, y = unit * GachaCenterY - focus / 2)
                    .size(focus)
                    .clickable(
                        interactionSource = null,
                        indication = RemoteFocus,
                        enabled = !rolling,
                        onClick = onRoll,
                    )
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .pageTextDrift(pageOffset, peek)
        ) {
            HeroText(
                label = HeroLabel(
                    when {
                        result == null || rolling -> "Gacha here!"
                        isResultPlaying -> "Now playing"
                        else -> "You got"
                    }
                ),
                title = if (result == null || rolling) {
                    "Random plays"
                } else {
                    val artists = result.artists.joinToString(", ") { it.name }
                    if (artists.isNotEmpty()) "${result.title} - $artists" else result.title
                },
                pill = if (rolling) HeroPillKind.Rolling else HeroPillKind.Roll,
                pillEnabled = !rolling,
                onPillClick = onRoll,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

/** the die on the roll pill: a white rounded square showing five. it tumbles while the reel spins */
@Composable
private fun GachaDie(spinning: Boolean) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            while (true) {
                rotation.animateTo(rotation.value + 360f, tween(620, easing = LinearEasing))
            }
        } else {
            // settle on the nearest face, with a little wobble
            val rest = (rotation.value / 90f).roundToInt() * 90f
            rotation.animateTo(rest, spring(dampingRatio = 0.45f, stiffness = 300f))
        }
    }
    Canvas(
        modifier = Modifier
            .size(21.dp)
            .graphicsLayer { rotationZ = rotation.value }
    ) {
        drawRoundRect(color = Color.White, cornerRadius = CornerRadius(4.5.dp.toPx()))
        val pip = 2.1.dp.toPx()
        val near = size.width * 0.28f
        val far = size.width * 0.72f
        val mid = size.width / 2f
        listOf(
            Offset(near, near), Offset(far, near),
            Offset(mid, mid),
            Offset(near, far), Offset(far, far),
        ).forEach { drawCircle(color = GachaPipColor, radius = pip, center = it) }
    }
}
