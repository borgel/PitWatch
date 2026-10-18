package com.pitwatch.app.widget

import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Fixed sample content for the widget picker's generated previews. */
object SampleWidgetData {
    private const val HEADER = "5507 · #34 · 1-2-0"

    private fun line(vararg teams: String, opr: Double? = null) =
        MatchListModel.AllianceLine(teams.map { MatchListModel.TeamChip(it, it == "5507") }, opr)

    private fun row(n: Int, at: Instant, alliance: MatchAlliance, phase: Phase?, next: Boolean) = MatchListModel.MatchRow(
        key = "sample_qm$n", label = "Qual $n", shortLabel = "Q$n", time = at, estimated = true, alliance = alliance, phase = phase,
        red = line("4698", "5507", "1678", opr = 351.0), blue = line("6036", "9470", "6814", opr = 539.0), isNext = next,
        countdown = if (next) MatchListModel.Countdown(at.plus(Duration.ofMinutes(4)), "to match end") else null,
    )

    private val last = MatchListModel.Result(
        "sample_qm22", "Qual 22", "Q22", 403, 299, "W", line("9400", "6418", "5104"), line("5507", "2813", "8033"), 299, 403,
    )

    private fun today(now: Instant): MatchListModel.Day = MatchListModel.Day(
        now.atZone(ZoneId.systemDefault()).toLocalDate(), "Today",
        listOf(
            MatchListModel.Item.Upcoming(row(36, now.plus(Duration.ofMinutes(2)), MatchAlliance.RED, Phase.ON_FIELD, next = true)),
            MatchListModel.Item.Upcoming(row(43, now.plus(Duration.ofMinutes(74)), MatchAlliance.RED, null, next = false)),
            MatchListModel.Item.Break("Lunch", now.plus(Duration.ofMinutes(110)), now.plus(Duration.ofMinutes(170))),
            MatchListModel.Item.Upcoming(row(52, now.plus(Duration.ofMinutes(190)), MatchAlliance.BLUE, null, next = false)),
        ),
    )

    fun main(now: Instant): WidgetModel {
        val day = today(now)
        val next = (day.items[0] as MatchListModel.Item.Upcoming).row
        return WidgetModel(
            WidgetModel.State.READY, HEADER, "California Northern", next,
            listOf(day.copy(items = day.items.drop(1))), last, null, next.countdown?.deadline, scheduleDelay = "6m behind",
        )
    }

    fun schedule(now: Instant): ScheduleWidgetModel = ScheduleWidgetModel(WidgetModel.State.READY, HEADER, listOf(today(now)), last, null)

    private fun eventRow(n: Int, at: Instant?, red: List<String>, blue: List<String>, score: Pair<Int, Int>? = null) = EventMatchRow(
        "sample_qm$n", "Q$n", at, estimated = at != null, line(*red.toTypedArray()), line(*blue.toTypedArray()),
        ours = when ("5507") { in red -> MatchAlliance.RED; in blue -> MatchAlliance.BLUE; else -> null },
        redScore = score?.first, blueScore = score?.second,
    )

    fun eventSchedule(now: Instant): EventScheduleModel {
        fun at(minutes: Long) = now.plus(Duration.ofMinutes(minutes))
        return EventScheduleModel(
            WidgetModel.State.READY, "California Northern",
            listOf(
                EventScheduleLine.Header("Today"),
                EventScheduleLine.Upcoming(eventRow(36, at(2), listOf("4698", "5507", "1678"), listOf("6036", "9470", "6814"))),
                EventScheduleLine.Upcoming(eventRow(37, at(9), listOf("254", "971", "8033"), listOf("2813", "649", "7419"))),
                EventScheduleLine.Upcoming(eventRow(38, at(16), listOf("1868", "6418", "5104"), listOf("9400", "4255", "199"))),
                EventScheduleLine.Upcoming(eventRow(39, at(23), listOf("3256", "2135", "253"), listOf("5507", "841", "100"))),
                EventScheduleLine.Header(EventScheduleModels.RESULTS),
                EventScheduleLine.Played(eventRow(35, null, listOf("2135", "253", "7419"), listOf("1678", "254", "604"), 216 to 401)),
                EventScheduleLine.Played(eventRow(34, null, listOf("971", "649", "8033"), listOf("6814", "4698", "199"), 288 to 263)),
            ),
            null,
        )
    }
}
