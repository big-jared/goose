package dev.goose.mavericks

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
import dev.goose.runtime.LocalNavigator
import dev.goose.runtime.Navigator
import dev.goose.runtime.NavigatorHandle
import dev.goose.runtime.Screen
import java.util.UUID

/**
 * Retains the [NavigatorHandle] alongside the VM in the entry's ViewModelStore, and runs cleanup
 * exactly when the entry is popped (its store cleared) — notably unregistering the Mavericks
 * SavedStateProvider so popped entries don't leak their VM through the activity's registry.
 */
internal class NavigatorHandleHolder : ViewModel() {
    val handle = NavigatorHandle()
    var onClearedAction: (() -> Unit)? = null

    override fun onCleared() {
        onClearedAction?.invoke()
    }
}

/** Same cleanup hook for flow-scoped VMs, keyed per flow VM in the flow entry's store. */
internal class SavedStateCleanupHolder : ViewModel() {
    var onClearedAction: (() -> Unit)? = null

    override fun onCleared() {
        onClearedAction?.invoke()
    }
}

/**
 * Obtains (or creates) the [MavericksViewModel] for [screen], scoped to the current nav entry.
 * [create] runs once, at first creation — inject the VM's assisted factory into your ScreenUi
 * and delegate to it:
 * ```
 * val vm = screenViewModel<ProfileViewModel, ProfileState>(screen, vmFactory::create)
 * ```
 * - Retention: the VM lives in the entry's ViewModelStoreOwner (provided by the host's
 *   ViewModelStore decorator), so it survives configuration changes and clears on pop.
 * - Process death: `@PersistState` restoration works through the activity's SavedStateRegistry
 *   under a per-entry stable key; the provider is unregistered when the entry pops.
 * - Navigation: the [Navigator] handed to [create] is a [NavigatorHandle] retained with the VM
 *   and rebound to the live host navigator on every composition.
 */
@Composable
inline fun <reified VM : MavericksViewModel<S>, reified S : MavericksState> screenViewModel(
    screen: Screen,
    noinline create: (initialState: S, navigator: Navigator) -> VM,
): VM = screenViewModel(screen, VM::class.java, S::class.java, create)

@OptIn(com.airbnb.mvrx.InternalMavericksApi::class)
@Composable
fun <VM : MavericksViewModel<S>, S : MavericksState> screenViewModel(
    screen: Screen,
    vmClass: Class<VM>,
    stateClass: Class<S>,
    create: (initialState: S, navigator: Navigator) -> VM,
): VM {
    val navigator = LocalNavigator.current
    val activity = checkNotNull(LocalContext.current.findComponentActivity()) {
        "screenViewModel must be hosted in a ComponentActivity"
    }
    val vmStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner. Goose hosts must install a ViewModelStore nav entry decorator."
    }
    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current

    // Stable per-entry identity across process death (rememberSaveable is entry-scoped under the
    // host's saveable decorator). Known limitation shared with Nav3's default contentKey: two
    // EQUAL screen values on one stack share an entry scope — and therefore a ViewModel. Give
    // screens a distinguishing field if the same destination can be pushed twice concurrently.
    val entryId = rememberSaveable { UUID.randomUUID().toString() }
    val key = "${vmClass.name}:$entryId"

    val handleHolder = viewModel<NavigatorHandleHolder>(
        viewModelStoreOwner = vmStoreOwner,
        key = "goose:navigatorHandle",
    )
    DisposableEffect(navigator) {
        handleHolder.handle.bind(navigator)
        onDispose { handleHolder.handle.unbind(navigator) }
    }
    // Reassign each composition so the cleanup always targets the CURRENT activity's registry.
    SideEffect {
        val registry = savedStateRegistryOwner.savedStateRegistry
        handleHolder.onClearedAction = { registry.unregisterSavedStateProvider(key) }
    }

    return remember(screen, entryId) {
        @Suppress("UNCHECKED_CAST")
        val erasedCreate = create as (MavericksState, Navigator) -> MavericksViewModel<*>
        GooseVmLocator.withScope(GooseVmLocator.Scope(handleHolder.handle, erasedCreate)) {
            MavericksViewModelProvider.get(
                viewModelClass = vmClass,
                stateClass = stateClass,
                viewModelContext = ActivityViewModelContext(
                    activity = activity,
                    args = screen,
                    owner = vmStoreOwner,
                    savedStateRegistry = savedStateRegistryOwner.savedStateRegistry,
                ),
                key = key,
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
