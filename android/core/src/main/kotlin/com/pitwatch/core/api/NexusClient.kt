package com.pitwatch.core.api

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.PitMap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.DeserializationStrategy

/** FRC Nexus API. Every failure degrades to null so Nexus can never block TBA data. */
class NexusClient(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun fetchEventStatus(eventKey: String): NexusEvent? =
        getOrNull("/event/$eventKey", NexusEvent.serializer())

    suspend fun fetchPitMap(eventKey: String): PitMap? =
        getOrNull("/event/$eventKey/map", PitMap.serializer())

    private suspend fun <T> getOrNull(path: String, deserializer: DeserializationStrategy<T>): T? =
        try {
            val response = httpClient.get(baseUrl.trimEnd('/') + path) {
                header("Nexus-Api-Key", apiKey)
                header(HttpHeaders.UserAgent, "PitWatch")
            }
            if (response.status != HttpStatusCode.OK) null
            else PitWatchJson.decodeFromString(deserializer, response.bodyAsText())
        } catch (e: CancellationException) {
            throw e // never swallow cancellation: the live service must be able to stop a poll
        } catch (e: Exception) {
            null
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://frc.nexus/api/v1"
    }
}
