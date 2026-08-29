// Compose-enabled Android library (the runtime modules).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

apply(plugin = "goose.android.base")

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    buildFeatures { compose = true }
}
