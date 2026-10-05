import SwiftUI
import TBAKit

@main
struct PitWatchWatchApp: App {
    @StateObject private var connectivity = ConnectivityManager.shared
    private let store = TBADataStore(containerURL: AppGroup.containerURL)

    var body: some Scene {
        WindowGroup {
            let config = store.loadConfig()
            if config.isConfigured {
                NavigationStack {
                    MatchListWatchView(config: config, store: store)
                }
            } else {
                VStack(spacing: 8) {
                    Image(systemName: "iphone.and.arrow.forward")
                        .font(.largeTitle).foregroundStyle(.secondary)
                    Text("Set up PitWatch on your iPhone")
                        .font(.caption).multilineTextAlignment(.center)
                }
            }
        }
    }
}
