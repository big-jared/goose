package dev.goose.metro

import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass

/**
 * Aggregates every feature's contributed [ScreenEntry] into one lookup. Feature modules contribute
 * with:
 * ```
 * @ContributesIntoMap(AppScope::class)
 * @ClassKey(ProfileScreen::class)
 * @Inject
 * class ProfileEntry(...) : ScreenEntry { ... }
 * ```
 */
@SingleIn(AppScope::class)
@Inject
class ScreenRegistry(
    private val entries: Map<KClass<*>, Provider<ScreenEntry>>,
) {
    fun entryFor(screen: Screen): ScreenEntry =
        entries[screen::class]?.invoke()
            ?: error(
                "No ScreenEntry contributed for ${screen::class.qualifiedName}. " +
                    "Is the feature's :impl module on the app's runtime classpath?"
            )
}
