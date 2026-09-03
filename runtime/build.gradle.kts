plugins {
    id("goose.publish")
    id("goose.docs")
    id("goose.api-tracking")
    id("goose.android.library")
}

android {
    namespace = "dev.goose.runtime"
    defaultConfig.consumerProguardFiles.add(file("consumer-rules.pro"))
    // Result-correlation tests are pure JVM; Looper.getMainLooper() -> null makes
    // requireMainThread a no-op there, as documented.
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    api(libs.nav3.runtime)
    api(libs.coroutines.android)
    // api: ScreenTransitions and sharedScreenElement expose compose-animation/ui types.
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.lifecycle.viewmodel.compose)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("org.robolectric:robolectric:4.16")
}
