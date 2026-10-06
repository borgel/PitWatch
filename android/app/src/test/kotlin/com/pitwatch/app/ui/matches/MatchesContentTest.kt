package com.pitwatch.app.ui.matches

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
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
    fun `hero - header, queue line and next match`() {
        show()
        compose.onNodeWithText("CALIFORNIA NORTHERN").assertIsDisplayed()
        compose.onNodeWithText("Team 5507 · Rank #34 · 1-2-0").assertIsDisplayed()
        compose.onNodeWithText("QUAL 36").assertIsDisplayed()
        compose.onNodeWithText("ON FIELD").assertIsDisplayed()
        compose.onNode(hasContentDescription("On field, step 3 of 4")).assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("Your team, 5507")).onFirst().assertIsDisplayed()
        // The hero is taller than the test screen: the button sits below the fold.
        compose.onNodeWithTag("matches").performScrollToNode(hasText("START LIVE TRACKING"))
        compose.onNodeWithText("START LIVE TRACKING").assertIsDisplayed()
        compose.onNodeWithTag("matches").performScrollToNode(hasText("NOW QUEUING · QUALIFICATION 38"))
    }

    @Test
    fun `the hero match is not repeated in the list`() {
        show()
        compose.onNodeWithTag("matches").performScrollToNode(hasText("Q43"))
        compose.onNodeWithText("Q43").assertIsDisplayed() // later rows use the short label…
        compose.onAllNodes(hasText("Q36")).assertCountEquals(0) // …and the hero's match isn't one of them
    }

    @Test
    fun `a day holding only the hero match gets no header`() {
        val lonely = model.copy(days = listOf(MatchListModel.Day(null, "Lonely day", listOf(MatchListModel.Item.Upcoming(model.next!!)))))
        compose.setContent { MatchesContent(lonely, SNAP_NOW, false, false, {}, {}, {}, {}) }
        // Bring the first list item after the hero on screen, so a header there would be composed.
        compose.onNodeWithTag("matches").performScrollToNode(hasText("NOW QUEUING", substring = true))
        compose.onAllNodes(hasText("Lonely day", ignoreCase = true)).assertCountEquals(0)
    }

    @Test
    fun `days, breaks and results are listed`() {
        show()
        val list = compose.onNodeWithTag("matches")
        for (text in listOf("SATURDAY, APR 11", "LUNCH", "END OF DAY", "LAST", "Q22")) {
            list.performScrollToNode(hasText(text, substring = true))
            compose.onAllNodes(hasText(text, substring = true)).onFirst().assertIsDisplayed()
        }
    }

    @Test
    fun `result rows show both scores and the outcome`() {
        show()
        compose.onNodeWithTag("matches").performScrollToNode(hasText("403"))
        compose.onNodeWithText("403").assertIsDisplayed()
        compose.onNodeWithText("299").assertIsDisplayed()
        compose.onAllNodes(hasText("WIN")).onFirst().assertIsDisplayed()
    }

    @Test
    fun `TBA-only events show the timeline with nothing current`() {
        val tbaOnly = MatchListModels.build(snapshotCache(), UserConfig(teamNumber = 5507, apiKey = "k"), RefreshState(), SNAP_NOW, Locale.US, com.pitwatch.app.LA)
        compose.setContent { MatchesContent(tbaOnly, SNAP_NOW, false, false, {}, {}, {}, {}) }
        compose.onNode(hasContentDescription("Not queued yet")).assertIsDisplayed()
    }

    @Test
    fun `tracking button reflects and toggles state`() {
        var toggles = 0
        show(tracking = true, onToggle = { toggles++ })
        compose.onNodeWithTag("matches").performScrollToNode(hasText("STOP LIVE TRACKING"))
        compose.onNodeWithText("STOP LIVE TRACKING").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `tapping a result opens it on TBA`() {
        var opened: String? = null
        show(onOpen = { opened = it })
        compose.onNodeWithTag("matches").performScrollToNode(hasText("Q22"))
        compose.onNodeWithText("Q22").performClick()
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

    @Test
    fun `an untimed hero match doesn't read Time TBD start`() {
        val untimed = model.copy(days = model.days.map { day ->
            day.copy(items = day.items.map { if (it is MatchListModel.Item.Upcoming && it.row.isNext) MatchListModel.Item.Upcoming(it.row.copy(time = null)) else it })
        })
        compose.setContent { MatchesContent(untimed, SNAP_NOW, false, false, {}, {}, {}, {}) }
        compose.onAllNodes(hasText("TBD start", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `a day left with only a break after the hero takes its match is hidden`() {
        val next = model.next!!
        val breakOnly = model.copy(days = listOf(
            MatchListModel.Day(null, "Lonely day", listOf(MatchListModel.Item.Upcoming(next), MatchListModel.Item.Break("End of day", SNAP_NOW, null)))
        ) + model.days.drop(1))
        compose.setContent { MatchesContent(breakOnly, SNAP_NOW, false, false, {}, {}, {}, {}) }
        compose.onNodeWithTag("matches").performScrollToNode(hasText("NOW QUEUING", substring = true))
        compose.onAllNodes(hasText("Lonely day", ignoreCase = true)).assertCountEquals(0)
        compose.onAllNodes(hasText("— END OF DAY —")).assertCountEquals(0)
    }

    @Test
    fun `the list offers the schedule notification when it's off`() {
        var asked = 0
        compose.setContent { MatchesContent(model, SNAP_NOW, false, false, {}, {}, {}, {}, showScheduleOffer = true, onShowSchedule = { asked++ }) }
        compose.onNodeWithTag("matches").performScrollToNode(hasText("Show schedule in notifications"))
        compose.onNodeWithText("Show schedule in notifications").performClick()
        assertEquals(1, asked)
    }
}
