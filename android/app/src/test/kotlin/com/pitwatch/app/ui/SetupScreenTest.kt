package com.pitwatch.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

    @Test
    fun `a check cut short by rotation or a theme switch never leaves the button stuck on Checking`() {
        // The check runs in the screen's scope, which a recreated activity cancels; the busy flag mustn't outlive it.
        val restore = StateRestorationTester(compose)
        restore.setContent { SetupScreen(container, UserConfig(), validate = { _, _, _ -> kotlinx.coroutines.awaitCancellation() }) }
        compose.onNodeWithText("TBA API key").performTextInput("key")
        compose.onNodeWithText("Team number").performTextInput("5507")
        compose.onNodeWithText("CONTINUE").performClick()
        compose.onNodeWithText("CHECKING…").assertIsDisplayed()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("CONTINUE").assertIsEnabled()
    }

    @Test
    fun `a failed check shows why and can be retried`() {
        var attempts = 0
        compose.setContent {
            SetupScreen(container, UserConfig(), validate = { _, _, _ -> attempts++; SetupValidator.Outcome.Invalid(SetupValidator.NO_CONNECTION) })
        }
        compose.onNodeWithText("CONTINUE").performClick()
        compose.onNodeWithText(SetupValidator.NO_CONNECTION).assertIsDisplayed()
        compose.onNodeWithText("CONTINUE").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { attempts == 2 }
    }
}
