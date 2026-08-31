plugins {
    id("goose.android.api")
}

android { namespace = "dev.goose.gaggle.catalog.api" }

dependencies {
    api(project(":runtime"))
    api(libs.serialization.core)
}
