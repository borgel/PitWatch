import SwiftUI
import WidgetKit
import TBAKit

struct MediumWidgetView: View {
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
                if let ranking = entry.ranking {
                    Text("#\(String(ranking.rank)) · \(ranking.record?.display ?? "")")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(.secondary)
                }
                Spacer()
                if let name = entry.eventName {
                    Text(name)
                        .font(.system(size: 10))
                        .foregroundStyle(.tertiary)
                        .lineLimit(1)
                }
            }

            HStack(spacing: 10) {
                // Next match card
                VStack(alignment: .leading, spacing: 4) {
                    Text("NEXT")
                        .font(.system(size: 8, weight: .semibold, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(.secondary)
                    if let next = entry.nextMatch {
                        HStack {
                            Text(next.shortLabel)
                                .font(.system(size: 18, weight: .bold, design: .monospaced))
                            Spacer()
                            if let target = entry.countdownTarget {
                                Text(target, style: .relative)
                                    .font(.system(size: 11, design: .monospaced))
                                    .monospacedDigit()
                                    .foregroundStyle(.secondary)
                            }
                        }
                        // Wall clock time
                        if let time = matchTime {
                            Text(formatMatchTime(time, prefix: entry.timePrefix))
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundStyle(.tertiary)
                        }
                        ForEach(["red", "blue"], id: \.self) { color in
                            let keys = next.alliances[color]?.teamKeys ?? []
                            AllianceLineCompact(
                                allianceColor: color, teamKeys: keys,
                                trackedTeamKey: entry.teamKey,
                                opr: entry.oprs?.summedOPR(for: keys)
                            )
                        }
                    } else {
                        Text("None")
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(8)
                .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 8))

                // Last match card
                VStack(alignment: .leading, spacing: 4) {
                    Text("LAST")
                        .font(.system(size: 8, weight: .semibold, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(.secondary)
                    if let last = entry.lastMatch {
                        Text(last.shortLabel)
                            .font(.system(size: 18, weight: .bold, design: .monospaced))
                        HStack {
                            Spacer()
                            ScoreDisplay(match: last).font(.system(size: 18))
                            Spacer()
                        }
                        HStack {
                            Spacer()
                            WinLossLabel(match: last, teamKey: entry.teamKey)
                            Spacer()
                        }
                    } else {
                        Text("No results")
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(8)
                .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 8))
            }
        }
        .containerBackground(for: .widget) {
            Color(hex: "#1C1C1E")
        }
    }
}
