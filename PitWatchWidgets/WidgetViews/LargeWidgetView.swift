import SwiftUI
import WidgetKit
import TBAKit

struct LargeWidgetView: View {
    let entry: MatchWidgetEntry

    /// Maximum number of upcoming match rows to render. Single-line horizontal
    /// rows fit many more than the old 3-line stacked rows; bumped from 4 to 8
    /// to match the timeline provider's prefix(8) headroom. Drop if the iPhone
    /// SE large widget clips.
    private let upcomingRowTarget: Int = 8

    private var matchTime: Date? {
        entry.nextMatch?.matchDate(useScheduled: true)
    }

    private struct UpcomingRow {
        let match: Match
        let showDayDivider: Bool
    }

    private func upcomingRowsWithDayBreaks() -> [UpcomingRow] {
        let calendar = Calendar.current
        var rows: [UpcomingRow] = []
        var previousDay: Date?
        for match in entry.upcomingMatches.prefix(upcomingRowTarget) {
            let day = match.matchDate(useScheduled: entry.useScheduledTime)
                .map { calendar.startOfDay(for: $0) }
            let show = previousDay != nil && day != nil && day != previousDay
            rows.append(UpcomingRow(match: match, showDayDivider: show))
            if day != nil { previousDay = day }
        }
        return rows
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(String(entry.teamNumber ?? 0))
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                    if let color = entry.nextMatchAllianceColor, let next = entry.nextMatch {
                        AllianceBadge(allianceColor: color, matchLabel: next.shortLabel)
                    }
                    Spacer()
                    if let ranking = entry.ranking {
                        Text("#\(String(ranking.rank)) · \(ranking.record?.display ?? "")")
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundStyle(widgetLabelDim.opacity(0.65))
                    }
                }
                if let name = entry.eventName {
                    Text(name)
                        .font(.system(size: 12))
                        .foregroundStyle(widgetLabelDim.opacity(0.65))
                        .lineLimit(2)
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

            // Upcoming matches — single-line rows: label, both alliances inline, time
            if !entry.upcomingMatches.isEmpty {
                let upcomingRows = upcomingRowsWithDayBreaks()
                VStack(alignment: .leading, spacing: 4) {
                    Text("UPCOMING")
                        .font(.system(size: 8, weight: .semibold, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(widgetLabelDim.opacity(0.45))
                    ForEach(upcomingRows, id: \.match.id) { row in
                        if row.showDayDivider {
                            Rectangle()
                                .fill(widgetLabelDim.opacity(0.2))
                                .frame(height: 0.5)
                        }
                        let match = row.match
                        let trackedAlliance = match.allianceColor(for: entry.teamKey)
                        HStack(spacing: 6) {
                            Text(match.shortLabel)
                                .font(.system(size: 12, weight: .semibold, design: .monospaced))
                            AllianceLineCompact(
                                allianceColor: "red",
                                teamKeys: match.alliances["red"]?.teamKeys ?? [],
                                trackedTeamKey: entry.teamKey,
                                opr: nil,
                                highlighted: "red" == trackedAlliance
                            )
                            AllianceLineCompact(
                                allianceColor: "blue",
                                teamKeys: match.alliances["blue"]?.teamKeys ?? [],
                                trackedTeamKey: entry.teamKey,
                                opr: nil,
                                highlighted: "blue" == trackedAlliance
                            )
                            Spacer()
                            if let date = match.matchDate(useScheduled: entry.useScheduledTime) {
                                Text(formatMatchTime(date, prefix: entry.timePrefix))
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundStyle(widgetLabelDim.opacity(0.45))
                            }
                        }
                    }
                }
            }

            Spacer(minLength: 0)

            // Last match — single flat row, anchored to bottom
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
