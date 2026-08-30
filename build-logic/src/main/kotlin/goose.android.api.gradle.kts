// A feature :api module: screens, results, shared-element keys. Serialization, no compose,
// no Metro — api modules carry contracts, not contributions. They may never depend on impl
// modules (that would re-export the impl to every consumer, silently defeating the modularity
// rule the feature plugin enforces).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

apply(plugin = "goose.android.base")

dev.goose.buildlogic.GooseModuleChecks.forbidImplProjectDependencies(project, "Feature api")
