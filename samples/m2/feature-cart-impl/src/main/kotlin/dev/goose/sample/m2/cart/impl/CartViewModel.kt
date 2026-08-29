package dev.goose.sample.m2.cart.impl

import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import dev.goose.mavericks.gooseVmFactory
import dev.goose.runtime.Navigator
import dev.goose.sample.m2.cart.api.CheckoutScreen
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.launch

data class CartState(
    val items: List<String> = listOf("goose plush", "metro card", "nav3 sticker"),
    val lastCheckoutAddress: String? = null,
) : MavericksState

@AssistedInject
class CartViewModel(
    @Assisted initialState: CartState,
    @Assisted private val navigator: Navigator,
) : MavericksViewModel<CartState>(initialState) {

    /** Cross-stack result: the checkout wizard runs as a nested flow and answers back here. */
    fun checkout() {
        viewModelScope.launch {
            val result = navigator.goToForResult(CheckoutScreen())
            setState { copy(lastCheckoutAddress = result?.shippingAddress) }
        }
    }

    @AssistedFactory
    fun interface Factory {
        fun create(initialState: CartState, navigator: Navigator): CartViewModel
    }

    companion object : MavericksViewModelFactory<CartViewModel, CartState> by gooseVmFactory(CartViewModel::class)
}
