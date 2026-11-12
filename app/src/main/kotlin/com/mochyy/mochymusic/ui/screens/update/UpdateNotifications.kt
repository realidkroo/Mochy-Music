// update notifications, at most three per release, riding the launch-time release check with no background job (see docs/notes/update.md)

package com.mochyy.mochymusic.ui.screens.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mochyy.mochymusic.MainActivity
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.core.updater.GithubRelease
import com.mochyy.mochymusic.core.updater.UpdateState
import com.mochyy.mochymusic.core.updater.getLatestRelease
import com.mochyy.mochymusic.core.updater.updateChannel
import com.mochyy.mochymusic.core.updater.isNewerThanInstalled
import com.mochyy.mochymusic.ui.component.LocalZoomOutOverlayState

/** intent action the notification opens MainActivity with, it then shows the version sheet */
const val ActionOpenUpdate = "com.mochyy.mochymusic.action.OPEN_UPDATE"

private const val PrefsName = "update_notifications"
private const val KeyRelease = "release"
private const val KeyFirstSeenAt = "first_seen_at"
private const val KeyPosted = "posted"

private const val DayMs = 24L * 60 * 60 * 1000

/** when each reminder is due, counted from the first time this release was seen */
private val ReminderOffsets = longArrayOf(0L, 14 * DayMs, 90 * DayMs)

/** the same channel App.kt registers at startup */
private const val ChannelId = "updates"
private const val NotificationId = 1001

/** call after a launch-time check found release newer than what's installed, posts a notification if one of its three reminders is due */
fun maybeNotifyUpdate(context: Context, release: GithubRelease, now: Long = System.currentTimeMillis()) {
    val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    val releaseKey = release.tag.ifBlank { release.version }

    // a newer release than the one being tracked starts its own three reminders
    val sameRelease = prefs.getString(KeyRelease, null) == releaseKey
    val firstSeenAt = if (sameRelease) prefs.getLong(KeyFirstSeenAt, now) else now
    val posted = if (sameRelease) prefs.getInt(KeyPosted, 0) else 0

    val due = ReminderOffsets.count { now >= firstSeenAt + it }
    if (due <= posted) {
        if (!sameRelease) {
            prefs.edit().putString(KeyRelease, releaseKey).putLong(KeyFirstSeenAt, firstSeenAt).putInt(KeyPosted, posted).apply()
        }
        return
    }

    // only count it as sent if it actually was (no permission = try again next launch)
    if (!postNotification(context, release)) return

    prefs.edit()
        .putString(KeyRelease, releaseKey)
        .putLong(KeyFirstSeenAt, firstSeenAt)
        // jump to however many are due, so missed reminders don't fire one per launch
        .putInt(KeyPosted, due)
        .apply()
}

private fun postNotification(context: Context, release: GithubRelease): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }

    val intent = Intent(context, MainActivity::class.java)
        .setAction(ActionOpenUpdate)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val pending = PendingIntent.getActivity(
        context,
        NotificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val summary = changelogSummaryLine(release.body).ifBlank { "Tap to see what's new." }
    val notification = NotificationCompat.Builder(context, ChannelId)
        .setSmallIcon(R.drawable.mochy_notification)
        .setContentTitle("Mochy ${release.version} is out")
        .setContentText(summary)
        .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
        .setContentIntent(pending)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .build()

    return runCatching { NotificationManagerCompat.from(context).notify(NotificationId, notification) }.isSuccess
}

/** opens the version sheet each time requests goes up (MainActivity bumps it when launched from the notification), place inside PopupSheetHost's content */
@Composable
fun UpdateNotificationOpener(requests: Int, onOpenDevSpace: (Int) -> Unit = {}) {
    val popup = LocalZoomOutOverlayState.current
    val context = LocalContext.current
    LaunchedEffect(requests) {
        if (requests == 0) return@LaunchedEffect
        val release = getLatestRelease(updateChannel(context)).getOrNull()
        val state = if (release != null && isNewerThanInstalled(release.version)) {
            UpdateState.Available(release)
        } else {
            UpdateState.UpToDate
        }
        popup.showUpdateSheet(state, onOpenDevSpace = { index ->
            popup.dismissAll()
            onOpenDevSpace(index)
        })
    }
}
