// Public-API surface tracking for the published ANDROID modules, using kotlinx
// binary-compatibility-validator's task classes registered by hand: the BCV *plugin* detects
// Kotlin via the kotlin-android plugin id, which AGP 9's built-in Kotlin never applies, so the
// plugin silently skips these modules. Driving KotlinApiBuildTask/KotlinApiCompareTask directly
// against the release compilation gives the same .api dumps and the same check.
//
// `apiDump` refreshes api/<module>.api; `apiCheck` (wired into `check`, so CI runs it) fails on
// any unrecorded public-API change. The JVM goose-compiler is covered by the regular BCV plugin
// at the root.
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask

val apiBuild = tasks.register<KotlinApiBuildTask>("apiBuild") {
    val compile = tasks.named("compileReleaseKotlin")
    dependsOn(compile)
    inputClassesDirs.from(compile.map { it.outputs.files })
    // Project dependencies need AGP's classes-jar artifact view; plain configuration
    // resolution outside a compilation hits variant ambiguity.
    inputDependencies.from(provider {
        configurations.getByName("releaseCompileClasspath").incoming
            .artifactView {
                attributes {
                    attribute(Attribute.of("artifactType", String::class.java), "android-classes-jar")
                }
                lenient(true)
            }
            .files
    })
    outputApiFile.set(layout.buildDirectory.file("api/${project.name}.api"))
}

tasks.register<Copy>("apiDump") {
    from(apiBuild.flatMap { it.outputApiFile })
    into(layout.projectDirectory.dir("api"))
}

val apiCheck = tasks.register<KotlinApiCompareTask>("apiCheck") {
    dependsOn(apiBuild)
    projectApiFile.set(layout.projectDirectory.file("api/${project.name}.api"))
    generatedApiFile.set(apiBuild.flatMap { it.outputApiFile })
}

// `check` is created later by the android plugin; hook it whenever it appears.
tasks.matching { it.name == "check" }.configureEach {
    dependsOn(apiCheck)
}
