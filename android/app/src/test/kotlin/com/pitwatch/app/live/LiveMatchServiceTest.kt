package com.pitwatch.app.live

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.AppContainer
import com.pitwatch.app.awaitMain
import com.pitwatch.app.installTestContainer
import com.pitwatch.core.config.UserConfig
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LiveMatchServiceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer(tmp.root)
        runBlocking { container.stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") } }
    }

    @After
    fun tearDown() = container.scope.cancel()

    private fun startService(intentAction: String? = LiveMatchService.ACTION_START): LiveMatchService {
        val controller = Robolectric.buildService(LiveMatchService::class.java).create()
        val service = controller.get()
        service.onStartCommand(intentAction?.let { LiveMatchService.intent(context, it) }, 0, 1)
        return service
    }

    private fun posted(): Notification? =
        shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(LiveNotification.NOTIFICATION_ID)

    @Test
    fun `start goes foreground with a promoted notification`() {
        val service = startService()
        val foreground = assertNotNull(shadowOf(service).lastForegroundNotification)
        assertTrue(foreground.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    @Test
    fun `polls and shows the tracked match`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        startService()
        awaitMain { posted()?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.startsWith("Q36") == true }
        assertEquals("Q36 · RED · ON FIELD", posted()!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun `restart after process death resumes tracking`() {
        // START_STICKY redelivers a null intent.
        val service = startService(intentAction = null)
        assertNotNull(shadowOf(service).lastForegroundNotification)
    }

    @Test
    fun `runs without notification permission`() {
        // POST_NOTIFICATIONS deliberately not granted.
        val service = startService()
        awaitMain { runBlocking { container.stores.refreshState.data.first().lastRefreshEpochMs != null } }
        assertNotNull(shadowOf(service).lastForegroundNotification)
    }

    @Test
    fun `stop suppresses the next match and stops the service`() {
        // Stop arrives before the first poll finishes, so the service suppresses the schedule's next match.
        runBlocking { container.repository.refresh(com.pitwatch.app.SNAP_NOW) }
        val service = startService()
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_STOP), 0, 2)
        assertTrue(shadowOf(service).isStoppedBySelf)
        val suppressed = runBlocking {
            withTimeout(5_000) { container.stores.liveControl.data.first { it.suppressedMatchKey != null } }
        }
        assertEquals("2026cancmp_qm36", suppressed.suppressedMatchKey)
    }
}
