package dev.goose.mavericks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.airbnb.mvrx.ActivityViewModelContext
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelProvider
import dev.goose.runtime.FlowViewModelScope
import dev.goose.runtime.LocalFlowScope

/**
 * Obtains (or creates) a [MavericksViewModel] shared by every screen under the enclosing
 * [FlowViewModelScope] — the flow-level analogue of Mavericks' `activityViewModel()`. The VM
 * lives in the flow host entry's ViewModelStore: retained across config changes, cleared when
 * the flow pops.
 *
 * Flow ViewModels are created through Mavericks' own conventions (companion factory or a
 * single-state-arg constructor); they are shared state pots, not navigators, so no Goose
 * assisted wiring is involved.
 */
@Composable
inline fun <reified VM : MavericksViewModel<S>, reified S : MavericksState> flowViewModel(): VM =
    flowViewModel(VM::class.java, S::class.java)

@OptIn(com.airbnb.mvrx.InternalMavericksApi::class)
@Composable
fun <VM : MavericksViewModel<S>, S : MavericksState> flowViewModel(
    vmClass: Class<VM>,
    stateClass: Class<S>,
): VM {
    val flowScope = checkNotNull(LocalFlowScope.current) {
        "flowViewModel requires an enclosing FlowViewModelScope."
    }
    val activity = checkNotNull(LocalContext.current.findComponentActivity()) {
        "flowViewModel must be hosted in a ComponentActivity"
    }
    return remember(flowScope, vmClass) {
        MavericksViewModelProvider.get(
            viewModelClass = vmClass,
            stateClass = stateClass,
            viewModelContext = ActivityViewModelContext(
                activity = activity,
                args = null,
                owner = flowScope.viewModelStoreOwner,
                savedStateRegistry = flowScope.savedStateRegistryOwner.savedStateRegistry,
            ),
            key = "${vmClass.name}:flow:${flowScope.flowId}",
        )
    }
}
