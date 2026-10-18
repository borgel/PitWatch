package com.pitwatch.app.data

import kotlinx.serialization.Serializable

/** App-side notification and preview settings (not part of :core's UserConfig). */
@Serializable
data class NotificationPrefs(
    /** The opt-in schedule notification. */
    val scheduleEnabled: Boolean = false,
    /** Re-post notifications when swiped away; they stop only from their own actions. */
    val pinned: Boolean = false,
    /** Live tracking as a scoreboard card beside a short Live Update; off: one Live Update with a progress bar. */
    val richLive: Boolean = true,
    /** Install (package last-update time) whose widget-picker previews were last registered. */
    val previewsVersion: Long = 0,
)
