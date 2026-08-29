plugins {
    id("goose.android.application")
}

android {
    namespace = "dev.goose.sample.m2"
    defaultConfig { applicationId = "dev.goose.sample.m2" }
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
