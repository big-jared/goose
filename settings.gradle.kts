pluginManagement {
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
    ":runtime",
    ":runtime-metro",
    ":runtime-mavericks",
    ":runtime-nav3",
    ":runtime-fragment",
    ":samples:m1:feature-home-api",
    ":samples:m1:feature-home-impl",
    ":samples:m1:feature-profile-api",
    ":samples:m1:feature-profile-impl",
    ":samples:m1:app",
)
