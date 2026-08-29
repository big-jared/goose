// A feature :impl module: compose UI + Metro contributions + serializer registration.
// Also enforces the modularity rule: an impl module may depend on :api modules and runtimes,
// never on another feature's :impl.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("dev.zacsweers.metro")
}

apply(plugin = "goose.android.base")

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    buildFeatures { compose = true }
}

afterEvaluate {
    val offending = configurations
        .filter { it.name in setOf("api", "implementation") }
        .flatMap { it.dependencies }
        .filterIsInstance<ProjectDependency>()
        .map { it.path }
        .filter { it != path && it.substringAfterLast(":").endsWith("-impl") }
    check(offending.isEmpty()) {
        "Feature impl module $path depends on other impl modules: $offending. " +
            "Features may only depend on :api modules — navigate via screens, not implementations."
    }
}
