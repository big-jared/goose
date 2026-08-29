package dev.goose.sample.m2.cart.impl

import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel

data class CheckoutFlowState(val address: String = "") : MavericksState

/**
 * Shared by every step of the checkout wizard via `flowViewModel()` — the flow-scoped analogue
 * of `activityViewModel()`. Lives in the CheckoutScreen entry's ViewModelStore: survives config
 * changes, dies when the wizard pops. Plain Mavericks creation, no DI wiring needed.
 */
class CheckoutFlowViewModel(initialState: CheckoutFlowState) :
    MavericksViewModel<CheckoutFlowState>(initialState) {

    fun setAddress(address: String) = setState { copy(address = address) }
}
