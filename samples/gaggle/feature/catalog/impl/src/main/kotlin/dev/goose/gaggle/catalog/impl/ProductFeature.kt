package dev.goose.gaggle.catalog.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.goose.gaggle.auth.api.LoggedInScope
import dev.goose.gaggle.cart.api.CartMutator
import dev.goose.gaggle.catalog.api.ProductImageKey
import dev.goose.gaggle.catalog.api.ProductPeekScreen
import dev.goose.gaggle.catalog.api.ProductScreen
import dev.goose.gaggle.catalog.api.ProductTitleKey
import dev.goose.runtime.GooseUi
import dev.goose.runtime.LocalNavigator
import dev.goose.runtime.sharedScreenElement

/**
 * Demonstrates: a SCOPE-registered screen contributed by one feature (catalog) into another
 * feature's session dependency (the cart, which lives in LoggedInScope). CartMutator injects
 * from the logged-in graph; the screen resolves only inside GooseScope. Also: shared-element
 * hero (the emoji travels from the list), and related products stacking the SAME screen type,
 * each push its own entry.
 */
@GooseUi(ProductScreen::class, scope = LoggedInScope::class)
@Composable
fun ProductUi(
    screen: ProductScreen,
    modifier: Modifier,
    repository: CatalogRepository,
    cart: CartMutator,
) {
    val navigator = LocalNavigator.current
    val product = repository.productById(screen.productId)
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            product.emoji,
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.sharedScreenElement(ProductImageKey(product.id)),
        )
        Text(
            product.name,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.sharedScreenElement(ProductTitleKey(product.id)),
        )
        Text(product.price, style = MaterialTheme.typography.titleLarge)
        Button(onClick = { cart.add(product.id, product.name) }) { Text("Add to cart") }
        Text("Related", style = MaterialTheme.typography.titleMedium)
        repository.related(product.id).forEach { related ->
            OutlinedButton(onClick = { navigator.goTo(ProductScreen(related.id)) }) {
                Text("${related.emoji} ${related.name}")
            }
        }
    }
}

/**
 * Demonstrates: an OverlayScreen with CUSTOM window properties — usePlatformDefaultWidth =
 * false on the screen, so this near-edge-to-edge card decides its own width. Also a dialog
 * that promotes itself: "Open full page" pops the overlay and pushes the detail screen in the
 * same frame, so the user never sees the catalog in between.
 */
@GooseUi(ProductPeekScreen::class)
@Composable
fun ProductPeekUi(screen: ProductPeekScreen, modifier: Modifier, repository: CatalogRepository) {
    val navigator = LocalNavigator.current
    val product = repository.productById(screen.productId)
    Card(Modifier.fillMaxWidth(0.96f)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(product.emoji, style = MaterialTheme.typography.displayMedium)
            Text("${product.name}  ${product.price}", style = MaterialTheme.typography.titleLarge)
            Text("A quick look. The full page has related products and add-to-cart.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { navigator.pop() }) { Text("Close") }
                Button(onClick = {
                    navigator.pop()
                    navigator.goTo(ProductScreen(product.id))
                }) { Text("Open full page") }
            }
        }
    }
}
