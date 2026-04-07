import Foundation
import WatchConnectivity
import TBAKit

class ConnectivityManager: NSObject, ObservableObject, WCSessionDelegate {
    static let shared = ConnectivityManager()
    @Published var lastSyncDate: Date?

    override init() {
        super.init()
        if WCSession.isSupported() {
            WCSession.default.delegate = self
            WCSession.default.activate()
        }
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {}

    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        guard let data = userInfo["eventCache"] as? Data else { return }
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        if let cache = try? JSONDecoder().decode(EventCache.self, from: data) {
            store.saveEventCache(cache)
            DispatchQueue.main.async {
                self.lastSyncDate = Date()
            }
        }
    }
}
