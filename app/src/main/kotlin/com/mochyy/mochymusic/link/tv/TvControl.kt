// TvControl.kt

package com.mochyy.mochymusic.link.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.mochyy.mochymusic.link.LinkControlRoutes
import com.mochyy.mochymusic.link.LinkHttpServer
import com.mochyy.mochymusic.link.LinkPlayer
import com.mochyy.mochymusic.link.LinkRequest
import com.mochyy.mochymusic.link.LinkResponse
import com.mochyy.mochymusic.link.LinkWire
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

// while a profile with a phone is open, the tv listens for that phone: play this here, play it next, skip, change a setting
object TvControl {
    private const val Prefs = "tv_control"
    private const val AllowedKey = "phone_can_control"

    // a phone that steers the tv asks what's on every few seconds; this long without a word and it has stopped
    private const val ControllerQuietMs = 12_000L

    private val server = LinkHttpServer("TvControl", ::route)
    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    @Volatile private var routes: LinkControlRoutes? = null

    // the tv's own "sync now", handed in by the activity that owns the sync
    @Volatile var onSyncRequest: (() -> Unit)? = null

    private val _allowed = MutableStateFlow(true)
    val allowed: StateFlow<Boolean> = _allowed.asStateFlow()

    private val _openPlayer = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // something the phone sent should be seen: the tv opens its player
    val openPlayer: SharedFlow<Unit> = _openPlayer.asSharedFlow()

    private val _controller = MutableStateFlow<String?>(null)

    // the name of the phone steering the tv right now, for the small pill in the corner
    val controller: StateFlow<String?> = _controller.asStateFlow()
    private val forgetController = Runnable { _controller.value = null }

    // the port the phone is told about on every hello, -1 while nobody's listening
    val port: Int get() = if (server.isRunning) server.port else -1

    fun start(context: Context) {
        appContext = context.applicationContext
        _allowed.value = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getBoolean(AllowedKey, true)
        if (routes == null) {
            routes = LinkControlRoutes(
                context,
                onOpenPlayer = { _openPlayer.tryEmit(Unit) },
                onSync = { onSyncRequest?.invoke() },
            )
        }
        if (!server.isRunning) server.start(LinkWire.TvControlPort)
    }

    fun stop() {
        server.stop()
        main.removeCallbacks(forgetController)
        _controller.value = null
    }

    fun setAllowed(on: Boolean) {
        _allowed.value = on
        if (!on) _controller.value = null
        appContext?.getSharedPreferences(Prefs, Context.MODE_PRIVATE)?.edit { putBoolean(AllowedKey, on) }
    }

    private fun route(request: LinkRequest): LinkResponse {
        val phone = TvProfiles.active()?.phone ?: return LinkResponse.status(409, "no phone linked")
        if (request.key != phone.key) return LinkResponse.status(401)
        if (!_allowed.value) return LinkResponse.status(403, "control is off on the tv")
        val connection = LinkPlayer.connection ?: return LinkResponse.status(409, "mochy isn't open on the tv")
        val handler = routes ?: return LinkResponse.status(409)
        noteController(phone.name)
        return handler.handle(request, connection)
    }

    private fun noteController(name: String) {
        if (_controller.value != name) _controller.value = name
        main.removeCallbacks(forgetController)
        main.postDelayed(forgetController, ControllerQuietMs)
    }
}
