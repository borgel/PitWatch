import Foundation

enum AppGroup {
    static let identifier = "group.com.pitwatch.shared"

    static var containerURL: URL {
        FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: identifier
        )!
    }

    static var configURL: URL { containerURL.appendingPathComponent("team_config.json") }
    static var eventCacheURL: URL { containerURL.appendingPathComponent("event_cache.json") }
    static var lastRefreshURL: URL { containerURL.appendingPathComponent("last_refresh.json") }
}
