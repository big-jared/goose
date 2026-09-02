package dev.goose.fragment

import dev.goose.runtime.Screen
import kotlin.reflect.KClass

/**
 * Marks a class as the [FragmentScreenNavigation] override for [screen]. The goose-compiler KSP
 * processor generates the entire Metro registration (the contribution, the map key, the provider
 * wired from the class's primary constructor):
 * ```
 * @GooseFragmentNavigation(HelpScreen::class)
 * class HelpNavigation : FragmentScreenNavigation {
 *     override fun navigate(request: FragmentNavigationRequest) {
 *         HelpDialogFragment().show(request.fragmentManager, "help")
 *     }
 * }
 * ```
 * Constructor parameters are injected from the app graph and may carry Metro qualifier
 * annotations; an `object` is provided as-is. Supported grammar (compile errors otherwise): a
 * public or internal, top-level, non-generic, concrete class or object implementing
 * [FragmentScreenNavigation], with a non-private primary constructor.
 *
 * There is no scope parameter: [FragmentNavigator] reads its override map from the app graph at
 * install time, so contributions are AppScope by construction. Overrides describe host
 * transaction mechanics, which don't depend on flow-scoped state; flow scoping applies to screen
 * UI (see `GooseUi.scope`), not to how the fragment host navigates.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GooseFragmentNavigation(val screen: KClass<out Screen>)

/**
 * Marks a class as the [ScreenFragmentBinder] mapping [screen] to its legacy fragment. The
 * goose-compiler KSP processor generates the entire Metro registration:
 * ```
 * @GooseFragmentBinder(DetailScreen::class)
 * class DetailFragmentBinder : ScreenFragmentBinder {
 *     override fun createFragment(screen: Screen) =
 *         DetailFragment.newInstance(screen as DetailScreen)
 * }
 * ```
 * Constructor parameters are injected from the app graph and may carry Metro qualifier
 * annotations; an `object` is provided as-is. Supported grammar (compile errors otherwise): a
 * public or internal, top-level, non-generic, concrete class or object implementing
 * [ScreenFragmentBinder], with a non-private primary constructor.
 *
 * There is no scope parameter: [FragmentNavigator] reads its binder map from the app graph at
 * install time, so contributions are AppScope by construction.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GooseFragmentBinder(val screen: KClass<out Screen>)
