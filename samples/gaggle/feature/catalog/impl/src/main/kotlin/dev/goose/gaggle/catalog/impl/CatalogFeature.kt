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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airbnb.mvrx.Async
import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.Uninitialized
import dev.goose.gaggle.catalog.api.CatalogScreen
import dev.goose.gaggle.catalog.api.ProductImageKey
import dev.goose.gaggle.catalog.api.ProductPeekScreen
import dev.goose.gaggle.catalog.api.ProductScreen
import dev.goose.gaggle.catalog.api.ProductTitleKey
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

    fun peekProduct(id: String) = navigator.goTo(ProductPeekScreen(id))

    @AssistedFactory
    fun interface Factory {
        fun create(initialState: CatalogState, navigator: Navigator): CatalogViewModel
    }

    // No Mavericks factory companion: goose-compiler-plugin generates the nested GooseFactory.
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
            // The deal banner is a second ORIGIN for the same shared-element keys: the hero
            // flies out of whichever origin was tapped, list row or banner.
            is Success -> OutlinedButton(onClick = { viewModel.openProduct(deal().id) }) {
                Text(
                    deal().emoji,
                    modifier = Modifier.sharedScreenElement(ProductImageKey(deal().id)),
                )
                Text("  Deal of the day: ", fontWeight = FontWeight.Bold)
                Text(
                    deal().name,
                    modifier = Modifier.sharedScreenElement(ProductTitleKey(deal().id)),
                )
                Text("  ${deal().price}")
            }
            else -> Text("Loading deal…")
        }

        when (val products = state.products) {
            is Uninitialized, is Loading -> CircularProgressIndicator()
            is Success -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products()) { product ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { viewModel.openProduct(product.id) },
                            modifier = Modifier.weight(1f),
                        ) {
                            // Two shared elements per row: emoji and title travel to the
                            // detail screen independently during the push.
                            Text(
                                product.emoji,
                                modifier = Modifier.sharedScreenElement(ProductImageKey(product.id)),
                            )
                            Text(
                                "  ${product.name}",
                                modifier = Modifier.sharedScreenElement(ProductTitleKey(product.id)),
                            )
                            Text("  ${product.price}")
                        }
                        OutlinedButton(onClick = { viewModel.peekProduct(product.id) }) {
                            Text("Peek")
                        }
                    }
                }
            }
            is Fail -> Text("Could not load products")
        }
    }
}
