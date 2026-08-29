plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

android {
    namespace = "dev.goose.sample.m1"
    compileSdk = 37
    compileSdkMinor = 0
    defaultConfig {
        applicationId = "dev.goose.sample.m1"
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
    // Source code only references :api modules and the runtimes; the :impl modules are on the
    // classpath purely so Metro aggregates their contributions into the app graph.
    implementation(project(":runtime-nav3"))
    implementation(project(":runtime-mavericks"))
    implementation(project(":samples:m1:feature-home-api"))
    implementation(project(":samples:m1:feature-home-impl"))
    implementation(project(":samples:m1:feature-profile-api"))
    implementation(project(":samples:m1:feature-profile-impl"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
}
