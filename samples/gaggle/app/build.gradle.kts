plugins {
    id("goose.android.application")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.goose.gaggle"
    defaultConfig { applicationId = "dev.goose.gaggle" }
}

dependencies {
    implementation(project(":runtime-nav3"))
    implementation(project(":runtime-mavericks"))
    implementation(project(":runtime-fragment"))
    implementation(project(":samples:gaggle:feature:auth:api"))
    implementation(project(":samples:gaggle:feature:auth:impl"))
    implementation(project(":samples:gaggle:feature:catalog:api"))
    implementation(project(":samples:gaggle:feature:catalog:impl"))
    implementation(project(":samples:gaggle:feature:cart:api"))
    implementation(project(":samples:gaggle:feature:cart:impl"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.fragment.ktx)
    implementation(libs.mavericks)
    implementation(libs.mavericks.compose)
}
