import SwiftUI
import ActivityKit
import WidgetKit
import TBAKit

enum FRCDynamicIsland {
    static func build(for context: ActivityViewContext<FRCMatchAttributes>) -> DynamicIsland {
        DynamicIsland {
            DynamicIslandExpandedRegion(.bottom) {
                ExpandedLiveActivityView(
                    state: context.state,
                    attributes: context.attributes,
                    showChevronBar: false
                )
            }
        } compactLeading: {
            compactLeading(context: context)
        } compactTrailing: {
            compactTrailing(context: context)
        } minimal: {
            VStack(spacing: 1) {
                HStack(spacing: 2) {
                    Text(context.state.currentPhase.label.prefix(1))
                        .font(.system(size: 6, weight: .bold, design: .monospaced))
                        .foregroundStyle(context.state.currentPhase.color)
                    Circle()
                        .fill(context.attributes.alliance.dotColor)
                        .frame(width: 4, height: 4)
                }
                Text(compactCountdown(to: context.state.phaseDeadline))
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundStyle(.white)
                    .monospacedDigit()
            }
        }
    }

    @ViewBuilder
    private static func compactLeading(
        context: ActivityViewContext<FRCMatchAttributes>
    ) -> some View {
        HStack(spacing: 0) {
            phaseColumn(context: context)
            divider()
            nowColumn(context: context)
        }
    }

    @ViewBuilder
    private static func compactTrailing(
        context: ActivityViewContext<FRCMatchAttributes>
    ) -> some View {
        HStack(spacing: 0) {
            yourMatchColumn(context: context)
            Circle()
                .fill(context.attributes.alliance.dotColor)
                .frame(width: 7, height: 7)
                .padding(.leading, 6)
        }
    }

    private static func phaseColumn(
        context: ActivityViewContext<FRCMatchAttributes>
    ) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(context.state.currentPhase.label)
                .font(.system(size: 8.5, weight: .semibold, design: .monospaced))
                .tracking(0.4)
                .foregroundStyle(context.state.currentPhase.color)
            Text(compactCountdown(to: context.state.phaseDeadline))
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .kerning(-0.5)
                .foregroundStyle(.white)
                .monospacedDigit()
        }
        .padding(.leading, 8)
        .padding(.trailing, 6)
    }

    private static func nowColumn(
        context: ActivityViewContext<FRCMatchAttributes>
    ) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text("NOW")
                .font(.system(size: 8, weight: .regular, design: .monospaced))
                .tracking(0.4)
                .foregroundStyle(.white.opacity(0.30))
            Text("Q \(context.state.currentMatchOnField)")
                .font(.system(size: 13, weight: .bold, design: .monospaced))
                .foregroundStyle(.white.opacity(0.50))
        }
        .padding(.horizontal, 6)
    }

    private static func yourMatchColumn(
        context: ActivityViewContext<FRCMatchAttributes>
    ) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text("YOUR MATCH")
                .font(.system(size: 8, weight: .regular, design: .monospaced))
                .tracking(0.4)
                .foregroundStyle(context.state.currentPhase.color)
            Text("Q \(context.attributes.matchNumber)")
                .font(.system(size: 13, weight: .bold, design: .monospaced))
                .foregroundStyle(.white)
        }
        .padding(.leading, 6)
    }

    private static func allianceDot(
        context: ActivityViewContext<FRCMatchAttributes>
    ) -> some View {
        Circle()
            .fill(context.attributes.alliance.dotColor)
            .frame(width: 7, height: 7)
    }

    private static func divider() -> some View {
        Rectangle()
            .fill(.white.opacity(0.10))
            .frame(width: 0.5, height: 30)
    }

    private static func compactCountdown(to deadline: Date) -> String {
        let remaining = max(0, deadline.timeIntervalSinceNow)
        let totalMinutes = Int(remaining / 60)
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        return String(format: "%d:%02d", hours, minutes)
    }
}
