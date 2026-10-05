package com.pitwatch.core.api

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.model.Team
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.serializer

/** The Blue Alliance API v3. Send the stored Last-Modified as [lastModified] to get [FetchResult.NotModified]. */
class TbaClient(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun <T> fetch(
        deserializer: DeserializationStrategy<T>,
        path: String,
        lastModified: String? = null,
    ): FetchResult<T> {
        val response = httpClient.get(baseUrl.trimEnd('/') + path) {
            header("X-TBA-Auth-Key", apiKey)
            header(HttpHeaders.UserAgent, "PitWatch")
            lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
        }
        return when (val code = response.status.value) {
            304 -> FetchResult.NotModified
            200 -> FetchResult.Data(
                PitWatchJson.decodeFromString(deserializer, response.bodyAsText()),
                response.headers[HttpHeaders.LastModified],
            )
            else -> throw TbaException(code, "API error $code: ${response.bodyAsText()}")
        }
    }

    suspend inline fun <reified T> fetch(path: String, lastModified: String? = null): FetchResult<T> =
        fetch(serializer<T>(), path, lastModified)

    suspend fun validateTeam(number: Int): Team =
        when (val result = fetch<Team>(Endpoints.team(number))) {
            is FetchResult.Data -> result.value
            FetchResult.NotModified -> throw TbaException(304, "Got 304 on team validation")
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://www.thebluealliance.com/api/v3"
    }
}
