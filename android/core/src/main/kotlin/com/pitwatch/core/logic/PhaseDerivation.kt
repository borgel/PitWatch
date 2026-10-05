package com.pitwatch.core.logic

import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.Phase
import java.time.Duration
import java.time.Instant

object PhaseDerivation {
    val MATCH_DURATION: Duration = Duration.ofSeconds(150)

    data class Result(
        val phase: Phase,
        val deadline: Instant?,
        val phaseStart: Instant,
        val queueDeadline: Instant?,
        val onDeckDeadline: Instant?,
        val onFieldDeadline: Instant?,
        val matchStartDeadline: Instant?,
        val matchEndDeadline: Instant?,
    )

    /**
     * Current phase and countdown deadline. Nexus's discrete status wins over times, except statuses
     * containing "soon" ("Queuing soon", "On deck soon"), which mean the phase hasn't started.
     */
    fun derivePhase(nexusMatch: NexusMatch, now: Instant): Result {
        val times = nexusMatch.times
        val matchEnd = times.startDate?.plus(MATCH_DURATION)

        fun result(phase: Phase, deadline: Instant?, phaseStart: Instant) = Result(
            phase, deadline, phaseStart,
            queueDeadline = times.queueDate,
            onDeckDeadline = times.onDeckDate,
            onFieldDeadline = times.onFieldDate,
            matchStartDeadline = times.startDate,
            matchEndDeadline = matchEnd,
        )

        val status = nexusMatch.status?.lowercase()
        if (status != null && "soon" !in status) {
            if ("field" in status || "playing" in status) {
                return result(Phase.ON_FIELD, matchEnd, times.onFieldDate ?: now)
            }
            if ("deck" in status) {
                return result(Phase.ON_DECK, times.onFieldDate, times.onDeckDate ?: now)
            }
            if ("queu" in status) { // "queuing" or "queue"
                return result(Phase.QUEUEING, times.onDeckDate, times.queueDate ?: now)
            }
        }

        times.onFieldDate?.takeIf { it <= now }?.let { return result(Phase.ON_FIELD, matchEnd, it) }
        times.onDeckDate?.takeIf { it <= now }?.let { return result(Phase.ON_DECK, times.onFieldDate, it) }
        times.queueDate?.takeIf { it <= now }?.let { return result(Phase.QUEUEING, times.onDeckDate, it) }
        return result(Phase.PRE_QUEUE, times.queueDate, now)
    }

    /** Phase of [match] via its correlated Nexus match; null when uncorrelated. */
    fun phaseFor(match: Match, nexusEvent: NexusEvent?, now: Instant): Phase? =
        NexusMatchMerge.nexusInfo(match, nexusEvent)?.let { derivePhase(it, now).phase }

    /** Match number currently on the field, from the last Nexus match whose status says so. */
    fun currentMatchOnField(matches: List<NexusMatch>, fallbackMatchNumber: Int): Int =
        matches.lastOrNull { m ->
            val s = m.status?.lowercase() ?: return@lastOrNull false
            "field" in s || "playing" in s
        }?.let { extractMatchNumber(it.label) } ?: fallbackMatchNumber

    /** "Qualification 42" → 42. */
    fun extractMatchNumber(label: String): Int? =
        label.split(' ').filter { it.isNotEmpty() }.lastOrNull()?.toIntOrNull()
}
