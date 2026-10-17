package com.pitwatch.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import com.pitwatch.app.LA
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.ui.TimeFormat
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventScheduleWidgetContentTest {
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private val ready = EventScheduleModels.build(snapshotCache(), config, showPlayed = true, SNAP_NOW, Locale.US, LA)

    @Test
    fun `wide - title, each match with its teams, time, and the results with scores`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(360.dp, 344.dp))
        provideComposable { GlanceTheme { EventScheduleContent(ready) } }
        onNode(hasText("California Northern")).assertExists()
        onNode(hasTestTag("event-schedule-list")).assertExists()
        onNode(hasText("Q36")).assertExists()
        onNode(hasText("4698 5507 1678  ·  6036 9470 6814")).assertExists() // red, then blue
        // Each of our matches gets a dot in our alliance's color.
        val ourMatches = ready.lines.count { (it as? EventScheduleLine.Upcoming)?.row?.ours != null || (it as? EventScheduleLine.Played)?.row?.ours != null }
        onAllNodes(hasTextEqualTo("● ")).assertCountEquals(ourMatches)
        val q36 = (ready.lines.first { it is EventScheduleLine.Upcoming } as EventScheduleLine.Upcoming).row
        onNode(hasText(TimeFormat(LA, null).match(q36.time, q36.estimated))).assertExists()
        onNode(hasText("Results")).assertExists()
        onNode(hasText("216–401")).assertExists() // Q35, red–blue
    }

    @Test
    fun `the whole schedule is in the list, to the last match`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(360.dp, 344.dp))
        provideComposable { GlanceTheme { EventScheduleContent(ready) } }
        onNode(hasText("Q120")).assertExists()
        onNode(hasTextEqualTo("Q1")).assertExists()
    }

    @Test
    fun `narrow - label and time only, no team numbers`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(172.dp, 223.dp))
        provideComposable { GlanceTheme { EventScheduleContent(ready) } }
        onNode(hasText("Q36")).assertExists()
        onNode(hasText("6036 9470 6814")).assertDoesNotExist()
        onNode(hasText("216–401")).assertExists()
    }

    @Test
    fun `not configured shows the setup message`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(172.dp, 223.dp))
        provideComposable { GlanceTheme { EventScheduleContent(EventScheduleModels.build(EventCache(), UserConfig(), true, SNAP_NOW)) } }
        onNode(hasText("Set up PitWatch")).assertExists()
    }
}
