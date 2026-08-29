plugins {
    id("goose.android.api")
}

android { namespace = "dev.goose.sample.m2.cart.api" }

dependencies {
    api(project(":runtime"))
    api(libs.serialization.core)
}
