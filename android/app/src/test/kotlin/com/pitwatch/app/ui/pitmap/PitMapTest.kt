package com.pitwatch.app.ui.pitmap

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pitwatch.app.SNAP
import com.pitwatch.app.fixture
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.model.PitMap
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
    fun `positions are box centers`() {
        assertEquals(Rect(463.5f, 145f, 563.5f, 245f), PitMapGeometry.rect(PitMap.Position(513.5, 195.0), PitMap.MapSize(100.0, 100.0)))
    }

    @Test
    fun `real map areas tile without overlapping`() {
        // Found on-device: read as top-left corners (as iOS did), "Pit admin" overlapped "EMT"; as centers they tile.
        val boxes = map.areas!!.values.map { PitMapGeometry.rect(it.position, it.size) }
        for ((i, a) in boxes.withIndex()) for (b in boxes.drop(i + 1)) {
            val overlap = a.intersect(b)
            assertTrue(overlap.width <= 1f || overlap.height <= 1f, "$a overlaps $b")
        }
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
        compose.onNodeWithText("OUR PIT").assertIsDisplayed()
        compose.onNodeWithText("C1").assertIsDisplayed()
    }

    @Test
    fun `team without a pit still renders the map`() {
        compose.setContent { PitMapContent(PitMapState.Loaded(map), teamNumber = "9999", onOpenSettings = {}) }
        compose.onNodeWithText("PIT MAP").assertIsDisplayed()
        compose.onAllNodes(androidx.compose.ui.test.hasText("OUR PIT")).assertCountEquals(0)
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

    @Test
    fun `labels are sized to fit inside their box`() {
        // Found on-device: labels sized in sp were magnified with the map and overflowed into neighbors.
        val pit = Rect(0f, 0f, 100f, 100f)
        for (text in listOf("8", "1678", "10339", "Pit admin EMT Radio")) {
            val px = PitMapGeometry.labelFontPx(pit, text)
            assertTrue(px * PitMapGeometry.CHAR_WIDTH_EM * text.length <= pit.width * 0.9f + 0.01f, text)
            assertTrue(px <= pit.height * 0.4f + 0.01f, text)
            assertTrue(px > 0f, text)
        }
    }

    @Test
    fun `arrows are lines along the box, rotated clockwise by angle`() {
        val up = PitMapGeometry.arrowLine(PitMap.Arrow(PitMap.Position(100.0, 100.0), PitMap.MapSize(46.0, 86.0), "single", 0.0))
        assertEquals(androidx.compose.ui.geometry.Offset(100f, 143f) to androidx.compose.ui.geometry.Offset(100f, 57f), up)
        val right = PitMapGeometry.arrowLine(PitMap.Arrow(PitMap.Position(100.0, 100.0), PitMap.MapSize(46.0, 86.0), "single", 90.0))
        assertEquals(57f, right.first.x, 0.01f)
        assertEquals(143f, right.second.x, 0.01f)
        assertEquals(100f, right.second.y, 0.01f)
    }

    @Test
    fun `header names the event and counts the pits`() {
        compose.setContent { PitMapContent(PitMapState.Loaded(map), teamNumber = "5507", onOpenSettings = {}, eventName = "California Northern") }
        compose.onNodeWithText("California Northern · ${map.pits.size} pits").assertIsDisplayed()
    }
}
