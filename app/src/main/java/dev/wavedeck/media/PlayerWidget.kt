package dev.wavedeck.media

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.*
import dev.wavedeck.MainActivity
import dev.wavedeck.R

class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, context.getString(R.string.no_track), false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in listOf("dev.wavedeck.TOGGLE", "dev.wavedeck.NEXT")) return
        val pending = goAsync()
        val future =
            MediaController.Builder(
                    context,
                    SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                )
                .buildAsync()
        future.addListener(
            {
                runCatching {
                    val c = future.get()
                    if (intent.action == "dev.wavedeck.NEXT") c.seekToNextMediaItem()
                    else if (c.isPlaying) c.pause() else c.play()
                }
                Handler(Looper.getMainLooper())
                    .postDelayed(
                        {
                            MediaController.releaseFuture(future)
                            pending.finish()
                        },
                        1000,
                    )
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    companion object {
        fun update(context: Context, title: String, playing: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.player_widget)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(
                R.id.widget_toggle,
                context.getString(if (playing) R.string.pause else R.string.play),
            )
            views.setOnClickPendingIntent(
                R.id.widget_title,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            for ((id, action) in
                listOf(
                    R.id.widget_toggle to "dev.wavedeck.TOGGLE",
                    R.id.widget_next to "dev.wavedeck.NEXT",
                )) views.setOnClickPendingIntent(
                id,
                PendingIntent.getBroadcast(
                    context,
                    id,
                    Intent(context, PlayerWidget::class.java).setAction(action),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            manager.updateAppWidget(ids, views)
        }
    }
}
