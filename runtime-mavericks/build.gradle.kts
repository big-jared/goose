plugins {
    id("goose.publish")
    id("goose.docs")
    id("goose.api-tracking")
    id("goose.android.library")
    alias(libs.plugins.metro)
}

android {
    namespace = "dev.goose.mavericks"
    testOptions.targetSdk = 36
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(project(":runtime"))
    api(project(":runtime-metro"))
    api(libs.mavericks)
    api(libs.mavericks.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.lifecycle.viewmodel.compose)

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("org.robolectric:robolectric:4.16")
}
