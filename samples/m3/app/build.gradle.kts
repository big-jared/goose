plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

android {
    namespace = "dev.goose.sample.m3"
    compileSdk = 37
    compileSdkMinor = 0
    defaultConfig {
        applicationId = "dev.goose.sample.m3"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":runtime-nav3"))
    implementation(project(":runtime-mavericks"))
    implementation(project(":runtime-fragment"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.fragment.ktx)
    implementation(libs.core.ktx)
}
