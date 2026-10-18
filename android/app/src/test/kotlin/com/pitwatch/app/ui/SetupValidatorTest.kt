package com.pitwatch.app.ui

import com.pitwatch.app.FakeApi
import com.pitwatch.core.api.NexusClient
import com.pitwatch.core.api.TbaClient
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SetupValidatorTest {
    private val tba = FakeApi().apply { on("/team/frc5507") { """{"key":"frc5507","team_number":5507,"nickname":"Robotic Eagles"}""" } }
    private val client: (String) -> TbaClient = { TbaClient(it, tba.client, "https://tba.test/api/v3") }
    private val nexusApi = FakeApi().apply { on("/events") { "{}" } }
    private val nexus: (String) -> NexusClient = { NexusClient(it, nexusApi.client, "https://nexus.test/api/v1") }

    private suspend fun check(key: String, team: String, nexusKey: String) = SetupValidator.validate(key, team, nexusKey, client, nexus)
    private suspend fun invalid(key: String, team: String, nexusKey: String = "") = (check(key, team, nexusKey) as SetupValidator.Outcome.Invalid).message

    @Test
    fun `valid key and team`() = runBlocking<Unit> {
        val outcome = assertIs<SetupValidator.Outcome.Valid>(check(" key ", " 5507 ", "  "))
        assertEquals(5507, outcome.teamNumber)
        assertEquals("key", outcome.apiKey)
        assertEquals(null, outcome.nexusApiKey)
        assertEquals("Robotic Eagles", outcome.teamName)
        assertEquals(0, nexusApi.requests.size, "no Nexus key, no Nexus check")
    }

    @Test
    fun `local checks happen before any request`() = runBlocking<Unit> {
        assertEquals("Enter your TBA API key", invalid("", "5507", ""))
        assertEquals("Enter a valid team number", invalid("k", "abc", ""))
        assertEquals("Enter a valid team number", invalid("k", "0", ""))
        assertEquals(0, tba.requests.size)
    }

    @Test
    fun `TBA rejections become readable messages`() = runBlocking<Unit> {
        tba.on("/team/frc5507", HttpStatusCode.Unauthorized) { "" }
        assertEquals("TBA rejected that API key", invalid("bad", "5507", ""))
        assertEquals("Team 9999 not found", invalid("k", "9999", ""))
    }

    @Test
    fun `TBA unreachable - say so plainly and offer to continue with what was entered`() = runBlocking<Unit> {
        // Asked for: no way past setup without reaching TBA (e.g. venue Wi-Fi); a failure also read "...: null".
        tba.on("/team/frc5507") { throw java.net.UnknownHostException() }
        val outcome = assertIs<SetupValidator.Outcome.Unreachable>(check(" key ", "5507", " nk "))
        assertEquals(SetupValidator.NO_CONNECTION, outcome.message)
        assertEquals(SetupValidator.Outcome.Valid(5507, "key", "nk", null), outcome.unverified)
        assertEquals(0, nexusApi.requests.size)
        tba.on("/team/frc5507") { throw java.net.SocketTimeoutException() }
        assertIs<SetupValidator.Outcome.Unreachable>(check("key", "5507", ""))
    }

    @Test
    fun `a Nexus key is checked too, and a rejected one says so`() = runBlocking<Unit> {
        // Asked for: a mistyped Nexus key passed setup silently.
        assertEquals("nk", assertIs<SetupValidator.Outcome.Valid>(check("key", "5507", " nk ")).nexusApiKey)
        assertEquals("nk", nexusApi.requests.single().headers["Nexus-Api-Key"])
        nexusApi.on("/events", HttpStatusCode.Forbidden) { "" }
        assertEquals("FRC Nexus rejected that API key", invalid("key", "5507", "bad"))
        nexusApi.on("/events", HttpStatusCode.Unauthorized) { "" }
        assertEquals("FRC Nexus rejected that API key", invalid("key", "5507", "bad"))
    }

    @Test
    fun `a Nexus server problem doesn't block setup - it isn't the key's fault`() = runBlocking<Unit> {
        nexusApi.on("/events", HttpStatusCode.InternalServerError) { "" }
        assertIs<SetupValidator.Outcome.Valid>(check("key", "5507", "nk"))
        nexusApi.on("/events", HttpStatusCode.NotFound) { "" }
        assertIs<SetupValidator.Outcome.Valid>(check("key", "5507", "nk"))
    }

    @Test
    fun `Nexus unreachable - offer to continue too`() = runBlocking<Unit> {
        nexusApi.on("/events") { throw java.net.UnknownHostException() }
        val outcome = assertIs<SetupValidator.Outcome.Unreachable>(check("key", "5507", "nk"))
        assertEquals(SetupValidator.NEXUS_NO_CONNECTION, outcome.message)
        assertEquals(SetupValidator.Outcome.Valid(5507, "key", "nk", "Robotic Eagles"), outcome.unverified)
    }
}
