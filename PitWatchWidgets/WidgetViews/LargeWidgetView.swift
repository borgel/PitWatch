import SwiftUI
import WidgetKit
import TBAKit

struct LargeWidgetView: View {
    let entry: MatchWidgetEntry

    /// Maximum number of UPCOMING rows to render (matches + inline break rows).
    /// Single-line horizontal rows fit many more than the old 3-line stacked rows.
    /// Drop if the iPhone SE large widget clips.
    private let upcomingRowTarget: Int = 8

    private var matchTime: Date? {
        entry.nextMatch?.matchDate(useScheduled: true)
    }

    private struct UpcomingRow: Identifiable {
        let id: String
        let item: UpcomingScheduleItem
        let showDayDivider: Bool
    }

    /// Walks the entry's timeline (dropping the first match, which is rendered in
    /// the NEXT section above) and produces display rows with pre-computed day
    /// dividers. A `.breakInterval` row resets the day-tracking state so the
    /// next match doesn't double up with a divider — an overnight break already
    /// conveys the day transition; a lunch break stays within a day.
    private func upcomingTimelineRows() -> [UpcomingRow] {
        let calendar = Calendar.current
        let items = Array(entry.upcomingTimeline.dropFirst().prefix(upcomingRowTarget))
        var rows: [UpcomingRow] = []
        var lastMatchDay: Date?
        for item in items {
            switch item {
            case .match(let match):
                let day = match.matchDate(useScheduled: entry.useScheduledTime)
                    .map { calendar.startOfDay(for: $0) }
                let show = lastMatchDay != nil && day != nil && day != lastMatchDay
                rows.append(UpcomingRow(id: item.id, item: item, showDayDivider: show))
                if day != nil { lastMatchDay = day }
            case .breakInterval:
                rows.append(UpcomingRow(id: item.id, item: item, showDayDivider: false))
                lastMatchDay = nil
            }
        }
        return rows
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

            // Upcoming items — matches and inline break rows (lunch/overnight/session)
            let upcomingRows = upcomingTimelineRows()
            if !upcomingRows.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("UPCOMING")
                        .font(.system(size: 8, weight: .semibold, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(widgetLabelDim.opacity(0.45))
                    ForEach(upcomingRows) { row in
                        if row.showDayDivider {
                            Rectangle()
                                .fill(widgetLabelDim.opacity(0.2))
                                .frame(height: 0.5)
                        }
                        switch row.item {
                        case .match(let match):
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
                        case .breakInterval(let scheduleBreak):
                            WidgetScheduleBreakRow(scheduleBreak: scheduleBreak)
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

private struct WidgetScheduleBreakRow: View {
    let scheduleBreak: ScheduleBreak

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: iconName)
                .font(.system(size: 10))
                .foregroundStyle(widgetLabelDim.opacity(0.5))
            Text(title.uppercased())
                .font(.system(size: 9, weight: .semibold, design: .monospaced))
                .tracking(0.5)
                .foregroundStyle(widgetLabelDim.opacity(0.5))
            Spacer()
            Text(durationText)
                .font(.system(size: 10, design: .monospaced))
                .foregroundStyle(widgetLabelDim.opacity(0.45))
        }
    }

    private var iconName: String {
        switch scheduleBreak.kind {
        case .lunch:        return "fork.knife"
        case .overnight:    return "moon.stars"
        case .sessionBreak: return "pause.circle"
        }
    }

    private var title: String {
        switch scheduleBreak.kind {
        case .lunch:        return "Lunch break"
        case .overnight:    return "Overnight"
        case .sessionBreak: return "Break"
        }
    }

    private var durationText: String {
        let minutes = Int(scheduleBreak.duration / 60)
        if minutes < 60 { return "\(minutes) min" }
        let hours = minutes / 60
        let mins = minutes % 60
        return mins == 0 ? "\(hours) hr" : "\(hours) hr \(mins) min"
    }
}

#Preview("Large · Empty", as: .systemLarge) {
    NextMatchWidget()
} timeline: {
    MatchWidgetEntry.placeholder
}
