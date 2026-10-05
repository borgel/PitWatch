package com.pitwatch.app.ui

import com.pitwatch.app.FakeApi
import com.pitwatch.core.api.TbaClient
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SetupValidatorTest {
    private val tba = FakeApi().apply { on("/team/frc5507") { """{"key":"frc5507","team_number":5507,"nickname":"Robotic Eagles"}""" } }
    private val client: (String) -> TbaClient = { TbaClient(it, tba.client, "https://tba.test/api/v3") }

    @Test
    fun `valid key and team`() = runBlocking {
        val outcome = assertIs<SetupValidator.Outcome.Valid>(SetupValidator.validate(" key ", " 5507 ", "  ", client))
        assertEquals(5507, outcome.teamNumber)
        assertEquals("key", outcome.apiKey)
        assertEquals(null, outcome.nexusApiKey)
        assertEquals("Robotic Eagles", outcome.teamName)
    }

    @Test
    fun `local checks happen before any request`() = runBlocking {
        assertEquals("Enter your TBA API key", (SetupValidator.validate("", "5507", "", client) as SetupValidator.Outcome.Invalid).message)
        assertEquals("Enter a valid team number", (SetupValidator.validate("k", "abc", "", client) as SetupValidator.Outcome.Invalid).message)
        assertEquals("Enter a valid team number", (SetupValidator.validate("k", "0", "", client) as SetupValidator.Outcome.Invalid).message)
        assertEquals(0, tba.requests.size)
    }

    @Test
    fun `TBA rejections become readable messages`() = runBlocking {
        tba.on("/team/frc5507", HttpStatusCode.Unauthorized) { "" }
        assertEquals("TBA rejected that API key", (SetupValidator.validate("bad", "5507", "", client) as SetupValidator.Outcome.Invalid).message)
        assertEquals("Team 9999 not found", (SetupValidator.validate("k", "9999", "", client) as SetupValidator.Outcome.Invalid).message)
    }
}
