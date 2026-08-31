pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "goose"

include(
    ":goose-compiler",
    ":runtime",
    ":runtime-metro",
    ":runtime-mavericks",
    ":runtime-nav3",
    ":runtime-fragment",
    ":samples:dagger-interop:app",
    ":samples:gaggle:app",
    ":samples:gaggle:feature:auth:api",
    ":samples:gaggle:feature:auth:impl",
    ":samples:gaggle:feature:catalog:api",
    ":samples:gaggle:feature:catalog:impl",
    ":samples:gaggle:feature:cart:api",
    ":samples:gaggle:feature:cart:impl",
)
