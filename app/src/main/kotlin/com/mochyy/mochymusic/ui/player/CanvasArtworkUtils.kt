// CanvasArtworkUtils.kt

package com.mochyy.mochymusic.ui.player

import android.content.Context
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
