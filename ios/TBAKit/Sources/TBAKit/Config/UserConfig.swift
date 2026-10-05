import Foundation

public struct UserConfig: Codable, Sendable, Equatable {
    public var teamNumber: Int?
    public var apiKey: String?
    public var eventKeyOverride: String?
    public var useScheduledTime: Bool
    public var queueOffsetMinutes: Int
    public var liveActivityMode: LiveActivityMode
    public var nexusApiKey: String?
    public var timeSource: TimeSource?

    public init() {
        self.teamNumber = nil
        self.apiKey = nil
        self.eventKeyOverride = nil
        self.useScheduledTime = false
        self.queueOffsetMinutes = 0
        self.liveActivityMode = .nearMatch
        self.nexusApiKey = nil
        self.timeSource = nil
    }

    /// Effective time source: user's choice, or Nexus if configured, TBA otherwise.
    public var effectiveTimeSource: TimeSource {
        timeSource ?? (isNexusConfigured ? .nexus : .tba)
    }

    public var isConfigured: Bool {
        teamNumber != nil && apiKey != nil && !apiKey!.isEmpty
    }

    public var isNexusConfigured: Bool {
        nexusApiKey != nil && !nexusApiKey!.isEmpty
    }

    public var teamKey: String? {
        guard let number = teamNumber else { return nil }
        return "frc\(number)"
    }

    public var queueOffset: TimeInterval {
        TimeInterval(queueOffsetMinutes * 60)
    }
}

public enum LiveActivityMode: String, Codable, Sendable, CaseIterable {
    case nearMatch
    case allDay
}

public enum TimeSource: String, Codable, Sendable, CaseIterable {
    case nexus
    case tba
}
