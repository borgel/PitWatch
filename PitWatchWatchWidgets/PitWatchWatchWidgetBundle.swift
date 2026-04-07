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
        StaticConfiguration(kind: kind, provider: WatchPlaceholderProvider()) { entry in
            Text("PW")
        }
        .configurationDisplayName("Next Match")
        .description("Track your team's next FRC match.")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryCorner])
    }
}

struct WatchPlaceholderProvider: TimelineProvider {
    func placeholder(in context: Context) -> WatchSimpleEntry { WatchSimpleEntry(date: .now) }
    func getSnapshot(in context: Context, completion: @escaping (WatchSimpleEntry) -> Void) {
        completion(WatchSimpleEntry(date: .now))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<WatchSimpleEntry>) -> Void) {
        completion(Timeline(entries: [WatchSimpleEntry(date: .now)], policy: .after(.now.addingTimeInterval(3600))))
    }
}

struct WatchSimpleEntry: TimelineEntry {
    let date: Date
}
