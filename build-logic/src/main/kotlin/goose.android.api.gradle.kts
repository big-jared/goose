// A feature :api module: screens, results, shared-element keys. Serialization, no Metro —
// api modules carry contracts, not contributions. Presentation BEHAVIOR that rides on a screen
// contract (ScreenTransitions transforms, OverlayScreen dialog properties) is allowed: those
// compose-animation/ui types arrive transitively through :runtime, deliberately, so any module
// can push a screen and get its declared presentation. No compose UI beyond that. Api modules
// may never depend on impl modules (that would re-export the impl to every consumer, silently
// defeating the modularity rule the feature plugin enforces).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

apply(plugin = "goose.android.base")

dev.goose.buildlogic.GooseModuleChecks.forbidImplProjectDependencies(project, "Feature api")
