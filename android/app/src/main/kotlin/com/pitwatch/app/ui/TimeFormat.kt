package com.pitwatch.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Clock times in the display zone, suffixed with [label] (e.g. "JST") when the phone isn't in the event's zone.
 * Built per model, so a zone change (travel) is picked up on the next rebuild rather than fixed at class load.
 */
class TimeFormat(zone: ZoneId, private val label: String?) {
    private val clock: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone)
    private val suffix: String get() = label?.let { " $it" }.orEmpty()

    fun clock(time: Instant): String = clock.format(time)

    /** "~9:52 AM JST"; "~" marks an estimate. */
    fun match(time: Instant?, estimated: Boolean): String =
        time?.let { (if (estimated) "~" else "") + clock(it) + suffix } ?: "Time TBD"

    fun range(start: Instant, end: Instant): String = "${clock(start)} – ${clock(end)}$suffix"
}

val LocalTimeFormat = staticCompositionLocalOf { TimeFormat(ZoneId.systemDefault(), null) }
