import SwiftUI
import WidgetKit
import TBAKit

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
