plugins {
    id("goose.android.feature")
}

android { namespace = "dev.goose.gaggle.catalog.impl" }

dependencies {
    api(project(":samples:gaggle:feature:catalog:api"))
    implementation(project(":samples:gaggle:feature:auth:api"))
    implementation(project(":samples:gaggle:feature:cart:api"))
    implementation(project(":runtime-metro"))
    implementation(project(":runtime-mavericks"))
    implementation(project(":runtime-nav3"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.mavericks)
    implementation(libs.mavericks.compose)
}
