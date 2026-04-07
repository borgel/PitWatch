import Foundation

public enum ChangeReason: Sendable {
    case scoreChanged
    case predictedTimeShifted
    case rankChanged
    case allianceChanged
}

public struct ChangeResult: Sendable {
    public let reasons: Set<ChangeReason>
    public var shouldReloadWidgets: Bool { !reasons.isEmpty }

    public init(reasons: Set<ChangeReason>) {
        self.reasons = reasons
    }
}

public enum ChangeDetector {
    /// Compare old and new event cache to determine if widget-visible data changed.
    public static func detect(old: EventCache, new: EventCache, teamKey: String) -> ChangeResult {
        var reasons = Set<ChangeReason>()

        let oldMatchMap = Dictionary(uniqueKeysWithValues: old.matches.map { ($0.key, $0) })
        for match in new.matches {
            guard match.alliances.values.contains(where: { $0.teamKeys.contains(teamKey) }) else {
                continue
            }
            if let oldMatch = oldMatchMap[match.key] {
                // Score posted or changed
                if match.isPlayed != oldMatch.isPlayed {
                    reasons.insert(.scoreChanged)
                } else if match.isPlayed && oldMatch.isPlayed {
                    let newRed = match.alliances["red"]?.score ?? -1
                    let oldRed = oldMatch.alliances["red"]?.score ?? -1
                    let newBlue = match.alliances["blue"]?.score ?? -1
                    let oldBlue = oldMatch.alliances["blue"]?.score ?? -1
                    if newRed != oldRed || newBlue != oldBlue {
                        reasons.insert(.scoreChanged)
                    }
                }

                // Predicted time shifted by more than 5 minutes
                if let newPT = match.predictedTime, let oldPT = oldMatch.predictedTime {
                    if abs(newPT - oldPT) > 300 {
                        reasons.insert(.predictedTimeShifted)
                    }
                }

                // Alliance composition changed
                let newTeams = Set((match.alliances["red"]?.teamKeys ?? []) + (match.alliances["blue"]?.teamKeys ?? []))
                let oldTeams = Set((oldMatch.alliances["red"]?.teamKeys ?? []) + (oldMatch.alliances["blue"]?.teamKeys ?? []))
                if newTeams != oldTeams {
                    reasons.insert(.allianceChanged)
                }
            } else {
                reasons.insert(.scoreChanged) // New match appeared
            }
        }

        // Check ranking changes
        if let newRank = new.rankings?.rankings.first(where: { $0.teamKey == teamKey }),
           let oldRank = old.rankings?.rankings.first(where: { $0.teamKey == teamKey }) {
            if newRank.rank != oldRank.rank ||
               newRank.record != oldRank.record ||
               newRank.matchesPlayed != oldRank.matchesPlayed {
                reasons.insert(.rankChanged)
            }
        } else if (new.rankings != nil) != (old.rankings != nil) {
            reasons.insert(.rankChanged)
        }

        return ChangeResult(reasons: reasons)
    }
}
