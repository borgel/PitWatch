package com.pitwatch.app.live

import com.pitwatch.core.logic.NexusMatchMerge
import com.pitwatch.core.logic.PhaseDerivation
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import java.time.Duration
import java.time.Instant

/** Matches TBA never scored: well past their best-known start, we stop waiting on them. */
object StaleMatches {
    /** Generous on purpose: events often run late, and giving up early would drop a match that's still coming. */
    val GRACE: Duration = Duration.ofHours(1)

    fun isStale(match: Match, nexusEvent: NexusEvent?, now: Instant): Boolean {
        if (match.isPlayed) return false
        val start = NexusMatchMerge.nexusInfo(match, nexusEvent)?.times?.startDate ?: match.matchDate() ?: return false
        return now > start.plus(PhaseDerivation.MATCH_DURATION).plus(GRACE)
    }

    fun drop(matches: List<Match>, nexusEvent: NexusEvent?, now: Instant): List<Match> =
        matches.filterNot { isStale(it, nexusEvent, now) }
}
