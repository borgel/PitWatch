package com.pitwatch.app.widget

import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.ScheduleDelay
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.store.EventCache
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class WidgetModelTest {
    private val cache = snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json")))
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private fun build(c: EventCache = cache, cfg: UserConfig = config, now: Instant = SNAP_NOW) = WidgetModels.build(c, cfg, now, Locale.US, com.pitwatch.app.LA)

    @Test
    fun `ready - header, next match, what follows, last result`() {
        val m = build()
        assertEquals(WidgetModel.State.READY, m.state)
        assertEquals("5507 · #34 · 1-2-0", m.header)
        assertEquals("Q36", m.next?.shortLabel)
        // What follows the next match, grouped by day (the next match's own day keeps only what's after it).
        assertEquals(listOf("Friday, Apr 10", "Saturday, Apr 11", "Sunday, Apr 12"), m.laterDays.map { it.label })
        val later = m.laterDays.flatMap { it.items }.take(2).map {
            when (it) {
                is MatchListModel.Item.Upcoming -> it.row.shortLabel
                is MatchListModel.Item.Break -> it.title
            }
        }
        assertEquals(listOf("End of day", "Q43"), later)
        assertEquals("Q22", m.last?.shortLabel)
        assertEquals(Instant.ofEpochMilli(1775868749195), m.countdownDeadline) // the match start
    }

    @Test
    fun `ready - how far ahead or behind the event is running`() {
        val expected = ScheduleDelay.text(kotlin.test.assertNotNull(ScheduleDelay.of(cache.matches, cache.nexusEvent)))
        assertEquals(expected, build().scheduleDelay)
        assertEquals("24m behind", build().scheduleDelay) // the snapshot: Nexus has Q36 ~24 minutes past its slot
        // TBA time source: the delay comes from TBA's own prediction, not Nexus.
        val tba = config.copy(timeSource = com.pitwatch.core.config.TimeSource.TBA)
        assertEquals(ScheduleDelay.of(cache.matches, null)?.let(ScheduleDelay::text), build(cfg = tba).scheduleDelay)
    }

    @Test
    fun `ready - the live card's stage times and footer, so widget and card agree`() {
        // Asked for: the widgets match the scoreboard card's design.
        val live = kotlin.test.assertNotNull(com.pitwatch.app.live.LiveSnapshots.build(cache, config, "2026cancmp_qm36", SNAP_NOW))
        val m = build()
        assertEquals(listOf(live.milestones.queue, live.milestones.onDeck, live.milestones.onField, live.milestones.start), m.stageTimes)
        assertEquals(com.pitwatch.app.live.LiveCard.footer(live), m.footer)
        assertEquals("NOW · FIELD #36 · 24M BEHIND", m.footer)
    }

    @Test
    fun `no countdown once the deadline has passed`() {
        // Review focus #4: never show a negative chronometer.
        assertNull(build(now = SNAP_NOW.plusSeconds(3600)).countdownDeadline)
    }

    @Test
    fun `not configured`() {
        val m = build(cfg = UserConfig())
        assertEquals(WidgetModel.State.NOT_CONFIGURED, m.state)
        assertEquals("Set up PitWatch", m.message)
    }

    @Test
    fun `no event yet`() {
        assertEquals("No event yet", build(c = EventCache()).message)
    }

    @Test
    fun `upcoming event without matches shows when it starts`() {
        val m = build(c = EventCache(event = cache.event), now = Instant.parse("2026-04-01T12:00:00Z"))
        assertEquals(WidgetModel.State.NO_UPCOMING, m.state)
        assertEquals("Next event: California Northern · Apr 9", m.message)
    }

    @Test
    fun `finished schedule shows the last result`() {
        val played = cache.matches.filter { it.isPlayed }
        val m = build(c = cache.copy(matches = played))
        assertEquals("No upcoming matches", m.message)
        assertEquals("Q22", m.last?.shortLabel)
        // Asked for: once matches end, the team's whole history, most recent first.
        assertEquals(listOf("Q22", "Q14", "Q1"), m.results.map { it.shortLabel })
        assertEquals(m.results, com.pitwatch.app.widget.ScheduleWidgetModels.build(cache.copy(matches = played), config, SNAP_NOW, Locale.US, com.pitwatch.app.LA).results)
    }

    @Test
    fun `upcoming lines fit the budget and never orphan a day header`() {
        // Found on-device: day headers used up the rows and "Wednesday" showed with nothing under it.
        val days = build().laterDays
        fun names(lines: List<WidgetLine>) = lines.map {
            when (it) {
                is WidgetLine.Header -> "# ${it.label}"
                is WidgetLine.Entry -> when (val item = it.item) {
                    is MatchListModel.Item.Upcoming -> item.row.shortLabel
                    is MatchListModel.Item.Break -> item.title
                }
            }
        }
        assertEquals(listOf("# Friday, Apr 10", "End of day"), names(WidgetLines.fit(days, budget = 3)))
        assertEquals(listOf("# Friday, Apr 10", "End of day", "# Saturday, Apr 11", "Q43"), names(WidgetLines.fit(days, budget = 4)))
        assertEquals(emptyList(), WidgetLines.fit(days, budget = 1))
        // Glance renders at most 10 children per Column (found on-device: the tail was silently dropped).
        kotlin.test.assertTrue(WidgetLines.fit(days, budget = 50).size <= WidgetLines.MAX_LINES)
        assertEquals(9, WidgetLines.MAX_LINES)
    }

    @Test
    fun `team split puts our team in the middle`() {
        fun line(vararg t: String) = com.pitwatch.app.ui.matches.MatchListModel.AllianceLine(t.map { com.pitwatch.app.ui.matches.MatchListModel.TeamChip(it, it == "5507") }, null)
        assertEquals(Triple("4698 · ", "5507", " · 1678"), TeamSplit.of(line("4698", "5507", "1678")))
        assertEquals(Triple("", "5507", " · 1678"), TeamSplit.of(line("5507", "1678")))
        assertEquals(Triple("6036 · 9470 · 6814", null, ""), TeamSplit.of(line("6036", "9470", "6814")))
    }

    private fun plan(h: Int, w: Int = 360, tall: Boolean = h >= 250, countdown: Boolean = true) = WidgetPlans.plan(
        h.dp, w.dp, tall = tall, hasCountdown = countdown, hasTarget = countdown, hasLast = true, hasFooter = true,
    )

    @Test
    fun `a full 4x3 widget draws the whole scoreboard and the list`() {
        // Asked for: the widget in the scoreboard card's design. 360×344 dp is a 4×3 on emulator-5580.
        val p = plan(344)
        kotlin.test.assertTrue(p.header && p.phaseBar && p.target && p.alliances && p.footer && p.lastLine)
        assertEquals(StageLabels.TIMES, p.stageLabels)
        kotlin.test.assertTrue(p.listLines >= 1)
        kotlin.test.assertFalse(p.largeCountdown)
    }

    @Test
    fun `a 4x2 keeps both alliances and the footer`() {
        // On-device: at 4×2 the blue alliance line was once cut off at the bottom.
        val p = plan(223, tall = false)
        kotlin.test.assertTrue(p.header && p.alliances && p.phaseBar && p.footer)
        assertEquals(StageLabels.TIMES, p.stageLabels)
        assertEquals(0, p.listLines)
    }

    @Test
    fun `wide widgets keep the record in the top-right corner at no cost`() {
        // Asked for: the record stays at the top right, as before; the countdown sits under it.
        kotlin.test.assertTrue(plan(110, tall = false).header)
        kotlin.test.assertFalse(plan(110, w = 170, tall = false).header)
    }

    @Test
    fun `narrow widgets name the stages without times, and add the start time and record as height allows`() {
        val p = plan(344, w = 172)
        assertEquals(StageLabels.NAMES, p.stageLabels)
        kotlin.test.assertTrue(p.header && p.time && p.footer)
        kotlin.test.assertFalse(p.alliances)
        // On-device: one line cut the footer to "24M BEH…" at 2 cells wide; narrow widgets give it two.
        assertEquals(2, p.footerLines)
        assertEquals(1, plan(344).footerLines)
    }

    @Test
    fun `the smallest widget keeps only the match and its countdown`() {
        val p = plan(110, w = 110, tall = false)
        kotlin.test.assertFalse(p.header || p.phaseBar || p.target || p.alliances || p.time || p.footer)
        assertEquals(StageLabels.NONE, p.stageLabels)
    }

    @Test
    fun `without a countdown the start time comes first`() {
        kotlin.test.assertTrue(plan(140, w = 170, tall = false, countdown = false).time)
    }

    @Test
    fun `a very tall widget gets the big countdown and a capped list`() {
        val p = plan(600)
        kotlin.test.assertTrue(p.largeCountdown)
        kotlin.test.assertTrue(p.listLines >= WidgetLines.MAX_LINES)
    }

    @Test
    fun `the simple widget fills its top with the match, countdown and phase - big when there's room`() {
        // Asked for: a simplified widget, its top filled by the next match number, main time and phase name.
        val roomy = SimplePlans.plan(223.dp, 360.dp, hasCountdown = true, hasPhase = true)
        kotlin.test.assertTrue(roomy.big && roomy.phaseBar && roomy.target && roomy.phase)
        assertEquals(StageLabels.TIMES, roomy.stageLabels)
        val twoByTwo = SimplePlans.plan(170.dp, 170.dp, hasCountdown = true, hasPhase = true)
        kotlin.test.assertFalse(twoByTwo.big)
        kotlin.test.assertTrue(twoByTwo.target)
        val oneRow = SimplePlans.plan(60.dp, 250.dp, hasCountdown = true, hasPhase = true)
        kotlin.test.assertTrue(oneRow.singleRow)
        kotlin.test.assertFalse(oneRow.phaseBar || oneRow.target)
    }

    @Test
    fun `the stage bar shows before Nexus reports a phase - empty, with its stage times`() {
        // Found on-device: SF2-1 had no phase yet, and both widgets dropped the bar, leaving them mostly blank.
        val simple = SimplePlans.plan(223.dp, 360.dp, hasCountdown = false, hasPhase = false)
        kotlin.test.assertTrue(simple.phaseBar)
        assertEquals(StageLabels.TIMES, simple.stageLabels)
        val main = WidgetPlans.plan(
            440.dp, 360.dp, tall = true, hasCountdown = false, hasTarget = false, hasLast = true, hasFooter = true,
        )
        kotlin.test.assertTrue(main.phaseBar)
        assertEquals(StageLabels.TIMES, main.stageLabels)
    }
}
