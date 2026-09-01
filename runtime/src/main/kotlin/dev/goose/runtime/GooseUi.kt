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
 *
 * Supported grammar (compile errors otherwise): a public or internal, top-level, non-suspend,
 * non-generic, non-extension `@Composable` function taking a `@Serializable` screen. Default
 * parameter values are allowed but the generated registration always supplies every argument.
 * Injected parameters may carry Metro qualifier annotations (`@Named` etc.); they are copied to
 * the generated provider.
 *
 * [scope] is the Metro scope the registration contributes to; the default (`Unit::class`
 * sentinel, keeping this module Metro-free) means `AppScope`. A custom scope registers the
 * screen in that scope's child graph instead: hosts make it renderable with `GooseScope` around
 * the subtree that owns the graph, and the screen's injected parameters resolve from the child
 * graph (session objects, flow-owned repositories), with everything else falling back to the
 * parent.
 *
 * Factory lookup for a ViewModel parameter: a nested assisted factory first, then any
 * top-level `@AssistedFactory` in the ViewModel's own package whose create has the goose
 * shape — which is where an app's own factory codegen puts them, since processors can only
 * emit top-level types. Two matches is a compile error.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class GooseUi(
    val screen: KClass<out Screen>,
    val scope: KClass<*> = Unit::class,
)
