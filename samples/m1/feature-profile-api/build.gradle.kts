plugins {
    id("goose.android.api")
}

android { namespace = "dev.goose.sample.m1.profile.api" }

dependencies {
    api(project(":runtime"))
    api(libs.serialization.core)
}
