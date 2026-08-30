package dev.goose.runtime

import kotlin.reflect.KClass

/**
 * Marks a composable function as the UI for [screen]. The goose-compiler KSP processor generates
 * the entire registration (the Metro contribution, the map key, the ScreenEntry adapter) and the
 * ViewModel wiring:
 * ```
 * @GooseUi(ProfileScreen::class)
 * @Composable
 * fun ProfileUi(state: ProfileState, viewModel: ProfileViewModel, modifier: Modifier) { ... }
 * ```
 * Parameter rules, by type; all optional, order doesn't matter:
 * - the screen class: receives the screen being rendered
 * - `Modifier`: receives the host's modifier
 * - a MavericksViewModel with a nested assisted `(initialState, navigator)` factory: receives a
 *   screen-scoped `screenViewModel` (retained across rotation, cleared on pop); the factory is
 *   injected from the graph
 * - that ViewModel's state class: receives `collectAsState().value`, so the function recomposes
 *   on state changes
 * - anything else: injected from the app graph, checked at compile time
 *
 * Flow-shared ViewModels are acquired explicitly with `flowViewModel()` inside the function,
 * never as parameters.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class GooseUi(val screen: KClass<out Screen>)
