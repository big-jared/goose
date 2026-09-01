plugins {
    id("goose.publish")
    id("goose.docs")
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(kotlin("compiler-embeddable"))

    testImplementation(kotlin("compiler-embeddable"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("dev.zacsweers.kctfork:core:0.13.0")
}
