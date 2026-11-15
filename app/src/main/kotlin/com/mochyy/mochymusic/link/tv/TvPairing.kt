// TvPairing.kt

package com.mochyy.mochymusic.link.tv

import android.content.Context
import com.mochyy.mochymusic.link.DeviceKind
import com.mochyy.mochymusic.link.LinkAuth
import com.mochyy.mochymusic.link.LinkIdentity
import com.mochyy.mochymusic.link.LinkPairingDoor
import com.mochyy.mochymusic.link.LinkResponse
import com.mochyy.mochymusic.link.LinkWire
import com.mochyy.mochymusic.link.PairingCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// who a pairing is for: a brand new person on the tv, or a profile that wants its phone
sealed interface PairTarget {
    data object NewProfile : PairTarget
    data class Profile(val id: String) : PairTarget
}

// while its pairing screen is open, the tv waits on the wi-fi for a phone that knows the code it shows
@Singleton
class TvPairing @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sync: TvPhoneSync,
) {
    private val door = LinkPairingDoor(context, "TvPair", ::accept)

    @Volatile private var target: PairTarget = PairTarget.NewProfile

    val code: StateFlow<PairingCode?> = door.code

    private val _paired = MutableSharedFlow<TvProfile>(extraBufferCapacity = 1)
    val paired: SharedFlow<TvProfile> = _paired.asSharedFlow()

    fun open(target: PairTarget) {
        this.target = target
        door.open()
    }

    fun close() = door.close()

    fun advertiseAgain() = door.advertiseAgain()

    // the code matched: the phone lands in a profile, new or the one that asked for it
    private fun accept(body: JSONObject, remoteHost: String, code: String): LinkResponse {
        val phoneId = body.optString("phoneId").ifBlank { return LinkResponse.status(400) }
        // a phone on the old protocol sends the key itself instead of a public half; it has to update first
        val otherPublic = body.optString("pub").ifBlank { return LinkResponse.status(426, "update mochy on the phone") }
        val keyPair = LinkAuth.newKeyPair()
        val key = LinkAuth.sharedKey(keyPair, otherPublic, code, listOf(phoneId, LinkIdentity.id(context))) ?: return LinkResponse.status(400)
        val phone = LinkedPhone(
            id = phoneId,
            name = body.optString("phoneName").ifBlank { "Phone" },
            host = remoteHost,
            port = body.optInt("port", LinkWire.PhonePort),
            key = key,
            pairedAt = System.currentTimeMillis(),
        )
        val userName = body.optString("userName").ifBlank { phone.name }
        val profile = when (val goingTo = target) {
            is PairTarget.Profile -> {
                TvProfiles.update(context, goingTo.id) { it.copy(phone = phone) }
                TvProfiles.byId(goingTo.id)
            }
            // the same phone paired again lands in the profile it already had instead of a second copy of it
            // and takes the name the phone has now, which may have changed since
            PairTarget.NewProfile -> TvProfiles.byPhone(phone.id)?.also { existing ->
                TvProfiles.update(context, existing.id) { it.copy(phone = phone, name = userName) }
            } ?: TvProfiles.create(context, userName, avatarPath = null, phone = phone)
        } ?: return LinkResponse.status(409, "profile gone")
        if (body.optBoolean("hasAvatar")) sync.fetchAvatarAfterPairing(profile.id, body.optString("avatar").ifBlank { null })
        _paired.tryEmit(TvProfiles.byId(profile.id) ?: profile)
        return LinkResponse.json(
            JSONObject()
                .put("tvId", LinkIdentity.id(context))
                .put("tvName", LinkIdentity.name(context))
                .put("kind", DeviceKind.Tv.wire)
                .put("profileId", profile.id)
                .put("pub", LinkAuth.publicText(keyPair))
                .toString(),
        )
    }
}
