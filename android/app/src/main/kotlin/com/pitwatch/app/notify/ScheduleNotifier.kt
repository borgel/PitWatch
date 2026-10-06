package com.pitwatch.app.notify

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pitwatch.app.AppContainer
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.widget.ScheduleWidgetModels
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object ScheduleNotifier {
    const val ACTION_TURN_OFF = "com.pitwatch.app.schedule.TURN_OFF"
    const val ACTION_DISMISSED = "com.pitwatch.app.schedule.DISMISSED"

    /** Posts the current schedule when enabled (and permitted); cancels it otherwise. Runs with every widget refresh. */
    suspend fun update(context: Context, container: AppContainer) {
        val manager = NotificationManagerCompat.from(context)
        if (!container.stores.notificationPrefs.data.first().scheduleEnabled) {
            manager.cancel(ScheduleNotification.NOTIFICATION_ID)
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val model = ScheduleWidgetModels.build(container.stores.cache.data.first(), container.stores.config.data.first(), container.clock())
        manager.notify(ScheduleNotification.NOTIFICATION_ID, ScheduleNotification.build(context, ScheduleNotification.content(model)))
    }

    suspend fun setEnabled(context: Context, container: AppContainer, enabled: Boolean) {
        container.stores.notificationPrefs.updateData { it.copy(scheduleEnabled = enabled) }
        update(context, container)
    }
}

/** "Turn off", and swipes: pinned re-posts; otherwise a swipe turns the schedule notification off. */
class ScheduleNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending: PendingResult? = goAsync() // null when invoked directly (tests)
        val container = (context.applicationContext as PitWatchApp).container
        container.scope.launch {
            try {
                handle(context, container, intent.action)
            } finally {
                pending?.finish()
            }
        }
    }

    suspend fun handle(context: Context, container: AppContainer, action: String?) {
        when (action) {
            ScheduleNotifier.ACTION_TURN_OFF -> ScheduleNotifier.setEnabled(context, container, false)
            ScheduleNotifier.ACTION_DISMISSED -> {
                val prefs = container.stores.notificationPrefs.data.first()
                if (prefs.pinned && prefs.scheduleEnabled) {
                    ScheduleNotifier.update(context, container)
                } else {
                    ScheduleNotifier.setEnabled(context, container, false)
                }
            }
        }
    }
}
