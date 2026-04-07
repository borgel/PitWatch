import SwiftUI
import TBAKit

@main
struct PitWatchApp: App {
    @State private var config: UserConfig
    private let store: TBADataStore

    init() {
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        self.store = store
        self._config = State(initialValue: store.loadConfig())
    }

    var body: some Scene {
        WindowGroup {
            if config.isConfigured {
                Text("Match List (coming soon)")
            } else {
                SetupView(config: $config) {
                    store.saveConfig(config)
                }
            }
        }
    }
}
