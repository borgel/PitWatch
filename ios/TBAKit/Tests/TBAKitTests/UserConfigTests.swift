import Testing
import Foundation
@testable import TBAKit

@Test func defaultConfig() {
    let config = UserConfig()
    #expect(config.teamNumber == nil)
    #expect(config.apiKey == nil)
    #expect(config.eventKeyOverride == nil)
    #expect(config.useScheduledTime == false)
    #expect(config.queueOffsetMinutes == 0)
    #expect(config.liveActivityMode == .nearMatch)
}

@Test func configRoundTrip() throws {
    var config = UserConfig()
    config.teamNumber = 1234
    config.apiKey = "test-key"
    config.useScheduledTime = true
    config.queueOffsetMinutes = 20
    config.liveActivityMode = .allDay

    let data = try JSONEncoder().encode(config)
    let decoded = try JSONDecoder().decode(UserConfig.self, from: data)
    #expect(decoded.teamNumber == 1234)
    #expect(decoded.apiKey == "test-key")
    #expect(decoded.useScheduledTime == true)
    #expect(decoded.queueOffsetMinutes == 20)
    #expect(decoded.liveActivityMode == .allDay)
}

@Test func isConfigured() {
    var config = UserConfig()
    #expect(config.isConfigured == false)
    config.teamNumber = 1234
    #expect(config.isConfigured == false)
    config.apiKey = "key"
    #expect(config.isConfigured == true)
}

@Test func teamKey() {
    var config = UserConfig()
    #expect(config.teamKey == nil)
    config.teamNumber = 1234
    #expect(config.teamKey == "frc1234")
}

@Test func queueOffset() {
    var config = UserConfig()
    #expect(config.queueOffset == 0)
    config.queueOffsetMinutes = 20
    #expect(config.queueOffset == 1200)
}

@Test func nexusApiKeyConfig() {
    var config = UserConfig()
    #expect(config.nexusApiKey == nil)
    #expect(config.isNexusConfigured == false)

    config.nexusApiKey = "test-nexus-key"
    #expect(config.isNexusConfigured == true)
}
