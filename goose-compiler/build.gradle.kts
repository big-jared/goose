plugins {
    id("goose.publish")
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.ksp.api)

    testImplementation("junit:junit:4.13.2")
    testImplementation("dev.zacsweers.kctfork:ksp:0.13.0")
}
