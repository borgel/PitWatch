package com.pitwatch.core.config

import java.time.Duration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserConfig(
    val teamNumber: Int? = null,
    val apiKey: String? = null,
    val eventKeyOverride: String? = null,
    val useScheduledTime: Boolean = false,
    val queueOffsetMinutes: Int = 0,
    val liveActivityMode: LiveActivityMode = LiveActivityMode.NEAR_MATCH,
    val nexusApiKey: String? = null,
    val timeSource: TimeSource? = null,
) {
    /** Explicit choice, else Nexus if a Nexus key is configured, else TBA. */
    val effectiveTimeSource: TimeSource
        get() = timeSource ?: if (isNexusConfigured) TimeSource.NEXUS else TimeSource.TBA

    val isConfigured: Boolean get() = teamNumber != null && !apiKey.isNullOrEmpty()
    val isNexusConfigured: Boolean get() = !nexusApiKey.isNullOrEmpty()
    val teamKey: String? get() = teamNumber?.let { "frc$it" }
    val queueOffset: Duration get() = Duration.ofMinutes(queueOffsetMinutes.toLong())
}

@Serializable
enum class LiveActivityMode {
    @SerialName("nearMatch") NEAR_MATCH,
    @SerialName("allDay") ALL_DAY,
}

@Serializable
enum class TimeSource {
    @SerialName("nexus") NEXUS,
    @SerialName("tba") TBA,
}
