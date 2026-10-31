package com.mochyy.mochymusic.playback

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

// says when a call starts and ends, because playing without audio focus means android won't pause us for one
class CallWatcher(
    private val context: Context,
    private val onCallChanged: (inCall: Boolean) -> Unit,
) {
    private var stopWatching: (() -> Unit)? = null
    private var inCall = false

    fun start() {
        if (stopWatching != null) return
        stopWatching = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) watchAudioMode() else watchPhoneState()
    }

    fun stop() {
        stopWatching?.invoke()
        stopWatching = null
        inCall = false
    }

    private fun report(nowInCall: Boolean) {
        if (nowInCall == inCall) return
        inCall = nowInCall
        onCallChanged(nowInCall)
    }

    // any mode but normal is a ringing phone, a phone call or a voip call, and listening needs no permission
    @RequiresApi(Build.VERSION_CODES.S)
    private fun watchAudioMode(): () -> Unit {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val listener = AudioManager.OnModeChangedListener { mode -> report(mode != AudioManager.MODE_NORMAL) }
        audioManager.addOnModeChangedListener(ContextCompat.getMainExecutor(context), listener)
        return { audioManager.removeOnModeChangedListener(listener) }
    }

    // before android 12 the call state needs no permission, it just can't see voip calls
    @Suppress("DEPRECATION")
    private fun watchPhoneState(): () -> Unit {
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return {}
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                report(state != TelephonyManager.CALL_STATE_IDLE)
            }
        }
        telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        return { telephony.listen(listener, PhoneStateListener.LISTEN_NONE) }
    }
}
