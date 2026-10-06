package com.pitwatch.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.pitwatch.app.AppContainer
import com.pitwatch.app.installTestContainer
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.RefreshState
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Final review: errors must stay visible in Settings — the header subtitle only fits one line. */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
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
            container.stores.refreshState.updateData { RefreshState(nexusLastError = "HTTP 403") }
        }
    }

    @After
    fun tearDown() = container.scope.cancel()

    @Test
    fun `header shows the last refresh, the status card shows the errors`() {
        compose.setContent { SettingsScreen(container, config) }
        compose.onNodeWithText("Last refresh: never").assertIsDisplayed()
        compose.onNode(hasText("Nexus: HTTP 403", substring = true)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `API key fields carry the same help text`() {
        compose.setContent { SettingsScreen(container, config) }
        compose.onNode(hasText(ApiKeyHelp.TBA)).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(ApiKeyHelp.NEXUS)).performScrollTo().assertIsDisplayed()
    }
}
