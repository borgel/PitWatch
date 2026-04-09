import SwiftUI
import TBAKit

struct MatchRowView: View {
    let match: Match
    let teamKey: String
    let oprs: EventOPRs?
    let useScheduledTime: Bool
    let queueOffsetMinutes: Int
    let nexusEvent: NexusEvent?

    private var allianceColor: String? { match.allianceColor(for: teamKey) }
    private var nexusMatch: NexusMatch? { NexusMatchMerge.nexusInfo(for: match, in: nexusEvent) }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                allianceDot
                Text(match.label).font(.headline)
                if let status = nexusMatch?.status {
                    NexusStatusBadge(status: status)
                }
                Spacer()
                if let nexusMatch, !match.isPlayed {
                    nexusTimeDisplay(nexusMatch)
                } else if let date = match.matchDate(useScheduled: useScheduledTime) {
                    Text(timeText(date))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }

            // Nexus times detail (only for upcoming matches with Nexus data)
            if let nexusMatch, !match.isPlayed {
                NexusTimesView(times: nexusMatch.times)
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

    @ViewBuilder
    private func nexusTimeDisplay(_ nexus: NexusMatch) -> some View {
        if let nextPhase = nexus.times.nextPhaseDate(after: .now) {
            VStack(alignment: .trailing, spacing: 1) {
                Text(nextPhase.date, style: .relative)
                    .font(.subheadline).fontWeight(.semibold)
                Text("to \(nextPhase.label.lowercased())")
                    .font(.caption2).foregroundStyle(.secondary)
            }
        } else if let startDate = nexus.times.startDate {
            Text(timeText(startDate))
                .font(.subheadline).foregroundStyle(.secondary)
        }
    }

    private func timeText(_ date: Date) -> String {
        let prefix = useScheduledTime ? "" : "~"
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"
        return prefix + formatter.string(from: date)
    }
}

struct NexusStatusBadge: View {
    let status: String

    private var color: Color {
        switch status.lowercased() {
        case let s where s.contains("queuing"): return .orange
        case let s where s.contains("deck"): return .yellow
        case let s where s.contains("field"): return .green
        default: return .gray
        }
    }

    var body: some View {
        Text(status.uppercased())
            .font(.system(size: 9, weight: .bold))
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.2), in: Capsule())
            .foregroundStyle(color)
    }
}

struct NexusTimesView: View {
    let times: NexusMatchTimes

    private var phases: [(label: String, date: Date?, isPast: Bool)] {
        let now = Date.now
        return [
            ("Queue", times.queueDate, times.queueDate.map { $0 <= now } ?? false),
            ("On Deck", times.onDeckDate, times.onDeckDate.map { $0 <= now } ?? false),
            ("On Field", times.onFieldDate, times.onFieldDate.map { $0 <= now } ?? false),
            ("Start", times.startDate, times.startDate.map { $0 <= now } ?? false),
        ].filter { $0.date != nil }
    }

    private var nextPhaseIndex: Int? {
        phases.firstIndex { !$0.isPast }
    }

    var body: some View {
        if !phases.isEmpty {
            HStack(spacing: 12) {
                ForEach(Array(phases.enumerated()), id: \.offset) { index, phase in
                    VStack(spacing: 1) {
                        Text(phase.label)
                            .font(.system(size: 8))
                            .foregroundStyle(index == nextPhaseIndex ? .primary : .tertiary)
                        if let date = phase.date {
                            let timeText = Text(formatTime(date))
                                .font(.system(size: 10, weight: index == nextPhaseIndex ? .bold : .regular))
                            if index == nextPhaseIndex {
                                timeText.foregroundStyle(Color.accentColor)
                            } else if phase.isPast {
                                timeText.foregroundStyle(.tertiary)
                            } else {
                                timeText.foregroundStyle(.secondary)
                            }
                        }
                        if index == nextPhaseIndex, let date = phase.date {
                            Text(date, style: .relative)
                                .font(.system(size: 8))
                                .foregroundStyle(Color.accentColor)
                        }
                    }
                }
            }
            .padding(.vertical, 2)
        }
    }

    private func formatTime(_ date: Date) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "h:mm"
        return fmt.string(from: date)
    }
}
