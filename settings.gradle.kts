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
    ":samples:m1:feature-home-api",
    ":samples:m1:feature-home-impl",
    ":samples:m1:feature-profile-api",
    ":samples:m1:feature-profile-impl",
    ":samples:m1:app",
    ":samples:m2:feature-catalog-api",
    ":samples:m2:feature-catalog-impl",
    ":samples:m2:feature-cart-api",
    ":samples:m2:feature-cart-impl",
    ":samples:m2:app",
    ":samples:m3:app",
)
