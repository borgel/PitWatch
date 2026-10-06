package com.pitwatch.app.ui.pitmap

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.pitwatch.app.AppContainer
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.installTestContainer
import com.pitwatch.core.config.UserConfig
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Review finding I3: with a key configured, the screen must never say the key is missing (even while loading). */
@RunWith(RobolectricTestRunner::class)
class PitMapScreenTest {
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
    fun `configured key is never reported missing`() {
        compose.setContent { PitMapScreen(container, config, onOpenSettings = {}) }
        compose.onAllNodes(hasText("Add a FRC Nexus API key", substring = true)).assertCountEquals(0)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Pit C1")).fetchSemanticsNodes().isNotEmpty() }
    }
}
