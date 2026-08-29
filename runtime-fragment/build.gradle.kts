plugins {
    id("goose.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

android { namespace = "dev.goose.fragment" }

dependencies {
    api(project(":runtime"))
    api(project(":runtime-metro"))
    api(libs.fragment.ktx)
    api(libs.fragment.compose)
    api(libs.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
}
