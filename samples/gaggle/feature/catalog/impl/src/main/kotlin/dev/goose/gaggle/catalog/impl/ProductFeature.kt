package dev.goose.gaggle.catalog.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goose.gaggle.auth.api.LoggedInScope
import dev.goose.gaggle.cart.api.CartMutator
import dev.goose.gaggle.catalog.api.ProductImageKey
import dev.goose.gaggle.catalog.api.ProductPeekScreen
import dev.goose.gaggle.catalog.api.ProductScreen
import dev.goose.gaggle.catalog.api.ProductTitleKey
import dev.goose.gaggle.catalog.api.WriteReviewScreen
import dev.goose.runtime.GooseUi
import dev.goose.runtime.LocalNavigator
import dev.goose.runtime.Navigator
import dev.goose.runtime.StateHolder
import dev.goose.runtime.rememberStateHolder
import dev.goose.runtime.sharedScreenBounds
import kotlinx.coroutines.launch

/**
 * Awaits the review form's typed result from a RETAINED holder (the goToForResult contract:
 * the await must outlive this screen leaving composition while the form is up) and owns the
 * repository write, so the form stays a pure question.
 */
class ProductHolder(
    private val navigator: Navigator,
    private val reviews: ReviewsRepository,
    private val productId: String,
) : StateHolder<Unit>(Unit) {

    fun writeReview() {
        holderScope.launch {
            val posted = navigator.goToForResult(WriteReviewScreen(productId)) ?: return@launch
            reviews.add(productId, posted.rating, posted.text)
        }
    }
}

/**
 * Demonstrates: a SCOPE-registered screen contributed by one feature (catalog) into another
 * feature's session dependency (the cart, which lives in LoggedInScope). CartMutator injects
 * from the logged-in graph and is OBSERVABLE, so the add-to-cart button reflects the cart
 * live. Also: shared-element hero (scale-to-bounds, so the growing text never clips), the
 * custom-drawn rating chart, and related products stacking the SAME screen type.
 */
@GooseUi(ProductScreen::class, scope = LoggedInScope::class)
@Composable
fun ProductUi(
    screen: ProductScreen,
    modifier: Modifier,
    repository: CatalogRepository,
    reviews: ReviewsRepository,
    cart: CartMutator,
) {
    val navigator = LocalNavigator.current
    val product = repository.productById(screen.productId)
    val holder = rememberStateHolder { nav -> ProductHolder(nav, reviews, product.id) }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            product.emoji,
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.sharedScreenBounds(ProductImageKey(product.id)),
        )
        Text(
            product.name,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.sharedScreenBounds(ProductTitleKey(product.id)),
        )
        Text(product.price, style = MaterialTheme.typography.titleLarge)
        Text(product.description, style = MaterialTheme.typography.bodyMedium)
        AddToCartButton(product, cart)

        val summary = reviews.summary(product.id)
        Text("Reviews", style = MaterialTheme.typography.titleMedium)
        if (summary.count == 0) {
            Text("No reviews yet. Be the first honk.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(starLabel(summary.average), color = MaterialTheme.colorScheme.primary)
                Text(
                    "  ${summary.averageLabel} · ${summary.count} review(s)",
                    fontWeight = FontWeight.Bold,
                )
            }
            RatingBars(summary)
            reviews.reviews(product.id).forEach { review ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text(starLabel(review.rating.toDouble()), color = MaterialTheme.colorScheme.primary)
                            Text("  ${review.author}", fontWeight = FontWeight.Bold)
                        }
                        Text(review.text)
                    }
                }
            }
        }
        OutlinedButton(onClick = holder::writeReview) { Text("Write a review") }

        Text("Related", style = MaterialTheme.typography.titleMedium)
        repository.related(product.id).forEach { related ->
            OutlinedButton(onClick = { navigator.goTo(ProductScreen(related.id)) }) {
                Text("${related.emoji} ${related.name}")
            }
        }
    }
}

/** "★★★★☆" for a 4.3 average — rounded to whole stars; the chart carries the detail. */
private fun starLabel(rating: Double): String {
    val full = Math.round(rating).toInt().coerceIn(0, 5)
    return "★".repeat(full) + "☆".repeat(5 - full)
}

/**
 * The add-to-cart button reads the observable cart, so its label is the cart state: adding
 * animates the count in with a small pop (AnimatedContent), and a restored session shows
 * "In cart" without any click.
 */
@Composable
private fun AddToCartButton(product: Product, cart: CartMutator) {
    val quantity = cart.quantityOf(product.id)
    Button(onClick = { cart.add(product.id, product.name, product.priceCents) }) {
        AnimatedContent(
            targetState = quantity,
            transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith fadeOut() },
            label = "addToCart",
        ) { q ->
            Text(if (q == 0) "Add to cart" else "In cart · $q · add another")
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
