// SongPreviewPlayer.kt

package com.mochyy.mochymusic.ui.player.share.song

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

// how often the player thread reports where the song is
private const val ReportEveryMs = 40L

// a jump back this big between two reports means the window started over
private const val WrapJumpMs = 1_500L

// one thread for the share's song, so building and releasing its player never costs the editor a frame
private object SharePreviewThread {
    val handler: Handler by lazy { Handler(HandlerThread("SharePreview").apply { start() }.looper) }
}

// the song playing under the share editor, looping the picked window; touched only on its own thread
internal class SongPreviewPlayer(context: Context, dataSourceFactory: DataSource.Factory) {
    private val handler = SharePreviewThread.handler
    private val main = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null

    @Volatile private var clipStartMs = 0L
    @Volatile private var clipLengthMs = 0L
    @Volatile private var reportedMs = -1L
    @Volatile private var reportedAt = 0L
    @Volatile private var playing = false
    @Volatile private var released = false

    // counts every time the window starts over, so the background video can start over with it
    var loops by mutableIntStateOf(0)
        private set

    private val report = object : Runnable {
        override fun run() {
            val p = player ?: return
            val position = p.currentPosition
            val previous = reportedMs
            reportedMs = position
            reportedAt = SystemClock.uptimeMillis()
            playing = p.isPlaying
            if (previous >= 0L && previous - position > WrapJumpMs) main.post { if (!released) loops++ }
            handler.postDelayed(this, ReportEveryMs)
        }
    }

    init {
        val appContext = context.applicationContext
        handler.post {
            val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(4_000, 10_000, 500, 1_000)
                .setTargetBufferBytes(2 * 1024 * 1024)
                .build()
            player = ExoPlayer.Builder(appContext)
                .setLooper(handler.looper)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
                .apply {
                    // no audio focus: the share pauses the app's own playback itself and puts it back after
                    setAudioAttributes(
                        AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                        false,
                    )
                    repeatMode = Player.REPEAT_MODE_ONE
                }
            handler.post(report)
        }
    }

    // plays the song from startMs for lengthMs, over and over
    fun load(song: MediaItem, startMs: Long, lengthMs: Long, play: Boolean) {
        clipStartMs = startMs
        clipLengthMs = lengthMs
        reportedMs = -1L
        handler.post {
            player?.apply {
                val clipped = song.buildUpon()
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs)
                            .setEndPositionMs(startMs + lengthMs)
                            .build(),
                    )
                    .build()
                setMediaItem(clipped)
                prepare()
                playWhenReady = play
            }
        }
    }

    fun setPlaying(play: Boolean) = handler.post { player?.playWhenReady = play }

    fun restart() = handler.post { player?.seekTo(0L) }

    // the song position for a frame at nowMs (uptime), moved on from the last report; null before any sound
    fun positionMs(nowMs: Long): Long? {
        val reported = reportedMs
        if (reported < 0L) return null
        val moved = if (playing) (nowMs - reportedAt).coerceIn(0L, ReportEveryMs * 3) else 0L
        return clipStartMs + (reported + moved).coerceAtMost(clipLengthMs)
    }

    fun release() {
        released = true
        handler.post {
            handler.removeCallbacks(report)
            player?.release()
            player = null
        }
    }
}
