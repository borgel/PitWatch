package com.pitwatch.app

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.model.Alliance
import com.pitwatch.core.model.Event
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.store.EventCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay

// Duplicated from :core's TestSupport (accepted in the plan's Global Constraints).

const val SNAP = "2026cancmp/2026-04-11T00-51-22Z"

/** Nexus dataAsOfTime of [SNAP]: mid-event; team 5507 (red) is on the field for qm36, starting 65 s later. */
val SNAP_NOW: Instant = Instant.ofEpochMilli(1775868683705)

val LA: ZoneId = ZoneId.of("America/Los_Angeles")

fun fixture(path: String): String =
    requireNotNull(object {}.javaClass.getResource("/$path")) { "Missing fixture: $path" }.readText()

fun localInstant(iso: String, zone: ZoneId): Instant = LocalDateTime.parse(iso).atZone(zone).toInstant()

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
): Match = Match(
    key = "${eventKey}_$compLevel$number",
    compLevel = compLevel,
    setNumber = setNumber,
    matchNumber = number,
    eventKey = eventKey,
    time = time,
    predictedTime = predictedTime,
    actualTime = actualTime,
    alliances = mapOf(
        "red" to Alliance(score = redScore, teamKeys = red),
        "blue" to Alliance(score = blueScore, teamKeys = blue),
    ),
    winningAlliance = "",
)

fun testEvent(
    key: String = "2026test",
    startDate: String = "2026-04-09",
    endDate: String = "2026-04-12",
    timezone: String? = "America/Los_Angeles",
): Event = Event(
    key = key, name = "Test $key", eventCode = key.drop(4), eventType = 2,
    startDate = startDate, endDate = endDate, year = startDate.take(4).toInt(), timezone = timezone,
)

/** The [SNAP] event, matches and Nexus status as a cache. */
fun snapshotCache(): EventCache = EventCache(
    event = PitWatchJson.decodeFromString<Event>(fixture("$SNAP/tba_event.json")),
    matches = PitWatchJson.decodeFromString<List<Match>>(fixture("$SNAP/tba_matches.json")),
    nexusEvent = PitWatchJson.decodeFromString<NexusEvent>(fixture("$SNAP/nexus_event.json")),
)

/** A MockEngine-backed HttpClient that routes by URL path suffix and records every request. */
class FakeApi {
    private val routes = mutableMapOf<String, () -> Pair<HttpStatusCode, String>>()
    private val lock = Any()
    val requests: MutableList<HttpRequestData> = java.util.Collections.synchronizedList(mutableListOf())
    @Volatile var delayMs = 0L
    private var inFlight = 0
    @Volatile var maxInFlight = 0
        private set

    fun on(pathSuffix: String, status: HttpStatusCode = HttpStatusCode.OK, body: () -> String) {
        routes[pathSuffix] = { status to body() }
    }

    val client = HttpClient(
        MockEngine { request ->
            synchronized(lock) {
                requests += request
                inFlight++
                maxInFlight = maxOf(maxInFlight, inFlight)
            }
            try {
                if (delayMs > 0) delay(delayMs)
                val route = routes.entries.firstOrNull { request.url.encodedPath.endsWith(it.key) }?.value
                val (status, body) = route?.invoke() ?: (HttpStatusCode.NotFound to "")
                respond(body, status, headersOf(HttpHeaders.LastModified, "LM-${request.url.encodedPath}"))
            } finally {
                synchronized(lock) { inFlight-- }
            }
        },
    )
}

fun snapshotTba() = FakeApi().apply {
    on("/team/frc5507/events/2026") { "[" + fixture("$SNAP/tba_event.json") + "]" }
    on("/event/2026cancmp") { fixture("$SNAP/tba_event.json") }
    on("/event/2026cancmp/matches") { fixture("$SNAP/tba_matches.json") }
    on("/event/2026cancmp/rankings") { fixture("$SNAP/tba_rankings.json") }
    on("/event/2026cancmp/oprs") { fixture("$SNAP/tba_oprs.json") }
}

fun snapshotNexus() = FakeApi().apply {
    on("/event/2026cancmp") { fixture("$SNAP/nexus_event.json") }
    on("/event/2026cancmp/map") { fixture("$SNAP/nexus_map.json") }
}

/** Replaces the Robolectric app's container with one backed by temp files and fake HTTP. */
fun installTestContainer(
    dir: java.io.File,
    tba: FakeApi = snapshotTba(),
    nexus: FakeApi = snapshotNexus(),
    clock: () -> Instant = { SNAP_NOW },
    updateWidgets: suspend () -> Unit = {},
): AppContainer {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    val stores = com.pitwatch.app.data.Stores(dir, scope)
    val repository = com.pitwatch.app.data.Repository(
        stores,
        { com.pitwatch.core.api.TbaClient(it, tba.client, "https://tba.test/api/v3") },
        { com.pitwatch.core.api.NexusClient(it, nexus.client, "https://nexus.test/api/v1") },
    )
    val container = AppContainer(stores, repository, scope, clock, updateWidgets)
    androidx.test.core.app.ApplicationProvider.getApplicationContext<PitWatchApp>().container = container
    return container
}

/** Polls [condition] while letting the Robolectric main looper run, for up to [timeoutMs]. */
fun awaitMain(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        check(System.currentTimeMillis() < deadline) { "Timed out waiting for condition" }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        Thread.sleep(10)
    }
}
