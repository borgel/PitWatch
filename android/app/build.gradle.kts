plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pitwatch.app"
    // 37 for NotificationCompat's Live Update promotion APIs (they postdate base API 36); runs on 36+.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pitwatch.app"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Overridable for the local fake API (see docs/android-manual-test.md).
        buildConfigField("String", "TBA_BASE_URL", "\"${providers.gradleProperty("pitwatch.tbaBaseUrl").getOrElse("https://www.thebluealliance.com/api/v3")}\"")
        buildConfigField("String", "NEXUS_BASE_URL", "\"${providers.gradleProperty("pitwatch.nexusBaseUrl").getOrElse("https://frc.nexus/api/v1")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric reaches into JDK internals that JDK 17+ hides by default.
        unitTests.all {
            it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    // Same fixtures as :core — never copied.
    sourceSets.getByName("test").resources.directories += listOf(
        rootProject.file("../ios/TBAKit/Tests/TBAKitTests/Fixtures").path,
        rootProject.file("../scripts/fixtures").path,
    )
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.compose.material.icons.core)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.glance.appwidget.testing)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
