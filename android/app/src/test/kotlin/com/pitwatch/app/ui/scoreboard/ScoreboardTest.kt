package com.pitwatch.app.ui.scoreboard

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.theme.PitWatchTheme
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScoreboardTest {
    @get:Rule
    val compose = createComposeRule()

    private fun line(vararg teams: String, opr: Double? = null) =
        MatchListModel.AllianceLine(teams.map { MatchListModel.TeamChip(it, it == "5507") }, opr)

    @Test
    fun `phase steps`() {
        assertEquals(listOf(null, 1, 2, 3), Phase.entries.map { PhaseSteps.current(it) })
        assertNull(PhaseSteps.current(null))
        assertEquals("On field, step 3 of 4", PhaseSteps.description(Phase.ON_FIELD))
        assertEquals("In queue, step 1 of 4", PhaseSteps.description(Phase.QUEUEING))
        assertEquals("Not queued yet", PhaseSteps.description(Phase.PRE_QUEUE))
    }

    @Test
    fun `timeline announces the current step`() {
        compose.setContent { PitWatchTheme { PhaseTimeline(Phase.ON_FIELD) } }
        compose.onNode(hasContentDescription("On field, step 3 of 4")).assertIsDisplayed()
    }

    @Test
    fun `timeline before queueing has no current step`() {
        compose.setContent { PitWatchTheme { PhaseTimeline(null) } }
        compose.onNode(hasContentDescription("Not queued yet")).assertIsDisplayed()
    }

    @Test
    fun `alliance line marks our team and hides unknown OPR`() {
        compose.setContent { PitWatchTheme { AllianceLine(MatchAlliance.RED, line("4698", "5507", "1678")) } }
        compose.onNode(hasContentDescription("Your team, 5507")).assertIsDisplayed()
        compose.onNodeWithText("4698").assertIsDisplayed()
        compose.onAllNodes(hasText("Σ", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `alliance bands show OPR when known`() {
        compose.setContent { PitWatchTheme { AllianceBands(line("4698", "5507", "1678", opr = 351.4), line("6036", "9470", "6814", opr = 539.0)) } }
        compose.onNode(hasContentDescription("Your team, 5507")).assertIsDisplayed()
        compose.onNodeWithText("Σ 351").assertIsDisplayed()
        compose.onNodeWithText("Σ 539").assertIsDisplayed()
    }

    @Test
    fun `outcome pill says win, loss or tie`() {
        val empty = MatchListModel.AllianceLine(emptyList(), null)
        compose.setContent {
            PitWatchTheme {
                Column {
                    for (code in listOf("W", "T")) OutcomePill(MatchListModel.Result("k$code", "Qual 1", "Q1", 1, 1, code, empty, empty, 1, 1))
                }
            }
        }
        compose.onNodeWithText("WIN").assertIsDisplayed()
        compose.onNodeWithText("TIE").assertIsDisplayed()
    }

    @Test
    fun `hero countdown text`() {
        val now = Instant.parse("2026-04-11T00:00:00Z")
        assertEquals("3:27", HeroCountdown.text(now.plusSeconds(207), now))
        assertEquals("1:05:09", HeroCountdown.text(now.plusSeconds(3909), now))
        assertEquals("0:00", HeroCountdown.text(now.minusSeconds(30), now))
    }

    @Test
    fun `long header titles truncate and keep the action`() {
        compose.setContent {
            PitWatchTheme {
                ScreenHeader(
                    "FIRST in California Northern Championship presented by a very long sponsor list", "Team 5507",
                    action = { HeaderIconButton(Icons.Filled.DateRange, "Choose event") {} },
                )
            }
        }
        compose.onNode(hasContentDescription("Choose event")).assertIsDisplayed()
    }

    @Test
    fun `team separators are not read aloud`() {
        compose.setContent { PitWatchTheme { AllianceLine(MatchAlliance.RED, line("4698", "5507", "1678")) } }
        compose.onAllNodes(hasText("·", substring = true), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("1678").assertIsDisplayed()
    }
}
