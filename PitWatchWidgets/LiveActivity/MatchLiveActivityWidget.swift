import SwiftUI
import WidgetKit
import ActivityKit
import TBAKit

struct MatchLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FRCMatchAttributes.self) { context in
            ExpandedLiveActivityView(context: context)
        } dynamicIsland: { context in
            FRCDynamicIsland.build(for: context)
        }
    }
}
