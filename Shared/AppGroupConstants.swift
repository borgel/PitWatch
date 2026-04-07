import Foundation

enum AppGroup {
    static let identifier = "group.com.pitwatch.shared"

    static var containerURL: URL {
        if let url = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: identifier
        ) {
            return url
        }
        // Fallback for simulator/development when App Group isn't provisioned
        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
    }

    static var configURL: URL { containerURL.appendingPathComponent("team_config.json") }
    static var eventCacheURL: URL { containerURL.appendingPathComponent("event_cache.json") }
    static var lastRefreshURL: URL { containerURL.appendingPathComponent("last_refresh.json") }
}
