// OriginCard.kt

package com.mochyy.mochymusic.playback

import android.os.Bundle
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionCommand
import com.mochyy.mochymusic.car.CarLyricLines
import com.mochyy.mochymusic.car.CarLyricsSource
import com.mochyy.mochymusic.constants.SongSortType
import com.mochyy.mochymusic.db.MusicDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

// vivo's music card (com.vivo.musicwidgetmix), the player originos shows on the lock screen and in the atomic island ("origin player").
// it drives any app with play, pause and skip; an app whose browser service also answers com.vivo.musicwidgetmix.support.service
// (the manifest says so for MusicService) gets the heart, the loop button, lyrics and three lists too, read from keys of the card's
// own. vivo doesn't document any of it: the keys are the ones the origin-island-patches project found taking the card (17.0.8.3) apart
@UnstableApi
internal object OriginCard {
    const val Package = "com.vivo.musicwidgetmix"

    // what the card may show and do, as bits of the metadata; without it the card greys everything out. play controls, title and
    // artist, cover, lyrics, seeking, the heart, the loop button, then the queue, favourites and local lists (bit 5 is unused)
    private const val KeySupport = "vivomusicmix.media.metadata.support_event"
    private const val Support = 0b111_1101_1111L

    private const val KeyLoopMode = "vivomusicmix.media.metadata.LOOP_MODE"
    private const val LoopList = 1
    private const val LoopSingle = 2
    private const val LoopRandom = 3

    // the loop button sends this, with the mode it wants next under KeyLoopMode
    val PlayModeCommand = SessionCommand("vivomusicmix.media.action.PLAY_MODE", Bundle.EMPTY)

    // the lyrics go in the session's extras, as lrc; the misspellings are the card's own
    private const val ExtraAction = "vivomusicmix.meida.extra.key.action"
    private const val ActionLyricsChanged = "vivomusicmix.extra.lrc_change"
    private const val ExtraMediaId = "vivomusicmix.extra.key.meidia_id"
    private const val ExtraLyrics = "vivomusicmix.extra.key.lyric"

    // the lists, as browse parents: what's queued, the liked songs and the downloads
    const val ListQueue = "vivomusicmix_current_list"
    const val ListLiked = "vivomusicmix_favorite_list"
    const val ListLocal = "vivomusicmix_local_list"
    val Lists = setOf(ListQueue, ListLiked, ListLocal)

    // the card loads a list in pages and reads off its last entry whether there's another; every list here is the one page
    private const val KeyPage = "vivomusicmix_key_media_page"
    private const val KeyHasMore = "vivomusicmix_key_has_more"

    // an empty list keeps the card loading until it blames the network, so it gets one entry that says it's empty
    const val EmptyId = "origin_card_empty"

    // a list crosses to the card in one go, so it stays well short of what binder can carry
    const val MaxListSize = 200

    // how much of the queue before the song playing makes it into the card's queue
    private const val QueueBehind = 20

    fun loopMode(player: Player): Int = when {
        player.shuffleModeEnabled -> LoopRandom
        player.repeatMode == Player.REPEAT_MODE_ONE -> LoopSingle
        else -> LoopList
    }

    fun loopModeAsked(args: Bundle): Int = args.getInt(KeyLoopMode)

    // the card has no shuffle that also repeats one, so each turns the other off; list and random leave repeat-all (or off) as it was
    fun setLoopMode(player: Player, mode: Int) {
        when (mode) {
            LoopSingle -> {
                player.shuffleModeEnabled = false
                player.repeatMode = Player.REPEAT_MODE_ONE
            }
            LoopList, LoopRandom -> {
                if (player.repeatMode == Player.REPEAT_MODE_ONE) player.repeatMode = Player.REPEAT_MODE_OFF
                player.shuffleModeEnabled = mode == LoopRandom
            }
        }
    }

    // media3 only sends new metadata on when a field it compares has changed, and it never looks inside the extras, so the loop
    // mode goes in the station too, which nothing shows and media3 doesn't pass to the card; the heart is the overall rating
    fun withCardKeys(metadata: MediaMetadata, liked: Boolean, loopMode: Int): MediaMetadata {
        val extras = (metadata.extras?.let(::Bundle) ?: Bundle()).apply {
            putLong(KeySupport, Support)
            putLong(KeyLoopMode, loopMode.toLong())
        }
        return metadata.buildUpon()
            .setOverallRating(HeartRating(liked))
            .setStation("loop $loopMode")
            .setExtras(extras)
            .build()
    }

    // the queue around the song playing, under the songs' own ids so the card can mark the one that's on; main thread
    fun queue(player: Player): List<MediaItem> {
        val from = (player.currentMediaItemIndex - QueueBehind).coerceAtLeast(0)
        val until = minOf(player.mediaItemCount, from + MaxListSize)
        return (from until until).map { index ->
            val item = player.getMediaItemAt(index)
            MediaItem.Builder().setMediaId(item.mediaId).setMediaMetadata(item.mediaMetadata).build()
        }
    }

