import SwiftUI
import WidgetKit
import TBAKit

struct LargeWidgetView: View {
    let entry: MatchWidgetEntry

    private var matchTime: Date? {
        entry.nextMatch?.matchDate(useScheduled: true)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header
            HStack {
                Text(String(entry.teamNumber ?? 0))
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                if let name = entry.eventName {
                    Text("· \(name)")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
                Spacer()
                if let ranking = entry.ranking {
                    Text("#\(String(ranking.rank)) · \(ranking.record?.display ?? "")")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(.secondary)
                }
            }

            if let nowQueuing = entry.nowQueuing {
                HStack(spacing: 4) {
                    Circle()
                        .fill(Color(hex: "#FF9500"))
                        .frame(width: 6, height: 6)
                    Text("Queuing: \(nowQueuing)")
                        .font(.system(size: 10, weight: .medium, design: .monospaced))
                        .foregroundStyle(Color(hex: "#FF9500"))
                }
            }

            // Next match card
            if let next = entry.nextMatch {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("NEXT")
                            .font(.system(size: 8, weight: .semibold, design: .monospaced))
                            .tracking(0.5)
                            .foregroundStyle(.secondary)
                        Text(next.label)
                            .font(.system(size: 14, weight: .bold, design: .monospaced))
                        if let status = entry.nexusStatus {
                            Text(status.uppercased())
                                .font(.system(size: 8, weight: .bold, design: .monospaced))
                                .padding(.horizontal, 4).padding(.vertical, 1)
                                .background(nexusStatusColor(status).opacity(0.2), in: Capsule())
                                .foregroundStyle(nexusStatusColor(status))
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 1) {
                            if let target = entry.countdownTarget {
                                Text(target, style: .relative)
                                    .font(.system(size: 12, design: .monospaced))
                                    .monospacedDigit()
                                    .foregroundStyle(.secondary)
                            }
                            if let time = matchTime {
                                Text(formatMatchTime(time, prefix: entry.timePrefix))
                                    .font(.system(size: 10, design: .monospaced))
                                    .foregroundStyle(.tertiary)
                            }
                        }
                    }
                    ForEach(["red", "blue"], id: \.self) { color in
                        let keys = next.alliances[color]?.teamKeys ?? []
                        AllianceLineCompact(
                            allianceColor: color, teamKeys: keys,
                            trackedTeamKey: entry.teamKey,
                            opr: entry.oprs?.summedOPR(for: keys)
                        )
                    }
                }
                .padding(8)
                .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
            }

            // Upcoming matches
            if !entry.upcomingMatches.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("UPCOMING")
                        .font(.system(size: 8, weight: .semibold, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(.tertiary)
                    ForEach(entry.upcomingMatches) { match in
                        HStack {
                            Text(match.shortLabel)
                                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                            if let color = match.allianceColor(for: entry.teamKey) {
                                AllianceDot(color, size: 5)
                            }
                            Spacer()
                            if let date = match.matchDate(useScheduled: entry.useScheduledTime) {
                                Text(formatMatchTime(date, prefix: entry.timePrefix))
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.horizontal, 10).padding(.vertical, 5)
                    }
                }
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 8))
            }

            // Recent results
            if !entry.pastMatches.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("RESULTS")
                        .font(.system(size: 8, weight: .semibold, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(.tertiary)
                    ForEach(entry.pastMatches) { match in
                        HStack {
                            Text(match.shortLabel)
                                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                            if let color = match.allianceColor(for: entry.teamKey) {
                                AllianceDot(color, size: 5)
                            }
                            Spacer()
                            if let date = match.matchDate(useScheduled: entry.useScheduledTime) {
                                Text(formatMatchTime(date, prefix: entry.timePrefix))
                                    .font(.system(size: 10, design: .monospaced))
                                    .foregroundStyle(.tertiary)
                            }
                            ScoreDisplay(match: match).font(.system(size: 11))
                            WinLossLabel(match: match, teamKey: entry.teamKey)
                        }
                        .padding(.horizontal, 10).padding(.vertical, 5)
                    }
                }
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 8))
            }
            Spacer(minLength: 0)
        }
        .containerBackground(for: .widget) {
            Color(hex: "#1C1C1E")
        }
    }
}
