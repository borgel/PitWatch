package com.pitwatch.app.ui.pitmap

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pitwatch.app.SNAP
import com.pitwatch.app.fixture
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.model.PitMap
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PitMapTest {
    @get:Rule
    val compose = createComposeRule()

    private val map = PitWatchJson.decodeFromString<PitMap>(fixture("$SNAP/nexus_map.json"))

    @Test
    fun `positions are top-left corners, like iOS`() {
        assertEquals(Rect(513.5f, 195f, 613.5f, 295f), PitMapGeometry.rect(PitMap.Position(513.5, 195.0), PitMap.MapSize(100.0, 100.0)))
    }

    @Test
    fun `fit scale keeps the whole map visible`() {
        assertEquals(minOf(500f / 979f, 1000f / 1209f), PitMapGeometry.fitScale(map, 500f, 1000f))
    }

    @Test
    fun `focus finds our pit and tolerates teams without one`() {
        // Review focus #5
        assertEquals("C1", PitMapGeometry.focus(map, "5507")?.address)
        assertNull(PitMapGeometry.focus(map, "9999"))
        assertNull(PitMapGeometry.focus(map.copy(pits = emptyMap()), "5507"))
        assertNull(PitMapGeometry.focus(map, null))
    }

    @Test
    fun `loaded map shows our pit address`() {
        compose.setContent { PitMapContent(PitMapState.Loaded(map), teamNumber = "5507", onOpenSettings = {}) }
        compose.onNodeWithText("Pit C1").assertIsDisplayed()
    }

    @Test
    fun `team without a pit still renders the map`() {
        compose.setContent { PitMapContent(PitMapState.Loaded(map), teamNumber = "9999", onOpenSettings = {}) }
        compose.onNodeWithText("Pit map").assertIsDisplayed()
    }

    @Test
    fun `explains missing key and missing map`() {
        compose.setContent { PitMapContent(PitMapState.NoKey, "5507", {}) }
        compose.onNodeWithText("Add a FRC Nexus API key in Settings to see the pit map.").assertIsDisplayed()
    }

    @Test
    fun `explains an event without a map`() {
        compose.setContent { PitMapContent(PitMapState.Unavailable, "5507", {}) }
        compose.onNodeWithText("This event has no pit map on Nexus.").assertIsDisplayed()
    }
}
