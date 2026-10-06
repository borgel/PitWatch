package com.pitwatch.app.data

import com.pitwatch.app.FakeApi
import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotNexus
import com.pitwatch.app.snapshotTba
import com.pitwatch.core.api.NexusClient
import com.pitwatch.core.api.TbaClient
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = SNAP_NOW
    private val tba = snapshotTba()
    private val nexus = snapshotNexus()
    private lateinit var job: Job
    private lateinit var stores: Stores
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        job = SupervisorJob()
        stores = Stores(tmp.root, CoroutineScope(Dispatchers.IO + job))
        repo = Repository(
            stores,
            { TbaClient(it, tba.client, "https://tba.test/api/v3") },
            { NexusClient(it, nexus.client, "https://nexus.test/api/v1") },
        )
    }

    @After
    fun tearDown() = runBlocking { job.cancelAndJoin() }

    private suspend fun configure(config: UserConfig = UserConfig(teamNumber = 5507, apiKey = "tba-key", nexusApiKey = "nexus-key")) {
        stores.config.updateData { config }
    }

    private suspend fun cache() = stores.cache.data.first()

    @Test
    fun `not configured makes no requests`() = runBlocking {
        val outcome = repo.refresh(now)
        assertEquals(Repository.NOT_CONFIGURED, outcome.error)
        assertTrue(tba.requests.isEmpty())
    }

    @Test
    fun `first refresh auto-detects the event and fills the cache`() = runBlocking {
        configure()
        val outcome = repo.refresh(now)
        assertNull(outcome.error)
        assertTrue(outcome.changed)
        val cache = cache()
        assertEquals("2026cancmp", cache.event?.key)
        assertTrue(cache.matches.isNotEmpty())
        assertNotNull(cache.rankings)
        assertNotNull(cache.oprs)
        assertNotNull(cache.nexusEvent)
        val state = stores.refreshState.data.first()
        assertEquals(now.toEpochMilli(), state.lastRefreshEpochMs)
        assertNull(state.lastError)
    }

    @Test
    fun `second refresh sends If-Modified-Since, keeps data on 304, and does not re-detect`() = runBlocking {
        configure()
        repo.refresh(now)
        tba.on("/event/2026cancmp/matches", HttpStatusCode.NotModified) { "" }
        tba.requests.clear()
        val outcome = repo.refresh(now.plusSeconds(60))
        assertNull(outcome.error)
        val matchesRequest = tba.requests.single { it.url.encodedPath.endsWith("/matches") }
        assertEquals("LM-/api/v3/event/2026cancmp/matches", matchesRequest.headers[HttpHeaders.IfModifiedSince])
        assertTrue(cache().matches.isNotEmpty())
        assertTrue(tba.requests.none { "/team/" in it.url.encodedPath })
    }

    @Test
    fun `force skips If-Modified-Since`() = runBlocking {
        configure()
        repo.refresh(now)
        tba.requests.clear()
        repo.refresh(now, force = true)
        assertTrue(tba.requests.all { it.headers[HttpHeaders.IfModifiedSince] == null })
    }

    @Test
    fun `literal null rankings body keeps the other data`() = runBlocking {
        tba.on("/event/2026cancmp/rankings") { "null" }
        configure()
        val outcome = repo.refresh(now)
        assertNull(outcome.error)
        assertNull(cache().rankings)
        assertTrue(cache().matches.isNotEmpty())
    }

    @Test
    fun `event list failure is recorded and leaves the cache alone`() = runBlocking {
        tba.on("/team/frc5507/events/2026", HttpStatusCode.InternalServerError) { "boom" }
        configure()
        val outcome = repo.refresh(now)
        assertNotNull(outcome.error)
        assertEquals(EventCache(), cache())
        assertEquals(outcome.error, stores.refreshState.data.first().lastError)
    }

    @Test
    fun `nexus blip keeps recent nexus data and reports it`() = runBlocking {
        configure()
        repo.refresh(now)
        nexus.on("/event/2026cancmp", HttpStatusCode.BadGateway) { "<html>" }
        val outcome = repo.refresh(now.plusSeconds(60))
        assertNull(outcome.error)
        assertEquals(Repository.NEXUS_UNAVAILABLE, outcome.nexusError)
        assertNotNull(cache().nexusEvent)
        assertEquals(Repository.NEXUS_UNAVAILABLE, stores.refreshState.data.first().nexusLastError)
    }

    @Test
    fun `nexus data older than ten minutes is dropped on failure`() = runBlocking {
        configure()
        repo.refresh(now)
        nexus.on("/event/2026cancmp", HttpStatusCode.BadGateway) { "<html>" }
        repo.refresh(now.plusSeconds(11 * 60))
        assertNull(cache().nexusEvent)
    }

    @Test
    fun `switching the override clears the old event`() = runBlocking {
        configure()
        repo.refresh(now)
        tba.on("/event/2026other") { fixture("$SNAP/tba_event.json").replace("2026cancmp", "2026other") }
        stores.config.updateData { it.copy(eventKeyOverride = "2026other") }
        repo.refresh(now)
        assertEquals("2026other", cache().event?.key)
        assertTrue(cache().matches.isEmpty())
    }

    @Test
    fun `invalid override is ignored`() = runBlocking {
        configure(UserConfig(teamNumber = 5507, apiKey = "k", eventKeyOverride = "2026ca?x=1#f"))
        repo.refresh(now)
        assertEquals("2026cancmp", cache().event?.key)
        assertTrue(tba.requests.none { it.url.parameters.names().isNotEmpty() })
    }

    @Test
    fun `nexus-only refresh makes no TBA requests`() = runBlocking {
        configure()
        repo.refresh(now)
        tba.requests.clear()
        nexus.requests.clear()
        repo.refresh(now.plusSeconds(30), includeTba = false)
        assertTrue(tba.requests.isEmpty())
        assertEquals(1, nexus.requests.size)
    }

    @Test
    fun `ended event is re-detected`() = runBlocking {
        configure()
        repo.refresh(now)
        tba.requests.clear()
        repo.refresh(Instant.parse("2026-05-01T00:00:00Z"))
        assertTrue(tba.requests.any { it.url.encodedPath.endsWith("/team/frc5507/events/2026") })
    }

    @Test
    fun `concurrent refreshes are serialized`() = runBlocking {
        configure()
        tba.delayMs = 20
        coroutineScope { repeat(3) { launch(Dispatchers.IO) { repo.refresh(now) } } }
        assertEquals(1, tba.maxInFlight)
    }

    @Test
    fun `TBA endpoint failure is reported while partial data is kept`() = runBlocking {
        configure()
        repo.refresh(now)
        tba.on("/event/2026cancmp/matches", HttpStatusCode.InternalServerError) { "boom" }
        val outcome = repo.refresh(now.plusSeconds(60))
        assertNotNull(outcome.error)
        val state = stores.refreshState.data.first()
        assertEquals(outcome.error, state.lastError)
        assertEquals(now.toEpochMilli(), state.lastRefreshEpochMs) // not advanced by a failed refresh
        assertTrue(cache().matches.isNotEmpty())
    }

    @Test
    fun `unchanged refresh does not rewrite the cache file`() = runBlocking {
        configure()
        repo.refresh(now)
        val file = java.io.File(tmp.root, "event_cache.json")
        val before = file.lastModified()
        Thread.sleep(1100) // filesystem mtime resolution
        repo.refresh(now)
        assertEquals(before, file.lastModified())
    }

    @Test
    fun `nexus grace window uses our clock, not the server's`() = runBlocking {
        // The snapshot's dataAsOfTime is a day old here; a blip right after a successful poll must still keep the data.
        val later = now.plusSeconds(24 * 3600)
        configure()
        repo.refresh(later)
        nexus.on("/event/2026cancmp", HttpStatusCode.BadGateway) { "<html>" }
        repo.refresh(later.plusSeconds(60))
        assertNotNull(cache().nexusEvent)
        assertEquals(later.toEpochMilli(), stores.refreshState.data.first().nexusLastSuccessEpochMs)
    }
}
