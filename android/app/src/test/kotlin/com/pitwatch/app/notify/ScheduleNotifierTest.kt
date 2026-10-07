package com.pitwatch.app.notify

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.AppContainer
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.installTestContainer
import com.pitwatch.core.config.UserConfig
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ScheduleNotifierTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        container = installTestContainer(tmp.root)
        ScheduleNotification.ensureChannel(context)
        runBlocking {
            container.stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") }
            container.repository.refresh(SNAP_NOW)
        }
    }

    @After
    fun tearDown() = container.scope.cancel()

    private fun posted(): Notification? =
        shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(ScheduleNotification.NOTIFICATION_ID)

    @Test
    fun `off by default - nothing posted`() = runBlocking<Unit> {
        ScheduleNotifier.update(context, container)
        assertNull(posted())
    }

    @Test
    fun `enabled - posts a silent ongoing schedule on its channel`() = runBlocking<Unit> {
        ScheduleNotifier.setEnabled(context, container, true)
        val n = assertNotNull(posted())
        assertEquals(ScheduleNotification.CHANNEL_ID, n.channelId)
        assertEquals(true, n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(false, n.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        // Its own group: otherwise Android bundles it with the live notification (seen on-device).
        assertEquals(ScheduleNotification.GROUP, n.group)
    }

    @Test
    fun `turn off action disables and cancels`() = runBlocking<Unit> {
        ScheduleNotifier.setEnabled(context, container, true)
        ScheduleNotificationReceiver().handle(context, container, ScheduleNotifier.ACTION_TURN_OFF)
        assertNull(posted())
        assertFalse(container.stores.notificationPrefs.data.first().scheduleEnabled)
    }

    @Test
    fun `swiped away, not pinned - stays off`() = runBlocking<Unit> {
        ScheduleNotifier.setEnabled(context, container, true)
        ScheduleNotificationReceiver().handle(context, container, ScheduleNotifier.ACTION_DISMISSED)
        assertFalse(container.stores.notificationPrefs.data.first().scheduleEnabled)
        assertNull(posted())
    }

    @Test
    fun `swiped away, pinned - comes straight back`() = runBlocking<Unit> {
        container.stores.notificationPrefs.updateData { it.copy(pinned = true) }
        ScheduleNotifier.setEnabled(context, container, true)
        context.getSystemService(NotificationManager::class.java).cancel(ScheduleNotification.NOTIFICATION_ID) // the swipe
        ScheduleNotificationReceiver().handle(context, container, ScheduleNotifier.ACTION_DISMISSED)
        assertNotNull(posted())
    }

    @Test
    fun `without notification permission nothing is posted`() = runBlocking<Unit> {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ScheduleNotifier.setEnabled(context, container, true)
        assertNull(posted())
    }
}
