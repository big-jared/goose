package dev.goose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import java.util.UUID

/**
 * A retention scope shared by every screen composed beneath a [FlowViewModelScope] — the Goose
 * replacement for Mavericks' `activityViewModel()`/`existingViewModel()` when the thing to share
 * across is a FLOW (a wizard, a checkout, a nested stack), not the whole activity.
 *
 * The scope borrows the ViewModelStoreOwner of the entry that declares it, so shared ViewModels
 * live exactly as long as the flow's host entry: retained across config changes, cleared when the
 * flow pops.
 */
class FlowScope internal constructor(
    val viewModelStoreOwner: ViewModelStoreOwner,
    val savedStateRegistryOwner: SavedStateRegistryOwner,
    /** Stable across process death; disambiguates same-class VMs in sibling flows. */
    val flowId: String,
)

val LocalFlowScope = staticCompositionLocalOf<FlowScope?> { null }

/**
 * Declares the current nav entry as a flow scope. Typically wraps a nested NavigableGooseContent:
 * every screen in the nested stack can then obtain shared ViewModels via `flowViewModel()`.
 *
 * Scopes do not nest: an inner FlowViewModelScope SHADOWS the outer one, so a screen inside it
 * asking for the outer flow's VM class would silently get a fresh instance. Keep one scope per
 * flow, and pass data between flows via screen args or results.
 */
@Composable
fun FlowViewModelScope(content: @Composable () -> Unit) {
    val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "FlowViewModelScope requires a ViewModelStoreOwner (host it inside a Goose nav entry)."
    }
    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
    val flowId = rememberSaveable { UUID.randomUUID().toString() }
    val scope = remember(viewModelStoreOwner, savedStateRegistryOwner, flowId) {
        FlowScope(viewModelStoreOwner, savedStateRegistryOwner, flowId)
    }
    CompositionLocalProvider(LocalFlowScope provides scope, content = content)
}
