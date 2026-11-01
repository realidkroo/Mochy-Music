// SongCarousel.kt
//
// The songs in a row, the way every player style switches songs: the one playing, the song before
// it to the left and the song after it to the right. A sideways swipe slides the row under the
// finger one to one, like a picture pager, and letting go carries the flick's speed into the settle. A skip from
// anywhere else (the buttons, the song ending, the queue) runs the row along the same way.
//
// Covers, records, titles and backgrounds all read their place from this one row, so they move as
// one. Where the row is counts in steps, one per song moved through, and never resets: a song
// changing in the middle of a slide changes nothing on screen. The song that was coming in is the
// same slot (same composition, picture already loaded) now in the middle, and the row just carries
// on to it.

package com.mochyy.mochymusic.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.ui.player.models.QueueItemData
import com.mochyy.mochymusic.ui.player.models.QueueState
import com.mochyy.mochymusic.ui.player.models.TrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

/** One song in the row, [step] its place in it. */
@Immutable
data class CarouselSong(
    val uid: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val step: Int,
)

/** The player's row, for everything inside it; null outside the player and on the selector's cards. */
val LocalSongCarousel = staticCompositionLocalOf<SongCarousel?> { null }

// how fast the row settles, critically damped like a picture pager: no bounce, a flick's speed carried straight in
private const val SettleOmega = 18f

// a frame longer than this counts as this long, so a hitch pauses the row for a moment instead of making it jump
private const val MaxFrameSec = 1f / 40f

// at rest once it's within half a pixel and barely moving, and only then is the last half pixel closed
private const val RestDistance = 0.0005f
private const val RestSpeed = 0.01f

// the swipe's skip goes once the row is this close to its song and this slow: still to the eye, so the new song's work can't make it jump
private const val SkipDistance = 0.004f
private const val SkipSpeed = 0.06f

// a skip the player never confirms stops holding the row on its song after this long
private const val SkipConfirmMs = 1_200L

// about how long a glide of one song takes to come to rest; a song changing later than this lands on a row that has stopped
private const val GlideStillMs = 400L

// let go past half a song and it moves on; short of that it eases back, unless it was flicked
private const val SwipeCommit = 0.5f

/** Past the first or last song, the row only gives this share of the finger's travel. */
private const val EdgeGive = 0.3f

@Stable
class SongCarousel internal constructor(private val scope: CoroutineScope) {
    /** Where the row is, in steps. At rest it's [step]. Read while drawing or placing only. */
    var position by mutableFloatStateOf(0f)
        private set

    /** The playing song's step: one more for each song forward, one less for each back. */
    var step by mutableIntStateOf(0)
        private set

    /** The songs in the row, left to right: the one before, the playing one, the one after. */
    var slots by mutableStateOf<List<CarouselSong>>(emptyList())
        private set

    /** True from a finger taking the row until it lets go. */
    var dragging by mutableStateOf(false)
        private set

    /** 0 at rest, 1 a whole song along: lifts a record's arm while the row moves under it. */
    val away: Float get() = abs(position - step)

    /** Songs [slot] is from the middle: 0 in place, +1 a whole song to the right. */
    fun offsetOf(slot: CarouselSong): Float = slot.step - position

    /** Songs the playing one is from the middle, for what only it has (its canvas, its video). */
    val playingOffset: Float get() = step - position

    // how far the slot holding this song is from the middle, or null when it isn't in the row
    fun offsetOfSong(mediaId: String): Float? {
        var best: Float? = null
        for (slot in slots) {
            if (slot.mediaId != mediaId) continue
            val o = offsetOf(slot)
            if (best == null || abs(o) < abs(best)) best = o
        }
        return best
    }

    /**
     * How much of [slot] a crossfading background shows: the song left of where the row is drawn
     * solid, the one right of it on top by how far the row has come. Slots drawn left to right
     * that way blend into exactly the mix of the two, and never let anything behind show through.
     */
    fun crossfadeAlpha(slot: CarouselSong): Float {
        val lower = floor(position).toInt()
        val fraction = position - lower
        return when (slot.step) {
            lower -> 1f
            lower + 1 -> if (slots.any { it.step == lower }) fraction else 1f
            else -> 0f
        }
    }

    // ---- the queue ----

    private var playing: CarouselSong? = null
    private var previousUid: Int? = null
    private var nextUid: Int? = null
    private var queueIndex = -1

    /** A song left for one that wasn't beside it (a jump in the queue): it slides away on its own. */
    private var outgoing: CarouselSong? = null

    // what the row was last built from, to build it again once a leaving song has gone
    private var lastQueue: QueueState? = null
    private var lastTrack: TrackInfo? = null

