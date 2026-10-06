package com.pitwatch.app.widget

import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.glance.text.Text
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.snapshotCache
import com.pitwatch.core.config.UserConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Review finding I1: the widget builds its model from the stores at composition time. Glance's unit harness renders a
 * single frame (no recomposition on flow changes), so following changes within a session is verified on-device.
 */
@RunWith(RobolectricTestRunner::class)
class LiveWidgetTest {
    @Test
    fun `renders from the stores' current values`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.SMALL)
        val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
        val cache = MutableStateFlow(snapshotCache())
        provideComposable {
            GlanceTheme {
                LiveWidget(cache, MutableStateFlow(config), cache.value, config, clock = { SNAP_NOW }) { _, _ -> Text("COUNTDOWN") }
            }
        }
        onNode(hasText("Q36")).assertExists()
    }
}
