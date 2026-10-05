package com.pitwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EventOPRs(
    val oprs: Map<String, Double>,
    val dprs: Map<String, Double>,
    val ccwms: Map<String, Double>,
) {
    /** Sum of OPRs for [teamKeys]; null if any team is missing (early in an event). */
    fun summedOpr(teamKeys: List<String>): Double? {
        var total = 0.0
        for (key in teamKeys) total += oprs[key] ?: return null
        return total
    }
}
