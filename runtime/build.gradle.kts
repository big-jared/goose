plugins {
    id("goose.android.library")
}

android { namespace = "dev.goose.runtime" }

dependencies {
    api(libs.nav3.runtime)
    api(libs.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.lifecycle.viewmodel.compose)
}
