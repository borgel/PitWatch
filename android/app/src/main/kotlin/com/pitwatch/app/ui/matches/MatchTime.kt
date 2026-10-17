package com.pitwatch.app.ui.matches

import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusMatch
import java.time.Instant

/** The time a match row shows, and whether it's an estimate ("~"). Shared by the Matches screen and the widgets. */
data class MatchTime(val time: Instant?, val estimated: Boolean) {
    companion object {
        /** Nexus start when correlated; else the scheduled or best-known TBA time, per the user's choice. */
        fun of(match: Match, nexusMatch: NexusMatch?, config: UserConfig): MatchTime {
            val nexusStart = nexusMatch?.times?.startDate
            val time = nexusStart ?: if (config.useScheduledTime) {
                match.time?.let(Instant::ofEpochSecond) ?: match.matchDate()
            } else {
                match.matchDate()
            }
            val estimated = nexusStart != null || (!config.useScheduledTime && match.predictedTime != null && match.actualTime == null)
            return MatchTime(time, estimated)
        }
    }
}
