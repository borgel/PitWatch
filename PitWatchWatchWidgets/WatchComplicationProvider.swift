import WidgetKit
import TBAKit

struct WatchMatchEntry: TimelineEntry {
    let date: Date
    let teamNumber: Int?
    let nextMatch: Match?
    let allianceColor: String?
    let countdownTarget: Date?
    let countdownLabel: String
    let ranking: Ranking?
    let timePrefix: String

    static var placeholder: WatchMatchEntry {
        WatchMatchEntry(date: .now, teamNumber: 1234, nextMatch: nil,
            allianceColor: nil, countdownTarget: nil,
            countdownLabel: "to match", ranking: nil, timePrefix: "")
    }
}

struct WatchComplicationProvider: TimelineProvider {
    func placeholder(in context: Context) -> WatchMatchEntry { .placeholder }
    func getSnapshot(in context: Context, completion: @escaping (WatchMatchEntry) -> Void) {
        completion(makeEntry())
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<WatchMatchEntry>) -> Void) {
        let entry = makeEntry()
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        let config = store.loadConfig()
        let cache = store.loadEventCache()
        let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")
        let reloadDate = schedule.nextReloadDate(now: .now, useScheduledTime: config.useScheduledTime)
        completion(Timeline(entries: [entry], policy: .after(reloadDate)))
    }

    private func makeEntry() -> WatchMatchEntry {
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        let config = store.loadConfig()
        let cache = store.loadEventCache()
        let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")
        let next = schedule.nextMatch
        let matchDate = next?.matchDate(useScheduled: config.useScheduledTime)
        let countdownTarget: Date?
        let countdownLabel: String
        if config.queueOffsetMinutes > 0, let md = matchDate {
            countdownTarget = md.addingTimeInterval(-TimeInterval(config.queueOffsetMinutes * 60))
            countdownLabel = "to queue"
        } else {
            countdownTarget = matchDate
            countdownLabel = "to match"
        }
        return WatchMatchEntry(
            date: .now, teamNumber: config.teamNumber, nextMatch: next,
            allianceColor: next?.allianceColor(for: config.teamKey ?? ""),
            countdownTarget: countdownTarget, countdownLabel: countdownLabel,
            ranking: cache.rankings?.rankings.first { $0.teamKey == config.teamKey },
            timePrefix: config.useScheduledTime ? "" : "~"
        )
    }
}
