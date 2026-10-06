package com.pitwatch.app.ui

/** What each API key powers; shown under the fields on Setup and Settings. */
object ApiKeyHelp {
    const val TBA = "Required. Match schedule, times, scores, rankings and alliances. " +
        "Get a read key from your account page on thebluealliance.com."
    const val NEXUS = "Optional, recommended at events. Live queue status (queuing → on deck → on field), more accurate times, " +
        "breaks, the pit map and live tracking's phase timeline. Without it PitWatch uses TBA's estimated times."
}
