package com.pitwatch.core

import kotlinx.serialization.json.Json

/** Shared JSON config. Mirrors Swift Codable: unknown keys ignored, absent optionals decode as null. */
val PitWatchJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
