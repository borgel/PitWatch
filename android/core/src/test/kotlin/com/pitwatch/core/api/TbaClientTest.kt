package com.pitwatch.core.api

import com.pitwatch.core.model.Team
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class TbaClientTest {
    private val teamJson = """{"key":"frc1234","team_number":1234,"nickname":"Bots"}"""
    private var captured: HttpRequestData? = null

    private fun client(status: HttpStatusCode, body: String = "", lastModified: String? = null): TbaClient {
        val engine = MockEngine { request ->
            captured = request
            val headers = if (lastModified != null) headersOf(HttpHeaders.LastModified, lastModified) else headersOf()
            respond(body, status, headers)
        }
        return TbaClient("test-key-123", HttpClient(engine), "https://example.com/api/v3")
    }

    @Test
    fun `sends auth, user agent, and If-Modified-Since to the joined URL`() = runTest {
        client(HttpStatusCode.OK, teamJson).fetch<Team>("/team/frc1234", lastModified = "Mon, 01 Jan 2026 00:00:00 GMT")
        val request = captured!!
        assertEquals("https://example.com/api/v3/team/frc1234", request.url.toString())
        assertEquals("test-key-123", request.headers["X-TBA-Auth-Key"])
        assertEquals("PitWatch", request.headers[HttpHeaders.UserAgent])
        assertEquals("Mon, 01 Jan 2026 00:00:00 GMT", request.headers[HttpHeaders.IfModifiedSince])
    }

    @Test
    fun `200 decodes and returns Last-Modified`() = runTest {
        val result = client(HttpStatusCode.OK, teamJson, lastModified = "LM").fetch<Team>("/team/frc1234")
        val data = result as? FetchResult.Data ?: error("expected Data, got $result")
        assertEquals(1234, data.value.teamNumber)
        assertEquals("LM", data.lastModified)
    }

    @Test
    fun `304 is NotModified`() = runTest {
        assertEquals(FetchResult.NotModified, client(HttpStatusCode.NotModified).fetch<Team>("/team/frc1234", "LM"))
    }

    @Test
    fun `other statuses throw with the status code`() = runTest {
        val e = assertFailsWith<TbaException> { client(HttpStatusCode.Unauthorized, "bad key").fetch<Team>("/x") }
        assertEquals(401, e.statusCode)
    }

    @Test
    fun `validateTeam returns the team`() = runTest {
        assertEquals("frc1234", client(HttpStatusCode.OK, teamJson).validateTeam(1234).key)
        assertEquals("https://example.com/api/v3/team/frc1234", captured!!.url.toString())
    }
}
