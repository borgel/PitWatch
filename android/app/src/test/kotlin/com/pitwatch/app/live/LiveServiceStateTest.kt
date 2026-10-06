package com.pitwatch.app.live

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.AppContainer
import com.pitwatch.app.awaitMain
import com.pitwatch.app.installTestContainer
import com.pitwatch.app.snapshotNexus
import com.pitwatch.core.config.UserConfig
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
import org.robolectric.shadows.ShadowNetwork

@RunWith(RobolectricTestRunner::class)
class LiveServiceStateTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nexus = snapshotNexus()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer(tmp.root, nexus = nexus)
        runBlocking { container.stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") } }
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() = container.scope.cancel()

    private fun startTracking(): LiveMatchService {
        val service = Robolectric.buildService(LiveMatchService::class.java).create().get()
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_START), 0, 1)
        awaitMain {
            shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(LiveNotification.NOTIFICATION_ID)
                ?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.startsWith("Q36") == true
        }
        return service
    }

    private fun idle() = repeat(20) {
        ShadowLooper.idleMainLooper()
        Thread.sleep(10)
    }

    @Test
    fun `tracking state follows the service`() {
        val service = startTracking()
        assertTrue(LiveMatchService.tracking.value)
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_STOP), 0, 2)
        assertFalse(LiveMatchService.tracking.value)
    }

    @Test
    fun `the network callback's immediate first onAvailable does not add a poll`() {
        startTracking()
        idle()
        val callback = shadowOf(context.getSystemService(ConnectivityManager::class.java)).networkCallbacks.single()
        val before = nexus.requests.size
        callback.onAvailable(ShadowNetwork.newInstance(1))
        idle()
        assertEquals(before, nexus.requests.size)
        callback.onAvailable(ShadowNetwork.newInstance(1)) // a real reconnect later does poll
        awaitMain { nexus.requests.size > before }
    }
}
