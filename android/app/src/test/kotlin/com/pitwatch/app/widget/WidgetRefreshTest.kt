package com.pitwatch.app.widget

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.AppContainer
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.awaitMain
import com.pitwatch.app.installTestContainer
import com.pitwatch.core.config.UserConfig
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Review finding I2: the widget must re-render when its countdown runs out, not only when data changes. */
@RunWith(RobolectricTestRunner::class)
class WidgetRefreshTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var now: Instant = SNAP_NOW
    private var updates = 0
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer(tmp.root, clock = { now }, updateWidgets = { updates++ })
        runBlocking {
            container.stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") }
            container.repository.refresh(SNAP_NOW)
        }
    }

    @After
    fun tearDown() = container.scope.cancel()

    // Robolectric 4.17 exposes the alarm's PendingIntent only via the deprecated `operation` field.
    @Suppress("DEPRECATION")
    private fun widgetAlarm() = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.firstOrNull {
        shadowOf(it.operation).savedIntent.component?.className == WidgetRefreshReceiver::class.java.name
    }

    @Test
    fun `re-renders and arms a refresh just after the countdown deadline`() = runBlocking {
        var renders = 0
        WidgetRefresh.run(context, container) { renders++ }
        assertEquals(1, renders)
        // qm36 is on the field; its countdown ends at start + 150 s.
        assertEquals(Instant.ofEpochMilli(1775868749195).plusSeconds(151).toEpochMilli(), widgetAlarm()!!.triggerAtMs)
    }

    @Test
    fun `no alarm once nothing is counting down`() = runBlocking {
        now = SNAP_NOW.plusSeconds(3600)
        WidgetRefresh.run(context, container) {}
        assertNull(widgetAlarm())
    }

    @Test
    fun `the alarm re-renders the widgets`() {
        WidgetRefreshReceiver().onReceive(context, Intent(context, WidgetRefreshReceiver::class.java))
        awaitMain { updates > 0 }
    }
}
