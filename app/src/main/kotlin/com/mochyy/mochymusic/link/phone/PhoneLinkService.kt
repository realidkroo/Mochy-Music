// PhoneLinkService.kt

package com.mochyy.mochymusic.link.phone

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mochyy.mochymusic.MainActivity
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.link.isTelevision
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.launch

// keeps the phone reachable for its paired tvs while mochy is closed, with a quiet notification saying so
@AndroidEntryPoint
class PhoneLinkService : Service() {
    @Inject
    lateinit var host: PhoneLinkHost

    // keeps the ongoing notification saying what's true now: which tv is open, and whether it's streaming from here
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStop || host.tvs.value.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NotificationId,
                notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        }
        if (started.isFailure) {
            Timber.tag("Link").w(started.exceptionOrNull(), "couldn't stay in the background")
            stopSelf()
            return START_NOT_STICKY
        }
        host.refresh()
        watchState()
        return START_STICKY
    }

    private fun watchState() {
        if (watching) return
        watching = true
        scope.launch {
            kotlinx.coroutines.flow.combine(host.tvs, host.live, host.lastStream, ticks()) { _, _, _, _ -> notification() }
                .collect { NotificationManagerCompat.from(this@PhoneLinkService).runCatching { notify(NotificationId, it) } }
        }
    }

    // a tv that goes quiet drops back to "linked" without anything else changing, so the line is checked now and then
    private fun ticks() = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(30_000)
        }
    }

    private fun notification(): android.app.Notification {
        val manager = NotificationManagerCompat.from(this)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(ChannelId, NotificationManagerCompat.IMPORTANCE_MIN)
                .setName(getString(R.string.tv_link_channel))
                .setShowBadge(false)
                .build(),
        )
        val now = System.currentTimeMillis()
        val open_ = host.liveTvs(now)
        val streaming = open_.filter { tv -> host.lastStream.value[tv.id]?.let { now - it < StreamingForMs } == true }
        val names = host.tvs.value.joinToString { it.name }
        // says what's really going on: streaming to a tv, a tv open and in step, or only linked and waiting
        val (title, text) = when {
            streaming.isNotEmpty() -> getString(R.string.tv_link_status_streaming, streaming.joinToString { it.name }) to getString(R.string.tv_link_status_streaming_detail)
            open_.isNotEmpty() -> getString(R.string.tv_link_status_connected, open_.joinToString { it.name }) to getString(R.string.tv_link_status_connected_detail)
            else -> getString(R.string.tv_link_notification_title) to getString(R.string.tv_link_notification_text, names)
        }
        val open = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setAction(ActionOpenTvLink),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.mochy_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val ChannelId = "tv_link"
        private const val NotificationId = 4_711
        private const val ActionStop = "com.mochyy.mochymusic.link.STOP"
        const val ActionOpenTvLink = "com.mochyy.mochymusic.action.TV_LINK"

        // a tv that fetched a song from here this recently counts as streaming
        private const val StreamingForMs = 3 * 60_000L

        // android 12+ refuses this from the background, the link then only runs while mochy itself is up
        fun start(context: Context) {
            if (context.isTelevision()) return
            if (!PhoneLinkHost.hasPairedTvs(context) || !PhoneLinkHost.staysReachable(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, PhoneLinkService::class.java)) }
                .onFailure { Timber.tag("Link").i("background link not started: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PhoneLinkService::class.java)) }
        }
    }
}

// a phone that restarts comes back on for its tv without opening mochy first
class PhoneLinkBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        PhoneLinkService.start(context)
    }
}
