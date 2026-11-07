// TvControl.kt

package com.mochyy.mochymusic.link.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.mochyy.mochymusic.constants.PlayerStyle
import com.mochyy.mochymusic.constants.PlayerStyleKey
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.extensions.togglePlayPause
import com.mochyy.mochymusic.link.LinkHttpServer
import com.mochyy.mochymusic.link.LinkRequest
import com.mochyy.mochymusic.link.LinkResponse
import com.mochyy.mochymusic.link.LinkSongs
import com.mochyy.mochymusic.link.LinkWire
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.utils.dataStore
import androidx.datastore.preferences.core.edit as editPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// while a profile with a phone is open, the tv listens for that phone: play this here, play it next, pause, skip, change the look
object TvControl {
    private const val Prefs = "tv_control"
    private const val AllowedKey = "phone_can_control"

    private val server = LinkHttpServer("TvControl", ::route)
    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    // the open app's player, set while the tv activity is around
    @Volatile var player: PlayerConnection? = null

    private val _allowed = MutableStateFlow(true)
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    private val _openPlayer = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // something the phone sent should be seen: the tv opens its player
    val openPlayer: SharedFlow<Unit> = _openPlayer.asSharedFlow()

    // the port the phone is told about on every hello, -1 while nobody's listening
    val port: Int get() = if (server.isRunning) server.port else -1

    fun start(context: Context) {
        appContext = context.applicationContext
        _allowed.value = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getBoolean(AllowedKey, true)
        if (!server.isRunning) server.start(LinkWire.TvControlPort)
    }

    fun stop() = server.stop()

    fun setAllowed(on: Boolean) {
        _allowed.value = on
        appContext?.getSharedPreferences(Prefs, Context.MODE_PRIVATE)?.edit { putBoolean(AllowedKey, on) }
    }

    private fun route(request: LinkRequest): LinkResponse {
        val phone = TvProfiles.active()?.phone ?: return LinkResponse.status(409, "no phone linked")
        if (request.key != phone.key) return LinkResponse.status(401)
        if (!_allowed.value) return LinkResponse.status(403, "control is off on the tv")
        val connection = player ?: return LinkResponse.status(409, "mochy isn't open on the tv")
        val body = if (request.method == "POST") runCatching { JSONObject(request.bodyText()) }.getOrNull() ?: JSONObject() else JSONObject()
        return when (request.path) {
            "/now" -> LinkResponse.json(now(connection).toString())
            "/play" -> {
                val songs = LinkSongs.listFromJson(body.optJSONArray("songs"))
                if (songs.isEmpty()) return LinkResponse.status(400)
                val start = body.optInt("index").coerceIn(0, songs.lastIndex)
                onMain {
                    connection.playQueue(ListQueue(title = body.optString("title").ifBlank { null }, items = songs.map { it.toMediaItem() }, startIndex = start, position = body.optLong("position")))
                    _openPlayer.tryEmit(Unit)
                }
                LinkResponse.json("{}")
            }
            "/next" -> {
                val songs = LinkSongs.listFromJson(body.optJSONArray("songs"))
                onMain { connection.playNext(songs.map { it.toMediaItem() }) }
                LinkResponse.json("{}")
            }
            "/control" -> {
                val position = body.optLong("position", -1L)
                onMain {
                    when (body.optString("action")) {
                        "toggle" -> connection.player.togglePlayPause()
                        "play" -> connection.play()
                        "pause" -> connection.pause()
                        "next" -> connection.seekToNext()
                        "previous" -> connection.seekToPrevious()
                        "seek" -> if (position >= 0) connection.seekTo(position)
                        "open" -> _openPlayer.tryEmit(Unit)
                    }
                }
                LinkResponse.json("{}")
            }
            "/style" -> {
                val style = PlayerStyle.entries.firstOrNull { it.name == body.optString("style") && it.availableOnTv }
                    ?: return LinkResponse.status(400)
                val context = appContext ?: return LinkResponse.status(409)
                runBlocking { context.dataStore.editPreferences { it[PlayerStyleKey] = style.name } }
                onMain { _openPlayer.tryEmit(Unit) }
                LinkResponse.json("{}")
            }
            else -> LinkResponse.status(404)
        }
    }

    // what the tv is playing, read on the main thread where the player lives
    private fun now(connection: PlayerConnection): JSONObject {
        var json = JSONObject()
        onMainAndWait {
            val song = connection.mediaMetadata.value
            json = JSONObject()
                .put("playing", connection.isPlaying.value)
                .put("position", connection.player.currentPosition.coerceAtLeast(0L))
                .put("duration", connection.player.duration.coerceAtLeast(0L))
                .apply { song?.let { put("song", LinkSongs.toJson(it)) } }
        }
        val style = appContext?.let { context -> runBlocking { context.dataStore.data.first()[PlayerStyleKey] } }
        return json
            .put("style", style ?: PlayerStyle.FLAGSHIP.name)
            .put("styles", org.json.JSONArray(PlayerStyle.entries.filter { it.availableOnTv }.map { JSONObject().put("name", it.name).put("label", it.selectorName) }))
    }

    private fun onMain(block: () -> Unit) {
        main.post(block)
    }

    private fun onMainAndWait(block: () -> Unit) {
        val done = CountDownLatch(1)
        main.post {
            try {
                block()
            } finally {
                done.countDown()
            }
        }
        done.await(2, TimeUnit.SECONDS)
    }
}
