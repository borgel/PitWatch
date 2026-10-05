package com.pitwatch.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Team(
    val key: String,
    @SerialName("team_number") val teamNumber: Int,
    val name: String? = null,
    val nickname: String? = null,
    val city: String? = null,
    @SerialName("state_prov") val stateProv: String? = null,
    val country: String? = null,
    val website: String? = null,
    @SerialName("rookie_year") val rookieYear: Int? = null,
)
