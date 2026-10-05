package com.pitwatch.core

import kotlinx.serialization.json.Json

/**
 * Shared JSON config. Mirrors Swift Codable: unknown keys ignored, absent optionals decode as null, and
 * every non-null field is encoded (so persisted settings survive a future change of default). An explicit
 * JSON null in a non-null field with a default decodes as that default.
 */
val PitWatchJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}
