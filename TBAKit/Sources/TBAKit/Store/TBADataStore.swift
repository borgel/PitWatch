import Foundation

public struct EventCache: Codable, Sendable {
    public var event: Event?
    public var matches: [Match]
    public var rankings: EventRankings?
    public var oprs: EventOPRs?
    public var teams: [Team]
}

extension EventCache {
    /// Default empty cache
    public init() {
        self.event = nil
        self.matches = []
        self.rankings = nil
        self.oprs = nil
        self.teams = []
    }
}

public struct RefreshState: Codable, Sendable {
    public var lastRefreshDate: Date?
    public var lastModifiedHeaders: [String: String]
    public var isRefreshing: Bool
    public var lastError: String?

    public init() {
        self.lastRefreshDate = nil
        self.lastModifiedHeaders = [:]
        self.isRefreshing = false
        self.lastError = nil
    }

    public func lastModified(for path: String) -> String? {
        lastModifiedHeaders[path]
    }

    public mutating func setLastModified(_ value: String?, for path: String) {
        if let value {
            lastModifiedHeaders[path] = value
        }
    }
}

public final class TBADataStore: Sendable {
    private let configURL: URL
    private let eventCacheURL: URL
    private let lastRefreshURL: URL

    public init(containerURL: URL) {
        self.configURL = containerURL.appendingPathComponent("team_config.json")
        self.eventCacheURL = containerURL.appendingPathComponent("event_cache.json")
        self.lastRefreshURL = containerURL.appendingPathComponent("last_refresh.json")
    }

    // MARK: - UserConfig
    public func loadConfig() -> UserConfig {
        load(UserConfig.self, from: configURL) ?? UserConfig()
    }

    public func saveConfig(_ config: UserConfig) {
        save(config, to: configURL)
    }

    // MARK: - EventCache
    public func loadEventCache() -> EventCache {
        load(EventCache.self, from: eventCacheURL) ?? EventCache()
    }

    public func saveEventCache(_ cache: EventCache) {
        save(cache, to: eventCacheURL)
    }

    // MARK: - RefreshState
    public func loadRefreshState() -> RefreshState {
        load(RefreshState.self, from: lastRefreshURL) ?? RefreshState()
    }

    public func saveRefreshState(_ state: RefreshState) {
        save(state, to: lastRefreshURL)
    }

    // MARK: - Private
    private func load<T: Decodable>(_ type: T.Type, from url: URL) -> T? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private func save<T: Encodable>(_ value: T, to url: URL) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        try? data.write(to: url, options: .atomic)
    }
}
