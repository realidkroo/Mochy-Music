// TvPairing.kt

package com.mochyy.mochymusic.link.tv

import android.content.Context
import androidx.compose.runtime.Immutable
import com.mochyy.mochymusic.link.LinkAdvert
import com.mochyy.mochymusic.link.LinkHttpServer
import com.mochyy.mochymusic.link.LinkIdentity
import com.mochyy.mochymusic.link.LinkRequest
import com.mochyy.mochymusic.link.LinkResponse
import com.mochyy.mochymusic.link.LinkWire
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

// who a pairing is for: a brand new person on the tv, or a profile that wants its phone
sealed interface PairTarget {
    data object NewProfile : PairTarget
    data class Profile(val id: String) : PairTarget
}

// what the pairing screen shows: the code to type on the phone and the name the phone will see
@Immutable
data class PairingCode(val code: String, val tvName: String)

// while its pairing screen is open, the tv waits on the wi-fi for a phone that knows the code it shows
@Singleton
class TvPairing @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sync: TvPhoneSync,
) {
    private val server = LinkHttpServer("TvPair", ::route)
    private var advert: LinkAdvert? = null
    private val random = SecureRandom()

    @Volatile private var target: PairTarget = PairTarget.NewProfile
    @Volatile private var wrongTries = 0

    private val _code = MutableStateFlow<PairingCode?>(null)
    val code: StateFlow<PairingCode?> = _code.asStateFlow()

    private val _paired = MutableSharedFlow<TvProfile>(extraBufferCapacity = 1)
    val paired: SharedFlow<TvProfile> = _paired.asSharedFlow()

    @Synchronized
    fun open(target: PairTarget) {
        this.target = target
        if (server.isRunning) return
        val name = LinkIdentity.name(context)
        val port = server.start(LinkWire.TvPairPort)
        newCode(name)
        advert = LinkAdvert(
            context,
            LinkWire.TvServiceType,
            "Mochy $name",
            port,
            mapOf(LinkWire.AttrId to LinkIdentity.id(context), LinkWire.AttrName to name),
        )
    }

    @Synchronized
    fun close() {
        advert?.close()
        advert = null
        server.stop()
        _code.value = null
    }

    private fun newCode(name: String = LinkIdentity.name(context)) {
        wrongTries = 0
        _code.value = PairingCode(code = (random.nextInt(900_000) + 100_000).toString(), tvName = name)
    }

    private fun route(request: LinkRequest): LinkResponse = when {
        request.path == "/pair/hello" -> LinkResponse.json(
            JSONObject().put("id", LinkIdentity.id(context)).put("name", LinkIdentity.name(context)).put("protocol", LinkWire.ProtocolVersion).toString(),
        )
        request.path == "/pair" && request.method == "POST" -> pair(request)
        else -> LinkResponse.status(404)
    }

    private fun pair(request: LinkRequest): LinkResponse {
        val body = runCatching { JSONObject(request.bodyText()) }.getOrNull() ?: return LinkResponse.status(400)
        val expected = _code.value?.code ?: return LinkResponse.status(409, "not pairing")
        if (!sameCode(body.optString("code"), expected)) {
            // guessing is cut short: a few wrong codes and the screen shows a new one
            wrongTries++
            if (wrongTries >= MaxWrongTries) newCode()
            return LinkResponse.status(403, "wrong code")
        }
        val phone = LinkedPhone(
            id = body.optString("phoneId"),
            name = body.optString("phoneName").ifBlank { "Phone" },
            host = request.remoteHost,
            port = body.optInt("port", LinkWire.PhonePort),
            key = body.optString("key").ifBlank { return LinkResponse.status(400) },
            pairedAt = System.currentTimeMillis(),
        )
        val userName = body.optString("userName").ifBlank { phone.name }
        val profile = when (val goingTo = target) {
            is PairTarget.Profile -> {
                TvProfiles.update(context, goingTo.id) { it.copy(phone = phone) }
                TvProfiles.byId(goingTo.id)
            }
            // the same phone paired again lands in the profile it already had instead of a second copy of it
            PairTarget.NewProfile -> TvProfiles.byPhone(phone.id)?.also { existing ->
                TvProfiles.update(context, existing.id) { it.copy(phone = phone) }
            } ?: TvProfiles.create(context, userName, avatarPath = null, phone = phone)
        } ?: return LinkResponse.status(409, "profile gone")
        if (body.optBoolean("hasAvatar")) sync.fetchAvatarAfterPairing(profile.id)
        _paired.tryEmit(TvProfiles.byId(profile.id) ?: profile)
        newCode()
        return LinkResponse.json(
            JSONObject().put("tvId", LinkIdentity.id(context)).put("tvName", LinkIdentity.name(context)).put("profileId", profile.id).toString(),
        )
    }

    private fun sameCode(given: String, expected: String): Boolean =
        MessageDigest.isEqual(given.trim().toByteArray(), expected.toByteArray())

    private companion object {
        const val MaxWrongTries = 5
    }
}
