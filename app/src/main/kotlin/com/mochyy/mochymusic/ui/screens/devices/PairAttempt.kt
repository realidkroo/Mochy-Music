// PairAttempt.kt

package com.mochyy.mochymusic.ui.screens.devices

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.LinkPeer
import com.mochyy.mochymusic.link.phone.PairException
import com.mochyy.mochymusic.link.phone.PhoneLinkHost

// where a pairing attempt is, for the line under the code
sealed interface PairUi {
    data object Idle : PairUi
    data object Working : PairUi
    data object WrongCode : PairUi
    data object Full : PairUi
    data object Failed : PairUi
    data object NoneFound : PairUi
    data class Done(val name: String) : PairUi
}

// the line shown for a state, null while there's nothing to say
internal val PairUi.note: Int?
    get() = when (this) {
        PairUi.Working -> R.string.tv_link_pairing
        PairUi.WrongCode -> R.string.devices_wrong_code
        PairUi.Full -> R.string.devices_pair_full
        PairUi.Failed -> R.string.devices_pair_failed
        PairUi.NoneFound -> R.string.devices_enter_code_none
        else -> null
    }

internal val PairUi.isProblem: Boolean
    get() = this == PairUi.WrongCode || this == PairUi.Full || this == PairUi.Failed || this == PairUi.NoneFound

// one go at pairing with a typed code; with several devices in pairing mode it's tried on each, since only the right one takes it
@Stable
internal class PairAttempt(private val host: PhoneLinkHost) {
    var state by mutableStateOf<PairUi>(PairUi.Idle)
        private set

    fun reset() {
        state = PairUi.Idle
    }

    suspend fun pair(peers: List<LinkPeer>, code: String) {
        if (peers.isEmpty()) {
            state = PairUi.NoneFound
            return
        }
        state = PairUi.Working
        var wrong = false
        var full = false
        for (peer in peers) {
            val result = host.pair(peer, code)
            val paired = result.getOrNull()
            if (paired != null) {
                state = PairUi.Done(paired.name)
                return
            }
            when ((result.exceptionOrNull() as? PairException)?.reason) {
                PairException.Reason.WrongCode -> wrong = true
                PairException.Reason.Full -> full = true
                else -> Unit
            }
        }
        state = when {
            full -> PairUi.Full
            wrong -> PairUi.WrongCode
            else -> PairUi.Failed
        }
    }
}
