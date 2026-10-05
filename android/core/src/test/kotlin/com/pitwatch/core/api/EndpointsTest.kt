package com.pitwatch.core.api

import kotlin.test.Test
import kotlin.test.assertEquals

class EndpointsTest {
    @Test
    fun `endpoint paths`() {
        assertEquals("/team/frc1234", Endpoints.team(1234))
        assertEquals("/team/frc1234/events/2026", Endpoints.teamEvents(1234, 2026))
        assertEquals("/event/2026miket", Endpoints.event("2026miket"))
        assertEquals("/event/2026miket/matches", Endpoints.eventMatches("2026miket"))
        assertEquals("/event/2026miket/rankings", Endpoints.eventRankings("2026miket"))
        assertEquals("/event/2026miket/oprs", Endpoints.eventOprs("2026miket"))
        assertEquals("/event/2026miket/teams", Endpoints.eventTeams("2026miket"))
        assertEquals("/match/2026miket_qm32", Endpoints.match("2026miket_qm32"))
        assertEquals("/status", Endpoints.STATUS)
    }
}
