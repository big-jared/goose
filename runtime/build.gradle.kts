plugins {
    id("goose.android.library")
}

android {
    namespace = "dev.goose.runtime"
    defaultConfig.consumerProguardFiles.add(file("consumer-rules.pro"))
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
}
