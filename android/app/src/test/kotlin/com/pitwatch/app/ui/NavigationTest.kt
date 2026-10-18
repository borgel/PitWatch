package com.pitwatch.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    @Test
    fun `a narrow window navigates from the bottom bar`() {
        compose.setContent { PitWatchScaffold(Tab.MATCHES, onSelect = {}) { Text("body") } }
        compose.onNodeWithTag("nav-bar").assertIsDisplayed()
        compose.onNodeWithTag("nav-rail").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w800dp-h600dp")
    fun `a wide window (open foldable, tablet) navigates from a side rail`() {
        // Asked for: a wide mode by screen width, not orientation.
        val picks = mutableListOf<Tab>()
        compose.setContent { PitWatchScaffold(Tab.MATCHES, onSelect = { picks += it }) { Text("body") } }
        compose.onNodeWithTag("nav-rail").assertIsDisplayed()
        compose.onNodeWithTag("nav-bar").assertDoesNotExist()
        compose.onNodeWithText("Matches").assertIsSelected()
        compose.onNodeWithText("Pit map").performClick()
        assertEquals(listOf(Tab.PIT_MAP), picks)
    }

    @Test
    @Config(qualifiers = "w800dp-h600dp")
    fun `readable width keeps forms from stretching across a wide window`() {
        compose.setContent { ReadableWidth { Box(Modifier.fillMaxWidth().testTag("readable")) } }
        compose.onNodeWithTag("readable").assertWidthIsEqualTo(READABLE_MAX_WIDTH)
    }

    @Test
    fun `readable width is the whole width on a phone`() {
        compose.setContent { ReadableWidth { Box(Modifier.fillMaxWidth().testTag("readable")) } }
        compose.onNodeWithTag("readable").assertWidthIsEqualTo(320.dp)
    }
}

