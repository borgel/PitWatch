package com.pitwatch.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pitwatch.app.AppContainer
import java.time.Instant
import kotlinx.coroutines.flow.first

object AutoStartAlarm {
    private const val REQUEST_CODE = 42

    /** Arms (or with null, cancels) the exact alarm that starts live tracking. */
    fun arm(context: Context, at: Instant?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, Intent(context, AutoStartReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) {
            alarms.cancel(pending)
        } else {
            // USE_EXACT_ALARM (sideloaded) makes exact alarms available, and exact alarms may start an FGS.
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        }
    }
}

/** Re-plans the auto-start alarm from current state. Call after every refresh and whenever tracking stops. */
suspend fun rearmAutoStart(context: Context, container: AppContainer) {
    val at = AutoStartPlanner.nextStart(
        container.stores.cache.data.first(),
        container.stores.config.data.first(),
        container.stores.liveControl.data.first(),
        container.clock(),
    )
    AutoStartAlarm.arm(context, at)
}
