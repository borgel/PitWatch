package com.pitwatch.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.glance.text.Text
import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventRankings
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetContentTest {
    private val ready = WidgetModels.build(
        snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json"))),
        UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n"), SNAP_NOW, Locale.US, com.pitwatch.app.LA,
    )

    @Test
    fun `smallest - just the match and its countdown`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        setAppWidgetSize(PitWatchWidget.SMALL)
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("Q36")).assertExists()
        onNode(hasText("COUNTDOWN")).assertExists()
        onNode(hasText("5507 · #34 · 1-2-0")).assertDoesNotExist() // no room: it would push the countdown out
        onNode(hasText("LAST")).assertDoesNotExist()
    }

    @Test
    fun `2x2 - what the countdown counts to, and the stage bar`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        setAppWidgetSize(DpSize(170.dp, 170.dp))
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("TO MATCH START")).assertExists() // as the scoreboard card says it
        onAllNodes(hasTestTag("phase-step")).assertCountEquals(4)
    }

    @Test
    fun `wide - the scoreboard - record top right over the countdown, stages with times, alliances, footer`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        // Asked for: the widget in the scoreboard card's design, the record kept in the top-right corner.
        setAppWidgetSize(DpSize(360.dp, 223.dp))
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        val right = onNode(hasTestTag("scoreboard-right")).onChildren()
        right.assertAny(hasText("5507 · #34 · 1-2-0"))
        right.assertAny(hasText("COUNTDOWN"))
        right.assertAny(hasText("TO MATCH START"))
        onNode(hasTestTag("widget-root")).onChildren().filter(hasText("5507 · #34 · 1-2-0")).assertCountEquals(0)
        onNode(hasTextEqualTo("ON FIELD")).assertExists()
        onAllNodes(hasTestTag("stage-label")).assertCountEquals(4)
        onNode(hasText("QUEUE ")).assertExists() // "QUEUE 8:25"
        onNode(hasText("START ")).assertExists()
        onNode(hasTextEqualTo("5507")).assertExists() // our number on its alliance chip
        onNode(hasText("6036 · 9470 · 6814")).assertExists()
        onNode(hasTextEqualTo("NOW · FIELD #36 · 24M BEHIND")).assertExists()
        onNode(hasText("LAST")).assertDoesNotExist()
    }

    @Test
    fun `narrow - the record keeps its own top line, stages are named without times`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        setAppWidgetSize(DpSize(172.dp, 344.dp))
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasTestTag("widget-root")).onChildren().assertAny(hasText("5507 · #34 · 1-2-0"))
        onNode(hasTestTag("scoreboard-right")).assertDoesNotExist()
        onNode(hasTextEqualTo("QUEUE")).assertExists()
        onNode(hasTextEqualTo("NOW · FIELD #36 · 24M BEHIND")).assertExists()
    }

    @Test
    fun `without a countdown, the start time takes its place`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        setAppWidgetSize(DpSize(360.dp, 223.dp))
        provideComposable { GlanceTheme { WidgetContent(ready.copy(countdownDeadline = null)) { Text("COUNTDOWN") } } }
        onNode(hasText("COUNTDOWN")).assertDoesNotExist()
        onNode(hasTestTag("scoreboard-right")).onChildren().assertAny(hasText("~"))
    }

    @Test
    fun `large - upcoming list with breaks`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        setAppWidgetSize(DpSize(250.dp, 600.dp)) // tall enough that the 9-line cap, not the height, ends the list
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("Saturday, Apr 11")).assertExists()
        onNode(hasText("LAST · Q22")).assertExists()
        onAllNodes(hasText("End of day")).assertCountEquals(1)
        onNode(hasText("Lunch")).assertExists()
        onNode(hasText("Q43")).assertExists()
    }

    @Test
    fun `a mid-height large widget still lists what's next`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        // Final review: at 4×3 (360×344 dp on emulator-5580) the list vanished once; keep at least a day and a row.
        setAppWidgetSize(DpSize(360.dp, 344.dp))
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("Friday, Apr 10")).assertExists()
    }

    @Test
    fun `2x3 - the last result fits one line on a narrow widget`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        // On-device: at 2×3 (172 dp wide) "LAST · Q22" wrapped onto two lines.
        setAppWidgetSize(DpSize(172.dp, 420.dp))
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("LAST · Q22")).assertDoesNotExist()
        onNode(hasText("403–299")).assertExists()
        onNode(hasText("WIN")).assertExists()
    }

    @Test
    fun `message states`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        setAppWidgetSize(PitWatchWidget.SMALL)
        provideComposable { GlanceTheme { WidgetContent(WidgetModels.build(com.pitwatch.core.store.EventCache(), UserConfig(), SNAP_NOW)) { } } }
        onNode(hasText("Set up PitWatch")).assertExists()
    }

    @Test
    fun `columns stay under Glance's 10-children limit`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        setAppWidgetSize(DpSize(250.dp, 600.dp)) // every optional part drawn
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        // scoreboard, divider, last result, list. Adding more? Recount.
        onNode(hasTestTag("widget-root")).onChildren().assertCountEquals(4)
        // top row, stage bar, stage labels (names only at 250 dp), two alliances, footer, and so the start time: 7.
        onNode(hasTestTag("scoreboard")).onChildren().assertCountEquals(7)
    }
}
