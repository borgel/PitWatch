import SwiftUI

public enum MatchAlliance: String, Codable, Sendable, Hashable {
    case blue, red

    public var displayName: String {
        switch self {
        case .blue: return "BLUE"
        case .red:  return "RED"
        }
    }

    public var badgeText: Color {
        switch self {
        case .blue: return Color(hex: "#4DA6FF")
        case .red:  return Color(hex: "#FF6B6B")
        }
    }

    public var badgeBackground: Color {
        switch self {
        case .blue: return Color(red: 0, green: 122/255, blue: 255/255).opacity(0.18)
        case .red:  return Color(red: 255/255, green: 59/255, blue: 48/255).opacity(0.18)
        }
    }

    public var dotColor: Color {
        switch self {
        case .blue: return Color(hex: "#1E6FFF")
        case .red:  return Color(hex: "#FF3B30")
        }
    }
}
