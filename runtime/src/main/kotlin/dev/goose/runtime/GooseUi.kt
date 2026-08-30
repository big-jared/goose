package dev.goose.runtime

import kotlin.reflect.KClass

/**
 * Marks a composable function as the UI for [screen]. The goose-compiler KSP processor generates
 * the entire registration (the Metro contribution, the map key, the ScreenEntry adapter):
 * ```
 * @GooseUi(ProfileScreen::class)
 * @Composable
 * fun ProfileUi(screen: ProfileScreen, modifier: Modifier, vmFactory: ProfileViewModel.Factory) {
 *     val vm = screenViewModel<ProfileViewModel, ProfileState>(screen, vmFactory::create)
 *     ...
 * }
 * ```
 * Parameter rules: a parameter typed as the screen receives the screen, a `Modifier` parameter
 * receives the host's modifier, and every other parameter is injected from the app graph
 * (checked at compile time). Both are optional; order doesn't matter.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class GooseUi(val screen: KClass<out Screen>)
