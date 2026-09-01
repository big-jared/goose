// Dokka API docs for a published module; the root aggregates all of them into one site.
plugins {
    id("org.jetbrains.dokka")
}

/**
 * AGP 9's built-in Kotlin means org.jetbrains.kotlin.android is never applied, so Dokka's
 * KotlinAdapter registers NO source sets for android modules — they silently published EMPTY
 * docs (only the pure-JVM goose-compiler had content on the site). Same blindness BCV had,
 * same style of fix: register the main source set by hand. The classpath is the release
 * compile classpath viewed as classes jars (AARs unwrapped) plus android.jar, so android and
 * compose types resolve instead of degrading every signature and link.
 */
afterEvaluate {
    if (extensions.findByName("android") == null) return@afterEvaluate

    // Deferred: AGP creates the variant configurations after this afterEvaluate runs.
    val classesJars = provider {
        configurations.getByName("releaseCompileClasspath").incoming
            .artifactView {
                attributes {
                    attribute(Attribute.of("artifactType", String::class.java), "android-classes-jar")
                }
            }.files
    }

    val sdkDir = System.getenv("ANDROID_HOME")?.let(::file)
        ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { props ->
            java.util.Properties().apply { props.inputStream().use(::load) }
                .getProperty("sdk.dir")?.let(::file)
        }
    // Matches compileSdk 37 + compileSdkMinor 0 in goose.android.base; the CI docs job installs
    // this exact platform id.
    val androidJar = sdkDir?.resolve("platforms/android-37.0/android.jar")?.takeIf { it.exists() }

    dokka {
        dokkaSourceSets.register("main") {
            sourceRoots.from(layout.projectDirectory.dir("src/main/kotlin"))
            classpath.from(classesJars)
            if (androidJar != null) classpath.from(androidJar)
        }
    }
}
