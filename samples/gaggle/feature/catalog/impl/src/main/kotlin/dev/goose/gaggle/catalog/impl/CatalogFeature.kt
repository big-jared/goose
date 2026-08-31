package dev.goose.gaggle.catalog.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.mvrx.Async
import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.Uninitialized
import dev.goose.mavericks.gooseVmFactory
import dev.goose.gaggle.catalog.api.CatalogScreen
import dev.goose.gaggle.catalog.api.ProductImageKey
import dev.goose.gaggle.catalog.api.ProductScreen
import dev.goose.runtime.GooseUi
import dev.goose.runtime.Navigator
import dev.goose.runtime.sharedScreenElement
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject

data class CatalogState(
    val products: Async<List<Product>> = Uninitialized,
    val deal: Async<Product> = Uninitialized,
) : MavericksState

/**
 * Demonstrates: a stock Mavericks ViewModel with Async state, including a Fail + retry path
 * (the deal's first load always fails), navigating through the injected Navigator.
 */
@AssistedInject
class CatalogViewModel(
    @Assisted initialState: CatalogState,
    @Assisted private val navigator: Navigator,
    private val repository: CatalogRepository,
) : MavericksViewModel<CatalogState>(initialState) {

    init {
        suspend { repository.loadProducts() }.execute { copy(products = it) }
        loadDeal()
    }

    fun loadDeal() {
        suspend { repository.loadDeal() }.execute { copy(deal = it) }
    }

    fun openProduct(id: String) = navigator.goTo(ProductScreen(id))

    @AssistedFactory
    fun interface Factory {
        fun create(initialState: CatalogState, navigator: Navigator): CatalogViewModel
    }

    companion object : MavericksViewModelFactory<CatalogViewModel, CatalogState> by gooseVmFactory(CatalogViewModel::class)
}

/** Demonstrates: @GooseUi wiring state + VM by type; the shop tab's root screen. */
@GooseUi(CatalogScreen::class)
@Composable
fun CatalogUi(state: CatalogState, viewModel: CatalogViewModel, modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Shop", style = MaterialTheme.typography.headlineMedium)

        when (val deal = state.deal) {
            is Fail -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Deal failed to load. ", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = viewModel::loadDeal) { Text("Retry") }
            }
            is Success -> Text("Deal of the day: ${deal().emoji} ${deal().name} ${deal().price}")
            else -> Text("Loading deal…")
        }

        when (val products = state.products) {
            is Uninitialized, is Loading -> CircularProgressIndicator()
            is Success -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products()) { product ->
                    Button(
                        onClick = { viewModel.openProduct(product.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            product.emoji,
                            modifier = Modifier.sharedScreenElement(ProductImageKey(product.id)),
                        )
                        Text("  ${product.name}  ${product.price}")
                    }
                }
            }
            is Fail -> Text("Could not load products")
        }
    }
}
