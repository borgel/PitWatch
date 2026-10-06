package com.pitwatch.app.live

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.AppContainer
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.awaitMain
import com.pitwatch.app.installTestContainer
import com.pitwatch.app.snapshotNexus
import com.pitwatch.core.config.UserConfig
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowPowerManager

/** Screen-off behavior: the loop's timers pause while the CPU sleeps, so an exact alarm wakes each poll. */
@RunWith(RobolectricTestRunner::class)
class LiveWakeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nexus = snapshotNexus()
    private var now: Instant = SNAP_NOW
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer(tmp.root, nexus = nexus, clock = { now })
        runBlocking { container.stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") } }
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() = container.scope.cancel()

    private fun startTracking(): LiveMatchService {
        val service = Robolectric.buildService(LiveMatchService::class.java).create().get()
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_START), 0, 1)
        awaitMain { posted()?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.startsWith("Q36") == true }
        return service
    }

    private fun posted(): Notification? =
        shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(LiveNotification.NOTIFICATION_ID)

    // Robolectric 4.17 exposes the alarm's PendingIntent only via the deprecated `operation` field.
    @Suppress("DEPRECATION")
    private fun wakeAlarm() = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.firstOrNull {
        shadowOf(it.operation).savedIntent.component?.className == LiveWakeReceiver::class.java.name
    }

    private fun idle() = repeat(20) {
        ShadowLooper.idleMainLooper()
        Thread.sleep(10)
    }

    @Test
    fun `tracking arms a wake alarm no sooner than a minute out`() {
        startTracking()
        // qm36 is about to start, so the cadence is FAST (30 s); the alarm floor is 1 minute.
        assertEquals(SNAP_NOW.plus(LiveWakeAlarm.MIN_INTERVAL).toEpochMilli(), assertNotNull(wakeAlarm()).triggerAtMs)
    }

    @Test
    fun `stopping cancels the wake alarm`() {
        val service = startTracking()
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_STOP), 0, 2)
        assertNull(wakeAlarm())
    }

    @Test
    fun `wake when a poll is due polls`() {
        val service = startTracking()
        val before = nexus.requests.size
        now = now.plusSeconds(61) // the phone slept through the 30 s poll
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_WAKE), 0, 2)
        awaitMain { nexus.requests.size > before }
    }

    @Test
    fun `wake before a poll is due does not add a request`() {
        val service = startTracking()
        idle() // let any start-up pokes (e.g. the network callback) settle first
        val before = nexus.requests.size
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_WAKE), 0, 2)
        idle()
        assertEquals(before, nexus.requests.size)
    }

    @Test
    fun `wake for a service that is not tracking stops without going foreground`() {
        val service = Robolectric.buildService(LiveMatchService::class.java).create().get()
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_WAKE), 0, 1)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertNull(shadowOf(service).lastForegroundNotification)
    }

    @Test
    fun `wake lock covers the poll and is released afterwards`() {
        startTracking()
        val lock = assertNotNull(ShadowPowerManager.getLatestWakeLock())
        idle()
        assertFalse(lock.isHeld)
    }

    @Test
    fun `wake receiver forwards to the service`() {
        LiveWakeReceiver().onReceive(context, Intent(context, LiveWakeReceiver::class.java))
        assertEquals(LiveMatchService.ACTION_WAKE, shadowOf(context as Application).nextStartedService.action)
    }
}
