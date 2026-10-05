package com.pitwatch.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An FRC event from The Blue Alliance API v3. */
@Serializable
data class Event(
    val key: String,
    val name: String,
    @SerialName("event_code") val eventCode: String,
    @SerialName("event_type") val eventType: Int,
    val city: String? = null,
    @SerialName("state_prov") val stateProv: String? = null,
    val country: String? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val year: Int,
    @SerialName("short_name") val shortName: String? = null,
    @SerialName("event_type_string") val eventTypeString: String? = null,
    val week: Int? = null,
    @SerialName("location_name") val locationName: String? = null,
    val timezone: String? = null,
) {
    /** The event's local zone; UTC when TBA omits it or sends an unknown id. */
    val zone: ZoneId
        get() = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC

    /** Local midnight at the start of [startDate]. */
    val startInstant: Instant?
        get() = parse(startDate)?.atStartOfDay(zone)?.toInstant()

    /** Local midnight at the start of [endDate]. */
    val endInstant: Instant?
        get() = parse(endDate)?.atStartOfDay(zone)?.toInstant()

    /**
     * True from local midnight of [startDate] until local midnight after [endDate], in the event's own
     * zone. Deliberately differs from iOS, which used UTC days and so ended west-coast events around
     * 5 PM local on their final day.
     */
    fun isActive(now: Instant): Boolean {
        val start = startInstant ?: return false
        val endExclusive = parse(endDate)?.plusDays(1)?.atStartOfDay(zone)?.toInstant() ?: return false
        return now >= start && now < endExclusive
    }

    private fun parse(date: String): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()
}
