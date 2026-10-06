package com.pitwatch.app.widget

import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
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
        UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n"), SNAP_NOW, Locale.US,
    )

    @Test
    fun `small - next match and countdown`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.SMALL)
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("5507 · #34 · 1-2-0")).assertExists()
        onNode(hasText("Q36")).assertExists()
        onNode(hasText("COUNTDOWN")).assertExists()
        onNode(hasText("LAST")).assertDoesNotExist()
    }

    @Test
    fun `medium - adds alliances and the last result`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.MEDIUM)
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("LAST")).assertExists()
        onNode(hasText("W 403–299")).assertExists()
        onNode(hasText("🔴 4698 5507 1678")).assertExists()
    }

    @Test
    fun `large - upcoming list with breaks`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.LARGE)
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("UPCOMING")).assertExists()
        onNode(hasText("LAST  Q22  W 403–299")).assertExists()
        onNode(hasText("End of day")).assertExists()
        onNode(hasText("Q43")).assertExists()
    }

    @Test
    fun `message states`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.SMALL)
        provideComposable { GlanceTheme { WidgetContent(WidgetModels.build(com.pitwatch.core.store.EventCache(), UserConfig(), SNAP_NOW)) { } } }
        onNode(hasText("Set up PitWatch")).assertExists()
    }
}
