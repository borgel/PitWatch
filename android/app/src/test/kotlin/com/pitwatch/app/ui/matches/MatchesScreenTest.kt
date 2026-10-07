package com.pitwatch.app.ui.matches

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.AppContainer
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.installTestContainer
import com.pitwatch.core.config.UserConfig
import kotlin.test.assertFalse
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

/** Final review: the schedule offer must not silently "enable" a notification that can't be shown. */
@RunWith(RobolectricTestRunner::class)
class MatchesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer(tmp.root)
        runBlocking {
            container.stores.config.updateData { config }
            container.repository.refresh(SNAP_NOW)
        }
    }

    @After
    fun tearDown() = container.scope.cancel()

    @Test
    fun `without notification permission the offer asks instead of enabling`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        compose.setContent { MatchesScreen(container, config, onPickEvent = {}) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Show schedule in notifications")).fetchSemanticsNodes().isNotEmpty() ||
            runCatching { compose.onNodeWithTag("matches").performScrollToNode(hasText("Show schedule in notifications")); true }.getOrDefault(false) }
        compose.onNodeWithText("Show schedule in notifications").performClick()
        compose.waitForIdle()
        Thread.sleep(300)
        assertFalse(runBlocking { container.stores.notificationPrefs.data.first().scheduleEnabled })
    }
}