    // a list as the only page of it: every entry playable (the card plays whatever it shows) with its subtitle set (the card
    // shows a missing one as a word), and the last one saying there's no other page
    fun onePage(items: List<MediaItem>, emptyTitle: String, emptySubtitle: String): List<MediaItem> {
        val listed = items.take(MaxListSize).ifEmpty {
            listOf(
                MediaItem.Builder()
                    .setMediaId(EmptyId)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(emptyTitle).setSubtitle(emptySubtitle).build())
                    .build(),
            )
        }
        return listed.mapIndexed { index, item ->
            val metadata = item.mediaMetadata
            val extras = (metadata.extras?.let(::Bundle) ?: Bundle()).apply {
                if (index == listed.lastIndex) {
                    putBoolean(KeyHasMore, false)
                    putInt(KeyPage, 1)
                }
            }
            item.buildUpon().setMediaMetadata(
                metadata.buildUpon()
                    .setSubtitle(metadata.subtitle ?: metadata.artist ?: "")
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setExtras(extras)
                    .build(),
            ).build()
        }
    }

    // timed lyrics as lrc, moved by the song's lyrics offset the way the player follows them; plain lyrics have no times to give
    fun lrc(lyrics: CarLyricLines?): String {
        if (lyrics == null || !lyrics.synced) return ""
        return lyrics.lines.joinToString("\n") { line ->
            val at = (line.time - lyrics.offsetMs).coerceAtLeast(0)
            "[%02d:%02d.%02d]%s".format(Locale.ROOT, at / 60_000, at / 1_000 % 60, at % 1_000 / 10, line.text.replace('\n', ' '))
        }
    }

    fun withLyrics(extras: Bundle, mediaId: String, lrc: String) = Bundle(extras).apply {
        putString(ExtraAction, ActionLyricsChanged)
        putString(ExtraMediaId, mediaId)
        putString(ExtraLyrics, lrc)
    }
}

// the session's player as everything outside the app sees it, with what vivo's music card reads put in the metadata: the heart
// and the loop button. it's in the metadata for every phone, nothing but the card reads those keys
@UnstableApi
class OriginCardPlayer(player: Player, private val liked: (mediaId: String) -> Boolean) : ForwardingPlayer(player) {
    // the session's own listeners, told when the heart or the loop mode changes since the player underneath never changes its metadata for those
    private val listeners = CopyOnWriteArrayList<Player.Listener>()

    init {
        player.addListener(object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) = refresh()
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = refresh()
        })
    }

    // call on the main thread, the session checks it reads the player there
    fun refresh() {
        val metadata = mediaMetadata
        listeners.forEach { it.onMediaMetadataChanged(metadata) }
    }

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        listeners -= listener
    }

    override fun getMediaMetadata(): MediaMetadata {
        val real = super.getMediaMetadata()
        val id = currentMediaItem?.mediaId ?: return real
        return OriginCard.withCardKeys(real, liked(id), OriginCard.loopMode(this))
    }
}

// what keeps the card up to date while it's connected: each song's lyrics, and word when one of its lists changes
@UnstableApi
class OriginCardLink(
    private val session: MediaLibrarySession,
    private val database: MusicDatabase,
    private val downloadUtil: DownloadUtil,
    private val lyrics: CarLyricsSource,
    private val nowPlaying: StateFlow<com.mochyy.mochymusic.models.MediaMetadata?>,
    private val scope: CoroutineScope,
) {
    private val jobs = mutableListOf<Job>()
    private var queueNotice: Job? = null

    // the queue changes a few times in a row as a song starts and autoplay lines more up, so the card hears of it once it settles
    private val queueListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            queueNotice?.cancel()
            queueNotice = scope.launch {
                delay(QueueSettleMs)
                tell(OriginCard.ListQueue)
            }
        }
    }

    // main thread
    fun start() {
        if (jobs.isNotEmpty()) return
        session.player.addListener(queueListener)
        jobs += scope.launch {
            // the card only starts listening for lyrics a moment after it connects
            delay(LyricsAfterConnectMs)
            nowPlaying.distinctUntilChangedBy { it?.id }.collectLatest { song ->
                val id = song?.id ?: return@collectLatest
                // the last song's lyrics go first, so they never sit over this one while its own load
                publish(id, "")
                val lines = try {
                    lyrics.load(song)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                publish(id, OriginCard.lrc(lines))
            }
        }
        jobs += scope.launch {
            database.likedSongs(SongSortType.CREATE_DATE, true)
                .map { songs -> songs.map { it.id } }
                .distinctUntilChanged()
                .drop(1)
                .collect { tell(OriginCard.ListLiked) }
        }
        jobs += scope.launch {
            downloadUtil.downloads
                .map { all -> all.filterValues { it.state == Download.STATE_COMPLETED }.keys }
                .distinctUntilChanged()
                .drop(1)
                .collect { tell(OriginCard.ListLocal) }
        }
    }

    // main thread
    fun stop() {
        session.player.removeListener(queueListener)
        queueNotice?.cancel()
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    private fun publish(mediaId: String, lrc: String) {
        session.setSessionExtras(OriginCard.withLyrics(session.sessionExtras, mediaId, lrc))
    }

    private fun tell(list: String) {
        session.notifyChildrenChanged(list, Int.MAX_VALUE, null)
    }

    private companion object {
        const val QueueSettleMs = 500L
        const val LyricsAfterConnectMs = 1_500L
    }
}
