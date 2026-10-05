package com.pitwatch.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.live.LiveMatchService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Fired by [AutoStartAlarm]: start tracking if we're inside the window, otherwise re-arm. */
class AutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending: PendingResult? = goAsync() // null when invoked directly (tests)
        val container = (context.applicationContext as PitWatchApp).container
        container.scope.launch {
            try {
                val now = container.clock()
                val start = AutoStartPlanner.nextStart(
                    container.stores.cache.data.first(),
                    container.stores.config.data.first(),
                    container.stores.liveControl.data.first(),
                    now,
                )
                if (start != null && start <= now) LiveMatchService.start(context) else AutoStartAlarm.arm(context, start)
            } finally {
                pending?.finish()
            }
        }
    }
}
