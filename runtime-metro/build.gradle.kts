plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

android {
    namespace = "dev.goose.metro"
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
    api(libs.serialization.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
}
