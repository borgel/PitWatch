package com.pitwatch.app.ui

import com.pitwatch.core.store.RefreshState
import java.time.Duration
import java.time.Instant

object RefreshStatusText {
    fun format(state: RefreshState, now: Instant): String = buildString {
        append("Last refresh: ")
        append(state.lastRefreshEpochMs?.let { ago(Instant.ofEpochMilli(it), now) } ?: "never")
        state.lastError?.let { append("\nError: ").append(it) }
        state.nexusLastError?.let { append("\nNexus: ").append(it) }
    }

    fun ago(then: Instant, now: Instant): String {
        val minutes = Duration.between(then, now).toMinutes()
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 24 * 60 -> "${minutes / 60}h ago"
            else -> "${minutes / (24 * 60)}d ago"
        }
    }
}
