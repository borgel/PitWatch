package com.pitwatch.app.data

import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.snapshotNexus
import com.pitwatch.app.snapshotTba
import com.pitwatch.core.api.NexusClient
import com.pitwatch.core.api.TbaClient
import com.pitwatch.core.config.UserConfig
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RepositoryExtrasTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val tba = snapshotTba()
    private val nexus = snapshotNexus()
    private var changes = 0
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
            onChanged = { changes++ },
        )
        runBlocking { stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") } }
    }

    @After
    fun tearDown() = runBlocking { job.cancelAndJoin() }

    @Test
    fun `change hook fires on changed refreshes only`() = runBlocking {
        repo.refresh(SNAP_NOW)
        assertEquals(1, changes)
        repo.refresh(SNAP_NOW) // identical data
        assertEquals(1, changes)
    }

    @Test
    fun `season events come from TBA`() = runBlocking {
        assertEquals(listOf("2026cancmp"), repo.seasonEvents(SNAP_NOW).map { it.key })
    }

    @Test
    fun `season events surface TBA failures`() {
        tba.on("/team/frc5507/events/2026", HttpStatusCode.Unauthorized) { "" }
        assertFailsWith<Exception> { runBlocking { repo.seasonEvents(SNAP_NOW) } }
    }

    @Test
    fun `pit map for the cached event`() = runBlocking {
        repo.refresh(SNAP_NOW)
        assertEquals("C1", repo.pitMap()?.pit(forTeam = "5507")?.address)
    }

    @Test
    fun `no pit map without a Nexus key or event`() = runBlocking {
        assertNull(repo.pitMap()) // no event cached yet
        stores.config.updateData { it.copy(nexusApiKey = null) }
        repo.refresh(SNAP_NOW)
        assertNull(repo.pitMap())
    }

    @Test
    fun `a failing change hook never breaks the refresh`() = runBlocking {
        // Review finding m2: refresh() must not start throwing because widgets failed to update.
        val throwing = Repository(
            stores,
            { TbaClient(it, tba.client, "https://tba.test/api/v3") },
            { NexusClient(it, nexus.client, "https://nexus.test/api/v1") },
            onChanged = { error("widget host gone") },
        )
        val outcome = throwing.refresh(SNAP_NOW)
        kotlin.test.assertTrue(outcome.changed)
        assertNull(outcome.error)
    }

    @Test
    fun `refresh work runs on the work dispatcher, not the caller's thread`() = runBlocking {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "pitwatch-work") }
        try {
            var factoryThread: String? = null
            val onWork = Repository(
                stores,
                { factoryThread = Thread.currentThread().name; TbaClient(it, tba.client, "https://tba.test/api/v3") },
                { NexusClient(it, nexus.client, "https://nexus.test/api/v1") },
                workDispatcher = executor.asCoroutineDispatcher(),
            )
            onWork.refresh(SNAP_NOW)
            kotlin.test.assertTrue(factoryThread.orEmpty().startsWith("pitwatch-work"), factoryThread) // debug builds append " @coroutine#N"
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `pit map is cached per event for offline use`() = runBlocking {
        repo.refresh(SNAP_NOW)
        assertEquals("C1", repo.pitMap()?.pit(forTeam = "5507")?.address)
        nexus.on("/event/2026cancmp/map", HttpStatusCode.ServiceUnavailable) { "" }
        assertEquals("C1", repo.pitMap()?.pit(forTeam = "5507")?.address) // offline: cached copy
        stores.config.updateData { it.copy(eventKeyOverride = "2026other") }
        tba.on("/event/2026other") { com.pitwatch.app.fixture("${com.pitwatch.app.SNAP}/tba_event.json").replace("2026cancmp", "2026other") }
        repo.refresh(SNAP_NOW)
        assertNull(repo.pitMap()) // another event's map is never shown
    }
}
