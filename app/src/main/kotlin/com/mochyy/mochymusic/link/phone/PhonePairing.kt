// PhonePairing.kt

package com.mochyy.mochymusic.link.phone

import android.content.Context
import com.mochyy.mochymusic.link.DeviceKind
import com.mochyy.mochymusic.link.LinkIdentity
import com.mochyy.mochymusic.link.LinkPairingDoor
import com.mochyy.mochymusic.link.LinkResponse
import com.mochyy.mochymusic.link.PairingCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// while the add device sheet is open, this phone shows a code too, so another phone or tablet can pair with it
@Singleton
class PhonePairing @Inject constructor(
    @ApplicationContext private val context: Context,
    private val host: PhoneLinkHost,
) {
    private val door = LinkPairingDoor(context, "PhonePair", ::accept)

    val code: StateFlow<PairingCode?> = door.code

    private val _paired = MutableSharedFlow<PairedDevice>(extraBufferCapacity = 1)

    // someone typed this phone's code and is now paired with it
    val paired: SharedFlow<PairedDevice> = _paired.asSharedFlow()

    fun open() = door.open()

    fun close() = door.close()

    private fun accept(body: JSONObject, remoteHost: String): LinkResponse {
        val device = host.acceptPairing(body, remoteHost) ?: return LinkResponse.status(409, PhoneLinkHost.FullReply)
        _paired.tryEmit(device)
        return LinkResponse.json(
            JSONObject()
                .put("tvId", LinkIdentity.id(context))
                .put("tvName", LinkIdentity.name(context))
                .put("kind", DeviceKind.of(context).wire)
                .put("port", host.port)
                .toString(),
        )
    }
}
