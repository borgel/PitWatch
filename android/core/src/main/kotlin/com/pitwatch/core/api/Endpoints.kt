package com.pitwatch.core.api

/** TBA API v3 paths, relative to the base URL. */
object Endpoints {
    fun team(number: Int) = "/team/frc$number"
    fun teamEvents(number: Int, year: Int) = "/team/frc$number/events/$year"
    fun event(key: String) = "/event/$key"
    fun eventMatches(key: String) = "/event/$key/matches"
    fun eventRankings(key: String) = "/event/$key/rankings"
    fun eventOprs(key: String) = "/event/$key/oprs"
    fun eventTeams(key: String) = "/event/$key/teams"
    fun match(key: String) = "/match/$key"
    const val STATUS = "/status"
}
