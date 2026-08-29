package dev.goose.mavericks

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.airbnb.mvrx.ActivityViewModelContext
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelProvider
import dev.goose.metro.gooseGraph
import dev.goose.runtime.LocalNavigator
import dev.goose.runtime.Navigator
import dev.goose.runtime.NavigatorHandle
import dev.goose.runtime.Screen
import java.util.UUID

/** Retains the [NavigatorHandle] alongside the VM in the entry's ViewModelStore. */
internal class NavigatorHandleHolder : ViewModel() {
    val handle = NavigatorHandle()
}

/**
 * Obtains (or creates) the [MavericksViewModel] for [screen], scoped to the current nav entry.
 *
 * - Retention: the VM lives in the entry's ViewModelStoreOwner (provided by the host's
 *   ViewModelStore decorator), so it survives configuration changes and clears on pop.
 * - Process death: `@PersistState` restoration works through the entry's SavedStateRegistry with
 *   a per-entry stable key.
 * - Navigation: the VM's assisted [Navigator] is a [NavigatorHandle] retained with the VM and
 *   rebound to the live host navigator on every composition.
 */
@Composable
inline fun <reified VM : MavericksViewModel<S>, reified S : MavericksState> screenViewModel(
    screen: Screen,
): VM = screenViewModel(screen, VM::class.java, S::class.java)

@OptIn(com.airbnb.mvrx.InternalMavericksApi::class)
@Composable
fun <VM : MavericksViewModel<S>, S : MavericksState> screenViewModel(
    screen: Screen,
    vmClass: Class<VM>,
    stateClass: Class<S>,
): VM {
    val navigator = LocalNavigator.current
    val activity = checkNotNull(LocalContext.current.findComponentActivity()) {
        "screenViewModel must be hosted in a ComponentActivity"
    }
    val vmStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner. Goose hosts must install a ViewModelStore nav entry decorator."
    }
    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current

    // Stable per-entry identity: unique when the same screen appears twice on a stack, stable
    // across process death (rememberSaveable is entry-scoped under the host's saveable decorator).
    val entryId = rememberSaveable { UUID.randomUUID().toString() }

    val handleHolder = viewModel<NavigatorHandleHolder>(
        viewModelStoreOwner = vmStoreOwner,
        key = "goose:navigatorHandle",
    )
    DisposableEffect(navigator) {
        handleHolder.handle.bind(navigator)
        onDispose { handleHolder.handle.unbind(navigator) }
    }

    val creators = gooseGraph<GooseMavericksAccessors>().mavericksVmCreators
    return remember(screen, entryId) {
        GooseVmLocator.withScope(GooseVmLocator.Scope(screen, handleHolder.handle, creators)) {
            MavericksViewModelProvider.get(
                viewModelClass = vmClass,
                stateClass = stateClass,
                viewModelContext = ActivityViewModelContext(
                    activity = activity,
                    args = screen,
                    owner = vmStoreOwner,
                    savedStateRegistry = savedStateRegistryOwner.savedStateRegistry,
                ),
                key = "${vmClass.name}:$entryId",
            )
        }
    }
}

fun Context.findComponentActivity(): ComponentActivity? {
    var current = this
    while (true) {
        when (current) {
            is ComponentActivity -> return current
            is ContextWrapper -> current = current.baseContext
            else -> return null
        }
    }
}
