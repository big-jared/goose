package dev.goose.metro

import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.Provider
import kotlin.reflect.KClass

/**
 * Aggregates every feature's contributed [ScreenEntry] into one lookup. Feature modules contribute
 * with:
 * ```
 * @ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
 * @ClassKey(ProfileScreen::class)
 * @Inject
 * class ProfileUi(...) : ScreenUi<ProfileScreen>() { ... }
 * ```
 * The explicit `binding` is required for ScreenUi subclasses — Metro binds contributions as
 * their direct supertype (`ScreenUi<S>`), not the `ScreenEntry` this registry collects.
 *
 * Registries form a chain mirroring the graph tree: a child scope's registry (built by
 * `GooseScope` from a child graph's contributions) resolves its own entries first and falls
 * back to [parent] — so screens registered at AppScope keep working inside a child scope, and
 * a child-scoped screen's entry captures child-graph dependencies without ever being cached
 * beyond the child registry's own lifetime (the registry lives and dies with the composition
 * that remembered it, alongside its graph).
 */
class ScreenRegistry(
    private val entries: Map<KClass<*>, Provider<ScreenEntry>>,
    private val parent: ScreenRegistry? = null,
) {
    // Entries are stateless renderers; memoize per screen class so re-showing a screen (pop-back,
    // tab switch) doesn't re-run the entry's constructor injection. The cache lives in THIS
    // registry: child-scoped entries are cached in the child registry only.
    private val cache = java.util.concurrent.ConcurrentHashMap<KClass<*>, ScreenEntry>()

    fun entryFor(screen: Screen): ScreenEntry =
        entryForOrNull(screen)
            ?: error(
                "No ScreenEntry contributed for ${screen::class.qualifiedName}. " +
                    "Is the feature's :impl module on the app's runtime classpath? " +
                    "(For a scope-registered screen: is the enclosing GooseScope active?)"
            )

    private fun entryForOrNull(screen: Screen): ScreenEntry? {
        cache[screen::class]?.let { return it }
        val local = entries[screen::class]?.invoke()
        if (local != null) return cache.getOrPut(screen::class) { local }
        return parent?.entryForOrNull(screen)
    }
}
