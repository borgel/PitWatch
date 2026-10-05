package com.pitwatch.core.logic

import com.pitwatch.core.model.Match
import com.pitwatch.core.store.EventCache
import kotlin.math.abs

enum class ChangeReason { SCORE_CHANGED, PREDICTED_TIME_SHIFTED, RANK_CHANGED, ALLIANCE_CHANGED }

data class ChangeResult(val reasons: Set<ChangeReason>) {
    val shouldReloadWidgets: Boolean get() = reasons.isNotEmpty()
}

/** Did anything widget-visible change for [teamKey] between two caches? */
object ChangeDetector {
    fun detect(old: EventCache, new: EventCache, teamKey: String): ChangeResult {
        val reasons = mutableSetOf<ChangeReason>()
        val oldByKey = old.matches.associateBy { it.key }

        for (match in new.matches) {
            if (match.alliances.values.none { teamKey in it.teamKeys }) continue
            val oldMatch = oldByKey[match.key]
            if (oldMatch == null) {
                reasons += ChangeReason.SCORE_CHANGED // new match appeared
                continue
            }
            if (match.isPlayed != oldMatch.isPlayed) {
                reasons += ChangeReason.SCORE_CHANGED
            } else if (match.isPlayed && (score(match, "red") != score(oldMatch, "red") || score(match, "blue") != score(oldMatch, "blue"))) {
                reasons += ChangeReason.SCORE_CHANGED
            }
            val newPredicted = match.predictedTime
            val oldPredicted = oldMatch.predictedTime
            if (newPredicted != null && oldPredicted != null && abs(newPredicted - oldPredicted) > 300) {
                reasons += ChangeReason.PREDICTED_TIME_SHIFTED
            }
            if (teams(match) != teams(oldMatch)) reasons += ChangeReason.ALLIANCE_CHANGED
        }

        val newRank = new.rankings?.rankings?.firstOrNull { it.teamKey == teamKey }
        val oldRank = old.rankings?.rankings?.firstOrNull { it.teamKey == teamKey }
        if (newRank != null && oldRank != null) {
            if (newRank.rank != oldRank.rank || newRank.record != oldRank.record || newRank.matchesPlayed != oldRank.matchesPlayed) {
                reasons += ChangeReason.RANK_CHANGED
            }
        } else if ((new.rankings != null) != (old.rankings != null)) {
            reasons += ChangeReason.RANK_CHANGED
        }

        return ChangeResult(reasons)
    }

    private fun score(match: Match, color: String) = match.alliances[color]?.score ?: -1

    private fun teams(match: Match): Set<String> =
        (match.alliances["red"]?.teamKeys.orEmpty() + match.alliances["blue"]?.teamKeys.orEmpty()).toSet()
}
