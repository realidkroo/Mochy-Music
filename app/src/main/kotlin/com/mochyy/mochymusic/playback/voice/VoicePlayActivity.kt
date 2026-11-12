// VoicePlayActivity.kt

package com.mochyy.mochymusic.playback.voice

import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import com.mochyy.mochymusic.playback.MusicService

// google assistant on the phone sends "play … on mochy" here; it draws nothing, hands the words to the music service and closes
class VoicePlayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val query = intent?.getStringExtra(SearchManager.QUERY)?.trim().orEmpty()
        VoiceHandoff.send(applicationContext, query, VoiceSearch.extrasOf(intent?.extras))
        finish()
    }
}

// asks the session the same way android auto does, and keeps hold of it until the music starts so the service isn't let go mid-search
@androidx.annotation.OptIn(UnstableApi::class)
private object VoiceHandoff {
    // long enough for a slow search; when music was already playing it never starts again, so this is what lets go
    private const val HoldMs = 20_000L

    fun send(context: Context, query: String, extras: Bundle?) {
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val pending = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onError(controller: MediaController, sessionError: SessionError) {
                    Toast.makeText(context, sessionError.message, Toast.LENGTH_LONG).show()
                    controller.release()
                }
            })
            .buildAsync()
        pending.addListener({
            val controller = runCatching { pending.get() }.getOrNull() ?: return@addListener
            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) controller.release()
                }
            })
            controller.setMediaItem(request(query, extras))
            Handler(Looper.getMainLooper()).postDelayed({ controller.release() }, HoldMs)
        }, ContextCompat.getMainExecutor(context))
    }

    // the same shape media3 gives android auto's play-from-search: no id, just the words and google's hints
    private fun request(query: String, extras: Bundle?) = MediaItem.Builder()
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setSearchQuery(query)
                .setExtras(extras)
                .build(),
        )
        .build()
}
