package com.pitwatch.core.config

/** TBA event keys look like "2026cancmp": a 4-digit year, then lowercase letters/digits. */
object EventKeys {
    private val PATTERN = Regex("^\\d{4}[a-z0-9]+$")

    fun isValid(key: String): Boolean = PATTERN.matches(key)
}
