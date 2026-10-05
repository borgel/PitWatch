plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.ktor.client.core)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}

// One copy of every fixture: curated test fixtures live with the Swift package,
// raw API captures live in scripts/. Both suites read the same files.
sourceSets.test {
    resources.srcDir(rootProject.file("../ios/TBAKit/Tests/TBAKitTests/Fixtures"))
    resources.srcDir(rootProject.file("../scripts/fixtures"))
}

tasks.test {
    useJUnitPlatform()
}
