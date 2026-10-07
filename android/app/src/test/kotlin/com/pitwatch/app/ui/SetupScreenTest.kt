package com.pitwatch.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pitwatch.app.AppContainer
import com.pitwatch.app.installTestContainer
import com.pitwatch.core.config.UserConfig
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SetupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer(tmp.root)
    }

    @After
    fun tearDown() = container.scope.cancel()

    @Test
    fun `each key explains what it's for, and Nexus isn't labeled optional`() {
        compose.setContent { SetupScreen(container, UserConfig()) }
        compose.onNodeWithText("FRC Nexus API key").assertIsDisplayed()
        compose.onAllNodes(hasText("(optional)", substring = true)).assertCountEquals(0)
        compose.onNodeWithText(ApiKeyHelp.TBA).assertIsDisplayed()
        compose.onNodeWithText(ApiKeyHelp.NEXUS).assertIsDisplayed()
    }
}
