// Contracts + the session scope. Unlike a plain :api module this one applies Metro: scope
// markers, the graph extension, and the SessionManager singleton are contracts other features
// compile against, and they carry Metro annotations.
plugins {
    id("goose.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.metro)
}

android { namespace = "dev.goose.gaggle.auth.api" }

dependencies {
    api(project(":runtime"))
    api(project(":runtime-metro"))
    api(libs.serialization.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
}
