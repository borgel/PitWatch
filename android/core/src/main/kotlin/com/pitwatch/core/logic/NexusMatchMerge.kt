package com.pitwatch.core.logic

import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch

/** Correlates Nexus matches to TBA matches: normalized label first, then alliance composition. */
object NexusMatchMerge {
    private val WHITESPACE = Regex("\\s+")

    fun nexusInfo(match: Match, nexusEvent: NexusEvent?): NexusMatch? {
        if (nexusEvent == null) return null

        val canonical = "${match.compLevel}-${match.setNumber}-${match.matchNumber}"
        nexusEvent.matches.firstOrNull { parseNexusLabel(it.label) == canonical }?.let { return it }

        val tbaRed = match.alliances["red"]?.teamKeys.orEmpty().map(::stripFrc).toSet()
        val tbaBlue = match.alliances["blue"]?.teamKeys.orEmpty().map(::stripFrc).toSet()
        if (tbaRed.isEmpty()) return null
        return nexusEvent.matches.firstOrNull { nexus ->
            val red = nexus.redTeams.toSet()
            val blue = nexus.blueTeams.toSet()
            (tbaRed == red && tbaBlue == blue) || (tbaRed == blue && tbaBlue == red)
        }
    }

    /** "Qualification 32" → "qm-1-32", "Quarterfinal 2-1" → "qf-2-1". Tolerates extra whitespace — deliberately more lenient than iOS, which keeps it and falls back to team matching. */
    private fun parseNexusLabel(label: String): String? {
        val parts = label.trim().split(WHITESPACE, limit = 2)
        if (parts.size != 2) return null
        val level = when (val raw = parts[0].lowercase()) {
            "practice" -> "p"
            "qualification" -> "qm"
            "eighthfinal" -> "ef"
            "quarterfinal" -> "qf"
            "semifinal" -> "sf"
            "final" -> "f"
            else -> raw
        }
        val number = parts[1].trim()
        if ('-' !in number) return "$level-1-$number"
        val nums = number.split('-')
        return if (nums.size == 2) "$level-${nums[0]}-${nums[1]}" else null
    }

    private fun stripFrc(key: String) = key.replace("frc", "")
}
