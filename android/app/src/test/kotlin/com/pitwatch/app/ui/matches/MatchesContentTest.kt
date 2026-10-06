package com.pitwatch.app.ui.matches

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.store.RefreshState
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MatchesContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val model = MatchListModels.build(
        snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json"))),
        UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n"), RefreshState(), SNAP_NOW, Locale.US, com.pitwatch.app.LA,
    )

    private fun show(tracking: Boolean = false, onToggle: () -> Unit = {}, onOpen: (String) -> Unit = {}, onPick: () -> Unit = {}) {
        compose.setContent {
            MatchesContent(model, SNAP_NOW, tracking, refreshing = false, onRefresh = {}, onToggleTracking = onToggle, onOpenMatch = onOpen, onPickEvent = onPick)
        }
    }

    @Test
    fun `status card, queue line and next match`() {
        show()
        compose.onNodeWithText("California Northern").assertIsDisplayed()
        compose.onNodeWithText("Team 5507 · Rank #34 · 1-2-0").assertIsDisplayed()
        compose.onNodeWithText("Now queuing: Qualification 38").assertIsDisplayed()
        compose.onNodeWithText("Start live tracking").assertIsDisplayed()
        compose.onNodeWithText("Qual 36").assertIsDisplayed()
        compose.onNodeWithText("ON FIELD").assertIsDisplayed()
    }

    @Test
    fun `days, breaks and results are listed`() {
        show()
        val list = compose.onNodeWithTag("matches")
        for (text in listOf("Saturday, Apr 11", "Lunch", "End of day", "Results", "Qual 22")) {
            list.performScrollToNode(hasText(text, substring = true))
            compose.onNodeWithText(text, substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun `tracking button reflects and toggles state`() {
        var toggles = 0
        show(tracking = true, onToggle = { toggles++ })
        compose.onNodeWithText("Stop live tracking").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `tapping a result opens it on TBA`() {
        var opened: String? = null
        show(onOpen = { opened = it })
        compose.onNodeWithTag("matches").performScrollToNode(hasText("Qual 22"))
        compose.onNodeWithText("Qual 22").performClick()
        assertEquals("https://www.thebluealliance.com/match/2026cancmp_qm22", opened)
    }

    @Test
    fun `event picker opens from the top bar`() {
        var picks = 0
        show(onPick = { picks++ })
        compose.onNodeWithContentDescriptionSafe("Choose event").performClick()
        assertEquals(1, picks)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionSafe(label: String) =
        onNode(androidx.compose.ui.test.hasContentDescription(label))

    @Test
    fun `countdown text`() {
        assertEquals("now", Countdowns.text(SNAP_NOW, SNAP_NOW))
        assertEquals("12m", Countdowns.text(SNAP_NOW.plusSeconds(12 * 60 - 30), SNAP_NOW))
        assertEquals("1h 5m", Countdowns.text(SNAP_NOW.plusSeconds(65 * 60), SNAP_NOW))
    }

    private fun compactRow(phase: com.pitwatch.core.model.Phase?) = model.days[1].items
        .filterIsInstance<MatchListModel.Item.Upcoming>().first().row.copy(phase = phase)

    @Test
    fun `compact rows hide the pre-queue badge`() {
        // Found on-device: every future row wore a gray UPCOMING badge.
        compose.setContent { MatchRowItem(compactRow(com.pitwatch.core.model.Phase.PRE_QUEUE)) {} }
        compose.onAllNodes(hasText("UPCOMING")).assertCountEquals(0)
    }

    @Test
    fun `compact rows badge matches that are in motion`() {
        compose.setContent { MatchRowItem(compactRow(com.pitwatch.core.model.Phase.QUEUEING)) {} }
        compose.onNodeWithText("IN QUEUE").assertIsDisplayed()
    }

    @Test
    fun `away from the event, times carry the phone's zone`() {
        val tokyo = MatchListModels.build(
            snapshotCache(), UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n"), RefreshState(), SNAP_NOW, Locale.US,
            java.time.ZoneId.of("Asia/Tokyo"),
        )
        compose.setContent { MatchesContent(tokyo, SNAP_NOW, false, false, {}, {}, {}, {}) }
        compose.onNode(hasText("9:52", substring = true).and(hasText("JST", substring = true))).assertIsDisplayed()
    }
}
