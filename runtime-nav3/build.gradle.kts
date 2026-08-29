plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

android {
    namespace = "dev.goose.nav3"
    compileSdk = 37
    compileSdkMinor = 0
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
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
}