    /**
     * The queue as composition sees it. When the playing song changes, the row moves one step
     * toward wherever the new song was in it, or one step either way for a jump. Idempotent, so
     * composing twice changes nothing.
     */
    internal fun onQueue(queue: QueueState, track: TrackInfo) {
        lastQueue = queue
        lastTrack = track
        val items = queue.items
        val index = queue.currentIndex
        val now = if (index in items.indices) {
            songOf(items[index], step, track, current = true)
        } else {
            // no queue to read yet: the song the player says is on
            if (track.mediaId.isEmpty()) return
            CarouselSong(track.mediaId.hashCode(), track.mediaId, track.title, track.artist, track.album, track.thumbnailUrl, step)
        }
        val last = playing
        if (last != null && last.uid != now.uid) {
            // the song changed: a swipe's skip still waiting would be a second one, and a skip that was waiting on this is confirmed
            pendingSkip = null
            expectedStep = null
            confirmJob?.cancel()
            val delta = when (now.uid) {
                nextUid -> 1
                previousUid -> -1
                else -> if (index in 0 until queueIndex) -1 else 1
            }
            // a jump: the song that was playing isn't beside the new one, so it goes on its own
            outgoing = if (now.uid != nextUid && now.uid != previousUid) last else null
            step += delta
        }
        val current = now.copy(step = step)
        playing = current
        queueIndex = index
        previousUid = items.getOrNull(index - 1)?.uid
        nextUid = items.getOrNull(index + 1)?.uid

        val gone = outgoing
        val row = buildList {
            items.getOrNull(index - 1)?.let { add(songOf(it, step - 1, track, current = false)) }
            add(current)
            items.getOrNull(index + 1)?.let { add(songOf(it, step + 1, track, current = false)) }
            // the leaving song keeps its place; a neighbour sharing it waits until it's gone
            if (gone != null && none { it.uid == gone.uid }) {
                removeAll { it.step == gone.step }
                add(gone)
            }
        }.sortedBy { it.step }
        if (row != slots) slots = row
    }

    private fun songOf(item: QueueItemData, step: Int, track: TrackInfo, current: Boolean): CarouselSong {
        val isTrack = item.mediaId == track.mediaId
        return CarouselSong(
            uid = item.uid,
            mediaId = item.mediaId,
            title = item.title.ifEmpty { if (isTrack) track.title else "" },
            artist = item.artist.ifEmpty { if (isTrack) track.artist else "" },
            album = if (isTrack) track.album else "",
            // the playing song's own picture when the queue has none for it
            artworkUrl = item.artworkUri?.toString() ?: track.thumbnailUrl.takeIf { current && isTrack },
            step = step,
        )
    }

    // ---- moving the row ----

    private var job: Job? = null
    // songs per second, + toward the next one
    private var velocity = 0f
    private var headingTo = Float.NaN

    // a swipe's skip, held until the row is still, and the step it lands on
    private var pendingSkip: (() -> Unit)? = null
    private var pendingTarget = 0

    // the step a skip that has gone will land on, until the queue says it has (so nothing glides back meanwhile)
    private var expectedStep: Int? = null
    private var confirmJob: Job? = null

    // where the row belongs when nothing holds it: the playing song, or the one a skip is on its way to
    private fun restingStep(): Int = expectedStep ?: step

    /** Slides the row to the playing song, carrying on with whatever speed it has. */
    internal fun settle() {
        if (dragging) return
        glideTo(restingStep())
    }

    private fun firePendingSkip() {
        val skip = pendingSkip ?: return
        pendingSkip = null
        expect(pendingTarget, SkipConfirmMs)
        skip()
    }

    // holds the row on [target] until the queue says the song changed, or [confirmMs] goes by without it
    private fun expect(target: Int, confirmMs: Long) {
        expectedStep = target
        confirmJob?.cancel()
        confirmJob = scope.launch {
            delay(confirmMs)
            // the player never moved on: back to the song that's playing
            expectedStep = null
            settle()
        }
    }

    /** a skip button: the row glides on at once and [skip] waits until it's still, so the new song's work never lands on the glide; if a fade out holds the song back past the glide anyway ([songChangesAfterMs]), [skip] goes at once so the fade starts with the press */
    fun skipToNext(player: Player?, songChangesAfterMs: Long, skip: () -> Unit) {
        // mid-swipe, at the end of the queue or with a skip already on its way: straight through, and the row follows the songs
        if (dragging || player?.hasNextMediaItem() != true || pendingSkip != null || expectedStep != null) {
            firePendingSkip()
            skip()
            return
        }
        val target = step + 1
        if (songChangesAfterMs >= GlideStillMs) {
            expect(target, SkipConfirmMs + songChangesAfterMs)
            skip()
        } else {
            pendingTarget = target
            pendingSkip = skip
        }
        glideTo(target)
    }

