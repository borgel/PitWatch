import SwiftUI
import TBAKit

struct ScheduleBreakRow: View {
    let scheduleBreak: ScheduleBreak

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: iconName)
                .foregroundStyle(.secondary)
                .frame(width: 28, alignment: .center)
            Text(title)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Spacer()
            Text(durationText)
                .font(.caption)
                .foregroundStyle(.tertiary)
                .monospacedDigit()
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title), \(durationText)")
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

#Preview("Lunch") {
    List {
        ScheduleBreakRow(scheduleBreak: ScheduleBreak(
            kind: .lunch,
            startsAfter: "Qualification 62",
            endsBefore: "Qualification 63",
            start: .now,
            end: .now.addingTimeInterval(59 * 60)
        ))
        ScheduleBreakRow(scheduleBreak: ScheduleBreak(
            kind: .overnight,
            startsAfter: "Qualification 38",
            endsBefore: "Qualification 39",
            start: .now,
            end: .now.addingTimeInterval(15 * 3600)
        ))
        ScheduleBreakRow(scheduleBreak: ScheduleBreak(
            kind: .sessionBreak,
            startsAfter: "Practice 11",
            endsBefore: "Qualification 1",
            start: .now,
            end: .now.addingTimeInterval(45 * 60)
        ))
    }
}
