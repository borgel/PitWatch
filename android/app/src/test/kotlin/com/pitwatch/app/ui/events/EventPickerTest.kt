package com.pitwatch.app.ui.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
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
        EventOption.from(testEvent(key = "2026cancmp").copy(name = "California Northern", city = "Daly City", stateProv = "CA")),
        EventOption.from(testEvent(key = "2026casf", startDate = "2026-03-20", endDate = "2026-03-22").copy(name = "San Francisco")),
    )

    @Test
    fun `option shows dates and location`() {
        assertEquals(EventOption("2026cancmp", "California Northern", "2026-04-09 – 2026-04-12", "Daly City, CA"), options[0])
        assertNull(options[1].location)
    }

    @Test
    fun `selecting an event or Auto reports the choice`() {
        val picks = mutableListOf<String?>()
        compose.setContent { EventPickerContent(EventPickerState.Loaded(options), selectedKey = "2026cancmp", onSelect = { picks += it }, onBack = {}) }
        compose.onNodeWithText("San Francisco").performClick()
        compose.onNodeWithText("Auto (current or next event)").performClick()
        assertEquals(listOf("2026casf", null), picks)
    }

    @Test
    fun `loading and error states`() {
        var state: EventPickerState by mutableStateOf(EventPickerState.Loading)
        compose.setContent { EventPickerContent(state, null, {}, {}) }
        compose.onNodeWithText("Loading events…").assertIsDisplayed()
        state = EventPickerState.Error("API error 401")
        compose.onNodeWithText("Couldn't load events: API error 401").assertIsDisplayed()
    }
}
