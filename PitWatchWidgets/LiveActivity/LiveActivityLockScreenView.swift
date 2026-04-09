import SwiftUI
import ActivityKit
import WidgetKit
import TBAKit

struct LiveActivityLockScreenView: View {
    let context: ActivityViewContext<MatchActivityAttributes>

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(context.attributes.matchLabel).font(.headline)
                if let status = context.state.nexusStatus {
                    Text(status.uppercased())
                        .font(.system(size: 9, weight: .bold))
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(nexusStatusColor(status).opacity(0.2), in: Capsule())
                        .foregroundStyle(nexusStatusColor(status))
                }
                Spacer()
                Text(context.attributes.eventName).font(.subheadline).foregroundStyle(.secondary)
            }

            switch context.state.matchState {
            case .upcoming, .imminent:
                VStack(spacing: 4) {
                    if context.state.nexusStartTime != nil {
                        NexusLiveActivityTimes(state: context.state)
                    }
                    let target = nextNexusPhaseDate(state: context.state)
                        ?? context.state.queueTime ?? context.state.matchTime
                    if let target {
                        Text(target, style: .timer)
                            .font(.system(size: 32, weight: .bold)).monospacedDigit()
                            .frame(maxWidth: .infinity, alignment: .center)
                        Text(nextNexusPhaseLabel(state: context.state)
                             ?? (context.state.queueTime != nil ? "to queue" : "to match"))
                            .font(.caption).foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .center)
                    }
                }
            case .inProgress:
                Text("Match in progress")
                    .font(.title3).fontWeight(.semibold)
                    .frame(maxWidth: .infinity, alignment: .center)
            case .completed:
                HStack {
                    Spacer()
                    Text("\(context.state.redScore ?? 0)")
                        .font(.system(size: 36, weight: .bold)).foregroundStyle(.red)
                    Text("--").font(.title2).foregroundStyle(.secondary)
                    Text("\(context.state.blueScore ?? 0)")
                        .font(.system(size: 36, weight: .bold)).foregroundStyle(.blue)
                    let tracked = context.attributes.trackedAllianceColor
                    let won = context.state.winningAlliance == tracked
                    Text(won ? "WIN" : "LOSS")
                        .font(.caption).fontWeight(.bold)
                        .foregroundStyle(won ? .green : .red)
                        .padding(.leading, 8)
                    Spacer()
                }
            }

            laAllianceLine(color: "red", teams: context.attributes.redTeams, opr: context.state.redAllianceOPR)
            laAllianceLine(color: "blue", teams: context.attributes.blueTeams, opr: context.state.blueAllianceOPR)

            if let rank = context.state.rank, let record = context.state.record {
                Text("Rank #\(rank) \u{00B7} \(record)").font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding()
    }

    private func laAllianceLine(color: String, teams: [String], opr: Double?) -> some View {
        HStack(spacing: 4) {
            Circle().fill(color == "red" ? Color.red : Color.blue).frame(width: 8, height: 8)
            ForEach(teams, id: \.self) { team in
                if team == "\(context.attributes.teamNumber)" {
                    Text(team).font(.caption).bold()
                } else {
                    Text(team).font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if let opr {
                Text("\u{03A3} OPR \(opr, specifier: "%.1f")")
                    .font(.caption2).foregroundStyle(color == "red" ? .red : .blue)
            }
        }
    }
}

private struct NexusLiveActivityTimes: View {
    let state: MatchActivityAttributes.ContentState

    private var phases: [(label: String, date: Date, isPast: Bool)] {
        let now = Date.now
        var result: [(String, Date, Bool)] = []
        if let d = state.nexusQueueTime { result.append(("Queue", d, d <= now)) }
        if let d = state.nexusOnDeckTime { result.append(("Deck", d, d <= now)) }
        if let d = state.nexusOnFieldTime { result.append(("Field", d, d <= now)) }
        if let d = state.nexusStartTime { result.append(("Start", d, d <= now)) }
        return result
    }

    private var nextIndex: Int? { phases.firstIndex { !$0.isPast } }

    var body: some View {
        HStack(spacing: 10) {
            ForEach(Array(phases.enumerated()), id: \.offset) { index, phase in
                VStack(spacing: 1) {
                    Text(phase.label)
                        .font(.system(size: 8))
                        .foregroundStyle(index == nextIndex ? .primary : .tertiary)
                    Text(formatTime(phase.date))
                        .font(.system(size: 11, weight: index == nextIndex ? .bold : .regular))
                        .foregroundColor(index == nextIndex ? .accentColor : (phase.isPast ? .gray : .secondary))
                }
            }
        }
    }

    private func formatTime(_ date: Date) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "h:mm"
        return fmt.string(from: date)
    }
}

private func nextNexusPhaseDate(state: MatchActivityAttributes.ContentState) -> Date? {
    let now = Date.now
    let phases: [Date?] = [
        state.nexusQueueTime, state.nexusOnDeckTime,
        state.nexusOnFieldTime, state.nexusStartTime
    ]
    return phases.compactMap { $0 }.first { $0 > now }
}

private func nextNexusPhaseLabel(state: MatchActivityAttributes.ContentState) -> String? {
    let now = Date.now
    let phases: [(String, Date?)] = [
        ("to queue", state.nexusQueueTime),
        ("to on deck", state.nexusOnDeckTime),
        ("to on field", state.nexusOnFieldTime),
        ("to start", state.nexusStartTime),
    ]
    return phases.first { _, date in
        guard let date else { return false }
        return date > now
    }?.0
}