    // one critically damped spring, stepped per frame with long frames capped, so the row never jumps; the skip waits for it to be still
    private fun glideTo(target: Int) {
        val to = target.toFloat()
        // already on its way there: a restart would only be the same motion again
        if (headingTo == to && job?.isActive == true) return
        job?.cancel()
        headingTo = to
        job = scope.launch {
            var lastNanos = 0L
            var resting = false
            while (!resting) {
                resting = withFrameNanos { now ->
                    val dt = if (lastNanos == 0L) 1f / 120f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, MaxFrameSec)
                    lastNanos = now
                    val y = position - to
                    val e = exp(-SettleOmega * dt)
                    val tmp = (velocity + SettleOmega * y) * dt
                    val next = (y + tmp) * e
                    velocity = (velocity - SettleOmega * tmp) * e
                    position = to + next
                    abs(next) < RestDistance && abs(velocity) < RestSpeed
                }
                // still to the eye: the song can change now without the cover moving under its work
                if (pendingSkip != null && abs(position - to) < SkipDistance && abs(velocity) < SkipSpeed) firePendingSkip()
            }
            position = to
            velocity = 0f
            headingTo = Float.NaN
            firePendingSkip()
            if (restingStep() != target) {
                // the song didn't change after all: back to the one playing
                glideTo(restingStep())
                return@launch
            }
            if (outgoing != null) {
                outgoing = null
                val queue = lastQueue
                val track = lastTrack
                if (queue != null && track != null) onQueue(queue, track)
            }
        }
    }

    private var dragFrom = 0f

    // a finger touched the row: a gliding row stops dead under it, like any scrolling picture, and a skip it still owed goes now while nothing moves
    fun hold() {
        if (dragging) return
        job?.cancel()
        headingTo = Float.NaN
        velocity = 0f
        firePendingSkip()
    }

    // back on its song if something left it part-way: a moving cover rides the row, so even a sliver off shows as a cut
    fun ensureSettled() {
        if (dragging || job?.isActive == true) return
        if (abs(position - restingStep()) > RestDistance) glideTo(restingStep())
    }

    // the finger came up without swiping: whatever was caught settles on its song again
    fun letGo() {
        if (dragging) return
        settle()
    }

    // a finger took the row; from here it sits exactly under the finger
    fun startDrag() {
        hold()
        dragFrom = position
        dragging = true
    }

    /**
     * The finger is [travelledPx] from where it went down, on something [spacingPx] wide per song.
     * Past the queue's ends the row only gives a little.
     */
    fun dragTo(travelledPx: Float, spacingPx: Float, hasPrevious: Boolean, hasNext: Boolean) {
        if (!dragging || spacingPx <= 0f) return
        val base = restingStep()
        val raw = dragFrom - travelledPx / spacingPx
        val low = minOf(base - (if (hasPrevious) 1f else 0f), dragFrom)
        val high = maxOf(base + (if (hasNext) 1f else 0f), dragFrom)
        position = when {
            raw < low -> low - (low - raw) * EdgeGive
            raw > high -> high + (raw - high) * EdgeGive
            else -> raw
        }
    }

    // the finger lifted: past half a song or flicked, the row moves on one song with the flick's speed (the skip follows once it's still), otherwise it eases back
    fun release(travelledPx: Float, velocityPx: Float, spacingPx: Float, flingPx: Float, player: Player?) {
        if (!dragging) return
        dragging = false
        val spacing = spacingPx.coerceAtLeast(1f)
        val base = restingStep()
        // in songs, + toward the next one, measured from the song the row belongs on
        val moved = position - base
        val speed = -velocityPx / spacing
        val fling = flingPx / spacing
        val forward = (moved > SwipeCommit || speed > fling) && speed > -fling && player?.hasNextMediaItem() == true
        val back = !forward && (moved < -SwipeCommit || speed < -fling) && speed < fling &&
            player?.hasPreviousMediaItem() == true
        val target = base + when {
            forward -> 1
            back -> -1
            else -> 0
        }
        pendingTarget = target
        pendingSkip = when {
            player == null -> null
            forward -> { { player.seekToNextMediaItem() } }
            back -> { { player.seekToPreviousMediaItem() } }
            else -> null
        }
        // the finger's own speed carries straight into the settle, so letting go never jolts
        velocity = speed
        glideTo(target)
    }

    // the finger was taken away by something else: back to the song it belongs on
    fun cancelDrag() {
        if (!dragging) return
        dragging = false
        velocity = 0f
        glideTo(restingStep())
    }
}

