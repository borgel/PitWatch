package com.pitwatch.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `three destinations, current one selected`() {
        val picks = mutableListOf<Tab>()
        compose.setContent { PitWatchScaffold(Tab.MATCHES, onSelect = { picks += it }) { Text("body") } }
        compose.onNodeWithText("Matches").assertIsSelected()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("body").assertIsDisplayed()
        compose.onNodeWithText("Pit map").performClick()
        assertEquals(listOf(Tab.PIT_MAP), picks)
    }
}
