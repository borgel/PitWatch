package com.pitwatch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.pitwatch.app.live.LiveMatchService
import com.pitwatch.app.schedule.AutoStartPlanner
import com.pitwatch.app.schedule.RefreshWorker
import com.pitwatch.app.ui.PitWatchRoot
import com.pitwatch.app.ui.theme.PitWatchTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PitWatchApp).container
        RefreshWorker.ensureScheduled(this)
        // Opening the app inside the live window starts tracking (unless the user stopped this match).
        container.scope.launch {
            val now = container.clock()
            val start = AutoStartPlanner.nextStart(
                container.stores.cache.data.first(), container.stores.config.data.first(),
                container.stores.liveControl.data.first(), now,
            )
            if (start != null && start <= now) LiveMatchService.start(this@MainActivity)
        }
        setContent {
            PitWatchTheme {
                Surface { PitWatchRoot(container) }
            }
        }
    }
}
