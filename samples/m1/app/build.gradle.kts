plugins {
    id("goose.android.application")
}

android {
    namespace = "dev.goose.sample.m1"
    defaultConfig { applicationId = "dev.goose.sample.m1" }
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
