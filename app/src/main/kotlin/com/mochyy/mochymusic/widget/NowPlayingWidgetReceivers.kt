// NowPlayingWidgetReceivers.kt

package com.mochyy.mochymusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.mochyy.mochymusic.MainActivity
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.playback.MusicService

// the three now playing widgets, each its own receiver so the launcher lists them apart; they share the other widgets' actions
abstract class NowPlayingWidgetReceiver : AppWidgetProvider() {

    protected abstract val layoutRes: Int

    // the "nothing is playing yet" text, shown before the service has drawn anything
    protected abstract fun bindIdle(context: Context, views: RemoteViews)

    // this widget's buttons as (view id, action) pairs
    protected abstract val buttons: List<Pair<Int, String>>

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (MusicService.isRunning) {
            // the service redraws every placed widget, this one included
            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicWidgetReceiver.ACTION_UPDATE_WIDGET
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {

            }
        } else {
            // the app isn't running: show a tappable card instead of the preview text
            val views = RemoteViews(context.packageName, layoutRes)
            bindIdle(context, views)
            views.setOnClickPendingIntent(android.R.id.background, NowPlayingWidgetIntents.openApp(context))
            buttons.forEachIndexed { index, (viewId, action) ->
                views.setOnClickPendingIntent(
                    viewId,
                    NowPlayingWidgetIntents.broadcast(context, this::class.java, action, index)
                )
            }
            appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
        }
    }

    // resizing changes how much room the lyrics have (a short 2x2 drops the next line), so the service redraws
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        if (!MusicService.isRunning) return
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicWidgetReceiver.ACTION_UPDATE_WIDGET
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {

        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            MusicWidgetReceiver.ACTION_PLAY_PAUSE,
            MusicWidgetReceiver.ACTION_NEXT,
            MusicWidgetReceiver.ACTION_PREVIOUS,
            MusicWidgetReceiver.ACTION_LIKE,
            MusicWidgetReceiver.ACTION_REPEAT -> {
                val serviceIntent = Intent(context, MusicService::class.java).apply {
                    action = intent.action
                    putExtras(intent)
                }
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {

                }
            }
        }
    }
}

class NowPlaying2x2WidgetReceiver : NowPlayingWidgetReceiver() {
    override val layoutRes = R.layout.widget_np_2x2

    override val buttons = listOf(
        R.id.widget_np_prev to MusicWidgetReceiver.ACTION_PREVIOUS,
        R.id.widget_np_play to MusicWidgetReceiver.ACTION_PLAY_PAUSE,
        R.id.widget_np_next to MusicWidgetReceiver.ACTION_NEXT,
    )

    override fun bindIdle(context: Context, views: RemoteViews) {
        views.setTextViewText(R.id.widget_np_lyric_a, context.getString(R.string.not_playing))
    }
}

class NowPlaying4x1WidgetReceiver : NowPlayingWidgetReceiver() {
    override val layoutRes = R.layout.widget_np_4x1

    override val buttons = listOf(
        R.id.widget_np_like to MusicWidgetReceiver.ACTION_LIKE,
        R.id.widget_np_prev to MusicWidgetReceiver.ACTION_PREVIOUS,
        R.id.widget_np_play to MusicWidgetReceiver.ACTION_PLAY_PAUSE,
        R.id.widget_np_next to MusicWidgetReceiver.ACTION_NEXT,
        R.id.widget_np_repeat to MusicWidgetReceiver.ACTION_REPEAT,
    )

    override fun bindIdle(context: Context, views: RemoteViews) {
        views.setTextViewText(R.id.widget_np_title, context.getString(R.string.not_playing))
    }
}

class NowPlaying4x2WidgetReceiver : NowPlayingWidgetReceiver() {
    override val layoutRes = R.layout.widget_np_4x2

    override val buttons = listOf(
        R.id.widget_np_like to MusicWidgetReceiver.ACTION_LIKE,
        R.id.widget_np_prev to MusicWidgetReceiver.ACTION_PREVIOUS,
        R.id.widget_np_play to MusicWidgetReceiver.ACTION_PLAY_PAUSE,
        R.id.widget_np_next to MusicWidgetReceiver.ACTION_NEXT,
    )

    override fun bindIdle(context: Context, views: RemoteViews) {
        views.setTextViewText(R.id.widget_np_lyric_a, context.getString(R.string.not_playing))
        views.setTextViewText(R.id.widget_np_next_a, context.getString(R.string.tap_to_play))
    }
}

// the pending intents the now playing widgets share, whether the receiver or the manager draws them
object NowPlayingWidgetIntents {

    fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // slot keeps the request codes of one widget's buttons apart
    fun broadcast(context: Context, receiver: Class<*>, action: String, slot: Int): PendingIntent {
        val intent = Intent(context, receiver).apply { this.action = action }
        return PendingIntent.getBroadcast(
            context,
            100 + slot,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
