import SwiftUI
import WidgetKit
import TBAKit

// MARK: - Shared Color Tokens

/// Dark card background matching the Live Activity expanded view.
let widgetCardBackground = Color(hex: "#1C1C1E")

/// Dim label base color used by the Live Activity for secondary text.
/// Apply opacities 0.30 (tertiary), 0.45 (secondary-dim), 0.65 (secondary)
/// for the three levels of de-emphasis used throughout the widgets.
let widgetLabelDim = Color(red: 235/255, green: 235/255, blue: 245/255)

struct AllianceDot: View {
    let color: String?
    let size: CGFloat
    init(_ color: String?, size: CGFloat = 8) {
        self.color = color; self.size = size
    }
    var body: some View {
        Circle()
            .fill(color == "red" ? Color.red : (color == "blue" ? Color.blue : Color.gray))
            .frame(width: size, height: size)
    }
}

struct AllianceLineCompact: View {
    let allianceColor: String
    let teamKeys: [String]
    let trackedTeamKey: String
    let opr: Double?

    var body: some View {
        HStack(spacing: 2) {
            AllianceDot(allianceColor, size: 5)
            ForEach(teamKeys, id: \.self) { key in
                let num = key.replacingOccurrences(of: "frc", with: "")
                if key == trackedTeamKey {
                    Text(num).font(.system(size: 9)).bold()
                } else {
                    Text(num).font(.system(size: 9)).foregroundStyle(.secondary)
                }
            }
            if let opr {
                Spacer()
                Text(String(format: "%.1f", opr))
                    .font(.system(size: 8))
                    .foregroundStyle(allianceColor == "red" ? .red : .blue)
            }
        }
    }
}

/// Colored rounded-rect pill showing alliance + match label, matching the
/// Live Activity expanded view header. Render only when alliance color is known;
/// call sites should guard on `entry.nextMatchAllianceColor` before instantiating.
struct AllianceBadge: View {
    let allianceColor: String   // "red" or "blue"
    let matchLabel: String      // e.g., "Q32"

    private var backgroundColor: Color {
        switch allianceColor {
        case "red":  return Color.red.opacity(0.25)
        case "blue": return Color.blue.opacity(0.25)
        default:     return Color.gray.opacity(0.25)
        }
    }

    private var textColor: Color {
        switch allianceColor {
        case "red":  return Color(red: 1.0, green: 0.72, blue: 0.72)
        case "blue": return Color(red: 0.72, green: 0.80, blue: 1.0)
        default:     return widgetLabelDim.opacity(0.75)
        }
    }

    private var displayName: String {
        switch allianceColor {
        case "red":  return "Red"
        case "blue": return "Blue"
        default:     return "—"
        }
    }

    var body: some View {
        Text("\(displayName) · \(matchLabel)")
            .font(.system(size: 10, weight: .medium))
            .foregroundStyle(textColor)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(
                RoundedRectangle(cornerRadius: 4)
                    .fill(backgroundColor)
            )
    }
}

struct ScoreDisplay: View {
    let match: Match
    var body: some View {
        HStack(spacing: 4) {
            Text("\(match.alliances["red"]?.score ?? 0)").foregroundStyle(.red).fontWeight(.bold)
            Text("-").foregroundStyle(.secondary)
            Text("\(match.alliances["blue"]?.score ?? 0)").foregroundStyle(.blue).fontWeight(.bold)
        }
    }
}

struct WinLossLabel: View {
    let match: Match
    let teamKey: String
    var body: some View {
        let color = match.allianceColor(for: teamKey)
        if match.winningAlliance == color {
            Text("WIN").font(.caption2).bold().foregroundStyle(.green)
        } else if !match.winningAlliance.isEmpty {
            Text("LOSS").font(.caption2).bold().foregroundStyle(.red)
        }
    }
}

func formatMatchTime(_ date: Date?, prefix: String) -> String {
    guard let date else { return "--" }
    let fmt = DateFormatter()
    fmt.dateFormat = "h:mm a"
    return prefix + fmt.string(from: date)
}

func teamNumber(from key: String) -> String {
    key.replacingOccurrences(of: "frc", with: "")
}

func nexusStatusColor(_ status: String) -> Color {
    switch status.lowercased() {
    case let s where s.contains("queuing"): return .orange
    case let s where s.contains("deck"): return .yellow
    case let s where s.contains("field"): return .green
    default: return .gray
    }
}
