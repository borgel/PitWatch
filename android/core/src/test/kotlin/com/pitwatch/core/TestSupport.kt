package com.pitwatch.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Reads a fixture from the shared fixture dirs wired into test resources (see core/build.gradle.kts). */
fun fixture(path: String): String =
    requireNotNull(object {}.javaClass.getResource("/$path")) { "Missing fixture: $path" }.readText()

val LA: ZoneId = ZoneId.of("America/Los_Angeles")

fun localInstant(iso: String, zone: ZoneId): Instant = LocalDateTime.parse(iso).atZone(zone).toInstant()

val Instant.unixMs: Long get() = toEpochMilli()
