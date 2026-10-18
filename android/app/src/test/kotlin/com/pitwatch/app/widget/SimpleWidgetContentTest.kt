package com.pitwatch.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.glance.text.Text
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.snapshotCache
import com.pitwatch.core.config.UserConfig
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Asked for: a simplified widget — the next match number, times, the phase bar, and alliance color; nothing else. */
@RunWith(RobolectricTestRunner::class)
class SimpleWidgetContentTest {
    private val ready = WidgetModels.build(
        snapshotCache(), UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n"), SNAP_NOW, Locale.US, com.pitwatch.app.LA,
    )

    @Test
    fun `its top is the match on its alliance chip, the countdown, and the phase - then the stage bar`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        setAppWidgetSize(DpSize(360.dp, 223.dp))
        var countdownSp = 0f
        provideComposable { GlanceTheme { SimpleWidgetContent(ready) { countdownSp = LocalCountdownSp.current; Text("COUNTDOWN") } } }
        onNode(hasTestTag("match-chip")).assertExists()
        onNode(hasTextEqualTo("Q36")).assertExists()
        onNode(hasTextEqualTo("ON FIELD")).assertExists()
        onNode(hasText("COUNTDOWN")).assertExists()
        onNode(hasTextEqualTo("TO MATCH START")).assertExists()
        onAllNodes(hasTestTag("phase-step")).assertCountEquals(4)
        onNode(hasText("QUEUE ")).assertExists()
        assertEquals(56f, countdownSp, "a roomy widget fills its top with a big countdown")
    }

    @Test
    fun `nothing else - no record, alliances, footer, results or later matches`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        setAppWidgetSize(DpSize(360.dp, 600.dp))
        provideComposable { GlanceTheme { SimpleWidgetContent(ready) { Text("COUNTDOWN") } } }
        for (absent in listOf("5507 · #34", "4698", "6036", "24M BEHIND", "LAST", "Q43", "Lunch")) {
            onNode(hasText(absent)).assertDoesNotExist()
        }
    }

    @Test
    fun `2x2 - stacked, still with what the countdown counts to`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        setAppWidgetSize(DpSize(170.dp, 170.dp))
        var countdownSp = 0f
        provideComposable { GlanceTheme { SimpleWidgetContent(ready) { countdownSp = LocalCountdownSp.current; Text("COUNTDOWN") } } }
        onNode(hasTextEqualTo("Q36")).assertExists()
        onNode(hasText("COUNTDOWN")).assertExists()
        onNode(hasTextEqualTo("TO MATCH START")).assertExists()
        assertEquals(40f, countdownSp)
    }

    @Test
    fun `a one-row widget is just the match and its countdown`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        setAppWidgetSize(DpSize(250.dp, 60.dp))
        provideComposable { GlanceTheme { SimpleWidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasTextEqualTo("Q36")).assertExists()
        onNode(hasText("COUNTDOWN")).assertExists()
        onNode(hasTextEqualTo("TO MATCH START")).assertDoesNotExist()
        onAllNodes(hasTestTag("phase-step")).assertCountEquals(0)
    }

    @Test
    fun `message states`() = runGlanceAppWidgetUnitTest {
        setContext(androidx.test.core.app.ApplicationProvider.getApplicationContext()) // the scoreboard colors need it
        setAppWidgetSize(DpSize(170.dp, 170.dp))
        provideComposable { GlanceTheme { SimpleWidgetContent(WidgetModels.build(com.pitwatch.core.store.EventCache(), UserConfig(), SNAP_NOW)) { } } }
        onNode(hasText("Set up PitWatch")).assertExists()
    }
}
