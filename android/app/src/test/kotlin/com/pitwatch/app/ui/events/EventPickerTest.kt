package com.pitwatch.app.ui.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pitwatch.app.testEvent
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventPickerTest {
    @get:Rule
    val compose = createComposeRule()

    private val options = listOf(
        EventOption.from(testEvent(key = "2026cancmp").copy(name = "California Northern", city = "Daly City", stateProv = "CA"), java.util.Locale.US),
        EventOption.from(testEvent(key = "2026casf", startDate = "2026-03-20", endDate = "2026-03-22").copy(name = "San Francisco"), java.util.Locale.US),
    )

    @Test
    fun `option shows dates and location`() {
        assertEquals(EventOption("2026cancmp", "California Northern", "Apr 9 – 12, 2026", "Daly City, CA"), options[0])
        assertNull(options[1].location)
    }

    @Test
    fun `selecting an event or Auto reports the choice`() {
        val picks = mutableListOf<String?>()
        compose.setContent { EventPickerContent(EventPickerState.Loaded(options), selectedKey = "2026cancmp", onSelect = { picks += it }, onBack = {}, onRetry = {}) }
        compose.onNodeWithText("San Francisco").performClick()
        compose.onNodeWithText("Auto (current or next event)").performClick()
        assertEquals(listOf("2026casf", null), picks)
    }

    @Test
    fun `loading and error states`() {
        var state: EventPickerState by mutableStateOf(EventPickerState.Loading)
        compose.setContent { EventPickerContent(state, null, {}, {}, {}) }
        compose.onNodeWithText("Loading events…").assertIsDisplayed()
        state = EventPickerState.Error("API error 401")
        compose.onNodeWithText("Couldn't load events: API error 401").assertIsDisplayed()
    }

    @Test
    fun `date ranges read naturally`() {
        val us = java.util.Locale.US
        assertEquals("Mar 30 – Apr 2, 2026", EventOption.dates(testEvent(startDate = "2026-03-30", endDate = "2026-04-02"), us))
        assertEquals("Dec 30, 2025 – Jan 2, 2026", EventOption.dates(testEvent(startDate = "2025-12-30", endDate = "2026-01-02"), us))
        assertEquals("Apr 9, 2026", EventOption.dates(testEvent(startDate = "2026-04-09", endDate = "2026-04-09"), us))
        assertEquals("bad – dates", EventOption.dates(testEvent(startDate = "2026-04-09").copy(startDate = "bad", endDate = "dates"), us))
    }

    @Test
    fun `errors offer a retry`() {
        var retries = 0
        compose.setContent { EventPickerContent(EventPickerState.Error("timeout"), null, {}, {}, onRetry = { retries++ }) }
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `picker has a scoreboard header and marks the current event`() {
        compose.setContent { EventPickerContent(EventPickerState.Loaded(options), "2026casf", {}, {}, {}) }
        compose.onNodeWithText("CHOOSE EVENT").assertIsDisplayed()
        compose.onNode(hasContentDescription("Selected")).assertIsDisplayed()
    }
}
