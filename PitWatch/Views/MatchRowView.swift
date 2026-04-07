import SwiftUI
import TBAKit

struct MatchRowView: View {
    let match: Match
    let teamKey: String
    let oprs: EventOPRs?
    let useScheduledTime: Bool
    let queueOffsetMinutes: Int

    private var allianceColor: String? { match.allianceColor(for: teamKey) }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                allianceDot
                Text(match.label).font(.headline)
                Spacer()
                if let date = match.matchDate(useScheduled: useScheduledTime) {
                    Text(timeText(date))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }

            allianceLine(color: "red")
            allianceLine(color: "blue")

            if match.isPlayed {
                HStack {
                    Spacer()
                    let redScore = match.alliances["red"]?.score ?? 0
                    let blueScore = match.alliances["blue"]?.score ?? 0
                    Text("\(redScore)").foregroundStyle(.red).fontWeight(.bold)
                    Text("–").foregroundStyle(.secondary)
                    Text("\(blueScore)").foregroundStyle(.blue).fontWeight(.bold)

                    if match.winningAlliance == allianceColor {
                        Text("WIN").font(.caption).fontWeight(.bold).foregroundStyle(.green)
                    } else if !match.winningAlliance.isEmpty {
                        Text("LOSS").font(.caption).fontWeight(.bold).foregroundStyle(.red)
                    }
                    Spacer()
                }
            }
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var allianceDot: some View {
        Circle()
            .fill(allianceColor == "red" ? Color.red : (allianceColor == "blue" ? Color.blue : Color.gray))
            .frame(width: 10, height: 10)
    }

    @ViewBuilder
    private func allianceLine(color: String) -> some View {
        let alliance = match.alliances[color]
        let teamKeys = alliance?.teamKeys ?? []
        let sumOPR = oprs?.summedOPR(for: teamKeys)

        HStack(spacing: 4) {
            Circle()
                .fill(color == "red" ? Color.red.opacity(0.6) : Color.blue.opacity(0.6))
                .frame(width: 6, height: 6)
            ForEach(teamKeys, id: \.self) { key in
                let number = key.replacingOccurrences(of: "frc", with: "")
                if key == teamKey {
                    Text(number).font(.caption).fontWeight(.bold)
                } else {
                    Text(number).font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if let opr = sumOPR {
                Text("Σ \(opr, specifier: "%.1f")")
                    .font(.caption2)
                    .foregroundStyle(color == "red" ? .red : .blue)
            }
        }
    }

    private func timeText(_ date: Date) -> String {
        let prefix = useScheduledTime ? "" : "~"
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"
        return prefix + formatter.string(from: date)
    }
}
