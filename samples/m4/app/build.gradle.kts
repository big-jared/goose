plugins {
    id("goose.android.application")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.goose.sample.m4"
    defaultConfig { applicationId = "dev.goose.sample.m4" }
}

dependencies {
    implementation(project(":runtime-nav3"))
    implementation(project(":runtime-mavericks"))

    // The "existing app" half: plain Dagger, compiled by Dagger's own KSP processor,
    // side by side with goose-compiler and Metro.
    implementation(libs.dagger)
    ksp(libs.dagger.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
}
