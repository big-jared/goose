// A feature :impl module: compose UI + Metro contributions + serializer registration.
// Composes on goose.android.library (base android config + compose) and adds the modularity
// guard: an impl module may depend on :api modules and runtimes, never on another feature's impl.
plugins {
    id("goose.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("dev.zacsweers.metro")
}

dev.goose.buildlogic.GooseModuleChecks.forbidImplProjectDependencies(project, "Feature impl")
