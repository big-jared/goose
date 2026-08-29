plugins {
    id("goose.android.library")
    alias(libs.plugins.metro)
}

android { namespace = "dev.goose.metro" }

dependencies {
    api(project(":runtime"))
    api(libs.serialization.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
}
