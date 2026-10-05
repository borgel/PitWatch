package com.pitwatch.app.live

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pitwatch.app.MainActivity
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.schedule.rearmAutoStart
import com.pitwatch.core.logic.MatchSchedule
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the Live Update while tracking a match: polls on [PollCadence], re-renders at least every minute
 * (chip countdown), and follows [LiveLifecycle]. Unlike iOS, the cadence is ours — not the OS's.
 */
class LiveMatchService : Service() {
    private val container get() = (application as PitWatchApp).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pokes = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null
    private var tracked: String? = null
    private var resultSeenAt: Instant? = null
    private var lastSuccess: Instant? = null
    private var lastTbaPoll: Instant? = null
    private var failures = 0
    private var triggersRegistered = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            pokes.trySend(Unit)
        }
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            pokes.trySend(Unit)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking(suppress = true)
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> pokes.trySend(Unit)
        }
        // A null intent is a START_STICKY restart after process death: resume tracking.
        val now = container.clock()
        ServiceCompat.startForeground(
            this, LiveNotification.NOTIFICATION_ID,
            LiveNotification.build(this, null, lastSuccess, now, actions()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (loop == null) {
            registerTriggers()
            loop = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        unregisterTriggers()
        super.onDestroy()
    }

    private suspend fun runLoop() {
        var nextPollAt = Instant.MIN
        while (currentCoroutineContext().isActive) {
            val now = container.clock()
            if (now >= nextPollAt) {
                val includeTba = PollCadence.isTbaDue(lastTbaPoll, now)
                val outcome = container.repository.refresh(now, includeTba = includeTba)
                if (outcome.error == null && outcome.nexusError == null) {
                    failures = 0
                    lastSuccess = now
                    if (includeTba) lastTbaPoll = now
                } else {
                    failures++
                }
                val cache = container.repository.cache.first()
                val config = container.stores.config.data.first()
                when (val decision = LiveLifecycle.decide(cache, config, tracked, resultSeenAt, now)) {
                    LiveDecision.Stop -> {
                        stopTracking(suppress = false)
                        return
                    }
                    is LiveDecision.Track -> if (decision.matchKey != tracked) {
                        tracked = decision.matchKey
                        resultSeenAt = null
                    }
                }
                nextPollAt = now.plus(PollCadence.nextDelay(cache, config, tracked, now, failures))
            }
            render(now)
            val untilPoll = Duration.between(container.clock(), nextPollAt)
            val wait = minOf(untilPoll, RENDER_TICK).coerceAtLeast(Duration.ZERO)
            if (withTimeoutOrNull(wait.toMillis()) { pokes.receive() } != null) nextPollAt = Instant.MIN
        }
    }

    private suspend fun render(now: Instant) {
        val cache = container.repository.cache.first()
        val config = container.stores.config.data.first()
        val snapshot = tracked?.let { LiveSnapshots.build(cache, config, it, now) }
        if (snapshot?.result != null && resultSeenAt == null) resultSeenAt = now
        // Without the permission the notification is hidden but tracking (and the FGS) continue.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this)
                .notify(LiveNotification.NOTIFICATION_ID, LiveNotification.build(this, snapshot, lastSuccess, now, actions()))
        }
    }

    private fun stopTracking(suppress: Boolean) {
        val key = tracked
        loop?.cancel()
        loop = null
        val container = container
        val appContext = applicationContext
        container.scope.launch {
            if (suppress) {
                val target = key ?: MatchSchedule(
                    container.stores.cache.data.first().matches,
                    container.stores.config.data.first().teamKey.orEmpty(),
                ).nextMatch?.key
                container.stores.liveControl.updateData { it.copy(suppressedMatchKey = target) }
            }
            rearmAutoStart(appContext, container)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerTriggers() {
        if (triggersRegistered) return
        ContextCompat.registerReceiver(this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
        triggersRegistered = true
    }

    private fun unregisterTriggers() {
        if (!triggersRegistered) return
        unregisterReceiver(unlockReceiver)
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        triggersRegistered = false
    }

    private fun actions() = LiveNotification.Actions(
        content = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        refresh = PendingIntent.getService(this, 1, intent(this, ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE),
        stop = PendingIntent.getService(this, 2, intent(this, ACTION_STOP), PendingIntent.FLAG_IMMUTABLE),
    )

    companion object {
        const val ACTION_START = "com.pitwatch.app.live.START"
        const val ACTION_REFRESH = "com.pitwatch.app.live.REFRESH"
        const val ACTION_STOP = "com.pitwatch.app.live.STOP"
        private val RENDER_TICK: Duration = Duration.ofSeconds(60)

        fun intent(context: Context, action: String): Intent = Intent(context, LiveMatchService::class.java).setAction(action)

        fun start(context: Context) = ContextCompat.startForegroundService(context, intent(context, ACTION_START))
    }
}
