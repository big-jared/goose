plugins {
    id("goose.publish")
    id("goose.api-tracking")
    id("goose.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.metro)
}

android {
    namespace = "dev.goose.nav3"
    testOptions.targetSdk = 36
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(project(":runtime"))
    api(project(":runtime-metro"))
    api(libs.nav3.runtime)
    api(libs.nav3.ui)
    api(libs.lifecycle.viewmodel.nav3)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("org.robolectric:robolectric:4.16")
}
