import Foundation
import BackgroundTasks
import WidgetKit
import TBAKit

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
        let refreshTask = Task {
            do {
                try await performRefresh(store: store, config: config, apiKey: apiKey)
                task.setTaskCompleted(success: true)
            } catch {
                task.setTaskCompleted(success: false)
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

        // Save
        refreshState.lastRefreshDate = .now
        refreshState.lastError = nil
        store.saveEventCache(cache)
        store.saveRefreshState(refreshState)

        // Only reload widgets if data changed
        let teamKey = config.teamKey ?? ""
        let changes = ChangeDetector.detect(old: oldCache, new: cache, teamKey: teamKey)
        if forceReload || changes.shouldReloadWidgets {
            WidgetCenter.shared.reloadAllTimelines()
        }
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
