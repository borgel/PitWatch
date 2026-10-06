package com.pitwatch.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.pitwatch.app.AppContainer
import com.pitwatch.app.PitWatchApp
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Widgets change with the clock (phase deadlines, match time), not only with data. Every render also arms a
 * one-shot re-render just after the countdown runs out, so the widget never counts negative or shows a past phase.
 */
object WidgetRefresh {
    private const val REQUEST_CODE = 44

    suspend fun run(context: Context, container: AppContainer, render: suspend () -> Unit = { PitWatchWidget().updateAll(context) }) {
        render()
        val model = WidgetModels.build(container.stores.cache.data.first(), container.stores.config.data.first(), container.clock())
        arm(context, model.countdownDeadline?.plusSeconds(1))
    }

    private fun arm(context: Context, at: Instant?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, Intent(context, WidgetRefreshReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Non-wakeup: a home-screen widget only needs to be right when someone looks at it.
        if (at == null) alarms.cancel(pending) else alarms.set(AlarmManager.RTC, at.toEpochMilli(), pending)
    }
}

/** Fired by [WidgetRefresh]'s deadline alarm. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending: PendingResult? = goAsync() // null when invoked directly (tests)
        val container = (context.applicationContext as PitWatchApp).container
        container.scope.launch {
            try {
                container.updateWidgets()
            } finally {
                pending?.finish()
            }
        }
    }
}
