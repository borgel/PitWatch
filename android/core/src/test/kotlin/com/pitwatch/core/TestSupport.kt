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

fun testMatch(
    number: Int,
    compLevel: String = "qm",
    setNumber: Int = 1,
    time: Long? = 1_712_000_000,
    predictedTime: Long? = null,
    actualTime: Long? = null,
    red: List<String> = listOf("frc1234", "frc5678", "frc9012"),
    blue: List<String> = listOf("frc3456", "frc7890", "frc1111"),
    redScore: Int = -1,
    blueScore: Int = -1,
    eventKey: String = "2026test",
): com.pitwatch.core.model.Match = com.pitwatch.core.model.Match(
    key = "${eventKey}_$compLevel$number",
    compLevel = compLevel,
    setNumber = setNumber,
    matchNumber = number,
    eventKey = eventKey,
    time = time,
    predictedTime = predictedTime,
    actualTime = actualTime,
    alliances = mapOf(
        "red" to com.pitwatch.core.model.Alliance(score = redScore, teamKeys = red),
        "blue" to com.pitwatch.core.model.Alliance(score = blueScore, teamKeys = blue),
    ),
    winningAlliance = "",
    videos = emptyList(),
)

fun testEvent(
    key: String = "2026cancmp",
    startDate: String = "2026-04-09",
    endDate: String = "2026-04-12",
    timezone: String? = "America/Los_Angeles",
): com.pitwatch.core.model.Event = com.pitwatch.core.model.Event(
    key = key,
    name = "Test Event $key",
    eventCode = key.drop(4),
    eventType = 2,
    startDate = startDate,
    endDate = endDate,
    year = startDate.take(4).toInt(),
    timezone = timezone,
)
