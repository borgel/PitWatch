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
            listOf(day.copy(items = day.items.drop(1))), last, null, next.countdown?.deadline,
        )
    }

    fun schedule(now: Instant): ScheduleWidgetModel = ScheduleWidgetModel(WidgetModel.State.READY, HEADER, listOf(today(now)), last, null)
}
