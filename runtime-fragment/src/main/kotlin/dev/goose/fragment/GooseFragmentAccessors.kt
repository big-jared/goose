package dev.goose.fragment

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import kotlin.reflect.KClass

/** Accessor surface for hosts building a [FragmentNavigator] from the app graph. */
@ContributesTo(AppScope::class)
interface GooseFragmentAccessors {
    @Multibinds(allowEmpty = true)
    val fragmentBinders: Map<KClass<*>, ScreenFragmentBinder>
}
