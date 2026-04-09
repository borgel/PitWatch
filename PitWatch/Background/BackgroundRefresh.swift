import Foundation
import BackgroundTasks
import WidgetKit
import WatchConnectivity
import TBAKit

#if canImport(ActivityKit) && os(iOS)
import ActivityKit
#endif

/// Wraps a non-Sendable value for safe transfer across isolation boundaries
/// when the developer guarantees correct usage.
private struct SendableBox<T>: @unchecked Sendable {
    let value: T
}

enum BackgroundRefresh {
    static let taskIdentifier = "com.pitwatch.refresh"

    static func register() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: taskIdentifier,
            using: nil
        ) { task in
            guard let refreshTask = task as? BGAppRefreshTask else { return }
            handleRefresh(refreshTask)
        }
    }

    static func scheduleNext(store: TBADataStore) {
        let config = store.loadConfig()
        let cache = store.loadEventCache()
        let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")
        let interval = schedule.refreshInterval(now: .now, useScheduledTime: config.useScheduledTime)
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval)
        try? BGTaskScheduler.shared.submit(request)
    }

    private static func handleRefresh(_ task: BGAppRefreshTask) {
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        let config = store.loadConfig()
        guard config.isConfigured, let apiKey = config.apiKey else {
            task.setTaskCompleted(success: true)
            return
        }
        let boxedTask = SendableBox(value: task)
        let refreshTask = Task {
            do {
                try await performRefresh(store: store, config: config, apiKey: apiKey)
                boxedTask.value.setTaskCompleted(success: true)
            } catch {
                boxedTask.value.setTaskCompleted(success: false)
            }
            scheduleNext(store: store)
        }
        task.expirationHandler = { refreshTask.cancel() }
    }

    static func performRefresh(
        store: TBADataStore,
        config: UserConfig,
        apiKey: String,
        forceReload: Bool = false
    ) async throws {
        let client = TBAClient(apiKey: apiKey)
        var cache = store.loadEventCache()
        var refreshState = store.loadRefreshState()

        // Determine active event
        let eventKey: String
        if let override = config.eventKeyOverride {
            eventKey = override
            // Clear cached data if switching to a different event
            if cache.event?.key != override {
                cache.event = nil
                cache.matches = []
                cache.rankings = nil
                cache.oprs = nil
                cache.nexusEvent = nil
            }
        } else if let active = cache.event?.key {
            eventKey = active
        } else {
            guard let teamNumber = config.teamNumber else { return }
            let year = Calendar.current.component(.year, from: .now)
            let eventsResult = try await client.fetch(
                [Event].self,
                path: Endpoints.teamEvents(number: teamNumber, year: year)
            )
            if case .data(let events, _) = eventsResult {
                let detected = autoDetectEvent(from: events)
                if let detected {
                    cache.event = detected
                    eventKey = detected.key
                } else { return }
            } else { return }
        }

        let oldCache = cache

        // Fetch event details if not cached
        if cache.event == nil {
            let eventResult = try await client.fetch(Event.self, path: Endpoints.event(key: eventKey))
            if case .data(let event, _) = eventResult {
                cache.event = event
            }
        }

        // Fetch matches
        let matchesPath = Endpoints.eventMatches(key: eventKey)
        let matchesLM = forceReload ? nil : refreshState.lastModified(for: matchesPath)
        let matchesResult = try await client.fetch([Match].self, path: matchesPath, lastModified: matchesLM)
        if case .data(let matches, let lm) = matchesResult {
            cache.matches = matches
            refreshState.setLastModified(lm, for: matchesPath)
        }

        // Fetch rankings
        let rankingsPath = Endpoints.eventRankings(key: eventKey)
        let rankingsLM = forceReload ? nil : refreshState.lastModified(for: rankingsPath)
        let rankingsResult = try await client.fetch(EventRankings.self, path: rankingsPath, lastModified: rankingsLM)
        if case .data(let rankings, let lm) = rankingsResult {
            cache.rankings = rankings
            refreshState.setLastModified(lm, for: rankingsPath)
        }

        // Fetch OPRs
        let oprsPath = Endpoints.eventOPRs(key: eventKey)
        let oprsLM = forceReload ? nil : refreshState.lastModified(for: oprsPath)
        let oprsResult = try await client.fetch(EventOPRs.self, path: oprsPath, lastModified: oprsLM)
        if case .data(let oprs, let lm) = oprsResult {
            cache.oprs = oprs
            refreshState.setLastModified(lm, for: oprsPath)
        }

        // Fetch Nexus event status (non-fatal)
        if let nexusKey = config.nexusApiKey, !nexusKey.isEmpty {
            let nexusClient = NexusClient(apiKey: nexusKey)
            let nexusResult = await nexusClient.fetchEventStatus(eventKey: eventKey)
            cache.nexusEvent = nexusResult
            refreshState.nexusLastRefreshDate = .now
            refreshState.nexusLastError = nexusResult == nil ? "Nexus data unavailable" : nil
        } else {
            cache.nexusEvent = nil
        }

        // Save
        refreshState.lastRefreshDate = .now
        refreshState.lastError = nil
        store.saveEventCache(cache)
        store.saveRefreshState(refreshState)

        // Push data to watch via WatchConnectivity
        if WCSession.isSupported() {
            if WCSession.default.activationState == .activated {
                if let data = try? JSONEncoder().encode(cache) {
                    WCSession.default.transferUserInfo(["eventCache": data])
                }
            }
        }

        // Only reload widgets if data changed
        let teamKey = config.teamKey ?? ""
        let changes = ChangeDetector.detect(old: oldCache, new: cache, teamKey: teamKey)
        if forceReload || changes.shouldReloadWidgets {
            WidgetCenter.shared.reloadAllTimelines()
        }

        #if canImport(ActivityKit) && os(iOS)
        // Live Activity management
        let manager = LiveActivityManager.shared
        let schedule = MatchSchedule(matches: cache.matches, teamKey: teamKey)

        if let next = schedule.nextMatch {
            if manager.hasActiveActivity {
                await manager.updateActivity(
                    match: next,
                    useScheduledTime: config.useScheduledTime,
                    queueOffsetMinutes: config.queueOffsetMinutes,
                    ranking: cache.rankings?.rankings.first { $0.teamKey == teamKey },
                    oprs: cache.oprs
                )
            } else if schedule.shouldStartLiveActivity(
                now: .now, mode: config.liveActivityMode,
                useScheduledTime: config.useScheduledTime,
                hasActiveLiveActivity: false
            ) {
                let _ = try? manager.startActivity(
                    match: next,
                    teamNumber: config.teamNumber ?? 0,
                    teamKey: teamKey,
                    eventName: cache.event?.shortName ?? cache.event?.name ?? "",
                    useScheduledTime: config.useScheduledTime,
                    queueOffsetMinutes: config.queueOffsetMinutes,
                    ranking: cache.rankings?.rankings.first { $0.teamKey == teamKey },
                    oprs: cache.oprs
                )
            }
        }

        // End completed match Live Activities
        if let last = schedule.lastPlayedMatch {
            await manager.endActivity(for: last.key)
        }
        #endif
    }

    static func autoDetectEvent(from events: [Event]) -> Event? {
        let now = Date.now
        if let active = events.first(where: { $0.isActive(on: now) }) {
            return active
        }
        let upcoming = events
            .filter { ($0.startDateParsed ?? .distantPast) > now }
            .sorted { ($0.startDateParsed ?? .distantFuture) < ($1.startDateParsed ?? .distantFuture) }
        if let next = upcoming.first { return next }
        return events
            .sorted { ($0.endDateParsed ?? .distantPast) > ($1.endDateParsed ?? .distantPast) }
            .first
    }
}
