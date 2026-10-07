package com.pitwatch.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.store.EventCache
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScheduleWidgetContentTest {
    private val ready = ScheduleWidgetModels.build(
        snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json"))),
        UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n"), SNAP_NOW, Locale.US, com.pitwatch.app.LA,
    )

    @Test
    fun `2x2 - header, last result and the schedule from the next match`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(172.dp, 223.dp))
        provideComposable { GlanceTheme { ScheduleWidgetContent(ready) } }
        onNode(hasText("5507 · #34 · 1-2-0")).assertExists()
        onNode(hasText("403–299")).assertExists()
        onNode(hasText("Q36")).assertExists()
        onNode(hasText("ON FIELD")).assertExists() // the next match is in motion
        onNode(hasText("End of day")).assertExists()
    }

    @Test
    fun `tall - more of the schedule, capped, under the child limit`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(360.dp, 450.dp))
        provideComposable { GlanceTheme { ScheduleWidgetContent(ready) } }
        onNode(hasText("Q43")).assertExists()
        onNode(hasText("LAST · Q22")).assertExists()
        onNode(hasTestTag("schedule-root")).onChildren().assertCountEquals(3) // header, last, list
    }

    @Test
    fun `not configured shows the setup message`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(172.dp, 223.dp))
        provideComposable { GlanceTheme { ScheduleWidgetContent(ScheduleWidgetModels.build(EventCache(), UserConfig(), SNAP_NOW)) } }
        onNode(hasText("Set up PitWatch")).assertExists()
    }

    @Test
    fun `a match in motion shows its phase instead of its time`() = runGlanceAppWidgetUnitTest {
        // On-device at 2×2 the pill pushed the time to "~4:26 P"; the phase is what matters once it's moving.
        val next = ready.days.first().items.filterIsInstance<com.pitwatch.app.ui.matches.MatchListModel.Item.Upcoming>().first().row
        val time = com.pitwatch.app.ui.TimeFormat(ready.timeZone, ready.zoneLabel).match(next.time, next.estimated)
        setAppWidgetSize(DpSize(172.dp, 223.dp))
        provideComposable { GlanceTheme { ScheduleWidgetContent(ready) } }
        onNode(hasText("ON FIELD")).assertExists()
        onNode(hasText(time)).assertDoesNotExist()
    }

    @Test
    fun `a tall schedule widget lists past the 9-line column cap`() = runGlanceAppWidgetUnitTest {
        // On-device at 4×3 the list stopped at 9 lines with room to spare.
        val beyond = WidgetLines.fit(ready.days, 50, cap = 27).drop(WidgetLines.MAX_LINES)
            .filterIsInstance<WidgetLine.Entry>().map { it.item }
            .filterIsInstance<com.pitwatch.app.ui.matches.MatchListModel.Item.Upcoming>().first().row.shortLabel
        setAppWidgetSize(DpSize(360.dp, 600.dp))
        provideComposable { GlanceTheme { ScheduleWidgetContent(ready) } }
        onNode(hasText(beyond)).assertExists()
    }
}
