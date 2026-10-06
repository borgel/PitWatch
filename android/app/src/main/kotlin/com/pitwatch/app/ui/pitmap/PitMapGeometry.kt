package com.pitwatch.app.ui.pitmap

import androidx.compose.ui.geometry.Rect
import com.pitwatch.core.model.PitMap

object PitMapGeometry {
    /** Nexus positions are treated as top-left corners (as the iOS app does). Change here if that proves wrong. */
    fun rect(position: PitMap.Position, size: PitMap.MapSize): Rect =
        Rect(position.x.toFloat(), position.y.toFloat(), (position.x + size.x).toFloat(), (position.y + size.y).toFloat())

    /** Scale that fits the whole map into a width × height viewport. */
    fun fitScale(map: PitMap, width: Float, height: Float): Float =
        minOf(width / map.size.x.toFloat(), height / map.size.y.toFloat())

    /** Our pit, if the team has one on this map. */
    fun focus(map: PitMap, team: String?): PitMap.AssignedPit? = team?.let { map.pit(forTeam = it) }
}
