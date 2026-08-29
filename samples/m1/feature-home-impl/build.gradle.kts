plugins {
    id("goose.android.feature")
}

android { namespace = "dev.goose.sample.m1.home.impl" }

dependencies {
    api(project(":samples:m1:feature-home-api"))
    implementation(project(":samples:m1:feature-profile-api"))
    implementation(project(":runtime-mavericks"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
}
