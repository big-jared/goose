package dev.goose.sample.m2.catalog.impl

import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import dev.goose.mavericks.gooseVmFactory
import dev.goose.runtime.Navigator
import dev.goose.sample.m2.cart.api.CheckoutScreen
import dev.goose.sample.m2.catalog.api.ItemDetailScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.launch

/** A one-off service demonstrating `gooseGraph<Accessor>()` injection straight into composables. */
@SingleIn(AppScope::class)
@Inject
class PricingService {
    fun priceOf(itemId: String): String = "$${(itemId.hashCode().mod(40) + 10)}.99"
}

data class CatalogState(
    val items: List<String> = listOf("alpha", "bravo", "coral", "delta", "ember", "fjord"),
) : MavericksState

@AssistedInject
class CatalogViewModel(
    @Assisted initialState: CatalogState,
    @Assisted private val navigator: Navigator,
) : MavericksViewModel<CatalogState>(initialState) {

    fun onItemClicked(itemId: String) = navigator.goTo(ItemDetailScreen(itemId))

    @AssistedFactory
    fun interface Factory {
        fun create(initialState: CatalogState, navigator: Navigator): CatalogViewModel
    }

    companion object : MavericksViewModelFactory<CatalogViewModel, CatalogState> by gooseVmFactory(CatalogViewModel::class)
}

data class ItemDetailState(
    val itemId: String = "",
    val lastPurchaseAddress: String? = null,
) : MavericksState {
    constructor(screen: ItemDetailScreen) : this(itemId = screen.itemId)
}

@AssistedInject
class ItemDetailViewModel(
    @Assisted initialState: ItemDetailState,
    @Assisted private val navigator: Navigator,
) : MavericksViewModel<ItemDetailState>(initialState) {

    /** Cross-module + cross-feature result: launches the cart feature's checkout via its :api. */
    fun buyNow() {
        viewModelScope.launch {
            val itemId = awaitState().itemId
            val result = navigator.goToForResult(CheckoutScreen(itemId = itemId))
            setState { copy(lastPurchaseAddress = result?.shippingAddress) }
        }
    }

    @AssistedFactory
    fun interface Factory {
        fun create(initialState: ItemDetailState, navigator: Navigator): ItemDetailViewModel
    }

    companion object : MavericksViewModelFactory<ItemDetailViewModel, ItemDetailState> by gooseVmFactory(ItemDetailViewModel::class)
}
