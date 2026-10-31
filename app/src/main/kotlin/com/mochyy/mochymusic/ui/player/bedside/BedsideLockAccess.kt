package com.mochyy.mochymusic.ui.player.bedside

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.BedsideLockPromptDismissedKey
import com.mochyy.mochymusic.constants.BedsideLockScreenKey
import com.mochyy.mochymusic.ui.theme.InterFontFamily
import com.mochyy.mochymusic.utils.rememberPreference

// miui and hyperos keep a "show on lock screen" switch of their own per app, off until someone turns it on, and while it's off setShowWhenLocked does nothing at all
private const val MiuiShowWhenLockedOp = 10020

private val PromptShape = RoundedCornerShape(24.dp)
private val PromptFill = Color(0xFF1C1C1E)

private fun systemProperty(key: String): String? = try {
    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, key) as? String
} catch (e: Exception) {
    null
}

private val isMiui: Boolean by lazy {
    !systemProperty("ro.miui.ui.version.name").isNullOrEmpty() || !systemProperty("ro.mi.os.version.name").isNullOrEmpty()
}

// whether the phone lets mochy stand over the lock screen; only miui and hyperos ever say no
internal fun canShowOnLockScreen(context: Context): Boolean {
    if (!LockScreenSupported || !isMiui) return true
    return try {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val check = AppOpsManager::class.java.getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
        check.invoke(ops, MiuiShowWhenLockedOp, Process.myUid(), context.packageName) as Int == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        // can't tell, so it asks; the worst case is one extra prompt
        false
    }
}

// opens the screen where the switch lives: miui's own permission page if it can find it, the app's info page otherwise
internal fun openLockScreenAccess(context: Context) {
    val pkg = context.packageName
    val miuiPages = listOf(
        "com.miui.permcenter.permissions.PermissionsEditorActivity",
        "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
    ).map { page ->
        Intent("miui.intent.action.APP_PERM_EDITOR").setClassName("com.miui.securitycenter", page).putExtra("extra_pkgname", pkg)
    }
    val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
    for (intent in miuiPages + appInfo) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            continue
        } catch (e: SecurityException) {
            continue
        }
    }
}

// whether the switch is on, looked at again every time the app comes back, which is how it notices someone turning it on in settings
@Composable
internal fun rememberLockScreenAccess(): State<Boolean> {
    val context = LocalContext.current
    val allowed = remember(context) { mutableStateOf(canShowOnLockScreen(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed.value = canShowOnLockScreen(context) }
    return allowed
}

// asks once, over the bedside, for the switch the phone keeps off; "not now" leaves it to the bedside's sheet
@Composable
internal fun LockScreenPrompt(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wanted by rememberPreference(BedsideLockScreenKey, defaultValue = true)
    var dismissed by rememberPreference(BedsideLockPromptDismissedKey, defaultValue = false)
    val allowed by rememberLockScreenAccess()
    if (!wanted || dismissed || allowed || !LockScreenSupported) return
    Column(
        modifier = modifier
            .widthIn(max = 360.dp)
            .clip(PromptShape)
            .background(PromptFill)
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        BasicText(stringResource(R.string.bedside_lock_ask_title), style = promptText(16.sp, FontWeight.Bold, Color.White))
        Spacer(Modifier.height(6.dp))
        BasicText(stringResource(R.string.bedside_lock_ask_body), style = promptText(13.sp, FontWeight.Normal, Color.White.copy(alpha = 0.65f)))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PromptButton(stringResource(R.string.bedside_lock_ask_later), filled = false, modifier = Modifier.weight(1f)) { dismissed = true }
            PromptButton(stringResource(R.string.bedside_lock_ask_allow), filled = true, modifier = Modifier.weight(1f)) { openLockScreenAccess(context) }
        }
    }
}

@Composable
private fun PromptButton(label: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        BasicText(label, style = promptText(14.sp, FontWeight.SemiBold, if (filled) Color.Black else Color.White))
    }
}

private fun promptText(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, colour: Color) = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = size,
    fontWeight = weight,
    color = colour,
    letterSpacing = (-0.02).em,
)
