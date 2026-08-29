plugins {
    id("goose.android.feature")
}

android { namespace = "dev.goose.sample.m1.profile.impl" }

dependencies {
    api(project(":samples:m1:feature-profile-api"))
    implementation(project(":runtime-mavericks"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
}
