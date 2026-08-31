// A Goose sample app: compose + Metro graph assembly + the standard test harness
// (instrumented compose tests on-device, the same suites on Robolectric).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.zacsweers.metro")
    id("com.google.devtools.ksp")
}

apply(plugin = "goose.android.base")

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    buildFeatures { compose = true }
    defaultConfig {
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions.unitTests.isIncludeAndroidResources = true
    // Gradle managed device for CI: `./gradlew ciDebugAndroidTest` runs every sample's
    // instrumented suite on a headless ATD emulator (KVM on the runner).
    testOptions.managedDevices.localDevices.create("ci") {
        device = "Pixel 8"
        apiLevel = 35
        systemImageSource = "aosp-atd"
    }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val composeBom = libs.findLibrary("compose-bom").get()

dependencies {
    "ksp"(project(":goose-compiler"))

    "androidTestImplementation"(platform(composeBom))
    "androidTestImplementation"("androidx.compose.ui:ui-test-junit4")
    "androidTestImplementation"("androidx.test.ext:junit:1.3.0")
    "androidTestImplementation"("androidx.test:runner:1.7.0")
    "androidTestImplementation"("androidx.test.espresso:espresso-core:3.7.0")
    "debugImplementation"("androidx.compose.ui:ui-test-manifest")

    "testImplementation"(platform(composeBom))
    "testImplementation"("androidx.compose.ui:ui-test-junit4")
    "testImplementation"("androidx.test.ext:junit:1.3.0")
    "testImplementation"("androidx.test.espresso:espresso-core:3.7.0")
    "testImplementation"("org.robolectric:robolectric:4.16")
}
