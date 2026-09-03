plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1"
    id("org.jetbrains.dokka") version "2.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
}

// Coverage: kover aggregates the published modules into one merged report at the root
// (koverHtmlReport / koverXmlReport), and koverVerify holds the line-coverage floor. Samples
// and tools are deliberately out of scope: they demonstrate the library, the library is what
// the floor protects.
val coveredModules = listOf(
    ":runtime", ":runtime-metro", ":runtime-mavericks", ":runtime-nav3", ":runtime-fragment",
    ":goose-compiler", ":goose-compiler-plugin",
)
coveredModules.forEach { modulePath ->
    project(modulePath).plugins.apply("org.jetbrains.kotlinx.kover")
    dependencies.add("kover", project(modulePath))
}
// The gaggle sample's Robolectric suites are the library's integration tests (samples/README.md
// maps behavior to test), and they are what exercises the @Composable hosts the unit suites
// cannot compose. Merging its RUNS counts that coverage; the filter below keeps its own
// classes out of the report, so the floor still measures only library code.
project(":samples:gaggle:app").plugins.apply("org.jetbrains.kotlinx.kover")
dependencies.add("kover", project(":samples:gaggle:app"))

kover {
    reports {
        filters {
            excludes {
                // Compiler-generated bodies, not hand-written logic: Compose's lambda
                // containers, kotlinx-serialization's synthesized serializers, and Metro's
                // DI mirrors. Sample/tooling classes ride along with gaggle's merged test
                // runs and are not what the floor measures.
                classes(
                    "*ComposableSingletons*", "*\$serializer", "*\$BindsMirror",
                    "dev.goose.gaggle.*",
                )
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }
        total {
            verify {
                rule("published-module line coverage") {
                    minBound(80)
                }
            }
        }
    }
}

// Static analysis on every module. Rules come from the default rule sets plus the in-repo
// :tools:detekt-rules set (NoFullyQualifiedReference), tuned in config/detekt/detekt.yml.
// The plain `detekt` task parses without compiling, so it stays fast enough for CI's first step.
allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        parallel = true
    }
    // The rules module can't lint with a plugin jar built from itself.
    if (path != ":tools:detekt-rules") {
        dependencies.add("detektPlugins", dependencies.project(":tools:detekt-rules"))
    }
}

dependencies {
    dokka(project(":runtime"))
    dokka(project(":runtime-metro"))
    dokka(project(":runtime-mavericks"))
    dokka(project(":runtime-nav3"))
    dokka(project(":runtime-fragment"))
    dokka(project(":goose-compiler"))
    dokka(project(":goose-compiler-plugin"))
}

// Public-API surface tracking: apiDump writes per-module .api files, apiCheck (wired into
// `check`, so CI runs it) fails on any undumped change to the public API. Only the published
// modules participate.
apiValidation {
    ignoredProjects += listOf(
        "samples", "gaggle", "dagger-interop", "feature", "app",
        "auth", "catalog", "cart", "api", "impl",
        "tools", "detekt-rules",
    )
}
