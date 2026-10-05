package com.pitwatch.app.schedule

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.pitwatch.app.AppContainer
import com.pitwatch.app.installTestContainer
import com.pitwatch.app.testMatch
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
class RefreshWorkerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    /** qm36's Nexus queue time minus 3 h: inside the event, before team 5507's live window opens. */
    private val queue36 = Instant.ofEpochMilli(1775867343646)
    private val now = queue36.minus(Duration.ofHours(3))
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        container = installTestContainer(tmp.root, clock = { now })
        runBlocking { container.stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") } }
    }

    @After
    fun tearDown() = container.scope.cancel()

    @Test
    fun `refreshes, reschedules itself, and arms auto-start`() = runBlocking {
        val worker = TestListenableWorkerBuilder<RefreshWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertTrue(container.stores.cache.data.first().matches.isNotEmpty())
        val queued = WorkManager.getInstance(context).getWorkInfosForUniqueWork(RefreshWorker.UNIQUE_NAME).get()
        assertTrue(queued.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED })
        val alarm = shadowOf(context.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm()!!
        assertEquals(queue36.minus(Duration.ofHours(2)).toEpochMilli(), alarm.triggerAtMs)
    }

    @Test
    fun `next delay is never below 15 minutes and daily without matches`() {
        val config = UserConfig(teamNumber = 1234, apiKey = "k", useScheduledTime = true)
        val soon = EventCache(matches = listOf(testMatch(1, time = now.plusSeconds(600).epochSecond)))
        assertEquals(RefreshWorker.MIN_DELAY, RefreshWorker.nextDelay(soon, config, now))
        assertEquals(Duration.ofDays(1), RefreshWorker.nextDelay(EventCache(), config, now))
    }

    @Test
    fun `refreshNow replaces a far-off scheduled run`() {
        // Found on-device: a launch before setup parks the next run a day out; finishing setup must not wait for it.
        RefreshWorker.enqueue(context, Duration.ofDays(1), androidx.work.ExistingWorkPolicy.REPLACE)
        RefreshWorker.refreshNow(context)
        val pending = WorkManager.getInstance(context).getWorkInfosForUniqueWork(RefreshWorker.UNIQUE_NAME).get()
            .filter { !it.state.isFinished }
        assertEquals(listOf(0L), pending.map { it.initialDelayMillis })
    }
}
