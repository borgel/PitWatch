package com.pitwatch.core.store

import com.pitwatch.core.model.Event
import com.pitwatch.core.model.EventOPRs
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.Team
import kotlinx.serialization.Serializable

/** Everything fetched for the active event. Persisted by the app layer (Plan 2). */
@Serializable
data class EventCache(
    val event: Event? = null,
    val matches: List<Match> = emptyList(),
    val rankings: EventRankings? = null,
    val oprs: EventOPRs? = null,
    val teams: List<Team> = emptyList(),
    val nexusEvent: NexusEvent? = null,
)

@Serializable
data class RefreshState(
    val lastRefreshEpochMs: Long? = null,
    val lastModifiedHeaders: Map<String, String> = emptyMap(),
    val lastError: String? = null,
    val nexusLastRefreshEpochMs: Long? = null,
    val nexusLastError: String? = null,
    /** Device time of the last successful Nexus poll (the blip grace window is measured from this). */
    val nexusLastSuccessEpochMs: Long? = null,
) {
    fun lastModified(path: String): String? = lastModifiedHeaders[path]

    /** Records [value] for [path]; a null value (e.g. a 304) keeps the previous header. */
    fun withLastModified(value: String?, path: String): RefreshState =
        if (value == null) this else copy(lastModifiedHeaders = lastModifiedHeaders + (path to value))
}
