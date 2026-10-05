package com.pitwatch.app.ui

import com.pitwatch.core.store.RefreshState
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Test

class RefreshStatusTextTest {
    private val now = Instant.ofEpochSecond(1_800_000_000)

    @Test
    fun `relative ages`() {
        assertEquals("just now", RefreshStatusText.ago(now.minusSeconds(30), now))
        assertEquals("5m ago", RefreshStatusText.ago(now.minusSeconds(300), now))
        assertEquals("2h ago", RefreshStatusText.ago(now.minusSeconds(7300), now))
        assertEquals("3d ago", RefreshStatusText.ago(now.minusSeconds(3 * 86400 + 5), now))
    }

    @Test
    fun `status includes errors when present`() {
        assertEquals("Last refresh: never", RefreshStatusText.format(RefreshState(), now))
        val state = RefreshState(lastRefreshEpochMs = now.minusSeconds(300).toEpochMilli(), lastError = "API error 401", nexusLastError = "Nexus data unavailable")
        assertEquals("Last refresh: 5m ago\nError: API error 401\nNexus: Nexus data unavailable", RefreshStatusText.format(state, now))
    }
}
