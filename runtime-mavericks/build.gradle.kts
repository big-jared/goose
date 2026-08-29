plugins {
    id("goose.android.library")
    alias(libs.plugins.metro)
}

android { namespace = "dev.goose.mavericks" }

dependencies {
    api(project(":runtime"))
    api(project(":runtime-metro"))
    api(libs.mavericks)
    api(libs.mavericks.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.lifecycle.viewmodel.compose)
}
