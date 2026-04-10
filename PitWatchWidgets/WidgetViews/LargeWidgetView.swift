import SwiftUI
import WidgetKit
import TBAKit

struct LargeWidgetView: View {
    let entry: MatchWidgetEntry

    /// Maximum number of upcoming match rows to render. Starts at 4; if preview
    /// validation (Task 12) finds that 4 rows clip on the smallest iPhone large
    /// widget, drop to 3. Locked in before implementation wraps.
    private let upcomingRowTarget: Int = 4

    private var matchTime: Date? {
        entry.nextMatch?.matchDate(useScheduled: true)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header
            HStack {
                Text(String(entry.teamNumber ?? 0))
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                if let color = entry.nextMatchAllianceColor, let next = entry.nextMatch {
                    AllianceBadge(allianceColor: color, matchLabel: next.shortLabel)
                }
                if let name = entry.eventName {
                    Text("· \(name)")
                        .font(.system(size: 12))
                        .foregroundStyle(widgetLabelDim.opacity(0.65))
                }
                Spacer()
                if let ranking = entry.ranking {
                    Text("#\(String(ranking.rank)) · \(ranking.record?.display ?? "")")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(widgetLabelDim.opacity(0.65))
                }
            }

            if let nowQueuing = entry.nowQueuing {
                HStack(spacing: 4) {
                    Circle()
                        .fill(Phase.queueing.color)
                        .frame(width: 6, height: 6)
                    Text("Now Queuing: \(nowQueuing)")
                        .font(.system(size: 10, weight: .medium, design: .monospaced))
                        .foregroundStyle(Phase.queueing.color)
                }
            }

            // Next match section (flat, no card background)
            if let next = entry.nextMatch {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .firstTextBaseline) {
                        Text("NEXT")
                            .font(.system(size: 8, weight: .semibold, design: .monospaced))
                            .tracking(0.5)
                            .foregroundStyle(widgetLabelDim.opacity(0.45))
                        Text(next.label)
                            .font(.system(size: 14, weight: .bold, design: .monospaced))
                        if let status = entry.nexusStatus {
                            let pillColor = entry.nextMatchPhase?.color ?? nexusStatusColor(status)
                            Text(status.uppercased())
                                .font(.system(size: 8, weight: .bold, design: .monospaced))
                                .padding(.horizontal, 4).padding(.vertical, 1)
                                .background(pillColor.opacity(0.2), in: Capsule())
                                .foregroundStyle(pillColor)
                        }
                        Spacer()
                        if let target = entry.countdownTarget {
                            Text(target, style: .relative)
                                .font(.system(size: 12, design: .monospaced))
                                .monospacedDigit()
                                .foregroundStyle(entry.nextMatchPhase?.color ?? widgetLabelDim.opacity(0.65))
                        }
                    }
                    if let time = matchTime {
                        HStack {
                            Spacer()
                            Text(formatMatchTime(time, prefix: entry.timePrefix))
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundStyle(widgetLabelDim.opacity(0.45))
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
            }

            // Upcoming matches — flat, 3-line rows with tracked alliance highlighted
            if !entry.upcomingMatches.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("UPCOMING")
                        .font(.system(size: 8, weight: .semibold, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(widgetLabelDim.opacity(0.45))
                    ForEach(entry.upcomingMatches.prefix(upcomingRowTarget)) { match in
                        VStack(alignment: .leading, spacing: 3) {
                            HStack {
                                Text(match.shortLabel)
                                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                                Spacer()
                                if let date = match.matchDate(useScheduled: entry.useScheduledTime) {
                                    Text(formatMatchTime(date, prefix: entry.timePrefix))
                                        .font(.system(size: 11, design: .monospaced))
                                        .foregroundStyle(widgetLabelDim.opacity(0.45))
                                }
                            }
                            let trackedAlliance = match.allianceColor(for: entry.teamKey)
                            ForEach(["red", "blue"], id: \.self) { color in
                                let keys = match.alliances[color]?.teamKeys ?? []
                                AllianceLineCompact(
                                    allianceColor: color, teamKeys: keys,
                                    trackedTeamKey: entry.teamKey,
                                    opr: nil,
                                    highlighted: color == trackedAlliance
                                )
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }
            }

            // Last match — single flat row
            if let last = entry.lastMatch {
                HStack(spacing: 8) {
                    Text("LAST")
                        .font(.system(size: 8, weight: .semibold, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(widgetLabelDim.opacity(0.45))
                    Text(last.shortLabel)
                        .font(.system(size: 12, weight: .semibold, design: .monospaced))
                    Spacer()
                    ScoreDisplay(match: last).font(.system(size: 12))
                    WinLossLabel(match: last, teamKey: entry.teamKey)
                    if let date = last.matchDate(useScheduled: entry.useScheduledTime) {
                        Text(formatMatchTime(date, prefix: entry.timePrefix))
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundStyle(widgetLabelDim.opacity(0.45))
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .containerBackground(for: .widget) {
            widgetCardBackground
        }
    }
}

#Preview("Large · Empty", as: .systemLarge) {
    NextMatchWidget()
} timeline: {
    MatchWidgetEntry.placeholder
}
