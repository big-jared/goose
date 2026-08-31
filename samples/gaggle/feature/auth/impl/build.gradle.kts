plugins {
    id("goose.android.feature")
}

android { namespace = "dev.goose.gaggle.auth.impl" }

dependencies {
    api(project(":samples:gaggle:feature:auth:api"))
    implementation(project(":runtime-metro"))
    implementation(project(":runtime-mavericks"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.mavericks)
    implementation(libs.mavericks.compose)
}
