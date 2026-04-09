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
        BackgroundRefresh.register()
        BackgroundRefresh.scheduleNext(store: store)
    }

    var body: some Scene {
        WindowGroup {
            if config.isConfigured {
                NavigationStack {
                    MatchListView(config: $config, store: store)
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                NavigationLink {
                                    SettingsView(
                                        config: $config,
                                        store: store,
                                        onForceRefresh: {
                                            guard let apiKey = config.apiKey else { return }
                                            try? await BackgroundRefresh.performRefresh(
                                                store: store, config: config,
                                                apiKey: apiKey, forceReload: true
                                            )
                                        },
                                        onStartLiveActivity: {
                                            #if canImport(ActivityKit) && os(iOS)
                                            let cache = store.loadEventCache()
                                            let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")
                                            if let next = schedule.nextMatch {
                                                let nexusMatch = NexusMatchMerge.nexusInfo(for: next, in: cache.nexusEvent)
                                                let _ = try? LiveActivityManager.shared.startActivity(
                                                    match: next,
                                                    teamNumber: config.teamNumber ?? 0,
                                                    teamKey: config.teamKey ?? "",
                                                    nexusMatch: nexusMatch,
                                                    nexusEvent: cache.nexusEvent
                                                )
                                            }
                                            #endif
                                        }
                                    )
                                } label: {
                                    Image(systemName: "gear")
                                }
                            }
                            ToolbarItem(placement: .topBarLeading) {
                                NavigationLink {
                                    EventPickerView(
                                        config: config,
                                        store: store,
                                        selectedEventKey: $config.eventKeyOverride,
                                        autoDetectedEventKey: store.loadEventCache().event?.key
                                    )
                                } label: {
                                    Image(systemName: "calendar")
                                }
                            }
                        }
                }
            } else {
                SetupView(config: $config) {
                    store.saveConfig(config)
                    BackgroundRefresh.scheduleNext(store: store)
                }
            }
        }
    }
}
