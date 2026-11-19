// MetrolistWidgetManager.kt

package com.mochyy.mochymusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.toBitmap
import com.mochyy.mochymusic.MainActivity
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.MusicDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MochyWidgetManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase
) {
    private val imageLoader by lazy {
        ImageLoader.Builder(context)
            .crossfade(false)
            .build()
    }

    private var cachedArtworkUri: String? = null
    private var cachedAlbumArt: Bitmap? = null
    private var cachedCircularAlbumArt: Bitmap? = null

    // the now playing widgets' frosted backdrop, rounded thumbnail and header colour, made once per song instead of every tick
    private var npArtworkUri: String? = null
    private var npBackdrop: Bitmap? = null
    private var npThumb: Bitmap? = null
    private var npAccent: Int = Color.WHITE

    // widgets that already hold a full drawing of this song, so a tick only sends them text and nothing flashes
    private val npDrawn = HashSet<Int>()

    // two lyric blocks take turns: new lines go into the hidden one and the flipper crossfades to it
    private var lyricNow = ""
    private var lyricNext = ""
    // one index per size, a shared one gets toggled twice on the same tick and flips a widget back to itself
    private var lyricChild2x2 = 0
    private var lyricChild4x2 = 0
    private var progressSecond = -1L

    // whether a lyric widget is on the home screen, so the service only follows lyrics when someone can see them
    @Volatile
    var hasLyricWidgets = false
        private set

    /** One update at a time: they share the cached artwork, and they no longer run on one thread. */
    private val updating = kotlinx.coroutines.sync.Mutex()

    /**
     * Redraws every placed widget. Runs off the main thread: the system calls and bitmap work here
     * used to run on it every 200ms while music played, taking frames from whatever was on screen.
     * Does nothing at all, not even load the artwork, when no widget is placed.
     */
    suspend fun updateWidgets(
        title: String,
        artist: String,
        artworkUri: String?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0,
        currentLyric: String = "",
        nextLyric: String = "",
        // the once-a-second tick, it also moves the lyric widgets on to the line being sung
        progressOnly: Boolean = false,
        // one of Player.REPEAT_MODE_*, for the repeat button of the now playing 4x1 widget
        repeatMode: Int = 0,
    ) = withContext(Dispatchers.Default) {
        updating.withLock {
            drawWidgets(title, artist, artworkUri, isPlaying, isLiked, duration, currentPosition, currentLyric, nextLyric, progressOnly, repeatMode)
        }
    }

    private suspend fun drawWidgets(
        title: String,
        artist: String,
        artworkUri: String?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        currentPosition: Long,
        currentLyric: String,
        nextLyric: String,
        progressOnly: Boolean,
        repeatMode: Int,
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MusicWidgetReceiver::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        val turntableComponentName = ComponentName(context, TurntableWidgetReceiver::class.java)
        val turntableWidgetIds = if (progressOnly) IntArray(0) else appWidgetManager.getAppWidgetIds(turntableComponentName)
        // both lyric widgets refresh on the tick so their line keeps up with the song
        val np2x2Ids = appWidgetManager.getAppWidgetIds(ComponentName(context, NowPlaying2x2WidgetReceiver::class.java))
        val np4x1Ids = if (progressOnly) IntArray(0) else appWidgetManager.getAppWidgetIds(ComponentName(context, NowPlaying4x1WidgetReceiver::class.java))
        val np4x2Ids = appWidgetManager.getAppWidgetIds(ComponentName(context, NowPlaying4x2WidgetReceiver::class.java))
        hasLyricWidgets = np2x2Ids.isNotEmpty() || np4x2Ids.isNotEmpty()
        if (widgetIds.isEmpty() && turntableWidgetIds.isEmpty() &&
            np2x2Ids.isEmpty() && np4x1Ids.isEmpty() && np4x2Ids.isEmpty()
        ) return

        val albumArt: Bitmap?
        val circularAlbumArt: Bitmap?

        if (artworkUri != null && artworkUri == cachedArtworkUri && cachedAlbumArt != null) {
            albumArt = cachedAlbumArt
            circularAlbumArt = cachedCircularAlbumArt
        } else {
            albumArt = artworkUri?.let { loadAlbumArt(it, 300) }
            circularAlbumArt = albumArt?.let { getCircularBitmap(it) }

            cachedArtworkUri = artworkUri
            cachedAlbumArt = albumArt
            cachedCircularAlbumArt = circularAlbumArt
        }

        if (widgetIds.isNotEmpty()) {
            widgetIds.forEach { widgetId ->
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                val views = createRemoteViewsForSize(
                    options,
                    title,
                    artist,
                    albumArt,
                    isPlaying,
                    isLiked,
                    duration,
                    currentPosition
                )
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        if (turntableWidgetIds.isNotEmpty()) {
            val turntableViews = createTurntableRemoteViews(
                circularAlbumArt,
                isPlaying,
                isLiked
            )
            turntableWidgetIds.forEach { widgetId ->
                appWidgetManager.updateAppWidget(widgetId, turntableViews)
            }
        }

        if (np2x2Ids.isNotEmpty() || np4x2Ids.isNotEmpty()) {
            prepareNowPlayingArtwork(artworkUri, albumArt)
        }
        val lyricChanged = advanceLyric(currentLyric, nextLyric)
        val progressSecondNow = currentPosition / 1000L
        val progressDue = progressSecondNow != progressSecond
        progressSecond = progressSecondNow

        if (np2x2Ids.isNotEmpty()) {
            val (full, tick) = np2x2Ids.toList().partition { !progressOnly || it !in npDrawn }
            full.forEach { id ->
                val compact = isCompact(appWidgetManager.getAppWidgetOptions(id))
                appWidgetManager.updateAppWidget(id, createNowPlaying2x2RemoteViews(title, artist, isPlaying, compact))
                npDrawn += id
            }
            if (tick.isNotEmpty() && lyricChanged) {
                val partial = RemoteViews(context.packageName, R.layout.widget_np_2x2)
                bindLyric(partial, fourByTwo = false)
                appWidgetManager.partiallyUpdateAppWidget(tick.toIntArray(), partial)
            }
        }

        if (np4x1Ids.isNotEmpty()) {
            val views = createNowPlaying4x1RemoteViews(title, artist, circularAlbumArt, isPlaying, isLiked, repeatMode)
            np4x1Ids.forEach { appWidgetManager.updateAppWidget(it, views) }
        }

        if (np4x2Ids.isNotEmpty()) {
            val (full, tick) = np4x2Ids.toList().partition { !progressOnly || it !in npDrawn }
            if (full.isNotEmpty()) {
                val views = createNowPlaying4x2RemoteViews(title, artist, isPlaying, isLiked, duration, currentPosition)
                full.forEach { id ->
                    appWidgetManager.updateAppWidget(id, views)
                    npDrawn += id
                }
            }
            if (tick.isNotEmpty() && (lyricChanged || progressDue)) {
                if (lyricChanged) {
                    // the only update that flips, any other partial would replay the flip (see docs/notes/widgets.md)
                    val partial = RemoteViews(context.packageName, R.layout.widget_np_4x2)
                    bindLyric(partial, fourByTwo = true)
                    bindProgress(partial, duration, currentPosition)
                    appWidgetManager.partiallyUpdateAppWidget(tick.toIntArray(), partial)
                } else {
                    // a full drawing drops the old flip, a progress partial would replay it and blink the 4x2 every second
                    tick.forEach { id ->
                        appWidgetManager.updateAppWidget(
                            id,
                            createNowPlaying4x2RemoteViews(
                                title, artist, isPlaying, isLiked, duration, currentPosition,
                                staticLyric = true,
                            )
                        )
                    }
                }
            }
        }
    }

    // a short 2x2 has room for the line being sung but not the next one under it
    private fun isCompact(options: Bundle): Boolean {
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        return minHeight in 1 until CompactBelowDp
    }

    private fun createNowPlaying2x2RemoteViews(
        title: String,
        artist: String,
        isPlaying: Boolean,
        compact: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_np_2x2)
        views.setTextViewText(R.id.widget_np_track, "$title · $artist")
        views.setTextColor(R.id.widget_np_track, npAccent)
        bindLyric(views, fresh = true, fourByTwo = false)
        val nextVisibility = if (compact) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.widget_np_next_a, nextVisibility)
        views.setViewVisibility(R.id.widget_np_next_b, nextVisibility)
        bindBackdrop(views)
        views.setImageViewResource(R.id.widget_np_play, if (isPlaying) R.drawable.ic_np_pause else R.drawable.ic_np_play)

        val receiver = NowPlaying2x2WidgetReceiver::class.java
        views.setOnClickPendingIntent(android.R.id.background, NowPlayingWidgetIntents.openApp(context))
        views.setOnClickPendingIntent(R.id.widget_np_prev, NowPlayingWidgetIntents.broadcast(context, receiver, MusicWidgetReceiver.ACTION_PREVIOUS, 0))
        views.setOnClickPendingIntent(R.id.widget_np_play, NowPlayingWidgetIntents.broadcast(context, receiver, MusicWidgetReceiver.ACTION_PLAY_PAUSE, 1))
        views.setOnClickPendingIntent(R.id.widget_np_next, NowPlayingWidgetIntents.broadcast(context, receiver, MusicWidgetReceiver.ACTION_NEXT, 2))
        return views
    }

    private fun createNowPlaying4x1RemoteViews(
        title: String,
        artist: String,
        circularAlbumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        repeatMode: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_np_4x1)
        views.setTextViewText(R.id.widget_np_title, "$title - $artist")
        views.setImageViewBitmap(R.id.widget_np_art, circularAlbumArt ?: getCircularDefaultIcon())
        views.setImageViewResource(R.id.widget_np_play, if (isPlaying) R.drawable.ic_np_pause else R.drawable.ic_np_play)
        views.setImageViewResource(R.id.widget_np_like, if (isLiked) R.drawable.ic_np_star_filled else R.drawable.ic_np_star)
        views.setImageViewResource(
            R.id.widget_np_repeat,
            when (repeatMode) {
                Player.REPEAT_MODE_ALL -> R.drawable.ic_np_repeat_on
                Player.REPEAT_MODE_ONE -> R.drawable.ic_np_repeat_one
                else -> R.drawable.ic_np_repeat
            }
        )

        val receiver = NowPlaying4x1WidgetReceiver::class.java
        views.setOnClickPendingIntent(R.id.widget_np_art, NowPlayingWidgetIntents.openApp(context))
        views.setOnClickPendingIntent(R.id.widget_np_title, NowPlayingWidgetIntents.openApp(context))
        views.setOnClickPendingIntent(R.id.widget_np_like, NowPlayingWidgetIntents.broadcast(context, receiver, MusicWidgetReceiver.ACTION_LIKE, 0))
        views.setOnClickPendingIntent(R.id.widget_np_prev, NowPlayingWidgetIntents.broadcast(context, receiver, MusicWidgetReceiver.ACTION_PREVIOUS, 1))
        views.setOnClickPendingIntent(R.id.widget_np_play, NowPlayingWidgetIntents.broadcast(context, receiver, MusicWidgetReceiver.ACTION_PLAY_PAUSE, 2))
        views.setOnClickPendingIntent(R.id.widget_np_next, NowPlayingWidgetIntents.broadcast(context, receiver, MusicWidgetReceiver.ACTION_NEXT, 3))
        views.setOnClickPendingIntent(R.id.widget_np_repeat, NowPlayingWidgetIntents.broadcast(context, receiver, MusicWidgetReceiver.ACTION_REPEAT, 4))
        return views
    }

    private fun createNowPlaying4x2RemoteViews(
        title: String,
        artist: String,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        currentPosition: Long,
        // progress ticks draw the line into both children, the flipper keeps whichever one it shows
        staticLyric: Boolean = false,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_np_4x2)
        views.setTextViewText(R.id.widget_np_track, "$title · $artist")
        views.setTextColor(R.id.widget_np_track, npAccent)
        bindLyric(views, fresh = true, fourByTwo = true, staticLyric = staticLyric)
        bindBackdrop(views)
        npThumb?.let { views.setImageViewBitmap(R.id.widget_np_art, it) }
            ?: views.setImageViewResource(R.id.widget_np_art, R.drawable.widget_np_art_placeholder)
        views.setImageViewResource(R.id.widget_np_play, if (isPlaying) R.drawable.ic_np_pause else R.drawable.ic_np_play)
        views.setImageViewResource(R.id.widget_np_like, if (isLiked) R.drawable.ic_np_star_filled else R.drawable.ic_np_star)
        bindProgress(views, duration, currentPosition)

        val receiver = NowPlaying4x2WidgetReceiver::class.java
        views.setOnClickPendingIntent(android.R.id.background, NowPlayingWidgetIntents.openApp(context))
        views.setOnClickPendingIntent(R.id.widget_np_art, NowPlayingWidgetIntents.openApp(context))
        views.setOnClickPendingIntent(R.id.widget_np_prev, NowPlayingWidgetIntents.broadcast(context, receiver, MusicWidgetReceiver.ACTION_PREVIOUS, 0))
        views.setOnClickPendingIntent(R.id.widget_np_play, NowPlayingWidgetIntents.broadcast(context, receiver, MusicWidgetReceiver.ACTION_PLAY_PAUSE, 1))
        views.setOnClickPendingIntent(R.id.widget_np_next, NowPlayingWidgetIntents.broadcast(context, receiver, MusicWidgetReceiver.ACTION_NEXT, 2))
        views.setOnClickPendingIntent(R.id.widget_np_like, NowPlayingWidgetIntents.broadcast(context, receiver, MusicWidgetReceiver.ACTION_LIKE, 3))
        return views
    }

    private fun bindBackdrop(views: RemoteViews) {
        npBackdrop?.let { views.setImageViewBitmap(R.id.widget_np_backdrop, it) }
            ?: views.setImageViewResource(R.id.widget_np_backdrop, R.drawable.widget_np_art_placeholder)
    }

    private fun bindProgress(views: RemoteViews, duration: Long, position: Long) {
        val safeDuration = duration.coerceAtLeast(0L)
        val safePosition = position.coerceIn(0L, safeDuration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        val progress = if (safeDuration > 0L) ((safePosition.toDouble() / safeDuration.toDouble()) * 1000).toInt() else 0
        views.setProgressBar(R.id.widget_np_progress, 1000, progress, false)
        views.setTextViewText(R.id.widget_np_elapsed, formatWidgetTime(safePosition))
        views.setTextViewText(R.id.widget_np_duration, formatWidgetTime(safeDuration))
    }

    // keeps the lines to show and says whether they differ from what the widgets show now
    private fun advanceLyric(current: String, next: String): Boolean {
        if (current == lyricNow && next == lyricNext) return false
        lyricNow = current
        lyricNext = next
        return true
    }

    // only the lyric change partial flips, every other drawing writes both children and leaves the flipper alone
    private fun bindLyric(
        views: RemoteViews,
        fresh: Boolean = false,
        fourByTwo: Boolean = false,
        staticLyric: Boolean = false,
    ) {
        if (fresh || staticLyric) {
            views.setTextViewText(R.id.widget_np_lyric_a, lyricNow)
            views.setTextViewText(R.id.widget_np_next_a, lyricNext)
            views.setTextViewText(R.id.widget_np_lyric_b, lyricNow)
            views.setTextViewText(R.id.widget_np_next_b, lyricNext)
            return
        }
        val current = if (fourByTwo) lyricChild4x2 else lyricChild2x2
        val target = 1 - current
        val first = target == 0
        views.setTextViewText(if (first) R.id.widget_np_lyric_a else R.id.widget_np_lyric_b, lyricNow)
        views.setTextViewText(if (first) R.id.widget_np_next_a else R.id.widget_np_next_b, lyricNext)
        views.setDisplayedChild(R.id.widget_np_lyric_flipper, target)
        if (fourByTwo) lyricChild4x2 = target else lyricChild2x2 = target
    }

    // the frosted backdrop, thumbnail and accent colour, made once for each new cover
    private fun prepareNowPlayingArtwork(artworkUri: String?, albumArt: Bitmap?) {
        if (artworkUri == npArtworkUri && (albumArt == null || npBackdrop != null)) return
        npArtworkUri = artworkUri
        npBackdrop = albumArt?.let { frostedBackdrop(it) }
        npThumb = albumArt?.let { thumbnailOf(it) }
        npAccent = albumArt?.let { accentOf(it) } ?: Color.WHITE
        // a new cover means every widget needs a full drawing again, bitmaps included
        npDrawn.clear()
    }

    // a small blurred copy the widget scales up, a few kb to send instead of a few hundred
    private fun frostedBackdrop(source: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(source, BackdropPx, BackdropPx, true)
        val width = small.width
        val height = small.height
        var from = IntArray(width * height).also { small.getPixels(it, 0, width, 0, 0, width, height) }
        var into = IntArray(width * height)
        repeat(2) {
            blurPass(from, into, width, height, horizontal = true)
            blurPass(into, from, width, height, horizontal = false)
        }
        return Bitmap.createBitmap(from, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun blurPass(from: IntArray, into: IntArray, width: Int, height: Int, horizontal: Boolean) {
        val radius = BackdropBlur
        val taps = radius * 2 + 1
        for (y in 0 until height) {
            for (x in 0 until width) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                for (k in -radius..radius) {
                    val sx = if (horizontal) (x + k).coerceIn(0, width - 1) else x
                    val sy = if (horizontal) y else (y + k).coerceIn(0, height - 1)
                    val pixel = from[sy * width + sx]
                    a += pixel ushr 24
                    r += (pixel shr 16) and 0xFF
                    g += (pixel shr 8) and 0xFF
                    b += pixel and 0xFF
                }
                into[y * width + x] = ((a / taps) shl 24) or ((r / taps) shl 16) or ((g / taps) shl 8) or (b / taps)
            }
        }
    }

    // the cover as a rounded square, scaled down so the widget isn't sent more pixels than it shows
    private fun thumbnailOf(source: Bitmap): Bitmap {
        val rounded = getRoundedCornerBitmap(source, minOf(source.width, source.height) * 0.14f)
        return Bitmap.createScaledBitmap(rounded, ThumbPx, ThumbPx, true)
    }

    // a light, lively colour from the cover for the header line, white when the cover has none
    private fun accentOf(source: Bitmap): Int {
        val palette = Palette.from(source).maximumColorCount(12).generate()
        return palette.getLightVibrantColor(palette.getVibrantColor(palette.getLightMutedColor(Color.WHITE)))
    }

    private fun formatWidgetTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(java.util.Locale.ROOT, minutes, seconds)
    }

    private fun createRemoteViewsForSize(
        options: Bundle,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        currentPosition: Long
    ): RemoteViews {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        return when {
            minWidth < 180 && minHeight < 100 -> {

                createCompactSquareRemoteViews(albumArt, isPlaying)
            }
            minWidth >= 180 && minHeight < 100 -> {

                createCompactWideRemoteViews(title, artist, albumArt, isPlaying, isLiked)
            }
            else -> {

                createRemoteViews(title, artist, albumArt, isPlaying, isLiked, duration, currentPosition)
            }
        }
    }

    private fun createRemoteViews(
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long = 0,
        currentPosition: Long = 0
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_music_player)

        views.setTextViewText(R.id.widget_song_title, title)
        views.setTextViewText(R.id.widget_artist_name, artist)

        if (albumArt != null) {
            val roundedAlbumArt = getRoundedCornerBitmap(albumArt, 48f)
            views.setImageViewBitmap(R.id.widget_album_art, roundedAlbumArt)
        } else {
            views.setImageViewBitmap(R.id.widget_album_art, getRoundedDefaultIcon(48f))
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

        val likeIcon = if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        views.setImageViewResource(R.id.widget_like_button, likeIcon)

        if (duration > 0) {
            val level = ((currentPosition.toDouble() / duration.toDouble()) * 10000).toInt()
            views.setInt(R.id.widget_progress_fill, "setImageLevel", level)
        } else {
            views.setInt(R.id.widget_progress_fill, "setImageLevel", 0)
        }

        views.setOnClickPendingIntent(R.id.widget_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_play_pause_container, getPlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_like_button, getLikeIntent())

        return views
    }

    private suspend fun loadAlbumArt(artworkUri: String, size: Int = 200): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .size(size, size)
                    .allowHardware(false)
                    .crossfade(300)
                    .build()
                val result = imageLoader.execute(request)
                result.image?.toBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {

        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            shader = BitmapShader(squareBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        if (squareBitmap != bitmap) {
            squareBitmap.recycle()
        }

        return output
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)

        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            shader = BitmapShader(squareBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        if (squareBitmap != bitmap) {
            squareBitmap.recycle()
        }
        return output
    }

    private fun createCompactSquareRemoteViews(
        albumArt: Bitmap?,
        isPlaying: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_square)

        if (albumArt != null) {
            val roundedAlbumArt = getRoundedCornerBitmap(albumArt, 48f)
            views.setImageViewBitmap(R.id.widget_compact_album_art, roundedAlbumArt)
        } else {
            views.setImageViewBitmap(R.id.widget_compact_album_art, getRoundedDefaultIcon(48f))
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause_low else R.drawable.ic_widget_play_low
        views.setImageViewResource(R.id.widget_compact_play_pause, playPauseIcon)

        views.setOnClickPendingIntent(R.id.widget_compact_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_compact_play_container, getPlayPauseIntent())

        return views
    }

    private fun createCompactWideRemoteViews(
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_wide)

        views.setTextViewText(R.id.widget_wide_song_title, title)
        views.setTextViewText(R.id.widget_wide_artist_name, artist)

        if (albumArt != null) {
            val roundedAlbumArt = getRoundedCornerBitmap(albumArt, 48f)
            views.setImageViewBitmap(R.id.widget_wide_album_art, roundedAlbumArt)
        } else {

            views.setImageViewBitmap(R.id.widget_wide_album_art, getRoundedDefaultIcon(48f))
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause_low else R.drawable.ic_widget_play_low
        views.setImageViewResource(R.id.widget_wide_play_pause, playPauseIcon)

        val likeIcon = if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        views.setImageViewResource(R.id.widget_wide_like_button, likeIcon)

        views.setOnClickPendingIntent(R.id.widget_wide_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_wide_play_container, getPlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_wide_like_button, getLikeIntent())

        return views
    }

    private fun createTurntableRemoteViews(
        circularAlbumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_turntable)

        if (circularAlbumArt != null) {
            views.setImageViewBitmap(R.id.widget_turntable_album_art, circularAlbumArt)
        } else {

            views.setImageViewBitmap(R.id.widget_turntable_album_art, getCircularDefaultIcon())
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_widget_pause_secondary else R.drawable.ic_widget_play_secondary
        views.setImageViewResource(R.id.widget_turntable_play_pause, playPauseIcon)

        views.setOnClickPendingIntent(R.id.widget_turntable_album_art, getOpenAppIntent())
        views.setOnClickPendingIntent(R.id.widget_turntable_play_container, getTurntablePlayPauseIntent())
        views.setOnClickPendingIntent(R.id.widget_turntable_prev_button, getTurntablePreviousIntent())
        views.setOnClickPendingIntent(R.id.widget_turntable_next_button, getTurntableNextIntent())

        return views
    }

    private fun getCircularDefaultIcon(): Bitmap {

        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return getCircularBitmap(bitmap)
    }

    private fun getRoundedDefaultIcon(cornerRadius: Float): Bitmap {

        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return getRoundedCornerBitmap(bitmap, cornerRadius)
    }

    private fun getOpenAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getPlayPauseIntent(): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_PLAY_PAUSE
        }
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getLikeIntent(): PendingIntent {
        val intent = Intent(context, MusicWidgetReceiver::class.java).apply {
            action = MusicWidgetReceiver.ACTION_LIKE
        }
        return PendingIntent.getBroadcast(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntablePlayPauseIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_PLAY_PAUSE
        }
        return PendingIntent.getBroadcast(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntableNextIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_NEXT
        }
        return PendingIntent.getBroadcast(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getTurntablePreviousIntent(): PendingIntent {
        val intent = Intent(context, TurntableWidgetReceiver::class.java).apply {
            action = TurntableWidgetReceiver.ACTION_TURNTABLE_PREVIOUS
        }
        return PendingIntent.getBroadcast(
            context,
            5,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        // a 2x2 shorter than this (in dp) drops the next lyric line
        const val CompactBelowDp = 140
        // the frosted background: how many pixels it is made of, and how far each one is blurred
        const val BackdropPx = 120
        const val BackdropBlur = 3
        const val ThumbPx = 192
    }
}