/** The player's row, kept in step with its queue. */
@Composable
fun rememberSongCarousel(connection: PlayerConnection): SongCarousel {
    val scope = rememberCoroutineScope()
    val carousel = remember(scope) { SongCarousel(scope) }
    SongCarouselFeed(carousel, connection)
    return carousel
}

/** Its own scope, so a queue change recomposes only this and not the player around it. */
@Composable
private fun SongCarouselFeed(carousel: SongCarousel, connection: PlayerConnection) {
    val queue by connection.queueSnapshot.collectAsState()
    val track by connection.uiState.trackInfo.collectAsState()
    // in composition, so the slots move with the song in the very frame it changes
    carousel.onQueue(queue, track)
    LaunchedEffect(carousel, carousel.step) { carousel.settle() }
}

/**
 * The slots worth drawing text for: the playing song, and a neighbour while it's on its way in or
 * out. Changes only when one joins or leaves, not on every frame of the slide.
 */
@Composable
fun rememberTextSlots(carousel: SongCarousel): List<CarouselSong> {
    val shown by remember(carousel) {
        derivedStateOf {
            carousel.slots.filter { it.step == carousel.step || abs(carousel.offsetOf(it)) < 0.999f }
        }
    }
    return shown
}

/**
 * A line of text riding the row: it travels part of its own width per song and is gone by about
 * two thirds of the way, so the leaving and arriving lines never sit on top of each other.
 */
fun Modifier.carouselText(carousel: SongCarousel, slot: CarouselSong): Modifier = graphicsLayer {
    val o = carousel.offsetOf(slot)
    translationX = o * size.width * TextTravel
    alpha = (1f - abs(o) * TextFade).coerceIn(0f, 1f)
}

private const val TextTravel = 0.75f
private const val TextFade = 1.5f

/**
 * Pictures riding the row, one per song, side by side and clipped to this box: the next one comes
 * in as this one goes. Each picture drifts a little slower than its page ([parallax]), which is
 * what makes it read as depth instead of a flat strip. Neighbours stay composed (their pictures
 * already loaded) but aren't drawn until they come into view.
 *
 * Without a [carousel] there's just [fallbackUrl], still.
 */
@Composable
fun CarouselPictures(
    carousel: SongCarousel?,
    fallbackUrl: String?,
    modifier: Modifier = Modifier,
    parallax: Float = 0.3f,
    // with a shape each page is a card of its own that slides out whole, gap apart, instead of a picture moving inside one frame
    cardShape: Shape? = null,
    cardGap: Dp = 0.dp,
    picture: @Composable BoxScope.(url: String?, modifier: Modifier) -> Unit,
) {
    Box(modifier = if (cardShape == null) modifier.clipToBounds() else modifier) {
        val slots = carousel?.slots.orEmpty()
        if (carousel == null || slots.isEmpty()) {
            picture(fallbackUrl, Modifier.fillMaxSize())
            return@Box
        }
        for (slot in slots) {
            key(slot.uid) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val o = carousel.offsetOf(slot)
                            // off the row's visible part: kept, not drawn
                            alpha = if (abs(o) >= 1f) 0f else 1f
                            translationX = o * (size.width + cardGap.toPx())
                            // each page shows only its own part of the row: unclipped, the parallax shift put a third of the next picture on screen the instant it came in
                            if (cardShape != null) shape = cardShape
                            clip = true
                        }
                ) {
                    picture(
                        slot.artworkUrl,
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = -carousel.offsetOf(slot) * size.width * parallax },
                    )
                }
            }
        }
    }
}

/**
 * Backgrounds that crossfade along the row, one per song: dragging toward the next song fades its
 * background in by exactly how far the finger has gone. See [SongCarousel.crossfadeAlpha].
 */
@Composable
fun CarouselCrossfade(
    carousel: SongCarousel?,
    fallbackUrl: String?,
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.(url: String?) -> Unit,
) {
    Box(modifier = modifier) {
        val slots = carousel?.slots.orEmpty()
        if (carousel == null || slots.isEmpty()) {
            background(fallbackUrl)
            return@Box
        }
        for (slot in slots) {
            key(slot.uid) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = carousel.crossfadeAlpha(slot) },
                ) {
                    // a copy at zero opacity can skip drawing, so it isn't redrawn every frame for nothing
                    val shown = remember(carousel, slot) {
                        val visible = derivedStateOf { carousel.crossfadeAlpha(slot) > 0f }
                        val read: () -> Boolean = { visible.value }
                        read
                    }
                    CompositionLocalProvider(LocalCrossfadeShown provides shown) {
                        background(slot.artworkUrl)
                    }
                }
            }
        }
    }
}

/** Whether this crossfade copy shows anything right now. Read it while drawing, never in composition. */
val LocalCrossfadeShown = compositionLocalOf<() -> Boolean> { { true } }
