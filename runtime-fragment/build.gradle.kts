plugins {
    id("goose.publish")
    id("goose.docs")
    id("goose.api-tracking")
    id("goose.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

android {
    namespace = "dev.goose.fragment"
    testOptions.targetSdk = 36
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(project(":runtime"))
    api(project(":runtime-metro"))
    api(libs.fragment.ktx)
    api(libs.fragment.compose)
    api(libs.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation(platform(libs.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
}
