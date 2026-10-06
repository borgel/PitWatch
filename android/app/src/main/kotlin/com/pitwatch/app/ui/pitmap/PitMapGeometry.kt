package com.pitwatch.app.ui.pitmap

import androidx.compose.ui.geometry.Rect
import com.pitwatch.core.model.PitMap

object PitMapGeometry {
    /**
     * Nexus positions are box **centers** (the real cancmp map's areas tile exactly that way). The iOS app read
     * them as top-left corners, which made neighboring areas overlap.
     */
    fun rect(position: PitMap.Position, size: PitMap.MapSize): Rect {
        val halfW = (size.x / 2).toFloat()
        val halfH = (size.y / 2).toFloat()
        return Rect(position.x.toFloat() - halfW, position.y.toFloat() - halfH, position.x.toFloat() + halfW, position.y.toFloat() + halfH)
    }

    /** Scale that fits the whole map into a width × height viewport. */
    fun fitScale(map: PitMap, width: Float, height: Float): Float =
        minOf(width / map.size.x.toFloat(), height / map.size.y.toFloat())

    /** Our pit, if the team has one on this map. */
    fun focus(map: PitMap, team: String?): PitMap.AssignedPit? = team?.let { map.pit(forTeam = it) }

    /** Approximate glyph width as a fraction of font size (digits and Latin text). */
    const val CHAR_WIDTH_EM = 0.6f

    /**
     * Font size, in map units, for [text] to fit inside [box]: at most 40% of its height and 90% of its
     * width. Text is drawn inside the zoomed map transform, so it scales with the map, never past its box.
     */
    fun labelFontPx(box: Rect, text: String): Float {
        val byWidth = box.width * 0.9f / (CHAR_WIDTH_EM * text.length.coerceAtLeast(1))
        return minOf(box.height * 0.4f, byWidth)
    }
}
