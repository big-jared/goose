package dev.goose.mavericks

import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import kotlin.reflect.KClass

/**
 * Bridges Metro assisted injection into Mavericks VM creation — the Metro port of
 * mavericks-hilt's AssistedViewModelFactory map.
 *
 * Feature modules bind one per ViewModel:
 * ```
 * @ContributesTo(AppScope::class)
 * interface ProfileVmModule {
 *   companion object {
 *     @Provides @IntoMap @ClassKey(ProfileViewModel::class)
 *     fun creator(factory: ProfileViewModel.Factory): MavericksVmCreator =
 *       MavericksVmCreator { state, screen, nav ->
 *         factory.create(state as ProfileState, screen as ProfileScreen, nav)
 *       }
 *   }
 * }
 * ```
 */
fun interface MavericksVmCreator {
    fun create(initialState: MavericksState, screen: Screen, navigator: Navigator): MavericksViewModel<*>
}

/**
 * Checked-cast adapter for the common binding shape, so features don't hand-write `as` casts:
 * ```
 * @Provides @IntoMap @ClassKey(ProfileViewModel::class)
 * fun creator(factory: ProfileViewModel.Factory): MavericksVmCreator =
 *   mavericksVmCreator<ProfileState> { state, nav -> factory.create(state, nav) }
 * ```
 */
inline fun <reified S : MavericksState> mavericksVmCreator(
    crossinline create: (initialState: S, navigator: Navigator) -> MavericksViewModel<S>,
): MavericksVmCreator = MavericksVmCreator { state, _, navigator ->
    create(state as S, navigator)
}

/** Variant for ViewModels that also take their typed screen. */
inline fun <reified S : MavericksState, reified P : Screen> mavericksVmCreator(
    crossinline create: (initialState: S, screen: P, navigator: Navigator) -> MavericksViewModel<S>,
): MavericksVmCreator = MavericksVmCreator { state, screen, navigator ->
    create(state as S, screen as P, navigator)
}

@ContributesTo(AppScope::class)
interface GooseMavericksAccessors {
    @Multibinds(allowEmpty = true)
    val mavericksVmCreators: Map<KClass<*>, MavericksVmCreator>
}
