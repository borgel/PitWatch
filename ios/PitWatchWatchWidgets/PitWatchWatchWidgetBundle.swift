import SwiftUI
import WidgetKit

@main
struct PitWatchWatchWidgetBundle: WidgetBundle {
    var body: some Widget {
        WatchNextMatchWidget()
    }
}

struct WatchNextMatchWidget: Widget {
    let kind = "WatchNextMatchWidget"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: WatchComplicationProvider()) { entry in
            WatchWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Next Match")
        .description("Track your team's next FRC match.")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular])
    }
}

struct WatchWidgetEntryView: View {
    @Environment(\.widgetFamily) var family
    let entry: PhaseComplicationEntry
    var body: some View {
        switch family {
        case .accessoryCircular: CircularComplicationView(entry: entry)
        case .accessoryRectangular: RectangularComplicationView(entry: entry)
        default: CircularComplicationView(entry: entry)
        }
    }
}
