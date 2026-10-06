package com.pitwatch.app.schedule

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.pitwatch.app.AppContainer
import com.pitwatch.app.awaitMain
import com.pitwatch.app.installTestContainer
import com.pitwatch.core.config.UserConfig
import java.time.Duration
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

/** Exact alarms don't survive a reboot; the boot receiver re-arms auto-start straight away. */
@RunWith(RobolectricTestRunner::class)
class BootReceiverTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val queue36 = Instant.ofEpochMilli(1775867343646)
    private val now = queue36.minus(Duration.ofHours(3))
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        container = installTestContainer(tmp.root, clock = { now })
        runBlocking {
            container.stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") }
            container.repository.refresh(now)
        }
    }

    @After
    fun tearDown() = container.scope.cancel()

    @Test
    fun `boot re-arms the auto-start alarm`() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        awaitMain { alarms.peekNextScheduledAlarm() != null }
        assertEquals(queue36.minus(Duration.ofHours(2)).toEpochMilli(), alarms.peekNextScheduledAlarm()!!.triggerAtMs)
    }

    @Test
    fun `other broadcasts are ignored`() {
        BootReceiver().onReceive(context, Intent("com.example.SOMETHING"))
        Thread.sleep(200)
        assertNull(shadowOf(context.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm())
    }
}
