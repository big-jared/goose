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
}

// Public-API surface tracking: apiDump writes per-module .api files, apiCheck (wired into
// `check`, so CI runs it) fails on any undumped change to the public API. Only the published
// modules participate.
apiValidation {
    ignoredProjects += listOf(
        "samples",
        "m1", "m2", "m3",
        "app",
        "feature-home-api", "feature-home-impl",
        "feature-profile-api", "feature-profile-impl",
        "feature-catalog-api", "feature-catalog-impl",
        "feature-cart-api", "feature-cart-impl",
    )
}
