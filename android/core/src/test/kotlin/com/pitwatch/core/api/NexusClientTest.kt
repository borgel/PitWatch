package com.pitwatch.core.api

import com.pitwatch.core.fixture
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class NexusClientTest {
    private var captured: HttpRequestData? = null

    private fun client(status: HttpStatusCode, body: String) = NexusClient(
        "nexus-test-key",
        HttpClient(MockEngine { request -> captured = request; respond(body, status) }),
        "https://example.com/api/v1",
    )

    @Test
    fun `sends key and user agent to the event URL`() = runTest {
        client(HttpStatusCode.OK, fixture("nexus_event.json")).fetchEventStatus("2026miket")
        val request = captured!!
        assertEquals("https://example.com/api/v1/event/2026miket", request.url.toString())
        assertEquals("nexus-test-key", request.headers["Nexus-Api-Key"])
        assertEquals("PitWatch", request.headers[HttpHeaders.UserAgent])
    }

    @Test
    fun `decodes event status`() = runTest {
        assertEquals("Qualification 33", client(HttpStatusCode.OK, fixture("nexus_event.json")).fetchEventStatus("e")?.nowQueuing)
    }

    @Test
    fun `fetches the pit map from the map URL`() = runTest {
        val map = client(HttpStatusCode.OK, fixture("2026cancmp/2026-04-11T00-51-22Z/nexus_map.json")).fetchPitMap("2026cancmp")
        assertEquals("https://example.com/api/v1/event/2026cancmp/map", captured!!.url.toString())
        assertEquals("A1", map?.pit(forTeam = "3598")?.address)
    }

    @Test
    fun `non-200 degrades to null`() = runTest {
        assertNull(client(HttpStatusCode.NotFound, "no such event").fetchEventStatus("e"))
    }

    @Test
    fun `garbage body with 200 degrades to null`() = runTest {
        // Review focus #5
        assertNull(client(HttpStatusCode.OK, "<html>502 Bad Gateway</html>").fetchEventStatus("e"))
        assertNull(client(HttpStatusCode.OK, """{"dataAsOfTime": 1, "matches": [""").fetchEventStatus("e"))
    }

    @OptIn(ExperimentalCoroutinesApi::class) // runCurrent
    @Test
    fun `cancellation propagates instead of degrading to null`() = runTest {
        // Review focus #1: a catch-all must not swallow CancellationException.
        val nexus = NexusClient("k", HttpClient(MockEngine { awaitCancellation() }), "https://example.com/api/v1")
        var returnedNormally = false
        val job = launch {
            nexus.fetchEventStatus("e")
            returnedNormally = true
        }
        runCurrent()
        job.cancel()
        job.join()
        assertFalse(returnedNormally)
    }

    @Test
    fun `checking a key asks for the events list and reports the status`() = runTest {
        // Setup checks the key before there's an event: /events needs the key (401 missing, 403 invalid).
        assertEquals(200, client(HttpStatusCode.OK, "{}").checkKey())
        assertEquals("https://example.com/api/v1/events", captured?.url.toString())
        assertEquals("nexus-test-key", captured?.headers?.get("Nexus-Api-Key"))
        assertEquals(403, client(HttpStatusCode.Forbidden, "").checkKey())
        assertEquals(401, client(HttpStatusCode.Unauthorized, "").checkKey())
    }

    @Test
    fun `checking a key lets a network failure through, unlike the data calls`() = runTest {
        val offline = NexusClient("k", HttpClient(MockEngine { throw java.net.UnknownHostException("frc.nexus") }), "https://example.com/api/v1")
        kotlin.test.assertFailsWith<java.io.IOException> { offline.checkKey() }
    }
}
