package com.pitwatch.app.data

import com.pitwatch.core.api.Endpoints
import com.pitwatch.core.api.FetchResult
import com.pitwatch.core.api.NexusClient
import com.pitwatch.core.api.TbaClient
import com.pitwatch.core.config.EventKeys
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.ChangeDetector
import com.pitwatch.core.logic.EventSelection
import com.pitwatch.core.model.Event
import com.pitwatch.core.model.EventOPRs
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.PitMap
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable

data class RefreshOutcome(val changed: Boolean, val error: String?, val nexusError: String? = null)

/** The only fetch path. Port of iOS BackgroundRefresh.performRefresh. */
class Repository(
    private val stores: Stores,
    private val tbaClient: (apiKey: String) -> TbaClient,
    private val nexusClient: (apiKey: String) -> NexusClient,
    private val onChanged: suspend () -> Unit = {},
) {
    private val mutex = Mutex()

    val cache: Flow<EventCache> get() = stores.cache.data

    /**
     * TBA endpoints use If-Modified-Since unless [force]. With [includeTba] false only Nexus is polled
     * (the live notification's fast path). Serialized: the worker and the live service may overlap.
     * [onChanged] (widgets) runs after any refresh that changed what we show.
     */
    suspend fun refresh(now: Instant, force: Boolean = false, includeTba: Boolean = true): RefreshOutcome {
        val outcome = refreshLocked(now, force, includeTba)
        if (outcome.changed) {
            try {
                onChanged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Widgets failing to update must not turn a good refresh into an error.
            }
        }
        return outcome
    }

    private suspend fun refreshLocked(now: Instant, force: Boolean, includeTba: Boolean): RefreshOutcome = mutex.withLock {
        val config = stores.config.data.first()
        val apiKey = config.apiKey
        if (!config.isConfigured || apiKey == null) return@withLock RefreshOutcome(changed = false, error = NOT_CONFIGURED)

        val old = stores.cache.data.first()
        var state = stores.refreshState.data.first()
        try {
            var cache = old
            var tbaError: String? = null
            // A failed TBA call is remembered so the refresh reports it, while the data that did arrive is kept.
            suspend fun <T> fetchRecording(block: suspend () -> T): T? =
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (tbaError == null) tbaError = e.message ?: e::class.simpleName ?: "TBA request failed"
                    null
                }
            if (includeTba) {
                val client = tbaClient(apiKey)
                val resolved = resolveEvent(config, old.event, client, now)
                    ?: return@withLock RefreshOutcome(changed = false, error = null) // no events this season
                if (old.event?.key != resolved.key) cache = EventCache() // switching events: start clean
                val event = resolved.event ?: cache.event
                    ?: fetchRecording { (client.fetch<Event>(Endpoints.event(resolved.key)) as? FetchResult.Data)?.value }
                cache = cache.copy(event = event)

                // Conditional GET only when we already hold the data a 304 would refer to.
                suspend fun <T> conditional(path: String, deserializer: DeserializationStrategy<T>, have: Boolean): T? {
                    val lastModified = if (force || !have) null else state.lastModified(path)
                    val result = fetchRecording { client.fetch(deserializer, path, lastModified) } as? FetchResult.Data ?: return null
                    state = state.withLastModified(result.lastModified, path)
                    return result.value
                }

                val key = resolved.key
                conditional(Endpoints.eventMatches(key), ListSerializer(Match.serializer()), cache.matches.isNotEmpty())
                    ?.let { cache = cache.copy(matches = it) }
                // TBA returns a literal `null` body for rankings/OPRs early in an event: keep what we had.
                conditional(Endpoints.eventRankings(key), EventRankings.serializer().nullable, cache.rankings != null)
                    ?.let { cache = cache.copy(rankings = it) }
                conditional(Endpoints.eventOprs(key), EventOPRs.serializer().nullable, cache.oprs != null)
                    ?.let { cache = cache.copy(oprs = it) }
                state = if (tbaError == null) {
                    state.copy(lastRefreshEpochMs = now.toEpochMilli(), lastError = null)
                } else {
                    state.copy(lastError = tbaError)
                }
            }

            var nexusError: String? = null
            val eventKey = cache.event?.key ?: config.eventKeyOverride?.takeIf(EventKeys::isValid)
            val nexusKey = config.nexusApiKey
            if (!nexusKey.isNullOrEmpty() && eventKey != null) {
                val fresh = nexusClient(nexusKey).fetchEventStatus(eventKey)
                if (fresh == null) nexusError = NEXUS_UNAVAILABLE
                // A short blip keeps recent data so the live view doesn't flap to TBA times.
                val kept = fresh ?: cache.nexusEvent?.takeIf { now.toEpochMilli() - it.dataAsOfTime < NEXUS_STALE_AFTER.toMillis() }
                cache = cache.copy(nexusEvent = kept)
                state = state.copy(nexusLastRefreshEpochMs = now.toEpochMilli(), nexusLastError = nexusError)
            } else {
                cache = cache.copy(nexusEvent = null)
            }

            val finalCache = cache
            val finalState = state
            stores.cache.updateData { finalCache }
            stores.refreshState.updateData { finalState }
            val changed = force || old.event != cache.event || old.nexusEvent != cache.nexusEvent ||
                ChangeDetector.detect(old, cache, config.teamKey.orEmpty()).shouldReloadWidgets
            RefreshOutcome(changed, error = tbaError, nexusError = nexusError)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e::class.simpleName ?: "Refresh failed"
            stores.refreshState.updateData { it.copy(lastError = message) }
            RefreshOutcome(changed = false, error = message)
        }
    }

    /** The team's events this season, for the event picker. Throws on TBA failure. */
    suspend fun seasonEvents(now: Instant): List<Event> {
        val config = stores.config.data.first()
        val apiKey = config.apiKey ?: return emptyList()
        val team = config.teamNumber ?: return emptyList()
        val result = tbaClient(apiKey).fetch<List<Event>>(Endpoints.teamEvents(team, now.atZone(ZoneOffset.UTC).year))
        return (result as? FetchResult.Data)?.value.orEmpty().sortedBy { it.startDate }
    }

    /** Nexus pit map for the cached event; null without a Nexus key, an event, or a map. */
    suspend fun pitMap(): PitMap? {
        val nexusKey = stores.config.data.first().nexusApiKey?.takeIf { it.isNotEmpty() } ?: return null
        val eventKey = stores.cache.data.first().event?.key ?: return null
        return nexusClient(nexusKey).fetchPitMap(eventKey)
    }

    private data class ResolvedEvent(val key: String, val event: Event?)

    private suspend fun resolveEvent(config: UserConfig, cached: Event?, client: TbaClient, now: Instant): ResolvedEvent? {
        config.eventKeyOverride?.takeIf(EventKeys::isValid)?.let { key ->
            return ResolvedEvent(key, cached?.takeIf { it.key == key })
        }
        // Deliberate divergence from iOS, which kept the first detected event forever: re-detect once it's over.
        if (cached != null && (cached.isActive(now) || (cached.startInstant ?: Instant.MIN) > now)) {
            return ResolvedEvent(cached.key, cached)
        }
        val team = config.teamNumber ?: return null
        val year = now.atZone(ZoneOffset.UTC).year
        val events = when (val result = client.fetch<List<Event>>(Endpoints.teamEvents(team, year))) {
            is FetchResult.Data -> result.value
            FetchResult.NotModified -> return cached?.let { ResolvedEvent(it.key, it) }
        }
        return EventSelection.autoDetect(events, now)?.let { ResolvedEvent(it.key, it) }
    }

    companion object {
        const val NOT_CONFIGURED = "Not configured"
        const val NEXUS_UNAVAILABLE = "Nexus data unavailable"
        val NEXUS_STALE_AFTER: Duration = Duration.ofMinutes(10)
    }
}
