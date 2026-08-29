plugins {
    id("goose.android.application")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.goose.sample.m3"
    defaultConfig { applicationId = "dev.goose.sample.m3" }
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
