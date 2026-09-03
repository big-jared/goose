package dev.goose.fragment

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Qualifier
import kotlin.reflect.KClass

/**
 * Distinguishes the presentation-keyed navigation map from the screen-keyed override map —
 * both are `Map<KClass<*>, FragmentScreenNavigation>`, differing only in what the key means.
 * Applied by generated `@GoosePresentationNavigation` registrations; hand-written ones need it
 * on their `@Provides` too.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PresentationNavigations

/** Accessor surface for hosts building a [FragmentNavigator] from the app graph. */
@ContributesTo(AppScope::class)
interface GooseFragmentAccessors {
    @Multibinds(allowEmpty = true)
    val fragmentBinders: Map<KClass<*>, ScreenFragmentBinder>

    @Multibinds(allowEmpty = true)
    val fragmentNavigationOverrides: Map<KClass<*>, FragmentScreenNavigation>

    /** Keyed by [dev.goose.runtime.Presentation] class, not screen class. */
    @Multibinds(allowEmpty = true)
    @PresentationNavigations
    val presentationNavigations: Map<KClass<*>, FragmentScreenNavigation>
}
