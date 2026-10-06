package com.pitwatch.app.live

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Duration
import java.time.Instant

/**
 * Wakes the CPU for the next poll while tracking. A foreground service doesn't keep the phone awake, so with
 * the screen off the live loop's own timers stop; this exact alarm brings it back on schedule.
 *
 * Deep Doze (stationary, unplugged) still throttles allow-while-idle alarms to roughly one per 9 minutes.
 */
object LiveWakeAlarm {
    private const val REQUEST_CODE = 43

    /** Floor between wakes: a 30 s cadence becomes 1 min while asleep; slower cadences wake on schedule. */
    val MIN_INTERVAL: Duration = Duration.ofMinutes(1)

    fun arm(context: Context, nextPollAt: Instant, now: Instant) {
        val at = maxOf(nextPollAt, now.plus(MIN_INTERVAL))
        alarms(context).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending(context))
    }

    fun cancel(context: Context) = alarms(context).cancel(pending(context))

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, LiveWakeReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Forwards a [LiveWakeAlarm] to the service, which polls if one is due (and otherwise just re-renders). */
class LiveWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            // A plain start is fine: the service is already foreground, and exact alarms allow background starts.
            context.startService(LiveMatchService.intent(context, LiveMatchService.ACTION_WAKE))
        } catch (e: IllegalStateException) {
            // Background start refused (no longer tracking): nothing to wake.
        }
    }
}
