plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

android {
    namespace = "dev.goose.sample.m2"
    compileSdk = 37
    compileSdkMinor = 0
    defaultConfig {
        applicationId = "dev.goose.sample.m2"
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
    // App source touches only :api modules + runtimes; :impl modules are here solely so Metro
    // aggregates their contributions. Zero impl->impl edges anywhere in this sample.
    implementation(project(":runtime-nav3"))
    implementation(project(":runtime-mavericks"))
    implementation(project(":samples:m2:feature-catalog-api"))
    implementation(project(":samples:m2:feature-catalog-impl"))
    implementation(project(":samples:m2:feature-cart-api"))
    implementation(project(":samples:m2:feature-cart-impl"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
}
