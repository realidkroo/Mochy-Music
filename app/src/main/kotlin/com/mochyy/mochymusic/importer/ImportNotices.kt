// ImportNotices.kt

package com.mochyy.mochymusic.importer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mochyy.mochymusic.R

/** how a finished import or re-sync tells the user, wherever they are in the app by then */
internal object ImportNotices {

    fun importFinished(context: Context, matched: Int, total: Int) {
        val channelId = "music_import"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, context.getString(R.string.import_notice_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.mochy_notification)
            .setContentTitle(context.getString(R.string.import_notice_finished))
            .setContentText(context.getString(R.string.import_notice_matched, matched, total))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching { notificationManager.notify(4242, notification) }
    }

    /** what a finished re-sync changed, said the way a person would */
    fun resyncFinished(context: Context, done: ImportProgress) {
        val resync = done.resync ?: return
        toast(context, if (resync.playlists == 1) oneResynced(context, done, resync) else manyResynced(context, done, resync))
    }

    private fun oneResynced(context: Context, done: ImportProgress, resync: ResyncSummary): String {
        if (resync.failed.isNotEmpty()) return context.getString(
            R.string.import_notice_resync_failed, resync.playlistName, done.error.orEmpty()
        ).trim()
        val missing = done.unmatched.size
        val result = when {
            resync.added > 0 && missing > 0 ->
                context.resources.getQuantityString(R.plurals.import_notice_added_missing, resync.added, resync.added, resync.playlistName, missing)
            resync.added > 0 -> context.resources.getQuantityString(R.plurals.import_notice_added, resync.added, resync.added, resync.playlistName)
            missing > 0 -> context.resources.getQuantityString(R.plurals.import_notice_missing, missing, missing)
            else -> context.getString(R.string.import_notice_up_to_date, resync.playlistName)
        }
        return listOfNotNull(result, resync.note).joinToString(". ")
    }

    private fun manyResynced(context: Context, done: ImportProgress, resync: ResyncSummary): String {
        val read = resync.playlists - resync.failed.size
        val result = when {
            resync.added > 0 -> context.getString(R.string.import_notice_many_added, resync.added, resync.playlistsChanged)
            done.unmatched.isNotEmpty() -> context.resources.getQuantityString(R.plurals.import_notice_missing, done.unmatched.size, done.unmatched.size)
            read > 0 -> context.resources.getQuantityString(R.plurals.import_notice_many_current, read, read)
            else -> null
        }
        val failed = resync.failed.takeIf { it.isNotEmpty() }?.let {
            context.getString(R.string.import_notice_many_failed, it.joinToString(", "))
        }
        return listOfNotNull(result, failed).joinToString(". ")
    }

    /** shown over whatever the user is on now: re-sync carries on after its menu has closed */
    fun toast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

}
