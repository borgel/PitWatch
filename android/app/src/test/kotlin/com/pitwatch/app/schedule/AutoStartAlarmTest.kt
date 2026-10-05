package com.pitwatch.app.schedule

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.AppContainer
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.awaitMain
import com.pitwatch.app.installTestContainer
import com.pitwatch.app.live.LiveMatchService
import com.pitwatch.core.config.LiveActivityMode
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

@RunWith(RobolectricTestRunner::class)
class AutoStartAlarmTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer(tmp.root)
    }

    @After
    fun tearDown() = container.scope.cancel()

    @Test
    fun `arming schedules an exact alarm and null cancels it`() {
        val at = Instant.ofEpochSecond(1_900_000_000)
        AutoStartAlarm.arm(context, at)
        assertEquals(at.toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtMs)
        AutoStartAlarm.arm(context, null)
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test
    fun `receiver starts the live service inside the window`() {
        runBlocking {
            container.stores.config.updateData {
                UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n", liveActivityMode = LiveActivityMode.ALL_DAY, timeSource = com.pitwatch.core.config.TimeSource.TBA)
            }
            container.repository.refresh(SNAP_NOW)
        }
        AutoStartReceiver().onReceive(context, Intent(context, AutoStartReceiver::class.java))
        awaitMain { shadowOf(context as Application).peekNextStartedService() != null }
        assertEquals(LiveMatchService::class.java.name, shadowOf(context as Application).nextStartedService.component?.className)
    }
}
