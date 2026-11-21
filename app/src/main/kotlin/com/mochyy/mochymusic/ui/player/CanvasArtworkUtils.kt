// CanvasArtworkUtils.kt

package com.mochyy.mochymusic.ui.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.mochyy.mochymusic.applecanvas.AppleMusicCanvasProvider
import com.mochyy.mochymusic.canvas.CanvasArtwork
import com.mochyy.mochymusic.canvas.MonochromeApiCanvas
import com.mochyy.mochymusic.canvas.SpotifyCanvasProvider
import com.mochyy.mochymusic.constants.CanvasOrder
import com.mochyy.mochymusic.constants.CanvasOrderKey
import com.mochyy.mochymusic.constants.CanvasSource
import com.mochyy.mochymusic.constants.SpotifyCanvasKey
import com.mochyy.mochymusic.extensions.toEnum
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// the animated cover on the player right now, so the player's menu only offers its quality while one plays
object PlayerCanvasPlayback {
    // the short side of the picture playing, 0 when no animated cover is up
    var playingSide by mutableIntStateOf(0)
        private set
    private var owner: Any? = null

    // the cover that reported a picture last owns the value, so the song swiping away can't clear the new one's
    fun report(owner: Any, side: Int) {
        if (side > 0) {
            this.owner = owner
            playingSide = side
        } else if (this.owner === owner) {
            this.owner = null
            playingSide = 0
        }
    }
}

object CanvasArtworkPlaybackCache {
    private const val defaultMaxSize = 256
    private val map = LinkedHashMap<String, CanvasArtwork>(defaultMaxSize, 0.75f, true)
    @Volatile private var maxSize = defaultMaxSize

    @Synchronized
    fun get(mediaId: String): CanvasArtwork? {
        if (maxSize <= 0) return null
        return map[mediaId]
    }

    @Synchronized
    fun put(mediaId: String, artwork: CanvasArtwork) {
        val limit = maxSize
        if (limit <= 0) return
        if (mediaId.isBlank()) return
        map[mediaId] = artwork
        while (map.size > limit) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    @Synchronized
    fun setMaxSize(value: Int) {
        maxSize = value.coerceAtLeast(0)
        if (maxSize == 0) {
            map.clear()
            return
        }
        while (map.size > maxSize) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            } else {
                break
            }
        }
    }
}

// asks the canvas sources in the order picked in app behaviour and keeps the first loop one of them has
internal suspend fun findCanvas(
    context: Context,
    title: String,
    artist: String,
    album: String? = null,
    storefront: String = "us",
    durationSec: Int? = null,
): CanvasArtwork? {
    val order = context.dataStore[CanvasOrderKey].toEnum(CanvasOrder.APPLE_TIDAL_SPOTIFY)
    val spotify = context.dataStore.get(SpotifyCanvasKey, false)
    return withContext(Dispatchers.IO) {
        order.sources.firstNotNullOfOrNull { source ->
            when (source) {
                CanvasSource.APPLE_MUSIC -> AppleMusicCanvasProvider.getBySongArtist(title, artist, album, storefront, durationSec)
                CanvasSource.TIDAL -> MonochromeApiCanvas.getBySongArtist(title, artist, album, durationSec)
                CanvasSource.SPOTIFY -> if (spotify) SpotifyCanvasProvider.getBySongArtist(context, title, artist, durationSec) else null
            }?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
        }
    }
}

// the song's animated cover from the cache, else from the sources, kept for next time; the names are cleaned off the main thread
internal suspend fun findCanvasCached(context: Context, mediaId: String, title: String, artist: String, durationSec: Int?): CanvasArtwork? {
    CanvasArtworkPlaybackCache.get(mediaId)?.let { return it }
    val (cleanTitle, cleanArtist) = withContext(Dispatchers.Default) { normalizeCanvasSongTitle(title) to normalizeCanvasArtistName(artist) }
    if (cleanTitle.length < 2 || cleanArtist.length < 2) return null
    return findCanvas(context, cleanTitle, cleanArtist, durationSec = durationSec)?.also { CanvasArtworkPlaybackCache.put(mediaId, it) }
}

internal fun normalizeCanvasSongTitle(raw: String): String {
    val stripped =
        raw
            .replace(Regex("\\s*\\[[^]]*]"), "")
            .replace(
                Regex(
                    "\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(
                Regex(
                    "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(
                Regex(
                    "\\s*-\\s*(?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)\\b.*$",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(Regex("\\s+"), " ")
            .trim()

    return stripped
        .trim('-')
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun normalizeCanvasArtistName(raw: String): String {
    val first =
        raw
            .split(
                Regex(
                    "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
                    RegexOption.IGNORE_CASE,
                ),
                limit = 2,
            ).firstOrNull().orEmpty()

    return first.replace(Regex("\\s+"), " ").trim()
}
