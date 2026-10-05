import SwiftUI

public enum Phase: Int, Codable, CaseIterable, Identifiable, Sendable, Hashable {
    case preQueue = 0
    case queueing = 1
    case onDeck   = 2
    case onField  = 3

    public var id: Int { rawValue }

    /// Current state — what is happening right now.
    public var stateLabel: String {
        switch self {
        case .preQueue: return "UPCOMING"
        case .queueing: return "IN QUEUE"
        case .onDeck:   return "ON DECK"
        case .onField:  return "ON FIELD"
        }
    }

    /// Timer target — what happens when the countdown hits zero.
    public var targetLabel: String {
        switch self {
        case .preQueue: return "QUEUE STARTS"
        case .queueing: return "MOVE TO DECK"
        case .onDeck:   return "MOVE TO FIELD"
        case .onField:  return "MATCH ENDS"
        }
    }

    /// Single-letter glyph for compact surfaces (Dynamic Island compact leading).
    public var glyph: String {
        switch self {
        case .preQueue: return "U"
        case .queueing: return "Q"
        case .onDeck:   return "D"
        case .onField:  return "F"
        }
    }

    /// Lowercase prose name of the *next* phase — used for subtitles like "to on deck".
    /// Returns nil for `.onField` since there is no next phase.
    public var nextPhaseProse: String? {
        switch self {
        case .preQueue: return "queue"
        case .queueing: return "on deck"
        case .onDeck:   return "on field"
        case .onField:  return nil
        }
    }

    public var color: Color {
        switch self {
        case .preQueue: return Color(hex: "#636366")
        case .queueing: return Color(hex: "#FF9500")
        case .onDeck:   return Color(hex: "#FF6B00")
        case .onField:  return Color(hex: "#30D158")
        }
    }
}

public enum MatchesAwayDisplay {
    public static func text(for gap: Int) -> String {
        switch gap {
        case ...0: return "NOW"
        case 1:    return "NEXT"
        default:   return "\(gap) AWAY"
        }
    }

    public static func color(for gap: Int, phase: Phase) -> Color {
        switch gap {
        case ...0: return Color(hex: "#30D158").opacity(0.65)
        case 1:    return phase.color.opacity(0.65)
        default:   return Color.white.opacity(0.50)
        }
    }
}
