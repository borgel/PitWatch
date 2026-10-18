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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The rich live notification (the default): a short Live Update plus the colorized scoreboard card. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "night")
class RichLiveServiceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer(tmp.root, nexus = com.pitwatch.app.snapshotNexus())
        runBlocking { container.stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") } }
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() = container.scope.cancel()

    private fun start(): LiveMatchService {
        val service = Robolectric.buildService(LiveMatchService::class.java).create().get()
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_START), 0, 1)
        return service
    }

    private fun liveUpdate(): Notification? = shadowOf(manager).getNotification(LiveNotification.NOTIFICATION_ID)
    private fun card(): Notification? = shadowOf(manager).getNotification(LiveCard.NOTIFICATION_ID)

    @Test
    fun `the scoreboard card is the colorized foreground notification, beside a short Live Update`() {
        val service = start()
        awaitMain { card() != null && liveUpdate()?.channelId == LiveNotification.COMPACT_CHANNEL_ID }
        assertEquals(LiveCard.NOTIFICATION_ID, shadowOf(service).lastForegroundNotificationId)
        assertTrue(card()!!.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals(LiveCard.colors(context, dark = true).background, card()!!.color)
        assertEquals("Q36 · Red · Now", liveUpdate()!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(liveUpdate()!!.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    @Test
    fun `switching to light mode redraws the card in light colors`() {
        val service = start()
        awaitMain { card() != null }
        RuntimeEnvironment.setQualifiers("notnight")
        service.onConfigurationChanged(context.resources.configuration)
        awaitMain { card()?.color == LiveCard.colors(context, dark = false).background }
    }

    @Test
    fun `turning rich off while tracking goes back to the single Live Update`() {
        val service = start()
        awaitMain { card() != null }
        runBlocking { container.stores.notificationPrefs.updateData { it.copy(richLive = false) } }
        awaitMain { card() == null && liveUpdate()?.channelId == LiveNotification.CHANNEL_ID }
        assertEquals(LiveNotification.NOTIFICATION_ID, shadowOf(service).lastForegroundNotificationId)
        assertTrue(liveUpdate()!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("Q36 · RED · ON FIELD"))
    }

    @Test
    fun `stop removes both`() {
        val service = start()
        awaitMain { card() != null && liveUpdate() != null }
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_STOP), 0, 2)
        awaitMain { shadowOf(service).isStoppedBySelf }
        assertNull(card())
        assertNull(liveUpdate())
    }

    @Test
    fun `a redraw still in flight when tracking stops posts nothing afterwards`() {
        // Final review: a theme-change redraw that resumed after Stop re-posted both, and nothing removed them.
        val service = start()
        awaitMain { card() != null && liveUpdate() != null }
        RuntimeEnvironment.setQualifiers("notnight")
        service.onConfigurationChanged(context.resources.configuration)
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_STOP), 0, 2)
        awaitMain { shadowOf(service).isStoppedBySelf }
        val until = System.currentTimeMillis() + 1_500
        while (System.currentTimeMillis() < until) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertNull(card())
        assertNull(liveUpdate())
    }

    @Test
    fun `the card alerts once per tracking session, not again when it's re-posted`() {
        // Final review: a pinned swipe (or coming back from "Waiting") re-posted the card with its sound.
        runBlocking { container.stores.notificationPrefs.updateData { it.copy(pinned = true) } }
        val service = start()
        awaitMain { card() != null }
        kotlin.test.assertNotEquals(Notification.GROUP_ALERT_SUMMARY, card()!!.groupAlertBehavior, "the first one alerts")
        manager.cancel(LiveCard.NOTIFICATION_ID) // the swipe
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_DISMISSED), 0, 2)
        awaitMain { card() != null }
        assertEquals(Notification.GROUP_ALERT_SUMMARY, card()!!.groupAlertBehavior, "re-posted silently")
        assertEquals(LiveCard.NOTIFICATION_ID, shadowOf(service).lastForegroundNotificationId)
    }

    @Test
    fun `pinned - swiping either away brings both back`() {
        runBlocking { container.stores.notificationPrefs.updateData { it.copy(pinned = true) } }
        val service = start()
        awaitMain { card() != null && liveUpdate() != null }
        manager.cancel(LiveNotification.NOTIFICATION_ID) // the swipe
        manager.cancel(LiveCard.NOTIFICATION_ID)
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_DISMISSED), 0, 2)
        awaitMain { card() != null && liveUpdate() != null }
        assertTrue(LiveMatchService.tracking.value)
    }
}
