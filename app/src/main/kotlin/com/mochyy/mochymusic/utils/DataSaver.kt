// data saver as playback and the pages read it: on when its switch is, and only on mobile data unless it's set to always

package com.mochyy.mochymusic.utils

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import com.mochyy.mochymusic.constants.DataSaverKey
import com.mochyy.mochymusic.constants.DataSaverMobileOnlyKey

object DataSaver {

    @Volatile
    private var app: Context? = null

    /** app hands its context over at start, so code without one (the youtube stream picker) can still ask */
    fun attach(context: Context) {
        app = context.applicationContext
    }

    /** whether data saver is working right now on [context]'s network */
    fun isActive(context: Context): Boolean {
        val metered = context.getSystemService<ConnectivityManager>()?.isActiveNetworkMetered == true
        return isActive(context, metered)
    }

    /** the same for a caller that already knows whether the network is metered */
    fun isActive(metered: Boolean): Boolean = app?.let { isActive(it, metered) } ?: false

    // the cached settings snapshot, so this is cheap enough to ask for every song
    private fun isActive(context: Context, metered: Boolean): Boolean {
        val store = context.dataStore
        if (!store.get(DataSaverKey, false)) return false
        return !store.get(DataSaverMobileOnlyKey, true) || metered
    }
}

/** data saver for a page, following both switches and the phone moving between wi-fi and mobile data */
@Composable
fun rememberDataSaverActive(): Boolean {
    val context = LocalContext.current
    val (enabled) = rememberPreference(DataSaverKey, defaultValue = false)
    val (mobileOnly) = rememberPreference(DataSaverMobileOnlyKey, defaultValue = true)
    var metered by remember { mutableStateOf(context.getSystemService<ConnectivityManager>()?.isActiveNetworkMetered == true) }
    // only listened for while data saver is on and cares which network this is
    DisposableEffect(enabled, mobileOnly) {
        val manager = context.getSystemService<ConnectivityManager>()
        if (!enabled || !mobileOnly || manager == null) return@DisposableEffect onDispose {}
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, capabilities: android.net.NetworkCapabilities) {
                metered = manager.isActiveNetworkMetered
            }

            override fun onLost(network: android.net.Network) {
                metered = manager.isActiveNetworkMetered
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return enabled && (!mobileOnly || metered)
}
