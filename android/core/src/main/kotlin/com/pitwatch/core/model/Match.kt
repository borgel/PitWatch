package com.pitwatch.core.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** An FRC match from The Blue Alliance API v3. Times are Unix seconds. */
@Serializable
data class Match(
    val key: String,
    @SerialName("comp_level") val compLevel: String,
    @SerialName("set_number") val setNumber: Int,
    @SerialName("match_number") val matchNumber: Int,
    @SerialName("event_key") val eventKey: String,
    val time: Long? = null,
    @SerialName("predicted_time") val predictedTime: Long? = null,
    @SerialName("actual_time") val actualTime: Long? = null,
    val alliances: Map<String, Alliance>,
    @SerialName("winning_alliance") val winningAlliance: String,
    @SerialName("score_breakdown") val scoreBreakdown: JsonObject? = null,
    val videos: List<Video> = emptyList(),
) {
    /** e.g. "Qual 32", "QF 2-1", "SF 1-3", "Final 1". */
    val label: String
        get() = when (compLevel) {
            "qm" -> "Qual $matchNumber"
            "qf" -> "QF $setNumber-$matchNumber"
            "sf" -> "SF $setNumber-$matchNumber"
            "f" -> "Final $matchNumber"
            else -> "${compLevel.uppercase()} $matchNumber"
        }

    /** e.g. "Q32", "QF2-1", "SF1-3", "F1". */
    val shortLabel: String
        get() = when (compLevel) {
            "qm" -> "Q$matchNumber"
            "qf" -> "QF$setNumber-$matchNumber"
            "sf" -> "SF$setNumber-$matchNumber"
            "f" -> "F$matchNumber"
            else -> "${compLevel.uppercase()}$matchNumber"
        }

    /** Played = has an actual time and at least one non-negative score. */
    val isPlayed: Boolean
        get() = actualTime != null && alliances.values.any { it.score >= 0 }

    /** Chronological sort key: comp level, then set, then match number. */
    val sortOrder: Int
        get() {
            val level = when (compLevel) {
                "qm" -> 0
                "ef" -> 1
                "qf" -> 2
                "sf" -> 3
                "f" -> 4
                else -> 5
            }
            return level * 1_000_000 + setNumber * 1_000 + matchNumber
        }

    /** Actual, then predicted, then (if [useScheduled]) scheduled time. */
    fun matchDate(useScheduled: Boolean = true): Instant? =
        actualTime?.let(Instant::ofEpochSecond)
            ?: predictedTime?.let(Instant::ofEpochSecond)
            ?: if (useScheduled) time?.let(Instant::ofEpochSecond) else null

    /** "red", "blue", or null. */
    fun allianceColor(teamKey: String): String? =
        alliances.entries.firstOrNull { teamKey in it.value.teamKeys }?.key
}

@Serializable
data class Alliance(
    val score: Int,
    @SerialName("team_keys") val teamKeys: List<String>,
    @SerialName("surrogate_team_keys") val surrogateTeamKeys: List<String> = emptyList(),
    @SerialName("dq_team_keys") val dqTeamKeys: List<String> = emptyList(),
)

@Serializable
data class Video(val type: String, val key: String)
