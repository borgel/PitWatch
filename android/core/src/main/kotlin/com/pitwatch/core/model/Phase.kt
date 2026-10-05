package com.pitwatch.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where the tracked team is in the queue → field pipeline. Colors live in the app layer. */
@Serializable
enum class Phase {
    PRE_QUEUE, QUEUEING, ON_DECK, ON_FIELD;

    /** What is happening right now. */
    val stateLabel: String
        get() = when (this) {
            PRE_QUEUE -> "UPCOMING"
            QUEUEING -> "IN QUEUE"
            ON_DECK -> "ON DECK"
            ON_FIELD -> "ON FIELD"
        }

    /** What happens when the countdown hits zero. */
    val targetLabel: String
        get() = when (this) {
            PRE_QUEUE -> "QUEUE STARTS"
            QUEUEING -> "MOVE TO DECK"
            ON_DECK -> "MOVE TO FIELD"
            ON_FIELD -> "MATCH ENDS"
        }

    /** Single-letter glyph for compact surfaces (status-bar chip). */
    val glyph: String
        get() = when (this) {
            PRE_QUEUE -> "U"
            QUEUEING -> "Q"
            ON_DECK -> "D"
            ON_FIELD -> "F"
        }

    /** Lowercase name of the next phase, for "to on deck" subtitles; null for ON_FIELD. */
    val nextPhaseProse: String?
        get() = when (this) {
            PRE_QUEUE -> "queue"
            QUEUEING -> "on deck"
            ON_DECK -> "on field"
            ON_FIELD -> null
        }
}

@Serializable
enum class MatchAlliance {
    @SerialName("blue") BLUE,
    @SerialName("red") RED;

    val displayName: String get() = name
}

object MatchesAwayDisplay {
    fun text(gap: Int): String = when {
        gap <= 0 -> "NOW"
        gap == 1 -> "NEXT"
        else -> "$gap AWAY"
    }
}
