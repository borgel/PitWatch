package com.pitwatch.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.pitwatch.app.AppContainer
import com.pitwatch.app.awaitMain
import com.pitwatch.app.installTestContainer
import com.pitwatch.app.schedule.RefreshWorker
import kotlin.test.assertEquals
import kotlinx.coroutines.awaitCancellation
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

/** Review finding m3: a settings save must refetch even if the widget update stalls or the screen goes away. */
@RunWith(RobolectricTestRunner::class)
class SettingsActionsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        container = installTestContainer(tmp.root, updateWidgets = { awaitCancellation() })
    }

    @After
    fun tearDown() = container.scope.cancel()

    @Test
    fun `any save schedules a refresh even when widgets stall`() {
        // A time-source change replans the refresh cadence too, so every save refreshes.
        SettingsActions.save(context, container) { it.copy(timeSource = com.pitwatch.core.config.TimeSource.TBA) }
        awaitMain { WorkManager.getInstance(context).getWorkInfosForUniqueWork(RefreshWorker.UNIQUE_NAME).get().isNotEmpty() }
        assertEquals(com.pitwatch.core.config.TimeSource.TBA, runBlocking { container.stores.config.data.first().timeSource })
    }
}
