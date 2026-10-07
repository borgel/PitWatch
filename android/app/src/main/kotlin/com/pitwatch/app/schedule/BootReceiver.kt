package com.pitwatch.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.notify.ScheduleNotifier
import kotlinx.coroutines.launch

/** Exact alarms are cleared on reboot: re-arm auto-start, queue the refresh worker, and restore the schedule notification. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending: PendingResult? = goAsync() // null when invoked directly (tests)
        val container = (context.applicationContext as PitWatchApp).container
        container.scope.launch {
            try {
                rearmAutoStart(context, container)
                RefreshWorker.ensureScheduled(context)
                ScheduleNotifier.update(context, container)
            } finally {
                pending?.finish()
            }
        }
    }
}
