import SwiftUI

public enum Phase: Int, Codable, CaseIterable, Identifiable, Sendable, Hashable {
    case preQueue = 0
    case queueing = 1
    case onDeck   = 2
    case onField  = 3

    public var id: Int { rawValue }

    public var label: String {
        switch self {
        case .preQueue: return "PRE"
        case .queueing: return "QUEUE"
        case .onDeck:   return "DECK"
        case .onField:  return "FIELD"
        }
    }

    public var sublabel: String {
        switch self {
        case .preQueue: return "UNTIL QUEUEING"
        case .queueing: return "UNTIL ON DECK"
        case .onDeck:   return "UNTIL ON FIELD"
        case .onField:  return "MATCH IN PROGRESS"
        }
    }

    public var combinedLabel: String { "\(label) \u{00B7} \(sublabel)" }

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
