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
            Text(context.attributes.matchLabel)
                .font(.system(size: 12, weight: .semibold))
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
            if let target = context.state.queueTime ?? context.state.matchTime {
                Text(target, style: .timer)
                    .font(.system(size: 12, weight: .bold))
                    .monospacedDigit()
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
            if let target = context.state.queueTime ?? context.state.matchTime {
                VStack(alignment: .trailing) {
                    Text(target, style: .timer)
                        .font(.system(size: 18, weight: .bold))
                        .monospacedDigit()
                    Text(context.state.queueTime != nil ? "to queue" : "to match")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            } else {
                Text("--")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(.secondary)
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
