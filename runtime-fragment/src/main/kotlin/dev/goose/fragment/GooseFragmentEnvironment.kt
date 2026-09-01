package dev.goose.fragment

import dev.goose.metro.GooseEnvironment
import dev.goose.metro.GooseRuntimeAccessors
import kotlin.reflect.KClass

/**
 * A hand-built [GooseEnvironment] extended with the fragment-migration bindings a Metro graph
 * would aggregate by contribution: LEGACY fragment binders (typed screens that create old
 * fragments) and per-screen transaction overrides. Only needed while unmigrated fragments ride
 * the stack — an environment with no legacy fragments uses [GooseEnvironment] alone.
 *
 * ```
 * class MyApp : Application(), GooseGraphHolder {
 *     override val gooseGraph: Any = GooseFragmentEnvironment(
 *         base = GooseEnvironment.Builder().addUi<ProfileScreen> { s, m -> ProfileUi(s, m) }.build(),
 *         binders = mapOf(TermsScreen::class to ScreenFragmentBinder { TermsFragment() }),
 *     )
 * }
 * ```
 */
class GooseFragmentEnvironment(
    base: GooseEnvironment,
    binders: Map<KClass<*>, ScreenFragmentBinder> = emptyMap(),
    navigationOverrides: Map<KClass<*>, FragmentScreenNavigation> = emptyMap(),
) : GooseRuntimeAccessors by base, GooseFragmentAccessors {

    override val fragmentBinders: Map<KClass<*>, ScreenFragmentBinder> = binders.toMap()

    override val fragmentNavigationOverrides: Map<KClass<*>, FragmentScreenNavigation> =
        navigationOverrides.toMap()
}
