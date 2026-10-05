package com.pitwatch.app.ui

import com.pitwatch.core.api.TbaClient
import com.pitwatch.core.api.TbaException
import kotlin.coroutines.cancellation.CancellationException

object SetupValidator {
    sealed interface Outcome {
        data class Valid(val teamNumber: Int, val apiKey: String, val nexusApiKey: String?, val teamName: String?) : Outcome
        data class Invalid(val message: String) : Outcome
    }

    /** Local checks first, then confirms key + team against TBA (`/team/frc{n}`). */
    suspend fun validate(apiKey: String, teamNumberText: String, nexusKey: String, client: (String) -> TbaClient): Outcome {
        val key = apiKey.trim()
        if (key.isEmpty()) return Outcome.Invalid("Enter your TBA API key")
        val team = teamNumberText.trim().toIntOrNull()?.takeIf { it > 0 } ?: return Outcome.Invalid("Enter a valid team number")
        return try {
            val found = client(key).validateTeam(team)
            Outcome.Valid(team, key, nexusKey.trim().ifEmpty { null }, found.nickname)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TbaException) {
            Outcome.Invalid(
                when (e.statusCode) {
                    401 -> "TBA rejected that API key"
                    404 -> "Team $team not found"
                    else -> "TBA error ${e.statusCode}"
                },
            )
        } catch (e: Exception) {
            Outcome.Invalid("Couldn't reach TBA: ${e.message}")
        }
    }
}
