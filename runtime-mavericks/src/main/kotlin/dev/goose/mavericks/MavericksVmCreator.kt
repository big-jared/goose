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

@ContributesTo(AppScope::class)
interface GooseMavericksAccessors {
    @Multibinds(allowEmpty = true)
    val mavericksVmCreators: Map<KClass<*>, MavericksVmCreator>
}
