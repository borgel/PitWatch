package com.pitwatch.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/** FRC Nexus `GET /event/{eventKey}`. Timestamps are Unix milliseconds. */
@Serializable
data class NexusEvent(
    val dataAsOfTime: Long,
    val nowQueuing: String? = null,
    val matches: List<NexusMatch>,
)

@Serializable
data class NexusMatch(
    val label: String,
    val status: String? = null,
    @Serializable(with = NullDroppingStringListSerializer::class) val redTeams: List<String> = emptyList(),
    @Serializable(with = NullDroppingStringListSerializer::class) val blueTeams: List<String> = emptyList(),
    val times: NexusMatchTimes = NexusMatchTimes(),
    val replayOf: String? = null,
    /** Nexus's explicit break marker after this match, e.g. "Lunch", "End of day". Not yet used by logic. */
    val breakAfter: String? = null,
)

@Serializable
data class NexusMatchTimes(
    val estimatedQueueTime: Long? = null,
    val estimatedOnDeckTime: Long? = null,
    val estimatedOnFieldTime: Long? = null,
    val estimatedStartTime: Long? = null,
    val actualQueueTime: Long? = null,
) {
    val queueDate: Instant? get() = estimatedQueueTime?.let(Instant::ofEpochMilli)
    val onDeckDate: Instant? get() = estimatedOnDeckTime?.let(Instant::ofEpochMilli)
    val onFieldDate: Instant? get() = estimatedOnFieldTime?.let(Instant::ofEpochMilli)
    val startDate: Instant? get() = estimatedStartTime?.let(Instant::ofEpochMilli)
    val actualQueueDate: Instant? get() = actualQueueTime?.let(Instant::ofEpochMilli)

    /** First phase strictly after [after], in order queue → on deck → on field → start. */
    fun nextPhaseDate(after: Instant): PhaseTime? =
        listOf("Queue" to queueDate, "On Deck" to onDeckDate, "On Field" to onFieldDate, "Start" to startDate)
            .firstOrNull { (_, date) -> date != null && date > after }
            ?.let { (label, date) -> PhaseTime(label, date!!) }
}

data class PhaseTime(val label: String, val date: Instant)
