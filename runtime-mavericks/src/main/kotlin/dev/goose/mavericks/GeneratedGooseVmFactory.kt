package dev.goose.mavericks

import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.ViewModelContext

/**
 * The superclass of PLUGIN-GENERATED ViewModel factories. goose-compiler-plugin nests a
 * `class GooseFactory : GeneratedGooseVmFactory()` inside every concrete MavericksViewModel
 * subclass that has no hand-written factory, which removes the
 * `companion object : MavericksViewModelFactory by gooseVmFactory(...)` line from migrated
 * ViewModels. Mavericks discovers the factory by scanning nested classes for a
 * [MavericksViewModelFactory] implementation and instantiating it reflectively, so the nested
 * class does not need to be a companion — and the erased create signature means no generics
 * are needed here.
 *
 * Unlike [gooseVmFactory], creation outside a goose scope returns null instead of throwing:
 * Mavericks then falls back to its own reflective conventions. That is what makes blanket
 * generation safe for ViewModels goose never creates (flow-shared ViewModels via
 * `flowViewModel()`, legacy `fragmentViewModel` ViewModels with plain constructors).
 *
 * Hand-written factories always win: the plugin skips any class that already declares a
 * companion or a nested `GooseFactory`.
 */
abstract class GeneratedGooseVmFactory :
    MavericksViewModelFactory<MavericksViewModel<MavericksState>, MavericksState> {

    final override fun create(
        viewModelContext: ViewModelContext,
        state: MavericksState,
    ): MavericksViewModel<MavericksState>? {
        val scope = GooseVmLocator.current ?: return null
        @Suppress("UNCHECKED_CAST")
        return scope.create(state, scope.navigator) as MavericksViewModel<MavericksState>
    }
}
