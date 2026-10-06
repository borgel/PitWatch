package com.pitwatch.app.live

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pitwatch.app.MainActivity
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.schedule.AutoStartPlanner
import com.pitwatch.app.schedule.rearmAutoStart
import com.pitwatch.core.logic.MatchSchedule
import kotlin.coroutines.cancellation.CancellationException
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
    /** POKE forces a poll now (refresh, unlock, network); WAKE only re-checks the schedule (screen-off alarm). */
    private enum class Signal { POKE, WAKE }
    private val signals = Channel<Signal>(Channel.CONFLATED)
    /** Held only while a poll/render runs, so the phone can't fall back asleep mid-request after a wake. */
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PitWatch:livePoll")
            .apply { setReferenceCounted(false) }
    }
    private var loop: Job? = null
    private var tracked: String? = null
    private var resultSeenAt: Instant? = null
    private var lastSuccess: Instant? = null
    private var lastTbaPoll: Instant? = null
    private var failures = 0
    private var triggersRegistered = false
    /** Newest start command; stopping with it can't race a startForegroundService still in flight. */
    private var lastStartId = 0
    /** Last rendered notification, re-used on redundant starts so the live card never blanks. */
    private var lastNotification: Notification? = null
    /** Set for a START_STICKY restart: resume only if we're still inside the live window. */
    private var restarted = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            signals.trySend(Signal.POKE)
        }
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            signals.trySend(Signal.POKE)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking(suppress = true)
                return START_NOT_STICKY
            }
            // Already tracking: a repeat start (alarm, app open, button) or Refresh just polls now.
            ACTION_REFRESH, ACTION_START -> if (loop != null) signals.trySend(Signal.POKE)
            ACTION_WAKE -> {
                if (loop == null) { // not tracking (e.g. a stale alarm): don't resurrect anything
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                wakeLock.acquire(WAKE_LOCK_TIMEOUT.toMillis()) // bridge until the loop takes over
                signals.trySend(Signal.WAKE)
                return START_STICKY
            }
        }
        val notification = lastNotification ?: LiveNotification.build(this, null, lastSuccess, container.clock(), actions())
        try {
            ServiceCompat.startForeground(this, LiveNotification.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // e.g. a sticky restart from the background: give up quietly; the alarm/worker will start us later.
            stopTracking(suppress = false)
            return START_NOT_STICKY
        }
        if (loop == null) {
            restarted = intent == null // START_STICKY redelivers a null intent after process death
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
        if (restarted && !insideLiveWindow()) {
            stopTracking(suppress = false)
            return
        }
        var nextPollAt = Instant.MIN
        while (currentCoroutineContext().isActive) {
            try {
                nextPollAt = iterate(nextPollAt) ?: return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never let one bad iteration kill tracking (or crash-loop the process): back off and retry.
                Log.w(TAG, "Live loop iteration failed", e)
                failures++
                nextPollAt = Instant.MIN
                withTimeoutOrNull(PollCadence.backoff(failures).toMillis()) { signals.receive() }
            }
        }
    }

    /** One poll-and/or-render step. Returns when to poll next, or null once tracking has stopped. */
    private suspend fun iterate(pollAt: Instant): Instant? {
        var nextPollAt = pollAt
        val now = container.clock()
        wakeLock.acquire(WAKE_LOCK_TIMEOUT.toMillis())
        try {
            if (now >= nextPollAt) {
                val includeTba = PollCadence.isTbaDue(lastTbaPoll, now)
                // A Nexus failure only counts when we had Nexus data (a blip), not for events Nexus doesn't cover.
                val hadNexus = container.repository.cache.first().nexusEvent != null
                val outcome = container.repository.refresh(now, includeTba = includeTba)
                if (outcome.error == null && (outcome.nexusError == null || !hadNexus)) {
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
                        return null
                    }
                    is LiveDecision.Track -> if (decision.matchKey != tracked) {
                        tracked = decision.matchKey
                        resultSeenAt = null
                    }
                }
                nextPollAt = now.plus(PollCadence.nextDelay(cache, config, tracked, now, failures))
            }
            render(now)
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
        LiveWakeAlarm.arm(this, nextPollAt, container.clock())
        val untilPoll = Duration.between(container.clock(), nextPollAt)
        val wait = minOf(untilPoll, RENDER_TICK).coerceAtLeast(Duration.ZERO)
        if (withTimeoutOrNull(wait.toMillis()) { signals.receive() } == Signal.POKE) nextPollAt = Instant.MIN
        return nextPollAt
    }

    private suspend fun insideLiveWindow(): Boolean {
        val now = container.clock()
        val start = AutoStartPlanner.nextStart(
            container.repository.cache.first(), container.stores.config.data.first(), container.stores.liveControl.data.first(), now,
        )
        return start != null && start <= now
    }

    private suspend fun render(now: Instant) {
        val cache = container.repository.cache.first()
        val config = container.stores.config.data.first()
        val snapshot = tracked?.let { LiveSnapshots.build(cache, config, it, now) }
        if (snapshot?.result != null && resultSeenAt == null) resultSeenAt = now
        // Without the permission the notification is hidden but tracking (and the FGS) continue.
        val notification = LiveNotification.build(this, snapshot, lastSuccess, now, actions())
        lastNotification = notification
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(LiveNotification.NOTIFICATION_ID, notification)
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
        lastNotification = null
        LiveWakeAlarm.cancel(this)
        if (wakeLock.isHeld) wakeLock.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // stopSelf(startId): if a newer start is already queued, the service stays and that start re-runs tracking.
        stopSelf(lastStartId)
    }

    private fun registerTriggers() {
        if (triggersRegistered) return
        // Screen-on as well as unlock: the loop's timers don't advance while the CPU sleeps, so a glance at the
        // lock screen must refresh it.
        val wake = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, unlockReceiver, wake, ContextCompat.RECEIVER_NOT_EXPORTED)
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
        const val ACTION_WAKE = "com.pitwatch.app.live.WAKE"
        private val WAKE_LOCK_TIMEOUT: Duration = Duration.ofSeconds(60)
        private const val TAG = "LiveMatchService"
        private val RENDER_TICK: Duration = Duration.ofSeconds(60)

        fun intent(context: Context, action: String): Intent = Intent(context, LiveMatchService::class.java).setAction(action)

        fun start(context: Context) = ContextCompat.startForegroundService(context, intent(context, ACTION_START))
    }
}
