package com.pitwatch.app.live

import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.NexusMatchMerge
import com.pitwatch.core.logic.PhaseDerivation
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import com.pitwatch.core.store.EventCache
import java.time.Instant

/** Everything the live notification renders for one tracked match at one instant. */
data class LiveSnapshot(
    val matchKey: String,
    val matchLabel: String,
    val alliance: MatchAlliance?,
    val phase: Phase,
    /** When the current phase's countdown ends; null when unknown. */
    val deadline: Instant?,
    val milestones: Milestones,
    /** Matches between the one on the field and ours (quals only); null without a Nexus field status. */
    val matchesAway: Int?,
    val onFieldNumber: Int?,
    /** Set once the tracked match is scored. */
    val result: Result?,
) {
    data class Milestones(val queue: Instant?, val onDeck: Instant?, val onField: Instant?, val start: Instant?, val end: Instant?)

    data class Result(val ourScore: Int, val theirScore: Int) {
        val outcome: String
            get() = when {
                ourScore > theirScore -> "W"
                ourScore < theirScore -> "L"
                else -> "T"
            }
    }
}

object LiveSnapshots {
    private const val NO_FIELD = Int.MIN_VALUE

    /** Port of the content-state logic in iOS LiveActivityManager, with a real TBA fallback deadline. */
    fun build(cache: EventCache, config: UserConfig, trackedMatchKey: String, now: Instant): LiveSnapshot? {
        val teamKey = config.teamKey ?: return null
        val match = cache.matches.firstOrNull { it.key == trackedMatchKey } ?: return null
        val color = match.allianceColor(teamKey)
        val alliance = when (color) {
            "red" -> MatchAlliance.RED
            "blue" -> MatchAlliance.BLUE
            else -> null
        }
        val result = if (match.isPlayed && color != null) {
            val other = if (color == "red") "blue" else "red"
            LiveSnapshot.Result(match.alliances[color]?.score ?: 0, match.alliances[other]?.score ?: 0)
        } else {
            null
        }

        val nexusEvent = cache.nexusEvent.takeIf { config.effectiveTimeSource == TimeSource.NEXUS }
        val onField = nexusEvent
            ?.let { PhaseDerivation.currentMatchOnField(it.matches, fallbackMatchNumber = NO_FIELD) }
            ?.takeIf { it != NO_FIELD }
        val away = onField?.takeIf { match.compLevel == "qm" }?.let { match.matchNumber - it }

        val nexusMatch = NexusMatchMerge.nexusInfo(match, nexusEvent)
        if (nexusMatch != null) {
            val d = PhaseDerivation.derivePhase(nexusMatch, now)
            return LiveSnapshot(
                match.key, match.shortLabel, alliance, d.phase, d.deadline,
                LiveSnapshot.Milestones(d.queueDeadline, d.onDeckDeadline, d.onFieldDeadline, d.matchStartDeadline, d.matchEndDeadline),
                away, onField, result,
            )
        }

        // TBA fallback: count down to the match, or to queueing when a queue offset is set.
        val matchTime = if (config.useScheduledTime) {
            match.time?.let(Instant::ofEpochSecond) ?: match.matchDate()
        } else {
            match.matchDate()
        }
        return LiveSnapshot(
            match.key, match.shortLabel, alliance, Phase.PRE_QUEUE, matchTime?.minus(config.queueOffset),
            LiveSnapshot.Milestones(null, null, null, matchTime, matchTime?.plus(PhaseDerivation.MATCH_DURATION)),
            away, onField, result,
        )
    }
}
