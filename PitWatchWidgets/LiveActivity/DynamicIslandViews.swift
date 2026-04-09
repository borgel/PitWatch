import SwiftUI
import ActivityKit
import WidgetKit
import TBAKit

enum MatchDynamicIsland {
    static func build(for context: ActivityViewContext<MatchActivityAttributes>) -> DynamicIsland {
        DynamicIsland {
            DynamicIslandExpandedRegion(.leading) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(context.attributes.matchLabel).font(.headline)
                    if let rank = context.state.rank {
                        Text("Rank #\(rank)").font(.caption2).foregroundStyle(.secondary)
                    }
                }
            }
            DynamicIslandExpandedRegion(.trailing) {
                expandedTrailing(context: context)
            }
            DynamicIslandExpandedRegion(.bottom) {
                VStack(spacing: 2) {
                    diAllianceLine(color: "red", teams: context.attributes.redTeams,
                                   opr: context.state.redAllianceOPR, context: context)
                    diAllianceLine(color: "blue", teams: context.attributes.blueTeams,
                                   opr: context.state.blueAllianceOPR, context: context)
                }
            }
        } compactLeading: {
            compactLeadingView(context: context)
        } compactTrailing: {
            compactTrailingView(context: context)
        } minimal: {
            Circle()
                .fill(context.attributes.trackedAllianceColor == "red" ? Color.red : Color.blue)
                .frame(width: 6, height: 6)
        }
    }

    @ViewBuilder
    private static func compactLeadingView(context: ActivityViewContext<MatchActivityAttributes>) -> some View {
        HStack(spacing: 3) {
            Circle()
                .fill(context.attributes.trackedAllianceColor == "red" ? Color.red : Color.blue)
                .frame(width: 6, height: 6)
            if let phase = currentNexusPhase(state: context.state) {
                // Show phase state and your match number
                VStack(alignment: .leading, spacing: 0) {
                    Text(phase)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(nexusStatusColor(phase))
                    Text(context.attributes.matchLabel)
                        .font(.system(size: 9))
                        .foregroundStyle(.secondary)
                }
            } else {
                Text(context.attributes.matchLabel)
                    .font(.system(size: 12, weight: .semibold))
            }
        }
    }

    @ViewBuilder
    private static func compactTrailingView(context: ActivityViewContext<MatchActivityAttributes>) -> some View {
        switch context.state.matchState {
        case .completed:
            HStack(spacing: 1) {
                Text("\(context.state.redScore ?? 0)").foregroundStyle(.red)
                Text("-").foregroundStyle(.secondary)
                Text("\(context.state.blueScore ?? 0)").foregroundStyle(.blue)
            }
            .font(.system(size: 12, weight: .bold))
        case .inProgress:
            Text("LIVE")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(.green)
        case .upcoming, .imminent:
            let target = nextNexusPhaseDate(state: context.state)
                ?? context.state.queueTime ?? context.state.matchTime
            if let target {
                VStack(alignment: .trailing, spacing: 0) {
                    Text(target, style: .timer)
                        .font(.system(size: 12, weight: .bold))
                        .monospacedDigit()
                    if let nowQ = context.state.nowQueuing {
                        Text("Now: \(nowQ)")
                            .font(.system(size: 7))
                            .foregroundStyle(.tertiary)
                            .lineLimit(1)
                    }
                }
            } else {
                Text("--")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder
    private static func expandedTrailing(context: ActivityViewContext<MatchActivityAttributes>) -> some View {
        switch context.state.matchState {
        case .completed:
            HStack(spacing: 2) {
                Text("\(context.state.redScore ?? 0)").foregroundStyle(.red).bold()
                Text("–").foregroundStyle(.secondary)
                Text("\(context.state.blueScore ?? 0)").foregroundStyle(.blue).bold()
            }
            .font(.system(size: 18))
        case .inProgress:
            Text("LIVE")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundStyle(.green)
        case .upcoming, .imminent:
            VStack(alignment: .trailing) {
                if let status = context.state.nexusStatus {
                    Text(status.uppercased())
                        .font(.system(size: 8, weight: .bold))
                        .padding(.horizontal, 4).padding(.vertical, 1)
                        .background(nexusStatusColor(status).opacity(0.2), in: Capsule())
                        .foregroundStyle(nexusStatusColor(status))
                }
                let target = nextNexusPhaseDate(state: context.state)
                    ?? context.state.queueTime ?? context.state.matchTime
                if let target {
                    Text(target, style: .timer)
                        .font(.system(size: 18, weight: .bold))
                        .monospacedDigit()
                    Text(nextNexusPhaseLabel(state: context.state)
                         ?? (context.state.queueTime != nil ? "to queue" : "to match"))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private static func diAllianceLine(
        color: String, teams: [String], opr: Double?,
        context: ActivityViewContext<MatchActivityAttributes>
    ) -> some View {
        HStack(spacing: 3) {
            Circle().fill(color == "red" ? Color.red : Color.blue).frame(width: 6, height: 6)
            ForEach(teams, id: \.self) { team in
                if team == "\(context.attributes.teamNumber)" {
                    Text(team).font(.caption2).bold()
                } else {
                    Text(team).font(.caption2).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if let opr {
                Text(String(format: "%.1f", opr))
                    .font(.caption2).foregroundStyle(color == "red" ? .red : .blue)
            }
        }
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

/// Returns the current phase the team is in (most recent past phase).
/// e.g., if queue time has passed but on-deck hasn't, returns "QUEUING".
private func currentNexusPhase(state: MatchActivityAttributes.ContentState) -> String? {
    let now = Date.now
    let phases: [(String, Date?)] = [
        ("ON FIELD", state.nexusOnFieldTime),
        ("ON DECK", state.nexusOnDeckTime),
        ("QUEUING", state.nexusQueueTime),
    ]
    // Return the most advanced phase that has passed
    for (label, date) in phases {
        if let date, date <= now {
            return label
        }
    }
    // No phase has passed yet — check if we have Nexus times at all
    if state.nexusQueueTime != nil {
        return nil // Has Nexus data but not queuing yet
    }
    return nil
}
