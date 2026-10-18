package com.pitwatch.app.ui

import com.pitwatch.core.api.NexusClient
import com.pitwatch.core.api.TbaClient
import com.pitwatch.core.api.TbaException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

object SetupValidator {
    const val NO_CONNECTION = "Couldn't reach TBA. Check your connection and try again."
    const val NEXUS_NO_CONNECTION = "Couldn't reach FRC Nexus to check its key. Check your connection and try again."

    sealed interface Outcome {
        data class Valid(val teamNumber: Int, val apiKey: String, val nexusApiKey: String?, val teamName: String?) : Outcome
        data class Invalid(val message: String) : Outcome
        /** Couldn't check (no connection): setup may still continue with [unverified]; the first refresh checks it. */
        data class Unreachable(val message: String, val unverified: Valid) : Outcome
    }

    /**
     * Local checks first, then confirms key + team against TBA (`/team/frc{n}`), then a Nexus key if one was entered
     * (`/events`). A rejected key stops setup; not reaching a server only offers to continue unchecked.
     */
    suspend fun validate(
        apiKey: String,
        teamNumberText: String,
        nexusKey: String,
        client: (String) -> TbaClient,
        nexus: (String) -> NexusClient,
    ): Outcome {
        val key = apiKey.trim()
        if (key.isEmpty()) return Outcome.Invalid("Enter your TBA API key")
        val team = teamNumberText.trim().toIntOrNull()?.takeIf { it > 0 } ?: return Outcome.Invalid("Enter a valid team number")
        val nexusApiKey = nexusKey.trim().ifEmpty { null }
        val teamName = try {
            client(key).validateTeam(team).nickname
        } catch (e: CancellationException) {
            throw e
        } catch (e: TbaException) {
            return Outcome.Invalid(
                when (e.statusCode) {
                    401 -> "TBA rejected that API key"
                    404 -> "Team $team not found"
                    else -> "TBA error ${e.statusCode}"
                },
            )
        } catch (e: IOException) { // no network, DNS, timeouts (ktor's timeout is an IOException too)
            return Outcome.Unreachable(NO_CONNECTION, Outcome.Valid(team, key, nexusApiKey, null))
        } catch (e: Exception) {
            return Outcome.Invalid("Couldn't check with TBA (${e.message ?: e::class.simpleName}). Try again.")
        }
        val valid = Outcome.Valid(team, key, nexusApiKey, teamName)
        if (nexusApiKey == null) return valid
        return try {
            // 401/403 is the key; any other status is Nexus's own trouble, which shouldn't block setup.
            if (nexus(nexusApiKey).checkKey() in setOf(401, 403)) Outcome.Invalid("FRC Nexus rejected that API key") else valid
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Outcome.Unreachable(NEXUS_NO_CONNECTION, valid)
        } catch (e: Exception) {
            valid
        }
    }
}
