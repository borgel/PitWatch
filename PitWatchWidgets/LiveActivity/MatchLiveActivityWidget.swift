import SwiftUI
import WidgetKit
import ActivityKit
import TBAKit

struct MatchLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: MatchActivityAttributes.self) { context in
            LiveActivityLockScreenView(context: context)
        } dynamicIsland: { context in
            MatchDynamicIsland.build(for: context)
        }
    }
}
