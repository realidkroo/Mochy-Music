// FloatingLyricsSwitch.kt

package com.mochyy.mochymusic.floating

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.FloatingLyricsKey
import com.mochyy.mochymusic.utils.rememberPreference

// the floating lyrics switch the player menu, customisation and app behaviour share; it only reads as on once android lets mochy show over other apps
@Composable
fun rememberFloatingLyricsSwitch(): Pair<Boolean, (Boolean) -> Unit> {
    val context = LocalContext.current
    val (wanted, setWanted) = rememberPreference(FloatingLyricsKey, defaultValue = false)
    var allowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    // the permission is given on android's own screen, so it's checked again whenever mochy comes back
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = Settings.canDrawOverlays(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val onChange: (Boolean) -> Unit = { on ->
        setWanted(on)
        if (on && !Settings.canDrawOverlays(context)) askForOverlay(context)
    }
    return (wanted && allowed) to onChange
}

private fun askForOverlay(context: Context) {
    Toast.makeText(context, R.string.floating_lyrics_permission, Toast.LENGTH_LONG).show()
    val ask = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // a few phones hide that screen, the app's own settings page has the same switch
    runCatching { context.startActivity(ask) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
