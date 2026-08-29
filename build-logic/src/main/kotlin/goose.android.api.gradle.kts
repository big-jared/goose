// A feature :api module: screens, results, shared-element keys. Serialization, no compose,
// no Metro — api modules carry contracts, not contributions.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

apply(plugin = "goose.android.base")
