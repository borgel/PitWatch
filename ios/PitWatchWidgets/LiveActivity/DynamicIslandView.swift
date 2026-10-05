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
                    Text(context.state.currentPhase.glyph)
                        .font(.system(size: 6, weight: .bold, design: .monospaced))
                        .foregroundStyle(context.state.currentPhase.color)
                    Circle()
                        .fill(context.attributes.alliance.dotColor)
                        .frame(width: 4, height: 4)
                }
                PhaseCountdownText(deadline: context.state.phaseDeadline)
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
        phaseColumn(context: context)
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
            Text(context.state.currentPhase.stateLabel)
                .font(.system(size: 8.5, weight: .semibold, design: .monospaced))
                .tracking(0.4)
                .foregroundStyle(context.state.currentPhase.color)
            PhaseCountdownText(deadline: context.state.phaseDeadline)
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .kerning(-0.5)
                .foregroundStyle(.white)
                .monospacedDigit()
        }
        .padding(.leading, 8)
        .padding(.trailing, 6)
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

}
