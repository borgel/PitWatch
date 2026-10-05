package com.pitwatch.app.schedule

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pitwatch.app.PitWatchApp
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.MatchSchedule
import com.pitwatch.core.store.EventCache
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first

/** Background refresh on the adaptive schedule; re-enqueues itself and re-arms auto-start every run. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PitWatchApp).container
        val now = container.clock()
        container.repository.refresh(now)
        val cache = container.stores.cache.data.first()
        val config = container.stores.config.data.first()
        // APPEND: replacing would cancel this very run.
        enqueue(applicationContext, nextDelay(cache, config, now), ExistingWorkPolicy.APPEND_OR_REPLACE)
        rearmAutoStart(applicationContext, container)
        return Result.success() // failures are recorded in RefreshState; the next run is already queued
    }

    companion object {
        const val UNIQUE_NAME = "pitwatch-refresh"
        /** WorkManager's own floor for background work. */
        val MIN_DELAY: Duration = Duration.ofMinutes(15)

        fun nextDelay(cache: EventCache, config: UserConfig, now: Instant): Duration {
            val nexus = cache.nexusEvent.takeIf { config.effectiveTimeSource == TimeSource.NEXUS }
            val interval = MatchSchedule(cache.matches, config.teamKey.orEmpty()).refreshInterval(now, config.useScheduledTime, nexus)
            return maxOf(MIN_DELAY, interval)
        }

        fun enqueue(context: Context, delay: Duration, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInitialDelay(delay)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request)
        }

        /** App launch / after setup: refresh now unless a run is already scheduled. */
        fun ensureScheduled(context: Context) = enqueue(context, Duration.ZERO, ExistingWorkPolicy.KEEP)

        /** After setup or a key/event change: run now, replacing any far-off run planned from stale state. */
        fun refreshNow(context: Context) = enqueue(context, Duration.ZERO, ExistingWorkPolicy.REPLACE)
    }
}
