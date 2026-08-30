// Maven Central publication for the consumable goose modules (runtimes + compiler). Applied
// explicitly per module, never by the library convention, so sample modules stay unpublishable.
// Locally, `publishToMavenLocal` works with no credentials; the release workflow supplies
// mavenCentralUsername/mavenCentralPassword and the signing.* properties (see RELEASE.md).
plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    val moduleArtifact = if (name.startsWith("goose-")) name else "goose-$name"
    coordinates(
        groupId = "io.github.big-jared",
        artifactId = moduleArtifact,
        version = providers.gradleProperty("VERSION_NAME").getOrElse("0.1.0-SNAPSHOT"),
    )

    pom {
        name.set(moduleArtifact)
        description.set("Compose navigation for apps that grew up on MvRx and fragments: Metro + Mavericks + Navigation 3 behind a Circuit-like API, with a per-screen fragment migration path")
        inceptionYear.set("2026")
        url.set("https://github.com/big-jared/goose")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("big-jared")
                name.set("Jared Guttromson")
                email.set("jaredguttromson@gmail.com")
            }
        }

        scm {
            url.set("https://github.com/big-jared/goose")
        }
    }

    publishToMavenCentral()
    // Signing is release-only, opted into explicitly (-Pgoose.releaseSigning=true, as the
    // publish workflow does). Gating on the flag rather than on key presence keeps local
    // publishToMavenLocal independent of whatever signing state this machine happens to have.
    if (providers.gradleProperty("goose.releaseSigning").isPresent) {
        signAllPublications()
    }
}
