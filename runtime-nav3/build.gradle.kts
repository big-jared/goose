plugins {
    id("goose.android.library")
    alias(libs.plugins.metro)
}

android { namespace = "dev.goose.nav3" }

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
}
