package com.pitwatch.app.widget

import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
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
    fun `small - next match, countdown and phase bar`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.SMALL)
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("5507 · #34 · 1-2-0")).assertExists()
        onNode(hasText("Q36")).assertExists()
        onNode(hasText("COUNTDOWN")).assertExists()
        onAllNodes(hasTestTag("phase-step")).assertCountEquals(4)
        onNode(hasText("to match end")).assertExists() // what the countdown counts to, at every size
        onNode(hasText("LAST")).assertDoesNotExist()
    }

    @Test
    fun `medium - adds alliances with our chip and the last result`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.MEDIUM)
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("LAST")).assertExists()
        onNode(hasText("403–299")).assertExists()
        onNode(hasText("WIN")).assertExists()
        onNode(hasText("4698 · ")).assertExists()
        onNode(hasText(" · 1678")).assertExists()
    }

    @Test
    fun `large - upcoming list with breaks`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(androidx.compose.ui.unit.DpSize(250.dp, 600.dp)) // tall enough that the 9-line cap, not the height, ends the list
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("CALIFORNIA NORTHERN")).assertExists()
        onNode(hasText("Saturday, Apr 11")).assertExists()
        onNode(hasText("LAST · Q22")).assertExists()
        onAllNodes(hasText("End of day")).assertCountEquals(1)
        onNode(hasText("Lunch")).assertExists()
        onNode(hasText("Q43")).assertExists()
    }

    @Test
    fun `a tall widget with no room for a header and a row shows no list`() = runGlanceAppWidgetUnitTest {
        // On-device (emulator-5580): ~293 dp of content above the list; a header with no room for its row got clipped.
        setAppWidgetSize(androidx.compose.ui.unit.DpSize(250.dp, 320.dp))
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("Friday, Apr 10")).assertDoesNotExist()
        onNode(hasText("LAST · Q22")).assertExists()
    }

    @Test
    fun `a mid-height large widget still lists what's next`() = runGlanceAppWidgetUnitTest {
        // Final review: at 4×3 (360×344 dp on emulator-5580) the list vanished; the smaller countdown makes room for a day and a row.
        setAppWidgetSize(androidx.compose.ui.unit.DpSize(360.dp, 344.dp))
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("Friday, Apr 10")).assertExists()
        onNode(hasText("End of day")).assertExists()
    }

    @Test
    fun `message states`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.SMALL)
        provideComposable { GlanceTheme { WidgetContent(WidgetModels.build(com.pitwatch.core.store.EventCache(), UserConfig(), SNAP_NOW)) { } } }
        onNode(hasText("Set up PitWatch")).assertExists()
    }
}
