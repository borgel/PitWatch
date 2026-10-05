package com.pitwatch.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventRankings(
    val rankings: List<Ranking>,
    @SerialName("sort_order_info") val sortOrderInfo: List<SortOrderInfo> = emptyList(),
)

@Serializable
data class Ranking(
    @SerialName("team_key") val teamKey: String,
    val rank: Int,
    val record: WLTRecord? = null,
    @SerialName("qual_average") val qualAverage: Double? = null,
    @SerialName("matches_played") val matchesPlayed: Int,
    val dq: Int = 0,
    @SerialName("sort_orders") val sortOrders: List<Double>? = null,
)

@Serializable
data class WLTRecord(val wins: Int, val losses: Int, val ties: Int) {
    /** e.g. "5-2-0". */
    val display: String get() = "$wins-$losses-$ties"
}

@Serializable
data class SortOrderInfo(val name: String, val precision: Int)
