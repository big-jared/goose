package dev.goose.metro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Implemented by the app's [android.app.Application] so hosts that can't take composition locals
 * (fragments, activities created by the framework) can still reach the Metro graph.
 */
interface GooseGraphHolder {
    val gooseGraph: Any
}

/**
 * The app's merged Metro graph. Because a `@DependencyGraph(AppScope::class)` implements every
 * `@ContributesTo(AppScope::class)` interface, casting it to a contributed accessor interface is
 * compile-safe dependency access — see [gooseGraph].
 */
val LocalGooseGraph = staticCompositionLocalOf<Any> {
    error("No Goose graph provided. Wrap your content in CompositionLocalProvider(LocalGooseGraph provides graph).")
}

/**
 * One-off injection inside composables without a service locator: declare a
 * `@ContributesTo(AppScope::class)` accessor interface in any module, then
 * `gooseGraph<MyAccessor>().myRepo`.
 */
@Composable
inline fun <reified T> gooseGraph(): T =
    LocalGooseGraph.current as? T
        ?: error("Goose graph does not implement ${T::class.qualifiedName}. Is it @ContributesTo(AppScope::class)?")
