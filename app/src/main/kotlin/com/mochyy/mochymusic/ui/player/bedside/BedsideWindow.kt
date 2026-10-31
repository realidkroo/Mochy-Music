package com.mochyy.mochymusic.ui.player.bedside

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mochyy.mochymusic.ui.component.LocalPlayerBottomSheetState
import com.mochyy.mochymusic.ui.player.findActivity
import kotlinx.coroutines.flow.distinctUntilChanged

// asking for an unlock from over the lock screen needs android 8.1, older phones just don't get the option
internal val LockScreenSupported: Boolean get() = Build.VERSION.SDK_INT >= 27

// how open the player must look before the bedside keeps the screen on or stands over the lock screen
private const val OpenEnough = 0.9f

// keeps the screen on while the bedside is open and lets it stay up over the lock screen; the moment it starts to close, or a finger pulls it down, the lock screen gets its say back
@Composable
internal fun BedsideWindowFlags(keepAwake: Boolean, overLockScreen: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    val sheet = LocalPlayerBottomSheetState.current ?: return
    LaunchedEffect(activity, sheet, keepAwake, overLockScreen) {
        try {
            snapshotFlow { sheet.isExpandedOrExpanding && !sheet.isHeld && sheet.progress > OpenEnough }
                .distinctUntilChanged()
                .collect { open ->
                    activity.keepAwake(open && keepAwake)
                    activity.standOverLockScreen(open && overLockScreen)
                }
        } finally {
            activity.keepAwake(false)
            activity.standOverLockScreen(false)
        }
    }
}

// a window flag of its own, so it never fights the app wide keep screen on setting that sits on the view
private fun Activity.keepAwake(on: Boolean) {
    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}

private fun Activity.standOverLockScreen(on: Boolean) {
    if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(on)
}

// over the lock screen this asks for the phone to be unlocked and only then does it; unlocked, it just does it
internal fun Context.unlockThen(action: () -> Unit) {
    val activity = findActivity()
    val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    if (activity == null || keyguard == null || !keyguard.isKeyguardLocked || Build.VERSION.SDK_INT < 27) {
        action()
        return
    }
    askToUnlock(activity, keyguard, action)
}

@RequiresApi(27)
private fun askToUnlock(activity: Activity, keyguard: KeyguardManager, then: () -> Unit) {
    keyguard.requestDismissKeyguard(
        activity,
        object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = then()
        },
    )
}

// whether the phone is locked under the bedside, looked at again whenever the screen wakes, sleeps or unlocks
@Composable
internal fun rememberPhoneLocked(): State<Boolean> {
    val context = LocalContext.current
    val keyguard = remember(context) { context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager }
    val locked = remember(keyguard) { mutableStateOf(keyguard?.isKeyguardLocked == true) }
    DisposableEffect(context, keyguard) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                locked.value = keyguard?.isKeyguardLocked == true
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    // the screen waking over the lock screen resumes the app, and that's the moment that counts
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { locked.value = keyguard?.isKeyguardLocked == true }
    return locked
}

// only while locked, so it registers after the sheet's own back and wins: back asks for an unlock before the player closes
@Composable
internal fun LockedBackHandler(locked: Boolean, onBack: () -> Unit) {
    if (locked) BackHandler(onBack = onBack)
}
