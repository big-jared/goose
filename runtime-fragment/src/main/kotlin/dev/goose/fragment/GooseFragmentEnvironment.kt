package dev.goose.fragment

import dev.goose.metro.Goose
import dev.goose.metro.GooseRuntimeAccessors
import kotlin.reflect.KClass

/**
 * A hand-built [Goose] extended with the fragment-migration bindings a Metro graph would
 * aggregate by contribution: LEGACY fragment binders and per-screen transaction overrides.
 * Only needed while unmigrated fragments ride the stack — an app with no legacy fragments
 * hands the [Goose] itself to [dev.goose.metro.GooseGraphHolder].
 *
 * ```
 * class MyApp : Application(), GooseGraphHolder {
 *     override val gooseGraph: Any = GooseFragmentEnvironment(
 *         goose = Goose.Builder().addUi<ProfileScreen> { s, m -> ProfileUi(s, m) }.build(),
 *         binders = mapOf(TermsScreen::class to ScreenFragmentBinder { TermsFragment() }),
 *     )
 * }
 * ```
 */
class GooseFragmentEnvironment(
    override val goose: Goose,
    binders: Map<KClass<*>, ScreenFragmentBinder> = emptyMap(),
    navigationOverrides: Map<KClass<*>, FragmentScreenNavigation> = emptyMap(),
    /** Keyed by [dev.goose.runtime.Presentation] class — see [GoosePresentationNavigation]. */
    presentationNavigations: Map<KClass<*>, FragmentScreenNavigation> = emptyMap(),
) : GooseRuntimeAccessors, GooseFragmentAccessors {

    override val fragmentBinders: Map<KClass<*>, ScreenFragmentBinder> = binders.toMap()
    override val fragmentNavigationOverrides: Map<KClass<*>, FragmentScreenNavigation> =
        navigationOverrides.toMap()
    override val presentationNavigations: Map<KClass<*>, FragmentScreenNavigation> =
        presentationNavigations.toMap()
}
