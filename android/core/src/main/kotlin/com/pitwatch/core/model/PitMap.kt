package com.pitwatch.core.model

import kotlinx.serialization.Serializable

/** FRC Nexus `GET /event/{eventKey}/map`. */
@Serializable
data class PitMap(
    val size: MapSize,
    val pits: Map<String, Pit>,
    val areas: Map<String, Area>? = null,
    val labels: Map<String, MapLabel>? = null,
    val arrows: Map<String, Arrow>? = null,
    val walls: Map<String, Wall>? = null,
) {
    @Serializable data class MapSize(val x: Double, val y: Double)
    @Serializable data class Position(val x: Double, val y: Double)
    @Serializable data class Pit(val position: Position, val size: MapSize, val team: String? = null)
    @Serializable data class Area(val label: String, val position: Position, val size: MapSize)
    @Serializable data class MapLabel(val label: String, val position: Position, val size: MapSize)
    @Serializable data class Arrow(val position: Position, val size: MapSize, val type: String? = null, val angle: Double? = null)
    @Serializable data class Wall(val position: Position, val size: MapSize)

    data class AssignedPit(val address: String, val pit: Pit)

    /** The pit assigned to [forTeam] (bare team number, e.g. "1700"). */
    fun pit(forTeam: String): AssignedPit? =
        pits.entries.firstOrNull { it.value.team == forTeam }?.let { AssignedPit(it.key, it.value) }
}
