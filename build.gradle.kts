plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1"
    id("org.jetbrains.dokka") version "2.2.0"
}

dependencies {
    dokka(project(":runtime"))
    dokka(project(":runtime-metro"))
    dokka(project(":runtime-mavericks"))
    dokka(project(":runtime-nav3"))
    dokka(project(":runtime-fragment"))
    dokka(project(":goose-compiler"))
}

// Public-API surface tracking: apiDump writes per-module .api files, apiCheck (wired into
// `check`, so CI runs it) fails on any undumped change to the public API. Only the published
// modules participate.
apiValidation {
    ignoredProjects += listOf(
        "samples", "gaggle", "dagger-interop", "feature", "app",
        "auth", "catalog", "cart", "api", "impl",
    )
}
