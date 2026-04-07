import Foundation
#if canImport(ActivityKit) && os(iOS)
import ActivityKit

public struct MatchActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable, Sendable {
        public var matchTime: Date?
        public var queueTime: Date?
        public var redScore: Int?
        public var blueScore: Int?
        public var winningAlliance: String?
        public var redAllianceOPR: Double?
        public var blueAllianceOPR: Double?
        public var matchState: MatchState
        public var rank: Int?
        public var record: String?

        public init(matchTime: Date?, queueTime: Date?, redScore: Int?, blueScore: Int?,
                    winningAlliance: String?, redAllianceOPR: Double?, blueAllianceOPR: Double?,
                    matchState: MatchState, rank: Int?, record: String?) {
            self.matchTime = matchTime
            self.queueTime = queueTime
            self.redScore = redScore
            self.blueScore = blueScore
            self.winningAlliance = winningAlliance
            self.redAllianceOPR = redAllianceOPR
            self.blueAllianceOPR = blueAllianceOPR
            self.matchState = matchState
            self.rank = rank
            self.record = record
        }
    }

    public var teamNumber: Int
    public var eventName: String
    public var matchKey: String
    public var matchLabel: String
    public var compLevel: String
    public var redTeams: [String]
    public var blueTeams: [String]
    public var trackedAllianceColor: String

    public init(teamNumber: Int, eventName: String, matchKey: String, matchLabel: String,
                compLevel: String, redTeams: [String], blueTeams: [String], trackedAllianceColor: String) {
        self.teamNumber = teamNumber
        self.eventName = eventName
        self.matchKey = matchKey
        self.matchLabel = matchLabel
        self.compLevel = compLevel
        self.redTeams = redTeams
        self.blueTeams = blueTeams
        self.trackedAllianceColor = trackedAllianceColor
    }
}

public enum MatchState: String, Codable, Hashable, Sendable {
    case upcoming
    case imminent
    case inProgress
    case completed
}
#endif
